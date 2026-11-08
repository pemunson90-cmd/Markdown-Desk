package com.example.markdowndesk;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import static com.example.markdowndesk.HistoryPinsCheck.*;

/** Actual capture writer publication, not hand-written MDOP evidence. */
public final class PreparationPublicationCheck {
    static int assertions;
    static void yes(boolean condition,String why){assertions++;if(!condition)throw new AssertionError(why);}
    interface Work {void run()throws Exception;}
    static void refuses(Work work,String why)throws Exception{try{work.run();}catch(IOException expected){assertions++;return;}throw new AssertionError(why);}
    static final class Capture {
        final Fixture f=new Fixture();
        final HistoryStorage.Budget budget=new HistoryStorage.Budget(HistoryStorage.METADATA_BUDGET);
        final UUID preparation=UUID.randomUUID();
        final PreparationSources owner=PreparationSources.forPreparation(f.store.storageRoot(),preparation,budget,PreparationSources.newProducer());
        final PrivateCapturePlan plan;
        Capture()throws Exception {
            // This small fixture pre-admits its initial Graph/map/control/ledger owners.
            // It is not a maximum-size aggregate-heap proof.
            budget.charge(1L<<20);
            synchronized(HistoryStorage.monitorFor(f.store.storageRoot())){try(HistoryStorage.MutationScope lease=HistoryStorage.administration(f.store.storageRoot())){
                HistoryControlStore.Inspection control=inspect(f);
                HistoryGraph.Graph graph=new HistoryGraph(f.store,f.catalog,f.identity,f.index,new HistoryPolicy(64L<<20,20)).scan();
                HistoryArchiveManifest map=HistoryArchiveManifest.freeze(graph,control,UUID::randomUUID);
                PrivateOperationStore.Ledger ledger=PrivateOperationStore.inspect(f.store.storageRoot(),control,budget);
                plan=PrivateCapturePlan.create(f.store.storageRoot(),f.catalog.storageRoot(),f.identity.storageRoot(),f.index.storageRoot(),map,graph,control,ledger,owner,preparation,UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),Collections.emptyList(),control.registration.bootstrapQuotaBytes,budget);
            }}
        }
        PrivateCaptureWriter publish(ProtectionStore.Durability durability)throws Exception {
            synchronized(HistoryStorage.monitorFor(f.store.storageRoot())){try(HistoryStorage.MutationScope lease=HistoryStorage.administration(f.store.storageRoot())){
                return PrivateCaptureWriter.prepareLocked(plan,f.store.storageRoot(),durability,budget);
            }}
        }
    }
    static ProtectionStore.Durability durable(){return new Disk();}
    public static void main(String[] args)throws Exception {
        Capture basic=new Capture();PrivateCaptureWriter writer=basic.publish(durable());
        Path control=basic.f.parent.resolve("history_control");
        yes(Arrays.equals(Files.readAllBytes(control.resolve(basic.plan.prepare.name())),basic.plan.prepare.encoded()),"writer persists exact PREPARE");
        yes(!Files.exists(control.resolve(basic.plan.prepare.name()+".stage")),"PREPARE promotion consumes stage");
        yes(!Files.exists(control.resolve(basic.plan.prepare.begin.name())),"PREPARE grants no BEGIN or activation");
        synchronized(HistoryStorage.monitorFor(basic.f.store.storageRoot())){PrivateOperationStore.Ledger ledger=PrivateOperationStore.inspect(basic.f.store.storageRoot(),inspect(basic.f),new HistoryStorage.Budget(HistoryStorage.METADATA_BUDGET));yes(ledger.complete&&ledger.pending,"actual prepared ledger is complete and pending");}
        synchronized(HistoryStorage.monitorFor(basic.f.store.storageRoot())){try(HistoryStorage.MutationScope lease=HistoryStorage.administration(basic.f.store.storageRoot())){writer.writeNativeLocked();}}
        Path data=basic.f.parent.resolve("private_operations/"+basic.plan.prepare.preparation);
        for(PrivateOperationRecord.Artifact artifact:basic.plan.prepare.artifacts){Path actual=data.resolve(artifact.filename());yes(Files.isRegularFile(actual)&&Files.size(actual)==artifact.length&&PrivateOperationRecord.sha(Files.readAllBytes(actual)).equals(artifact.hash),"native artifact exact planned bytes");yes(!Files.exists(actual.resolveSibling(actual.getFileName()+".stage")),"native artifact stage promoted");}
        synchronized(HistoryStorage.monitorFor(basic.f.store.storageRoot())){PrivateOperationStore.Ledger ledger=PrivateOperationStore.inspect(basic.f.store.storageRoot(),inspect(basic.f),new HistoryStorage.Budget(HistoryStorage.METADATA_BUDGET));yes(ledger.complete&&ledger.pending&&ledger.reservedBytes==0&&ledger.reservedEntries==0,"native metadata replaces exact data credits without completing capture");try(HistoryStorage.MutationScope lease=HistoryStorage.administration(basic.f.store.storageRoot())){refuses(writer::writeNativeLocked,"native metadata cannot be rewritten");}}
        yes(!Files.exists(control.resolve(basic.plan.prepare.begin.name())),"native metadata still grants no BEGIN");
        refuses(()->basic.publish(durable()),"writer cannot silently reuse preparation");
        Capture fileFault=new Capture();refuses(()->fileFault.publish(new Disk(){public void syncFile(FileDescriptor fd)throws IOException{throw new IOException("injected file sync");}public void syncDirectory(File directory){} }),"failed file sync gives no acknowledged writer");
        yes(Files.exists(fileFault.f.parent.resolve("history_control/"+fileFault.plan.prepare.name()+".stage")),"failed preparation retains stage");
        Capture outsider=new Capture();refuses(()->outsider.publish(new Disk(){public void syncFile(FileDescriptor fd)throws IOException{fd.sync();Files.createDirectory(outsider.f.parent.resolve("private_operations"));Files.write(outsider.f.parent.resolve("private_operations/foreign"),new byte[]{1});}public void syncDirectory(File directory){} }),"callback-introduced foreign namespace cannot be acknowledged");
        yes(!Files.exists(outsider.f.parent.resolve("history_control/"+outsider.plan.prepare.name())),"foreign namespace refuses before promotion");
        for(String point:Arrays.asList("file-before","file-after","move-before","move-after","dir-before","dir-after")){
            Capture fault=new Capture();Disk disk=new Disk();disk.hook=(event,file)->{if(event.equals(point))throw new IOException("injected "+point);};
            refuses(()->fault.publish(disk),"durability failure cannot return writer at "+point);
            Path stage=fault.f.parent.resolve("history_control/"+fault.plan.prepare.name()+".stage"),committed=fault.f.parent.resolve("history_control/"+fault.plan.prepare.name());
            yes(Files.exists(stage)||Files.exists(committed),"fault preserves PREPARE evidence at "+point);
            synchronized(HistoryStorage.monitorFor(fault.f.store.storageRoot())){try(HistoryStorage.MutationScope lease=HistoryStorage.administration(fault.f.store.storageRoot())){refuses(fault.owner::claimLocked,"failed publication cannot mint closure at "+point);}}
        }
        for(String point:Arrays.asList("file-after","move-after","dir-after")){
            Capture replaced=new Capture();Disk disk=new Disk();disk.hook=(event,file)->{if(event.equals(point)){
                Path target=replaced.f.parent.resolve("history_control/"+replaced.plan.prepare.name()+(point.equals("file-after")?".stage":""));
                Path replacement=target.resolveSibling("replacement.tmp");Files.write(replacement,replaced.plan.prepare.encoded());Files.move(replacement,target,StandardCopyOption.REPLACE_EXISTING);
            }};
            refuses(()->replaced.publish(disk),"same-byte PREPARE replacement cannot be acknowledged at "+point);
        }
        for(String point:Arrays.asList("file-after","dir-after")){
            Capture nativeChanged=new Capture();Disk disk=new Disk();disk.hook=(event,file)->{if(event.equals(point)){
                Path target=nativeChanged.f.parent.resolve("store/"+nativeChanged.f.original.id+".snapshot");byte[] original=Files.readAllBytes(target);Path replacement=target.resolveSibling("replacement.tmp");Files.write(replacement,original);Files.move(replacement,target,StandardCopyOption.REPLACE_EXISTING);
            }};
            refuses(()->nativeChanged.publish(disk),"same-byte native replacement cannot be acknowledged at "+point);
        }
        for(boolean alter:new boolean[]{false,true})try(PrivateCapturePlanCheck.F old=new PrivateCapturePlanCheck.F(true,true)){
            old.locked(()->{
                PrivateCapturePlan plan=old.create(Collections.emptyList());Disk disk=new Disk();
                if(alter)disk.hook=(event,file)->{if(event.equals("dir-after")){
                    PrivateOperationRecord prior=old.control.operations.preparations.get(0);
                    Path artifact=old.parent.resolve("private_operations/"+prior.preparation+"/"+prior.artifact(1).filename());
                    java.nio.file.attribute.FileTime before=Files.getLastModifiedTime(artifact);Files.setLastModifiedTime(artifact,java.nio.file.attribute.FileTime.fromMillis(before.toMillis()+1000));
                }};
                if(alter)refuses(()->PrivateCaptureWriter.prepareLocked(plan,old.store.storageRoot(),disk,old.budget),"old private artifact physical change rejected even with unchanged bytes");
                else {PrivateCaptureWriter.prepareLocked(plan,old.store.storageRoot(),disk,old.budget);yes(plan.prepare.directoryMask==2,"actual writer supports accounted previous CLOSED capture");}
            });
        }
        Capture controlChanged=new Capture();HistoryPins changedPins=HistoryPins.open(controlChanged.f.store,controlChanged.f.catalog,controlChanged.f.identity,controlChanged.f.index,controlChanged.f.disk);
        HistoryPins.ManifestIdentity manifest=HistoryPins.ManifestIdentity.read(HistoryPinRecord.Domain.NATIVE_HISTORY_V1,0,new ByteArrayInputStream(new byte[]{9,8,7}));
        String unrelated=changedPins.begin(HistoryPinRecord.Purpose.EXPORT,HistoryPinRecord.Scope.ALL_BUNDLE,Collections.emptyList(),manifest);
        HistoryPinsCheck.close(changedPins,unrelated,HistoryPinRecord.Outcome.CANCELLED,null);
        refuses(()->controlChanged.publish(durable()),"new completed legacy pin invalidates original full control binding even with absent operation ledger");
        yes(!Files.exists(controlChanged.f.parent.resolve("history_control/"+controlChanged.plan.prepare.name())),"stale full control is rejected before PREPARE publication");
        for(String point:Arrays.asList("file-before","file-after","move-before","move-after","dir-before","dir-after")){
            Capture fault=new Capture();Disk disk=new Disk();PrivateCaptureWriter nativeWriter=fault.publish(disk);disk.hook=(event,file)->{if(event.equals(point))throw new IOException("native metadata "+point);};
            synchronized(HistoryStorage.monitorFor(fault.f.store.storageRoot())){try(HistoryStorage.MutationScope lease=HistoryStorage.administration(fault.f.store.storageRoot())){refuses(nativeWriter::writeNativeLocked,"native metadata durability fault refuses at "+point);}}
            yes(Files.exists(fault.f.parent.resolve("history_control/"+fault.plan.prepare.name())),"native metadata failure preserves funding at "+point);
        }
        for(int artifactIndex=1;artifactIndex<=2;artifactIndex++)for(String point:Arrays.asList("file-before","file-after","move-before","move-after","dir-before","dir-after")){
            Capture fault=new Capture();Disk disk=new Disk();PrivateCaptureWriter nativeWriter=fault.publish(disk);final int selected=artifactIndex;final int[] files={0},directories={0};
            disk.hook=(event,file)->{
                if(event.equals("file-before"))files[0]++;
                if(event.equals("dir-before")&&file.getName().equals(fault.preparation.toString()))directories[0]++;
                boolean selectedEvent=point.startsWith("file-")?files[0]==selected:point.startsWith("move-")?file!=null&&file.getName().startsWith(selected==1?"map-":"capture-"):file!=null&&file.getName().equals(fault.preparation.toString())&&directories[0]==selected;
                if(event.equals(point)&&selectedEvent)throw new IOException("artifact "+selected+" "+point);
            };
            synchronized(HistoryStorage.monitorFor(fault.f.store.storageRoot())){try(HistoryStorage.MutationScope lease=HistoryStorage.administration(fault.f.store.storageRoot())){refuses(nativeWriter::writeNativeLocked,"artifact durability refuses "+selected+" "+point);}}
            PrivateOperationRecord.Artifact artifact=fault.plan.prepare.artifact(selected);Path directory=fault.f.parent.resolve("private_operations/"+fault.preparation);
            Path stage=directory.resolve(artifact.filename()+".stage"),target=directory.resolve(artifact.filename());yes(Files.exists(stage)||Files.exists(target),"artifact bytes preserved "+selected+" "+point);
        }
        Capture superseded=new Capture();Disk supersedingDisk=new Disk();HistoryCapacity originalAuthority=HistoryStorage.capacity(superseded.f.store.storageRoot());final boolean[] replacementAttempted={false},replacementFailed={false};
        supersedingDisk.hook=(event,file)->{if(event.equals("file-after")&&!replacementAttempted[0]){
            replacementAttempted[0]=true;try{HistoryCapacity.openLocked(superseded.f.store,superseded.f.catalog,superseded.f.identity,superseded.f.index,superseded.f.disk);}catch(IOException expected){replacementFailed[0]=true;}
        }};
        refuses(()->superseded.publish(supersedingDisk),"callback superseded accepted capacity with unacknowledged candidate");
        yes(replacementAttempted[0]&&replacementFailed[0]&&HistoryStorage.capacity(superseded.f.store.storageRoot())!=originalAuthority,"real nested reopen installed replacement before refusing reconciliation");
        yes(!Files.exists(superseded.f.parent.resolve("history_control/"+superseded.plan.prepare.name()))&&Files.exists(superseded.f.parent.resolve("history_control/"+superseded.plan.prepare.name()+".stage")),"authority replacement leaves stage without committed PREPARE");
        Capture revoked=new Capture();Disk revokingDisk=new Disk();HistoryCapacity sameAuthority=HistoryStorage.capacity(revoked.f.store.storageRoot());final boolean[] readinessRevoked={false};
        revokingDisk.hook=(event,file)->{if(event.equals("file-after")&&!readinessRevoked[0]){
            try{sameAuthority.requireWriter(revoked.f.store.storageRoot());throw new AssertionError("ordinary writer unexpectedly admitted PREPARE.stage");}catch(IOException expected){readinessRevoked[0]=true;}
        }};
        refuses(()->revoked.publish(revokingDisk),"same installed authority lost readiness during intentional PREPARE.stage");
        yes(readinessRevoked[0]&&HistoryStorage.capacity(revoked.f.store.storageRoot())==sameAuthority,"actual failed ordinary writer check revoked same-object readiness");
        yes(!Files.exists(revoked.f.parent.resolve("history_control/"+revoked.plan.prepare.name()))&&Files.exists(revoked.f.parent.resolve("history_control/"+revoked.plan.prepare.name()+".stage")),"same-object readiness loss leaves stage without committed PREPARE");
        Capture guarded=new Capture();Disk guardDisk=new Disk();guardDisk.hook=(event,file)->{if(event.equals("file-after")){
            Thread freeze=new Thread(()->{try{guarded.owner.freeze();}catch(IOException failure){throw new AssertionError(failure);}});freeze.start();try{freeze.join(5000);}catch(InterruptedException failure){Thread.currentThread().interrupt();throw new IOException(failure);}if(freeze.isAlive())throw new IOException("freeze blocked");
            try{refuses(guarded.owner::claimLocked,"durability callback cannot claim while internal FD open");}catch(IOException failure){throw failure;}catch(Exception failure){throw new IOException(failure);}
        }};
        guarded.publish(guardDisk);
        synchronized(HistoryStorage.monitorFor(guarded.f.store.storageRoot())){try(HistoryStorage.MutationScope lease=HistoryStorage.administration(guarded.f.store.storageRoot())){guarded.owner.claimLocked().validateLocked();assertions++;}}
        System.out.println("PASS actual capture PREPARE publication: "+assertions+" assertions; no capture completion/activation claim");
    }
}
