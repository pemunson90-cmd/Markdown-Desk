package com.example.markdowndesk;

import java.io.*;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.util.*;

/** Native PREPARED sources and actual shared-control evidence, without runtime consumers. */
public final class HistoryPinsCheck {
    static int checks;
    static void check(boolean condition,String reason){checks++;if(!condition)throw new AssertionError(reason);}
    static class Disk implements ProtectionStore.Durability {
        Hook hook;interface Hook{void call(String event,File file)throws IOException;}
        void event(String name,File file)throws IOException{if(hook!=null)hook.call(name,file);}
        public void syncFile(FileDescriptor fd)throws IOException{event("file-before",null);fd.sync();event("file-after",null);}
        public void move(File a,File b)throws IOException{event("move-before",a);Files.move(a.toPath(),b.toPath(),StandardCopyOption.ATOMIC_MOVE);event("move-after",b);}
        public void syncDirectory(File directory)throws IOException{event("dir-before",directory);try(FileChannel channel=FileChannel.open(directory.toPath(),StandardOpenOption.READ)){channel.force(true);}event("dir-after",directory);}
    }
    static final class Fixture {
        final Path parent=Files.createTempDirectory("history-pins-");final Disk disk=new Disk();
        final ProtectionStore store=new ProtectionStore(parent.resolve("store").toFile(),disk,64L*1024*1024);
        final HistoryCatalog catalog=new HistoryCatalog(parent.resolve("catalog").toFile(),store,disk);
        final HistoryIdentity identity=new HistoryIdentity(parent.resolve("identity").toFile(),disk);
        final HistoryIndex index=new HistoryIndex(store,catalog,identity,parent.resolve("index").toFile(),disk);
        final ProtectionStore.Snapshot original=store.putSnapshot("tab","uri","title",1,new byte[]{1,2,3},"original");
        final ProtectionStore.Snapshot attempted=store.putSnapshot("tab","uri","title",1,new byte[]{4,5},"attempted");
        final ProtectionStore.Transaction pending=store.prepare(original,attempted,original.hash);
        final HistoryCapacity capacity=HistoryCapacity.register(store,catalog,identity,index,new HistoryPolicy(64L*1024*1024,20),disk);
        Fixture()throws Exception{}
    }
    static void uuid(DataOutputStream out,UUID id)throws IOException{out.writeLong(id.getMostSignificantBits());out.writeLong(id.getLeastSignificantBits());}
    static byte[] hash(String value){byte[] result=new byte[32];for(int i=0;i<32;i++)result[i]=(byte)Integer.parseInt(value.substring(i*2,i*2+2),16);return result;}
    static void discovery()throws Exception {
        Fixture f=new Fixture();HistoryControlStore.Inspection initial=new HistoryControlStore(f.parent.toFile()).inspect();
        check(f.store.transactions().size()==1&&!f.store.transactions().get(0).verified,"legitimate PREPARED recovery input");
        ProtectionStore.StoredRecord source=null;for(ProtectionStore.StoredRecord r:f.store.inspect().records)if(r.id.equals(f.original.id)&&r.kind.equals("snapshot"))source=r;
        UUID operation=UUID.randomUUID();ByteArrayOutputStream body=new ByteArrayOutputStream();DataOutputStream b=new DataOutputStream(body);
        uuid(b,operation);uuid(b,initial.registration.bundleId);b.write(hash(initial.registration.envelopeHash));b.writeLong(initial.latest.generation);b.write(hash(initial.latest.envelopeHash));
        b.writeByte(2);b.writeByte(1);b.writeByte(1);b.writeLong(0);b.writeLong(0);b.write(HistoryStorage.digest().digest(new byte[0]));b.writeInt(1);
        b.writeByte(1);b.writeByte(1);uuid(b,UUID.fromString(source.id));b.writeLong(source.serializedBytes);b.write(hash(source.envelopeHash));
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();DataOutputStream out=new DataOutputStream(bytes);out.writeInt(0x4d44504e);out.writeInt(1);out.writeByte(1);out.writeInt(body.size());body.writeTo(out);out.write(HistoryStorage.digest().digest(bytes.toByteArray()));
        Files.write(f.parent.resolve("history_control/pin-"+operation+".begin"),bytes.toByteArray());
        HistoryControlStore.Inspection inspection=new HistoryControlStore(f.parent.toFile()).inspect();
        check(inspection.chainComplete&&inspection.state==HistoryControlStore.State.VALIDATED,"typed pin BEGIN preserves ready policy authority while freezing pruning");
    }
    interface Action {void run()throws Exception;}
    static void rejects(Action action,String reason)throws Exception {try{action.run();throw new AssertionError(reason);}catch(IOException expected){checks++;}}
    static HistoryControlStore.Inspection inspect(Fixture f)throws IOException{return new HistoryControlStore(f.parent.toFile()).inspect();}
    static HistoryPinRecord record(Fixture f)throws Exception {
        HistoryControlStore.Inspection c=inspect(f);ProtectionStore.StoredRecord r;
        r=null;for(ProtectionStore.StoredRecord source:f.store.inspect().records)if(source.kind.equals("snapshot")&&source.id.equals(f.original.id))r=source;
        return HistoryPinRecord.begin(UUID.randomUUID(),c.registration,c.latest,HistoryPinRecord.Purpose.RESTORE,HistoryPinRecord.Scope.EXACT_SUBSET,
            new HistoryPinRecord.Manifest(HistoryPinRecord.Domain.NATIVE_HISTORY_V1,0,0,HistoryStorage.hex(HistoryStorage.digest().digest(new byte[0]))),
            Collections.singletonList(new HistoryPinRecord.Source(r.id,r.serializedBytes,r.envelopeHash)));
    }
    static void codec()throws Exception {
        Fixture f=new Fixture();HistoryPinRecord begin=record(f);byte[] encoded=HistoryPinRecord.encode(begin);
        check(Arrays.equals(encoded,HistoryPinRecord.encode(HistoryPinRecord.decode(encoded))),"canonical BEGIN roundtrip");
        for(HistoryPinRecord.Outcome outcome:HistoryPinRecord.Outcome.values()){
            byte[] close=HistoryPinRecord.close(begin,outcome);check(close.length==126,"fixed CLOSE reservation");
            check(HistoryPinRecord.matches(begin,HistoryPinRecord.decode(close)),"exact receipt binding");
            for(int n=0;n<close.length;n++){final byte[] truncated=Arrays.copyOf(close,n);rejects(()->HistoryPinRecord.decode(truncated),"truncated close");}
        }
        for(int i=0;i<encoded.length;i++){byte[] changed=encoded.clone();changed[i]^=1;rejects(()->HistoryPinRecord.decode(changed),"corrupt BEGIN");}
        rejects(()->HistoryPinRecord.decode(new byte[16385]),"complete byte bound");
        byte[] unsupported=encoded.clone();unsupported[173]=2; // first descriptor kind, after canonical header/body fields
        byte[] digest=HistoryStorage.digest().digest(Arrays.copyOf(unsupported,unsupported.length-32));System.arraycopy(digest,0,unsupported,unsupported.length-32,32);
        rejects(()->HistoryPinRecord.decode(unsupported),"unsupported descriptor framing");
        List<HistoryPinRecord.Source> many=new ArrayList<>();for(int i=0;i<257;i++)many.add(new HistoryPinRecord.Source(new UUID(0,i).toString(),45,begin.sources.get(0).hash));
        HistoryControlStore.Inspection c=inspect(f);
        rejects(()->HistoryPinRecord.begin(UUID.randomUUID(),c.registration,c.latest,begin.purpose,begin.scope,begin.manifest,many),"descriptor bound");
        check(HistoryPinRecord.knownName(begin.name())&&!HistoryPinRecord.knownName(begin.name()+".junk"),"fixed filenames");
    }
    static void reservation()throws Exception {
        Fixture f=new Fixture();HistoryPinRecord begin=record(f);Path committed=f.parent.resolve("history_control/"+begin.name());Files.write(committed,HistoryPinRecord.encode(begin));
        HistoryControlStore.Inspection initial=inspect(f);check(initial.pins.reservedBytes==126&&initial.pins.reservedEntries==1,"terminal credit before close");
        byte[] close=HistoryPinRecord.close(begin,HistoryPinRecord.Outcome.CANCELLED);Path stage=f.parent.resolve("history_control/pin-"+begin.operation+".close.stage");
        for(int n=0;n<=close.length;n++){
            Files.write(stage,Arrays.copyOf(close,n));HistoryControlStore.Inspection c=inspect(f);
            check(c.pins.valid&&c.pins.reservedBytes==126-n&&c.pins.reservedEntries==0,"exact prefix credit "+n);
            check(c.actualBytes+c.pins.reservedBytes==initial.actualBytes+126&&c.actualEntries==initial.actualEntries+1,"constant charged prefix "+n);
            check(c.state==HistoryControlStore.State.BLOCKED,"no prefix auto readiness");
        }
        byte[] unknown=close.clone();unknown[0]^=1;Files.write(stage,unknown);check(!inspect(f).pins.valid,"unknown stage gets no authority");
        rejects(()->f.capacity.reconcile(),"ordinary authority never promotes pin stage");
    }
    public static void main(String[] args)throws Exception{discovery();codec();reservation();System.out.println("History pin codec/discovery: "+checks+" checks PASS");}
}
