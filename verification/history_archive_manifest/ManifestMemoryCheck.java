package com.example.markdowndesk;

import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import static com.example.markdowndesk.HistoryManifestCheck.*;

/** Maximal metadata only. Does not allocate native payloads or a whole manifest byte array. */
public final class ManifestMemoryCheck {
    static final byte[] HEADROOM=new byte[32<<20];
    static void writeHandcrafted(OutputStream output,int count)throws Exception {
        MessageDigest digest=MessageDigest.getInstance("SHA-256");DigestOutputStream tracked=new DigestOutputStream(output,digest);
        DataOutputStream out=new DataOutputStream(tracked);
        int nameBytes=25; // store5 + catalog7 + identity8 + index5
        out.writeBytes("MDHMFST\n");out.writeInt(1);out.writeInt(212+nameBytes+74*count);out.writeInt(count);
        out.writeLong(45L*count);uuid(out,new UUID(0,91));out.write(new byte[32]);out.writeLong(0);out.write(new byte[32]);
        out.writeLong(64L<<20);out.writeInt(20);out.write(new byte[32]);
        for(int i=0;i<4;i++){out.writeByte(i+1);out.writeInt(NAMES[i].length());out.writeBytes(NAMES[i]);}
        byte[] hash=new byte[32];
        for(int i=0;i<count;i++){out.writeByte(1);out.writeByte(1);uuid(out,new UUID(0,i));uuid(out,archiveId(i));out.writeLong(45);out.write(hash);}
        tracked.on(false);out.write(digest.digest());
    }
    static final class Sink extends OutputStream {
        long count;final MessageDigest digest;
        Sink()throws Exception{digest=MessageDigest.getInstance("SHA-256");}
        public void write(int value){count++;digest.update((byte)value);}
        public void write(byte[] bytes,int offset,int length){count+=length;digest.update(bytes,offset,length);}
    }
    public static void main(String[] args)throws Exception {
        Arrays.fill(HEADROOM,(byte)73);Path file=Files.createTempFile("manifest-bound-",".metadata");
        try {
            try(OutputStream out=Files.newOutputStream(file)){writeHandcrafted(out,16380);}
            HistoryArchiveManifest manifest;
            try(InputStream in=Files.newInputStream(file)){manifest=HistoryArchiveManifest.read(in);}
            check(manifest.entries.size()==16380&&HistoryArchiveManifest.MAX_ENTRIES==16380,"actual maximum admitted descriptors");
            check(manifest.indexBytes==16777216&&manifest.indexBytes==HistoryArchiveManifest.MAX_INDEX_BYTES,"exact conservative index budget");
            check(manifest.serializedBytes==1212357&&Files.size(file)==manifest.serializedBytes,"separate complete serialized byte size");
            check(manifest.archiveBytes==64+16380L*(58+45),"max-count metadata feasibility");
            Sink replay=new Sink();manifest.write(replay);Sink source=new Sink();
            try(InputStream in=Files.newInputStream(file)){byte[] buffer=new byte[8192];int n;while((n=in.read(buffer))!=-1)source.write(buffer,0,n);}
            check(source.count==replay.count&&Arrays.equals(source.digest.digest(),replay.digest.digest()),"max-count persisted mapping exact replay");
            for(int i=0;i<manifest.entries.size();i++){HistoryArchiveManifest.Entry e=manifest.entries.get(i);check(e.nativeId.equals(new UUID(0,i))&&e.archiveId.equals(archiveId(i)),"every maximum-count native/archive identity retained");}
            // Only the header is supplied: the count cap must reject before reading/allocating entries.
            for(int count:new int[]{16381,65536,Integer.MAX_VALUE}){
                ByteArrayOutputStream bytes=new ByteArrayOutputStream();DataOutputStream out=new DataOutputStream(bytes);
                out.writeBytes("MDHMFST\n");out.writeInt(1);out.writeInt(1212431);out.writeInt(count);
                final boolean[] beyond={false};InputStream input=new ByteArrayInputStream(bytes.toByteArray()){
                    public synchronized int read(){if(available()==0){beyond[0]=true;throw new AssertionError("Read past rejected header");}return super.read();}
                    public synchronized int read(byte[] b,int o,int l){if(available()==0){beyond[0]=true;throw new AssertionError("Read past rejected header");}return super.read(b,o,l);}
                };
                rejects(()->HistoryArchiveManifest.read(input),"count boundary +1/larger");check(!beyond[0],"count rejected before metadata body read");
            }
            check(HEADROOM[0]==73&&HEADROOM[HEADROOM.length-1]==73,"32 MiB held for entire max-count run");
            System.out.println("History manifest memory: "+checks+" checks PASS; 16,380 descriptors, -Xmx128m, 32 MiB held; 1,212,357 wire bytes; 16 MiB index charge (not exact JVM allocation)");
        } finally {Files.delete(file);}
    }
}
