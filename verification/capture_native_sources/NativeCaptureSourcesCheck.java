package com.example.markdowndesk;

import java.io.*;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.util.*;

/** Actual native backends, with explicit callback/lease and shared-budget fixtures. */
public final class NativeCaptureSourcesCheck {
    static int scenarios;
    interface Task {void run()throws Exception;}
    static void require(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    static void rejects(Task task,String why)throws Exception {
        try{task.run();throw new AssertionError("Accepted "+why);}catch(IOException expected){}
    }
    static final class Disk implements ProtectionStore.Durability {
        Task callback;
        public void syncFile(FileDescriptor fd)throws IOException{fd.sync();}
        public void move(File a,File b)throws IOException{Files.move(a.toPath(),b.toPath(),StandardCopyOption.ATOMIC_MOVE);}
        public void syncDirectory(File dir)throws IOException {
            try(FileChannel c=FileChannel.open(dir.toPath(),StandardOpenOption.READ)){c.force(true);}
            if(callback!=null)try{callback.run();}catch(IOException e){throw e;}catch(Exception e){throw new IOException(e);}
        }
    }
    static final class F implements AutoCloseable {
        final Path parent=Files.createTempDirectory("capture-native-");final Disk disk=new Disk();
        final ProtectionStore store=new ProtectionStore(parent.resolve("store").toFile(),disk,64L<<20);
        final HistoryCatalog catalog=new HistoryCatalog(parent.resolve("catalog").toFile(),store,disk);
        final HistoryIdentity identity=new HistoryIdentity(parent.resolve("identity").toFile(),disk);
        final HistoryIndex index=new HistoryIndex(store,catalog,identity,parent.resolve("index").toFile(),disk);
        final HistoryGraph scanner=new HistoryGraph(store,catalog,identity,index,null);
        final HistoryCapacity capacity;final HistoryGraph.Graph graph;final HistoryArchiveManifest map;
        F(boolean populated)throws Exception {
            if(populated){String history=identity.resolve("content://capture","tab");
                ProtectionStore.Snapshot a=store.putSnapshot("tab","content://capture","a",1,new byte[]{1,2},"original");
                ProtectionStore.Snapshot b=store.putSnapshot("tab","content://capture","b",1,new byte[]{3},"attempted");
                ProtectionStore.Transaction tx=store.prepare(a,b,a.hash);store.complete(tx);catalog.name(b.id,"named");index.recordVerified(history,tx.id);
                store.prepare(a,b,a.hash);
            }
            capacity=HistoryCapacity.register(store,catalog,identity,index,new HistoryPolicy(64L<<20,20),disk);
            graph=scanner.scan();require(graph.complete,"fixture graph complete");map=HistoryArchiveManifest.freeze(graph,control(),UUID::randomUUID);
        }
        File[] roots(){return new File[]{store.storageRoot(),catalog.storageRoot(),identity.storageRoot(),index.storageRoot()};}
        HistoryControlStore.Inspection control()throws IOException{return new HistoryControlStore(parent.toFile()).inspect();}
        HistoryCapacity.EffectivePolicy acknowledged(HistoryControlStore.Inspection c){return capacity.graphPolicy(c,roots());}
        NativeCaptureFingerprint capture(HistoryStorage.Budget budget)throws IOException {
            File[] r=roots();return NativeCaptureFingerprint.capture(map,graph,control(),r[0],r[1],r[2],r[3],64L<<20,budget);
        }
        NativeCaptureFingerprint capture()throws IOException {
            // Reserve concurrently retained graph/map/control *before* helper's own tables.
            HistoryStorage.Budget budget=new HistoryStorage.Budget(HistoryStorage.METADATA_BUDGET);
            budget.charge(1L<<20);return capture(budget);
        }
        void locked(Task task)throws Exception {
            synchronized(HistoryStorage.monitorFor(store.storageRoot())){
                try(HistoryStorage.MutationScope scope=HistoryStorage.administration(store.storageRoot())){task.run();}
            }
        }
        NativeCaptureFingerprint.PhysicalProof prove(NativeCaptureFingerprint fp)throws IOException {
            HistoryControlStore.Inspection c=control();return fp.prove(c,acknowledged(c));
        }
        void recheck(NativeCaptureFingerprint.PhysicalProof proof)throws IOException {
            HistoryControlStore.Inspection c=control();proof.recheck(c,acknowledged(c));
        }
        Path nativePath(){HistoryArchiveManifest.Entry e=map.entries.get(0);return parent.resolve(e.role.nativeName).resolve(e.nativeId+"."+e.kind.nativeName);}
        public void close()throws IOException{try(java.util.stream.Stream<Path> paths=Files.walk(parent)){for(Path p:(Iterable<Path>)paths.sorted(Comparator.reverseOrder())::iterator)Files.delete(p);}}
    }
    static void baselineAndBudget()throws Exception {
        try(F f=new F(true)){f.locked(()->{
            NativeCaptureFingerprint fp=f.capture();
            require(fp.nativeEnvelopeBytes==f.map.nativeEnvelopeBytes,"native bytes exclude operation data");
            require(fp.nativeReservedCompletionBytes==f.graph.reservedCompletionBytes&&fp.nativeReservedCompletionBytes>0,"exact native reservation bytes");
            require(fp.nativeReservedCompletionSlots==f.graph.reservedCompletionSlots,"exact native reservation slots");
            try(NativeCaptureFingerprint.PhysicalProof proof=f.prove(fp)){f.recheck(proof);rejects(()->f.prove(fp),"second live proof");}
            try(NativeCaptureFingerprint.PhysicalProof proof=f.prove(fp)){f.recheck(proof);}
            rejects(()->f.capture(new HistoryStorage.Budget(65535)),"scratch/tables budget before allocation");
            HistoryStorage.Budget shared=new HistoryStorage.Budget(HistoryStorage.METADATA_BUDGET);shared.charge(HistoryStorage.METADATA_BUDGET-1024);
            rejects(()->f.capture(shared),"caller concurrent owners exhaust same budget");
            HistoryArchiveManifest other=HistoryArchiveManifest.freeze(f.graph,f.control(),UUID::randomUUID);
            File[] r=f.roots();NativeCaptureFingerprint alternate=NativeCaptureFingerprint.capture(other,f.graph,f.control(),r[0],r[1],r[2],r[3],64L<<20,new HistoryStorage.Budget(HistoryStorage.METADATA_BUDGET));
            require(fp.fingerprint.equals(alternate.fingerprint),"outer archive IDs excluded");
        });rejects(f::capture,"capture without lease");scenarios++;}
        try(F f=new F(false)){f.locked(()->{NativeCaptureFingerprint fp=f.capture();require(fp.nativeEnvelopeBytes==0,"empty native map");try(NativeCaptureFingerprint.PhysicalProof p=f.prove(fp)){f.recheck(p);}});scenarios++;}
    }
    static void membership()throws Exception {
        for(String fault:new String[]{"new","missing","stage","unknown","directory","symlink","changed"}){
            try(F f=new F(true)){f.locked(()->{NativeCaptureFingerprint fp=f.capture();Path p=f.nativePath();
                switch(fault){
                    case "new":Files.copy(p,p.resolveSibling(UUID.randomUUID()+".snapshot"));break;
                    case "missing":Files.delete(p);break;
                    case "stage":Files.write(f.parent.resolve("store/"+UUID.randomUUID()+".stage"),new byte[0]);break;
                    case "unknown":Files.write(f.parent.resolve("index/unknown"),new byte[0]);break;
                    case "directory":Files.createDirectory(f.parent.resolve("catalog/new"));break;
                    case "symlink":Files.createSymbolicLink(f.parent.resolve("identity/alias"),p);break;
                    case "changed":byte[] b=Files.readAllBytes(p);b[b.length-1]^=1;Files.write(p,b);break;
                }
                rejects(()->f.prove(fp),fault+" native source");
            });scenarios++;}
        }
    }
    static void callbackIdentity()throws Exception {
        for(String role:new String[]{"native","registration","policy","enrollment","root"})try(F f=new F(true)){
            if(role.equals("policy"))f.capacity.setPolicy(new HistoryPolicy(128L<<20,10));
            f.locked(()->{
                // Use current initial complete evidence for the policy generation fixture.
                HistoryGraph.Graph graph=f.scanner.scan();HistoryArchiveManifest map=HistoryArchiveManifest.freeze(graph,f.control(),UUID::randomUUID);File[] r=f.roots();
                NativeCaptureFingerprint fp=NativeCaptureFingerprint.capture(map,graph,f.control(),r[0],r[1],r[2],r[3],64L<<20,new HistoryStorage.Budget(HistoryStorage.METADATA_BUDGET));
                NativeCaptureFingerprint.PhysicalProof proof=f.prove(fp);
                Path p=role.equals("native")?f.nativePath():role.equals("enrollment")?f.parent.resolve(HistoryControlStore.ENROLLMENT_NAME):role.equals("root")?f.parent.resolve("index"):f.parent.resolve("history_control/"+(role.equals("policy")?HistoryControlStore.policyFilename(1):HistoryControlStore.REGISTRATION_NAME));
                f.disk.callback=()->{Path moved=f.parent.resolve("replaced-"+UUID.randomUUID());Files.move(p,moved);if(role.equals("root")){Files.createDirectory(p);try(DirectoryStream<Path> entries=Files.newDirectoryStream(moved)){for(Path e:entries)Files.copy(e,p.resolve(e.getFileName()));}}else Files.copy(moved,p);};
                f.disk.syncDirectory(f.parent.toFile());f.disk.callback=null;
                rejects(()->f.recheck(proof),"same-byte callback replacement "+role);
                rejects(()->f.recheck(proof),"failed proof is invalid");proof.close();
                // No promise that a new proof remembers an inode from an earlier proof.
                try(NativeCaptureFingerprint.PhysicalProof fresh=f.prove(fp)){f.recheck(fresh);}
            });scenarios++;
        }
    }
    static void authority()throws Exception {
        try(F f=new F(true)){final NativeCaptureFingerprint[] saved={null};f.locked(()->saved[0]=f.capture());
            f.capacity.setPolicy(new HistoryPolicy(128L<<20,10));f.capacity.setPolicy(new HistoryPolicy(64L<<20,20));
            f.locked(()->rejects(()->f.prove(saved[0]),"A B A policy generation"));scenarios++;}
        try(F f=new F(true)){f.locked(()->{File[] r=f.roots();
            rejects(()->NativeCaptureFingerprint.capture(f.map,f.graph,f.control(),r[1],r[0],r[2],r[3],64L<<20,new HistoryStorage.Budget(HistoryStorage.METADATA_BUDGET)),"swapped registered roles");
            rejects(()->NativeCaptureFingerprint.capture(f.map,f.graph,f.control(),r[0],r[1],r[2],r[3],128L<<20,new HistoryStorage.Budget(HistoryStorage.METADATA_BUDGET)),"conflicting bootstrap");
            NativeCaptureFingerprint fp=f.capture();HistoryControlStore.Inspection c=f.control();
            rejects(()->fp.prove(c,null),"unacknowledged authority");
        });scenarios++;}
        try(F f=new F(true)){final NativeCaptureFingerprint[] saved={null};f.locked(()->saved[0]=f.capture());
            f.store.putSnapshot("tab","uri","later",4,new byte[]{7},"attempted");
            f.locked(()->rejects(()->f.prove(saved[0]),"legitimate native graph advance"));scenarios++;}
    }
    static void frozenAdmission()throws Exception {
        try(F f=new F(true)){
            f.store.putSnapshot("tab","uri","after freeze",1,new byte[]{9},"attempted");
            f.locked(()->{
                rejects(f::capture,"frozen initial graph/map omit fresh native member");
                File[] r=f.roots();HistoryGraph.Graph fresh=f.scanner.scan();
                rejects(()->NativeCaptureFingerprint.capture(f.map,fresh,f.control(),r[0],r[1],r[2],r[3],64L<<20,new HistoryStorage.Budget(HistoryStorage.METADATA_BUDGET)),"map historical graphHash differs from initial graph");
            });scenarios++;
        }
        try(F f=new F(true)){f.locked(()->{
            NativeCaptureFingerprint fp=f.capture();HistoryControlStore.Inspection captured=f.control();HistoryCapacity.EffectivePolicy ack=f.acknowledged(captured);
            Path policy=f.parent.resolve("history_control/"+HistoryControlStore.policyFilename(1)+".stage");
            Files.write(policy,HistoryControlStore.encodePolicy(captured.registration.bundleId,1,captured.latest.envelopeHash,128L<<20,10));
            rejects(()->fp.prove(captured,ack),"physical authority stage even with stale supplied inspection");
            Files.move(policy,policy.resolveSibling(HistoryControlStore.policyFilename(1)));
            rejects(()->fp.prove(captured,ack),"new committed authority member even with stale supplied inspection");
        });scenarios++;}
    }
    static void proofOwnership()throws Exception {
        try(F f=new F(true)){f.locked(()->{
            NativeCaptureFingerprint fp=f.capture();NativeCaptureFingerprint.PhysicalProof proof=f.prove(fp);
            final Throwable[] error={null};Thread foreign=new Thread(()->{
                try{f.recheck(proof);error[0]=new AssertionError("foreign proof recheck accepted");}
                catch(IOException expected){}catch(Throwable failure){error[0]=failure;}
            });foreign.start();foreign.join();if(error[0]!=null)throw new AssertionError(error[0]);
            // An unauthorized caller cannot release another thread's proof slot.
            rejects(()->f.prove(fp),"foreign rejected recheck must not free live proof");
            f.recheck(proof);proof.close();rejects(()->f.recheck(proof),"closed physical proof");
        });scenarios++;}
    }
    static void largePayload()throws Exception {
        try(F f=new F(false)){
            f.store.putSnapshot("tab","uri","large",1,new byte[ProtectionStore.MAX_DOCUMENT_BYTES],"attempted");
            f.locked(()->{HistoryGraph.Graph graph=f.scanner.scan();HistoryArchiveManifest map=HistoryArchiveManifest.freeze(graph,f.control(),UUID::randomUUID);File[] r=f.roots();
                HistoryStorage.Budget budget=new HistoryStorage.Budget(HistoryStorage.METADATA_BUDGET);budget.charge(HistoryStorage.METADATA_BUDGET-(128L<<10));
                NativeCaptureFingerprint fp=NativeCaptureFingerprint.capture(map,graph,f.control(),r[0],r[1],r[2],r[3],64L<<20,budget);
                require(fp.nativeEnvelopeBytes>(8L<<20),"fixture has full document plus envelope");
                for(int i=0;i<3;i++)try(NativeCaptureFingerprint.PhysicalProof p=f.prove(fp)){f.recheck(p);}
            });scenarios++;
        }
    }
    static void ownOperation()throws Exception {
        try(F f=new F(true)){f.locked(()->{
            NativeCaptureFingerprint fp=f.capture();HistoryControlStore.Inspection c=f.control();
            PrivateOperationRecord.Binding binding=new PrivateOperationRecord.Binding(UUID.randomUUID(),1,c.registration.bundleId,c.registration.envelopeHash,c.latest.generation,c.latest.envelopeHash,64L<<20,c.latest.quotaBytes,c.latest.automaticCount,c.registration.roles,fp.fingerprint,"00".repeat(32),0);
            ByteArrayOutputStream bytes=new ByteArrayOutputStream();f.map.write(bytes);byte[] mapBytes=bytes.toByteArray();
            PrivateOperationRecord.Artifact map=new PrivateOperationRecord.Artifact(1,UUID.randomUUID(),PrivateOperationRecord.ZERO,0,mapBytes.length,PrivateOperationRecord.sha(mapBytes));
            PrivateOperationRecord descriptor=PrivateOperationRecord.descriptor(binding,map,Collections.emptyList());byte[] descriptorBytes=descriptor.encoded();
            PrivateOperationRecord.Artifact desc=new PrivateOperationRecord.Artifact(2,UUID.randomUUID(),PrivateOperationRecord.ZERO,0,descriptorBytes.length,descriptor.envelopeHash);
            HistoryPinRecord begin=HistoryPinRecord.begin(UUID.randomUUID(),c.registration,c.latest,HistoryPinRecord.Purpose.EXPORT,HistoryPinRecord.Scope.ALL_BUNDLE,new HistoryPinRecord.Manifest(HistoryPinRecord.Domain.LOCAL_CAPTURE_V1,f.map.entries.size(),descriptorBytes.length,descriptor.envelopeHash),Collections.emptyList());
            PrivateOperationRecord prepare=PrivateOperationRecord.prepare(binding,3,Arrays.asList(map,desc),begin);
            try(NativeCaptureFingerprint.PhysicalProof proof=f.prove(fp)){
                Files.write(f.parent.resolve("history_control/"+prepare.name()),prepare.encoded());f.recheck(proof);
                Path dir=Files.createDirectories(f.parent.resolve("private_operations/"+binding.preparation));
                Files.write(dir.resolve(map.filename()+".stage"),Arrays.copyOf(mapBytes,13));f.recheck(proof);
                Files.write(dir.resolve(map.filename()+".stage"),mapBytes);Files.move(dir.resolve(map.filename()+".stage"),dir.resolve(map.filename()));
                Files.write(dir.resolve(desc.filename()),descriptorBytes);f.recheck(proof);
                require(!f.scanner.scan().complete,"pending operation graph remains incomplete");
                Files.write(f.parent.resolve("history_control/"+begin.name()),HistoryPinRecord.encode(begin));f.recheck(proof);
                PrivateOperationRecord link=PrivateOperationRecord.link(prepare);Files.write(f.parent.resolve("history_control/"+link.name()),link.encoded());f.recheck(proof);
                HistoryStorage.Budget budget=new HistoryStorage.Budget(HistoryStorage.METADATA_BUDGET);
                require(PrivateOperationStore.inspect(f.store.storageRoot(),f.control(),budget).complete,"own artifacts use real complete accounting");
            }
            try(NativeCaptureFingerprint.PhysicalProof proof=f.prove(fp)){f.recheck(proof);}
        });scenarios++;}
    }
    public static void main(String[] args)throws Exception {
        baselineAndBudget();membership();callbackIdentity();authority();frozenAdmission();proofOwnership();largePayload();ownOperation();
        System.out.println("Native capture sources: "+scenarios+" scenarios PASS");
    }
}
