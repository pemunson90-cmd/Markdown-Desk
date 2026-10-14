package com.example.markdowndesk;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;

/** Explicit pressure previews, using native writes and actual quota failures. No unlink tests. */
public final class HistoryPressureCheck {
    static int checks;
    static void check(boolean value,String reason){checks++;if(!value)throw new AssertionError(reason);}
    static HistoryPruner.DeletePlan planned(HistoryPruner.Result r){check(r.plan!=null&&r.blockedReason==null,"expected plan, got "+r.blockedReason);return r.plan;}
    static void blocked(HistoryPruner.Result r,HistoryPruner.BlockedReason reason){check(r.plan==null&&r.blockedReason==reason,"expected "+reason+", got "+r.blockedReason);}
    static void regular()throws Exception {
        HistoryPrunerCheck.Fixture f=new HistoryPrunerCheck.Fixture();
        ProtectionStore.Transaction oldest=f.group("content://a",true,true), middle=f.group("content://a",true,true), newest=f.group("content://a",true,true);
        Map<String,String> before=f.capture();
        HistoryPruner.DeletePlan floor=planned(f.pruner(1).planAutomatic(1,1)), pressure=planned(f.pruner(1).planAutomaticPressure(1,1));
        check(floor.mode==HistoryPruner.Mode.AUTOMATIC_FLOOR&&pressure.mode==HistoryPruner.Mode.AUTOMATIC_PRESSURE&&pressure.automatic,"explicit automatic modes");
        check(floor.graphFingerprint.equals(pressure.graphFingerprint)&&floor.groups.get(0).transactionId.equals(pressure.groups.get(0).transactionId),"same graph and selected group across modes");
        check(!floor.proofFingerprint.equals(pressure.proofFingerprint),"mode separates otherwise identical proof");
        check(floor.proofFingerprint.equals(legacyFingerprint(floor))&&floor.proofBytes==legacySize(floor),"legacy floor canonical bytes preserved");
        check(pressure.proofFingerprint.equals(planned(f.pruner(1).planAutomaticPressure(1,1)).proofFingerprint),"pressure proof deterministic");
        HistoryPruner.DeletePlan changedDemand=planned(f.pruner(1).planAutomaticPressure(2,1));
        check(changedDemand.groups.get(0).transactionId.equals(oldest.id)&&!changedDemand.proofFingerprint.equals(pressure.proofFingerprint),"actual demand changes proof even with same selection");
        for(int count:new int[]{5,10,20,50}){
            blocked(f.pruner(count).planAutomatic(1,1),HistoryPruner.BlockedReason.CAPACITY_SHORTFALL);
            HistoryPruner.DeletePlan p=planned(f.pruner(count).planAutomaticPressure(1,1));
            check(p.groups.size()==1&&p.groups.get(0).transactionId.equals(oldest.id)&&p.anchors.get(0).transactionId.equals(newest.id),"pressure below desired count preserves newest anchor");
        }
        HistoryPruner.Result shortfall=f.pruner(20).planAutomaticPressure(1,11);
        blocked(shortfall,HistoryPruner.BlockedReason.CAPACITY_SHORTFALL);
        check(shortfall.eligibleEntries==10&&shortfall.eligibleBytes>0&&shortfall.protectedEntries==6,"atomic shortfall reports all capacity and identity/anchor protection");
        HistoryPruner.DeletePlan all=planned(f.pruner(20).planAutomaticPressure(shortfall.eligibleBytes,shortfall.eligibleEntries));
        check(all.groups.size()==2&&all.groups.get(0).transactionId.equals(oldest.id)&&all.groups.get(1).transactionId.equals(middle.id),"oldest persisted sequence order until demand satisfied");
        check(all.members.size()==10&&all.proofBytes<=HistoryPruner.MAX_PROOF_BYTES,"bounded whole groups");
        blocked(f.pruner(0).planAutomaticPressure(1,1),HistoryPruner.BlockedReason.AUTOMATIC_DISABLED);
        blocked(f.pruner(0).planAutomaticPressure(0,0),HistoryPruner.BlockedReason.AUTOMATIC_DISABLED);
        blocked(f.pruner(20).planAutomaticPressure(-1,0),HistoryPruner.BlockedReason.INVALID_REQUEST);
        blocked(f.pruner(20).planAutomaticPressure(0,-1),HistoryPruner.BlockedReason.INVALID_REQUEST);
        blocked(f.pruner(20).planAutomaticPressure(Long.MAX_VALUE,Long.MAX_VALUE),HistoryPruner.BlockedReason.CAPACITY_SHORTFALL);
        check(planned(f.pruner(20).planAutomaticPressure(0,0)).members.isEmpty(),"no requested pressure removes no groups");
        check(before.equals(f.capture()),"all pressure previews preserve every envelope");
        HistoryCatalog.Checkpoint label=f.catalog.name(oldest.attempted.id,"named");
        HistoryPruner.DeletePlan selected=planned(f.pruner(0).planSelected(Arrays.asList(label.id)));
        check(selected.mode==HistoryPruner.Mode.SELECTED&&selected.proofFingerprint.equals(legacyFingerprint(selected))&&selected.proofBytes==legacySize(selected),"selected v1 proof bytes preserved");
        check(planned(f.pruner(20).planAutomaticPressure(1,1)).groups.get(0).transactionId.equals(middle.id),"named oldest protected");
        Set<String> pins=new HashSet<>(Arrays.asList(middle.original.id));
        HistoryPruner pinned=new HistoryPruner(f.graph(20),pins);pins.clear();
        HistoryPruner.Result noEligible=pinned.planAutomaticPressure(1,1);
        blocked(noEligible,HistoryPruner.BlockedReason.CAPACITY_SHORTFALL);
        check(noEligible.eligibleBytes==0&&noEligible.eligibleEntries==0,"named and externally pinned groups cannot supply partial pressure");
        HistoryPruner.DeletePlan anchorPin=planned(new HistoryPruner(f.graph(20),Arrays.asList(newest.original.id)).planAutomaticPressure(1,1));
        check(anchorPin.externalProtectedSnapshotIds.contains(newest.original.id),"external pin snapshot bound");
        blocked(new HistoryPruner(f.graph(20),Arrays.asList(UUID.randomUUID().toString())).planAutomaticPressure(1,1),HistoryPruner.BlockedReason.UNKNOWN_EXTERNAL_PIN);
        blocked(new HistoryPruner(new HistoryGraph(f.store,f.catalog,f.identity,f.index,null)).planAutomaticPressure(1,1),HistoryPruner.BlockedReason.MISSING_POLICY);
        for(String root:Arrays.asList("store","catalog","identity","index")){
            Path stage=f.parent.resolve(root).resolve(UUID.randomUUID()+".stage");Files.write(stage,new byte[]{7});
            Map<String,String> staged=f.capture();blocked(f.pruner(20).planAutomaticPressure(1,1),HistoryPruner.BlockedReason.INCOMPLETE_GRAPH);
            check(staged.equals(f.capture()),"pressure preserves retained stage in "+root);Files.delete(stage);
        }
        HistoryPrunerCheck.Fixture protectedF=new HistoryPrunerCheck.Fixture();
        ProtectionStore.Transaction shared=protectedF.group("content://a",true,true);
        ProtectionStore.Snapshot attempted=protectedF.store.putSnapshot("tab","content://a","title",1,new byte[]{9},"attempted");
        protectedF.pair("content://a",shared.original,attempted,false,false);
        protectedF.group("content://a",true,false); // Unindexed completion.
        protectedF.store.putSnapshot("tab","content://a",null,1,new byte[0],"original"); // Orphan.
        protectedF.group("content://a",true,true); // Sole fixed anchor for this history.
        protectedF.group("content://other",true,true); // Another history cannot replace the anchor.
        Map<String,String> protectedBefore=protectedF.capture();
        HistoryPruner.Result protectedResult=protectedF.pruner(20).planAutomaticPressure(1,1);
        blocked(protectedResult,HistoryPruner.BlockedReason.CAPACITY_SHORTFALL);
        check(protectedResult.eligibleEntries==0&&protectedResult.protectedEntries>0&&protectedBefore.equals(protectedF.capture()),"shared/pending/unindexed/orphan and per-history anchors protected");
        HistoryPrunerCheck.Fixture bounded=new HistoryPrunerCheck.Fixture();
        for(int i=0;i<53;i++)bounded.group("content://a",true,true);
        HistoryPruner.Result capacity=bounded.pruner(20).planAutomaticPressure(Long.MAX_VALUE,0);
        blocked(capacity,HistoryPruner.BlockedReason.CAPACITY_SHORTFALL);
        blocked(bounded.pruner(20).planAutomaticPressure(capacity.eligibleBytes,capacity.eligibleEntries),HistoryPruner.BlockedReason.PLAN_LIMIT);
        check(planned(bounded.pruner(20).planAutomaticPressure(1,1)).members.size()==5,"small pressure plan allowed despite larger eligible capacity");
        HistoryPrunerCheck.Fixture proofBound=new HistoryPrunerCheck.Fixture();
        for(int i=0;i<30;i++){proofBound.group("content://history/"+i,true,true);proofBound.group("content://history/"+i,true,true);}
        blocked(proofBound.pruner(20).planAutomaticPressure(0,150),HistoryPruner.BlockedReason.PLAN_LIMIT);
    }
    static void memory()throws Exception {
        final long quota=64L*1024*1024;
        final String uri="content://pressure-large";
        HistoryPrunerCheck.Fixture f=new HistoryPrunerCheck.Fixture();
        byte[] payload=new byte[ProtectionStore.MAX_DOCUMENT_BYTES-1];
        List<ProtectionStore.Transaction> complete=new ArrayList<>();
        long attemptedOverhead=0;
        for(int i=0;i<3;i++){
            ProtectionStore.Snapshot a=f.store.putSnapshot("tab",uri,null,1,payload,"original");
            ProtectionStore.Snapshot b=f.store.putSnapshot("tab",uri,null,1,payload,"attempted");
            attemptedOverhead=Files.size(f.parent.resolve("store").resolve(b.id+".snapshot"))-payload.length;
            complete.add(f.pair(uri,a,b,true,true));
        }
        ProtectionStore.Snapshot fourthOriginal=f.store.putSnapshot("tab",uri,null,1,payload,"original");payload=null;
        long occupied=0;try(java.util.stream.Stream<Path> paths=Files.list(f.parent.resolve("store"))){for(Path path:(Iterable<Path>)paths::iterator)occupied+=Files.size(path);}
        int attemptedBytes=Math.toIntExact(quota-occupied-attemptedOverhead-100);
        check(attemptedBytes<=ProtectionStore.MAX_DOCUMENT_BYTES&&attemptedBytes>ProtectionStore.MAX_DOCUMENT_BYTES-16*1024,"fourth attempted source remains near 8MiB");
        ProtectionStore.Snapshot fourthAttempted=f.store.putSnapshot("tab",uri,null,1,new byte[attemptedBytes],"attempted");
        Map<String,String> before=f.capture();
        try{f.store.prepare(fourthOriginal,fourthAttempted,fourthOriginal.hash);throw new AssertionError("fourth preparation should fail quota");}
        catch(IOException expected){check("Protection quota exhausted".equals(expected.getMessage()),"real native fourth preparation is quota blocked");}
        byte[] headroom=new byte[32*1024*1024];
        HistoryGraph.Graph graph=f.graph(20).scan();
        check(graph.complete&&graph.chargedBytes>quota-16*1024&&graph.orphanSnapshotIds.containsAll(Arrays.asList(fourthOriginal.id,fourthAttempted.id)),"near64MiB graph preserves failed-save sources as orphans");
        // Future caller computes exact shared-capacity shortfall from serialized peak demand.
        // The native store above still uses its existing per-root quota, without admission wiring.
        long peak=Files.size(f.parent.resolve("store").resolve(complete.get(0).id+".transaction"))*2;
        long requested=Math.max(0,Math.addExact(graph.chargedBytes,peak)-quota);
        blocked(f.pruner(20).planAutomatic(requested,2),HistoryPruner.BlockedReason.CAPACITY_SHORTFALL);
        HistoryPruner.DeletePlan p=planned(f.pruner(20).planAutomaticPressure(requested,2));
        check(p.groups.size()==1&&p.groups.get(0).transactionId.equals(complete.get(0).id)&&p.anchors.get(0).transactionId.equals(complete.get(2).id),"pressure reclaims oldest paired near8MiB group while retaining newest");
        check(p.bytes>2L*(ProtectionStore.MAX_DOCUMENT_BYTES-1)&&p.members.size()==5&&p.requiredBytes==requested&&p.proofBytes<=HistoryPruner.MAX_PROOF_BYTES,"exact requested capacity, serialized bytes and bounded proof");
        check(p.members.stream().noneMatch(r->r.id.equals(fourthOriginal.id)||r.id.equals(fourthAttempted.id))&&before.equals(f.capture()),"pressure never mutates or plans failed-save orphan sources");
        check(headroom[headroom.length-1]==0,"128MiB heap/32MiB retained headroom remains live");
    }
    // Independent legacy v1 serialization reference: catches any change to existing proof bytes.
    static byte[] legacyProof(HistoryPruner.DeletePlan p)throws Exception {
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();try(DataOutputStream out=new DataOutputStream(bytes)){
            text(out,"history-retention-preview-v1");text(out,p.graphFingerprint);out.writeBoolean(p.automatic);
            out.writeLong(p.requiredBytes);out.writeLong(p.requiredEntries);out.writeLong(p.bytes);out.writeLong(p.entries);
            out.writeLong(p.policy.quotaBytes);out.writeInt(p.policy.automaticCount);
            strings(out,p.externalProtectedSnapshotIds);strings(out,p.selectedCheckpointIds);
            out.writeInt(p.members.size());for(ProtectionStore.StoredRecord r:p.members)record(out,r);
            out.writeInt(p.groups.size());for(HistoryPruner.GroupProof g:p.groups)group(out,g);
            out.writeInt(p.anchors.size());for(HistoryPruner.GroupProof g:p.anchors)group(out,g);
            out.writeInt(p.identities.size());for(HistoryIdentity.Descriptor i:p.identities){text(out,i.id);text(out,i.exactUri);text(out,i.tabId);strings(out,new TreeSet<>(i.origins));}
            out.writeInt(p.entriesByRoot.size());for(String role:p.entriesByRoot.keySet()){text(out,role);out.writeLong(p.entriesByRoot.get(role));out.writeLong(p.bytesByRoot.get(role));}
        }return bytes.toByteArray();
    }
    static String legacyFingerprint(HistoryPruner.DeletePlan p)throws Exception{return HistoryStorage.hex(MessageDigest.getInstance("SHA-256").digest(legacyProof(p)));}
    static int legacySize(HistoryPruner.DeletePlan p)throws Exception{return legacyProof(p).length;}
    static void group(DataOutputStream out,HistoryPruner.GroupProof g)throws IOException {
        text(out,g.historyId);text(out,g.ownershipId);text(out,g.transactionId);text(out,g.originalSnapshotId);text(out,g.attemptedSnapshotId);out.writeLong(g.sequence);
        strings(out,g.checkpointIds);out.writeInt(g.records.size());for(ProtectionStore.StoredRecord r:g.records)record(out,r);
        out.writeInt(g.references.size());for(HistoryPruner.Reference r:g.references){text(out,r.sourceRole);text(out,r.sourceId);text(out,r.sourceKind);text(out,r.targetRole);text(out,r.targetId);text(out,r.targetKind);}
    }
    static void record(DataOutputStream out,ProtectionStore.StoredRecord r)throws IOException{text(out,r.rootRole);text(out,r.filename);text(out,r.id);text(out,r.kind);out.writeLong(r.serializedBytes);text(out,r.envelopeHash);}
    static void strings(DataOutputStream out,Collection<String> values)throws IOException{out.writeInt(values.size());for(String value:values)text(out,value);}
    static void text(DataOutputStream out,String value)throws IOException{if(value==null){out.writeInt(-1);return;}byte[] bytes=value.getBytes(StandardCharsets.UTF_8);out.writeInt(bytes.length);out.write(bytes);}
    public static void main(String[] args)throws Exception{if(args.length==0)regular();else memory();System.out.println("PASS explicit pressure planner fixtures: "+checks+" checks; pure host previews only");}
}
