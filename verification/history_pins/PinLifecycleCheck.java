package com.example.markdowndesk;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import static com.example.markdowndesk.HistoryPinRecord.*;
import static com.example.markdowndesk.HistoryPinsCheck.*;

public final class PinLifecycleCheck {
    static byte[] manifest={9,8,7};
    static InputStream input(){return new ByteArrayInputStream(manifest);}
    static HistoryPins.ManifestIdentity manifest()throws IOException{return HistoryPins.ManifestIdentity.read(Domain.NATIVE_HISTORY_V1,2,input());}
    static HistoryPins open(Fixture f)throws IOException{return HistoryPins.open(f.store,f.catalog,f.identity,f.index,f.disk);}
    static String begin(Fixture f,HistoryPins pins,Scope scope)throws Exception{return pins.begin(Purpose.RESTORE,scope,scope==Scope.EXACT_SUBSET?Collections.singleton(f.original.id):Collections.emptySet(),manifest());}
    static HistoryGraph graph(Fixture f)throws IOException{return new HistoryGraph(f.store,f.catalog,f.identity,f.index,new HistoryPolicy(64L*1024*1024,20));}
    static void blocked(Fixture f)throws Exception {
        check(graph(f).scan().diagnostics.contains("graph: active or unreconciled operation pin"),"mandatory graph pin gate");
        HistoryPruner planner=new HistoryPruner(graph(f));
        check(planner.planAutomatic(1,1).blockedReason==HistoryPruner.BlockedReason.INCOMPLETE_GRAPH,"floor freeze");
        check(planner.planAutomaticPressure(1,1).blockedReason==HistoryPruner.BlockedReason.INCOMPLETE_GRAPH,"pressure freeze");
        check(planner.planSelected(Collections.singleton(f.original.id)).blockedReason==HistoryPruner.BlockedReason.INCOMPLETE_GRAPH,"selected freeze");
    }
    static void lifecycle()throws Exception {
        for(Scope scope:Scope.values())for(Outcome outcome:Outcome.values()){
            Fixture f=new Fixture();HistoryPins pins=open(f);String id=begin(f,pins,scope);blocked(f);
            check(f.capacity.inspect().controlReservedBytes==126&&f.capacity.inspect().controlReservedEntries==1,"active reservation exposed");
            rejects(()->begin(f,pins,scope),"second active operation");
            rejects(()->pins.resume(id,null),"resume requires independent original manifest");
            rejects(()->pins.resume(id,new ByteArrayInputStream(new byte[]{0})),"wrong manifest");
            check(pins.resume(id,input()).operation.toString().equals(id),"exact manifest resumes input identity");
            f.store.putSnapshot("tab","uri","title",2,new byte[]{6},"attempted");
            f.capacity.setPolicy(new HistoryPolicy(128L*1024*1024,5));
            check(pins.resume(id,input()).generation==0,"policy ancestor remains pinned");blocked(f);
            close(pins,id,outcome,outcome==Outcome.RELEASED?input():null);
            check(!graph(f).scan().diagnostics.contains("graph: active or unreconciled operation pin"),"durable close releases operation gate");
            check(f.capacity.inspect().controlReservedBytes==0&&f.capacity.inspect().controlReservedEntries==0,"closed credit converted");
            close(pins,id,outcome,null);rejects(()->close(pins,id,outcome==Outcome.RELEASED?Outcome.CANCELLED:Outcome.RELEASED,null),"immutable outcome");
            check(inspect(f).pins.begins.size()==1&&inspect(f).pins.closes.size()==1,"audit retained");
        }
    }
    static void sources()throws Exception {
        Fixture f=new Fixture();HistoryPins pins=open(f);
        Files.write(f.parent.resolve("catalog/"+UUID.randomUUID()+".checkpoint"),new byte[]{1,2,3});
        check(!graph(f).scan().complete,"unrelated optional metadata makes pruning graph incomplete");
        rejects(()->pins.begin(Purpose.EXPORT,Scope.EXACT_SUBSET,Collections.singleton(f.pending.id),manifest()),"transaction is not snapshot");
        String id=begin(f,pins,Scope.EXACT_SUBSET);Path snapshot=f.parent.resolve("store/"+f.original.id+".snapshot");byte[] bytes=Files.readAllBytes(snapshot);bytes[bytes.length-1]^=1;Files.write(snapshot,bytes);
        rejects(()->pins.resume(id,input()),"changed pinned input");
        for(Outcome outcome:Outcome.values())rejects(()->close(pins,id,outcome,input()),"new close never forgives source corruption");
        blocked(f);
        Fixture closed=new Fixture();HistoryPins closedPins=open(closed);String closedId=begin(closed,closedPins,Scope.EXACT_SUBSET);close(closedPins,closedId,Outcome.CANCELLED,null);
        Files.delete(closed.parent.resolve("store/"+closed.original.id+".snapshot"));open(closed);check(!HistoryPins.pruneBlocked(closed.store.storageRoot(),inspect(closed)),"closed audits do not dereference retired sources");
    }
    static void callbacks()throws Exception {
        Fixture f=new Fixture();HistoryPins pins=open(f);String id=begin(f,pins,Scope.EXACT_SUBSET);
        InputStream nested=new ByteArrayInputStream(manifest){boolean fired;public synchronized int read(byte[] b,int off,int len){if(!fired){fired=true;try{HistoryPinsCheck.close(pins,id,Outcome.CANCELLED,null);throw new AssertionError("nested close acquired parent lease");}catch(IOException expected){checks++;}}return super.read(b,off,len);}};
        pins.resume(id,nested);blocked(f);
        Path source=f.parent.resolve("store/"+f.original.id+".snapshot");byte[] bytes=Files.readAllBytes(source);
        InputStream replace=new ByteArrayInputStream(manifest){boolean fired;public synchronized int read(byte[] b,int off,int len){if(!fired){fired=true;try{Path other=source.resolveSibling("replacement");Files.write(other,bytes);Files.move(other,source,StandardCopyOption.REPLACE_EXISTING);}catch(IOException failure){throw new UncheckedIOException(failure);}}return super.read(b,off,len);}};
        rejects(()->pins.resume(id,replace),"same-byte source replacement in stream callback");
    }
    static void stagesAndFaults()throws Exception {
        for(String event:Arrays.asList("file-before","file-after","move-before","move-after","dir-before","dir-after"))for(boolean closing:new boolean[]{false,true}){
            Fixture f=new Fixture();HistoryPins pins=open(f);String id=closing?begin(f,pins,Scope.EXACT_SUBSET):null;
            f.disk.hook=(name,file)->{if(name.equals(event))throw new IOException("injected "+event);};
            rejects(()->{if(closing)close(pins,id,Outcome.CANCELLED,null);else begin(f,pins,Scope.EXACT_SUBSET);},"fault must reject acknowledgement "+event);
            f.disk.hook=null;blocked(f);
            HistoryControlStore.Inspection before=inspect(f);List<Path> paths=new ArrayList<>();List<Object> keys=new ArrayList<>();
            for(HistoryControlStore.Entry entry:before.pins.stages){Path path=f.parent.resolve("history_control/"+entry.filename);paths.add(path);keys.add(Files.readAttributes(path,java.nio.file.attribute.BasicFileAttributes.class).fileKey());}
            if(closing&&!before.pins.stages.isEmpty())recover(pins,id,null);else open(f);HistoryControlStore.Inspection after=inspect(f);
            check(after.pins.stages.isEmpty(),"full canonical stage reconciled");
            for(int i=0;i<paths.size();i++){Path promoted=Paths.get(paths.get(i).toString().replace(".stage",""));check(Objects.equals(keys.get(i),Files.readAttributes(promoted,java.nio.file.attribute.BasicFileAttributes.class).fileKey()),"full stage promoted without rewrite");}
            check(closing?!HistoryPins.pruneBlocked(f.store.storageRoot(),after):after.pins.active!=null,"reconciled outcome");open(f);
        }
        for(boolean closing:new boolean[]{false,true})for(int size:new int[]{0,1,60,125}){
            Fixture f=new Fixture();HistoryPins pins=open(f);HistoryPinRecord begin;
            if(closing){begin(f,pins,Scope.EXACT_SUBSET);begin=inspect(f).pins.active;}else begin=record(f);
            byte[] complete=closing?HistoryPinRecord.close(begin,Outcome.CANCELLED):HistoryPinRecord.encode(begin);
            Path stage=f.parent.resolve("history_control/"+(closing?"pin-"+begin.operation+".close":begin.name())+".stage");byte[] partial=Arrays.copyOf(complete,size);Files.write(stage,partial);
            rejects(()->open(f),"partial retained stage blocked");check(Arrays.equals(partial,Files.readAllBytes(stage)),"partial preserved");
            rejects(()->f.capacity.reconcile(),"ordinary authority cannot promote pin stage");blocked(f);
        }
    }
    static void callbackFaults()throws Exception {
        for(String changed:Arrays.asList("source","stage","enrollment","policy","directory","unknown")){
            Fixture f=new Fixture();HistoryPins pins=open(f);String id=begin(f,pins,Scope.EXACT_SUBSET);boolean[] fired={false};
            f.disk.hook=(event,file)->{if(!fired[0]&&event.equals(changed.equals("stage")?"file-after":"dir-after")){fired[0]=true;
                Path target=changed.equals("source")?f.parent.resolve("store/"+f.original.id+".snapshot"):changed.equals("enrollment")?f.parent.resolve(HistoryControlStore.ENROLLMENT_NAME):changed.equals("policy")?f.parent.resolve("history_control/"+HistoryControlStore.REGISTRATION_NAME):f.parent.resolve("history_control/pin-"+id+".close.stage");
                if(changed.equals("unknown")){Files.write(f.parent.resolve("history_control/foreign"),new byte[]{1});return;}
                if(changed.equals("directory")){Path root=f.parent.resolve("index");Files.move(root,f.parent.resolve("old-index"));Files.createDirectory(root);return;}
                byte[] bytes=Files.readAllBytes(target);Path replacement=target.resolveSibling("replace.tmp");Files.write(replacement,bytes);Files.move(replacement,target,StandardCopyOption.REPLACE_EXISTING);
            }};
            rejects(()->close(pins,id,Outcome.CANCELLED,null),"callback identity/authority change "+changed);f.disk.hook=null;
            check(HistoryPins.pruneBlocked(f.store.storageRoot(),inspect(f)),"failed close never installs acknowledgement");
        }
        Fixture all=new Fixture();HistoryPins pins=open(all);String id=begin(all,pins,Scope.ALL_BUNDLE);
        all.disk.hook=(event,file)->{if(event.equals("dir-after")){Path source=all.parent.resolve("store/"+all.original.id+".snapshot");byte[] bytes=Files.readAllBytes(source);bytes[bytes.length-1]^=1;Files.write(source,bytes);}};
        rejects(()->close(pins,id,Outcome.CANCELLED,null),"ALL cancellation validates mandatory safety after final callback");all.disk.hook=null;
        check(HistoryPins.pruneBlocked(all.store.storageRoot(),inspect(all)),"ALL failed final validation retains protection");
    }
    static void policyRecord(Fixture f,long generation,String previous)throws Exception {
        HistoryControlStore.Inspection c=inspect(f);Files.write(f.parent.resolve("history_control/"+HistoryControlStore.policyFilename(generation)),HistoryControlStore.encodePolicy(c.registration.bundleId,generation,previous,64L*1024*1024,20));
    }
    static void allowance()throws Exception {
        Fixture slots=new Fixture();HistoryPins pins=open(slots);String id=begin(slots,pins,Scope.EXACT_SUBSET);
        while(inspect(slots).actualEntries<255){HistoryControlStore.Inspection c=inspect(slots);policyRecord(slots,c.latest.generation+1,c.latest.envelopeHash);}
        slots.capacity.reconcile();check(slots.capacity.inspect().controlActualEntries+slots.capacity.inspect().controlReservedEntries==256,"full entry allowance includes terminal slot");
        rejects(()->slots.capacity.setPolicy(new HistoryPolicy(128L*1024*1024,5)),"policy cannot spend reserved terminal slot");
        rejectedPolicyPromotion(slots);slots.capacity.reconcile();close(pins,id,Outcome.CANCELLED,null);
        check(inspect(slots).actualEntries==256,"terminal conversion fits full slot allowance");rejects(()->begin(slots,pins,Scope.ALL_BUNDLE),"retained audit exhaustion");
        Fixture bytes=new Fixture();HistoryControlStore.Inspection initial=inspect(bytes);HistoryPinRecord sample=record(bytes);
        int pairs=70;long base=initial.actualBytes+HistoryPinRecord.encode(sample).length+126+pairs*330L;
        int total=(int)((HistoryControlStore.MAX_BYTES-base)/58);check(total>=pairs&&total<=pairs*256,"byte fixture representable");
        for(int i=0;i<pairs;i++){
            int count=Math.min(256,total-(pairs-i-1));total-=count;List<Source> sources=new ArrayList<>();
            for(int j=0;j<count;j++)sources.add(new Source(new UUID(0,j).toString(),45,sample.sources.get(0).hash));
            HistoryPinRecord audit=HistoryPinRecord.begin(UUID.randomUUID(),initial.registration,initial.latest,Purpose.EXPORT,Scope.EXACT_SUBSET,sample.manifest,sources);
            Files.write(bytes.parent.resolve("history_control/"+audit.name()),HistoryPinRecord.encode(audit));Files.write(bytes.parent.resolve("history_control/pin-"+audit.operation+".close"),HistoryPinRecord.close(audit,Outcome.RELEASED));
        }
        HistoryPins full=open(bytes);String active=begin(bytes,full,Scope.EXACT_SUBSET);HistoryControlStore.Inspection c=inspect(bytes);
        byte[] policy=HistoryControlStore.encodePolicy(c.registration.bundleId,1,c.latest.envelopeHash,128L*1024*1024,5);
        check(c.actualBytes+policy.length<=HistoryControlStore.MAX_BYTES&&c.actualBytes+c.pins.reservedBytes+policy.length>HistoryControlStore.MAX_BYTES,"policy fits physical allowance only by stealing reservation");
        rejects(()->bytes.capacity.setPolicy(new HistoryPolicy(128L*1024*1024,5)),"policy cannot spend terminal bytes");rejectedPolicyPromotion(bytes);bytes.capacity.reconcile();close(full,active,Outcome.CANCELLED,null);
        check(inspect(bytes).actualBytes<=HistoryControlStore.MAX_BYTES,"close fits byte reservation");
    }
    static void rejectedPolicyPromotion(Fixture f)throws Exception {
        HistoryControlStore.Inspection c=inspect(f);String name=HistoryControlStore.policyFilename(c.latest.generation+1);
        Path stage=f.parent.resolve("history_control/"+name+".stage");byte[] bytes=HistoryControlStore.encodePolicy(c.registration.bundleId,c.latest.generation+1,c.latest.envelopeHash,128L*1024*1024,5);Files.write(stage,bytes);
        rejects(()->f.capacity.reconcile(),"over-reserved policy stage blocked");
        check(Files.exists(stage)&&!Files.exists(f.parent.resolve("history_control/"+name)),"over-reserved policy stage never promoted");
        check(Arrays.equals(bytes,Files.readAllBytes(stage)),"over-reserved stage preserved exactly");Files.delete(stage);
    }
    static void overQuota()throws Exception {
        Fixture f=new Fixture();f.capacity.setPolicy(new HistoryPolicy(128L*1024*1024,20));long additional=64L*1024*1024+1-f.capacity.inspect().dataChargedBytes;
        while(additional>0){long size=Math.min(additional,ProtectionStore.MAX_DOCUMENT_BYTES);try(RandomAccessFile file=new RandomAccessFile(f.parent.resolve("store/"+UUID.randomUUID()+".stage").toFile(),"rw")){file.setLength(size);}additional-=size;}
        f.capacity.setPolicy(new HistoryPolicy(64L*1024*1024,20));check(f.capacity.inspect().overQuota,"over data quota fixture");HistoryPins pins=open(f);String id=begin(f,pins,Scope.EXACT_SUBSET);close(pins,id,Outcome.CANCELLED,null);
        check(inspect(f).pins.closes.size()==1,"pin admission does not require spare data quota");
    }
    static void invalidEvidence()throws Exception {
        Fixture original=new Fixture();HistoryPinRecord foreign=record(original);
        for(String kind:Arrays.asList("foreign","malformed","oversized","unknown","nonregular","duplicate","conflicting")){
            Fixture f=new Fixture();HistoryPins pins=open(f);HistoryPinRecord begin=record(f);Path root=f.parent.resolve("history_control");
            if(kind.equals("foreign"))Files.write(root.resolve(foreign.name()),HistoryPinRecord.encode(foreign));
            if(kind.equals("malformed"))Files.write(root.resolve(begin.name()),new byte[]{1});
            if(kind.equals("oversized"))Files.write(root.resolve(begin.name()),new byte[16385]);
            if(kind.equals("unknown"))Files.write(root.resolve("pin-unknown.begin"),new byte[]{1});
            if(kind.equals("nonregular"))Files.createDirectory(root.resolve(begin.name()));
            if(kind.equals("duplicate")){Files.write(root.resolve(begin.name()),HistoryPinRecord.encode(begin));HistoryPinRecord other=record(f);Files.write(root.resolve(other.name()),HistoryPinRecord.encode(other));}
            if(kind.equals("conflicting")){Files.write(root.resolve(begin.name()),HistoryPinRecord.encode(begin));Files.write(root.resolve(begin.name()+".stage"),HistoryPinRecord.encode(begin));}
            rejects(()->open(f),"invalid pin evidence "+kind);rejects(()->f.capacity.reconcile(),"ordinary authority blocks invalid evidence "+kind);
            check(HistoryStorage.pins(f.store.storageRoot())==pins,"failed candidate never replaces installed acknowledgement");
        }
        Fixture f=new Fixture();HistoryPins pins=open(f);String id=begin(f,pins,Scope.EXACT_SUBSET);
        try{HistoryMaintenanceRecovery.open(f.store,f.catalog,f.identity,f.index,f.disk);throw new AssertionError("maintenance bypass");}catch(IOException failure){check(failure.getMessage().equals("Maintenance blocked by operation pins"),"maintenance consults mandatory pin gate first");}
        f.disk.hook=(event,file)->{if(event.equals("file-after")){try{f.capacity.setPolicy(new HistoryPolicy(128L*1024*1024,5));throw new AssertionError("nested policy write");}catch(IOException expected){checks++;}throw new IOException("candidate failure");}};
        rejects(()->open(f),"failed candidate reconciliation");f.disk.hook=null;check(HistoryStorage.pins(f.store.storageRoot())==pins,"candidate acknowledgement isolation");pins.resume(id,input());
        for(boolean closing:new boolean[]{false,true}){
            Fixture changed=new Fixture();HistoryPins p=open(changed);HistoryPinRecord b;
            if(closing){begin(changed,p,Scope.EXACT_SUBSET);b=inspect(changed).pins.active;}else b=record(changed);
            Path stage=changed.parent.resolve("history_control/"+(closing?"pin-"+b.operation+".close":b.name())+".stage");byte[] bytes=closing?HistoryPinRecord.close(b,Outcome.CANCELLED):HistoryPinRecord.encode(b);Files.write(stage,bytes);
            Files.delete(changed.parent.resolve("store/"+changed.original.id+".snapshot"));rejects(()->{if(closing)recover(p,b.operation.toString(),null);else open(changed);},"full stage promotion requires live bound snapshot");check(Arrays.equals(bytes,Files.readAllBytes(stage)),"invalid full stage preserved");
        }
    }
    static void promotedCloseFinalCallback()throws Exception {
        Fixture f=new Fixture();HistoryPins pins=open(f);begin(f,pins,Scope.EXACT_SUBSET);HistoryPinRecord active=inspect(f).pins.active;
        Files.write(f.parent.resolve("history_control/pin-"+active.operation+".close.stage"),HistoryPinRecord.close(active,Outcome.CANCELLED));
        f.disk.hook=(event,file)->{if(event.equals("dir-after")&&file.equals(f.parent.toFile())){Path source=f.parent.resolve("store/"+f.original.id+".snapshot");byte[] bytes=Files.readAllBytes(source);bytes[bytes.length-1]^=1;Files.write(source,bytes);}};
        rejects(()->recover(pins,active.operation.toString(),null),"newly promoted CLOSE still validates input after last reconciliation callback");f.disk.hook=null;
        check(HistoryPins.pruneBlocked(f.store.storageRoot(),inspect(f)),"failed promoted close never acknowledged");
    }
    static void knownEvidenceOmission()throws Exception {
        for(boolean closed:new boolean[]{false,true}){
            Fixture f=new Fixture();HistoryPins pins=open(f);String id=begin(f,pins,Scope.EXACT_SUBSET);if(closed)close(pins,id,Outcome.CANCELLED,null);
            Files.delete(f.parent.resolve("history_control/pin-"+id+".begin"));if(closed)Files.delete(f.parent.resolve("history_control/pin-"+id+".close"));
            check(HistoryPins.pruneBlocked(f.store.storageRoot(),inspect(f)),"known retained pin omission fails closed");
            check(f.capacity.inspect().mutationBlocked,"capacity inspection exposes known omitted evidence");
            rejects(()->open(f),"explicit reconciliation cannot forget retained pin evidence");
            rejects(()->f.capacity.setPolicy(new HistoryPolicy(128L*1024*1024,5)),"policy cannot spend invisible known-pin reservation");
            rejects(()->f.capacity.reconcile(),"capacity reconciliation cannot forget known pin");
            rejects(()->f.store.putSnapshot("tab",null,null,1,new byte[]{8},"attempted"),"ordinary admission blocks missing known pin evidence");
            rejects(()->begin(f,pins,Scope.ALL_BUNDLE),"new BEGIN cannot erase missing active history");blocked(f);
        }
        Fixture callback=new Fixture();HistoryPins protectedPins=open(callback);String protectedId=begin(callback,protectedPins,Scope.EXACT_SUBSET);
        callback.disk.hook=(event,file)->{if(event.equals("dir-after")&&file.equals(callback.parent.resolve("history_control").toFile()))Files.delete(callback.parent.resolve("history_control/pin-"+protectedId+".begin"));};
        rejects(()->callback.capacity.reconcile(),"final capacity callback cannot erase pin reservation");callback.disk.hook=null;
        check(callback.capacity.inspect().mutationBlocked,"failed final capacity reconciliation stays blocked");
        Fixture older=new Fixture();HistoryPins retained=open(older);String first=begin(older,retained,Scope.EXACT_SUBSET);close(retained,first,Outcome.CANCELLED,null);
        String later=begin(older,retained,Scope.EXACT_SUBSET);close(retained,later,Outcome.CANCELLED,null);
        Files.delete(older.parent.resolve("history_control/pin-"+first+".begin"));Files.delete(older.parent.resolve("history_control/pin-"+first+".close"));
        check(inspect(older).pins.valid&&inspect(older).pins.present,"later closed pair remains valid on disk");
        check(HistoryPins.pruneBlocked(older.store.storageRoot(),inspect(older)),"omitted older audit cannot hide among later retained pairs");rejects(()->open(older),"reconciliation cannot drop older audit prefix");
        Fixture f=new Fixture();HistoryPins pins=open(f);String id=begin(f,pins,Scope.EXACT_SUBSET);close(pins,id,Outcome.CANCELLED,null);
        Path old=f.parent.resolve("old-control"),root=f.parent.resolve("history_control");Files.move(root,old);Files.createDirectory(root);
        try(DirectoryStream<Path> files=Files.newDirectoryStream(old)){for(Path file:files)Files.move(file,root.resolve(file.getFileName()));}
        check(HistoryPins.pruneBlocked(f.store.storageRoot(),inspect(f)),"replacement control directory requires new durability acknowledgement");open(f);
        check(!HistoryPins.pruneBlocked(f.store.storageRoot(),inspect(f)),"stable equivalent directory explicitly reconciles");
    }
    static void stagedBeginReservation()throws Exception {
        for(boolean byteLimit:new boolean[]{false,true}){
            Fixture f=new Fixture();HistoryPins pins=open(f);HistoryPinRecord sample=record(f);HistoryControlStore.Inspection initial=inspect(f);
            if(!byteLimit){while(inspect(f).actualEntries<255){HistoryControlStore.Inspection c=inspect(f);policyRecord(f,c.latest.generation+1,c.latest.envelopeHash);}}
            else{
                int pairs=70,total=(int)((HistoryControlStore.MAX_BYTES-initial.actualBytes-HistoryPinRecord.encode(sample).length-126-pairs*330L)/58)+2;
                for(int i=0;i<pairs;i++){int count=Math.min(256,total-(pairs-i-1));total-=count;List<Source> sources=new ArrayList<>();
                    for(int j=0;j<count;j++)sources.add(new Source(new UUID(0,j).toString(),45,sample.sources.get(0).hash));
                    HistoryPinRecord audit=HistoryPinRecord.begin(UUID.randomUUID(),initial.registration,initial.latest,Purpose.EXPORT,Scope.EXACT_SUBSET,sample.manifest,sources);
                    Files.write(f.parent.resolve("history_control/"+audit.name()),HistoryPinRecord.encode(audit));Files.write(f.parent.resolve("history_control/pin-"+audit.operation+".close"),HistoryPinRecord.close(audit,Outcome.CANCELLED));
                }
            }
            HistoryPinRecord begin=record(f);Path stage=f.parent.resolve("history_control/"+begin.name()+".stage");byte[] bytes=HistoryPinRecord.encode(begin);Files.write(stage,bytes);
            HistoryControlStore.Inspection c=inspect(f);check(c.actualBytes<=HistoryControlStore.MAX_BYTES&&c.actualEntries<=HistoryControlStore.MAX_ENTRIES,"full BEGIN stage physically fits control allowance");
            check(c.actualBytes+c.pins.reservedBytes>HistoryControlStore.MAX_BYTES||c.actualEntries+c.pins.reservedEntries>HistoryControlStore.MAX_ENTRIES,"full BEGIN stage cannot fund terminal reservation");
            rejects(()->open(f),"full BEGIN stage must reserve CLOSE before promotion");check(Files.exists(stage)&&Arrays.equals(bytes,Files.readAllBytes(stage))&&!Files.exists(f.parent.resolve("history_control/"+begin.name())),"over-reserved BEGIN stage preserved");
        }
    }
    public static void main(String[] args)throws Exception {lifecycle();sources();callbacks();stagesAndFaults();callbackFaults();allowance();overQuota();invalidEvidence();promotedCloseFinalCallback();knownEvidenceOmission();stagedBeginReservation();System.out.println("History pin lifecycle: "+checks+" checks PASS");}
}
