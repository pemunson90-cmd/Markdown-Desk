package com.example.markdowndesk;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import com.example.markdowndesk.PrivateArchive.*;
import static com.example.markdowndesk.PrivateArchive.*;

/** Repeated document-sized payloads, generated and checked without materializing a payload. */
public final class ArchiveMemoryCheck {
    private static final byte[][] HEADROOM = new byte[8][];
    private static final long SIZE=MAX_DOCUMENT_ENTRY_BYTES-7L;
    private static UUID id(int n){return new UUID(0x0123456789ab4000L,0x8000000000000000L+n);}
    private static final class Generated extends InputStream {
        private final long length; private long offset;
        Generated(long length){this.length=length;}
        public int read(){return offset==length?-1:pattern(offset++);}
        public int read(byte[] b,int off,int len){
            if(len==0)return 0;if(offset==length)return -1;
            int n=(int)Math.min(Math.min(len,65536),length-offset);
            for(int i=0;i<n;i++)b[off+i]=(byte)pattern(offset++);return n;
        }
    }
    private static int pattern(long offset){return (int)((offset*31+17)&255);}
    private static ArchiveSource source(int count,long size){return new ArchiveSource(){int n;public SourceEntry next(){return n==count?null:new SourceEntry(id(++n),Type.SNAPSHOT,size,()->new Generated(size));}};}
    private static byte[] prefix()throws IOException {
        ByteArrayOutputStream out=new ByteArrayOutputStream();DataOutputStream wire=new DataOutputStream(out);
        wire.write("MDESKBACKUP\r\n\032\n".getBytes(StandardCharsets.US_ASCII));wire.writeInt(1);return out.toByteArray();
    }
    private static byte[] frame(int n,byte[] hash)throws IOException {
        ByteArrayOutputStream out=new ByteArrayOutputStream();DataOutputStream wire=new DataOutputStream(out);
        wire.writeByte(1);wire.writeByte(1);wire.writeLong(id(n).getMostSignificantBits());wire.writeLong(id(n).getLeastSignificantBits());
        wire.writeLong(MAX_DOCUMENT_ENTRY_BYTES);wire.write(hash);return out.toByteArray();
    }
    private static void readTotalLimit()throws Exception {
        MessageDigest hash=MessageDigest.getInstance("SHA-256");byte[] buffer=new byte[TRANSFER_BYTES];
        InputStream payload=new Generated(MAX_DOCUMENT_ENTRY_BYTES);int n;
        while((n=payload.read(buffer))!=-1)hash.update(buffer,0,n);
        byte[] payloadHash=hash.digest();
        // Independently generated hostile stream; no writer-produced cap-limited fixture.
        InputStream oversized=new InputStream(){
            InputStream current=new ByteArrayInputStream(prefix());int record;boolean payloadPending;
            private boolean advance()throws IOException {
                if(payloadPending){current=new Generated(MAX_DOCUMENT_ENTRY_BYTES);payloadPending=false;return true;}
                if(record==128)return false;
                current=new ByteArrayInputStream(frame(++record,payloadHash));payloadPending=true;return true;
            }
            public int read()throws IOException {while(true){int value=current.read();if(value>=0)return value;if(!advance())return -1;}}
            public int read(byte[] b,int o,int length)throws IOException{while(true){int read=current.read(b,o,length);if(read>=0)return read;if(!advance())return -1;}}
        };
        int[] started={0};
        try{PrivateArchive.read(oversized,e->{started[0]++;return OutputStream.nullOutputStream();});throw new AssertionError("accepted reader >1GiB");}
        catch(IOException expected){if(!expected.getMessage().contains("Archive byte limit"))throw expected;}
        if(started[0]!=127)throw new AssertionError("reader opened oversized entry sink");
    }
    public static void main(String[] args)throws Exception {
        for(int n=0;n<HEADROOM.length;n++){HEADROOM[n]=new byte[4*1024*1024];Arrays.fill(HEADROOM[n],(byte)(n+1));}
        Path file=Files.createTempFile("synthetic-private-archive-",".bin");
        try {
            final int count=20;ArchiveSummary written;
            try(OutputStream out=Files.newOutputStream(file)){written=PrivateArchive.write(source(count,SIZE),out);}
            long[] checked={0};int[] finished={0};
            EntrySink sink=new EntrySink(){
                public OutputStream begin(Entry e){return new OutputStream(){long offset;
                    public void write(int b){if(b!=pattern(offset++))throw new AssertionError("byte mismatch");checked[0]++;}
                    public void write(byte[] b,int o,int n){for(int i=0;i<n;i++){if((b[o+i]&255)!=pattern(offset++))throw new AssertionError("stream byte mismatch");}checked[0]+=n;}
                };}
                public void finish(Entry e,OutputStream stream,boolean valid){if(!valid)throw new AssertionError("unverified entry");finished[0]++;}
            };
            ArchiveSummary read;
            try(InputStream in=Files.newInputStream(file)){read=PrivateArchive.read(in,sink);}
            if(written.entryCount!=count||read.entryCount!=count||checked[0]!=count*SIZE||finished[0]!=count||!written.sha256.equals(read.sha256)||written.byteCount!=Files.size(file))throw new AssertionError("memory roundtrip");
            // Independent 1GiB output ceiling: stream to a discard destination without a giant file.
            try{PrivateArchive.write(source(129,MAX_DOCUMENT_ENTRY_BYTES),OutputStream.nullOutputStream());throw new AssertionError("accepted >1GiB archive");}catch(IOException expected){if(!expected.getMessage().contains("Archive byte limit"))throw expected;}
            readTotalLimit();
            for(int n=0;n<HEADROOM.length;n++){if(HEADROOM[n][0]!=(byte)(n+1)||HEADROOM[n][HEADROOM[n].length-1]!=(byte)(n+1))throw new AssertionError("headroom lost");}
            System.out.println("PrivateArchive memory PASS:128MiB heap,32MiB retained headroom,20 repeated near8MiB entries,"+checked[0]+" bytes checked;1GiB write/read caps rejected");
        } finally {Files.deleteIfExists(file);}
    }
}
