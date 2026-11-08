package com.example.markdowndesk;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import static com.example.markdowndesk.HistoryPinsCheck.Disk;

/** Canonical historical fixture only: this does not invoke or claim a cold cancellation API. */
public final class TerminalCancelledAdmissionCheck {
    static int cases;
    interface Work {void run()throws Exception;}
    static void yes(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    static void reject(Work work,String why)throws Exception{try{work.run();throw new AssertionError("accepted "+why);}catch(IOException expected){}}
    static final String ZERO="0000000000000000000000000000000000000000000000000000000000000000";
    static final class F {
        final Path parent=Files.createTempDirectory("cancelled-admission-");final Disk disk=new Disk();
        final ProtectionStore store=new ProtectionStore(parent.resolve("store").toFile(),disk,64L<<20);
        final HistoryCatalog catalog=new HistoryCatalog(parent.resolve("catalog").toFile(),store,disk);
        final HistoryIdentity identity=new HistoryIdentity(parent.resolve("identity").toFile(),disk);
        final HistoryIndex index=new HistoryIndex(store,catalog,identity,parent.resolve("index").toFile(),disk);
        final HistoryStorage.Budget budget=new HistoryStorage.Budget(HistoryStorage.METADATA_BUDGET);
        final UUID prep=UUID.randomUUID(),oldPrep=UUID.randomUUID();
        final HistoryCapacity capacity;final HistoryGraph scanner=new HistoryGraph(store,catalog,identity,index,new HistoryPolicy(64L<<20,20));
        final String checkpoint;final HistoryPruner.DeletePlan stale;final HistoryPruneIntent.Authorization auth;
        final PrivateOperationRecord old;final Path stage;final PreparationSources owner;final HistoryPins pins;
        PrivateCapturePlan plan;
        final Object retainedMaintenance;final java.lang.reflect.Method retainedControl;
        F(boolean namespace,boolean oldDirectory,boolean oldOwner)throws Exception {
            String selected=group("old"),anchor=group("anchor");checkpoint=selected;
            capacity=HistoryCapacity.register(store,catalog,identity,index,new HistoryPolicy(64L<<20,20),disk);
            stale=new HistoryPruner(scanner).planSelected(Collections.singleton(selected)).plan;yes(stale!=null,"positive selected plan before cancelled evidence");
            auth=new HistoryPruneIntent.Authorization(stale.proofFingerprint,stale.selectedCheckpointIds);
            Class<?> stableClass=Class.forName("com.example.markdowndesk.HistoryMaintenanceExecutor$Stable");
            java.lang.reflect.Constructor<?> constructor=stableClass.getDeclaredConstructor(HistoryMaintenanceExecutor.class,HistoryControlStore.Inspection.class);constructor.setAccessible(true);
            Object[] retained={null};locked(()->retained[0]=constructor.newInstance(new HistoryMaintenanceExecutor(store,catalog,identity,index,disk),control()));retainedMaintenance=retained[0];
            retainedControl=stableClass.getDeclaredMethod("control",HistoryControlStore.Inspection.class,boolean.class);retainedControl.setAccessible(true);
            old=record(oldPrep,3);stage=parent.resolve("private_operations/"+oldPrep+"/"+old.artifact(1).filename()+".stage");
            owner=PreparationSources.forPreparation(store.storageRoot(),oldOwner?oldPrep:prep,budget,PreparationSources.newProducer());
            if(oldOwner)reject(()->owner.open(()->{throw new IOException("uncertain historical source factory");}),"source uncertainty");
            Files.write(parent.resolve("history_control/"+old.name()),old.encoded());Files.write(parent.resolve("history_control/"+old.begin.name()),HistoryPinRecord.encode(old.begin));
            Files.write(parent.resolve("history_control/pin-"+old.operation+".close"),HistoryPinRecord.close(old.begin,HistoryPinRecord.Outcome.CANCELLED));
            if(namespace)Files.createDirectory(parent.resolve("private_operations"));
            if(oldDirectory){Files.createDirectory(stage.getParent());Files.write(stage,new byte[]{1,2,3});}
            capacity.reconcile();pins=HistoryPins.open(store,catalog,identity,index,disk);
            budget.charge(1L<<20); // concurrent caller-owned snapshots for this small fixture
        }
        String group(String label)throws Exception {
            ProtectionStore.Snapshot a=store.putSnapshot(label,"content://cancel","title",1,new byte[]{1},"original"),b=store.putSnapshot(label,"content://cancel","title",1,new byte[]{2},"attempted");
            ProtectionStore.Transaction t=store.prepare(a,b,a.hash);store.complete(t);String h=identity.resolve("content://cancel",label);index.recordVerified(h,t.id);return catalog.name(a.id,label).id;
        }
        PrivateOperationRecord record(UUID id,int mask)throws IOException {
            HistoryControlStore.Inspection c=new HistoryControlStore(parent.toFile()).inspect();
            PrivateOperationRecord.Binding b=new PrivateOperationRecord.Binding(id,1,c.registration.bundleId,c.registration.envelopeHash,c.latest.generation,c.latest.envelopeHash,64L<<20,c.latest.quotaBytes,c.latest.automaticCount,c.registration.roles,ZERO,ZERO,0);
            PrivateOperationRecord.Artifact m=new PrivateOperationRecord.Artifact(1,UUID.randomUUID(),PrivateOperationRecord.ZERO,0,512,ZERO);
            PrivateOperationRecord d=PrivateOperationRecord.descriptor(b,m,Collections.emptyList());
            PrivateOperationRecord.Artifact dr=new PrivateOperationRecord.Artifact(2,UUID.randomUUID(),PrivateOperationRecord.ZERO,0,d.encoded().length,d.envelopeHash);
            HistoryPinRecord begin=HistoryPinRecord.begin(UUID.randomUUID(),c.registration,c.latest,HistoryPinRecord.Purpose.EXPORT,HistoryPinRecord.Scope.ALL_BUNDLE,new HistoryPinRecord.Manifest(HistoryPinRecord.Domain.LOCAL_CAPTURE_V1,0,d.encoded().length,d.envelopeHash),Collections.emptyList());
            return PrivateOperationRecord.prepare(b,mask,Arrays.asList(m,dr),begin);
        }
        void locked(Work work)throws Exception{synchronized(HistoryStorage.monitorFor(store.storageRoot())){try(HistoryStorage.MutationScope lease=HistoryStorage.administration(store.storageRoot())){work.run();}}}
        HistoryControlStore.Inspection control()throws IOException{return new HistoryControlStore(parent.toFile()).inspect();}
        PrivateOperationStore.Ledger ledger()throws IOException{return PrivateOperationStore.inspect(store.storageRoot(),control(),budget);}
        void assemble()throws Exception {locked(()->{
            HistoryGraph.Graph graph=scanner.scan();yes(graph.complete&&graph.pruneBlocked,"typed accounting complete with pending pruning block");
            HistoryControlStore.Inspection c=control();PrivateOperationStore.Ledger l=ledger();yes(l.complete&&l.pending&&c.operations.pending,"pending remains true");
            HistoryArchiveManifest map=HistoryArchiveManifest.freeze(graph,c,UUID::randomUUID);
            plan=PrivateCapturePlan.create(store.storageRoot(),catalog.storageRoot(),identity.storageRoot(),index.storageRoot(),map,graph,c,l,owner,prep,UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),Collections.emptyList(),64L<<20,budget);
            yes(PrivateCapturePlan.initialLedgerHash(l).equals(plan.initialLedgerHash),"entire original ledger bound");
        });}
        PrivateCaptureWriter.CapturedBinding publish()throws Exception {assemble();final PrivateCaptureWriter[] writer={null};locked(()->{writer[0]=PrivateCaptureWriter.prepareLocked(plan,store.storageRoot(),disk,budget);writer[0].writeNativeLocked();});return writer[0].commitCapture(pins);}
    }
    static void newCaptureAndActivation()throws Exception {
        F f=new F(true,true,false);byte[] old=Files.readAllBytes(f.stage);long[] credits={0,0};f.locked(()->{PrivateOperationStore.Ledger l=f.ledger();credits[0]=l.reservedBytes;credits[1]=l.reservedEntries;});
        PrivateCaptureWriter.CapturedBinding binding=f.publish();yes(f.plan.prepare.directoryMask==2,"no shared namespace creation credit");
        f.locked(()->{PrivateOperationStore.Ledger l=f.ledger();yes(l.pending&&l.reservedBytes==credits[0]&&l.reservedEntries==credits[1],"all old remaining credits retained");yes(f.control().controlReservedBytes==173+126&&f.control().controlReservedEntries==2,"old unused LINK plus new CLOSE credits retained");});
        HistoryOperations ops=f.pins.operations();HistoryOperations.OperationHandle handle=ops.activateCaptured(binding,f.budget);
        try(InputStream in=ops.openCapturedSource(handle,f.plan.nativeMap.entries.get(0).archiveId)){byte[] scratch=new byte[4096];while(in.read(scratch)!=-1){}}
        yes(Arrays.equals(old,Files.readAllBytes(f.stage)),"old partial bytes preserved");
        HistoryOperations.Quiescence q=ops.quiesce(ops.freeze(handle)).toCompletableFuture().getNow(null);try(InputStream d=ops.capturedManifest(q)){f.pins.close(q,HistoryPinRecord.Outcome.CANCELLED,d);}
        yes(f.scanner.scan().pruneBlocked&&f.control().controlReservedBytes==173&&f.control().controlReservedEntries==1,"old pruning block and unused LINK survive new CLOSE");cases++;
    }
    static void retainedStages()throws Exception {
        for(int mode=0;mode<3;mode++){
            F f=new F(true,true,false);f.publish();byte[] bytes=Files.readAllBytes(f.stage);
            if(mode==0)Files.write(f.stage,new byte[]{3,2,1});else if(mode==1){Path replacement=f.stage.resolveSibling("replacement.tmp");Files.write(replacement,bytes);Files.move(replacement,f.stage,StandardCopyOption.REPLACE_EXISTING);}else Files.delete(f.stage);
            f.locked(()->reject(f::ledger,"old stage mutation/replacement/disappearance after handoff"));cases++;
        }
    }
    static void boundaries()throws Exception {
        F absent=new F(false,false,false);yes(!absent.scanner.scan().complete,"absent mask3 namespace ineligible");absent.locked(()->reject(()->HistoryArchiveManifest.freeze(absent.scanner.scan(),absent.control(),UUID::randomUUID),"absent namespace freeze"));cases++;
        F missingDirectory=new F(true,false,false);PrivateCaptureWriter.CapturedBinding result=missingDirectory.publish();yes(result!=null&&!Files.exists(missingDirectory.stage.getParent()),"missing historical prepdir stays missing and separately funded");missingDirectory.locked(()->yes(missingDirectory.ledger().reservedEntries==3,"old prepdir and two missing artifacts retain three distinct slot credits"));cases++;
        F uncertain=new F(true,true,true);reject(()->PreparationSources.forPreparation(uncertain.store.storageRoot(),uncertain.prep,uncertain.budget,PreparationSources.newProducer()),"uncertain old registered owner cannot reset");uncertain.locked(()->reject(()->uncertain.owner.requireIdleBindingLocked(uncertain.store.storageRoot(),uncertain.prep,uncertain.budget),"old owner cannot bind new request"));cases++;
        F counts=new F(true,true,false);PrivateOperationRecord one=counts.record(UUID.randomUUID(),2);Files.write(counts.parent.resolve("history_control/"+one.name()),one.encoded());yes(counts.control().operations.valid,"old cancelled plus one unfinished control valid");PrivateOperationRecord two=counts.record(UUID.randomUUID(),2);Files.write(counts.parent.resolve("history_control/"+two.name()),two.encoded());yes(!counts.control().operations.valid,"two unfinished still invalid");cases++;
        F released=new F(true,true,false);Files.write(released.parent.resolve("history_control/pin-"+released.old.operation+".close"),HistoryPinRecord.close(released.old.begin,HistoryPinRecord.Outcome.RELEASED));PrivateOperationRecord next=released.record(UUID.randomUUID(),2);Files.write(released.parent.resolve("history_control/"+next.name()),next.encoded());yes(!released.control().operations.valid,"arbitrary RELEASED CLOSE does not receive cancelled unfinished discount");
        F link=new F(true,true,false);Files.write(link.parent.resolve("history_control/"+PrivateOperationRecord.link(link.old).name()+".stage"),PrivateOperationRecord.link(link.old).encoded());yes(!link.scanner.scan().complete,"cancelled LINK.stage not eligible");cases++;
    }
    static void refreshDuringAnotherPreparation()throws Exception {
        F f=new F(true,true,false);PrivateOperationRecord live=f.record(f.prep,2);
        Files.write(f.parent.resolve("history_control/"+live.name()),live.encoded());
        Path mutable=f.parent.resolve("private_operations/"+live.preparation+"/"+live.artifact(1).filename()+".stage");Files.createDirectory(mutable.getParent());Files.write(mutable,new byte[]{5});
        f.locked(()->{HistoryControlStore.Inspection c=f.control();PrivateOperationStore.Ledger l=f.ledger();yes(!PrivateOperationStore.historicalCancelledOnly(c,l),"another unfinished P disables global history-only classification");
            long charge=0;for(String key:l.entries.keySet())charge+=1024L+key.length()*2L;for(HistoryControlStore.Entry e:c.entries)if(PrivateOperationStore.operationEntry(e))charge+=1024L+e.filename.length()*2L;
            f.budget.charge(charge);HistoryStorage.rememberOperationEvidence(f.store.storageRoot(),c,l);
        });
        Files.write(mutable,new byte[]{5,6});f.locked(()->yes(f.ledger().complete,"mutable current prefix was not newly pinned"));
        Files.delete(f.stage);f.locked(()->reject(f::ledger,"refresh during another P cannot forget old immutable stage"));cases++;
        F before=new F(true,true,false);before.assemble();Files.write(before.stage,new byte[]{3,2,1});before.locked(()->reject(()->PrivateCaptureWriter.prepareLocked(before.plan,before.store.storageRoot(),before.disk,before.budget),"old-stage mutation before own P publication"));cases++;
    }
    static void deletionGuards()throws Exception {
        F f=new F(true,true,false);HistoryPruner pruner=new HistoryPruner(f.scanner);
        yes(pruner.planAutomatic(1,1).blockedReason==HistoryPruner.BlockedReason.INCOMPLETE_GRAPH&&pruner.planAutomaticPressure(1,1).blockedReason==HistoryPruner.BlockedReason.INCOMPLETE_GRAPH&&pruner.planSelected(Collections.singleton(f.checkpoint)).blockedReason==HistoryPruner.BlockedReason.INCOMPLETE_GRAPH,"all planner modes refuse historical pending");
        reject(()->new HistoryMaintenanceExecutor(f.store,f.catalog,f.identity,f.index,f.disk).execute(f.capacity,f.stale,f.auth),"stale selected maintenance refuses");
        f.locked(()->{HistoryCapacity.EffectivePolicy p=f.capacity.policy();reject(()->HistoryPruneIntent.create(f.stale,f.scanner.scan(),new HistoryPruneIntent.PolicyBinding(p.bundleId,p.generation,p.controlRecordHash,p.quotaBytes,p.automaticCount),f.auth,ZERO),"intent guard refuses pruning block");});
        f.locked(()->{try{f.retainedControl.invoke(f.retainedMaintenance,f.control(),true);throw new AssertionError("retained retirement validation accepted pending history");}
            catch(java.lang.reflect.InvocationTargetException rejected){yes(rejected.getCause() instanceof IOException&&"Changed operation ledger".equals(rejected.getCause().getMessage()),"retained maintenance/retirement control guard refuses pending");}});
        f.store.putSnapshot("later","uri","ordinary",2,new byte[]{7},"original");yes(f.scanner.scan().pruneBlocked,"ordinary save permitted without unblocking pruning");cases++;
    }
    public static void main(String[] args)throws Exception{newCaptureAndActivation();retainedStages();boundaries();refreshDuringAnotherPreparation();deletionGuards();PrivateCapturePlanCheck.nativeAndEmpty();System.out.println("Terminal cancelled capture admission: "+cases+" groups PASS; explicit fixtures, no cold cancellation API");}
}
