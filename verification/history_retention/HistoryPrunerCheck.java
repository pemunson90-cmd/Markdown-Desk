package com.example.markdowndesk;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Native on-disk groups and graph proofs; planner calls never unlink or publish records. */
public final class HistoryPrunerCheck {
    private static int checks;
    private static void check(boolean ok,String reason){checks++;if(!ok)throw new AssertionError(reason);}
    private static HistoryPruner planner(HistoryGraphCheck.Fixture f,int count)throws Exception {
        return new HistoryPruner(new HistoryGraph(f.store,f.catalog,f.identity,f.index,new HistoryPolicy(64L*1024*1024,count)));
    }
    private static ProtectionStore.Transaction group(HistoryGraphCheck.Fixture f,String uri,boolean indexed,boolean complete,int bytes)throws Exception {
        String id=f.identity.resolve(uri,"tab");
        byte[] payload=new byte[bytes];
        ProtectionStore.Snapshot a=f.store.putSnapshot("tab",uri,uri.length()>1000?"t".repeat(3000):"title",1,payload,"original");
        if(bytes>0)payload[0]=1;
        ProtectionStore.Snapshot b=f.store.putSnapshot("tab",uri,uri.length()>1000?"t".repeat(3000):"title",1,payload,"attempted");
        payload=null;
        ProtectionStore.Transaction t=f.store.prepare(a,b,a.hash);if(complete)f.store.complete(t);
        if(indexed)f.index.recordVerified(id,t.id);
        return t;
    }
    private static Set<String> ids(HistoryPruner.DeletePlan p){Set<String> ids=new HashSet<>();for(HistoryPruner.GroupProof g:p.groups)ids.add(g.transactionId);return ids;}
    private static void blocked(HistoryPruner.DeletePlan p,String reason){check(p.blocked&&p.members.isEmpty()&&p.groups.isEmpty(),reason+": zero members");}
    private static void closure(HistoryGraphCheck.Fixture f,HistoryPruner.DeletePlan p)throws Exception {
        long bytes=0;Map<String,Integer> roots=new HashMap<>();Set<String> unique=new HashSet<>();
        for(ProtectionStore.StoredRecord r:p.members){check(unique.add(r.rootRole+"/"+r.filename),"unique member");Path path=f.parent.resolve(r.rootRole).resolve(r.filename);
            check(Files.size(path)==r.serializedBytes&&HistoryGraphCheck.hash(path).equals(r.envelopeHash),"exact full envelope proof");bytes+=r.serializedBytes;roots.merge(r.rootRole,1,Integer::sum);}
        check(bytes==p.reclaimedBytes&&roots.equals(p.reclaimedEntriesByRoot),"exact reclaimed accounting");
        check(p.reclaimedStoreEntries==roots.getOrDefault("store",0),"requested entry capacity counts store slots only");
        check(p.proofBytes<=65536&&p.members.size()<=256,"bounded future intent proof");
        for(HistoryPruner.GroupProof g:p.groups){check(g.sequence>0&&!g.transactionId.equals(g.survivor.transactionId),"separate persisted-sequence survivor");
            check(g.historyId.equals(g.survivor.historyId)&&g.sequence<g.survivor.sequence,"local anchor same history/newer sequence");
            check(g.original.id.equals(g.originalSnapshotId)&&g.attempted.id.equals(g.attemptedSnapshotId),"full metadata proof");}
    }
    private static void basic()throws Exception {
        HistoryGraphCheck.Fixture f=new HistoryGraphCheck.Fixture();ProtectionStore.Transaction old=group(f,"content://a",true,true,3), newest=group(f,"content://a",true,true,4);
        HistoryPruner p=planner(f,0);Map<Path,String> before=HistoryGraphCheck.capture(f);HistoryPruner.DeletePlan plan=p.planAutomatic(0,0);
        check(!plan.blocked&&plan.members.size()==5&&ids(plan).equals(Collections.singleton(old.id)),"count zero prunes older only");closure(f,plan);
        check(plan.requestedBytes==0&&plan.requestedStoreEntries==0,"requested capacity retained");
        check(plan.eligibleBytes==plan.reclaimedBytes&&plan.protectedBytes+plan.eligibleBytes==plan.chargedBytes,"eligible/protected accounting");
        check(p.isCurrent(plan),"fresh plan comparison");check(before.equals(HistoryGraphCheck.capture(f)),"all plan/compare calls preserve native bytes");
        check(planner(f,5).planAutomatic(0,0).members.isEmpty(),"under desired count normal no-op");
        HistoryPruner.DeletePlan capacity=planner(f,20).planAutomatic(1,4);check(!capacity.blocked&&ids(capacity).equals(Collections.singleton(old.id)),"capacity reclaims older within desired count");
        check(capacity.reclaimedEntriesByRoot.get("index")==1&&capacity.reclaimedStoreEntries==4,"per-root freed entries");
        blocked(planner(f,20).planAutomatic(capacity.reclaimedBytes+1,0),"capacity shortfall");
        blocked(planner(f,20).planAutomatic(0,5),"store slot shortfall");
        blocked(p.planAutomatic(Long.MAX_VALUE,0),"long demand shortfall");blocked(p.planAutomatic(-1,0),"invalid capacity");
        HistoryCatalog.Checkpoint label=f.catalog.name(old.attempted.id,"keep");check(!p.isCurrent(plan),"changed pin invalidates preview");
        check(p.planAutomatic(0,0).members.isEmpty(),"named group protected");
        HistoryPruner.DeletePlan selected=p.planSelected(Collections.singleton(label.id));check(!selected.blocked&&selected.members.size()==6,"exact selected named group closure");closure(f,selected);check(p.isCurrent(selected),"exact selected preview remains current");
        check(selected.selectedCheckpoints.size()==1&&selected.selectedCheckpoints.get(0).label.equals("keep"),"selected label proof");
        HistoryCatalog.Checkpoint label2=f.catalog.name(old.original.id,"both sides");blocked(p.planSelected(Collections.singleton(label.id)),"all pins must be selected");
        selected=p.planSelected(new HashSet<>(Arrays.asList(label.id,label2.id)));check(!selected.blocked&&selected.members.size()==7,"both exact checkpoint pins allowed");
        HistoryCatalog.Checkpoint newestLabel=f.catalog.name(newest.attempted.id,"newest");blocked(p.planSelected(Collections.singleton(newestLabel.id)),"newest explicit deletion rejected");
        blocked(p.planSelected(Collections.singleton(UUID.randomUUID().toString())),"unknown label");
        blocked(p.planSelected(Collections.emptySet()),"empty selected deletion");
        blocked(p.planSelected(null),"null selection");blocked(p.planSelected(Collections.singleton(null)),"null checkpoint selection");
        try{selected.members.clear();throw new AssertionError("mutable plan");}catch(UnsupportedOperationException expected){checks++;}
        try{selected.reclaimedEntriesByRoot.clear();throw new AssertionError("mutable accounting");}catch(UnsupportedOperationException expected){checks++;}
    }
    private static void protectedRoots()throws Exception {
        HistoryGraphCheck.Fixture f=new HistoryGraphCheck.Fixture();HistoryPruner p=planner(f,0);
        ProtectionStore.Transaction lone=group(f,"content://only",true,true,1);HistoryCatalog.Checkpoint loneName=f.catalog.name(lone.attempted.id,"sole");
        group(f,"content://different",true,true,1);blocked(p.planSelected(Collections.singleton(loneName.id)),"cross-history survivor never counts");
        ProtectionStore.Transaction pending=group(f,"content://pending",false,false,1), unindexed=group(f,"content://unindexed",false,true,1);
        ProtectionStore.Snapshot orphan=f.store.putSnapshot("tab",null,null,1,new byte[]{4},"attempted");
        HistoryCatalog.Checkpoint pendingName=f.catalog.name(pending.attempted.id,"pending"), unindexedName=f.catalog.name(unindexed.attempted.id,"unindexed"), orphanName=f.catalog.name(orphan.id,"orphan");
        for(HistoryCatalog.Checkpoint c:Arrays.asList(pendingName,unindexedName,orphanName))blocked(p.planSelected(Collections.singleton(c.id)),"unresolved/unindexed/orphan protected");
        check(p.planAutomatic(0,0).members.isEmpty(),"every protected root retained");
        for(String root:Arrays.asList("store","catalog","identity","index")) {
            Path stage=f.parent.resolve(root).resolve(UUID.randomUUID()+".stage");Files.write(stage,new byte[]{7});Map<Path,String> before=HistoryGraphCheck.capture(f);
            blocked(p.planAutomatic(1,0),"retained stage blocks all plans");check(before.equals(HistoryGraphCheck.capture(f)),"stage byte preservation");Files.delete(stage);
        }
        Path bad=f.parent.resolve("store/unknown");Files.write(bad,new byte[]{9});blocked(p.planAutomatic(0,0),"unknown graph no members");Files.delete(bad);
        Path completion=f.parent.resolve("store").resolve(lone.id+".complete");byte[] saved=Files.readAllBytes(completion);Files.write(completion,new byte[]{1});
        blocked(p.planAutomatic(0,0),"corrupt graph no members");Files.write(completion,saved);
    }
    private static void sharedSnapshots()throws Exception {
        HistoryGraphCheck.Fixture f=new HistoryGraphCheck.Fixture();String h=f.identity.resolve("content://shared","tab");
        ProtectionStore.Transaction a=group(f,"content://shared",true,true,2);
        ProtectionStore.Snapshot next=f.store.putSnapshot("tab","content://shared",null,1,new byte[]{3},"attempted");
        ProtectionStore.Transaction b=f.store.prepare(a.original,next,a.original.hash);f.store.complete(b);f.index.recordVerified(h,b.id);
        group(f,"content://shared",true,true,2);HistoryPruner p=planner(f,0);
        check(p.planAutomatic(0,0).members.isEmpty(),"shared snapshots make both groups ineligible");
        HistoryCatalog.Checkpoint c=f.catalog.name(a.original.id,"shared");blocked(p.planSelected(Collections.singleton(c.id)),"explicit shared-group rejection");
    }
    private static void orderingAndStaleness()throws Exception {
        HistoryGraphCheck.Fixture f=new HistoryGraphCheck.Fixture();List<ProtectionStore.Transaction> groups=new ArrayList<>();
        for(int i=0;i<7;i++)groups.add(group(f,"content://ordered",true,true,1));
        HistoryPruner p=planner(f,5);HistoryPruner.DeletePlan count=p.planAutomatic(0,0);
        check(!count.blocked&&count.groups.size()==2&&count.groups.get(0).transactionId.equals(groups.get(0).id)&&count.groups.get(1).transactionId.equals(groups.get(1).id),"oldest persisted sequences beyond desired count first");
        HistoryPruner.DeletePlan capacity=p.planAutomatic(count.reclaimedBytes+1,0);check(!capacity.blocked&&capacity.groups.size()==3,"capacity adds next older group within desired count");
        for(HistoryPruner.GroupProof g:capacity.groups)check(g.survivor.transactionId.equals(groups.get(6).id),"fixed newest anchor retained");
        HistoryPruner differentPolicy=planner(f,10);check(!differentPolicy.isCurrent(count),"different graph provider rejects preview");
        f.identity.forkAfterVerifiedSaveAs(count.groups.get(0).historyId,"content://fork");check(!p.isCurrent(count),"routing/provenance change rejects preview");
        HistoryPruner.DeletePlan fresh=p.planAutomatic(0,0);ProtectionStore.Transaction extra=group(f,"content://ordered",true,true,1);check(!p.isCurrent(fresh),"new ownership/survivor rejects preview");
        fresh=p.planAutomatic(0,0);Path anchor=f.parent.resolve("store").resolve(extra.attempted.id+".snapshot");byte[] bytes=Files.readAllBytes(anchor);Files.write(anchor,new byte[]{3});check(!p.isCurrent(fresh),"changed newest surviving anchor rejects preview");Files.write(anchor,bytes);
        Map<Path,String> before=HistoryGraphCheck.capture(f);p.planAutomatic(0,0);check(before.equals(HistoryGraphCheck.capture(f)),"all ordering/staleness planning read-only");
    }
    private static void limits()throws Exception {
        HistoryGraphCheck.Fixture f=new HistoryGraphCheck.Fixture();ProtectionStore.Transaction old=group(f,"content://limit",true,true,0);group(f,"content://limit",true,true,0);
        Set<String> labels=new HashSet<>();for(int i=0;i<252;i++)labels.add(f.catalog.name(old.attempted.id,"n"+i).id);
        HistoryPruner.DeletePlan over=planner(f,0).planSelected(labels);blocked(over,"256 member bound requires bounded replan");
        check(over.blockedReasons.contains(HistoryPruner.BlockedReason.PLAN_LIMIT),"bound reason explicit");
        Set<String> oversizedSelection=new HashSet<>();for(int i=0;i<257;i++)oversizedSelection.add(UUID.randomUUID().toString());
        blocked(planner(f,0).planSelected(oversizedSelection),"oversized input bounded before selected proof copy");
        HistoryGraphCheck.Fixture proof=new HistoryGraphCheck.Fixture();String uri="content://"+"u".repeat(3000);
        for(int i=0;i<4;i++)group(proof,uri,true,true,0);
        HistoryPruner.DeletePlan largeProof=planner(proof,0).planAutomatic(0,0);
        check(!largeProof.blocked&&!largeProof.groups.isEmpty()&&largeProof.groups.size()<3&&more(largeProof),"64KiB proof bound returns oldest count batch below256 members");
        closure(proof,largeProof);
    }
    private static boolean more(HistoryPruner.DeletePlan p)throws Exception {
        try{return p.getClass().getField("moreCountMaintenance").getBoolean(p);}
        catch(NoSuchFieldException missing){throw new AssertionError("bounded count maintenance continuation flag absent",missing);}
    }
    private static void mixedHistoryOrder()throws Exception {
        HistoryGraphCheck.Fixture f=new HistoryGraphCheck.Fixture();
        ProtectionStore.Transaction oldest=group(f,"content://B",true,true,1);group(f,"content://B",true,true,1);
        for(int i=0;i<6;i++)group(f,"content://A",true,true,1);
        HistoryPruner.DeletePlan p=planner(f,5).planAutomatic(1,0);
        check(!p.blocked&&p.groups.size()==1&&p.groups.get(0).transactionId.equals(oldest.id),"positive pressure selects globally oldest B1 instead of count-excess A3");
    }
    private static void boundedCount()throws Exception {
        HistoryGraphCheck.Fixture f=new HistoryGraphCheck.Fixture();List<ProtectionStore.Transaction> groups=new ArrayList<>();
        for(int i=0;i<56;i++)groups.add(group(f,"content://batch",true,true,0));
        HistoryPruner planner=planner(f,0);Map<Path,String> before=HistoryGraphCheck.capture(f);
        HistoryPruner.DeletePlan p=planner.planAutomatic(0,0);
        check(!p.blocked&&!p.groups.isEmpty()&&p.groups.size()<55,"large count-only history produces one bounded nonempty batch");
        check(more(p),"remaining count maintenance explicit");closure(f,p);
        for(int i=0;i<p.groups.size();i++)check(p.groups.get(i).transactionId.equals(groups.get(i).id),"bounded count batch contains oldest prefix");
        check(planner.isCurrent(p),"bound continuation proof stays current");
        HistoryPruner.DeletePlan capacity=planner.planAutomatic(p.eligibleBytes,0);
        blocked(capacity,"capacity not fulfilled by one bounded batch yields no partial permission");
        check(capacity.blockedReasons.contains(HistoryPruner.BlockedReason.PLAN_LIMIT),"capacity batch bound explicit");
        check(before.equals(HistoryGraphCheck.capture(f)),"bounded previews preserve every native record");
    }
    private static void persistedPolicy()throws Exception {
        HistoryGraphCheck.Fixture f=new HistoryGraphCheck.Fixture();group(f,"content://policy",true,true,1);group(f,"content://policy",true,true,1);
        HistoryPolicy initial=new HistoryPolicy(64L*1024*1024,0);initial.configure(f.store,f.catalog,f.identity,f.index);
        HistoryGraph graph=new HistoryGraph(f.store,f.catalog,f.identity,f.index,initial);HistoryPruner planner=new HistoryPruner(graph);
        HistoryPruner.DeletePlan before=planner.planAutomatic(0,0);check(!before.blocked&&planner.isCurrent(before),"same graph starts with current persisted policy proof");
        new HistoryPolicy(128L*1024*1024,5).apply(f.store);
        check(!planner.isCurrent(before),"same Graph durable policy mutation rejects exact old plan");
        HistoryPruner.DeletePlan after=planner.planAutomatic(0,0);check(after.policy.quotaBytes==128L*1024*1024&&after.policy.automaticCount==5&&after.members.isEmpty(),"same graph observes durable effective policy after change");
    }
    private static void memory()throws Exception {
        HistoryGraphCheck.Fixture f=new HistoryGraphCheck.Fixture();List<ProtectionStore.Transaction> groups=new ArrayList<>();
        for(int i=0;i<3;i++)groups.add(group(f,"content://large",true,true,ProtectionStore.MAX_DOCUMENT_BYTES));
        byte[] headroom=new byte[32*1024*1024];HistoryPruner p=planner(f,20);Map<Path,String> before=HistoryGraphCheck.capture(f);
        HistoryPruner.DeletePlan plan=p.planAutomatic(16L*1024*1024,4);
        check(!plan.blocked&&plan.groups.size()==1&&plan.groups.get(0).transactionId.equals(groups.get(0).id),"8MiB repeated pairs plan safe reclaim within default20 count");
        closure(f,plan);check(p.isCurrent(plan)&&before.equals(HistoryGraphCheck.capture(f)),"near-limit planning preserves every actual payload");
        check(headroom[0]==0,"128MiB heap and retained32MiB headroom");
    }
    public static void main(String[] args)throws Exception {
        if(args.length>0&&args[0].equals("memory"))memory();else if(args.length>0&&args[0].equals("batch"))boundedCount();else if(args.length>0&&args[0].equals("policy"))persistedPolicy();else{mixedHistoryOrder();boundedCount();persistedPolicy();basic();protectedRoots();sharedSnapshots();orderingAndStaleness();limits();}
        System.out.println("PASS pure history planner native fixtures: "+checks+" assertions; zero unlink/write operations");
    }
}
