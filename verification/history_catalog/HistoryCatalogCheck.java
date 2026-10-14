package com.example.markdowndesk;

import java.io.*;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;

/** Behavioral checks against the production store/catalog and actual host files. */
public final class HistoryCatalogCheck {
    static int assertions, cases;
    static final byte[] EXACT="\ufeffOriginal\r\n雪🧭\r\n".getBytes(StandardCharsets.UTF_8);
    interface Io {void run() throws Exception;}
    static void require(boolean value,String message) {assertions++;if(!value)throw new AssertionError(message);}
    static void rejects(Io action,String message) throws Exception {
        try {action.run();throw new AssertionError("Accepted "+message);} catch(IOException expected){assertions++;}
    }
    static byte[] hash(byte[] bytes)throws Exception {return MessageDigest.getInstance("SHA-256").digest(bytes);}
    static final class Disk implements ProtectionStore.Durability {
        String fail; boolean armed; boolean child; Io onFileSync, onMove; final List<String> calls=new ArrayList<>();
        void before(String operation)throws IOException {
            if(armed){calls.add(operation);if(child)boundary("before-"+operation);if(operation.equals(fail))throw new IOException("Injected "+operation);}
        }
        void after(String operation)throws IOException {if(armed&&child)boundary("after-"+operation);}
        void hook(Io action)throws IOException {if(armed&&action!=null)try{action.run();}catch(Exception failure){throw new IOException(failure);}}
        public void syncFile(FileDescriptor fd)throws IOException {before("file-sync");fd.sync();hook(onFileSync);after("file-sync");}
        public void move(File a,File b)throws IOException {before("move");Files.move(a.toPath(),b.toPath(),StandardCopyOption.ATOMIC_MOVE);hook(onMove);after("move");}
        public void syncDirectory(File directory)throws IOException {
            // Source snapshot validation also syncs its own directory; target only catalog commits.
            boolean target=directory.getName().equals("history_catalog");
            if(target)before("directory-sync");
            try(FileChannel channel=FileChannel.open(directory.toPath(),StandardOpenOption.READ)){channel.force(true);}
            if(target)after("directory-sync");
        }
    }
    static final class Fixture {
        final Path parent, protection, catalog; final Disk disk=new Disk();
        final ProtectionStore store; final HistoryCatalog history; final ProtectionStore.Snapshot snapshot;
        Fixture()throws Exception {this(Files.createTempDirectory("catalog-check-"));}
        Fixture(Path parent)throws Exception {
            this.parent=parent;protection=parent.resolve("protection");catalog=parent.resolve("history_catalog");
            store=new ProtectionStore(protection.toFile(),disk,ProtectionStore.DEFAULT_QUOTA_BYTES);
            snapshot=store.putSnapshot("doc", "content://synthetic", "Synthetic.md",7,EXACT,"original");
            history=new HistoryCatalog(catalog.toFile(),store,disk);
        }
        Path source(){return protection.resolve(snapshot.id+".snapshot");}
        HistoryCatalog reopen()throws Exception {
            return new HistoryCatalog(catalog.toFile(),new ProtectionStore(protection.toFile(),new Disk(),ProtectionStore.DEFAULT_QUOTA_BYTES),new Disk());
        }
        void preserved(byte[] original)throws Exception {
            require(Arrays.equals(original,Files.readAllBytes(source())),"source envelope byte-identical");
            require(Arrays.equals(hash(original),hash(Files.readAllBytes(source()))),"source file SHA-256 unchanged");
            require(Arrays.equals(EXACT,new ProtectionStore(protection.toFile(),new Disk(),ProtectionStore.DEFAULT_QUOTA_BYTES).snapshot(snapshot.id).bytes()),"exact independently reopened source bytes");
        }
    }
    static void test(String name,Io action)throws Exception {action.run();cases++;System.out.println("PASS "+name);}
    static List<Path> files(Path root)throws IOException {try(java.util.stream.Stream<Path>s=Files.list(root)){return s.sorted().collect(java.util.stream.Collectors.toList());}}
    static void rewriteChecksum(Path path,byte[] raw)throws Exception {
        System.arraycopy(hash(Arrays.copyOf(raw,raw.length-32)),0,raw,raw.length-32,32);Files.write(path,raw);
    }
    static void regularTests()throws Exception {
        test("nameAndReopenPreserveExactSnapshot",()->{
            Fixture f=new Fixture();byte[] source=Files.readAllBytes(f.source());f.disk.armed=true;
            HistoryCatalog.Checkpoint cp=f.history.name(f.snapshot.id,"  exact name 雪  ");
            require(f.disk.calls.equals(Arrays.asList("file-sync","move","directory-sync")),"durability ordering");
            require(UUID.fromString(cp.id).toString().equals(cp.id),"generated canonical UUID");
            HistoryCatalog.Checkpoint read=f.reopen().list("doc").get(0);
            require(read.id.equals(cp.id)&&read.snapshotId.equals(f.snapshot.id)&&read.documentId.equals("doc")&&read.snapshotHash.equals(f.snapshot.hash)&&read.label.equals("  exact name 雪  ")&&read.createdAt==cp.createdAt,"exact immutable metadata");
            require(f.reopen().pinnedSnapshotIds().equals(Collections.singleton(f.snapshot.id)),"complete pin set");
            f.preserved(source);
            try{f.history.list("doc").clear();throw new AssertionError("mutable list");}catch(UnsupportedOperationException expected){assertions++;}
            try{f.history.pinnedSnapshotIds().clear();throw new AssertionError("mutable pins");}catch(UnsupportedOperationException expected){assertions++;}
        });
        test("sameLabelDifferentVersionsRemainDistinct",()->{
            Fixture f=new Fixture();ProtectionStore.Snapshot second=f.store.putSnapshot("doc",null,null,8,"new\n".getBytes(StandardCharsets.UTF_8),"attempted");
            HistoryCatalog.Checkpoint a=f.history.name(f.snapshot.id,"release"),b=f.history.name(second.id,"release");
            require(!a.id.equals(b.id)&&f.reopen().list("doc").size()==2,"append-only duplicate labels");
            require(f.history.list("other").isEmpty(),"document filtering");
            require(f.history.pinnedSnapshotIds().size()==2,"both versions pinned");
        });
        test("utf8LabelByteLimit",()->{
            Fixture f=new Fixture();f.history.name(f.snapshot.id,"é".repeat(128));
            for(String value:new String[]{"é".repeat(129),"a".repeat(257)," \t\n","\u2003", "\ud800"})rejects(()->f.history.name(f.snapshot.id,value),"invalid label");
            rejects(()->f.history.name(f.snapshot.id,null),"null label");
            require(files(f.catalog).size()==1,"invalid input creates no stage");
            rejects(()->f.history.name("../escape","label"),"non-UUID source ID");
            rejects(()->f.history.name(UUID.randomUUID().toString(),"label"),"absent source");
            rejects(()->f.history.list("x".repeat(4097)),"oversized document ID");
        });
        test("corruptRecordRejectsPins",()->{
            Fixture f=new Fixture();f.history.name(f.snapshot.id,"label");Path record=files(f.catalog).get(0);byte[] raw=Files.readAllBytes(record);
            raw[raw.length-1]^=1;Files.write(record,raw);rejects(()->f.reopen().pinnedSnapshotIds(),"checksum corruption");
            rejects(()->f.reopen().list("unrelated"),"corruption cannot hide behind document filter");
        });
        test("strictFramingAndSourceLinks",()->{
            for(int mode=0;mode<7;mode++){
                Fixture f=new Fixture();HistoryCatalog.Checkpoint cp=f.history.name(f.snapshot.id,"label");Path record=f.catalog.resolve(cp.id+".checkpoint");byte[] raw=Files.readAllBytes(record);
                // Wire: magic/version/bodyLength then ID, snapshotID, documentID, hash, label, time.
                if(mode==0)raw[7]=2; // unknown version
                if(mode==1)raw[8]=(byte)0x7f; // forged body length
                if(mode==2)raw[12]=(byte)0x7f; // forged first string length
                int sourceOffset=12+4+36+4;
                if(mode==3)raw[sourceOffset]=(byte)(raw[sourceOffset]=='a'?'b':'a');
                int documentOffset=sourceOffset+36+4;
                if(mode==4)raw[documentOffset]='x'; // document differs from validated source
                if(mode==5){int offset=documentOffset+3+4;raw[offset]=(byte)(raw[offset]=='0'?'1':'0');} // valid-looking hash mismatch
                if(mode==6)raw[documentOffset]=(byte)0xc0; // malformed UTF-8
                rewriteChecksum(record,raw);rejects(()->f.reopen().pinnedSnapshotIds(),"framing or link mismatch "+mode);
            }
        });
        test("missingOrChangedSourceRejectsPins",()->{
            for(boolean remove:new boolean[]{true,false}){
                Fixture f=new Fixture();f.history.name(f.snapshot.id,"label");
                if(remove)Files.delete(f.source());else{byte[] raw=Files.readAllBytes(f.source());raw[raw.length-1]^=1;Files.write(f.source(),raw);}
                rejects(()->f.reopen().pinnedSnapshotIds(),"dangling/corrupt source");
            }
        });
        test("symlinkAndNonregularRejected",()->{
            Fixture f=new Fixture();Path alias=f.parent.resolve("alias");Files.createSymbolicLink(alias,f.catalog);
            rejects(()->new HistoryCatalog(alias.toFile(),f.store,new Disk()),"symlink root");
            Path parentAlias=f.parent.resolve("parent-alias");Files.createSymbolicLink(parentAlias,f.parent);
            rejects(()->new HistoryCatalog(parentAlias.resolve("history_catalog").toFile(),f.store,new Disk()),"symlink ancestor");
            Files.createSymbolicLink(f.catalog.resolve(UUID.randomUUID()+".checkpoint"),f.source());
            rejects(()->f.history.pinnedSnapshotIds(),"symlink entry");
            Fixture g=new Fixture();Files.createDirectory(g.catalog.resolve(UUID.randomUUID()+".checkpoint"));rejects(()->g.history.pinnedSnapshotIds(),"directory entry");
        });
        test("fileSyncMoveDirectorySyncFailuresKeepSource",()->{
            for(String failure:new String[]{"file-sync","move","directory-sync"}){
                Fixture f=new Fixture();byte[] before=Files.readAllBytes(f.source());f.disk.armed=true;f.disk.fail=failure;
                rejects(()->f.history.name(f.snapshot.id,"label"),failure);f.disk.armed=false;f.preserved(before);
                require(files(f.catalog).size()==1,"failed metadata retained");
                if(failure.equals("directory-sync"))require(f.reopen().pinnedSnapshotIds().contains(f.snapshot.id),"committed metadata revalidated after failed sync");
                else rejects(()->f.reopen().pinnedSnapshotIds(),"incomplete stage blocks pruning");
            }
        });
        test("sourceRevalidatedBeforePublication",()->{
            Fixture f=new Fixture();byte[] before=Files.readAllBytes(f.source());
            f.disk.onFileSync=()->{byte[] raw=Files.readAllBytes(f.source());raw[raw.length-1]^=1;Files.write(f.source(),raw);};
            f.disk.armed=true;rejects(()->f.history.name(f.snapshot.id,"label"),"changed source before publication");
            require(files(f.catalog).size()==1&&files(f.catalog).get(0).toString().endsWith(".stage"),"changed source prevents publication");
            require(!f.disk.calls.contains("move"),"changed source does not reach move");
            // Restore the fixture's external corruption; catalog itself never alters source bytes.
            Files.write(f.source(),before);f.disk.armed=false;f.preserved(before);
        });
        test("committedReadbackFailureKeepsSource",()->{
            Fixture f=new Fixture();byte[] before=Files.readAllBytes(f.source());
            f.disk.onMove=()->{Path path=files(f.catalog).get(0);byte[] raw=Files.readAllBytes(path);raw[raw.length-1]^=1;Files.write(path,raw);};
            f.disk.armed=true;rejects(()->f.history.name(f.snapshot.id,"label"),"adapter changed committed bytes");
            f.disk.armed=false;f.preserved(before);require(files(f.catalog).size()==1,"invalid committed bytes retained");
            rejects(()->f.reopen().pinnedSnapshotIds(),"readback corruption visible on restart");
        });
        test("sameRootHandlesSerializeNames",()->{
            Fixture f=new Fixture();HistoryCatalog second=f.reopen();
            java.util.concurrent.ExecutorService pool=java.util.concurrent.Executors.newFixedThreadPool(2);
            try{
                List<java.util.concurrent.Future<HistoryCatalog.Checkpoint>> pending=new ArrayList<>();
                for(int i=0;i<24;i++){HistoryCatalog h=i%2==0?f.history:second;pending.add(pool.submit(()->h.name(f.snapshot.id,"same")));}
                Set<String> ids=new HashSet<>();for(java.util.concurrent.Future<HistoryCatalog.Checkpoint> item:pending)ids.add(item.get().id);
                require(ids.size()==24&&f.reopen().list("doc").size()==24,"concurrent handles append distinct committed entries");
                require(f.history.pinnedSnapshotIds().equals(Collections.singleton(f.snapshot.id)),"concurrent complete pin enumeration");
            }finally{pool.shutdownNow();}
        });
        test("recordAndQuotaCapsFailClosed",()->{
            Fixture f=new Fixture();Path stage=f.catalog.resolve(UUID.randomUUID()+".stage");Files.write(stage,new byte[16385]);
            rejects(()->f.history.pinnedSnapshotIds(),"record cap");rejects(()->f.history.name(f.snapshot.id,"name"),"oversized stage");
            Fixture byteCap=new Fixture();for(int i=0;i<512;i++)Files.write(byteCap.catalog.resolve(UUID.randomUUID()+".stage"),new byte[16384]);
            rejects(()->byteCap.history.name(byteCap.snapshot.id,"label"),"8MiB incl stages exhausted");
            require(files(byteCap.catalog).size()==512,"byte cap creates no new stage");
            Files.write(byteCap.catalog.resolve(UUID.randomUUID()+".stage"),new byte[]{1});rejects(()->byteCap.reopen(),"over byte cap constructor");
            Fixture entries=new Fixture();for(int i=0;i<4096;i++)Files.createFile(entries.catalog.resolve(UUID.randomUUID()+".stage"));
            rejects(()->entries.history.name(entries.snapshot.id,"label"),"entry cap incl stages");require(files(entries.catalog).size()==4096,"entry cap creates no new stage");
            Files.createFile(entries.catalog.resolve(UUID.randomUUID()+".stage"));rejects(()->entries.reopen(),"over entry cap constructor");
        });
        test("committedEntryCapacityBoundary",()->{
            Fixture f=new Fixture();HistoryCatalog.Checkpoint seed=f.history.name(f.snapshot.id,"label");byte[] raw=Files.readAllBytes(f.catalog.resolve(seed.id+".checkpoint"));
            for(int i=1;i<4095;i++){
                String id=UUID.randomUUID().toString();byte[] copy=raw.clone();System.arraycopy(id.getBytes(StandardCharsets.US_ASCII),0,copy,16,36);
                rewriteChecksum(f.catalog.resolve(id+".checkpoint"),copy);
            }
            f.history.name(f.snapshot.id,"last slot");require(files(f.catalog).size()==4096,"last committed entry accepted");
            rejects(()->f.history.name(f.snapshot.id,"one too many"),"committed entry capacity");
            require(files(f.catalog).size()==4096,"committed capacity creates no stage");
        });
        test("unknownAndTruncatedRecordsFailClosed",()->{
            Fixture f=new Fixture();Files.write(f.catalog.resolve("unknown"),new byte[]{1});rejects(()->f.history.pinnedSnapshotIds(),"unknown metadata");
            Fixture g=new Fixture();g.history.name(g.snapshot.id,"label");Path record=files(g.catalog).get(0);Files.write(record,new byte[]{1,2,3});rejects(()->g.history.pinnedSnapshotIds(),"truncated metadata");
        });
        test("v1ProtectionFilesByteIdentical",()->{
            Fixture f=new Fixture();ProtectionStore.Snapshot attempted=f.store.putSnapshot("doc","content://synthetic","Synthetic.md",7,"edit".getBytes(StandardCharsets.UTF_8),"attempted");
            ProtectionStore.Transaction transaction=f.store.prepare(f.snapshot,attempted,f.snapshot.hash);f.store.complete(transaction);
            Map<Path,byte[]> before=new HashMap<>();for(Path p:files(f.protection))before.put(p,Files.readAllBytes(p));
            f.history.name(f.snapshot.id,"old");f.history.name(attempted.id,"new");f.reopen().pinnedSnapshotIds();
            require(before.size()==files(f.protection).size(),"no private source entries changed");
            for(Map.Entry<Path,byte[]> e:before.entrySet())require(Arrays.equals(e.getValue(),Files.readAllBytes(e.getKey())),"v1 snapshot/preparation/completion byte-identical");
        });
        System.out.println("PASS "+cases+" production catalog cases / "+assertions+" assertions");
    }
    static final BufferedReader CONTROL=new BufferedReader(new InputStreamReader(System.in,StandardCharsets.UTF_8));
    static void boundary(String name)throws IOException {
        System.out.println(name);System.out.flush();if(!"continue".equals(CONTROL.readLine()))throw new IOException("No continuation");
    }
    public static void main(String[] args)throws Exception {
        if(args.length==0){regularTests();return;}
        Path root=Paths.get(args[1]);
        if(args[0].equals("child")){
            Fixture f=new Fixture(root);Files.write(root.resolve("source-envelope.bin"),Files.readAllBytes(f.source()));
            Files.write(root.resolve("snapshot-id.txt"),f.snapshot.id.getBytes(StandardCharsets.US_ASCII));
            ProtectionStore.Snapshot attempted=f.store.putSnapshot("doc","content://synthetic","Synthetic.md",7,"\ufeffattempted\r\n🧭".getBytes(StandardCharsets.UTF_8),"attempted");
            Files.write(root.resolve("attempted-envelope.bin"),Files.readAllBytes(f.protection.resolve(attempted.id+".snapshot")));
            Files.write(root.resolve("attempted-id.txt"),attempted.id.getBytes(StandardCharsets.US_ASCII));
            f.disk.child=true;f.disk.armed=true;f.history.name(f.snapshot.id,"restart checkpoint");throw new AssertionError("Expected parent SIGKILL");
        }
        String id=Files.readString(root.resolve("snapshot-id.txt"),StandardCharsets.US_ASCII);
        ProtectionStore store=new ProtectionStore(root.resolve("protection").toFile(),new Disk(),ProtectionStore.DEFAULT_QUOTA_BYTES);
        Path source=root.resolve("protection").resolve(id+".snapshot");
        require(Arrays.equals(Files.readAllBytes(root.resolve("source-envelope.bin")),Files.readAllBytes(source)),"SIGKILL source exact envelope/hash survives");
        require(Arrays.equals(EXACT,store.snapshot(id).bytes()),"SIGKILL original bytes validated independently");
        String attemptedId=Files.readString(root.resolve("attempted-id.txt"),StandardCharsets.US_ASCII);
        require(Arrays.equals(Files.readAllBytes(root.resolve("attempted-envelope.bin")),Files.readAllBytes(root.resolve("protection").resolve(attemptedId+".snapshot"))),"SIGKILL attempted envelope/hash survives");
        require(Arrays.equals("\ufeffattempted\r\n🧭".getBytes(StandardCharsets.UTF_8),store.snapshot(attemptedId).bytes()),"SIGKILL attempted bytes validated independently");
        require(store.snapshots().size()==2,"SIGKILL retains every source snapshot");
        List<Path> before=files(root.resolve("history_catalog"));Map<Path,byte[]> contents=new HashMap<>();for(Path p:before)contents.put(p,Files.readAllBytes(p));
        HistoryCatalog catalog=new HistoryCatalog(root.resolve("history_catalog").toFile(),store,new Disk());
        boolean committed=args[2].equals("after-move")||args[2].contains("directory-sync");
        if(committed){require(catalog.list("doc").size()==1,"committed checkpoint validates");require(catalog.pinnedSnapshotIds().equals(Collections.singleton(id)),"committed source pin");}
        else rejects(()->catalog.pinnedSnapshotIds(),"uncommitted stage explicitly blocks pins");
        require(before.equals(files(root.resolve("history_catalog"))),"reopen retains all metadata/stages");
        for(Map.Entry<Path,byte[]> e:contents.entrySet())require(Arrays.equals(e.getValue(),Files.readAllBytes(e.getKey())),"reopen does not rewrite metadata");
        System.out.println("PASS restart "+args[2]);
    }
}
