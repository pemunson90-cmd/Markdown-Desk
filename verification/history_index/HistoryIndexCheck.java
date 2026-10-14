package com.example.markdowndesk;

import java.io.*;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Actual production store, coordinator, catalog and identity/index disk records. */
public final class HistoryIndexCheck {
    static int assertions, cases;
    interface Io {void run() throws Exception;}
    static void require(boolean value,String message){assertions++;if(!value)throw new AssertionError(message);}
    static void rejects(Io action,String message)throws Exception {
        try{action.run();throw new AssertionError("Accepted "+message);}catch(IOException expected){assertions++;}
    }
    static void immutable(Io action)throws Exception {
        try{action.run();throw new AssertionError("Mutable collection");}catch(UnsupportedOperationException expected){assertions++;}
    }
    static void test(String name,Io action)throws Exception {action.run();cases++;System.out.println("PASS "+name);}
    static final class Disk implements ProtectionStore.Durability {
        String failure, target="history_index"; boolean armed, commitMoved, child; Io afterMove, onCompletionMove;
        void fault(String point)throws IOException {if(armed&&child)boundary(point);if(armed&&point.equals(failure))throw new IOException("Injected "+point);}
        public void syncFile(FileDescriptor fd)throws IOException {fault("before-file-sync");fd.sync();fault("after-file-sync");}
        public void move(File a,File b)throws IOException {fault("before-move");Files.move(a.toPath(),b.toPath(),StandardCopyOption.ATOMIC_MOVE);if(armed&&b.getParentFile().getName().equals(target))commitMoved=true;if(armed&&afterMove!=null)try{afterMove.run();}catch(Exception e){throw new IOException(e);}if(b.getName().endsWith(".complete")&&onCompletionMove!=null)try{onCompletionMove.run();}catch(Exception e){throw new IOException(e);}fault("after-move");}
        public void syncDirectory(File d)throws IOException {
            boolean selected=d.getName().equals(target)&&commitMoved;if(selected)fault("before-directory-sync");
            try(FileChannel c=FileChannel.open(d.toPath(),StandardOpenOption.READ)){c.force(true);}if(selected)fault("after-directory-sync");
        }
    }
    static final class Fixture {
        final Path parent, source, aliases, indexRoot; final Disk disk=new Disk();
        final ProtectionStore store; final HistoryCatalog catalog; final HistoryIdentity identity; final HistoryIndex index;
        Fixture()throws Exception {this(Files.createTempDirectory("history-index-"));}
        Fixture(Path parent)throws Exception {
            this.parent=parent;source=parent.resolve("protection");aliases=parent.resolve("history_identity");indexRoot=parent.resolve("history_index");
            store=new ProtectionStore(source.toFile(),disk,ProtectionStore.DEFAULT_QUOTA_BYTES);
            catalog=new HistoryCatalog(parent.resolve("history_catalog").toFile(),store,disk);
            identity=new HistoryIdentity(aliases.toFile(),disk);
            index=new HistoryIndex(store,catalog,identity,indexRoot.toFile(),disk);
        }
        ProtectionStore.Transaction group(String doc,String uri,long revision,boolean verified)throws Exception {
            ProtectionStore.Snapshot original=store.putSnapshot(doc,uri,"file",revision,"\ufefforiginal\r\n雪".getBytes(StandardCharsets.UTF_8),"original");
            ProtectionStore.Snapshot edit=store.putSnapshot(doc,uri,"file",revision,"edit\r\n".getBytes(StandardCharsets.UTF_8),"attempted");
            ProtectionStore.Transaction tx=store.prepare(original,edit,original.hash);if(verified)store.complete(tx);return tx;
        }
    }
    static List<Path> files(Path p)throws IOException {try(java.util.stream.Stream<Path> s=Files.list(p)){return s.sorted().collect(java.util.stream.Collectors.toList());}}
    static Map<Path,byte[]> capture(Path p)throws IOException {Map<Path,byte[]> m=new HashMap<>();for(Path f:files(p))m.put(f,Files.readAllBytes(f));return m;}
    static void unchanged(Map<Path,byte[]> before,Path root)throws Exception {
        require(before.size()==files(root).size(),"source entry count unchanged");
        for(Map.Entry<Path,byte[]> e:before.entrySet())require(Arrays.equals(e.getValue(),Files.readAllBytes(e.getKey())),"exact source envelope unchanged");
    }
    static void regular()throws Exception {
        test("exactURIReopenNamespacesAndEqualText",()->{
            Fixture f=new Fixture();String a=f.identity.resolve("content://p/%41","tab"), b=f.identity.resolve("content://p/A","tab");
            require(!a.equals(b),"exact spelling keeps distinct URIs");
            require(a.equals(new Fixture(f.parent).identity.resolve("content://p/%41","different-tab")),"reopen exact URI");
            String tab=f.identity.resolve(null,"content://p/%41");require(!tab.equals(a),"tab namespace separated");
            require(tab.equals(f.identity.resolve("","content://p/%41")),"null and empty unsaved URI");
            require(!tab.equals(f.identity.resolve(null,"other-tab")),"unsaved tabs separate");
            f.group("same-doc","content://p/%41",1,true);f.group("same-doc","content://p/A",1,true);
            require(f.index.list(a).size()==2&&f.index.list(b).size()==2,"equal bytes different URI histories separate");
            immutable(()->f.identity.descriptors().clear());immutable(()->f.index.list(a).clear());
            rejects(()->f.identity.resolve(null,null),"no unsaved tab identity");
        });
        test("validCompletionsRemainUnindexedAndPendingOrphansVisible",()->{
            Fixture f=new Fixture();String id=f.identity.resolve("content://x","tab");
            ProtectionStore.Transaction done=f.group("legacy-tab","content://x",1,true), pending=f.group("legacy-tab","content://x",2,false);
            ProtectionStore.Snapshot orphan=f.store.putSnapshot("legacy-tab","content://x",null,3,new byte[0],"attempted");
            List<HistoryIndex.Version> versions=f.index.list(id);require(versions.size()==5,"all groups and orphan visible");
            require(versions.stream().filter(v->v.status==HistoryIndex.Status.UNINDEXED_VERIFIED).count()==2,"completion authoritative without optional record");
            require(versions.stream().filter(v->v.status==HistoryIndex.Status.PENDING).count()==2,"pending pair retained");
            require(versions.stream().filter(v->v.status==HistoryIndex.Status.ORPHAN&&v.snapshotId.equals(orphan.id)).count()==1,"orphan retained");
            require(versions.stream().allMatch(v->v.protectedFromPruning),"unindexed/pending/orphan protected");
            rejects(()->f.index.recordVerified(id,pending.id),"unverified source");
            f.catalog.name(done.attempted.id,"release");
            HistoryIndex.Version named=f.index.list(id).stream().filter(v->v.snapshotId.equals(done.attempted.id)).findFirst().get();
            require(named.names.size()==1&&named.names.get(0).label.equals("release"),"catalog names attached by exact snapshot");
            immutable(()->named.names.clear());
            f.index.recordVerified(id,done.id);require(f.index.list(id).stream().filter(v->v.status==HistoryIndex.Status.INDEXED_VERIFIED).count()==2,"indexed validated pair");
        });
        test("twelveUnsavedTabHistoriesKeepOrphansIndependent",()->{
            Fixture f=new Fixture();List<String> ids=new ArrayList<>();List<String> snapshots=new ArrayList<>();
            for(int i=0;i<12;i++){String tab="tab-"+i;ids.add(f.identity.resolve(null,tab));
                snapshots.add(f.store.putSnapshot(tab,i%2==0?null:"",null,i,"same bytes".getBytes(StandardCharsets.UTF_8),"attempted").id);}
            require(new HashSet<>(ids).size()==12,"twelve tab identities remain independent");Map<Path,byte[]> before=capture(f.source);
            for(int i=0;i<12;i++){List<HistoryIndex.Version> versions=f.index.list(ids.get(i));
                require(versions.size()==1&&versions.get(0).snapshotId.equals(snapshots.get(i))&&versions.get(0).status==HistoryIndex.Status.ORPHAN,"each URI-empty tab sees only its own orphan");}
            unchanged(before,f.source);
        });
        test("catalogAllAcrossDocumentsImmutableAndFailClosed",()->{
            Fixture f=new Fixture();String a=f.identity.resolve("content://a","tab"),b=f.identity.resolve("content://b","tab");
            ProtectionStore.Transaction one=f.group("one","content://a",1,true),two=f.group("two","content://b",1,true);
            f.catalog.name(one.attempted.id,"first");f.catalog.name(two.attempted.id,"second");
            require(f.catalog.all().size()==2,"fresh all API returns both documents");immutable(()->f.catalog.all().clear());
            require(f.index.list(a).stream().filter(v->!v.names.isEmpty()).count()==1&&f.index.list(b).stream().filter(v->!v.names.isEmpty()).count()==1,"one catalog scan attaches exact document names");
            Files.write(f.parent.resolve("history_catalog").resolve(UUID.randomUUID()+".stage"),new byte[]{1});
            rejects(()->f.catalog.all(),"catalog stage blocks full all API");rejects(()->f.index.list(a),"catalog stage fails full index graph visibly");
        });
        test("idempotentMonotonicOwnershipAndUnsavedAlias",()->{
            Fixture f=new Fixture();String id=f.identity.resolve(null,"tab");
            ProtectionStore.Transaction tx=f.group("tab",null,1,true);Map<Path,byte[]> before=capture(f.source);
            f.index.recordVerified(id,tx.id);long first=f.index.list(id).get(0).sequence;
            f.index.recordVerified(id,tx.id);require(files(f.indexRoot).size()==1,"idempotent group record");
            Fixture fresh=new Fixture(f.parent);ProtectionStore.Transaction next=fresh.group("tab","",2,true);
            fresh.index.recordVerified(id,next.id);List<HistoryIndex.Version> versions=fresh.index.list(id);
            require(versions.get(0).sequence==first&&versions.get(2).sequence==first+1,"persisted monotonic sequence");
            String other=f.identity.resolve("content://different","tab");rejects(()->f.index.recordVerified(other,tx.id),"wrong URI history");
            require(f.index.list(other).isEmpty(),"URI histories never infer tab alias");
            for(Map.Entry<Path,byte[]> e:before.entrySet())require(Arrays.equals(e.getValue(),Files.readAllBytes(e.getKey())),"v1 original files unchanged");
        });
    }
    static void rewrite(Path path,byte[] bytes)throws Exception {
        byte[] digest=java.security.MessageDigest.getInstance("SHA-256").digest(Arrays.copyOf(bytes,bytes.length-32));
        System.arraycopy(digest,0,bytes,bytes.length-32,32);Files.write(path,bytes);
    }
    static void corruptionTests()throws Exception {
        test("identityChecksumsVersionsFramingUnknownAndStagesFailClosed",()->{
            for(int mode=0;mode<6;mode++){
                Fixture f=new Fixture();String id=f.identity.resolve("content://x","tab");Path record=files(f.aliases).get(0);byte[] bytes=Files.readAllBytes(record);
                if(mode==0){bytes[bytes.length-1]^=1;Files.write(record,bytes);}
                if(mode==1){bytes[7]=2;rewrite(record,bytes);}
                if(mode==2){bytes[12]=127;rewrite(record,bytes);}
                if(mode==3){int alias=12+40+40+7+4;bytes[alias]=(byte)0xc0;rewrite(record,bytes);}
                if(mode==4)Files.write(f.aliases.resolve("unknown"),new byte[]{1});
                if(mode==5)Files.write(f.aliases.resolve(UUID.randomUUID()+".stage"),new byte[]{1});
                rejects(()->f.identity.lookup(id),"identity full enumeration "+mode);
                rejects(()->f.identity.resolve("content://other","tab"),"no partial resolution "+mode);
                rejects(()->new Fixture(f.parent),"invalid identity process reopen "+mode);
            }
        });
        test("identityConflictingAliasesAndDanglingProvenance",()->{
            Fixture f=new Fixture();String id=f.identity.resolve("content://x","tab");Path record=files(f.aliases).get(0);byte[] copy=Files.readAllBytes(record);
            String duplicate=UUID.randomUUID().toString();byte[] newId=duplicate.getBytes(StandardCharsets.UTF_8);
            System.arraycopy(newId,0,copy,16,36);System.arraycopy(newId,0,copy,56,36);rewrite(f.aliases.resolve(duplicate+".identity"),copy);
            rejects(()->f.identity.lookup(id),"two generated IDs bound to exact URI");
            Fixture g=new Fixture();String old=g.identity.resolve(null,"tab"),fork=g.identity.forkAfterVerifiedSaveAs(old,"content://dest");
            Files.delete(g.aliases.resolve(old+".identity"));rejects(()->g.identity.lookup(fork),"dangling old identity");
            Fixture h=new Fixture();String original=h.identity.resolve(null,"tab"),destination=h.identity.resolve("content://dest","tab");
            h.identity.forkAfterVerifiedSaveAs(original,"content://dest");Files.delete(h.aliases.resolve(destination+".identity"));
            rejects(()->h.identity.descriptors(),"dangling existing destination edge");
        });
        test("forgedUnsavedProvenanceAndFreshGroupMetadataMismatch",()->{
            Fixture f=new Fixture();String old=f.identity.resolve(null,"tab"),fork=f.identity.forkAfterVerifiedSaveAs(old,"content://dest");
            Path path=f.aliases.resolve(fork+".identity");byte[] bytes=Files.readAllBytes(path);
            System.arraycopy("TAB".getBytes(StandardCharsets.UTF_8),0,bytes,96,3);rewrite(path,bytes);
            rejects(()->f.identity.lookup(fork),"TAB binding cannot carry Save As provenance");
            for(int field=0;field<8;field++){
                Fixture g=new Fixture();String id=g.identity.resolve("content://x","tab");ProtectionStore.Transaction tx=g.group("tab","content://x",1,true);g.index.recordVerified(id,tx.id);
                Path record=files(g.indexRoot).get(0);byte[] raw=Files.readAllBytes(record);java.nio.ByteBuffer buf=java.nio.ByteBuffer.wrap(raw);
                int offset=140;int[] strings=new int[6];for(int i=0;i<6;i++){
                    if(i==2||i==3)offset++;int len=buf.getInt(offset);offset+=4;strings[i]=offset;offset+=len;
                }
                if(field<6)raw[strings[field]]=(byte)(raw[strings[field]]=='0'?'1':'0');
                if(field==6)buf.putLong(offset,tx.original.revision+1);
                if(field==7)buf.putInt(offset+16,tx.original.byteCount+1);
                rewrite(record,raw);rejects(()->g.index.list(id),"index source field differs "+field);
            }
        });
        test("indexCorruptionAndDanglingLinksFailBeforeFiltering",()->{
            for(int mode=0;mode<9;mode++){
                Fixture f=new Fixture();String id=f.identity.resolve("content://x","tab"), other=f.identity.resolve("content://other","tab");
                ProtectionStore.Transaction tx=f.group("tab","content://x",1,true);f.index.recordVerified(id,tx.id);
                Path path=files(f.indexRoot).get(0);byte[] bytes=Files.readAllBytes(path);
                if(mode==0){bytes[bytes.length-1]^=1;Files.write(path,bytes);}
                if(mode==1){bytes[7]=2;rewrite(path,bytes);}
                if(mode==2){System.arraycopy(UUID.randomUUID().toString().getBytes(StandardCharsets.UTF_8),0,bytes,96,36);rewrite(path,bytes);}
                if(mode==3){System.arraycopy(other.getBytes(StandardCharsets.UTF_8),0,bytes,56,36);rewrite(path,bytes);}
                if(mode==4){Files.delete(f.source.resolve(tx.id+".complete"));}
                if(mode==5){byte[] raw=Files.readAllBytes(f.source.resolve(tx.id+".complete"));raw[raw.length-1]^=1;Files.write(f.source.resolve(tx.id+".complete"),raw);}
                if(mode==6){Files.delete(f.source.resolve(tx.attempted.id+".snapshot"));}
                if(mode==7){Files.write(f.indexRoot.resolve(UUID.randomUUID()+".stage"),new byte[]{1});}
                if(mode==8){Files.write(f.indexRoot.resolve("unknown"),new byte[]{1});}
                rejects(()->f.index.list(other),"corruption outside requested history "+mode);
                rejects(()->f.index.recordVerified(id,tx.id),"corrupt index cannot return idempotent success "+mode);
                rejects(()->new Fixture(f.parent),"corruption fails reopened constructor "+mode);
            }
        });
        test("duplicateSequenceOwnershipAndOverflow",()->{
            for(boolean duplicateSequence:new boolean[]{false,true}){
                Fixture f=new Fixture();String id=f.identity.resolve("content://x","tab");ProtectionStore.Transaction a=f.group("tab","content://x",1,true);
                f.index.recordVerified(id,a.id);Path one=files(f.indexRoot).get(0);
                if(duplicateSequence){ProtectionStore.Transaction b=f.group("tab","content://x",2,true);f.index.recordVerified(id,b.id);
                    Path two=files(f.indexRoot).stream().filter(p->!p.equals(one)).findFirst().get();byte[] bytes=Files.readAllBytes(two);
                    java.nio.ByteBuffer.wrap(bytes).putLong(132,1);rewrite(two,bytes);
                }else{String newId=UUID.randomUUID().toString();byte[] bytes=Files.readAllBytes(one);
                    System.arraycopy(newId.getBytes(StandardCharsets.UTF_8),0,bytes,16,36);rewrite(f.indexRoot.resolve(newId+".index"),bytes);}
                rejects(()->f.index.list(id),"duplicate sequence/group conflict");
            }
            Fixture f=new Fixture();String id=f.identity.resolve("content://x","tab");ProtectionStore.Transaction tx=f.group("tab","content://x",1,true);
            f.index.recordVerified(id,tx.id);Path path=files(f.indexRoot).get(0);byte[] bytes=Files.readAllBytes(path);
            java.nio.ByteBuffer.wrap(bytes).putLong(132,Long.MAX_VALUE);rewrite(path,bytes);
            ProtectionStore.Transaction next=f.group("tab","content://x",2,true);rejects(()->f.index.recordVerified(id,next.id),"sequence overflow");
            require(files(f.indexRoot).size()==1,"overflow cannot append stage");
        });
        test("catalogBadCheckpointAndCompletionWithoutPreparationVisible",()->{
            Fixture f=new Fixture();String id=f.identity.resolve("content://x","tab");ProtectionStore.Transaction tx=f.group("tab","content://x",1,true);
            HistoryCatalog.Checkpoint cp=f.catalog.name(tx.attempted.id,"name");Path p=f.parent.resolve("history_catalog").resolve(cp.id+".checkpoint");
            byte[] raw=Files.readAllBytes(p);raw[raw.length-1]^=1;Files.write(p,raw);rejects(()->f.catalog.all(),"corrupt catalog all API");rejects(()->f.index.list(id),"invalid checkpoint");
            Fixture g=new Fixture();String gid=g.identity.resolve("content://x","tab");ProtectionStore.Transaction gt=g.group("tab","content://x",1,true);
            Files.delete(g.source.resolve(gt.id+".transaction"));rejects(()->g.index.list(gid),"completion without preparation");
        });
    }
    static void durabilityTests()throws Exception {
        test("eachIdentityAndIndexDurabilityFailureRetainsV1Sources",()->{
            for(String target:new String[]{"history_identity","history_index"})for(String phase:new String[]{"before","after"})for(String operation:new String[]{"file-sync","move","directory-sync"}){
                Fixture f=new Fixture();String id=f.identity.resolve("content://x","tab");ProtectionStore.Transaction tx=f.group("tab","content://x",1,true);
                Map<Path,byte[]> before=capture(f.source);f.disk.target=target;f.disk.failure=phase+"-"+operation;f.disk.armed=true;
                if(target.equals("history_identity"))rejects(()->f.identity.forkAfterVerifiedSaveAs(id,"content://dest"),f.disk.failure);
                else rejects(()->f.index.recordVerified(id,tx.id),f.disk.failure);
                f.disk.armed=false;unchanged(before,f.source);Path changed=target.equals("history_identity")?f.aliases:f.indexRoot;
                if(operation.equals("directory-sync"))require(f.disk.commitMoved&&files(changed).size()==(target.equals("history_identity")?2:1),"publication directory sync reached after new record moved");
                boolean staged=files(changed).stream().anyMatch(p->p.toString().endsWith(".stage"));
                if(staged)rejects(()->new Fixture(f.parent),"retained uncommitted metadata blocks enumeration");
                else {Fixture fresh=new Fixture(f.parent);require(fresh.index.list(id).size()==2,"verified source remains recoverable");}
            }
        });
        test("indexCommittedReadbackCorruptionRetainsV1",()->{
            Fixture f=new Fixture();String id=f.identity.resolve("content://x","tab");ProtectionStore.Transaction tx=f.group("tab","content://x",1,true);Map<Path,byte[]> before=capture(f.source);
            f.disk.afterMove=()->{Path record=files(f.indexRoot).get(0);byte[] raw=Files.readAllBytes(record);raw[raw.length-1]^=1;Files.write(record,raw);};f.disk.armed=true;
            rejects(()->f.index.recordVerified(id,tx.id),"committed readback mismatch");f.disk.armed=false;unchanged(before,f.source);
            rejects(()->f.index.list(id),"committed corruption cannot hide");
        });
        test("boundedStringsRecordsEntriesBytesAndSymlinks",()->{
            Fixture f=new Fixture();rejects(()->f.identity.resolve("é".repeat(2049),"tab"),"4096 UTF8 bytes");
            rejects(()->f.identity.resolve("\ud800","tab"),"invalid surrogate");require(files(f.aliases).isEmpty(),"invalid strings create no stages");
            f.identity.resolve("é".repeat(2048),"tab");
            for(String target:new String[]{"history_identity","history_index"})for(int mode=0;mode<5;mode++){
                Fixture g=new Fixture();Path root=g.parent.resolve(target);
                if(mode==0)Files.write(root.resolve(UUID.randomUUID()+".stage"),new byte[16385]);
                if(mode==1)for(int i=0;i<4097;i++)Files.createFile(root.resolve(UUID.randomUUID()+".stage"));
                if(mode==2){for(int i=0;i<512;i++)Files.write(root.resolve(UUID.randomUUID()+".stage"),new byte[16384]);Files.write(root.resolve(UUID.randomUUID()+".stage"),new byte[]{1});}
                if(mode==3)Files.createSymbolicLink(root.resolve(UUID.randomUUID()+".stage"),g.source);
                if(mode==4)Files.createDirectory(root.resolve(UUID.randomUUID()+".stage"));
                rejects(()->new Fixture(g.parent),"bounded nonregular metadata "+mode);
            }
            Fixture g=new Fixture();Path alias=g.parent.resolve("symlink");Files.createSymbolicLink(alias,g.aliases);
            rejects(()->new HistoryIdentity(alias.toFile(),g.disk),"symlink identity root");
            Path parentAlias=g.parent.resolve("parent-alias");Files.createSymbolicLink(parentAlias,g.parent);
            rejects(()->new HistoryIndex(g.store,g.catalog,g.identity,parentAlias.resolve("new-index").toFile(),g.disk),"symlink ancestor index root");
        });
        test("exactMetadataInventoryCapsAcceptBoundaryThenRejectPlusOne",()->{
            Fixture f=new Fixture();Path root=f.parent.resolve("bounds-record");HistoryMetadataIO metadata=new HistoryMetadataIO(root.toFile(),".identity",1,new Disk());
            Path stage=root.resolve(UUID.randomUUID()+".stage");Files.write(stage,new byte[16384]);
            new HistoryMetadataIO(root.toFile(),".identity",1,new Disk());require(true,"exact 16KiB inventory entry accepted");
            Files.write(stage,new byte[16385]);rejects(()->new HistoryMetadataIO(root.toFile(),".identity",1,new Disk()),"record inventory cap");
            Path entries=f.parent.resolve("bounds-entries");new HistoryMetadataIO(entries.toFile(),".index",1,new Disk());
            for(int i=0;i<4096;i++)Files.createFile(entries.resolve(UUID.randomUUID()+".stage"));
            new HistoryMetadataIO(entries.toFile(),".index",1,new Disk());require(true,"exact 4096 inventory entries accepted");
            Files.createFile(entries.resolve(UUID.randomUUID()+".stage"));rejects(()->new HistoryMetadataIO(entries.toFile(),".index",1,new Disk()),"entry inventory cap");
            Path bytes=f.parent.resolve("bounds-bytes");new HistoryMetadataIO(bytes.toFile(),".index",1,new Disk());
            for(int i=0;i<512;i++)Files.write(bytes.resolve(UUID.randomUUID()+".stage"),new byte[16384]);
            new HistoryMetadataIO(bytes.toFile(),".index",1,new Disk());require(true,"exact 8MiB staged inventory accepted");
            Files.write(bytes.resolve(UUID.randomUUID()+".stage"),new byte[]{1});rejects(()->new HistoryMetadataIO(bytes.toFile(),".index",1,new Disk()),"byte inventory cap");
        });
        test("sameRootHandlesSerializeResolutionAndSequence",()->{
            Fixture f=new Fixture(), fresh=new Fixture(f.parent);java.util.concurrent.ExecutorService pool=java.util.concurrent.Executors.newFixedThreadPool(4);
            try{
                List<java.util.concurrent.Future<String>> resolved=new ArrayList<>();for(int i=0;i<16;i++){HistoryIdentity h=i%2==0?f.identity:fresh.identity;resolved.add(pool.submit(()->h.resolve("content://x","tab")));}
                Set<String> ids=new HashSet<>();for(java.util.concurrent.Future<String> value:resolved)ids.add(value.get());require(ids.size()==1&&files(f.aliases).size()==1,"one exact URI mapping under concurrency");
                String id=ids.iterator().next();List<ProtectionStore.Transaction> txs=new ArrayList<>();for(int i=0;i<12;i++)txs.add(f.group("tab","content://x",i,true));
                List<java.util.concurrent.Future<?>> indexed=new ArrayList<>();for(int i=0;i<txs.size();i++){HistoryIndex ix=i%2==0?f.index:fresh.index;String txid=txs.get(i).id;
                    indexed.add(pool.submit(()->{ix.recordVerified(id,txid);ix.recordVerified(id,txid);return null;}));}
                for(java.util.concurrent.Future<?> value:indexed)value.get();Set<Long> seq=new HashSet<>();for(HistoryIndex.Version v:f.index.list(id))seq.add(v.sequence);
                require(seq.size()==12&&Collections.min(seq)==1&&Collections.max(seq)==12&&files(f.indexRoot).size()==12,"unique monotonic persisted sequence and idempotent races");
            }finally{pool.shutdownNow();}
        });
    }
    /** Future-controller contract probe, not an Activity implementation or UI acceptance test. */
    static final class Guard {
        String adopted,historyError; boolean saved;
        void saveAs(Fixture f,String old,String uri,SaveCoordinator.Provider provider)throws Exception {
            if(uri==null)return; // Picker cancellation: no fork, coordinator or optional record.
            SaveCoordinator.Result result=new SaveCoordinator(f.store).save(new SaveCoordinator.Request("tab",uri,"file",1,"edited".getBytes(StandardCharsets.UTF_8),null,null,true),provider);
            saved=true;
            require(f.store.validate(result.transaction).verified,"actual fresh completion establishes successful save");
            try{adopted=f.identity.forkAfterVerifiedSaveAs(old,uri);f.index.recordVerified(adopted,result.transaction.id);}
            catch(IOException optional){historyError=optional.getMessage();}
        }
    }
    static SaveCoordinator.Provider provider(boolean failRead,boolean failWrite,boolean mismatch){
        return new SaveCoordinator.Provider(){byte[] bytes="original".getBytes(StandardCharsets.UTF_8);int reads;
            public byte[] read()throws IOException {if(failRead)throw new IOException("Read denied");reads++;if(mismatch&&reads>=3)return new byte[0];return bytes.clone();}
            public void write(byte[] value)throws IOException {if(failWrite){bytes=new byte[0];throw new IOException("Write truncated");}bytes=value.clone();}
        };
    }
    static void coordinatorTests()throws Exception {
        test("actualCoordinatorCancellationFailureGuardKeepsIdentityMetadata",()->{
            for(int mode=0;mode<4;mode++){
                Fixture f=new Fixture();String id=f.identity.resolve(null,"tab");Map<Path,byte[]> before=capture(f.aliases);Guard guard=new Guard();
                if(mode==0)guard.saveAs(f,id,null,provider(false,false,false));
                else{final int selected=mode;rejects(()->guard.saveAs(f,id,"content://dest",provider(selected==1,selected==2,selected==3)),"coordinator failure");}
                require(!guard.saved&&guard.adopted==null,"failed/cancelled save never adopts or forks");unchanged(before,f.aliases);require(files(f.indexRoot).isEmpty(),"no optional ownership from failed save");
            }
        });
        test("verifiedSaveAsForkAndExistingDestinationProvenance",()->{
            Fixture f=new Fixture();String old=f.identity.resolve(null,"tab");Guard guard=new Guard();guard.saveAs(f,old,"content://dest",provider(false,false,false));
            require(guard.saved&&guard.historyError==null&&!guard.adopted.equals(old),"success forks only after actual verified save");
            require(f.identity.lookup(guard.adopted).origins.equals(Collections.singletonList(old)),"explicit origin provenance");
            immutable(()->f.identity.lookup(guard.adopted).origins.clear());
            require(f.identity.resolve("content://dest","newtab").equals(guard.adopted),"native exact destination reopen");
            String second=f.identity.resolve(null,"secondtab");Map<Path,byte[]> before=capture(f.aliases);
            require(f.identity.forkAfterVerifiedSaveAs(second,"content://dest").equals(guard.adopted),"existing destination identity preserved");
            require(f.identity.lookup(guard.adopted).origins.containsAll(Arrays.asList(old,second)),"additional immutable provenance edge");
            for(Map.Entry<Path,byte[]> e:before.entrySet())require(Arrays.equals(e.getValue(),Files.readAllBytes(e.getKey())),"no existing alias rewritten");
            int count=files(f.aliases).size();f.identity.forkAfterVerifiedSaveAs(second,"content://dest");require(files(f.aliases).size()==count,"same provenance idempotent");
            require(f.index.list(guard.adopted).stream().allMatch(v->v.status==HistoryIndex.Status.INDEXED_VERIFIED),"destination history owns actual transaction");
        });
        test("optionalIndexFailureDoesNotNegateActualSave",()->{
            Fixture f=new Fixture();String old=f.identity.resolve(null,"tab"),dest=f.identity.forkAfterVerifiedSaveAs(old,"content://dest");
            f.disk.failure="before-file-sync";f.disk.onCompletionMove=()->f.disk.armed=true;Guard guard=new Guard();
            // The actual source completion moves before optional metadata is armed to fail.
            guard.saveAs(f,old,"content://dest",provider(false,false,false));f.disk.armed=false;
            require(guard.saved&&guard.historyError!=null&&dest.equals(guard.adopted),"actual guard reports provider success with optional history error");
            require(f.identity.resolve("content://dest","another").equals(dest),"existing destination identity survives optional failure");
            rejects(()->f.index.list(dest),"retained optional stage is visibly blocked");
            require(f.store.transactions().size()==1&&f.store.transactions().get(0).verified,"actual successful versions remain independently recoverable");
        });
    }
    static final BufferedReader CONTROL=new BufferedReader(new InputStreamReader(System.in,StandardCharsets.UTF_8));
    static void boundary(String point)throws IOException {System.out.println(point);System.out.flush();if(CONTROL.readLine()==null)throw new IOException("Boundary control closed");}
    public static void main(String[] args)throws Exception {
        if(args.length>0){
            if(args[0].equals("child")){Fixture f=new Fixture(Paths.get(args[1]));String id=f.identity.resolve("content://x","tab");ProtectionStore.Transaction tx=f.group("tab","content://x",1,true);
                Files.writeString(f.parent.resolve("info"),id+"\n"+tx.id);f.disk.target=args[2];f.disk.child=true;f.disk.armed=true;
                if(args[2].equals("history_identity"))f.identity.forkAfterVerifiedSaveAs(id,"content://dest");else f.index.recordVerified(id,tx.id);return;
            }
            if(args[0].equals("fault-reopen")){Path parent=Paths.get(args[1]);List<String> info=Files.readAllLines(parent.resolve("info"));
                ProtectionStore store=new ProtectionStore(parent.resolve("protection").toFile(),new Disk(),ProtectionStore.DEFAULT_QUOTA_BYTES);
                List<ProtectionStore.Transaction> groups=store.transactions();require(groups.size()==1&&groups.get(0).verified&&groups.get(0).id.equals(info.get(1)),"killed optional commit retains actual verified source");
                Path target=parent.resolve(args[2]);boolean staged=files(target).stream().anyMatch(p->p.toString().endsWith(".stage"));
                boolean beforeMove=Arrays.asList("before-file-sync","after-file-sync","before-move").contains(args[3]);require(staged==beforeMove,"exact stage/publication boundary");
                if(staged)rejects(()->new Fixture(parent),"crash stage visibly blocks partial index");
                else{Fixture f=new Fixture(parent);List<HistoryIndex.Version> versions=f.index.list(info.get(0));require(versions.size()==2,"crash reopen retains versions");
                    if(args[2].equals("history_index"))require(versions.stream().allMatch(v->v.status==HistoryIndex.Status.INDEXED_VERIFIED),"published crash index validates");
                    else require(f.identity.lookup(f.identity.resolve("content://dest","tab")).origins.equals(Collections.singletonList(info.get(0))),"published crash provenance validates");
                }System.out.println("PASS "+args[2]+" "+args[3]+" independent crash reopen");return;
            }
            Fixture f=new Fixture(Paths.get(args[1]));
            if(args[0].equals("seed")){String id=f.identity.resolve("content://restart/%41","tab");ProtectionStore.Transaction tx=f.group("legacy","content://restart/%41",1,true);f.index.recordVerified(id,tx.id);
                f.identity.forkAfterVerifiedSaveAs(id,"content://restart/destination");System.out.println(id);}
            else{String id=args[2];require(f.identity.resolve("content://restart/%41","newtab").equals(id),"process restart native alias");
                List<HistoryIndex.Version> versions=f.index.list(id);require(versions.size()==2&&versions.get(0).sequence==1&&versions.stream().allMatch(v->v.verified),"process restart validated index");
                require(f.identity.lookup(f.identity.resolve("content://restart/destination","tab")).origins.equals(Collections.singletonList(id)),"process restart provenance");
                System.out.println("PASS independent JVM reopen");}return;
        }
        regular();corruptionTests();durabilityTests();coordinatorTests();System.out.println("PASS "+cases+" history identity/index cases / "+assertions+" assertions");
    }
}
