package com.example.markdowndesk;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Twelve sequential generated documents; no document/session-sized array or sink buffer. */
public final class PrivateStateMemoryCheck {
    static int checks;
    static void check(boolean value,String message) { checks++; if(!value) throw new AssertionError(message); }
    static final int LIMIT=8*1024*1024;
    static int value(long offset,int seed,boolean bom) {
        if(bom && offset<3) return new int[]{0xef,0xbb,0xbf}[(int)offset];
        long at=offset-(bom?3:0);
        if(at%1024==0) return '\r'; if(at%1024==1) return '\n';
        if(at%1024==2) return 0; if(at%1024==3) return ' ';
        return 'a'+(int)((at+seed)%26);
    }
    static final class Generated extends InputStream {
        final int length,seed; final boolean bom; int at,max;
        Generated(int length,int seed,boolean bom) { this.length=length;this.seed=seed;this.bom=bom; }
        public int read() { return at==length?-1:value(at++,seed,bom); }
        public int read(byte[] bytes,int offset,int count) {
            max=Math.max(max,count); if(count>65536) throw new AssertionError("unbounded source request");
            if(at==length) return -1;
            int actual=Math.min(count,length-at);
            for(int i=0;i<actual;i++) bytes[offset+i]=(byte)value(at++,seed,bom);
            return actual;
        }
    }
    static final class GuardInput extends FilterInputStream {
        int max;
        GuardInput(InputStream input) { super(input); }
        public int read(byte[] bytes,int offset,int length) throws IOException {
            max=Math.max(max,length); if(length>65536) throw new AssertionError("unbounded frame read");
            return in.read(bytes,offset,length);
        }
    }
    static final class Sink extends OutputStream {
        final int seed; final boolean bom; long at; int max;
        Sink(int seed,boolean bom) { this.seed=seed;this.bom=bom; }
        public void write(int b) { if((b&255)!=value(at++,seed,bom)) throw new AssertionError("changed payload byte"); }
        public void write(byte[] bytes,int offset,int length) {
            max=Math.max(max,length); if(length>65536) throw new AssertionError("unbounded sink request");
            for(int i=0;i<length;i++) write(bytes[offset+i]);
        }
    }
    public static void main(String[] args) throws Exception {
        Path directory=Path.of(args[0]); Files.createDirectories(directory);
        byte[] headroom=new byte[32*1024*1024]; Arrays.fill(headroom,(byte)0x5a);
        long total=0;
        for(int seed=0;seed<12;seed++) {
            boolean bom=seed%2==0; int units=LIMIT-(bom?3:0);
            PrivateStateCodec.Tab tab=new PrivateStateCodec.Tab("synthetic-"+seed,"Boundary.md",
                    new PrivateStateCodec.Provenance(null,null,-1,false),bom,true,false,units,0,
                    Integer.MAX_VALUE,0,seed%3,"","",false,false,false);
            Path frame=directory.resolve("current-frame.bin"); Generated source=new Generated(LIMIT,seed,bom);
            try(OutputStream out=Files.newOutputStream(frame)) { PrivateStateCodec.writeTab(tab,LIMIT,source,out); }
            check(source.at==LIMIT,"writer exact source length"); check(source.max<=65536,"writer bounded bulk reads");
            long size=Files.size(frame); check(size<=LIMIT+32768L,"complete archive document envelope cap");
            Sink sink=new Sink(seed,bom);
            try(GuardInput input=new GuardInput(Files.newInputStream(frame))) {
                PrivateStateCodec.Record record=PrivateStateCodec.read(input,sink);
                check(record.payloadBytes==LIMIT,"exact8MiB payload accepted including BOM");
                check(record.utf16Units==units,"streamed units exact");
                check(record.tab.selectionStart==units&&record.tab.selectionEnd==0,"reverse boundary selection");
                check(input.max<=65536,"reader bounded bulk reads");
            }
            check(sink.at==LIMIT&&sink.max<=65536,"sink byte fidelity/bound"); total+=sink.at;
        }
        check(total==12L*LIMIT,"twelve separate payloads streamed");
        check(headroom[0]==0x5a&&headroom[headroom.length-1]==0x5a,"32MiB headroom retained");
        PrivateStateCodec.Tab tab=new PrivateStateCodec.Tab("id","limit",new PrivateStateCodec.Provenance(null,null,-1,false),false,true,false,0,0,0,0,0,"","",false,false,false);
        InputStream neverRead=new InputStream(){ public int read(){ throw new AssertionError("oversize source read"); }};
        OutputStream neverWrite=new OutputStream(){ public void write(int b){ throw new AssertionError("oversize frame written"); }};
        try { PrivateStateCodec.writeTab(tab,LIMIT+1,neverRead,neverWrite); throw new AssertionError("oversize accepted"); }
        catch(IOException expected) { checks++; }
        Files.delete(directory.resolve("current-frame.bin"));
        System.out.println("PASS "+checks+" private-state memory assertions; "+total+" generated payload bytes; heap128MiB, headroom32MiB");
    }
}
