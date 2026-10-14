package com.example.markdowndesk;

import java.io.*;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.util.*;

/** Synthetic interrupted removals; only fixtures unlink. Production recovery is read-only. */
public final class MaintenanceRecoveryCheck {
    static int checks,cases;
    static void check(boolean value,String reason){checks++;if(!value)throw new AssertionError(reason);}
    interface Action {void run()throws Exception;}
    static void rejects(Action action)throws Exception{try{action.run();throw new AssertionError("expected maintenance rejection");}catch(IOException expected){checks++;}}
    static final class Disk implements ProtectionStore.Durability {
        String callbackDirectory;IntentCodecCheck.Action callback;int moves,writes,syncs;
        public void syncFile(FileDescriptor descriptor)throws IOException{syncs++;descriptor.sync();}
        public void move(File a,File b)throws IOException{moves++;Files.move(a.toPath(),b.toPath(),StandardCopyOption.ATOMIC_MOVE);}
        public void syncDirectory(File directory)throws IOException{
            syncs++;if(callback!=null&&directory.getName().equals(callbackDirectory)){IntentCodecCheck.Action action=callback;callback=null;action.run();}
            try(FileChannel channel=FileChannel.open(directory.toPath(),StandardOpenOption.READ)){channel.force(true);}
        }
    }
    static final class Fixture {
        final Path parent;final Disk disk=new Disk();
        final ProtectionStore store;final HistoryCatalog catalog;final HistoryIdentity identity;final HistoryIndex index;
        HistoryCapacity capacity;HistoryPruneIntent intent;HistoryGraph.Graph before;HistoryPruner.DeletePlan plan;
        ProtectionStore.Transaction old,anchor;String history;int nextOriginalSize=3;
        Fixture()throws Exception{this(Files.createTempDirectory("maintenance-projection-"));}
        Fixture(Path parent)throws Exception{
            this.parent=parent;store=new ProtectionStore(parent.resolve("store").toFile(),disk,64L*1024*1024);
            catalog=new HistoryCatalog(parent.resolve("catalog").toFile(),store,disk);identity=new HistoryIdentity(parent.resolve("identity").toFile(),disk);
            index=new HistoryIndex(store,catalog,identity,parent.resolve("index").toFile(),disk);
        }
        ProtectionStore.Transaction group()throws Exception {
            byte[] original=nextOriginalSize==3?new byte[]{1,2,3}:new byte[nextOriginalSize];nextOriginalSize=3;
            ProtectionStore.Snapshot a=store.putSnapshot("tab","content://maintenance","title",1,original,"original");
            ProtectionStore.Snapshot b=store.putSnapshot("tab","content://maintenance","title",1,new byte[]{4,5},"attempted");
            ProtectionStore.Transaction t=store.prepare(a,b,a.hash);store.complete(t);
            history=identity.resolve("content://maintenance","tab");index.recordVerified(history,t.id);return t;
        }
        void prepare(boolean named)throws Exception {prepare(named?1:0,0);}
        void prepare(int names,int policies)throws Exception {
            old=group();anchor=group();List<String> checkpoints=new ArrayList<>();
            for(int i=0;i<names;i++)checkpoints.add(catalog.name(old.original.id,"old selected name "+i).id);
            catalog.name(anchor.original.id,"anchor name");
            capacity=HistoryCapacity.register(store,catalog,identity,index,new HistoryPolicy(64L*1024*1024,20),disk);
            for(int i=0;i<policies;i++)capacity.setPolicy(new HistoryPolicy(64L*1024*1024,20));
            before=graph().scan();HistoryPruner pruner=new HistoryPruner(graph());
            plan=names>0?pruner.planSelected(checkpoints).plan:pruner.planAutomaticPressure(1,1).plan;
            check(plan!=null,"native registered plan");
            HistoryCapacity.EffectivePolicy p=capacity.policy();
            intent=HistoryPruneIntent.create(plan,before,new HistoryPruneIntent.PolicyBinding(p.bundleId,p.generation,p.controlRecordHash,p.quotaBytes,p.automaticCount),IntentCodecCheck.auth(plan),HistoryMaintenanceRecovery.unaffectedFingerprint(before,plan.members));
        }
        HistoryGraph graph()throws Exception{return new HistoryGraph(store,catalog,identity,index,new HistoryPolicy(64L*1024*1024,20));}
        Path intentPath(){return parent.resolve("history_control/prune.intent");}
        Path file(HistoryPruneIntent.Member member){return parent.resolve(member.role.wire).resolve(member.id+"."+member.kind.wire);}
        void publish()throws Exception{Files.write(intentPath(),HistoryPruneIntent.encode(intent));}
        void remove(String kind)throws Exception{for(HistoryPruneIntent.Member member:intent.members)if(kind==null||member.kind.wire.equals(kind))Files.delete(file(member));}
        HistoryMaintenanceRecovery.RecoveryView open()throws IOException{return HistoryMaintenanceRecovery.open(store,catalog,identity,index,disk);}
        Map<String,String> capture()throws Exception{return captureTree(parent);}
    }
    static Map<String,String> captureTree(Path parent)throws Exception {
        Map<String,String> result=new TreeMap<>();
        try(java.util.stream.Stream<Path> paths=Files.walk(parent)){
            for(Path file:(Iterable<Path>)paths::iterator)if(Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS)){
                java.security.MessageDigest digest=HistoryStorage.digest();try(InputStream in=Files.newInputStream(file)){byte[] buffer=new byte[8192];int n;while((n=in.read(buffer))!=-1)digest.update(buffer,0,n);}
                result.put(parent.relativize(file).toString(),HistoryStorage.hex(digest.digest()));
            }
        }return result;
    }
    static void discovery()throws Exception {
        Fixture f=new Fixture();f.prepare(false);f.publish();
        HistoryControlStore.Inspection control=new HistoryControlStore(f.parent.toFile()).inspect();
        check(control.chainComplete&&control.committedIntent.intent!=null,"typed committed intent retains chain");
        check(control.state==HistoryControlStore.State.BLOCKED&&control.usageComplete&&control.actualEntries==3,"shared marker/control/intent accounting");
        Map<String,String> bytes=f.capture();
        for(Action read:Arrays.<Action>asList(f.store::snapshots,f.store::transactions,f.store::inspect,f.catalog::all,()->f.catalog.list("tab"),f.catalog::pinnedSnapshotIds,
                f.identity::descriptors,()->f.identity.lookup(f.history),()->f.index.list(f.history),()->f.graph().scan(),()->f.store.snapshot(f.anchor.original.id),()->f.anchor.original.bytes(),()->f.store.validate(f.anchor)))rejects(read);
        rejects(f.capacity::reconcile);rejects(()->HistoryCapacity.open(f.store,f.catalog,f.identity,f.index,f.disk));
        rejects(()->HistoryCapacity.register(f.store,f.catalog,f.identity,f.index,new HistoryPolicy(64L*1024*1024,20),f.disk));
        rejects(()->f.capacity.setPolicy(new HistoryPolicy(128L*1024*1024,20)));
        rejects(()->f.store.putSnapshot("tab",null,null,1,new byte[0],"original"));check(bytes.equals(f.capture()),"ordinary paths preserve intent and sources");
        cases++;
    }
    static void verifySurvivors(Fixture f)throws Exception {
        HistoryMaintenanceRecovery.RecoveryView opened=f.open();HistoryGraph.Graph graph=f.graph().scan();
        check(graph.complete&&graph.transactions.size()==1&&graph.ownerships.size()==1,"complete logical survivor graph");
        check(graph.records.size()==7&&graph.snapshots.size()==2&&graph.checkpoints.size()==1,"logical graph excludes entire removed closure");
        check(graph.reservedCompletionBytes==0&&graph.reservedCompletionSlots==0,"no phantom reservation");
        check(f.store.transactions().size()==1&&f.store.transactions().get(0).id.equals(f.anchor.id),"only anchor transaction");
        check(f.store.snapshots().size()==2&&f.catalog.all().size()==1&&f.catalog.list("tab").size()==1&&f.catalog.pinnedSnapshotIds().size()==1,"all logical enumeration boundaries");
        check(f.index.list(f.history).size()==2&&f.identity.lookup(f.history)!=null,"index and identity survivors");
        check(Arrays.equals(f.store.snapshot(f.anchor.original.id).bytes(),new byte[]{1,2,3}),"exact original anchor bytes");
        check(Arrays.equals(f.anchor.attempted.bytes(),new byte[]{4,5}),"exact attempted anchor bytes");
        rejects(()->f.store.snapshot(f.old.original.id));rejects(()->f.old.original.bytes());rejects(()->f.store.validate(f.old));
        long physical=0;int entries=0;for(String role:Arrays.asList("store","catalog","identity","index"))try(java.util.stream.Stream<Path> paths=Files.list(f.parent.resolve(role))){for(Path file:(Iterable<Path>)paths::iterator){physical+=Files.size(file);entries++;}}
        check(graph.actualBytes==physical&&graph.occupiedEntries==entries,"raw bytes and slots remain physically charged");
        check(opened.graph.actualBytes==physical&&opened.intentHash.equals(f.intent.envelopeHash),"opener reports exact read proof");
        check(HistoryMaintenanceRecovery.unaffectedFingerprint(graph,f.intent.members).equals(f.intent.unaffectedFingerprint),"same whole-closure fingerprint after recovery");
        rejects(()->f.capacity.reconcile());
    }
    static void projections()throws Exception {
        for(String removed:Arrays.asList("intact","complete","snapshot","index","checkpoint","all")){
            Fixture f=new Fixture();f.prepare(true);f.publish();if(!removed.equals("intact"))f.remove(removed.equals("all")?null:removed);
            Map<String,String> before=f.capture();int moves=f.disk.moves;verifySurvivors(f);verifySurvivors(f);
            check(before.equals(f.capture())&&moves==f.disk.moves,"repeated projection never changes disk: "+removed);cases++;
        }
    }
    static void replaceSame(Path path)throws IOException {byte[] bytes=Files.readAllBytes(path);Path temp=path.resolveSibling(path.getFileName()+".replacement");Files.write(temp,bytes);Files.move(temp,path,StandardCopyOption.REPLACE_EXISTING);}
    static void installedReplacement()throws Exception {
        Fixture f=new Fixture();f.prepare(false);f.publish();f.open();replaceSame(f.file(f.intent.members.get(0)));
        for(Action read:Arrays.<Action>asList(f.store::snapshots,f.store::transactions,f.store::inspect,f.catalog::all,()->f.catalog.list("tab"),f.catalog::pinnedSnapshotIds,
                f.identity::descriptors,()->f.identity.lookup(f.history),()->f.index.list(f.history),()->f.graph().scan(),()->f.store.snapshot(f.anchor.original.id),()->f.anchor.original.bytes(),()->f.store.validate(f.anchor)))rejects(read);
        cases++;
    }
    static void failures()throws Exception {
        for(String fault:Arrays.asList("member","anchor","identity","outside-missing","unknown","stage","symlink","directory","marker","policy","intent","intent-stage","policy-stage","enrollment-stage","registration-stage","control-unknown","index-stage","identity-stage","store-stage","member-replaced")){
            Fixture f=new Fixture();f.prepare(true);f.publish();
            switch(fault){
                case "member": Files.write(f.file(f.intent.members.get(0)),new byte[]{0});break;
                case "anchor": Files.write(f.parent.resolve("store/"+f.anchor.original.id+".snapshot"),new byte[]{0});break;
                case "identity": Files.write(f.file(f.intent.identityEnvelopes.get(0)),new byte[]{0});break;
                case "outside-missing":Files.delete(f.parent.resolve("store/"+f.anchor.original.id+".snapshot"));break;
                case "unknown":Files.write(f.parent.resolve("store/unknown"),new byte[]{0});break;
                case "stage":Files.write(f.parent.resolve("catalog/"+UUID.randomUUID()+".stage"),new byte[]{0});break;
                case "symlink":{Path member=f.file(f.intent.members.get(0));Files.delete(member);Files.createSymbolicLink(member,f.parent.resolve("store/"+f.anchor.original.id+".snapshot"));break;}
                case "directory":{Path member=f.file(f.intent.members.get(0));Files.delete(member);Files.createDirectory(member);break;}
                case "marker":Files.delete(f.parent.resolve(HistoryControlStore.ENROLLMENT_NAME));break;
                case "policy":{HistoryControlStore.Inspection c=new HistoryControlStore(f.parent.toFile()).inspect();Files.write(f.parent.resolve("history_control/"+HistoryControlStore.policyFilename(1)),HistoryControlStore.encodePolicy(c.latest.bundleId,1,c.latest.envelopeHash,128L*1024*1024,20));break;}
                case "intent":Files.write(f.intentPath(),new byte[]{0});break;
                case "intent-stage":Files.write(f.parent.resolve("history_control/prune.intent.stage"),HistoryPruneIntent.encode(f.intent));break;
                case "policy-stage":{HistoryControlStore.Inspection c=new HistoryControlStore(f.parent.toFile()).inspect();Files.write(f.parent.resolve("history_control/"+HistoryControlStore.policyFilename(1)+".stage"),HistoryControlStore.encodePolicy(c.latest.bundleId,1,c.latest.envelopeHash,128L*1024*1024,20));break;}
                case "enrollment-stage":Files.copy(f.parent.resolve(HistoryControlStore.ENROLLMENT_NAME),f.parent.resolve(HistoryControlStore.ENROLLMENT_STAGE));break;
                case "registration-stage":Files.copy(f.parent.resolve("history_control/registration.control"),f.parent.resolve("history_control/registration.control.stage"));break;
                case "control-unknown":Files.write(f.parent.resolve("history_control/unknown"),new byte[]{0});break;
                case "index-stage":Files.write(f.parent.resolve("index/"+UUID.randomUUID()+".stage"),new byte[]{0});break;
                case "identity-stage":Files.write(f.parent.resolve("identity/"+UUID.randomUUID()+".stage"),new byte[]{0});break;
                case "store-stage":Files.write(f.parent.resolve("store/"+UUID.randomUUID()+".stage"),new byte[]{0});break;
                case "member-replaced":f.open();replaceSame(f.file(f.intent.members.get(0)));rejects(f.store::inspect);cases++;continue;
            }
            Map<String,String> before=f.capture();rejects(f::open);check(before.equals(f.capture()),"failed recovery retains evidence: "+fault);cases++;
        }
    }
    static void callbacks()throws Exception {
        for(String fault:Arrays.asList("failure","member","same-member","same-intent","intent","marker","policy","anchor")){
            Fixture f=new Fixture();f.prepare(false);f.publish();String policyHash=f.capacity.policy().controlRecordHash;
            f.disk.callbackDirectory="history_control";f.disk.callback=()->{
                // The candidate must not filter a same-thread ordinary read before commit.
                try{f.store.snapshots();throw new AssertionError("candidate leaked or active intent allowed raw read");}catch(IOException expected){checks++;}
                try{f.store.putSnapshot("tab",null,null,0,new byte[0],"original");throw new AssertionError("nested mutation accepted");}catch(IOException expected){checks++;}
                if(fault.equals("failure"))throw new IOException("injected last callback failure");
                if(fault.equals("member"))Files.write(f.file(f.intent.members.get(0)),new byte[]{0});
                if(fault.equals("same-member"))replaceSame(f.file(f.intent.members.get(0)));
                if(fault.equals("same-intent"))replaceSame(f.intentPath());
                if(fault.equals("intent"))Files.write(f.intentPath(),new byte[]{0});
                if(fault.equals("marker"))Files.delete(f.parent.resolve(HistoryControlStore.ENROLLMENT_NAME));
                if(fault.equals("anchor"))Files.write(f.parent.resolve("store/"+f.anchor.original.id+".snapshot"),new byte[]{0});
                if(fault.equals("policy")){HistoryControlStore.Inspection c=new HistoryControlStore(f.parent.toFile()).inspect();Files.write(f.parent.resolve("history_control/"+HistoryControlStore.policyFilename(1)),HistoryControlStore.encodePolicy(c.latest.bundleId,1,c.latest.envelopeHash,128L*1024*1024,20));}
            };
            rejects(f::open);check(f.capacity.policy().controlRecordHash.equals(policyHash),"failed candidate preserves acknowledged policy");
            rejects(f.store::snapshots);
            if(fault.equals("failure")){rejects(f.store::snapshots);f.open();check(f.store.snapshots().size()==2,"explicit later retry installs projection");}
            cases++;
        }
    }
    static void uninstalledGate()throws Exception {
        for(String name:Arrays.asList(HistoryPruneIntent.INTENT_NAME,HistoryPruneIntent.STAGE_NAME))for(String type:Arrays.asList("malformed","directory","symlink","unreadable")){
            Fixture f=new Fixture();f.prepare(false);Path path=f.parent.resolve("history_control").resolve(name);
            if(type.equals("directory"))Files.createDirectory(path);
            else if(type.equals("symlink"))Files.createSymbolicLink(path,f.parent.resolve("absent-target"));
            else Files.write(path,new byte[]{0});
            Set<java.nio.file.attribute.PosixFilePermission> permissions=null;
            if(type.equals("unreadable")){permissions=Files.getPosixFilePermissions(path);Files.setPosixFilePermissions(path,Collections.emptySet());}
            try{
                for(Action read:Arrays.<Action>asList(f.store::snapshots,f.store::transactions,f.store::inspect,f.catalog::all,()->f.catalog.list("tab"),f.catalog::pinnedSnapshotIds,
                        f.identity::descriptors,()->f.identity.lookup(f.history),()->f.index.list(f.history),()->f.graph().scan(),()->f.store.snapshot(f.anchor.original.id),()->f.anchor.original.bytes(),()->f.store.validate(f.anchor)))rejects(read);
                rejects(f::open);rejects(f.capacity::reconcile);
            }finally{if(permissions!=null)Files.setPosixFilePermissions(path,permissions);}
            cases++;
        }
        Fixture profile=new Fixture();profile.prepare(false);
        HistoryGraph.Graph alternate=new HistoryGraph(profile.store,profile.catalog,profile.identity,profile.index,new HistoryPolicy(128L*1024*1024,5)).scan();
        check(HistoryMaintenanceRecovery.unaffectedFingerprint(alternate,profile.plan.members).equals(profile.intent.unaffectedFingerprint),"unaffected domain excludes desired profile");cases++;
    }
    static void installedAuthority()throws Exception {
        for(String fault:Arrays.asList("marker","intent","policy","stage","unknown","anchor")){
            Fixture f=new Fixture();f.prepare(false);f.publish();f.open();
            if(fault.equals("marker"))Files.delete(f.parent.resolve(HistoryControlStore.ENROLLMENT_NAME));
            if(fault.equals("intent"))Files.write(f.intentPath(),new byte[]{0});
            if(fault.equals("stage"))Files.write(f.parent.resolve("history_control/prune.intent.stage"),HistoryPruneIntent.encode(f.intent));
            if(fault.equals("unknown"))Files.write(f.parent.resolve("store/unknown"),new byte[]{0});
            if(fault.equals("anchor"))Files.delete(f.parent.resolve("store/"+f.anchor.attempted.id+".snapshot"));
            if(fault.equals("policy")){HistoryControlStore.Inspection c=new HistoryControlStore(f.parent.toFile()).inspect();Files.write(f.parent.resolve("history_control/"+HistoryControlStore.policyFilename(1)),HistoryControlStore.encodePolicy(c.latest.bundleId,1,c.latest.envelopeHash,128L*1024*1024,20));}
            rejects(f.store::snapshots);rejects(f.catalog::all);rejects(()->f.index.list(f.history));cases++;
        }
        Fixture f=new Fixture();f.prepare(false);f.publish();f.open();
        f.disk.callbackDirectory="history_control";f.disk.callback=()->{check(f.store.snapshots().size()==2,"previous installed view remains available during retry");throw new IOException("retry sync failure");};
        rejects(f::open);check(f.store.snapshots().size()==2,"failed retry preserves installed capability");
        synchronized(HistoryStorage.monitorFor(f.store.storageRoot())){check(HistoryStorage.capacity(f.store.storageRoot())==f.capacity,"maintenance never replaced capacity authority");}
        f.remove(null);check(f.store.snapshots().size()==2&&f.store.inspect().reservedCompletionBytes==0,"installed view permits exact closure becoming absent");cases++;
    }
    static void unreadable()throws Exception {
        for(String target:Arrays.asList("member","anchor","intent","index-root")){
            Fixture f=new Fixture();f.prepare(false);f.publish();Path path=target.equals("member")?f.file(f.intent.members.get(0)):target.equals("anchor")?f.parent.resolve("store/"+f.anchor.original.id+".snapshot"):target.equals("intent")?f.intentPath():f.parent.resolve("index");
            Map<String,String> before=f.capture();Set<java.nio.file.attribute.PosixFilePermission> permissions=Files.getPosixFilePermissions(path);
            try{Files.setPosixFilePermissions(path,Collections.emptySet());rejects(f::open);}
            finally{Files.setPosixFilePermissions(path,permissions);}
            check(before.equals(f.capture()),"unreadable input retained: "+target);cases++;
        }
    }
    static byte[] replaceAscii(byte[] bytes,String old,String value)throws Exception {
        byte[] from=old.getBytes(java.nio.charset.StandardCharsets.UTF_8),to=value.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        check(from.length==to.length,"equal field byte length");byte[] result=bytes.clone();int offset=0,count=0;
        while((offset=IntentCodecCheck.indexOf(result,from,offset))>=0){System.arraycopy(to,0,result,offset,to.length);offset+=to.length;count++;}
        check(count>0,"field occurs in native proof");return result;
    }
    static void checksum(byte[] bytes)throws Exception {
        byte[] hash=java.security.MessageDigest.getInstance("SHA-256").digest(Arrays.copyOf(bytes,bytes.length-32));System.arraycopy(hash,0,bytes,bytes.length-32,32);
    }
    static void rebindMember(Fixture f,HistoryPruneIntent.Member member,byte[] changed)throws Exception {
        byte[] encoded=Files.readAllBytes(f.intentPath());String hash=HistoryStorage.hex(java.security.MessageDigest.getInstance("SHA-256").digest(changed));
        encoded=replaceAscii(encoded,member.envelopeHash,hash);
        int proofLength=java.nio.ByteBuffer.wrap(encoded,176,4).getInt();byte[] proofHash=java.security.MessageDigest.getInstance("SHA-256").digest(Arrays.copyOfRange(encoded,180,180+proofLength));
        System.arraycopy(proofHash,0,encoded,80,32);System.arraycopy(proofHash,0,encoded,112,32);checksum(encoded);
        check(HistoryPruneIntent.decode(encoded)!=null,"hostile exact-member proof still has valid codec framing");
        Files.write(f.file(member),changed);Files.write(f.intentPath(),encoded);
    }
    static void removedReferences()throws Exception {
        for(String kind:Arrays.asList("transaction","complete","checkpoint","index","snapshot")){
            Fixture f=new Fixture();f.prepare(true);f.publish();HistoryPruneIntent.Member member=null;for(HistoryPruneIntent.Member value:f.intent.members)if(value.kind.wire.equals(kind)){member=value;break;}
            check(member!=null,"removed native member");byte[] changed=Files.readAllBytes(f.file(member));
            if(kind.equals("snapshot"))changed[0]^=1; // full envelope hash cannot replace native framing validation
            else if(kind.equals("checkpoint"))changed=replaceAscii(changed,f.old.original.id,f.old.attempted.id); // alternate valid closure target disagrees with exact edge
            else changed=replaceAscii(changed,f.old.original.id,f.anchor.original.id);
            checksum(changed);rebindMember(f,member,changed);
            Map<String,String> before=f.capture();rejects(f::open);check(before.equals(f.capture()),"removed native framing/references validated: "+kind);cases++;
        }
    }
    static void bounds()throws Exception {
        Fixture large=new Fixture();large.prepare(30,0);check(large.intent.serializedBytes>16384&&large.intent.serializedBytes<=65536,"valid intent crosses policy record cap");
        large.publish();large.remove(null);verifySurvivors(large);cases++;
        Fixture full=new Fixture();full.prepare(0,253);full.publish();
        HistoryControlStore.Inspection control=new HistoryControlStore(full.parent.toFile()).inspect();
        check(control.actualEntries==256&&control.chainComplete&&control.usageComplete,"exact shared entry allowance includes marker");
        full.open();check(full.store.transactions().size()==1,"full valid allowance permits read-only recovery");
        Files.write(full.parent.resolve("history_control/"+HistoryControlStore.policyFilename(254)),HistoryControlStore.encodePolicy(control.latest.bundleId,254,control.latest.envelopeHash,64L*1024*1024,20));
        control=new HistoryControlStore(full.parent.toFile()).inspect();check(!control.usageComplete&&!control.chainComplete,"overflow shared entry allowance blocks proof");rejects(full::open);cases++;
        Fixture oversized=new Fixture();oversized.prepare(false);oversized.publish();
        try(RandomAccessFile file=new RandomAccessFile(oversized.intentPath().toFile(),"rw")){file.setLength(HistoryControlStore.MAX_BYTES);}
        control=new HistoryControlStore(oversized.parent.toFile()).inspect();check(control.actualBytes>HistoryControlStore.MAX_BYTES&&control.committedIntent.intent==null,"oversized intent charged under shared byte allowance");rejects(oversized::open);cases++;
        try(RandomAccessFile file=new RandomAccessFile(oversized.intentPath().toFile(),"rw")){file.setLength(8L*1024*1024*1024);}
        rejects(oversized::open);cases++;
    }
    public static void main(String[] args)throws Exception {
        if(args.length>0&&args[0].equals("memory")){
            byte[] retained=new byte[32*1024*1024];Arrays.fill(retained,(byte)7);
            Fixture f=new Fixture();f.nextOriginalSize=ProtectionStore.MAX_DOCUMENT_BYTES;f.prepare(false);f.publish();verifySurvivors(f);
            check(retained[retained.length-1]==7,"retained heap headroom");
            System.out.println("Maintenance streaming projection: 8MiB member / 128MiB heap / 32MiB retained headroom PASS");return;
        }
        discovery();if(args.length==0){projections();installedReplacement();failures();callbacks();uninstalledGate();installedAuthority();unreadable();removedReferences();bounds();}
        System.out.println("Maintenance recovery: "+cases+" cases / "+checks+" checks PASS");
    }
}
