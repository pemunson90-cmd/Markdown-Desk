package com.example.markdowndesk;

import java.io.*;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import java.util.concurrent.*;

/** Production native files; no parser/graph doubles and no content logging. */
public final class HistoryGraphCheck {
    static int checks;
    interface Task {void run()throws Exception;}
    static void check(boolean ok,String reason){checks++;if(!ok)throw new AssertionError(reason);}
    static void rejects(Task task)throws Exception {try{task.run();throw new AssertionError("Expected rejection");}catch(IOException expected){checks++;}}
    static final class Disk implements ProtectionStore.Durability {
        int storeSyncs; volatile CountDownLatch entered, release;Task onSync;String onSyncRoot;
        public void syncFile(FileDescriptor fd)throws IOException {fd.sync();}
        public void move(File a,File b)throws IOException {Files.move(a.toPath(),b.toPath(),StandardCopyOption.ATOMIC_MOVE);}
        public void syncDirectory(File root)throws IOException {
            if(root.getName().equals("store"))storeSyncs++;
            if(onSync!=null&&root.getName().equals(onSyncRoot)){Task action=onSync;onSync=null;try{action.run();}catch(Exception e){throw new IOException(e);}}
            CountDownLatch signal=entered, barrier=release;
            if(signal!=null&&root.getName().equals("store")){entered=null;signal.countDown();try{if(!barrier.await(10,TimeUnit.SECONDS))throw new IOException("Barrier timeout");}catch(InterruptedException e){throw new IOException(e);}}
            try(FileChannel c=FileChannel.open(root.toPath(),StandardOpenOption.READ)){c.force(true);}
        }
    }
    static final class Fixture {
        final Path parent=Files.createTempDirectory("history-graph-");final Disk disk=new Disk();
        final ProtectionStore store=new ProtectionStore(parent.resolve("store").toFile(),disk,64L*1024*1024);
        final HistoryCatalog catalog=new HistoryCatalog(parent.resolve("catalog").toFile(),store,disk);
        final HistoryIdentity identity=new HistoryIdentity(parent.resolve("identity").toFile(),disk);
        final HistoryIndex index=new HistoryIndex(store,catalog,identity,parent.resolve("index").toFile(),disk);
        final HistoryGraph graph=new HistoryGraph(store,catalog,identity,index,new HistoryPolicy(64L*1024*1024,20));
        Fixture()throws Exception {}
        ProtectionStore.Transaction group(boolean complete)throws Exception {return groupWith(store,complete);}
        ProtectionStore.Transaction groupWith(ProtectionStore source,boolean complete)throws Exception {
            ProtectionStore.Snapshot a=source.putSnapshot("tab","content://fixture","title",1,new byte[]{1,2,3},"original");
            ProtectionStore.Snapshot b=source.putSnapshot("tab","content://fixture","title",1,new byte[]{4,5},"attempted");
            ProtectionStore.Transaction t=source.prepare(a,b,a.hash);if(complete)source.complete(t);return t;
        }
    }
    static String hash(Path file)throws Exception {
        MessageDigest digest=MessageDigest.getInstance("SHA-256");try(InputStream in=Files.newInputStream(file)){byte[] bytes=new byte[8192];int n;while((n=in.read(bytes))!=-1)digest.update(bytes,0,n);}return HistoryStorage.hex(digest.digest());
    }
    static Map<Path,String> capture(Fixture f)throws Exception {
        Map<Path,String> result=new HashMap<>();for(String root:Arrays.asList("store","catalog","identity","index"))try(java.util.stream.Stream<Path> paths=Files.list(f.parent.resolve(root))){for(Path p:(Iterable<Path>)paths::iterator)if(Files.isRegularFile(p,LinkOption.NOFOLLOW_LINKS))result.put(p,hash(p));}return result;
    }
    static void regular()throws Exception {
        Fixture f=new Fixture();String id=f.identity.resolve("content://fixture","tab");
        ProtectionStore.Transaction done=f.group(true), pending=f.group(false);
        f.index.recordVerified(id,done.id);f.catalog.name(done.attempted.id,"first");f.catalog.name(done.attempted.id,"second");
        ProtectionStore.Snapshot orphan=f.store.putSnapshot("tab","content://fixture",null,2,new byte[0],"attempted");
        f.identity.forkAfterVerifiedSaveAs(id,"content://fork");
        Map<Path,String> before=capture(f);HistoryGraph.Graph g=f.graph.scan();
        check(g.complete&&!g.pruneBlocked,"complete known graph");check(g.records.size()==before.size(),"all actual files counted");
        long actual=0;for(ProtectionStore.StoredRecord r:g.records){Path file=f.parent.resolve(r.rootRole).resolve(r.filename);check(Files.size(file)==r.serializedBytes,"exact length");check(hash(file).equals(r.envelopeHash),"whole envelope digest");actual+=Files.size(file);}
        check(actual==g.actualBytes,"combined actual accounting");check(g.reservedCompletionSlots==1&&g.reservedCompletionBytes==Files.size(f.parent.resolve("store").resolve(pending.id+".transaction")),"native completion reservation");
        check(g.chargedBytes==actual+g.reservedCompletionBytes,"combined charge");check(g.orphanSnapshotIds.contains(orphan.id)&&g.pinnedSnapshotIds.contains(done.attempted.id),"orphans and names protected");
        check(g.transactions.size()==2&&g.ownerships.size()==1&&g.identities.size()==2,"native links and fork descriptors");
        check(g.fingerprint.equals(f.graph.scan().fingerprint),"deterministic scan");check(before.equals(capture(f)),"scan preserves exact v1 envelopes");
        try{g.records.clear();throw new AssertionError("Mutable graph");}catch(UnsupportedOperationException expected){checks++;}
        int syncs=f.disk.storeSyncs;f.catalog.all();check(f.disk.storeSyncs-syncs==1,"catalog validates unique sources once in operation");
        f.store.withValidatedSources(context->{check(context.validatedSnapshotCount()==5,"one validation per actual snapshot");Fixture other;try{other=new Fixture();}catch(Exception e){throw new IOException(e);}rejectIo(()->other.catalog.allAgainstValidatedSnapshots(context));return null;});
        final ProtectionStore.ValidatedSources[] expired=new ProtectionStore.ValidatedSources[1];
        f.store.withValidatedSources(context->{expired[0]=context;return null;});rejects(()->f.catalog.allAgainstValidatedSnapshots(expired[0]));
        rejects(()->f.store.withValidatedSources(context->{f.catalog.name(orphan.id,"mutation");rejectIo(()->f.catalog.allAgainstValidatedSnapshots(context));return null;}));
        f.catalog.name(orphan.id,"orphan");check(!g.fingerprint.equals(f.graph.scan().fingerprint),"metadata mutation changes fingerprint");
        check(!f.graph.scan().fingerprint.equals(new HistoryGraph(f.store,f.catalog,f.identity,f.index,new HistoryPolicy(128L*1024*1024,5)).scan().fingerprint),"policy descriptor changes fingerprint without native policy mutation");
        ProtectionStore second=new ProtectionStore(f.parent.resolve("store").toFile(),f.disk,1);check(HistoryStorage.monitorFor(f.parent.resolve("store").toFile())==HistoryStorage.monitorFor(f.parent.resolve("index").toFile()),"same parent monitor and legacy differing quota handles");
        Map<Path,String> quotaBefore=capture(f);rejects(()->second.putSnapshot("tab",null,null,1,new byte[]{9},"attempted"));check(quotaBefore.equals(capture(f)),"low-quota legacy handle refuses write without changing another handle quota or bytes");
        Fixture freshness=new Fixture();String freshnessId=freshness.identity.resolve("content://fixture","tab");ProtectionStore.Transaction freshGroup=freshness.group(true);freshness.index.recordVerified(freshnessId,freshGroup.id);
        freshness.disk.onSyncRoot="index";freshness.disk.onSync=()->freshness.store.putSnapshot("tab","content://fixture",null,4,new byte[0],"attempted");
        rejects(()->freshness.graph.scan());
        Fixture emptyCatalog=new Fixture();emptyCatalog.disk.onSyncRoot="catalog";emptyCatalog.disk.onSync=()->emptyCatalog.store.putSnapshot("tab",null,null,1,new byte[0],"attempted");
        rejects(()->emptyCatalog.catalog.all());
        Path duplicateParent=Files.createTempDirectory("history-role-alias-");ProtectionStore duplicateStore=new ProtectionStore(duplicateParent.resolve("same").toFile(),f.disk,100000);
        HistoryCatalog duplicateCatalog=new HistoryCatalog(duplicateParent.resolve("same").toFile(),duplicateStore,f.disk);
        HistoryIdentity duplicateIdentity=new HistoryIdentity(duplicateParent.resolve("identity").toFile(),f.disk);
        HistoryIndex duplicateIndex=new HistoryIndex(duplicateStore,duplicateCatalog,duplicateIdentity,duplicateParent.resolve("index").toFile(),f.disk);
        rejects(()->new HistoryGraph(duplicateStore,duplicateCatalog,duplicateIdentity,duplicateIndex,null));
        rejects(()->new HistoryGraph(second,f.catalog,f.identity,f.index,null));
        Path other=Files.createTempDirectory("history-other-");HistoryIdentity foreign=new HistoryIdentity(other.resolve("identity").toFile(),f.disk);rejects(()->new HistoryGraph(f.store,f.catalog,foreign,f.index,null));
        rejects(()->new HistoryCatalog(other.resolve("catalog").toFile(),f.store,f.disk));
        rejects(()->new HistoryIndex(f.store,f.catalog,foreign,f.parent.resolve("foreign-index").toFile(),f.disk));
        rejects(()->HistoryStorage.monitorFor(f.parent.resolve("nested/../store").toFile()));
        Path alias=f.parent.resolve("alias");Files.createSymbolicLink(alias,f.parent.resolve("store"));rejects(()->HistoryStorage.monitorFor(alias.toFile()));
        barrier(f,id);
        for(String root:Arrays.asList("store","catalog","identity","index")){
            Path stage=f.parent.resolve(root).resolve(UUID.randomUUID()+".stage");Files.write(stage,new byte[]{7});Map<Path,String> staged=capture(f);HistoryGraph.Graph blocked=f.graph.scan();check(blocked.pruneBlocked&&!blocked.complete,"stage blocks proof");check(blocked.actualBytes==growingSize(f),"every private stage charged");check(staged.equals(capture(f)),"stage never mutated");Files.delete(stage);
        }
        Path unknown=f.parent.resolve("store/unknown");try(RandomAccessFile out=new RandomAccessFile(unknown.toFile(),"rw")){out.setLength(100L*1024*1024);}HistoryGraph.Graph oversized=f.graph.scan();check(oversized.pruneBlocked&&oversized.actualBytes>=100L*1024*1024,"oversized raw usage without unbounded hash");check(oversized.records.stream().filter(r->r.filename.equals("unknown")).allMatch(r->r.envelopeHash==null),"oversized never hashed");Files.delete(unknown);
        Path link=f.parent.resolve("catalog/link");Files.createSymbolicLink(link,f.parent.resolve("store").resolve(orphan.id+".snapshot"));check(f.graph.scan().pruneBlocked,"symlink blocks proof");Files.delete(link);
        Path tx=f.parent.resolve("store").resolve(done.id+".transaction");byte[] saved=Files.readAllBytes(tx);Files.write(tx,new byte[]{1});Map<Path,String> corrupt=capture(f);check(f.graph.scan().pruneBlocked,"corrupt preparation blocks");check(corrupt.equals(capture(f)),"corrupt scan nonmutating");Files.write(tx,saved);
        Path missing=f.parent.resolve("store").resolve(done.original.id+".snapshot");byte[] source=Files.readAllBytes(missing);Files.delete(missing);check(f.graph.scan().pruneBlocked,"missing source blocks complete ownership");Files.write(missing,source);
        HistoryGraph.Graph limited=new HistoryGraph(f.store,f.catalog,f.identity,f.index,null,128).scan();
        check(limited.pruneBlocked&&limited.diagnostics.stream().anyMatch(d->d.contains("budget")),"metadata budget diagnostic fails closed");
        Path hugeMetadata=f.parent.resolve("store").resolve(UUID.randomUUID()+".transaction");
        try(RandomAccessFile out=new RandomAccessFile(hugeMetadata.toFile(),"rw")){out.setLength(ProtectionStore.MAX_DOCUMENT_BYTES);}
        HistoryGraph.Graph metadataOversized=f.graph.scan();check(metadataOversized.pruneBlocked,"oversized native metadata blocks");
        check(metadataOversized.records.stream().filter(r->r.filename.equals(hugeMetadata.getFileName().toString())).allMatch(r->r.envelopeHash==null),"oversized native metadata never hashed or materialized");Files.delete(hugeMetadata);
    }
    static long growingSize(Fixture f)throws Exception {long n=0;for(Path p:capture(f).keySet())n+=Files.size(p);return n;}
    interface Io {void run()throws IOException;}
    static void rejectIo(Io task)throws IOException {try{task.run();throw new AssertionError("Foreign cache accepted");}catch(IOException expected){checks++;}}
    static void barrier(Fixture f,String id)throws Exception {
        ProtectionStore.Transaction awaitingIndex=f.group(true);
        String namedSource=awaitingIndex.attempted.id;
        HistoryGraph.Graph before=f.graph.scan();
        ProtectionStore writerStore=new ProtectionStore(f.parent.resolve("store").toFile(),f.disk,64L*1024*1024);
        ExecutorService pool=Executors.newFixedThreadPool(4);
        CountDownLatch begun=new CountDownLatch(1),started=new CountDownLatch(3);f.disk.entered=begun;f.disk.release=new CountDownLatch(1);
        try{Future<?> scan=pool.submit(()->{try{f.graph.scan();}catch(Exception e){throw new RuntimeException(e);}});check(begun.await(5,TimeUnit.SECONDS),"scan reached real directory sync");
            Future<?> names=pool.submit(()->{started.countDown();try{f.catalog.name(namedSource,"concurrent");}catch(Exception e){throw new RuntimeException(e);}});
            Future<?> index=pool.submit(()->{started.countDown();try{f.index.recordVerified(id,awaitingIndex.id);}catch(Exception e){throw new RuntimeException(e);}});
            Future<?> save=pool.submit(()->{started.countDown();try{f.groupWith(writerStore,true);}catch(Exception e){throw new RuntimeException(e);}});
            check(started.await(5,TimeUnit.SECONDS),"all native writer contenders started");
            check(!names.isDone()&&!index.isDone()&&!save.isDone(),"native naming/index/save serialize behind shared parent monitor");
            f.disk.release.countDown();scan.get(10,TimeUnit.SECONDS);names.get(10,TimeUnit.SECONDS);index.get(10,TimeUnit.SECONDS);save.get(10,TimeUnit.SECONDS);
            HistoryGraph.Graph after=f.graph.scan();check(after.complete&&after.transactions.size()==before.transactions.size()+1&&after.checkpoints.size()==before.checkpoints.size()+1&&after.ownerships.size()==before.ownerships.size()+1,"all real concurrent writes visible without deadlock");
        }finally{f.disk.release.countDown();pool.shutdownNow();}
    }
    static void memory()throws Exception {
        Fixture f=new Fixture();
        // Disk-generated native near-limit fixture; native writer is deliberately outside graph heap probe.
        byte[] payload=new byte[ProtectionStore.MAX_DOCUMENT_BYTES-1];f.store.putSnapshot("d".repeat(4096),"content://large",null,1,payload,"attempted");payload=null;
        for(int i=0;i<8;i++)f.catalog.name(f.store.snapshots().get(0).id,"name"+i);
        byte[] headroom=new byte[32*1024*1024];HistoryGraph.Graph g=f.graph.scan();check(g.complete&&g.snapshots.size()==1,"128MiB heap with 32MiB retained headroom and near-limit disk source");check(headroom.length==32*1024*1024,"headroom retained");
        int syncs=f.disk.storeSyncs;f.catalog.all();check(f.disk.storeSyncs-syncs==1,"duplicate large-source names reuse one actual inventory validation");
        Path catalogRoot=f.parent.resolve("catalog"),template;
        try(java.util.stream.Stream<Path> paths=Files.list(catalogRoot)){template=paths.findFirst().get();}
        byte[] nativeRecord=Files.readAllBytes(template);
        for(int i=0;i<512;i++){
            String id=UUID.randomUUID().toString();byte[] record=nativeRecord.clone();
            System.arraycopy(id.getBytes(java.nio.charset.StandardCharsets.UTF_8),0,record,16,36);
            byte[] checksum=MessageDigest.getInstance("SHA-256").digest(Arrays.copyOf(record,record.length-32));
            System.arraycopy(checksum,0,record,record.length-32,32);Files.write(catalogRoot.resolve(id+".checkpoint"),record);
        }
        HistoryGraph.Graph bounded=f.graph.scan();check(!bounded.complete&&bounded.pruneBlocked&&bounded.diagnostics.stream().anyMatch(d->d.contains("budget")),"default16MiB graph metadata budget diagnostic on real native files");
        check(bounded.usageComplete&&bounded.actualBytes>g.actualBytes,"budget exhaustion still reports complete actual regular-file usage");
        check(headroom[0]==0,"32MiB headroom remains live through metadata exhaustion");
    }
    public static void main(String[] args)throws Exception {if(args.length>0)memory();else regular();System.out.println("PASS retention graph production fixtures: "+checks+" checks; read-only host evidence only");}
}
