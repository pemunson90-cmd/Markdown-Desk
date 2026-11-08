package com.example.markdowndesk;

import java.io.*;
import java.lang.reflect.*;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Real backend/codec evidence; scalar funding boundary probes are explicitly separate. */
public final class PrivateCapturePlanCheck {
    static int scenarios;
    interface Task {void run()throws Exception;}
    static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    static void rejects(Task task,String why)throws Exception {
        try{task.run();throw new AssertionError("Accepted "+why);}catch(IOException expected){}
    }
    static final class Disk implements ProtectionStore.Durability {
        public void syncFile(FileDescriptor fd)throws IOException{fd.sync();}
        public void move(File a,File b)throws IOException{Files.move(a.toPath(),b.toPath(),StandardCopyOption.ATOMIC_MOVE);}
        public void syncDirectory(File dir)throws IOException{try(FileChannel c=FileChannel.open(dir.toPath(),StandardOpenOption.READ)){c.force(true);}}
    }
    static final class F implements AutoCloseable {
        final Path parent=Files.createTempDirectory("capture-plan-");final Disk disk=new Disk();
        final ProtectionStore store=new ProtectionStore(parent.resolve("store").toFile(),disk,64L<<20);
        final HistoryCatalog catalog=new HistoryCatalog(parent.resolve("catalog").toFile(),store,disk);
        final HistoryIdentity identity=new HistoryIdentity(parent.resolve("identity").toFile(),disk);
        final HistoryIndex index=new HistoryIndex(store,catalog,identity,parent.resolve("index").toFile(),disk);
        final HistoryCapacity capacity;final HistoryGraph scanner=new HistoryGraph(store,catalog,identity,index,null);
        final HistoryStorage.Budget budget=new HistoryStorage.Budget(HistoryStorage.METADATA_BUDGET);
        final UUID prep=UUID.randomUUID();final PreparationSources owner;
        HistoryGraph.Graph graph;HistoryArchiveManifest map;HistoryControlStore.Inspection control;PrivateOperationStore.Ledger ledger;
        PrivateFramePreflight.Workspace workspace;
        F(boolean populated)throws Exception {this(populated,false);}
        F(boolean populated,boolean closedSeed)throws Exception {
            if(populated){String history=identity.resolve("content://plan","tab");ProtectionStore.Snapshot a=store.putSnapshot("tab","content://plan","old",1,new byte[]{1},"original");ProtectionStore.Snapshot b=store.putSnapshot("tab","content://plan","new",1,new byte[]{2},"attempted");ProtectionStore.Transaction tx=store.prepare(a,b,a.hash);store.complete(tx);catalog.name(b.id,"named");index.recordVerified(history,tx.id);store.prepare(a,b,a.hash);}
            capacity=HistoryCapacity.register(store,catalog,identity,index,new HistoryPolicy(64L<<20,20),disk);
            if(closedSeed){refresh();seedClosed(this);}
            owner=PreparationSources.forPreparation(store.storageRoot(),prep,budget,PreparationSources.newProducer());refresh();
        }
        void locked(Task t)throws Exception{synchronized(HistoryStorage.monitorFor(store.storageRoot())){try(HistoryStorage.MutationScope lease=HistoryStorage.administration(store.storageRoot())){t.run();}}}
        void refresh()throws Exception {
            // Fixture reserves concurrent caller-owned graph/map/control/ledger metadata.
            budget.charge(1L<<20);locked(()->{graph=scanner.scan();control=new HistoryControlStore(parent.toFile()).inspect();ledger=PrivateOperationStore.inspect(store.storageRoot(),control,budget);if(!graph.complete)throw new IOException("fixture graph incomplete: "+graph.diagnostics+" control="+control.diagnostics);map=HistoryArchiveManifest.freeze(graph,control,UUID::randomUUID);});
        }
        PrivateCapturePlan create(List<PrivateFramePreflight.ClosedFrame> frames)throws IOException {
            return create(prep,UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),frames,budget);
        }
        PrivateCapturePlan create(UUID prepId,UUID operation,UUID mapId,UUID descriptor,List<PrivateFramePreflight.ClosedFrame> frames,HistoryStorage.Budget supplied)throws IOException {
            return PrivateCapturePlan.create(store.storageRoot(),catalog.storageRoot(),identity.storageRoot(),index.storageRoot(),map,graph,control,ledger,owner,prepId,operation,mapId,descriptor,frames,64L<<20,supplied);
        }
        PrivateFramePreflight.ClosedFrame frame(PrivateStateCodec.Kind kind,int ordinal,String tabId)throws IOException {
            return frame(kind,ordinal,tabId,UUID.randomUUID(),UUID.randomUUID());
        }
        PrivateFramePreflight.ClosedFrame frame(PrivateStateCodec.Kind kind,int ordinal,String tabId,UUID item,UUID artifact)throws IOException {
            if(workspace==null)workspace=PrivateFramePreflight.create(owner,budget);
            ByteArrayOutputStream bytes=new ByteArrayOutputStream();
            if(kind==PrivateStateCodec.Kind.PREFERENCES)PrivateStateCodec.writePreferences(Collections.singletonMap("ui_theme",8),bytes);
            else if(kind==PrivateStateCodec.Kind.RECOVERY)PrivateStateCodec.writeRecovery(new PrivateStateCodec.Recovery("Recovery",new PrivateStateCodec.Provenance(null,null,-1,false),false,0,0,1),0,new ByteArrayInputStream(new byte[0]),bytes);
            else PrivateStateCodec.writeTab(new PrivateStateCodec.Tab(tabId,"Draft雪",new PrivateStateCodec.Provenance(null,null,-1,false),false,true,false,0,0,0,0,0,"","",false,false,false),0,new ByteArrayInputStream(new byte[0]),bytes);
            byte[] frame=bytes.toByteArray();return workspace.attempt(kind,ordinal,item,artifact,()->new ByteArrayInputStream(frame)).run();
        }
        public void close()throws IOException{try(java.util.stream.Stream<Path> paths=Files.walk(parent)){for(Path p:(Iterable<Path>)paths.sorted(Comparator.reverseOrder())::iterator)Files.delete(p);}}
    }
    static void roundtrip(PrivateCapturePlan p)throws Exception {
        check(Arrays.equals(p.descriptor.encoded(),PrivateOperationRecord.decode(p.descriptor.encoded()).encoded()),"D roundtrip");
        check(Arrays.equals(p.prepare.encoded(),PrivateOperationRecord.decode(p.prepare.encoded()).encoded()),"P roundtrip");
        check(Arrays.equals(p.link.encoded(),PrivateOperationRecord.decode(p.link.encoded()).encoded()),"LINK roundtrip");
        check(p.prepare.begin.manifest.hash.equals(p.descriptor.envelopeHash)&&p.prepare.begin.manifest.length==p.descriptor.encoded().length,"BEGIN exact D hash and length");
        check(p.prepare.begin.manifest.count==p.nativeMap.entries.size()+p.privateFrames.size()&&p.prepare.begin.manifest.domain==HistoryPinRecord.Domain.LOCAL_CAPTURE_V1,"domain2 exact item count");
        check(p.prepare.artifact(2).hash.equals(p.descriptor.envelopeHash)&&p.link.descriptorHash.equals(p.descriptor.envelopeHash)&&p.link.preparationHash.equals(p.prepare.envelopeHash),"acyclic descriptor/prepare/link binding");
        long bytes=0;for(PrivateOperationRecord.Artifact a:p.prepare.artifacts)bytes+=a.length;
        check(bytes==p.artifactBytes&&HistoryPinRecord.encode(p.prepare.begin).length==204&&p.link.encoded().length==173,"exact funding units");
        ByteArrayOutputStream map=new ByteArrayOutputStream();p.nativeMap.write(map);check(p.prepare.artifact(1).length==map.size()&&p.prepare.artifact(1).hash.equals(PrivateOperationRecord.sha(map.toByteArray())),"streamed map length/hash exact");
    }
    static void nativeAndEmpty()throws Exception {
        String[] descriptors=new String[2];
        for(int i=0;i<2;i++)try(F f=new F(false)){final int at=i;f.locked(()->{
            PrivateCapturePlan p=f.create(Collections.emptyList());roundtrip(p);descriptors[at]=p.descriptor.envelopeHash;
            check(p.nativeMap.entries.isEmpty()&&p.prepare.binding.phase==1&&p.prepare.binding.flags==0&&p.prepare.directoryMask==3,"zero-native phase A exact directory credit");
            check(p.descriptor.preparation.equals(f.prep)&&p.prepare.preparation.equals(f.prep),"zero-native descriptor retains exact preparation UUID");
            check(p.initialLedgerHash.equals("d85d1350026fd0c27784a3126cd3adff553265dc45b173b353b13c049645825e"),"absent ledger independent golden");
            check(PrivateCapturePlan.initialLedgerHash(f.ledger).equals(p.initialLedgerHash),"writer can rebind fresh canonical initial ledger");
            check(p.sourceOwner==f.owner&&p.nativeMap==f.map,"exact retained provenance");
            check(!Files.exists(f.parent.resolve("private_operations"))&&!Files.exists(f.parent.resolve("history_control/"+p.prepare.name())),"assembly writes no operation evidence");
        });scenarios++;}
        check(!descriptors[0].equals(descriptors[1]),"zero-native captures remain distinct");
        try(F f=new F(true)){f.locked(()->{PrivateCapturePlan p=f.create(Collections.emptyList());roundtrip(p);check(p.nativeSources.nativeReservedCompletionBytes>0,"native completion reservation retained");});rejects(()->f.create(Collections.emptyList()),"assembly without primary lease");scenarios++;}
    }
    static void privateTable()throws Exception {
        try(F f=new F(true)){
            List<PrivateFramePreflight.ClosedFrame> frames=new ArrayList<>();for(int i=0;i<12;i++)frames.add(f.frame(PrivateStateCodec.Kind.SESSION_TAB,i,"tab-"+i));frames.add(f.frame(PrivateStateCodec.Kind.RECOVERY,0,null));frames.add(f.frame(PrivateStateCodec.Kind.PREFERENCES,0,null));
            f.locked(()->{PrivateCapturePlan p=f.create(frames);roundtrip(p);check(p.prepare.binding.phase==2&&p.prepare.binding.flags==3&&p.prepare.artifacts.size()==16,"all14 private rows and exact presence flags");frames.clear();check(p.privateFrames.size()==14,"caller list detached");try{p.privateFrames.clear();throw new AssertionError("mutable private list");}catch(UnsupportedOperationException expected){} });scenarios++;
        }
        try(F f=new F(false)){
            PrivateFramePreflight.ClosedFrame preferences=f.frame(PrivateStateCodec.Kind.PREFERENCES,0,null);
            f.locked(()->check(f.create(Collections.singletonList(preferences)).prepare.binding.flags==2,"preferences-only phase B without recovery"));scenarios++;
        }
        for(String fault:new String[]{"missing-prefs","order","ordinal","duplicate-source","duplicate-row"})try(F f=new F(false)){
            PrivateFramePreflight.ClosedFrame a=f.frame(PrivateStateCodec.Kind.SESSION_TAB,0,"same");
            PrivateFramePreflight.ClosedFrame b=f.frame(PrivateStateCodec.Kind.SESSION_TAB,fault.equals("ordinal")?2:1,fault.equals("duplicate-source")?"same":"different");
            PrivateFramePreflight.ClosedFrame prefs=f.frame(PrivateStateCodec.Kind.PREFERENCES,0,null);List<PrivateFramePreflight.ClosedFrame> frames=new ArrayList<>(Arrays.asList(a,b,prefs));
            if(fault.equals("missing-prefs"))frames.remove(2);if(fault.equals("order"))Collections.swap(frames,0,1);if(fault.equals("duplicate-row"))frames.set(1,a);
            f.locked(()->rejects(()->f.create(frames),"canonical private table "+fault));scenarios++;
        }
        try(F f=new F(false);F other=new F(false)){
            PrivateFramePreflight.ClosedFrame p=other.frame(PrivateStateCodec.Kind.PREFERENCES,0,null);f.locked(()->rejects(()->f.create(Collections.singletonList(p)),"different preflight owner"));scenarios++;
        }
    }
    static void identitiesAndEvidence()throws Exception {
        try(F f=new F(true)){f.locked(()->{
            UUID id=UUID.randomUUID();rejects(()->f.create(f.prep,id,id,UUID.randomUUID(),Collections.emptyList(),f.budget),"generated ID collision");
            rejects(()->f.create(f.prep,f.map.bundleId,UUID.randomUUID(),UUID.randomUUID(),Collections.emptyList(),f.budget),"bundle ID collision");
            rejects(()->f.create(f.prep,UUID.randomUUID(),f.map.entries.get(0).archiveId,UUID.randomUUID(),Collections.emptyList(),f.budget),"native outer archive ID collision");
            rejects(()->f.create(UUID.randomUUID(),id,UUID.randomUUID(),UUID.randomUUID(),Collections.emptyList(),f.budget),"owner preparation mismatch");
            rejects(()->f.create(f.prep,id,UUID.randomUUID(),UUID.randomUUID(),Collections.emptyList(),new HistoryStorage.Budget(HistoryStorage.METADATA_BUDGET)),"foreign budget");
            f.ledger.complete=false;rejects(()->f.create(Collections.emptyList()),"incomplete ledger");f.ledger.complete=true;
            f.ledger.fingerprint="00".repeat(32);rejects(()->f.create(Collections.emptyList()),"absent ledger cannot carry fabricated fingerprint");f.ledger.fingerprint="";
        });scenarios++;}
        try(F f=new F(false)){
            PrivateFramePreflight.ClosedFrame prefs=f.frame(PrivateStateCodec.Kind.PREFERENCES,0,null,UUID.randomUUID(),f.prep);f.locked(()->rejects(()->f.create(Collections.singletonList(prefs)),"private artifact collides with preparation"));scenarios++;
        }
        try(F f=new F(true)){
            f.store.putSnapshot("tab","uri","after freeze",2,new byte[]{4},"attempted");f.locked(()->rejects(()->f.create(Collections.emptyList()),"stale frozen graph/map"));scenarios++;
        }
        try(F f=new F(false)){
            f.capacity.setPolicy(new HistoryPolicy(128L<<20,10));f.locked(()->rejects(()->f.create(Collections.emptyList()),"stale initial policy"));scenarios++;
        }
        try(F f=new F(false)){
            f.owner.freeze();f.locked(()->rejects(()->f.create(Collections.emptyList()),"frozen source owner is not initial idle assembly owner"));scenarios++;
        }
        try(F f=new F(false)){
            PreparationSources.Reader reader=f.owner.open(()->new ByteArrayInputStream(new byte[0]));f.locked(()->rejects(()->f.create(Collections.emptyList()),"live idle reader blocks assembly"));reader.close();scenarios++;
        }
        try(F f=new F(false)){
            long owner=2048L+f.store.storageRoot().getPath().length()*2L;f.budget.charge(HistoryStorage.METADATA_BUDGET-owner-(1L<<20)-1024);f.locked(()->rejects(()->f.create(Collections.emptyList()),"assembly budget before fixed records"));scenarios++;
        }
    }
    static void closedCapture()throws Exception {
        try(F f=new F(true)){
            final PrivateCapturePlan[] first={null};f.locked(()->first[0]=f.create(Collections.emptyList()));PrivateCapturePlan p=first[0];
            // Test-only persistence fixture of exact planned records; not a production writer.
            Path dir=Files.createDirectories(f.parent.resolve("private_operations/"+f.prep));try(OutputStream out=Files.newOutputStream(dir.resolve(p.prepare.artifact(1).filename()))){f.map.write(out);}Files.write(dir.resolve(p.prepare.artifact(2).filename()),p.descriptor.encoded());
            Files.write(f.parent.resolve("history_control/"+p.prepare.name()),p.prepare.encoded());Files.write(f.parent.resolve("history_control/"+p.prepare.begin.name()),HistoryPinRecord.encode(p.prepare.begin));Files.write(f.parent.resolve("history_control/"+p.link.name()),p.link.encoded());
            Files.write(f.parent.resolve("history_control/pin-"+p.prepare.operation+".close"),HistoryPinRecord.close(p.prepare.begin,HistoryPinRecord.Outcome.CANCELLED));f.capacity.reconcile();HistoryPins.open(f.store,f.catalog,f.identity,f.index,f.disk);f.refresh();
            f.locked(()->rejects(()->f.create(Collections.emptyList()),"existing preparation directory cannot be reused"));
            scenarios++;
        }
    }
    static void seedClosed(F f)throws Exception {
        final PrivateOperationRecord[] records=new PrivateOperationRecord[3];final HistoryArchiveManifest nativeMap=f.map;
        f.locked(()->{
            NativeCaptureFingerprint source=NativeCaptureFingerprint.capture(nativeMap,f.graph,f.control,f.store.storageRoot(),f.catalog.storageRoot(),f.identity.storageRoot(),f.index.storageRoot(),64L<<20,f.budget);
            PrivateOperationRecord.Binding c=new PrivateOperationRecord.Binding(UUID.randomUUID(),1,f.control.registration.bundleId,f.control.registration.envelopeHash,f.control.latest.generation,f.control.latest.envelopeHash,64L<<20,f.control.latest.quotaBytes,f.control.latest.automaticCount,f.control.registration.roles,source.fingerprint,"d85d1350026fd0c27784a3126cd3adff553265dc45b173b353b13c049645825e",0);
            ByteArrayOutputStream bytes=new ByteArrayOutputStream();nativeMap.write(bytes);
            PrivateOperationRecord.Artifact map=new PrivateOperationRecord.Artifact(1,UUID.randomUUID(),PrivateOperationRecord.ZERO,0,bytes.size(),PrivateOperationRecord.sha(bytes.toByteArray()));
            PrivateOperationRecord d=PrivateOperationRecord.descriptor(c,map,Collections.emptyList());
            PrivateOperationRecord.Artifact descriptor=new PrivateOperationRecord.Artifact(2,UUID.randomUUID(),PrivateOperationRecord.ZERO,0,d.encoded().length,d.envelopeHash);
            HistoryPinRecord begin=HistoryPinRecord.begin(UUID.randomUUID(),f.control.registration,f.control.latest,HistoryPinRecord.Purpose.EXPORT,HistoryPinRecord.Scope.ALL_BUNDLE,new HistoryPinRecord.Manifest(HistoryPinRecord.Domain.LOCAL_CAPTURE_V1,nativeMap.entries.size(),d.encoded().length,d.envelopeHash),Collections.emptyList());
            records[0]=PrivateOperationRecord.prepare(c,3,Arrays.asList(map,descriptor),begin);records[1]=d;records[2]=PrivateOperationRecord.link(records[0]);
        });
        PrivateOperationRecord p=records[0];Path dir=Files.createDirectories(f.parent.resolve("private_operations/"+p.preparation));try(OutputStream out=Files.newOutputStream(dir.resolve(p.artifact(1).filename()))){nativeMap.write(out);}Files.write(dir.resolve(p.artifact(2).filename()),records[1].encoded());
        Files.write(f.parent.resolve("history_control/"+p.name()),p.encoded());Files.write(f.parent.resolve("history_control/"+p.begin.name()),HistoryPinRecord.encode(p.begin));Files.write(f.parent.resolve("history_control/"+records[2].name()),records[2].encoded());Files.write(f.parent.resolve("history_control/pin-"+p.operation+".close"),HistoryPinRecord.close(p.begin,HistoryPinRecord.Outcome.CANCELLED));
        f.capacity.reconcile();HistoryPins.open(f.store,f.catalog,f.identity,f.index,f.disk);
    }
    static void existingClosedAllowed()throws Exception {
        try(F f=new F(true,true)){f.locked(()->{
            check(f.ledger.present&&!f.ledger.pending&&f.graph.complete,"real prior closed capture fully accounted");
            PrivateCapturePlan p=f.create(Collections.emptyList());roundtrip(p);check(p.prepare.directoryMask==2,"existing namespace receives no new root slot");
            java.security.MessageDigest digest=HistoryStorage.digest();digest.update("MDOP-initial-ledger-v1\n".getBytes(StandardCharsets.US_ASCII));digest.update((byte)1);
            byte[] raw=new byte[32];for(int i=0;i<32;i++)raw[i]=(byte)Integer.parseInt(f.ledger.fingerprint.substring(2*i,2*i+2),16);digest.update(raw);
            check(p.initialLedgerHash.equals(HistoryStorage.hex(digest.digest())),"present ledger binding independent exact domain");
            String old=f.ledger.entries.get(PrivateOperationStore.ROOT);f.ledger.entries.put(PrivateOperationStore.ROOT,old+"changed");rejects(()->f.create(Collections.emptyList()),"mutated ledger tables cannot reuse old fingerprint");
            f.ledger.entries.put(PrivateOperationStore.ROOT,null);rejects(()->f.create(Collections.emptyList()),"noncanonical null ledger value is rejected as IO failure");
        });scenarios++;}
    }
    static void badFreshEvidence()throws Exception {
        for(String fault:new String[]{"pending","active-pin","policy-stage","unknown-namespace"})try(F f=new F(false)){
            final PrivateCapturePlan[] planned={null};f.locked(()->planned[0]=f.create(Collections.emptyList()));
            switch(fault){
                case "pending":Files.write(f.parent.resolve("history_control/"+planned[0].prepare.name()),planned[0].prepare.encoded());break;
                case "active-pin":HistoryPinRecord begin=HistoryPinRecord.begin(UUID.randomUUID(),f.control.registration,f.control.latest,HistoryPinRecord.Purpose.EXPORT,HistoryPinRecord.Scope.ALL_BUNDLE,new HistoryPinRecord.Manifest(HistoryPinRecord.Domain.NATIVE_HISTORY_V1,0,0,PrivateOperationRecord.sha(new byte[0])),Collections.emptyList());Files.write(f.parent.resolve("history_control/"+begin.name()),HistoryPinRecord.encode(begin));break;
                case "policy-stage":Files.write(f.parent.resolve("history_control/"+HistoryControlStore.policyFilename(1)+".stage"),HistoryControlStore.encodePolicy(f.control.registration.bundleId,1,f.control.latest.envelopeHash,128L<<20,10));break;
                case "unknown-namespace":Files.createDirectories(f.parent.resolve("private_operations/unknown"));break;
            }
            f.locked(()->{f.graph=f.scanner.scan();f.control=new HistoryControlStore(f.parent.toFile()).inspect();f.ledger=PrivateOperationStore.inspect(f.store.storageRoot(),f.control,f.budget);rejects(()->f.create(Collections.emptyList()),"fresh invalid evidence "+fault);});scenarios++;
        }
        try(F f=new F(true)){f.locked(()->{
            int[] n={0};f.map=HistoryArchiveManifest.freeze(f.graph,f.control,()->n[0]++==0?f.control.registration.bundleId:UUID.randomUUID());rejects(()->f.create(Collections.emptyList()),"outer native archive ID collides with bundle");
        });scenarios++;}
    }
    static void realQuotaAndControlAdmission()throws Exception {
        try(F f=new F(false)){
            List<PrivateFramePreflight.ClosedFrame> frames=new ArrayList<>();f.workspace=PrivateFramePreflight.create(f.owner,f.budget);
            for(int i=0;i<9;i++){
                Path path=f.parent.resolve("tab-"+i+".frame");InputStream payload=new InputStream(){int left=PrivateStateCodec.MAX_PAYLOAD_BYTES;public int read(){if(left==0)return -1;left--;return 'x';}public int read(byte[] b,int o,int n){if(left==0)return -1;n=Math.min(n,left);Arrays.fill(b,o,o+n,(byte)'x');left-=n;return n;}};
                try(OutputStream out=Files.newOutputStream(path)){PrivateStateCodec.writeTab(new PrivateStateCodec.Tab("large-"+i,"Large",new PrivateStateCodec.Provenance(null,null,-1,false),false,true,false,0,0,0,0,0,"","",false,false,false),PrivateStateCodec.MAX_PAYLOAD_BYTES,payload,out);}
                frames.add(f.workspace.attempt(PrivateStateCodec.Kind.SESSION_TAB,i,UUID.randomUUID(),UUID.randomUUID(),()->Files.newInputStream(path)).run());
            }
            frames.add(f.frame(PrivateStateCodec.Kind.PREFERENCES,0,null));
            f.locked(()->{try{f.create(frames);throw new AssertionError("Accepted real oversized private capture");}catch(IOException expected){check(expected.getMessage().contains("funding"),"actual data quota rejects valid frozen frames");}});
            f.capacity.setPolicy(new HistoryPolicy(128L<<20,20));f.refresh();
            f.locked(()->{PrivateCapturePlan p=f.create(frames);roundtrip(p);check(p.artifactBytes>(64L<<20),"same valid frames admitted only after exact larger policy capture");});scenarios++;
        }
        try(F f=new F(false)){
            String previous=f.control.latest.envelopeHash;
            for(int i=1;i<=251;i++){byte[] bytes=HistoryControlStore.encodePolicy(f.control.registration.bundleId,i,previous,64L<<20,20);Files.write(f.parent.resolve("history_control/"+HistoryControlStore.policyFilename(i)),bytes);previous=PrivateOperationRecord.sha(bytes);}
            f.capacity.reconcile();f.refresh();f.locked(()->{check(f.control.actualEntries==253&&f.graph.complete,"valid near-limit control backend");try{f.create(Collections.emptyList());throw new AssertionError("Accepted unfunded four control slots");}catch(IOException expected){check(expected.getMessage().contains("funding"),"actual control slots reject new capture");}});scenarios++;
        }
    }
    static HistoryControlStore.Inspection permuted(HistoryControlStore.Inspection c)throws Exception {
        List<HistoryControlStore.Entry> entries=new ArrayList<>(c.entries);Collections.reverse(entries);
        Constructor<HistoryControlStore.Inspection> make=HistoryControlStore.Inspection.class.getDeclaredConstructor(HistoryControlStore.State.class,HistoryControlStore.Registration.class,HistoryControlStore.Record.class,List.class,List.class,long.class,int.class,boolean.class,boolean.class,List.class);make.setAccessible(true);
        return make.newInstance(c.state,c.registration,c.latest,entries,c.stages,c.actualBytes,c.actualEntries,c.usageComplete,c.chainComplete,c.diagnostics);
    }
    static String independentControlHash(HistoryControlStore.Inspection c)throws IOException {
        List<HistoryControlStore.Entry> rows=new ArrayList<>(c.entries);rows.sort(Comparator.comparing(e->e.filename));
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();DataOutputStream out=new DataOutputStream(bytes);
        out.write("MDOP-initial-control-v1\n".getBytes(StandardCharsets.US_ASCII));out.writeInt(rows.size());
        for(HistoryControlStore.Entry e:rows){byte[] name=e.filename.getBytes(StandardCharsets.UTF_8);out.writeInt(name.length);out.write(name);out.writeLong(e.length);for(int i=0;i<32;i++)out.writeByte(Integer.parseInt(e.envelopeHash.substring(2*i,2*i+2),16));out.writeByte(e.stage?1:0);}
        return PrivateOperationRecord.sha(bytes.toByteArray());
    }
    static void initialControlBinding()throws Exception {
        try(F f=new F(false)){f.locked(()->{
            PrivateCapturePlan original=f.create(Collections.emptyList());
            check(original.initialControlHash.equals(PrivateCapturePlan.initialControlHash(f.control)),"plan retains original complete control binding");
            check(original.initialControlHash.equals(independentControlHash(f.control)),"independent exact control domain/count/UTF8/length/SHA/stage encoding");
            check(original.initialControlHash.equals(PrivateCapturePlan.initialControlHash(new HistoryControlStore(f.parent.toFile()).inspect())),"fresh unchanged inspection has stable digest");
            check(original.initialControlHash.equals(PrivateCapturePlan.initialControlHash(permuted(f.control))),"physical enumeration permutation does not change digest");
            HistoryPinRecord begin=HistoryPinRecord.begin(UUID.randomUUID(),f.control.registration,f.control.latest,HistoryPinRecord.Purpose.EXPORT,HistoryPinRecord.Scope.ALL_BUNDLE,new HistoryPinRecord.Manifest(HistoryPinRecord.Domain.NATIVE_HISTORY_V1,0,0,PrivateOperationRecord.sha(new byte[0])),Collections.emptyList());
            Files.write(f.parent.resolve("history_control/"+begin.name()),HistoryPinRecord.encode(begin));Files.write(f.parent.resolve("history_control/pin-"+begin.operation+".close"),HistoryPinRecord.close(begin,HistoryPinRecord.Outcome.CANCELLED));
            HistoryControlStore.Inspection fresh=new HistoryControlStore(f.parent.toFile()).inspect();PrivateOperationStore.Ledger latest=PrivateOperationStore.inspect(f.store.storageRoot(),fresh,f.budget);
            check(fresh.pins.active==null&&fresh.pins.valid,"actual legacy pin is closed, not active");
            check(original.initialLedgerHash.equals(PrivateCapturePlan.initialLedgerHash(latest)),"closed legacy domain1 pin leaves absent operation ledger unchanged");
            check(!original.initialControlHash.equals(PrivateCapturePlan.initialControlHash(fresh)),"new CLOSED legacy pin changes full original-control digest");
            check(PrivateCapturePlan.initialControlHash(fresh).equals(independentControlHash(fresh)),"fresh legacy pin entries bind count/hash/order");
            PrivateCapturePlan metadataOnly=f.create(Collections.emptyList());
            check(metadataOnly.initialControlHash.equals(original.initialControlHash),"pure assembly preserves supplied C0 rather than discovering/rebinding newer control");
            check(!Files.exists(f.parent.resolve("history_control/"+metadataOnly.prepare.name())),"stale metadata planning never publishes PREPARE");
        });scenarios++;}
    }
    static void scalarFunding()throws Exception {
        Method admit=PrivateCapturePlan.class.getDeclaredMethod("admit",long.class,long.class,long.class,long.class,long.class,long.class,long.class,long.class,long.class,int.class,int.class);admit.setAccessible(true);
        class Probe {void call(long data,long artifacts,long quota,long control,long credit,long p,long controls,long slots,long op,int dirs)throws Exception {
            try{admit.invoke(null,data,artifacts,quota,control,credit,p,controls,slots,op,dirs,2);}catch(InvocationTargetException e){if(e.getCause() instanceof IOException)throw (IOException)e.getCause();throw e;}
        }}Probe probe=new Probe();
        // p includes only PREPARE; fixed BEGIN+CLOSE+LINK is 503B and four control slots.
        probe.call(90,10,100,1048576-603,0,100,252,65536-4,0,2);
        probe.call(90,10,100,1048576-623,20,100,252,65536-5,1,2);
        rejects(()->probe.call(90,10,100,1048576-623,21,100,252,0,0,2),"existing control credit is charged");
        rejects(()->probe.call(90,10,100,0,0,100,252,65536-5,2,2),"existing reserved namespace slots are charged");
        rejects(()->probe.call(91,10,100,0,0,100,0,0,0,2),"data quota +1");
        rejects(()->probe.call(0,1,100,1048576-602,0,100,0,0,0,2),"control bytes +1");
        rejects(()->probe.call(0,1,100,0,0,100,253,0,0,2),"control slots +1");
        rejects(()->probe.call(0,1,100,0,0,100,0,65536-3,0,2),"namespace slots +1");
        rejects(()->probe.call(Long.MAX_VALUE,1,Long.MAX_VALUE,0,0,100,0,0,0,2),"overflow cannot wrap funding");scenarios++;
    }
    public static void main(String[] args)throws Exception {nativeAndEmpty();privateTable();identitiesAndEvidence();closedCapture();existingClosedAllowed();badFreshEvidence();realQuotaAndControlAdmission();initialControlBinding();scalarFunding();System.out.println("Private capture plan: "+scenarios+" scenarios PASS");}
}
