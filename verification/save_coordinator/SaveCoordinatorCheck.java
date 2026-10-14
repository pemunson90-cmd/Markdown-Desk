package com.example.markdowndesk;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.util.*;

/** Actual production store/coordinator; providers and fault boundaries are controlled host adapters. */
public final class SaveCoordinatorCheck {
    static int checks, cases;
    static final byte[] ORIGINAL="\ufefforiginal\r\n雪".getBytes(StandardCharsets.UTF_8);
    static final byte[] EDITED="\ufeffedited\r\n🧭".getBytes(StandardCharsets.UTF_8);
    static void check(boolean value,String name) { checks++; if(!value)throw new AssertionError(name); }
    static void bytes(byte[] expected,byte[] actual,String name) { check(Arrays.equals(expected,actual),name); }
    static final class Disk implements ProtectionStore.Durability {
        int calls, failAt;
        void before() throws IOException { if(++calls==failAt) throw new IOException("injected durability failure"); }
        public void syncFile(FileDescriptor fd)throws IOException { before(); fd.sync(); }
        public void move(File a,File b)throws IOException { before(); Files.move(a.toPath(),b.toPath(),StandardCopyOption.ATOMIC_MOVE); }
        public void syncDirectory(File dir)throws IOException { before(); try(FileChannel c=FileChannel.open(dir.toPath(),StandardOpenOption.READ)){c.force(true);} }
    }
    static class Provider implements SaveCoordinator.Provider {
        byte[] value=ORIGINAL.clone(), expectedAttempt=EDITED.clone(); int reads,writes; String failure; Runnable afterRecheck,afterWrite; int changeAt; byte[] changed;
        ProtectionStore store;
        public byte[] read()throws IOException {
            reads++; if(reads==2&&afterRecheck!=null)afterRecheck.run(); if(reads==changeAt)value=changed.clone();
            if("read".equals(failure)||("readback".equals(failure)&&writes>0))throw new IOException("read unavailable");
            return value.clone();
        }
        public void write(byte[] content)throws IOException {
            check(store.transactions().size()==1,"transaction committed before mutation");
            ProtectionStore.Transaction t=store.transactions().get(0);
            bytes(ORIGINAL,t.original.bytes(),"original durably recoverable at mutation");
            bytes(expectedAttempt,t.attempted.bytes(),"edit durably recoverable at mutation");
            check(!t.verified,"not verified before mutation");
            writes++; value=new byte[0];
            if("zero".equals(failure))throw new IOException("zero write");
            if("partial".equals(failure)){value=Arrays.copyOf(content,2);throw new IOException("partial write");}
            value=content.clone(); if(afterWrite!=null)afterWrite.run();
            if("mismatch".equals(failure))value="altered".getBytes(StandardCharsets.UTF_8);
            if("flush".equals(failure)||"close".equals(failure))throw new IOException(failure);
        }
    }
    static final class Fixture implements AutoCloseable {
        final Path root=Files.createTempDirectory("coordinator-fixture-");
        final Disk disk=new Disk(); final ProtectionStore store; final Provider provider=new Provider();
        Fixture()throws IOException { store=new ProtectionStore(root.resolve("protection").toFile(),disk,64L*1024*1024); provider.store=store; disk.calls=0; }
        public void close()throws IOException { try(java.util.stream.Stream<Path> s=Files.walk(root)){for(Path p:(Iterable<Path>)s.sorted(Comparator.reverseOrder())::iterator)Files.delete(p);} }
        SaveCoordinator.Request request(String baseline,String consent,boolean destination) {
            return new SaveCoordinator.Request("document-1","content://fixture/document","Synthetic.md",7,EDITED,baseline,consent,destination);
        }
        SaveCoordinator.Result save()throws IOException {return new SaveCoordinator(store).save(request(EditorTools.sha256(ORIGINAL),null,false),provider);}
    }
    static void pass(String name){cases++;System.out.println("PASS "+name);}
    static SaveCoordinator.SaveFailure failure(Fixture f)throws IOException {
        try {f.save();throw new AssertionError("save falsely succeeded");} catch(SaveCoordinator.SaveFailure e){return e;}
    }
    public static void main(String[] ignored)throws Exception {
        try(Fixture f=new Fixture()) {
            SaveCoordinator.Result result=f.save();
            check(f.provider.writes==1,"exactly one write"); bytes(EDITED,f.provider.value,"verified bytes");
            check(result.hash.equals(EditorTools.sha256(EDITED)),"verified hash");
            ProtectionStore reopened=new ProtectionStore(f.root.resolve("protection").toFile(),new Disk(),64L*1024*1024);
            ProtectionStore.Transaction t=reopened.transactions().get(0); check(t.verified,"verified outcome after restart");
            bytes(ORIGINAL,t.original.bytes(),"restarted original"); bytes(EDITED,t.attempted.bytes(),"restarted edit");pass("verified save/restart exact bytes");
        }
        for(String fault:new String[]{"zero","partial","flush","close","mismatch","readback"})try(Fixture f=new Fixture()) {
            f.provider.failure=fault;SaveCoordinator.SaveFailure e=failure(f);
            check(e.writeMayHaveOccurred,"uncertain provider outcome");check(e.transaction!=null,"prepared recovery ID");
            ProtectionStore reopened=new ProtectionStore(f.root.resolve("protection").toFile(),new Disk(),64L*1024*1024);
            check(!reopened.transactions().get(0).verified,"no false verified record");
            bytes(ORIGINAL,reopened.transactions().get(0).original.bytes(),"failed original survives");
            bytes(EDITED,reopened.transactions().get(0).attempted.bytes(),"failed edits survive");
            check(f.provider.writes==1,"no automatic rollback/retry");pass("provider "+fault+" retains both versions");
        }
        try(Fixture f=new Fixture()) {
            f.provider.failure="read";SaveCoordinator.SaveFailure e=failure(f);
            check(!e.writeMayHaveOccurred&&f.provider.writes==0,"read error never mutates");
            check(e.attempted!=null,"attempt committed before read");bytes(EDITED,f.store.snapshots().get(0).bytes(),"orphan attempt recoverable");pass("failed source read keeps attempt");
        }
        for(String baseline:new String[]{null,"stale"})try(Fixture f=new Fixture()) {
            try{new SaveCoordinator(f.store).save(f.request(baseline,null,false),f.provider);throw new AssertionError("unknown/stale baseline allowed");}
            catch(SaveCoordinator.ConflictException e){check(e.observedHash.equals(EditorTools.sha256(ORIGINAL)),"conflict binds observed hash");}
            check(f.provider.writes==0,"conflict no writes");check(f.store.snapshots().size()==1,"conflict retains attempt");pass("unknown/stale baseline conflict");
        }
        try(Fixture f=new Fixture()) {
            new SaveCoordinator(f.store).save(f.request("stale",EditorTools.sha256(ORIGINAL),false),f.provider);
            check(f.provider.writes==1,"exact observed consent permits save");pass("bound overwrite consent");
        }
        try(Fixture f=new Fixture()) {
            try{new SaveCoordinator(f.store).save(f.request("stale","older-consent",false),f.provider);throw new AssertionError("stale consent reused");}
            catch(SaveCoordinator.ConflictException expected){check(f.provider.writes==0,"stale consent no writes");}pass("stale consent rejected");
        }
        try(Fixture f=new Fixture()) {
            f.provider.changeAt=2;f.provider.changed="external newer".getBytes(StandardCharsets.UTF_8);
            try{f.save();throw new AssertionError("post-preparation conflict ignored");}catch(SaveCoordinator.ConflictException e){check(e.transaction!=null,"conflict retains prepared versions");}
            check(f.provider.writes==0,"external post-preparation no writes");bytes(f.provider.changed,f.provider.value,"external source intact");pass("source change after preparation");
        }
        for(int boundary=1;boundary<=9;boundary++)try(Fixture f=new Fixture()) {
            f.disk.failAt=boundary;SaveCoordinator.SaveFailure e=failure(f);
            check(f.provider.writes==0,"protection boundary "+boundary+" prevents writes");check(!e.writeMayHaveOccurred,"preparation not uncertain provider");pass("protection fault "+boundary);
        }
        try(Fixture f=new Fixture()) {
            new SaveCoordinator(f.store).save(f.request(null,null,true),f.provider);check(f.provider.writes==1,"new destination allowed without baseline");pass("new destination protects its existing bytes");
        }
        for(int boundary=1;boundary<=3;boundary++)try(Fixture f=new Fixture()) {
            final int point=boundary;f.provider.afterWrite=()->{f.disk.calls=0;f.disk.failAt=point;};SaveCoordinator.SaveFailure e=failure(f);
            check(f.provider.writes==1&&e.writeMayHaveOccurred,"completion failure uncertain, no retry");
            ProtectionStore reopened=new ProtectionStore(f.root.resolve("protection").toFile(),new Disk(),64L*1024*1024);
            bytes(ORIGINAL,reopened.transactions().get(0).original.bytes(),"completion-failed original");
            bytes(EDITED,reopened.transactions().get(0).attempted.bytes(),"completion-failed edit");pass("completion fault "+boundary);
        }
        for(byte[] invalid:new byte[][]{new byte[8388608+1],new byte[]{(byte)0xc0,(byte)0xaf}})try(Fixture f=new Fixture()) {
            f.provider.value=invalid;SaveCoordinator.SaveFailure e=failure(f);
            check(f.provider.writes==0&&!e.writeMayHaveOccurred,"invalid source no mutation");
            check(e.attempted!=null&&f.store.snapshots().size()==1,"invalid source retains edit");pass("invalid/oversized source rejected");
        }
        try(Fixture f=new Fixture()) {
            f.provider.afterRecheck=()->{
                try {String id=f.store.transactions().get(0).original.id;Files.write(f.root.resolve("protection").resolve(id+".snapshot"),new byte[]{0});}
                catch(IOException e){throw new RuntimeException(e);}
            };
            SaveCoordinator.SaveFailure e=failure(f);
            check(!e.writeMayHaveOccurred&&f.provider.writes==0,"corrupt protection prevents provider invocation");pass("protection invalidated during source recheck");
        }
        for(byte[] invalid:new byte[][]{new byte[8388608+1],new byte[]{(byte)0xc0,(byte)0xaf}})try(Fixture f=new Fixture()) {
            SaveCoordinator.Request r=new SaveCoordinator.Request("document-1","content://fixture/document","Synthetic.md",7,invalid,EditorTools.sha256(ORIGINAL),null,false);
            try{new SaveCoordinator(f.store).save(r,f.provider);throw new AssertionError("invalid attempt allowed");}catch(IOException expected){check(f.provider.reads==0&&f.provider.writes==0,"invalid attempt no provider call");}
            pass("invalid/oversized attempt rejected");
        }
        for(String suffix:new String[]{"😀雪", "é🧭\r\n"})try(Fixture f=new Fixture()) {
            byte[] payload=("a".repeat(8191)+suffix).getBytes(StandardCharsets.UTF_8);
            f.provider.expectedAttempt=payload.clone();
            SaveCoordinator.Request r=new SaveCoordinator.Request("document-1","content://fixture/document","Synthetic.md",7,payload,EditorTools.sha256(ORIGINAL),null,false);
            payload[0]='x';
            new SaveCoordinator(f.store).save(r,f.provider);
            bytes(f.provider.expectedAttempt,f.provider.value,"chunk boundary UTF8 and immutable captured edit");pass("bounded UTF8 validation preserves Unicode");
        }
        try(Fixture f=new Fixture()) {
            byte[] payload=new byte[8194];Arrays.fill(payload,(byte)'a');payload[8191]=(byte)0xf0;payload[8192]=(byte)0x9f;payload[8193]=(byte)0x98;
            SaveCoordinator.Request r=new SaveCoordinator.Request("document-1","content://fixture/document","Synthetic.md",7,payload,EditorTools.sha256(ORIGINAL),null,false);
            try{new SaveCoordinator(f.store).save(r,f.provider);throw new AssertionError("truncated UTF8 allowed");}catch(IOException expected){check(f.provider.reads==0&&f.provider.writes==0,"truncated chunk no provider access");}
            pass("truncated UTF8 at buffer boundary rejected");
        }
        try(Fixture f=new Fixture()) {
            SaveCoordinator.Request original=f.request("stale",null,false);
            SaveCoordinator.Request consent=original.withConsent(EditorTools.sha256(ORIGINAL));
            check(consent.revision==original.revision&&consent.documentId.equals(original.documentId)&&consent.uri.equals(original.uri),"consent retains revision and identity");
            check(consent.baselineHash.equals(original.baselineHash)&&original.consentHash==null,"consent doesn't alter original request");
            new SaveCoordinator(f.store).save(consent,f.provider);
            bytes(EDITED,f.provider.value,"consent retains originally captured edit");pass("consent replaces only observed hash");
        }
        System.out.println("PASS "+cases+" production coordinator cases, "+checks+" assertions; host boundaries, Android NOT RUN");
    }
}
