package com.example.markdowndesk;
import java.io.*;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.nio.file.attribute.*;
import java.util.*;
public final class ExecutorCheck {
    static int checks;
    static void check(boolean value,String reason){checks++;if(!value)throw new AssertionError(reason);}
    interface Action{void run()throws Exception;}
    static void rejects(Action action,String reason)throws Exception{try{action.run();throw new AssertionError(reason);}catch(IOException expected){checks++;}}
    static final class Disk implements ProtectionStore.Durability {
        interface Hook{void call(String event,File file)throws IOException;}Hook hook;
        void event(String name,File file)throws IOException{if(hook!=null)hook.call(name,file);}
        public void syncFile(FileDescriptor fd)throws IOException{event("file-before",null);fd.sync();event("file-after",null);}
        public void move(File a,File b)throws IOException{event("move-before",a);Files.move(a.toPath(),b.toPath(),StandardCopyOption.ATOMIC_MOVE);event("move-after",b);}
        public void syncDirectory(File dir)throws IOException{event("dir-before",dir);try(FileChannel channel=FileChannel.open(dir.toPath(),StandardOpenOption.READ)){channel.force(true);}event("dir-after",dir);}
    }
    static final class Bundle {
        final Path parent;final Disk disk=new Disk();final ProtectionStore store;final HistoryCatalog catalog;final HistoryIdentity identity;final HistoryIndex index;
        HistoryCapacity capacity;ProtectionStore.Transaction old,anchor,pending;HistoryPruner.DeletePlan plan;HistoryPruneIntent intent;Set<String> selection=new TreeSet<>();
        Bundle()throws Exception{this(Files.createTempDirectory("history-executor-"));}
        Bundle(Path parent)throws Exception{this(parent,64L*1024*1024);}
        Bundle(Path parent,long hint)throws Exception{this.parent=parent;Files.createDirectories(parent);store=new ProtectionStore(parent.resolve("store").toFile(),disk,hint);catalog=new HistoryCatalog(parent.resolve("catalog").toFile(),store,disk);identity=new HistoryIdentity(parent.resolve("identity").toFile(),disk);index=new HistoryIndex(store,catalog,identity,parent.resolve("index").toFile(),disk);}
        ProtectionStore.Transaction group(int size,boolean verified)throws Exception {
            byte[] original=new byte[size];Arrays.fill(original,(byte)7);byte[] attempted=new byte[size];Arrays.fill(attempted,(byte)9);
            ProtectionStore.Snapshot a=store.putSnapshot("tab","uri","title",1,original,"original"),b=store.putSnapshot("tab","uri","title",1,attempted,"attempted");ProtectionStore.Transaction t=store.prepare(a,b,a.hash);
            if(verified){store.complete(t);index.recordVerified(identity.resolve("uri","tab"),t.id);}return t;
        }
        void prepare(int size,boolean unresolved)throws Exception {
            old=group(size,true);anchor=group(size,true);selection.add(catalog.name(old.original.id,"selected").id);catalog.name(anchor.original.id,"anchor");if(unresolved)pending=group(2,false);
            capacity=HistoryCapacity.register(store,catalog,identity,index,new HistoryPolicy(64L*1024*1024,20),disk);preview();
        }
        HistoryGraph graph()throws IOException {HistoryCapacity.EffectivePolicy p=capacity.policy();return new HistoryGraph(store,catalog,identity,index,new HistoryPolicy(p.quotaBytes,p.automaticCount));}
        void preview()throws Exception {HistoryGraph.Graph before=graph().scan();plan=new HistoryPruner(graph()).planSelected(selection).plan;check(plan!=null,"selected native plan");HistoryCapacity.EffectivePolicy p=capacity.policy();intent=HistoryPruneIntent.create(plan,before,new HistoryPruneIntent.PolicyBinding(p.bundleId,p.generation,p.controlRecordHash,p.quotaBytes,p.automaticCount),auth(plan),HistoryMaintenanceRecovery.unaffectedFingerprint(before,plan.members));}
        HistoryMaintenanceExecutor executor()throws IOException{return new HistoryMaintenanceExecutor(store,catalog,identity,index,disk);}
        HistoryMaintenanceExecutor executor(HistoryMaintenanceExecutor.Boundary boundary)throws IOException{return new HistoryMaintenanceExecutor(store,catalog,identity,index,disk,boundary);}
        Path file(HistoryPruneIntent.Member member){return parent.resolve(member.role.wire).resolve(member.id+"."+member.kind.wire);}
        HistoryControlStore.Inspection control()throws IOException{return new HistoryControlStore(parent.toFile()).inspect();}
        Map<String,String> capture()throws Exception{return MaintenanceRecoveryCheck.captureTree(parent);}
        void execute()throws Exception{capacity=executor().execute(capacity,plan,auth(plan));}
    }
    static HistoryPruneIntent.Authorization auth(HistoryPruner.DeletePlan plan)throws IOException{return new HistoryPruneIntent.Authorization(plan.proofFingerprint,plan.selectedCheckpointIds);}
    static void survivors(Bundle f,Map<String,String> before)throws Exception {
        Set<String> removed=new HashSet<>();for(HistoryPruneIntent.Member member:f.intent.members){removed.add(member.key());check(!Files.exists(f.file(member)),"exact selected closure absent");}
        Map<String,String> after=f.capture();for(Map.Entry<String,String> entry:before.entrySet())if(!removed.contains(entry.getKey()))check(entry.getValue().equals(after.get(entry.getKey())),"unrelated exact bytes preserved: "+entry.getKey());
        check(after.size()==before.size()-removed.size(),"no extra publication or deletion");check(!f.capacity.inspect().mutationBlocked,"terminal capacity ready");
    }
    static void selected()throws Exception {
        Bundle f=new Bundle();f.prepare(3,true);long reserve=f.capacity.inspect().completionReservedBytes;Map<String,String> before=f.capture();f.execute();survivors(f,before);
        check(f.store.snapshots().size()==4&&f.store.transactions().size()==2,"survivor readers exclude whole selected group");
        check(f.capacity.inspect().completionReservedBytes==reserve&&f.capacity.inspect().completionReservedSlots==1,"unrelated PREPARED reservation survives");
        f.store.putSnapshot("tab",null,null,1,new byte[]{4},"attempted");check(f.store.snapshots().size()==5,"ordinary writes resume");
    }
    static void rejectedAdmission()throws Exception {
        for(String fault:Arrays.asList("policy","aba","superseded","profile","selection","changed","anchor","active-pin","unknown-stage","intent-stage","automatic")){
            Bundle f=new Bundle();f.prepare(3,false);HistoryCapacity authority=f.capacity;HistoryPruner.DeletePlan plan=f.plan;HistoryPruneIntent.Authorization authorization=auth(plan);
            switch(fault){
                case "policy":f.capacity.setPolicy(new HistoryPolicy(128L*1024*1024,5));break;
                case "aba":f.capacity.setPolicy(new HistoryPolicy(128L*1024*1024,5));f.capacity.setPolicy(new HistoryPolicy(64L*1024*1024,20));break;
                case "superseded":f.capacity=HistoryCapacity.open(f.store,f.catalog,f.identity,f.index,f.disk);break;
                case "profile":plan=new HistoryPruner(new HistoryGraph(f.store,f.catalog,f.identity,f.index,new HistoryPolicy(128L*1024*1024,5))).planSelected(f.selection).plan;authorization=auth(plan);break;
                case "selection":authorization=new HistoryPruneIntent.Authorization(plan.proofFingerprint,Collections.singleton(UUID.randomUUID().toString()));break;
                case "changed":f.catalog.name(f.old.attempted.id,"new unselected name");break;
                case "anchor":{Path p=f.parent.resolve("store/"+f.anchor.original.id+".snapshot");byte[] bytes=Files.readAllBytes(p);bytes[bytes.length-1]^=1;Files.write(p,bytes);break;}
                case "active-pin":HistoryPins pins=HistoryPins.open(f.store,f.catalog,f.identity,f.index,f.disk);pins.begin(HistoryPinRecord.Purpose.EXPORT,HistoryPinRecord.Scope.ALL_BUNDLE,Collections.emptySet(),HistoryPins.ManifestIdentity.read(HistoryPinRecord.Domain.NATIVE_HISTORY_V1,0,new ByteArrayInputStream(new byte[0])));break;
                case "unknown-stage":Files.write(f.parent.resolve("history_control/unknown.stage"),new byte[]{1});break;
                case "intent-stage":Files.write(f.parent.resolve("history_control/prune.intent.stage"),new byte[]{1});break;
                case "automatic":plan=new HistoryPruner(f.graph()).planAutomatic(0,0).plan;authorization=auth(plan);break;
            }
            Map<String,String> before=f.capture();final HistoryPruner.DeletePlan preview=plan;final HistoryPruneIntent.Authorization auth=authorization;
            rejects(()->f.executor().execute(authority,preview,auth),"invalid selected admission "+fault);check(before.equals(f.capture()),"rejected admission writes nothing "+fault);
        }
        MaintenanceRecoveryCheck.Fixture automatic=new MaintenanceRecoveryCheck.Fixture();automatic.prepare(false);automatic.publish();
        Map<String,String> before=automatic.capture();rejects(()->new HistoryMaintenanceExecutor(automatic.store,automatic.catalog,automatic.identity,automatic.index,automatic.disk).replay(),"automatic replay unsupported");check(before.equals(automatic.capture()),"automatic replay unmodified");
    }
    static void publicationFaults()throws Exception {
        for(String event:Arrays.asList("file-before","file-after","move-before","move-after","dir-before","dir-after")){
            Bundle f=new Bundle();f.prepare(3,false);Map<String,String> before=f.capture();
            f.disk.hook=(name,file)->{if(name.equals(event)&&(!event.startsWith("dir")||file.getName().equals("history_control")))throw new IOException("publication fault");};
            rejects(f::execute,"publication failure "+event);f.disk.hook=null;
            Map<String,String> after=f.capture();for(Map.Entry<String,String> entry:before.entrySet())check(entry.getValue().equals(after.get(entry.getKey())),"no native unlink before durable intent");
            if(f.control().intentStage!=null){Map<String,String> retained=f.capture();rejects(()->f.executor().replay(),"all retained stages blocked");check(retained.equals(f.capture()),"stage preserved");}
            else{f.capacity=f.executor().replay();survivors(f,before);}
        }
    }
    static void retirement()throws Exception {
        for(String fault:Arrays.asList("before-intent","after-intent","final-sync")){
            Bundle f=new Bundle();f.prepare(3,false);Map<String,String> before=f.capture();boolean[] fired={false};
            HistoryMaintenanceExecutor executor=f.executor((event,key)->{if(!fired[0]&&event==(fault.equals("before-intent")?HistoryMaintenanceExecutor.Event.BEFORE_INTENT_UNLINK:HistoryMaintenanceExecutor.Event.AFTER_INTENT_UNLINK)&&!fault.equals("final-sync")){fired[0]=true;throw new IOException("retirement fault");}});
            if(fault.equals("final-sync"))f.disk.hook=(event,file)->{if(event.equals("dir-before")&&file.getName().equals("history_control")&&!Files.exists(f.parent.resolve("history_control/prune.intent"))){fired[0]=true;throw new IOException("last control sync fault");}};
            rejects(()->executor.execute(f.capacity,f.plan,auth(f.plan)),"retirement interrupted");f.disk.hook=null;
            check(fired[0]&&HistoryStorage.retirement(f.store.storageRoot())!=null,"shared retirement capability retained");
            rejects(()->f.store.snapshots(),"retirement readers cannot use stale view");
            rejects(()->f.store.putSnapshot("tab",null,null,1,new byte[]{1},"attempted"),"retirement blocks writers");
            rejects(()->HistoryCapacity.open(f.store,f.catalog,f.identity,f.index,f.disk),"ordinary open cannot erase retirement");
            rejects(()->f.capacity.reconcile(),"ordinary reconcile cannot erase retirement");
            rejects(()->HistoryPins.open(f.store,f.catalog,f.identity,f.index,f.disk),"ordinary pin open cannot replace terminal acknowledgement");
            Bundle second=new Bundle(f.parent);f.capacity=second.executor().replay();survivors(f,before);check(HistoryStorage.retirement(f.store.storageRoot())==null,"exact terminal state cleared");
        }
        Bundle f=new Bundle();f.prepare(3,false);Set<PosixFilePermission> permissions=Files.getPosixFilePermissions(f.parent.resolve("history_control"));
        try{HistoryMaintenanceExecutor executor=f.executor((event,key)->{if(event==HistoryMaintenanceExecutor.Event.BEFORE_INTENT_UNLINK)Files.setPosixFilePermissions(f.parent.resolve("history_control"),PosixFilePermissions.fromString("r-x------"));});
            rejects(()->executor.execute(f.capacity,f.plan,auth(f.plan)),"actual OS intent unlink permission failure");check(Files.exists(f.parent.resolve("history_control/prune.intent")),"failed OS unlink retains intent");
        }finally{Files.setPosixFilePermissions(f.parent.resolve("history_control"),permissions);}
        f.capacity=f.executor().replay();check(!f.capacity.inspect().mutationBlocked,"same-process present-intent retirement retry");
    }
    static void replace(Path path)throws IOException {Path copy=path.resolveSibling(path.getFileName()+".replacement");Files.copy(path,copy);Files.move(copy,path,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}
    static void callbacks()throws Exception {
        for(String fault:Arrays.asList("member","survivor","intent","policy","directory","unknown","after-member","last-data-sync","last-control-sync","last-capacity-sync")){
            Bundle f=new Bundle();f.prepare(3,false);boolean[] fired={false};int[] removed={0},terminalSyncs={0};Map<String,String> before=f.capture();
            Action mutate=()->{switch(fault){
                case "member":replace(f.file(f.intent.members.get(0)));break;
                case "intent":replace(f.parent.resolve("history_control/prune.intent"));break;
                case "policy":replace(f.parent.resolve("history_control/"+(f.capacity.policy().generation==0?HistoryControlStore.REGISTRATION_NAME:HistoryControlStore.policyFilename(f.capacity.policy().generation))));break;
                case "directory":{Path from=f.parent.resolve("identity"),to=f.parent.resolve("moved-identity");Files.move(from,to);Files.createDirectory(from);try(java.util.stream.Stream<Path> paths=Files.list(to)){for(Path p:(Iterable<Path>)paths::iterator)Files.move(p,from.resolve(p.getFileName()));}Files.delete(to);break;}
                case "unknown":Files.write(f.parent.resolve("store/unknown"),new byte[]{1});break;
                default:replace(f.parent.resolve("store/"+f.anchor.original.id+".snapshot"));
            }};
            HistoryMaintenanceExecutor.Boundary boundary=(event,key)->{
                if(event==HistoryMaintenanceExecutor.Event.AFTER_MEMBER_UNLINK)removed[0]++;
                if(!fired[0]&&(fault.equals("after-member")?event==HistoryMaintenanceExecutor.Event.AFTER_MEMBER_UNLINK:event==HistoryMaintenanceExecutor.Event.BEFORE_MEMBER_UNLINK)&&!fault.startsWith("last-")){
                    try{mutate.run();fired[0]=true;}catch(Exception e){throw new IOException(e);}
                }
            };
            if(fault.startsWith("last-"))f.disk.hook=(event,file)->{boolean all=true;for(HistoryPruneIntent.Member member:f.intent.members)all&=!Files.exists(f.file(member));
                if(event.equals("dir-after")&&file.getName().equals("history_control")&&!Files.exists(f.parent.resolve("history_control/prune.intent")))terminalSyncs[0]++;
                if(!fired[0]&&event.equals("dir-after")&&all&&(fault.equals("last-data-sync")?file.getName().equals("index"):file.getName().equals("history_control")&&terminalSyncs[0]==(fault.equals("last-capacity-sync")?2:1))){
                    try{mutate.run();fired[0]=true;}catch(Exception e){throw new IOException(e);}
                }};
            rejects(()->f.executor(boundary).execute(f.capacity,f.plan,auth(f.plan)),"callback mutation blocked "+fault);f.disk.hook=null;
            check(fired[0],"mutation boundary reached "+fault);check(removed[0]==(fault.startsWith("last-")?f.intent.members.size():fault.equals("after-member")?1:0),"no deletion after mutated evidence "+fault);
            if(fault.equals("last-data-sync"))check(Files.exists(f.parent.resolve("history_control/prune.intent")),"last data callback cannot retire intent");
            if(fault.equals("last-control-sync")||fault.equals("last-capacity-sync")){check(HistoryStorage.retirement(f.store.storageRoot())!=null,"terminal callback keeps capability");rejects(()->HistoryCapacity.open(f.store,f.catalog,f.identity,f.index,f.disk),"corrupt terminal state never opens");}
            for(Map.Entry<String,String> record:before.entrySet())if(!record.getKey().startsWith("store/unknown"))check(!Files.exists(f.parent.resolve(record.getKey()))||record.getValue().equals(f.capture().get(record.getKey())),"same-byte callback changes preserve survivor bytes");
        }
        Bundle nested=new Bundle();nested.prepare(3,false);int[] attempts={0};nested.disk.hook=(event,file)->{
            attempts[0]++;try{nested.store.putSnapshot("tab",null,null,1,new byte[]{1},"attempted");throw new AssertionError("reentrant mutation accepted");}catch(IOException blocked){}
            try{HistoryCapacity.open(nested.store,nested.catalog,nested.identity,nested.index,nested.disk);throw new AssertionError("reentrant authority accepted");}catch(IOException blocked){}
        };nested.execute();check(attempts[0]>10,"nested writers rejected across sync and move callbacks");nested.disk.hook=null;
    }
    static void retainedStagesAndAudits()throws Exception {
        for(int length:new int[]{0,1,20,Integer.MAX_VALUE}){
            Bundle f=new Bundle();f.prepare(3,false);byte[] encoded=HistoryPruneIntent.encode(f.intent);Files.write(f.parent.resolve("history_control/prune.intent.stage"),Arrays.copyOf(encoded,Math.min(encoded.length,length)));Map<String,String> before=f.capture();
            rejects(()->f.executor().replay(),"all retained intent stages blocked");check(before.equals(f.capture()),"retained stage bytes unchanged");
        }
        Bundle f=new Bundle();f.prepare(3,false);HistoryPins pins=HistoryPins.open(f.store,f.catalog,f.identity,f.index,f.disk);
        String operation=pins.begin(HistoryPinRecord.Purpose.EXPORT,HistoryPinRecord.Scope.EXACT_SUBSET,Collections.singleton(f.old.original.id),HistoryPins.ManifestIdentity.read(HistoryPinRecord.Domain.NATIVE_HISTORY_V1,0,new ByteArrayInputStream(new byte[0])));
        pins.close(operation,HistoryPinRecord.Outcome.CANCELLED,null);f.preview();Map<String,String> before=f.capture();f.execute();survivors(f,before);HistoryPins.open(f.store,f.catalog,f.identity,f.index,f.disk);check(!HistoryPins.pruneBlocked(f.store.storageRoot(),f.control()),"closed audit survives retired native inputs and intent removal");
        Bundle full=new Bundle();full.prepare(3,false);for(int i=full.control().actualEntries;i<HistoryControlStore.MAX_ENTRIES;i++)full.capacity.setPolicy(new HistoryPolicy(64L*1024*1024,20));full.preview();Map<String,String> exact=full.capture();rejects(full::execute,"shared slot allowance blocks publication");check(exact.equals(full.capture()),"full allowance no write");
    }
    static void byteAllowanceAndSelection()throws Exception {
        Bundle f=new Bundle();f.prepare(3,false);HistoryControlStore.Inspection control=f.control();int pairs=70,total=(int)((HistoryControlStore.MAX_BYTES-control.actualBytes-pairs*330L)/58);
        String hash=HistoryStorage.hex(HistoryStorage.digest().digest(new byte[0]));HistoryPinRecord.Manifest manifest=new HistoryPinRecord.Manifest(HistoryPinRecord.Domain.NATIVE_HISTORY_V1,0,0,hash);
        for(int i=0;i<pairs;i++){
            int count=Math.min(256,total-(pairs-i-1));total-=count;List<HistoryPinRecord.Source> sources=new ArrayList<>();for(int j=0;j<count;j++)sources.add(new HistoryPinRecord.Source(new UUID(0,j).toString(),45,hash));
            HistoryPinRecord audit=HistoryPinRecord.begin(UUID.randomUUID(),control.registration,control.latest,HistoryPinRecord.Purpose.EXPORT,HistoryPinRecord.Scope.EXACT_SUBSET,manifest,sources);
            Files.write(f.parent.resolve("history_control/"+audit.name()),HistoryPinRecord.encode(audit));Files.write(f.parent.resolve("history_control/pin-"+audit.operation+".close"),HistoryPinRecord.close(audit,HistoryPinRecord.Outcome.RELEASED));
        }
        HistoryPins.open(f.store,f.catalog,f.identity,f.index,f.disk);f.capacity.reconcile();f.preview();control=f.control();check(control.actualBytes<=HistoryControlStore.MAX_BYTES&&control.actualBytes+f.intent.serializedBytes>HistoryControlStore.MAX_BYTES&&control.actualEntries<256,"byte-only allowance boundary");
        Map<String,String> before=f.capture();rejects(f::execute,"shared bytes block intent publication");check(before.equals(f.capture()),"full shared bytes no mutation");
        Bundle names=new Bundle();names.prepare(3,false);String second=names.catalog.name(names.old.attempted.id,"second selected").id;check(new HistoryPruner(names.graph()).planSelected(names.selection).plan==null,"partial group selection blocked");
        String newest=names.catalog.name(names.anchor.attempted.id,"newest").id;check(new HistoryPruner(names.graph()).planSelected(Collections.singleton(newest)).plan==null,"newest history anchor cannot execute");
        names.selection.add(second);names.preview();before=names.capture();names.execute();survivors(names,before);
    }

    static void interruptedAccounting()throws Exception {
        for(String kind:Arrays.asList("checkpoint","index","complete","transaction","snapshot")){
            Bundle f=new Bundle();f.prepare(3,true);Map<String,String> before=f.capture();long reserved=f.capacity.inspect().completionReservedBytes;
            HistoryMaintenanceExecutor executor=f.executor((event,key)->{if(event==HistoryMaintenanceExecutor.Event.AFTER_MEMBER_UNLINK&&key.endsWith("."+kind))throw new IOException("after real "+kind+" unlink");});
            rejects(()->executor.execute(f.capacity,f.plan,auth(f.plan)),"interrupted actual member prefix "+kind);
            long physical=0;for(String role:Arrays.asList("store","catalog","identity","index"))try(java.util.stream.Stream<Path> paths=Files.list(f.parent.resolve(role))){for(Path p:(Iterable<Path>)paths::iterator)physical+=Files.size(p);}
            HistoryCapacity.Usage usage=f.capacity.inspect();check(usage.usageComplete&&usage.dataActualBytes==physical,"partial prefix charges physical survivors "+kind);check(usage.completionReservedBytes==reserved&&usage.completionReservedSlots==1,"partial prefix keeps only genuine reservation "+kind);
            check(f.store.snapshots().size()==4&&f.store.transactions().size()==2,"partial prefix logical readers omit full closure "+kind);
            f.capacity=f.executor().replay();survivors(f,before);
        }
    }
    static void foreignBootstrap()throws Exception {
        for(boolean terminal:new boolean[]{false,true}){
            Bundle valid=new Bundle();Bundle foreign=new Bundle(valid.parent,128L*1024*1024);valid.prepare(3,false);
            if(terminal){HistoryMaintenanceExecutor interrupted=valid.executor((event,key)->{if(event==HistoryMaintenanceExecutor.Event.BEFORE_INTENT_UNLINK)throw new IOException("retirement boundary");});rejects(()->interrupted.execute(valid.capacity,valid.plan,auth(valid.plan)),"retained retirement");}
            if(!terminal){foreign.capacity=valid.capacity;foreign.selection.addAll(valid.selection);foreign.preview();}
            Map<String,String> before=valid.capture();rejects(()->{if(terminal)foreign.executor().replay();else foreign.executor().execute(valid.capacity,foreign.plan,auth(foreign.plan));},"pre-enrollment foreign bootstrap rejected");
            check(before.equals(valid.capture()),"foreign bootstrap cannot publish or retire intent "+terminal);
            if(terminal){valid.capacity=valid.executor().replay();check(!valid.capacity.inspect().mutationBlocked,"correct bootstrap retries retained retirement");}
        }
    }
    static void largeIntent()throws Exception {Bundle f=new Bundle();f.prepare(3,false);for(int i=0;i<30;i++)f.selection.add(f.catalog.name(f.old.original.id,"selected "+i).id);f.preview();check(f.intent.serializedBytes>16384&&f.intent.serializedBytes<=65536,"executor uses bounded intent allowance above policy cap");Map<String,String> before=f.capture();f.execute();survivors(f,before);}
    static void memory()throws Exception {Bundle f=new Bundle();f.prepare(ProtectionStore.MAX_DOCUMENT_BYTES,true);byte[] pressure=new byte[32*1024*1024];Map<String,String> before=f.capture();f.execute();survivors(f,before);check(pressure[pressure.length-1]==0,"32MiB live pressure survives near-limit pairs");}
    public static void main(String[] args)throws Exception{if(args.length>0){memory();System.out.println("Journaled executor memory: "+checks+" checks PASS");return;}foreignBootstrap();selected();rejectedAdmission();publicationFaults();retirement();callbacks();retainedStagesAndAudits();byteAllowanceAndSelection();interruptedAccounting();largeIntent();System.out.println("Journaled executor: "+checks+" checks PASS");}
}
