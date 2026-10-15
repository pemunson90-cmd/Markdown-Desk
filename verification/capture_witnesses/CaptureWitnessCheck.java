package com.example.markdowndesk;

import java.io.*;
import java.lang.reflect.*;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Independent byte encodings against actual registered backends, not source-string checks. */
public final class CaptureWitnessCheck {
    static int groups;
    static Class<?> inputType;
    interface Work {void run()throws Exception;}
    static void yes(boolean value,String why){if(!value)throw new AssertionError(why);}
    static void reject(Work w,String why)throws Exception {try{w.run();throw new AssertionError("accepted "+why);}catch(IOException expected){}}
    static Object call(Object target,Class<?> type,String name,Class<?>[] types,Object... args)throws Exception {
        try {Method m=type.getDeclaredMethod(name,types);m.setAccessible(true);return m.invoke(target,args);}
        catch(InvocationTargetException e){Throwable c=e.getCause();if(c instanceof Exception)throw (Exception)c;throw (Error)c;}
    }
    static Object input(HistoryStorage.Budget budget,int maximum)throws Exception {
        return call(null,inputType,"create",new Class<?>[]{HistoryStorage.Budget.class,int.class},budget,maximum);
    }
    static long length(Object in)throws Exception{return (Long)call(in,inputType,"length",new Class<?>[0]);}
    static String hash(Object in)throws Exception{return (String)call(in,inputType,"hash",new Class<?>[0]);}
    static InputStream open(Object in)throws Exception{return (InputStream)call(in,inputType,"open",new Class<?>[0]);}
    static byte[] bytes(Object in)throws Exception {
        try(InputStream r=open(in);ByteArrayOutputStream out=new ByteArrayOutputStream()){
            byte[] scratch=new byte[4096];int n;while((n=r.read(scratch))!=-1)out.write(scratch,0,n);return out.toByteArray();
        }
    }
    static void c0(HistoryControlStore.Inspection c,HistoryStorage.Budget b,Object in)throws Exception {
        call(null,PrivateCapturePlan.class,"writeInitialControlInput",new Class<?>[]{HistoryControlStore.Inspection.class,HistoryStorage.Budget.class,inputType},c,b,in);
    }
    static void ledger(PrivateOperationStore.Ledger l,HistoryControlStore.Inspection c,HistoryStorage.Budget b,Object in)throws Exception {
        call(null,PrivateOperationStore.class,"writeOriginalInput",new Class<?>[]{PrivateOperationStore.Ledger.class,HistoryControlStore.Inspection.class,HistoryStorage.Budget.class,inputType},l,c,b,in);
    }
    static void nativeInput(NativeCaptureFingerprint n,HistoryStorage.Budget b,Object in)throws Exception {
        call(n,NativeCaptureFingerprint.class,"writeOriginalInput",new Class<?>[]{HistoryStorage.Budget.class,inputType},b,in);
    }
    static HistoryGraph.Graph graph(HistoryGraph scanner,HistoryStorage.Budget b,Object in)throws Exception {
        return (HistoryGraph.Graph)call(scanner,HistoryGraph.class,"scan",new Class<?>[]{HistoryStorage.Budget.class,inputType},b,in);
    }
    static String sha(byte[] bytes)throws IOException{return HistoryStorage.hex(HistoryStorage.digest().digest(bytes));}
    static void text(DataOutputStream o,String v)throws IOException{if(v==null){o.writeInt(-1);return;}byte[] a=v.getBytes(StandardCharsets.UTF_8);o.writeInt(a.length);o.write(a);}
    static byte[] rawHash(String s){byte[] r=new byte[32];for(int i=0;i<32;i++)r[i]=(byte)Integer.parseInt(s.substring(i*2,i*2+2),16);return r;}
    static final class Disk implements ProtectionStore.Durability {
        public void syncFile(FileDescriptor fd)throws IOException{fd.sync();}
        public void move(File a,File b)throws IOException{Files.move(a.toPath(),b.toPath(),StandardCopyOption.ATOMIC_MOVE);}
        public void syncDirectory(File d)throws IOException{try(FileChannel c=FileChannel.open(d.toPath(),StandardOpenOption.READ)){c.force(true);}}
    }
    static final class F implements AutoCloseable {
        final Path parent=Files.createTempDirectory("witness-");final Disk disk=new Disk();
        final ProtectionStore store=new ProtectionStore(parent.resolve("store").toFile(),disk,64L<<20);
        final HistoryCatalog catalog=new HistoryCatalog(parent.resolve("catalog").toFile(),store,disk);
        final HistoryIdentity identity=new HistoryIdentity(parent.resolve("identity").toFile(),disk);
        final HistoryIndex index=new HistoryIndex(store,catalog,identity,parent.resolve("index").toFile(),disk);
        final HistoryCapacity capacity=HistoryCapacity.register(store,catalog,identity,index,new HistoryPolicy(64L<<20,20),disk);
        final HistoryStorage.Budget budget=new HistoryStorage.Budget(HistoryStorage.METADATA_BUDGET);
        F()throws Exception{}
        void locked(Work w)throws Exception{synchronized(HistoryStorage.monitorFor(store.storageRoot())){try(HistoryStorage.MutationScope lease=HistoryStorage.administration(store.storageRoot())){w.run();}}}
        HistoryControlStore.Inspection control()throws IOException{return new HistoryControlStore(parent.toFile()).inspect();}
        HistoryGraph scanner(HistoryPolicy p)throws IOException{return new HistoryGraph(store,catalog,identity,index,p);}
        public void close()throws IOException{try(java.util.stream.Stream<Path> p=Files.walk(parent)){for(Path x:(Iterable<Path>)p.sorted(Comparator.reverseOrder())::iterator)Files.delete(x);}}
    }
    static byte[] expectedControl(HistoryControlStore.Inspection c)throws Exception {
        ByteArrayOutputStream b=new ByteArrayOutputStream();DataOutputStream o=new DataOutputStream(b);
        o.write("MDOP-initial-control-v1\n".getBytes(StandardCharsets.US_ASCII));o.writeInt(c.entries.size());
        for(HistoryControlStore.Entry e:c.entries){text(o,e.filename);o.writeLong(e.length);o.write(rawHash(e.envelopeHash));o.writeBoolean(e.stage);}return b.toByteArray();
    }
    static byte[] expectedGraph(F f,HistoryPolicy p,HistoryControlStore.Inspection c,HistoryGraph.Graph g,PrivateOperationStore.Ledger l)throws Exception {
        ByteArrayOutputStream b=new ByteArrayOutputStream();DataOutputStream o=new DataOutputStream(b);
        text(o,"read-only-history-graph-v1");for(String n:new String[]{"store","catalog","identity","index"})text(o,n);
        o.writeLong(64L<<20);o.writeBoolean(p!=null);if(p!=null){o.writeLong(p.quotaBytes);o.writeInt(p.automaticCount);}
        o.writeBoolean(true);o.writeLong(c.actualBytes);o.writeInt(c.actualEntries);o.writeBoolean(c.usageComplete);text(o,c.state.name());
        for(HistoryControlStore.Entry e:c.entries){text(o,e.filename);o.writeLong(e.length);text(o,e.envelopeHash);o.writeBoolean(e.stage);}
        o.writeBoolean(true);HistoryCapacity.EffectivePolicy e=g.effectivePolicy;text(o,e.bundleId);o.writeLong(e.generation);text(o,e.controlRecordHash);o.writeLong(e.quotaBytes);o.writeInt(e.automaticCount);
        o.writeLong(g.actualBytes);o.writeLong(g.reservedCompletionBytes);o.writeLong(g.reservedCompletionSlots);o.writeBoolean(g.usageComplete);o.writeInt(g.records.size());
        for(ProtectionStore.StoredRecord r:g.records){text(o,r.rootRole);text(o,r.filename);text(o,r.id);text(o,r.kind);o.writeLong(r.serializedBytes);text(o,r.envelopeHash);}
        for(ProtectionStore.Transaction t:g.transactions){text(o,t.id);text(o,t.original.id);text(o,t.attempted.id);o.writeBoolean(t.verified);}
        for(String d:g.diagnostics)text(o,d);
        if(l.present){text(o,"operation-ledger-binding-v1");text(o,l.fingerprint);}return b.toByteArray();
    }
    static byte[] expectedLedger(PrivateOperationStore.Ledger l,HistoryControlStore.Inspection c)throws Exception {
        ByteArrayOutputStream b=new ByteArrayOutputStream();DataOutputStream o=new DataOutputStream(b);if(!l.present)return b.toByteArray();
        o.writeUTF("private-operation-ledger-v1");for(Map.Entry<String,String> e:l.controlIdentities.entrySet()){o.writeUTF(e.getKey());o.writeUTF(e.getValue());}for(Map.Entry<String,String> e:l.entries.entrySet()){o.writeUTF(e.getKey());o.writeUTF(e.getValue());}
        for(HistoryControlStore.Entry e:c.entries)if(PrivateOperationStore.operationEntry(e)){o.writeUTF(e.filename);o.writeLong(e.length);o.writeUTF(e.envelopeHash==null?"":e.envelopeHash);o.writeBoolean(e.stage);}
        o.writeLong(l.actualBytes);o.writeLong(l.reservedBytes);o.writeLong(l.occupiedEntries);o.writeLong(l.reservedEntries);o.writeLong(c.controlReservedBytes);o.writeInt(c.controlReservedEntries);o.writeBoolean(l.complete);return b.toByteArray();
    }
    static byte[] expectedNative(F f,HistoryControlStore.Inspection c,HistoryGraph.Graph g)throws Exception {
        ByteArrayOutputStream b=new ByteArrayOutputStream();DataOutputStream o=new DataOutputStream(b);
        text(o,"native-capture-sources-v1");text(o,c.registration.bundleId.toString());text(o,c.registration.envelopeHash);o.writeLong(64L<<20);o.writeLong(c.latest.generation);text(o,c.latest.envelopeHash);o.writeLong(c.latest.quotaBytes);o.writeInt(c.latest.automaticCount);
        int role=0;for(String n:new String[]{"store","catalog","identity","index"}){o.writeInt(++role);text(o,f.parent.resolve(n).toFile().getCanonicalPath());}
        o.writeLong(g.actualBytes-g.operationActualBytes);o.writeLong(g.reservedCompletionBytes);o.writeLong(g.reservedCompletionSlots);o.writeInt(g.records.size());
        List<ProtectionStore.StoredRecord> rows=new ArrayList<>(g.records);rows.sort(Comparator.comparingInt((ProtectionStore.StoredRecord r)->Arrays.asList("store","catalog","identity","index").indexOf(r.rootRole)).thenComparing(r->r.filename));
        for(ProtectionStore.StoredRecord r:rows){o.writeInt(HistoryArchiveManifest.Role.parse(r.rootRole).wireId);text(o,r.filename);o.writeLong(r.serializedBytes);text(o,r.envelopeHash);}return b.toByteArray();
    }
    static void canonicalProfiles()throws Exception {
        for(HistoryPolicy profile:new HistoryPolicy[]{null,new HistoryPolicy(123456789,7)})try(F f=new F()){
            f.locked(()->{
                HistoryGraph scanner=f.scanner(profile);String old=scanner.scan().fingerprint;
                Object gi=input(f.budget,65536);HistoryGraph.Graph g=graph(scanner,f.budget,gi);yes(g.complete,"complete graph");yes(old.equals(g.fingerprint),"old graph hash unchanged");
                HistoryControlStore.Inspection c=f.control();PrivateOperationStore.Ledger l=PrivateOperationStore.inspect(f.store.storageRoot(),c,f.budget);
                yes(Arrays.equals(bytes(gi),expectedGraph(f,profile,c,g,l)),"independent exact graph/profile bytes");yes(hash(gi).equals(g.fingerprint),"graph digest");
                Object ci=input(f.budget,65536);c0(c,f.budget,ci);yes(Arrays.equals(bytes(ci),expectedControl(c)),"exact C0 bytes");yes(hash(ci).equals(PrivateCapturePlan.initialControlHash(c)),"C0 unchanged digest");
                Object li=input(f.budget,0);ledger(l,c,f.budget,li);yes(length(li)==0&&bytes(li).length==0,"actual absent input empty");
                yes(PrivateCapturePlan.initialLedgerHash(l).equals("d85d1350026fd0c27784a3126cd3adff553265dc45b173b353b13c049645825e"),"absent conversion golden");yes(!hash(li).equals(PrivateCapturePlan.initialLedgerHash(l)),"empty raw integrity is not binding");
                PrivateOperationStore.Ledger fake=new PrivateOperationStore.Ledger();Object fi=input(f.budget,0);reject(()->ledger(fake,c,f.budget,fi),"uninspected absent DTO");reject(()->open(fi),"failed absence sealed invalid");
                HistoryArchiveManifest map=HistoryArchiveManifest.freeze(g,c,UUID::randomUUID);NativeCaptureFingerprint n=NativeCaptureFingerprint.capture(map,g,c,f.store.storageRoot(),f.catalog.storageRoot(),f.identity.storageRoot(),f.index.storageRoot(),64L<<20,f.budget);
                Object ni=input(f.budget,65536);nativeInput(n,f.budget,ni);yes(Arrays.equals(bytes(ni),expectedNative(f,c,g)),"exact native bytes");yes(hash(ni).equals(n.fingerprint),"native digest unchanged");
                HistoryStorage.Budget foreign=new HistoryStorage.Budget(1L<<20);Object wrong=input(foreign,1024);reject(()->nativeInput(n,foreign,wrong),"native original budget");reject(()->open(wrong),"foreign failure invalidates");
                reject(()->c0(c,f.budget,ci),"sealed witness reuse");
            });groups++;
        }
    }
    static void populatedNative()throws Exception {
        try(F f=new F()){
            ProtectionStore.Snapshot a=f.store.putSnapshot("tab","content://witness","old",1,new byte[]{1},"original"),b=f.store.putSnapshot("tab","content://witness","new",1,new byte[]{2},"attempted");
            ProtectionStore.Transaction t=f.store.prepare(a,b,a.hash);f.store.complete(t);String h=f.identity.resolve("content://witness","tab");f.index.recordVerified(h,t.id);f.catalog.name(b.id,"named");f.store.prepare(a,b,a.hash);
            f.locked(()->{
                HistoryGraph scanner=f.scanner(null);String old=scanner.scan().fingerprint;Object gi=input(f.budget,65536);HistoryGraph.Graph g=graph(scanner,f.budget,gi);yes(g.complete&&g.reservedCompletionBytes>0,"native reservation owner retained");
                HistoryControlStore.Inspection c=f.control();PrivateOperationStore.Ledger l=PrivateOperationStore.inspect(f.store.storageRoot(),c,f.budget);
                yes(old.equals(g.fingerprint)&&Arrays.equals(bytes(gi),expectedGraph(f,null,c,g,l)),"populated exact graph records/typed edges bytes");
                HistoryArchiveManifest map=HistoryArchiveManifest.freeze(g,c,UUID::randomUUID);NativeCaptureFingerprint n=NativeCaptureFingerprint.capture(map,g,c,f.store.storageRoot(),f.catalog.storageRoot(),f.identity.storageRoot(),f.index.storageRoot(),64L<<20,f.budget);Object ni=input(f.budget,65536);nativeInput(n,f.budget,ni);
                yes(Arrays.equals(bytes(ni),expectedNative(f,c,g))&&hash(ni).equals(n.fingerprint),"populated exact native source tuples");
            });groups++;
        }
    }
    static void cancelledStage()throws Exception {
        try(F f=new F()){
            HistoryControlStore.Inspection initial=f.control();String z=String.join("",Collections.nCopies(64,"0"));UUID prep=UUID.randomUUID();
            PrivateOperationRecord.Binding b=new PrivateOperationRecord.Binding(prep,1,initial.registration.bundleId,initial.registration.envelopeHash,initial.latest.generation,initial.latest.envelopeHash,64L<<20,64L<<20,20,initial.registration.roles,z,z,0);
            PrivateOperationRecord.Artifact m=new PrivateOperationRecord.Artifact(1,UUID.randomUUID(),PrivateOperationRecord.ZERO,0,512,z);PrivateOperationRecord d=PrivateOperationRecord.descriptor(b,m,Collections.emptyList());PrivateOperationRecord.Artifact dr=new PrivateOperationRecord.Artifact(2,UUID.randomUUID(),PrivateOperationRecord.ZERO,0,d.encoded().length,d.envelopeHash);
            HistoryPinRecord begin=HistoryPinRecord.begin(UUID.randomUUID(),initial.registration,initial.latest,HistoryPinRecord.Purpose.EXPORT,HistoryPinRecord.Scope.ALL_BUNDLE,new HistoryPinRecord.Manifest(HistoryPinRecord.Domain.LOCAL_CAPTURE_V1,0,d.encoded().length,d.envelopeHash),Collections.emptyList());PrivateOperationRecord p=PrivateOperationRecord.prepare(b,3,Arrays.asList(m,dr),begin);
            Path control=f.parent.resolve("history_control"),dir=f.parent.resolve("private_operations/"+prep);Files.createDirectories(dir);Path stage=dir.resolve(m.filename()+".stage");Files.write(stage,new byte[]{1,2,3});Files.write(control.resolve(p.name()),p.encoded());Files.write(control.resolve(begin.name()),HistoryPinRecord.encode(begin));Files.write(control.resolve("pin-"+begin.operation+".close"),HistoryPinRecord.close(begin,HistoryPinRecord.Outcome.CANCELLED));f.capacity.reconcile();HistoryPins.open(f.store,f.catalog,f.identity,f.index,f.disk);
            f.locked(()->{
                HistoryControlStore.Inspection c=f.control();PrivateOperationStore.Ledger l=PrivateOperationStore.inspect(f.store.storageRoot(),c,f.budget);yes(l.complete&&l.pending&&l.reservedBytes>0,"historical complete/pending with remaining credit");
                long credit=l.reservedBytes;Object li=input(f.budget,65536);ledger(l,c,f.budget,li);yes(Arrays.equals(bytes(li),expectedLedger(l,c)),"exact old controls/identities/stage/credits bytes");yes(hash(li).equals(l.fingerprint),"present ledger digest");
                HistoryStorage.Budget foreign=new HistoryStorage.Budget(1L<<20);Object foreignInput=input(foreign,65536);reject(()->ledger(l,c,foreign,foreignInput),"ledger original budget");
                Object wrongControl=input(f.budget,65536);reject(()->ledger(l,f.control(),f.budget,wrongControl),"different control snapshot");
                Object tooSmall=input(f.budget,0);reject(()->ledger(l,c,f.budget,tooSmall),"absent witness for present ledger");reject(()->open(tooSmall),"failed present output invalidated");
                Object forged=input(f.budget,0);PrivateOperationStore.Ledger absent=new PrivateOperationStore.Ledger();reject(()->ledger(absent,c,f.budget,forged),"present control cannot accept absent DTO");
                HistoryGraph scanner=f.scanner(null);String old=scanner.scan().fingerprint;Object gi=input(f.budget,65536);HistoryGraph.Graph g=graph(scanner,f.budget,gi);yes(g.complete,"historical complete: "+g.diagnostics);yes(g.pruneBlocked,"historical pruning block");yes(old.equals(g.fingerprint),"historical graph compatibility "+old+" / "+g.fingerprint);yes(Arrays.equals(bytes(gi),expectedGraph(f,null,c,g,l)),"historical graph exact bytes");
                yes(l.reservedBytes==credit&&Arrays.equals(Files.readAllBytes(stage),new byte[]{1,2,3}),"emission changed no old data/credit");
            });groups++;
        }
    }
    static void budgetAndClosure()throws Exception {
        int size=65537;long paid=(256L<<10)+size+128+256;HistoryStorage.Budget exact=new HistoryStorage.Budget(paid);Object paged=input(exact,size);
        DataOutputStream writer=(DataOutputStream)call(paged,inputType,"writer",new Class<?>[]{HistoryStorage.Budget.class},exact);byte[] data=new byte[size];for(int i=0;i<size;i++)data[i]=(byte)i;writer.write(data);
        call(paged,inputType,"seal",new Class<?>[]{String.class},sha(data));try(InputStream r=open(paged)){byte[] dest=new byte[70000];yes(r.read(dest)==65536&&r.read(dest,65536,1)==1&&r.read()==-1,"page-boundary EOF and <=64KiB reads");yes(Arrays.equals(data,Arrays.copyOf(dest,size)),"page boundary exact bytes");}
        ((AutoCloseable)paged).close();reject(()->exact.charge(1),"original debit not refunded after source/reader close");
        HistoryStorage.Budget bounded=new HistoryStorage.Budget(1L<<20);Object failed=input(bounded,1);DataOutputStream failWriter=(DataOutputStream)call(failed,inputType,"writer",new Class<?>[]{HistoryStorage.Budget.class},bounded);failWriter.write(7);
        reject(()->failWriter.write(8),"leaf output bound");reject(()->call(failed,inputType,"seal",new Class<?>[]{String.class},sha(new byte[]{7})),"failed leaf cannot seal accepted prefix");reject(()->open(failed),"failed leaf sealed invalid");
        HistoryStorage.Budget tiny=new HistoryStorage.Budget(4096);reject(()->input(tiny,1),"precharge includes overhead beyond payload");reject(()->tiny.charge(4097),"failed charge grants no allowance");
        try(F f=new F()){
            HistoryStorage.Budget small=new HistoryStorage.Budget(1L<<20);Object gi=input(small,1024);
            f.locked(()->reject(()->graph(f.scanner(null),small,gi),"shared graph control reserve, not fresh allowance"));reject(()->open(gi),"failed graph invalidates sink");
            Object ci=input(f.budget,0);f.locked(()->reject(()->c0(f.control(),f.budget,ci),"C0 bounded output"));reject(()->open(ci),"C0 failure no partial read");
            Object good=input(f.budget,65536);f.locked(()->c0(f.control(),f.budget,good));InputStream r=open(good);reject(()->open(good),"only prepaid active reader");r.close();reject(()->r.read(),"reader really closed");
            ((AutoCloseable)good).close();reject(()->open(good),"source closed");
            HistoryStorage.Budget other=new HistoryStorage.Budget(1L<<20);Object wi=input(other,1024);f.locked(()->reject(()->c0(f.control(),f.budget,wi),"writer budget binding"));reject(()->open(wi),"writer mismatch invalidates");
        }groups++;
    }
    public static void main(String[] args)throws Exception {
        try{inputType=Class.forName("com.example.markdowndesk.PrivateCapturePlan$HashInput");}
        catch(ClassNotFoundException absent){throw new AssertionError("Missing bounded canonical witness API",absent);}
        canonicalProfiles();populatedNative();cancelledStage();budgetAndClosure();System.out.println("Capture witnesses: "+groups+" focused groups PASS");
    }
}
