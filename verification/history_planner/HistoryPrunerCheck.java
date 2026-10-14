package com.example.markdowndesk;

import java.io.*;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;

/** Native production writes and fresh scans, never synthetic valid Graph objects. */
public final class HistoryPrunerCheck {
    static int checks;
    static void check(boolean value,String reason){checks++;if(!value)throw new AssertionError(reason);}
    interface Construction {void run()throws Exception;}
    static void rejects(Construction action)throws Exception{try{action.run();throw new AssertionError("Expected invalid construction");}catch(IllegalArgumentException expected){checks++;}}
    interface Action {void run()throws IOException;}
    static final class Disk implements ProtectionStore.Durability {
        Action onCatalogSync;
        public void syncFile(FileDescriptor fd)throws IOException{fd.sync();}
        public void move(File a,File b)throws IOException{Files.move(a.toPath(),b.toPath(),StandardCopyOption.ATOMIC_MOVE);}
        public void syncDirectory(File d)throws IOException{if(d.getName().equals("catalog")&&onCatalogSync!=null){Action a=onCatalogSync;onCatalogSync=null;a.run();}try(FileChannel c=FileChannel.open(d.toPath(),StandardOpenOption.READ)){c.force(true);}}
    }
    static final class Fixture {
        final Path parent=Files.createTempDirectory("history-planner-"); final Disk disk=new Disk();
        final ProtectionStore store=new ProtectionStore(parent.resolve("store").toFile(),disk,64L*1024*1024);
        final HistoryCatalog catalog=new HistoryCatalog(parent.resolve("catalog").toFile(),store,disk);
        final HistoryIdentity identity=new HistoryIdentity(parent.resolve("identity").toFile(),disk);
        final HistoryIndex index=new HistoryIndex(store,catalog,identity,parent.resolve("index").toFile(),disk);
        Fixture()throws Exception{}
        HistoryGraph graph(int count)throws Exception{return new HistoryGraph(store,catalog,identity,index,new HistoryPolicy(64L*1024*1024,count));}
        HistoryPruner pruner(int count)throws Exception{return new HistoryPruner(graph(count));}
        ProtectionStore.Transaction group(String uri,boolean complete,boolean indexed)throws Exception{
            ProtectionStore.Snapshot a=store.putSnapshot("tab",uri,"title",1,new byte[]{1,2,3},"original");
            ProtectionStore.Snapshot b=store.putSnapshot("tab",uri,"title",1,new byte[]{4,5},"attempted");
            return pair(uri,a,b,complete,indexed);
        }
        ProtectionStore.Transaction pair(String uri,ProtectionStore.Snapshot a,ProtectionStore.Snapshot b,boolean complete,boolean indexed)throws Exception{
            ProtectionStore.Transaction t=store.prepare(a,b,a.hash);if(complete)store.complete(t);
            if(indexed)index.recordVerified(identity.resolve(uri,"tab"),t.id);return t;
        }
        Map<String,String> capture()throws Exception{
            Map<String,String> m=new TreeMap<>();for(String r:Arrays.asList("store","catalog","identity","index"))try(java.util.stream.Stream<Path> s=Files.list(parent.resolve(r))){
                for(Path p:(Iterable<Path>)s::iterator){MessageDigest d=MessageDigest.getInstance("SHA-256");try(InputStream in=Files.newInputStream(p)){byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1)d.update(b,0,n);}m.put(r+"/"+p.getFileName(),HistoryStorage.hex(d.digest()));}}
            return m;
        }
    }
    static HistoryPruner.DeletePlan planned(HistoryPruner.Result r){check(r.plan!=null&&r.blockedReason==null,"expected plan, got "+r.blockedReason);return r.plan;}
    static void blocked(HistoryPruner.Result r,HistoryPruner.BlockedReason reason){check(r.plan==null&&r.blockedReason==reason,"expected "+reason+", got "+r.blockedReason);}
    static void regular()throws Exception{
        Fixture f=new Fixture();ProtectionStore.Transaction old=f.group("content://a",true,true), middle=f.group("content://a",true,true), newest=f.group("content://a",true,true);
        HistoryGraph boundaryGraph=f.graph(1);
        rejects(()->new HistoryPruner(boundaryGraph,Arrays.asList("invalid")));
        rejects(()->new HistoryPruner(boundaryGraph,Arrays.asList("AAAAAAAA-AAAA-AAAA-AAAA-AAAAAAAAAAAA")));
        rejects(()->new HistoryPruner(boundaryGraph,Arrays.asList(old.original.id,old.original.id)));
        rejects(()->new HistoryPruner(boundaryGraph,Arrays.asList((String)null)));
        List<String> excess=new ArrayList<>();for(int i=0;i<=HistoryPruner.MAX_EXTERNAL_PINS;i++)excess.add(UUID.randomUUID().toString());
        rejects(()->new HistoryPruner(boundaryGraph,excess));
        blocked(f.pruner(1).planSelected(excess),HistoryPruner.BlockedReason.INVALID_REQUEST);
        blocked(f.pruner(1).planSelected(Arrays.asList("invalid")),HistoryPruner.BlockedReason.INVALID_REQUEST);
        blocked(f.pruner(1).planSelected(Arrays.asList("AAAAAAAA-AAAA-AAAA-AAAA-AAAAAAAAAAAA")),HistoryPruner.BlockedReason.INVALID_REQUEST);
        Map<String,String> before=f.capture();HistoryPruner.DeletePlan p=planned(f.pruner(1).planAutomatic(1,1));
        check(p.groups.size()==1&&p.groups.get(0).transactionId.equals(old.id),"oldest persisted sequence first");
        check(p.members.size()==5&&p.entriesByRoot.get("store")==4&&p.entriesByRoot.get("index")==1,"exact pair closure and per-root counts");
        check(p.anchors.size()==1&&p.anchors.get(0).transactionId.equals(newest.id),"same-history fixed newest anchor");
        check(p.bytes>0&&p.entries==5&&p.graphFingerprint.equals(f.graph(1).scan().fingerprint),"fresh full graph binding");
        check(p.proofFingerprint.equals(planned(f.pruner(1).planAutomatic(1,1)).proofFingerprint),"deterministic proof");
        check(p.proofBytes<=HistoryPruner.MAX_PROOF_BYTES&&f.capture().equals(before),"bounded pure scan preserves bytes");
        for(ProtectionStore.StoredRecord r:p.members){check(before.get(r.rootRole+"/"+r.filename).equals(r.envelopeHash),"actual full member envelope hash");check(Files.size(f.parent.resolve(r.rootRole).resolve(r.filename))==r.serializedBytes,"actual member serialized length");}
        check(p.groups.get(0).references.size()==9,"full transaction/completion/index outgoing closure");
        try{p.members.clear();throw new AssertionError("mutable");}catch(UnsupportedOperationException expected){checks++;}
        blocked(f.pruner(20).planAutomatic(1,1),HistoryPruner.BlockedReason.CAPACITY_SHORTFALL);
        blocked(f.pruner(0).planAutomatic(1,1),HistoryPruner.BlockedReason.AUTOMATIC_DISABLED);
        blocked(f.pruner(1).planAutomatic(Long.MAX_VALUE,Long.MAX_VALUE),HistoryPruner.BlockedReason.CAPACITY_SHORTFALL);
        HistoryPruner.Result shortfall=f.pruner(1).planAutomatic(1,11);blocked(shortfall,HistoryPruner.BlockedReason.CAPACITY_SHORTFALL);
        check(shortfall.eligibleEntries==10&&shortfall.protectedBytes>0,"report full eligible and protected capacity without partial plan");
        blocked(f.pruner(1).planAutomatic(-1,0),HistoryPruner.BlockedReason.INVALID_REQUEST);
        blocked(new HistoryPruner(new HistoryGraph(f.store,f.catalog,f.identity,f.index,null)).planAutomatic(1,1),HistoryPruner.BlockedReason.MISSING_POLICY);
        HistoryCatalog.Checkpoint one=f.catalog.name(old.original.id,"same"), two=f.catalog.name(old.attempted.id,"same"), three=f.catalog.name(old.attempted.id,"third");
        blocked(f.pruner(1).planSelected(Arrays.asList(one.id,two.id)),HistoryPruner.BlockedReason.PARTIAL_CHECKPOINT_SELECTION);
        HistoryPruner.DeletePlan named=planned(f.pruner(0).planSelected(Arrays.asList(three.id,two.id,one.id)));
        check(named.members.size()==8&&named.selectedCheckpointIds.size()==3,"all duplicate labels included and explicit works with automatic off");
        check(named.groups.get(0).transactionId.equals(old.id)&&!named.proofFingerprint.equals(p.proofFingerprint),"name mutation changes exact proof");
        check(named.proofFingerprint.equals(planned(f.pruner(0).planSelected(Arrays.asList(one.id,two.id,three.id))).proofFingerprint),"selection ordering does not change exact proof");
        HistoryPruner.DeletePlan pinOne=planned(new HistoryPruner(f.graph(0),Arrays.asList(newest.original.id)).planSelected(Arrays.asList(one.id,two.id,three.id)));
        HistoryPruner.DeletePlan pinTwo=planned(new HistoryPruner(f.graph(0),Arrays.asList(newest.attempted.id)).planSelected(Arrays.asList(one.id,two.id,three.id)));
        check(!pinOne.proofFingerprint.equals(pinTwo.proofFingerprint)&&pinOne.graphFingerprint.equals(pinTwo.graphFingerprint),"external pin snapshot changes proof with unchanged stored graph");
        check(planned(f.pruner(1).planAutomatic(1,1)).groups.get(0).transactionId.equals(middle.id),"automatic protects named group");
        blocked(f.pruner(1).planSelected(Arrays.asList(one.id,one.id)),HistoryPruner.BlockedReason.INVALID_REQUEST);
        blocked(f.pruner(1).planSelected(Arrays.asList(UUID.randomUUID().toString())),HistoryPruner.BlockedReason.UNKNOWN_CHECKPOINT);
        HistoryCatalog.Checkpoint latest=f.catalog.name(newest.attempted.id,"latest");blocked(f.pruner(1).planSelected(Arrays.asList(latest.id)),HistoryPruner.BlockedReason.NEWEST_ANCHOR);
        Set<String> pins=new HashSet<>(Arrays.asList(old.original.id));HistoryPruner pinned=new HistoryPruner(f.graph(1),pins);pins.clear();
        blocked(pinned.planSelected(Arrays.asList(one.id,two.id,three.id)),HistoryPruner.BlockedReason.PROTECTED_GROUP);
        HistoryPruner.DeletePlan pinnedAuto=planned(pinned.planAutomatic(1,1));check(pinnedAuto.externalProtectedSnapshotIds.contains(old.original.id),"immutable external pins bound in proof");
        blocked(new HistoryPruner(f.graph(1),Arrays.asList(UUID.randomUUID().toString())).planAutomatic(1,1),HistoryPruner.BlockedReason.UNKNOWN_EXTERNAL_PIN);
        Fixture cross=new Fixture();ProtectionStore.Transaction only=cross.group("content://only",true,true);cross.group("content://other",true,true);
        HistoryCatalog.Checkpoint onlyName=cross.catalog.name(only.attempted.id,"only");blocked(cross.pruner(1).planSelected(Arrays.asList(onlyName.id)),HistoryPruner.BlockedReason.NEWEST_ANCHOR);
        Fixture protectedF=new Fixture();ProtectionStore.Transaction pending=protectedF.group("content://a",false,false), unindexed=protectedF.group("content://a",true,false);
        ProtectionStore.Snapshot orphan=protectedF.store.putSnapshot("tab","content://a",null,1,new byte[0],"attempted");
        protectedF.group("content://a",true,true);check(protectedF.graph(1).scan().complete,"pending/unindexed/orphan known graph remains readable");
        for(String id:Arrays.asList(pending.attempted.id,unindexed.attempted.id,orphan.id)){HistoryCatalog.Checkpoint label=protectedF.catalog.name(id,"protected");blocked(protectedF.pruner(1).planSelected(Arrays.asList(label.id)),HistoryPruner.BlockedReason.PROTECTED_GROUP);}
        Fixture shared=new Fixture();ProtectionStore.Transaction sharedOld=shared.group("content://a",true,true);
        ProtectionStore.Snapshot b=shared.store.putSnapshot("tab","content://a","title",1,new byte[]{8},"attempted");shared.pair("content://a",sharedOld.original,b,false,false);shared.group("content://a",true,true);
        HistoryCatalog.Checkpoint sharedLabel=shared.catalog.name(sharedOld.attempted.id,"shared");blocked(shared.pruner(1).planSelected(Arrays.asList(sharedLabel.id)),HistoryPruner.BlockedReason.PROTECTED_GROUP);
        Fixture sequence=new Fixture();ProtectionStore.Transaction createdFirst=sequence.group("content://a",true,false), createdLater=sequence.group("content://a",true,false);
        String history=sequence.identity.resolve("content://a","tab");sequence.index.recordVerified(history,createdLater.id);sequence.index.recordVerified(history,createdFirst.id);
        check(planned(sequence.pruner(1).planAutomatic(1,1)).groups.get(0).transactionId.equals(createdLater.id),"timestamps/content do not override persisted sequence");
        String prior=named.graphFingerprint;f.identity.forkAfterVerifiedSaveAs(f.identity.resolve("content://a","tab"),"content://fork");
        HistoryPruner.DeletePlan fork=planned(f.pruner(1).planSelected(Arrays.asList(one.id,two.id,three.id)));check(!fork.graphFingerprint.equals(prior)&&fork.members.stream().noneMatch(r->r.rootRole.equals("identity")),"identity/FORK proof changes, never deletion member");
        for(String root:Arrays.asList("store","catalog","identity","index")){Path stage=f.parent.resolve(root).resolve(UUID.randomUUID()+".stage");Files.write(stage,new byte[]{7});Map<String,String> staged=f.capture();blocked(f.pruner(1).planAutomatic(1,1),HistoryPruner.BlockedReason.INCOMPLETE_GRAPH);check(staged.equals(f.capture()),"stage preserved");Files.delete(stage);}
        Fixture duplicate=new Fixture();duplicate.group("content://a",true,true);duplicate.group("content://a",true,true);
        HistoryGraph.Graph duplicateGraph=duplicate.graph(1).scan();HistoryGraph.Ownership own=duplicateGraph.ownerships.get(0);
        Path duplicateFile=duplicate.parent.resolve("index").resolve(own.id+".index");byte[] duplicateBytes=Files.readAllBytes(duplicateFile);String duplicateId=UUID.randomUUID().toString();
        System.arraycopy(duplicateId.getBytes(java.nio.charset.StandardCharsets.UTF_8),0,duplicateBytes,16,36);
        byte[] digest=MessageDigest.getInstance("SHA-256").digest(Arrays.copyOf(duplicateBytes,duplicateBytes.length-32));System.arraycopy(digest,0,duplicateBytes,duplicateBytes.length-32,32);
        Files.write(duplicate.parent.resolve("index").resolve(duplicateId+".index"),duplicateBytes);Map<String,String> dupBefore=duplicate.capture();
        blocked(duplicate.pruner(1).planAutomatic(1,1),HistoryPruner.BlockedReason.INCOMPLETE_GRAPH);check(dupBefore.equals(duplicate.capture()),"valid-envelope duplicate ownership fails closed without mutation");
        Path corrupt=f.parent.resolve("store").resolve(old.id+".transaction");byte[] bytes=Files.readAllBytes(corrupt);Files.write(corrupt,new byte[]{1});blocked(f.pruner(1).planAutomatic(1,1),HistoryPruner.BlockedReason.INCOMPLETE_GRAPH);blocked(f.pruner(0).planAutomatic(1,1),HistoryPruner.BlockedReason.INCOMPLETE_GRAPH);Files.write(corrupt,bytes);
        Path unknown=f.parent.resolve("catalog/unknown");Files.write(unknown,new byte[]{1});blocked(f.pruner(1).planSelected(Arrays.asList(one.id,two.id,three.id)),HistoryPruner.BlockedReason.INCOMPLETE_GRAPH);Files.delete(unknown);
        Fixture changed=new Fixture();changed.group("content://a",true,true);changed.group("content://a",true,true);
        changed.disk.onCatalogSync=()->changed.store.putSnapshot("tab","content://a",null,1,new byte[0],"attempted");
        blocked(changed.pruner(1).planAutomatic(1,1),HistoryPruner.BlockedReason.INCOMPLETE_GRAPH);
        Fixture bounded=new Fixture();for(int i=0;i<53;i++)bounded.group("content://a",true,true);
        HistoryPruner.Result tooBig=bounded.pruner(1).planAutomatic(Long.MAX_VALUE,0);blocked(tooBig,HistoryPruner.BlockedReason.CAPACITY_SHORTFALL);
        blocked(bounded.pruner(1).planAutomatic(tooBig.eligibleBytes,tooBig.eligibleEntries),HistoryPruner.BlockedReason.PLAN_LIMIT);
        check(planned(bounded.pruner(1).planAutomatic(1,1)).members.size()==5,"bounded small plan on larger actual graph");
    }
    static void memory()throws Exception{
        Fixture f=new Fixture();byte[] payload=new byte[ProtectionStore.MAX_DOCUMENT_BYTES-1];
        ProtectionStore.Snapshot a=f.store.putSnapshot("tab","content://large",null,1,payload,"original");payload=null;
        ProtectionStore.Snapshot b=f.store.putSnapshot("tab","content://large",null,1,new byte[]{1},"attempted");ProtectionStore.Transaction old=f.pair("content://large",a,b,true,true);f.group("content://large",true,true);
        for(int i=0;i<64;i++)f.catalog.name(old.attempted.id,"label"+i);
        byte[] headroom=new byte[32*1024*1024];List<String> names=new ArrayList<>();for(HistoryCatalog.Checkpoint c:f.catalog.all())names.add(c.id);
        HistoryPruner.DeletePlan p=planned(f.pruner(1).planSelected(names));check(p.members.size()==69&&p.bytes>ProtectionStore.MAX_DOCUMENT_BYTES&&headroom[0]==0,"complete near-limit graph, duplicate checkpoint sources, 128MiB heap/32MiB retained headroom");
        for(int i=64;i<128;i++)names.add(f.catalog.name(old.attempted.id,"label"+i).id);
        blocked(f.pruner(1).planSelected(names),HistoryPruner.BlockedReason.PLAN_LIMIT);
        blocked(new HistoryPruner(new HistoryGraph(f.store,f.catalog,f.identity,f.index,new HistoryPolicy(64L*1024*1024,1),128)).planAutomatic(1,1),HistoryPruner.BlockedReason.INCOMPLETE_GRAPH);
        check(headroom[headroom.length-1]==0,"headroom live after bounded scan exhaustion");
    }
    public static void main(String[] args)throws Exception{if(args.length==0)regular();else memory();System.out.println("PASS history planner production fixtures: "+checks+" checks; pure host previews only");}
}
