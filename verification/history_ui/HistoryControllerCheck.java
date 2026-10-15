package com.example.markdowndesk;

import java.io.*;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import com.example.markdowndesk.HistoryController.*;

/** Functional controller calls over actual native files, provider bytes and shared capacity. */
public final class HistoryControllerCheck {
    static int checks;
    interface Task {void run()throws Exception;}
    static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
    static void rejects(Task work,String why)throws Exception{try{work.run();throw new AssertionError("Accepted "+why);}catch(IOException expected){checks++;}}
    static byte[] bytes(String value){return value.getBytes(StandardCharsets.UTF_8);}
    static final class Disk implements ProtectionStore.Durability {
        String failRoot,failPoint;boolean moved;Task syncCallback;
        volatile CountDownLatch entered,release;
        public void syncFile(FileDescriptor fd)throws IOException{fd.sync();}
        public void move(File from,File to)throws IOException {
            if(to.getParentFile().getName().equals(failRoot)&&"move".equals(failPoint))throw new IOException("injected optional move");
            Files.move(from.toPath(),to.toPath(),StandardCopyOption.ATOMIC_MOVE);
            if(to.getParentFile().getName().equals(failRoot))moved=true;
        }
        public void syncDirectory(File dir)throws IOException {
            if(dir.getName().equals(failRoot)&&moved&&"directory".equals(failPoint))throw new IOException("injected optional directory sync");
            if(dir.getName().equals("protected_saves")){
                if(syncCallback!=null){Task callback=syncCallback;syncCallback=null;try{callback.run();}catch(Exception e){throw new IOException(e);}}
                CountDownLatch signal=entered,gate=release;
                if(signal!=null){entered=null;signal.countDown();try{if(!gate.await(10,TimeUnit.SECONDS))throw new IOException("latch timeout");}catch(InterruptedException e){throw new IOException(e);}}
            }
            try(FileChannel c=FileChannel.open(dir.toPath(),StandardOpenOption.READ)){c.force(true);}
        }
    }
    static final class Fixture implements AutoCloseable {
        boolean keep;final Path parent;final Disk disk=new Disk();final ProtectionStore store;final HistoryController controller;
        Fixture()throws Exception{this(Files.createTempDirectory("history-controller-"));}
        Fixture(Path parent)throws Exception {
            this.parent=parent;store=new ProtectionStore(parent.resolve("protected_saves").toFile(),disk,ProtectionStore.DEFAULT_QUOTA_BYTES);
            controller=HistoryController.open(store,disk);
        }
        HistoryIdentity identity()throws IOException{return new HistoryIdentity(parent.resolve("history_identity").toFile(),disk);}
        CapturedRevision capture(String tab,String uri,String text,long revision)throws IOException {
            return new CapturedRevision(tab,controller.resolveDocument(uri,tab),uri,"fixture.md",revision,bytes(text));
        }
        ProtectionStore.Transaction group(String tab,String uri,long revision,boolean completed)throws IOException {
            ProtectionStore.Snapshot a=store.putSnapshot(tab,uri,"fixture.md",revision,bytes("old\r\n"),"original");
            ProtectionStore.Snapshot b=store.putSnapshot(tab,uri,"fixture.md",revision,bytes("new\r\n"),"attempted");
            ProtectionStore.Transaction tx=store.prepare(a,b,a.hash);if(completed)store.complete(tx);return tx;
        }
        public void close()throws IOException{if(keep)return;try(java.util.stream.Stream<Path> paths=Files.walk(parent)){for(Path p:(Iterable<Path>)paths.sorted(Comparator.reverseOrder())::iterator)Files.delete(p);}}
    }
    static void identitiesAndNames()throws Exception {
        try(Fixture f=new Fixture()) {
            String uri="content://fixture/%41",tab="tab-a";
            String history=f.controller.resolveDocument(uri,tab);
            check(history.equals(f.controller.resolveDocument(uri,"tab-reopened")),"URI reopen ignores new tab ID");
            check(!history.equals(f.controller.resolveDocument("content://fixture/A",tab)),"exact URI aliases remain distinct");
            check(!f.controller.resolveDocument(null,tab).equals(f.controller.resolveDocument(null,"tab-b")),"unsaved tabs isolated");
            byte[] edit=bytes("\ufeffone\r\n 雪 \n");CapturedRevision capture=new CapturedRevision(tab,history,uri,"fixture.md",12,edit);
            edit[0]=0;NameResult named=f.controller.nameCapturedRevision(capture,"first");
            check(named.named()&&named.checkpoint!=null&&named.diagnostic.isEmpty(),"captured checkpoint named");
            ProtectionStore.Snapshot snapshot=f.store.snapshot(named.snapshotId);
            check(Arrays.equals(snapshot.bytes(),bytes("\ufeffone\r\n 雪 \n"))&&snapshot.revision==12&&snapshot.documentId.equals(tab),"defensive captured strict bytes/revision");
            List<HistoryIndex.Version> versions=f.controller.list(history);
            check(versions.size()==1&&versions.get(0).names.get(0).label.equals("first")&&versions.get(0).status==HistoryIndex.Status.ORPHAN,"named capture is protected orphan, not manufactured save");
            try{versions.clear();throw new AssertionError("mutable list");}catch(UnsupportedOperationException expected){checks++;}
            f.store.putSnapshot("legacy-recovery",uri,"fixture.md",15,bytes("legacy"),"attempted");
            check(f.controller.list(history).size()==1&&f.store.snapshots().size()==2,"legacy recovery stays in global store only");
            int count=f.store.snapshots().size();
            for(String label:new String[]{"", "\u2003", "x".repeat(257), "雪".repeat(86), "\ud800"})rejects(()->f.controller.nameCapturedRevision(capture,label),"invalid label before snapshot");
            check(f.store.snapshots().size()==count,"invalid labels create no snapshot");
            check(f.controller.nameCapturedRevision(capture,"x".repeat(256)).named(),"exact label byte limit");
            CapturedRevision foreign=new CapturedRevision(tab,history,"content://elsewhere","fixture.md",13,bytes("foreign"));
            rejects(()->f.controller.nameCapturedRevision(foreign,"wrong owner"),"captured alias mismatch");
            rejects(()->new CapturedRevision(tab,history,uri,"fixture.md",1,new byte[]{(byte)0xc0,(byte)0x80}),"strict UTF8");
            rejects(()->new CapturedRevision(tab,history,uri,"fixture.md",-1,new byte[0]),"negative revision");
            rejects(()->new CapturedRevision(tab,history,uri,"fixture.md",1,new byte[8388609]),"document cap including BOM");
            rejects(()->new CapturedRevision("legacy-recovery",history,uri,"fixture.md",1,new byte[0]),"legacy naming forbidden");
            rejects(()->new CapturedRevision(tab,"not-an-id",uri,"fixture.md",1,new byte[0]),"invalid history ID");
            rejects(()->new CapturedRevision("",history,uri,"fixture.md",1,new byte[0]),"missing tab identity");
            rejects(()->new CapturedRevision(tab,history,uri,"x".repeat(4097),1,new byte[0]),"metadata byte cap");
            rejects(()->new CapturedRevision(tab,history,uri,"\ud800",1,new byte[0]),"metadata strict encoding");
            rejects(()->new CapturedRevision(tab,history,uri,"fixture.md",1,null),"missing captured bytes");
            rejects(()->f.controller.nameCapturedRevision(capture,null),"missing checkpoint label");
            rejects(()->f.controller.nameCapturedRevision(null,"label"),"missing captured revision");
            try(Fixture reopened=new Fixture(f.parent)) {
                reopened.keep=true;
                check(reopened.controller.resolveDocument(uri,"fresh-tab").equals(history)&&reopened.controller.list(history).size()==2,"reopened controller reads durable identity/names");
            }
        }
    }
    static void namingFailures()throws Exception {
        for(String point:new String[]{"move","directory"})try(Fixture f=new Fixture()) {
            CapturedRevision capture=f.capture("tab",null,"captured\r\n",1);
            f.disk.failRoot="history_catalog";f.disk.failPoint=point;
            NameResult result=f.controller.nameCapturedRevision(capture,"label");
            check(!result.named()&&result.checkpoint==null&&!result.diagnostic.isEmpty(),"optional label outcome reported separately");
            f.disk.failRoot=null;
            check(Arrays.equals(f.store.snapshot(result.snapshotId).bytes(),bytes("captured\r\n")),"snapshot retained after optional naming failure");
            check(f.store.transactions().isEmpty(),"naming did not manufacture completed save");
            Provider provider=new Provider("original");SaveCoordinator.Result saved=save(f,provider,"tab","content://after-name-failure",2,false);
            check(f.store.validate(saved.transaction).verified,"failed optional naming does not block subsequent mandatory save");
        }
        try(Fixture f=new Fixture()) {
            for(int i=0;i<12;i++) {
                CapturedRevision capture=f.capture("tab-"+i,null,"same",i);
                f.controller.nameCapturedRevision(capture,"name-"+i);
                check(f.controller.list(capture.historyId).size()==1,"12 unsaved histories stay isolated");
            }
            byte[] limit=new byte[8388608];Arrays.fill(limit,(byte)'x');limit[0]=(byte)0xef;limit[1]=(byte)0xbb;limit[2]=(byte)0xbf;
            String history=f.controller.resolveDocument(null,"large");
            NameResult exact=f.controller.nameCapturedRevision(new CapturedRevision("large",history,null,"large",99,limit),"exact limit");
            check(f.store.snapshot(exact.snapshotId).byteCount==8388608,"exact byte limit including BOM named");
        }
    }
    static final class Provider implements SaveCoordinator.Provider {
        byte[] value;int writes;boolean fail;
        Provider(String value){this.value=bytes(value);}
        public byte[] read(){return value.clone();}
        public void write(byte[] bytes)throws IOException {writes++;value=bytes.clone();if(fail)throw new IOException("injected provider write failure");}
    }
    static SaveCoordinator.Result save(Fixture f,Provider provider,String tab,String uri,long revision,boolean fresh)throws IOException {
        return new SaveCoordinator(f.controller.protectionStore()).save(new SaveCoordinator.Request(tab,uri,"fixture.md",revision,
                bytes("submitted\r\n雪"),EditorTools.sha256(provider.value),null,fresh),provider);
    }
    static void verifiedRoutes()throws Exception {
        try(Fixture f=new Fixture()) {
            Provider provider=new Provider("old");String uri="content://saved";
            SaveCoordinator.Result saved=save(f,provider,"tab",uri,5,false);
            check(!saved.transaction.verified,"returned coordinator transaction is not trusted as completion flag");
            IndexResult result=f.controller.recordVerifiedSave(new SaveRoute("tab",uri,uri,5),saved.transaction.id);
            check(result.indexed&&result.historyId!=null&&result.diagnostic.isEmpty(),"fresh actual completion indexed");
            check(f.controller.list(result.historyId).stream().allMatch(v->v.status==HistoryIndex.Status.INDEXED_VERIFIED),"actual completion owns both versions");
            check(f.controller.recordVerifiedSave(new SaveRoute("tab",uri,uri,5),saved.transaction.id).indexed,"index retry idempotent");
            check(f.identity().descriptors().get(0).origins.isEmpty(),"ordinary same-URI save never forks");
            String destination="content://existing",destinationId=f.controller.resolveDocument(destination,"other-tab");
            Provider dest=new Provider("destination old");SaveCoordinator.Result copy=save(f,dest,"tab",destination,6,true);
            IndexResult fork=f.controller.recordVerifiedSave(new SaveRoute("tab",uri,destination,6),copy.transaction.id);
            check(fork.indexed&&fork.historyId.equals(destinationId),"Save As retains existing destination identity");
            check(f.identity().lookup(destinationId).origins.contains(result.historyId),"Save As captured old URI creates explicit origin");
            check(f.controller.list(result.historyId).size()==2&&f.controller.list(destinationId).size()==2,"Save As does not merge histories");
            Provider unsavedProvider=new Provider("");SaveCoordinator.Result first=save(f,unsavedProvider,"unsaved","content://first",7,true);
            IndexResult firstResult=f.controller.recordVerifiedSave(new SaveRoute("unsaved",null,"content://first",7),first.transaction.id);
            check(firstResult.indexed&&f.identity().lookup(firstResult.historyId).origins.contains(f.controller.resolveDocument(null,"unsaved")),"first save keeps unsaved TAB origin");
            int aliases=f.identity().descriptors().size();
            for(SaveRoute wrong:new SaveRoute[]{new SaveRoute("foreign",uri,uri,5),new SaveRoute("tab",uri,"content://wrong",5),new SaveRoute("tab",uri,uri,4)}) {
                IndexResult invalid=f.controller.recordVerifiedSave(wrong,saved.transaction.id);
                check(!invalid.indexed&&!invalid.diagnostic.contains("remains verified"),"invalid route does not claim verified outcome");
            }
            check(f.identity().descriptors().size()==aliases,"route mismatch cannot create aliases");
            ProtectionStore.Transaction pending=f.group("pending","content://pending",8,false);
            check(!f.controller.recordVerifiedSave(new SaveRoute("pending",null,"content://pending",8),pending.id).indexed,"uncompleted transaction rejected");
            check(f.identity().descriptors().size()==aliases,"uncompleted transaction cannot fork");
            Provider broken=new Provider("");broken.fail=true;SaveCoordinator.SaveFailure failure;
            try{save(f,broken,"failed","content://failed",9,true);throw new AssertionError("provider failure accepted");}catch(SaveCoordinator.SaveFailure expected){failure=expected;checks++;}
            check(failure.writeMayHaveOccurred&&failure.transaction!=null,"failed provider preserves transaction");
            check(!f.controller.recordVerifiedSave(new SaveRoute("failed",null,"content://failed",9),failure.transaction.id).indexed,"failed provider cannot be indexed as verified");
            check(f.identity().descriptors().size()==aliases,"failed save creates no history provenance");
        }
        for(String root:new String[]{"history_identity","history_index"})try(Fixture f=new Fixture()) {
            Provider provider=new Provider("original");SaveCoordinator.Result saved=save(f,provider,"tab","content://optional",1,false);
            f.disk.failRoot=root;f.disk.failPoint="move";
            IndexResult result=f.controller.recordVerifiedSave(new SaveRoute("tab","content://optional","content://optional",1),saved.transaction.id);
            check(!result.indexed&&!result.diagnostic.isEmpty(),"optional publication failure is separate result");
            f.disk.failRoot=null;
            check(Arrays.equals(provider.value,bytes("submitted\r\n雪"))&&f.store.validate(saved.transaction).verified,"optional error does not negate saved bytes/completion");
        }
    }
    static void comparisons()throws Exception {
        try(Fixture f=new Fixture()) {
            String uri="content://compare",history=f.controller.resolveDocument(uri,"tab");
            NameResult a=f.controller.nameCapturedRevision(f.capture("tab",uri,"\ufeffold\r\n",1),"a");
            NameResult b=f.controller.nameCapturedRevision(f.capture("tab",uri,"new\n",2),"b");
            HistoryDiff.Preview preview=f.controller.preview(history,a.snapshotId,b.snapshotId);
            check(preview.complete&&preview.leftBom&&!preview.rightBom&&preview.lines.stream().anyMatch(l->l.ending==HistoryDiff.Ending.CRLF),"actual bounded comparison preserves BOM/endings");
            try{preview.lines.clear();throw new AssertionError("mutable preview");}catch(UnsupportedOperationException expected){checks++;}
            NameResult foreign=f.controller.nameCapturedRevision(f.capture("other","content://other","foreign",1),"foreign");
            rejects(()->f.controller.preview(history,a.snapshotId,foreign.snapshotId),"foreign history comparison");
            ProtectionStore.Snapshot legacy=f.store.putSnapshot("legacy-recovery",uri,"legacy",1,bytes("legacy"),"attempted");
            rejects(()->f.controller.preview(history,a.snapshotId,legacy.id),"legacy recovery membership");
            NameResult large=f.controller.nameCapturedRevision(f.capture("tab",uri,"x".repeat(262145),3),"large");
            check(!f.controller.preview(history,a.snapshotId,large.snapshotId).complete,"controller preserves bounded summary for large input");
            for(String[] test:new String[][]{{"\n".repeat(10001),"", "logical line"},{"a\n".repeat(2001),"a\n".repeat(2001),"output"},{"a\n".repeat(1100),"b\n".repeat(1100),"work"}}) {
                NameResult left=f.controller.nameCapturedRevision(f.capture("tab",uri,test[0],10),"bounded left");
                NameResult right=f.controller.nameCapturedRevision(f.capture("tab",uri,test[1],11),"bounded right");
                HistoryDiff.Preview bounded=f.controller.preview(history,left.snapshotId,right.snapshotId);
                check(!bounded.complete&&bounded.reason.contains(test[2])&&bounded.lines.size()<=2000,"controller preserves actual "+test[2]+" bound");
            }
            CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);f.disk.entered=entered;f.disk.release=release;
            ExecutorService workers=Executors.newFixedThreadPool(2);
            try {
                Thread[] readerThread={null},writerThread={null};
                Future<HistoryDiff.Preview> compare=workers.submit(()->{readerThread[0]=Thread.currentThread();return f.controller.preview(history,a.snapshotId,b.snapshotId);});
                check(entered.await(5,TimeUnit.SECONDS),"actual validation sync callback reached");
                CountDownLatch attempting=new CountDownLatch(1);Future<?> mutation=workers.submit(()->{writerThread[0]=Thread.currentThread();attempting.countDown();f.store.putSnapshot("other",null,"late",1,new byte[0],"attempted");return null;});
                check(attempting.await(5,TimeUnit.SECONDS),"second native writer attempting");
                java.lang.management.ThreadMXBean threads=java.lang.management.ManagementFactory.getThreadMXBean();
                java.lang.management.ThreadInfo waiting=null;long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);
                while(System.nanoTime()<deadline){waiting=threads.getThreadInfo(writerThread[0].getId());if(waiting!=null&&waiting.getThreadState()==Thread.State.BLOCKED)break;Thread.yield();}
                check(waiting!=null&&waiting.getThreadState()==Thread.State.BLOCKED&&waiting.getLockOwnerId()==readerThread[0].getId(),"native writer actually blocked on validation thread's monitor");
                check(!mutation.isDone(),"native mutation has not completed during held validation");
                release.countDown();check(compare.get(5,TimeUnit.SECONDS).complete,"comparison completed under controller scope");mutation.get(5,TimeUnit.SECONDS);checks++;
            }finally{release.countDown();workers.shutdownNow();}
            f.disk.syncCallback=()->f.store.putSnapshot("reentrant",null,"late",1,new byte[0],"attempted");
            rejects(()->f.controller.preview(history,a.snapshotId,b.snapshotId),"actual validation callback reentrant mutation invalidates generation");
            check(f.controller.preview(history,a.snapshotId,b.snapshotId).complete,"fresh request after generation change works");
            Path source=f.parent.resolve("protected_saves").resolve(a.snapshotId+".snapshot");byte[] original=Files.readAllBytes(source);original[original.length-1]^=1;Files.write(source,original);
            rejects(()->f.controller.preview(history,a.snapshotId,b.snapshotId),"changed stored source never returns cached diff");
        }
    }
    static void startup()throws Exception {
        try(Fixture f=new Fixture()) {
            HistoryControlStore.Inspection control=new HistoryControlStore(f.parent.toFile()).inspect();
            check(control.state==HistoryControlStore.State.VALIDATED&&control.registration.roles.get(0).basename.equals("protected_saves"),"initialized exact shared role registration");
            Process child=new ProcessBuilder(System.getProperty("java.home")+"/bin/java","-Xmx128m","-cp",System.getProperty("java.class.path"),HistoryControllerCheck.class.getName(),"reopen",f.parent.toString()).inheritIO().start();
            try{check(child.waitFor(15,TimeUnit.SECONDS)&&child.exitValue()==0,"fresh-process explicit startup before mandatory write");}
            finally{if(child.isAlive())child.destroyForcibly();}
        }
        for(String defect:new String[]{"missing-role","enrollment","policy-stage","maintenance-stage","pin-stage"})try(Fixture f=new Fixture()) {
            ProtectionStore.Snapshot retained=f.store.putSnapshot("retained",null,"original",1,bytes("recoverable"),"attempted");
            Path retainedPath=f.parent.resolve("protected_saves").resolve(retained.id+".snapshot");byte[] retainedEnvelope=Files.readAllBytes(retainedPath);
            Path parent=f.parent,control=parent.resolve("history_control");
            if(defect.equals("missing-role"))Files.delete(parent.resolve("history_identity"));
            else if(defect.equals("enrollment")){Path p=parent.resolve("history_enrollment.control");byte[] b=Files.readAllBytes(p);b[b.length-1]^=1;Files.write(p,b);}
            else if(defect.equals("policy-stage"))Files.write(control.resolve("policy-0000000000000001.control.stage"),new byte[]{1});
            else if(defect.equals("maintenance-stage"))Files.write(control.resolve(HistoryPruneIntent.STAGE_NAME),new byte[]{1});
            else Files.write(control.resolve("pin-"+UUID.randomUUID()+".begin.stage"),new byte[]{1});
            rejects(()->HistoryController.open(f.store,f.disk),"uncertain startup "+defect);
            rejects(()->f.store.putSnapshot("blocked",null,"title",1,new byte[0],"attempted"),"no legacy write fallback "+defect);
            Provider blocked=new Provider("unchanged");rejects(()->save(f,blocked,"blocked","content://blocked",2,false),"uncertain authority prevents provider mutation "+defect);
            check(blocked.writes==0&&Arrays.equals(blocked.value,bytes("unchanged"))&&Arrays.equals(retainedEnvelope,Files.readAllBytes(retainedPath)),"uncertain startup preserves provider and retained envelope "+defect);
            if(defect.equals("missing-role"))check(!Files.exists(parent.resolve("history_identity")),"controlled constructor did not recreate missing role");
            if(defect.equals("enrollment")||defect.equals("pin-stage")) {
                Process child=new ProcessBuilder(System.getProperty("java.home")+"/bin/java","-Xmx128m","-cp",System.getProperty("java.class.path"),HistoryControllerCheck.class.getName(),"blocked",f.parent.toString()).inheritIO().start();
                try{check(child.waitFor(15,TimeUnit.SECONDS)&&child.exitValue()==0,"fresh-process startup uncertainty remains blocked "+defect);}
                finally{if(child.isAlive())child.destroyForcibly();}
            }
        }
        for(String root:new String[]{"history_catalog","history_identity","history_index"})try(Fixture f=new Fixture()) {
            String extension=root.equals("history_catalog")?"checkpoint":root.equals("history_identity")?"identity":"index";
            Files.write(f.parent.resolve(root).resolve(UUID.randomUUID()+"."+extension),new byte[]{1});
            HistoryController reopened=HistoryController.open(f.store,f.disk);
            Provider provider=new Provider("old");SaveCoordinator.Result saved=new SaveCoordinator(reopened.protectionStore()).save(new SaveCoordinator.Request("tab","content://optional-corrupt","title",1,bytes("saved"),EditorTools.sha256(provider.value),null,false),provider);
            check(f.store.validate(saved.transaction).verified&&Arrays.equals(provider.value,bytes("saved")),"optional semantic corruption does not fail mandatory save "+root);
            check(!reopened.recordVerifiedSave(new SaveRoute("tab","content://optional-corrupt","content://optional-corrupt",1),saved.transaction.id).indexed,"optional corruption remains visible "+root);
        }
    }
    static void retainedPolicy()throws Exception {
        try(Fixture f=new Fixture()) {
            HistoryStorage.capacity(f.store.storageRoot()).setPolicy(new HistoryPolicy(128L<<20,0));
            HistoryController reopened=HistoryController.open(f.store,f.disk);
            check(HistoryStorage.capacity(f.store.storageRoot()).policy().quotaBytes==(128L<<20)&&HistoryStorage.capacity(f.store.storageRoot()).policy().automaticCount==0,"reopen preserves acknowledged policy instead of default reset");
            Provider provider=new Provider("old");SaveCoordinator.Result result=save(f,provider,"tab","content://off",1,false);
            check(f.store.validate(result.transaction).verified,"automatic retention OFF never disables mandatory protection");
            check(reopened.recordVerifiedSave(new SaveRoute("tab","content://off","content://off",1),result.transaction.id).indexed,"verified save ownership remains separate from automatic retention policy");
        }
    }
    static void blockedChild(Path parent)throws Exception {
        Disk disk=new Disk();ProtectionStore raw=new ProtectionStore(parent.resolve("protected_saves").toFile(),disk,ProtectionStore.DEFAULT_QUOTA_BYTES);
        rejects(()->HistoryController.open(raw,disk),"fresh-process uncertain startup rejected");
        rejects(()->raw.putSnapshot("blocked",null,"title",1,new byte[0],"attempted"),"fresh-process uncertainty grants no legacy fallback");
        System.out.println("History controller blocked startup: "+checks+" checks PASS");
    }
    static void reopenChild(Path parent)throws Exception {
        Disk disk=new Disk();ProtectionStore raw=new ProtectionStore(parent.resolve("protected_saves").toFile(),disk,ProtectionStore.DEFAULT_QUOTA_BYTES);
        rejects(()->raw.putSnapshot("early",null,"title",1,new byte[0],"attempted"),"fresh process cannot write before explicit open");
        HistoryController controller=HistoryController.open(raw,disk);
        check(controller.protectionStore().putSnapshot("ready",null,"title",2,new byte[0],"attempted")!=null,"explicit startup grants actual ready native writes");
        System.out.println("History controller fresh startup: "+checks+" checks PASS");
    }
    public static void main(String[] args)throws Exception{
        if(args.length>0){if(args[0].equals("blocked"))blockedChild(Paths.get(args[1]));else reopenChild(Paths.get(args[1]));return;}
        identitiesAndNames();namingFailures();verifiedRoutes();comparisons();startup();retainedPolicy();
        System.out.println("History controller: "+checks+" checks PASS");
    }

}
