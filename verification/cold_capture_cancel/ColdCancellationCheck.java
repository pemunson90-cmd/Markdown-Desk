package com.example.markdowndesk;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import static com.example.markdowndesk.HistoryPinsCheck.*;

/** Actual registered PREPARE production, process exit, cancellation and independent rediscovery. */
public final class ColdCancellationCheck {
    static int groups;
    interface Work {void run()throws Exception;}
    static void yes(boolean value,String why){if(!value)throw new AssertionError(why);}
    static void reject(Work work,String why)throws Exception{try{work.run();throw new AssertionError("accepted "+why);}catch(IOException expected){}}
    static final class Reopen {
        final File parent,root;final Disk disk=new Disk();final ProtectionStore store;final HistoryCatalog catalog;
        final HistoryIdentity identity;final HistoryIndex index;final HistoryPins pins;final UUID id;
        Reopen(String path,String id)throws Exception {
            parent=new File(path);root=new File(parent,"store");this.id=UUID.fromString(id);
            store=new ProtectionStore(root,disk,64L<<20);catalog=new HistoryCatalog(new File(parent,"catalog"),store,disk);
            identity=new HistoryIdentity(new File(parent,"identity"),disk);index=new HistoryIndex(store,catalog,identity,new File(parent,"index"),disk);
            pins=HistoryPins.forColdCancellation(store,catalog,identity,index,disk);
        }
        void locked(Work work)throws Exception{synchronized(HistoryStorage.monitorFor(root)){try(HistoryStorage.MutationScope lease=HistoryStorage.administration(root)){work.run();}}}
        HistoryControlStore.Inspection control()throws IOException{return new HistoryControlStore(parent).inspect();}
        PrivateOperationRecord p()throws IOException{for(PrivateOperationRecord p:control().operations.preparations)if(p.preparation.equals(id))return p;throw new IOException("No typed P");}
        Path path(String name){return parent.toPath().resolve("history_control/"+name);}
        void cancel(HistoryPins.ColdStages stages)throws IOException{pins.cancelColdPreparation(id,new HistoryStorage.Budget(HistoryStorage.METADATA_BUDGET),stages);}
    }
    static void child(String mode,String path,String id,String... extra)throws Exception {
        List<String> args=new ArrayList<>(Arrays.asList(new File(System.getProperty("java.home"),"bin/java").getPath(),"-Xmx128m","-cp",System.getProperty("java.class.path"),ColdCancellationCheck.class.getName(),mode,path,id));
        args.addAll(Arrays.asList(extra));ProcessBuilder process=new ProcessBuilder(args).inheritIO();process.environment().remove("JAVA_TOOL_OPTIONS");Process child=process.start();yes(child.waitFor()==0,"child "+mode+" "+Arrays.toString(extra));
    }
    static PreparationPublicationCheck.Capture fixture(boolean namespace)throws Exception {
        PreparationPublicationCheck.Capture c=new PreparationPublicationCheck.Capture();c.publish(new Disk());
        if(namespace){Path dir=c.f.parent.resolve("private_operations/"+c.preparation);Files.createDirectories(dir);
            Files.write(dir.resolve(c.plan.prepare.artifact(1).filename()+".stage"),new byte[]{9,8,7});}
        return c;
    }
    static Map<String,String> bytes(Path root)throws Exception {
        Map<String,String> result=new TreeMap<>();try(java.util.stream.Stream<Path> paths=Files.walk(root)){
            for(Path p:(Iterable<Path>)paths.filter(Files::isRegularFile)::iterator)result.put(root.relativize(p).toString(),PrivateOperationRecord.sha(Files.readAllBytes(p)));
        }return result;
    }
    static void sameProcess()throws Exception {
        PreparationPublicationCheck.Capture c=fixture(true);
        HistoryPins pins=HistoryPins.forColdCancellation(c.f.store,c.f.catalog,c.f.identity,c.f.index,c.f.disk);
        Map<String,String> before=bytes(c.f.parent);
        reject(()->pins.cancelColdPreparation(c.preparation,new HistoryStorage.Budget(HistoryStorage.METADATA_BUDGET),HistoryPins.ColdStages.REJECT),"live registered original owner");
        c.owner.freeze();reject(()->pins.cancelColdPreparation(c.preparation,new HistoryStorage.Budget(HistoryStorage.METADATA_BUDGET),HistoryPins.ColdStages.REJECT),"frozen owner");
        yes(before.equals(bytes(c.f.parent)),"owner refusals leave all durable evidence untouched");
        child("cancel",c.f.parent.toString(),c.preparation.toString());
        child("verify",c.f.parent.toString(),c.preparation.toString(),"eligible");
        child("verify",c.f.parent.toString(),c.preparation.toString(),"eligible");groups++;
        PreparationPublicationCheck.Capture uncertain=fixture(true);
        reject(()->uncertain.owner.open(()->{throw new IOException("source uncertainty");}),"injected uncertain owner");
        HistoryPins up=HistoryPins.forColdCancellation(uncertain.f.store,uncertain.f.catalog,uncertain.f.identity,uncertain.f.index,uncertain.f.disk);
        reject(()->up.cancelColdPreparation(uncertain.preparation,new HistoryStorage.Budget(HistoryStorage.METADATA_BUDGET),HistoryPins.ColdStages.REJECT),"uncertain owner");groups++;
    }
    static void normalChild(Reopen r)throws Exception {
        PrivateOperationRecord p=r.p();Map<String,String> old=bytes(r.parent.toPath());long[] credits=new long[2];String[] ledger=new String[1];
        r.locked(()->{HistoryControlStore.Inspection c=r.control();PrivateOperationStore.Ledger l=PrivateOperationStore.inspect(r.root,c,new HistoryStorage.Budget(HistoryStorage.METADATA_BUDGET));credits[0]=l.reservedBytes;credits[1]=l.reservedEntries;ledger[0]=l.entries.toString();});
        r.cancel(HistoryPins.ColdStages.REJECT);
        r.locked(()->{HistoryControlStore.Inspection c=r.control();PrivateOperationStore.Ledger l=PrivateOperationStore.inspect(r.root,c,new HistoryStorage.Budget(HistoryStorage.METADATA_BUDGET));
            yes(c.operations.terminalCancelled.contains(r.id)&&c.operations.pending&&l.pending&&l.complete,"typed cancellation retains pending complete accounting");
            yes(c.controlReservedBytes==173&&c.controlReservedEntries==1,"unused LINK credit retained exactly");
            yes(l.reservedBytes==credits[0]&&l.reservedEntries==credits[1]&&ledger[0].equals(l.entries.toString()),"data identities/bytes/credits unchanged");
            yes(HistoryStorage.preparationSources(r.root)==null,"no synthetic preparation owner");
        });
        for(Map.Entry<String,String> e:old.entrySet())yes(e.getValue().equals(PrivateOperationRecord.sha(Files.readAllBytes(r.parent.toPath().resolve(e.getKey())))),"old evidence unchanged "+e.getKey());
        yes(Arrays.equals(Files.readAllBytes(r.path(p.begin.name())),HistoryPinRecord.encode(p.begin)),"exact embedded BEGIN204");
        yes(Arrays.equals(Files.readAllBytes(r.path("pin-"+p.operation+".close")),HistoryPinRecord.close(p.begin,HistoryPinRecord.Outcome.CANCELLED)),"exact canonical CANCELLED126");
        yes(!Files.exists(r.path(PrivateOperationRecord.link(p).name())),"no LINK or activation binding");
    }
    static void verify(Reopen r,boolean eligible)throws Exception {
        r.locked(()->{HistoryControlStore.Inspection c=r.control();PrivateOperationStore.Ledger l=PrivateOperationStore.inspect(r.root,c,new HistoryStorage.Budget(HistoryStorage.METADATA_BUDGET));
            yes(c.operations.terminalCancelled.contains(r.id)&&c.operations.unfinished==0&&c.operations.pending&&l.pending&&l.complete,"fresh cancellation classification/accounting");
            yes(c.controlReservedBytes==173&&c.controlReservedEntries==1,"fresh LINK funding retained");
            yes(PrivateOperationStore.historicalCancelledOnly(c,l)==eligible,"physical namespace eligibility");
            yes(HistoryStorage.preparationSources(r.root)==null&&HistoryStorage.operations(r.root)==null,"fresh process has no previous lifetimes");
            PrivateOperationRecord p=r.p();yes(Arrays.equals(Files.readAllBytes(r.path(p.begin.name())),HistoryPinRecord.encode(p.begin)),"fresh exact BEGIN");
            yes(Arrays.equals(Files.readAllBytes(r.path("pin-"+p.operation+".close")),HistoryPinRecord.close(p.begin,HistoryPinRecord.Outcome.CANCELLED)),"fresh exact CLOSE");
        });
    }
    static void refusalsAndPromotion()throws Exception {
        for(String mode:Arrays.asList("partial-p","conflict-p","partial-begin","conflict-begin","link-stage","released-close","unknown-control","oversized-data","symlink-data")){
            PreparationPublicationCheck.Capture c=fixture(true);PrivateOperationRecord p=c.plan.prepare;Path control=c.f.parent.resolve("history_control");
            if(mode.equals("partial-p")){Files.move(control.resolve(p.name()),control.resolve(p.name()+".stage"));Files.write(control.resolve(p.name()+".stage"),Arrays.copyOf(p.encoded(),42));}
            if(mode.equals("conflict-p"))Files.write(control.resolve(p.name()+".stage"),p.encoded());
            if(mode.equals("partial-begin"))Files.write(control.resolve(p.begin.name()+".stage"),Arrays.copyOf(HistoryPinRecord.encode(p.begin),101));
            if(mode.equals("conflict-begin")){byte[] wrong=HistoryPinRecord.encode(p.begin);wrong[20]^=1;Files.write(control.resolve(p.begin.name()+".stage"),wrong);}
            if(mode.equals("link-stage")){Files.write(control.resolve(p.begin.name()),HistoryPinRecord.encode(p.begin));Files.write(control.resolve(PrivateOperationRecord.link(p).name()+".stage"),new byte[0]);}
            if(mode.equals("released-close")){Files.write(control.resolve(p.begin.name()),HistoryPinRecord.encode(p.begin));Files.write(control.resolve("pin-"+p.operation+".close.stage"),HistoryPinRecord.close(p.begin,HistoryPinRecord.Outcome.RELEASED));}
            if(mode.equals("unknown-control"))Files.write(control.resolve("unknown.stage"),new byte[]{7});
            Path data=c.f.parent.resolve("private_operations/"+c.preparation+"/"+p.artifact(1).filename()+".stage");
            if(mode.equals("oversized-data"))try(RandomAccessFile out=new RandomAccessFile(data.toFile(),"rw")){out.setLength(p.artifact(1).length+1);}
            if(mode.equals("symlink-data")){Files.delete(data);Files.createSymbolicLink(data,control.resolve(p.name()));}
            child("reject",c.f.parent.toString(),c.preparation.toString(),mode);
        }groups++;
        for(boolean prepare:new boolean[]{true,false}){
            PreparationPublicationCheck.Capture c=fixture(true);PrivateOperationRecord p=c.plan.prepare;Path control=c.f.parent.resolve("history_control");
            if(prepare)Files.move(control.resolve(p.name()),control.resolve(p.name()+".stage"));
            else Files.write(control.resolve(p.begin.name()+".stage"),HistoryPinRecord.encode(p.begin));
            child("promote",c.f.parent.toString(),c.preparation.toString());
            child("verify",c.f.parent.toString(),c.preparation.toString(),"eligible");
            child("verify",c.f.parent.toString(),c.preparation.toString(),"eligible");
        }groups++;
        for(int length:new int[]{0,94,125}){
            PreparationPublicationCheck.Capture c=fixture(true);PrivateOperationRecord p=c.plan.prepare;Path control=c.f.parent.resolve("history_control");
            Files.write(control.resolve(p.begin.name()),HistoryPinRecord.encode(p.begin));
            Path stage=control.resolve("pin-"+p.operation+".close.stage");Files.write(stage,Arrays.copyOf(HistoryPinRecord.close(p.begin,HistoryPinRecord.Outcome.CANCELLED),length));
            child("prefix",c.f.parent.toString(),c.preparation.toString());
            child("verify",c.f.parent.toString(),c.preparation.toString(),"eligible");
            child("verify",c.f.parent.toString(),c.preparation.toString(),"eligible");
        }groups++;
    }
    static void faultsAndCallbacks()throws Exception {
        for(String fault:Arrays.asList("begin-stage","begin-final","close-stage","close-final")){
            PreparationPublicationCheck.Capture c=fixture(true);
            child("fault",c.f.parent.toString(),c.preparation.toString(),fault);
            child("verify",c.f.parent.toString(),c.preparation.toString(),"eligible");
            child("verify",c.f.parent.toString(),c.preparation.toString(),"eligible");
        }groups++;
        for(String mode:Arrays.asList("mutation","terminal","participant","unrelated-owner","quiescent-lifetime","uncertain-lifetime","budget")){
            PreparationPublicationCheck.Capture c=fixture(true);
            child("callback",c.f.parent.toString(),c.preparation.toString(),mode);
        }groups++;
        PreparationPublicationCheck.Capture absent=fixture(false);child("cancel",absent.f.parent.toString(),absent.preparation.toString());
        child("verify",absent.f.parent.toString(),absent.preparation.toString(),"ineligible");
        child("verify",absent.f.parent.toString(),absent.preparation.toString(),"ineligible");groups++;
        PreparationPublicationCheck.Capture missing=fixture(true);Path dir=missing.f.parent.resolve("private_operations/"+missing.preparation);
        Files.delete(dir.resolve(missing.plan.prepare.artifact(1).filename()+".stage"));Files.delete(dir);
        child("cancel",missing.f.parent.toString(),missing.preparation.toString());child("verify",missing.f.parent.toString(),missing.preparation.toString(),"eligible");
        child("verify",missing.f.parent.toString(),missing.preparation.toString(),"eligible");groups++;
    }
    static void staleAndAdmission()throws Exception {
        for(boolean shortFile:new boolean[]{false,true}){
            PreparationPublicationCheck.Capture c=fixture(true);PrivateOperationRecord.Artifact a=c.plan.prepare.artifact(1);
            Path stage=c.f.parent.resolve("private_operations/"+c.preparation+"/"+a.filename()+".stage");Files.delete(stage);
            byte[] stale=new byte[(int)(shortFile?3:a.length)];Arrays.fill(stale,(byte)7);Files.write(stage.resolveSibling(a.filename()),stale);
            child("stale",c.f.parent.toString(),c.preparation.toString());
            child("verify-stale",c.f.parent.toString(),c.preparation.toString());child("verify-stale",c.f.parent.toString(),c.preparation.toString());
        }groups++;
        PreparationPublicationCheck.Capture c=fixture(true);child("cancel",c.f.parent.toString(),c.preparation.toString());
        child("new-capture",c.f.parent.toString(),c.preparation.toString());groups++;
    }
    static void stale(Reopen r,boolean cancel)throws Exception {
        PrivateOperationRecord p=r.p();Map<String,String> original=bytes(r.parent.toPath());
        if(cancel)r.cancel(HistoryPins.ColdStages.REJECT);
        r.locked(()->{
            HistoryControlStore.Inspection c=r.control();PrivateOperationStore.Ledger l=PrivateOperationStore.inspect(r.root,c,new HistoryStorage.Budget(HistoryStorage.METADATA_BUDGET));
            yes(c.operations.terminalCancelled.contains(r.id)&&c.controlReservedBytes==173&&c.controlReservedEntries==1,"stale cancellation retains canonical records and unused LINK credit");
            yes(!l.complete&&!PrivateOperationStore.historicalCancelledOnly(c,l),"cancellation-specific stale accounting grants no global completeness/admission");
            yes(Arrays.equals(Files.readAllBytes(r.path(p.begin.name())),HistoryPinRecord.encode(p.begin)),"stale exact BEGIN");
            yes(Arrays.equals(Files.readAllBytes(r.path("pin-"+p.operation+".close")),HistoryPinRecord.close(p.begin,HistoryPinRecord.Outcome.CANCELLED)),"stale exact CLOSE");
        });
        for(Map.Entry<String,String> e:original.entrySet())yes(e.getValue().equals(PrivateOperationRecord.sha(Files.readAllBytes(r.parent.toPath().resolve(e.getKey())))),"stale evidence retained "+e.getKey());
    }
    static void newCapture(Reopen r)throws Exception {
        r.pins.reconcile();
        HistoryCapacity.open(r.store,r.catalog,r.identity,r.index,r.disk);
        HistoryStorage.Budget budget=new HistoryStorage.Budget(HistoryStorage.METADATA_BUDGET);budget.charge(1L<<20);
        UUID id=UUID.randomUUID();PreparationSources owner=PreparationSources.forPreparation(r.root,id,budget,PreparationSources.newProducer());
        PrivateCaptureWriter[] writer={null};PrivateCapturePlan[] selected={null};
        r.locked(()->{
            HistoryControlStore.Inspection c=r.control();HistoryGraph history=new HistoryGraph(r.store,r.catalog,r.identity,r.index,new HistoryPolicy(64L<<20,20));HistoryGraph.Graph graph=history.scan();
            yes(graph.complete&&graph.pruneBlocked,"cold cancellation leaves accounting complete and pruning blocked");
            HistoryPruner pruner=new HistoryPruner(history);
            yes(pruner.planAutomatic(1,1).plan==null&&pruner.planAutomaticPressure(1,1).plan==null&&pruner.planSelected(Collections.singleton(UUID.randomUUID().toString())).plan==null,"all planner modes remain blocked");
            HistoryArchiveManifest map=HistoryArchiveManifest.freeze(graph,c,UUID::randomUUID);
            PrivateOperationStore.Ledger ledger=PrivateOperationStore.inspect(r.root,c,budget);
            selected[0]=PrivateCapturePlan.create(r.root,r.catalog.storageRoot(),r.identity.storageRoot(),r.index.storageRoot(),map,graph,c,ledger,owner,id,UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),Collections.emptyList(),c.registration.bootstrapQuotaBytes,budget);
            writer[0]=PrivateCaptureWriter.prepareLocked(selected[0],r.root,r.disk,budget);writer[0].writeNativeLocked();
        });
        PrivateCaptureWriter.CapturedBinding binding=writer[0].commitCapture(r.pins);HistoryOperations operations=r.pins.operations();HistoryOperations.OperationHandle handle=operations.activateCaptured(binding,budget);
        HistoryArchiveManifest.Entry item=selected[0].nativeMap.entries.get(0);long length=0;
        try(InputStream input=operations.openCapturedSource(handle,item.archiveId)){byte[] buffer=new byte[1024];for(int n;(n=input.read(buffer))!=-1;)length+=n;}
        yes(length==item.envelopeBytes,"post-cancellation genuine new capture activates and reads its own declared source");
        r.locked(()->{HistoryControlStore.Inspection c=r.control();PrivateOperationStore.Ledger l=PrivateOperationStore.inspect(r.root,c,new HistoryStorage.Budget(HistoryStorage.METADATA_BUDGET));
            yes(c.operations.pending&&l.pending&&l.complete&&c.operations.terminalCancelled.contains(r.id),"new capture retains old cancellation evidence/credits");});
    }
    static void foreignClosedLifetime()throws Exception {
        for(String state:Arrays.asList("uncertain","quiescent")){
            PreparationPublicationCheck.Capture c=fixture(true);
            child("foreign-lifetime",c.f.parent.toString(),c.preparation.toString(),state);
        }groups++;
    }
    static void foreignClosedLifetime(Reopen r,boolean uncertain)throws Exception {
        HistoryControlStore.Inspection c=r.control();PrivateOperationRecord selected=r.p();
        HistoryPinRecord legacy=HistoryPinRecord.begin(UUID.randomUUID(),c.registration,c.latest,
                HistoryPinRecord.Purpose.EXPORT,HistoryPinRecord.Scope.ALL_BUNDLE,
                new HistoryPinRecord.Manifest(HistoryPinRecord.Domain.NATIVE_HISTORY_V1,0,0,HistoryStorage.hex(HistoryStorage.digest().digest(new byte[0]))),Collections.emptyList());
        Files.write(r.path(legacy.name()),HistoryPinRecord.encode(legacy));r.pins.reconcile();
        HistoryOperations ops=r.pins.operations();HistoryOperations.OperationHandle handle=ops.activateAcknowledged(legacy.operation.toString(),new ByteArrayInputStream(new byte[0]));
        if(uncertain)reject(()->ops.openReader(handle,()->{throw new IOException("foreign uncertain factory");}),"foreign factory uncertainty");
        else yes(ops.quiesce(ops.freeze(handle)).toCompletableFuture().getNow(null)!=null,"foreign previously activated lifetime actually quiescent");
        Files.write(r.path("pin-"+legacy.operation+".close"),HistoryPinRecord.close(legacy,HistoryPinRecord.Outcome.CANCELLED));
        Map<String,String> before=bytes(r.parent.toPath());int[] callbacks={0};r.disk.hook=(event,file)->callbacks[0]++;
        if(uncertain){
            reject(()->r.cancel(HistoryPins.ColdStages.REJECT),"foreign uncertain closed-audit owner");
            yes(callbacks[0]==0&&before.equals(bytes(r.parent.toPath())),"foreign lifetime refusal precedes EVERY P/BEGIN durability callback/publication");
            yes(!Files.exists(r.path(selected.begin.name()))&&!Files.exists(r.path(selected.begin.name()+".stage")),"selected BEGIN remains absent");
            yes(!ops.quiesce(ops.freeze(handle)).toCompletableFuture().isDone(),"uncertain old resource remains owned; no reset/clear/false proof");
        }else{
            r.cancel(HistoryPins.ColdStages.REJECT);
            r.locked(()->yes(r.control().operations.terminalCancelled.contains(r.id),"existing foreign closed-audit quiescence rules preserved despite historical epoch"));
        }
    }
    static void rejectUnchanged(Reopen r,HistoryPins.ColdStages option)throws Exception {
        Map<String,String> before=bytes(r.parent.toPath());reject(()->r.cancel(option),"retained conflicting/partial evidence");
        yes(before.equals(bytes(r.parent.toPath())),"refusal changes no durable byte");
    }
    static void fault(Reopen r,String point)throws Exception {
        PrivateOperationRecord p=r.p();String bn=p.begin.name(),cn="pin-"+p.operation+".close";boolean[] fired={false};
        r.disk.hook=(event,file)->{
            boolean stage=point.endsWith("stage"),close=point.startsWith("close");String name=close?cn:bn;
            if(!fired[0]&&event.equals(stage?"file-after":"dir-after")&&Files.exists(r.path(name+(stage?".stage":"")))){
                fired[0]=true;throw new IOException("distinct durable prefix "+point);
            }
        };
        reject(()->r.cancel(HistoryPins.ColdStages.REJECT),"injected "+point);yes(fired[0],"reached "+point);r.disk.hook=null;
        if(point.equals("begin-stage")){rejectUnchanged(r,HistoryPins.ColdStages.REJECT);r.cancel(HistoryPins.ColdStages.PROMOTE_COMPLETE);}
        else r.cancel(HistoryPins.ColdStages.REJECT);
        r.locked(()->yes(r.control().operations.terminalCancelled.contains(r.id),"exact same-process cold retry "+point));
    }
    static void callback(Reopen r,String mode)throws Exception {
        PrivateOperationRecord p=r.p();String cn="pin-"+p.operation+".close";
        if(mode.equals("budget")){
            Map<String,String> before=bytes(r.parent.toPath());reject(()->r.pins.cancelColdPreparation(r.id,new HistoryStorage.Budget(1<<20),HistoryPins.ColdStages.REJECT),"shared metadata allowance");
            yes(before.equals(bytes(r.parent.toPath())),"budget refusal before mutation");return;
        }
        if(mode.equals("unrelated-owner")){
            PreparationSources owner=PreparationSources.forPreparation(r.root,UUID.randomUUID(),new HistoryStorage.Budget(1<<20),PreparationSources.newProducer());
            rejectUnchanged(r,HistoryPins.ColdStages.REJECT);r.locked(()->yes(HistoryStorage.preparationSources(r.root)==owner,"unrelated owner retained"));return;
        }
        if(mode.endsWith("lifetime")){
            Files.write(r.path(p.begin.name()),HistoryPinRecord.encode(p.begin));HistoryOperations ops=r.pins.operations();
            HistoryOperations.Quiescence proof=ops.quiesce(ops.freezeForTerminal(p.operation.toString())).toCompletableFuture().getNow(null);
            yes(proof!=null,"real process quiescence");
            if(mode.equals("uncertain-lifetime"))reject(()->r.pins.close(proof,HistoryPinRecord.Outcome.CANCELLED,null),"unacknowledged terminal attempt");
            rejectUnchanged(r,HistoryPins.ColdStages.REJECT);return;
        }
        boolean[] fired={false};int[] blocked={0};
        r.disk.hook=(event,file)->{if(!fired[0]&&event.equals("dir-after")&&Files.exists(r.path(p.begin.name()))){
            fired[0]=true;
            if(mode.equals("participant")){
                try{r.pins.operations().freezeForTerminal(p.operation.toString());throw new AssertionError("false callback quiescence");}catch(IOException expected){blocked[0]++;}
                try{r.cancel(HistoryPins.ColdStages.REJECT);throw new AssertionError("callback cancellation reentry");}catch(IOException expected){blocked[0]++;}
                return;
            }
            if(mode.equals("terminal")){Files.write(r.path(cn),HistoryPinRecord.close(p.begin,HistoryPinRecord.Outcome.RELEASED));return;}
            Path stage=r.parent.toPath().resolve("private_operations/"+r.id+"/"+p.artifact(1).filename()+".stage");
            byte[] bytes=Files.readAllBytes(stage);Path replacement=stage.resolveSibling("replacement.tmp");Files.write(replacement,bytes);Files.move(replacement,stage,StandardCopyOption.REPLACE_EXISTING);
        }};
        if(mode.equals("participant")){r.cancel(HistoryPins.ColdStages.REJECT);yes(blocked[0]==2,"callback false-quiescence/terminal-entry guard");}
        else{reject(()->r.cancel(HistoryPins.ColdStages.REJECT),"callback "+mode);yes(!Files.exists(r.path(cn+".stage")),"no cancellation CLOSE after interference");
            if(mode.equals("terminal"))yes(Arrays.equals(Files.readAllBytes(r.path(cn)),HistoryPinRecord.close(p.begin,HistoryPinRecord.Outcome.RELEASED)),"conflicting callback terminal untouched");
        }
        yes(fired[0],"reached callback "+mode);
    }
    public static void main(String[] args)throws Exception {
        if(args.length>0){Reopen r=new Reopen(args[1],args[2]);
            switch(args[0]){
                case "cancel":normalChild(r);break;
                case "verify":verify(r,args[3].equals("eligible"));break;
                case "reject":rejectUnchanged(r,HistoryPins.ColdStages.PROMOTE_COMPLETE);break;
                case "promote":rejectUnchanged(r,HistoryPins.ColdStages.REJECT);r.cancel(HistoryPins.ColdStages.PROMOTE_COMPLETE);break;
                case "fault":fault(r,args[3]);break;
                case "callback":callback(r,args[3]);break;
                case "foreign-lifetime":foreignClosedLifetime(r,args[3].equals("uncertain"));break;
                case "stale":stale(r,true);break;
                case "verify-stale":stale(r,false);break;
                case "new-capture":newCapture(r);break;
                case "prefix":{
                    Path stage=r.path("pin-"+r.p().operation+".close.stage");Object key=PrivateOperationStore.attributes(stage).fileKey();r.cancel(HistoryPins.ColdStages.REJECT);
                    yes(key.equals(PrivateOperationStore.attributes(r.path("pin-"+r.p().operation+".close")).fileKey()),"prefix helper preserves exact inode");break;
                }
                default:throw new AssertionError(args[0]);
            }return;
        }
        sameProcess();refusalsAndPromotion();faultsAndCallbacks();staleAndAdmission();foreignClosedLifetime();System.out.println("Cold cancellation: "+groups+" focused groups PASS");
    }
}
