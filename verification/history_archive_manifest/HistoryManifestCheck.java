package com.example.markdowndesk;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import com.example.markdowndesk.HistoryArchiveManifest.*;

/** Independently framed metadata plus real native Graph/control/archive compatibility. */
public final class HistoryManifestCheck {
    static int checks;
    interface Task {void run()throws Exception;}
    static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
    static void rejects(Task task,String why)throws Exception {
        try{task.run();throw new AssertionError("Accepted "+why);}catch(IOException expected){checks++;}
    }
    static UUID archiveId(long n){return new UUID(0x123456789abc4000L,0x8000000000000000L+n);}
    static byte[] sha(byte[] bytes)throws Exception{return MessageDigest.getInstance("SHA-256").digest(bytes);}
    static final String[] NAMES={"store","catalog","identity","index"};
    static final class Member {
        int role,kind;UUID nativeId,archiveId;long length;byte[] hash=new byte[32];
        Member(int role,int kind,long id){this.role=role;this.kind=kind;nativeId=new UUID(0,id);archiveId=archiveId(id);length=role==1?45:44;hash[0]=(byte)id;}
    }
    static Member[] allKinds(){return new Member[]{new Member(1,1,1),new Member(1,2,2),new Member(1,3,2),new Member(2,4,3),new Member(3,5,4),new Member(4,6,5)};}
    static void uuid(DataOutputStream out,UUID id)throws IOException{out.writeLong(id.getMostSignificantBits());out.writeLong(id.getLeastSignificantBits());}
    /** Does not call production writer or derive fields/constants from production codec. */
    static byte[] frame(Member[] entries,String[] names)throws Exception {
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();DataOutputStream out=new DataOutputStream(bytes);
        int size=212+74*entries.length;for(String name:names)size+=name.getBytes(StandardCharsets.UTF_8).length;
        long total=0;for(Member entry:entries)total+=entry.length;
        out.write("MDHMFST\n".getBytes(StandardCharsets.US_ASCII));out.writeInt(1);out.writeInt(size);out.writeInt(entries.length);
        out.writeLong(total);uuid(out,new UUID(0,91));out.write(new byte[32]);out.writeLong(0);out.write(new byte[32]);
        out.writeLong(64L<<20);out.writeInt(20);out.write(new byte[32]);
        for(int i=0;i<4;i++){out.writeByte(i+1);byte[] name=names[i].getBytes(StandardCharsets.UTF_8);out.writeInt(name.length);out.write(name);}
        for(Member entry:entries){out.writeByte(entry.role);out.writeByte(entry.kind);uuid(out,entry.nativeId);uuid(out,entry.archiveId);out.writeLong(entry.length);out.write(entry.hash);}
        out.write(sha(bytes.toByteArray()));return bytes.toByteArray();
    }
    static byte[] reseal(byte[] frame)throws Exception {byte[] result=frame.clone();System.arraycopy(sha(Arrays.copyOf(result,result.length-32)),0,result,result.length-32,32);return result;}
    static byte[] number(byte[] frame,int at,long value,int width)throws Exception {
        byte[] result=frame.clone();ByteBuffer b=ByteBuffer.wrap(result);if(width==4)b.putInt(at,(int)value);else b.putLong(at,value);return reseal(result);
    }
    static byte[] octet(byte[] frame,int at,int value)throws Exception{byte[] result=frame.clone();result[at]=(byte)value;return reseal(result);}
    static HistoryArchiveManifest read(byte[] bytes)throws IOException{return HistoryArchiveManifest.read(new ByteArrayInputStream(bytes));}
    static byte[] write(HistoryArchiveManifest manifest)throws IOException{ByteArrayOutputStream out=new ByteArrayOutputStream();manifest.write(out);return out.toByteArray();}
    static int entriesOffset(String[] names){int offset=180;for(String name:names)offset+=name.getBytes(StandardCharsets.UTF_8).length;return offset;}
    static void handwritten()throws Exception {
        Member[] members=allKinds();members[2].archiveId=archiveId(99);
        byte[] valid=frame(members,NAMES);HistoryArchiveManifest m=read(valid);
        check(Arrays.equals(valid,write(m)),"handwritten full wire replay");check(m.entries.size()==6,"all six native categories");
        check(m.nativeEnvelopeBytes==267&&m.archiveBytes==64+6*58+267,"exact envelope/archive accounting");
        check(m.entries.get(1).nativeId.equals(m.entries.get(2).nativeId)&&!m.entries.get(1).archiveId.equals(m.entries.get(2).archiveId),"preparation/completion keep distinct IDs");
        check(m.entries.get(5).kind.archiveType==PrivateArchive.Type.INDEX,"index explicit category");
        check(m.indexBytes==4096+6*1024&&m.serializedBytes==valid.length,"independent count charge/byte size");
        for(int n=0;n<valid.length;n++){byte[] prefix=Arrays.copyOf(valid,n);rejects(()->read(prefix),"truncated prefix "+n);}
        byte[] extra=Arrays.copyOf(valid,valid.length+1);rejects(()->read(extra),"true EOF");
        byte[] damaged=valid.clone();damaged[44]^=1;rejects(()->read(damaged),"checksum");
        rejects(()->read(octet(valid,0,'X')),"foreign domain");rejects(()->read(number(valid,8,2,4)),"version");
        for(long count:new long[]{-1,16381,Integer.MAX_VALUE})rejects(()->read(number(valid,16,count,4)),"count cap "+count);
        for(long size:new long[]{0,valid.length-1,valid.length+1,16777217,Integer.MAX_VALUE})rejects(()->read(number(valid,12,size,4)),"byte cap/size "+size);
        for(long total:new long[]{-1,0,268,1073741824L,Long.MAX_VALUE})rejects(()->read(number(valid,20,total,8)),"total/overflow "+total);
        rejects(()->read(number(valid,76,-1,8)),"negative generation");
        for(long quota:new long[]{0,63L<<20,65L<<20,Long.MAX_VALUE})rejects(()->read(number(valid,116,quota,8)),"quota "+quota);
        for(int automatic:new int[]{-1,1,51,Integer.MAX_VALUE})rejects(()->read(number(valid,124,automatic,4)),"automatic count "+automatic);
        for(long quota:new long[]{64L<<20,128L<<20,256L<<20,512L<<20,1024L<<20})check(read(number(valid,116,quota,8)).quotaBytes==quota,"allowed quota");
        for(int automatic:new int[]{0,5,10,20,50})check(read(number(valid,124,automatic,4)).automaticCount==automatic,"allowed count");
        check(read(number(valid,76,Long.MAX_VALUE,8)).policyGeneration==Long.MAX_VALUE,"generation upper bound");
        rejects(()->read(octet(valid,160,2)),"registered role order");
        for(int length:new int[]{0,-1,65,Integer.MAX_VALUE})rejects(()->read(number(valid,161,length,4)),"basename length");
        rejects(()->read(octet(valid,165,0xff)),"strict basename UTF8");
        for(String bad:new String[]{"", ".","..","a/b","a\\b","a:b","\u0000","\u007f","catalog","history_control","history_enrollment.control","history_enrollment.control.stage","x".repeat(65),"雪".repeat(22)}) {
            String[] names=NAMES.clone();names[0]=bad;rejects(()->read(frame(members,names)),"invalid/reserved basename");
        }
        String[] names=NAMES.clone();names[0]="x".repeat(64);check(read(frame(members,names)).registeredRoles.get(Role.STORE).length()==64,"exact name byte cap");
        names[0]="雪".repeat(21);check(read(frame(members,names)).registeredRoles.get(Role.STORE).equals(names[0]),"strict unicode role names");
        int start=entriesOffset(NAMES);
        for(int role:new int[]{0,2,5,255})rejects(()->read(octet(valid,start,role)),"entry role/mismatch");
        for(int kind:new int[]{0,4,7,255})rejects(()->read(octet(valid,start+1,kind)),"kind/mismatch");
        rejects(()->read(octet(valid,start+24,0x10)),"non-v4 archive UUID");
        rejects(()->read(octet(valid,start+26,0)),"non-RFC archive UUID");
        for(long length:new long[]{-1,0,44,8421377,Long.MAX_VALUE})rejects(()->read(number(valid,start+34,length,8)),"snapshot length "+length);
        for(int i=0;i<6;i++) {
            long max=i==0?8421376:i<3?32768:16384;Member[] one={new Member(members[i].role,members[i].kind,i+10)};
            one[0].length=max;check(read(frame(one,NAMES)).entries.get(0).envelopeBytes==max,"exact kind cap");
            one[0].length=max+1;rejects(()->read(frame(one,NAMES)),"kind cap+1");
            one[0].length=i<3?44:43;rejects(()->read(frame(one,NAMES)),"kind minimum-1");
        }
        Member[] duplicate={new Member(1,1,1),new Member(1,1,1)};duplicate[1].archiveId=archiveId(2);
        rejects(()->read(frame(duplicate,NAMES)),"duplicate native key");
        Member[] wrongOrder={new Member(1,1,2),new Member(1,1,1)};rejects(()->read(frame(wrongOrder,NAMES)),"native order");
        Member[] sameId=allKinds();rejects(()->read(frame(sameId,NAMES)),"duplicate archive ID");
        Member[] unsigned={new Member(1,1,1),new Member(1,1,2)};unsigned[0].nativeId=new UUID(0,Long.MAX_VALUE);unsigned[1].nativeId=new UUID(0,Long.MIN_VALUE);
        check(read(frame(unsigned,NAMES)).entries.size()==2,"unsigned native UUID order");
        Collections.reverse(Arrays.asList(unsigned));rejects(()->read(frame(unsigned,NAMES)),"signed order is not canonical");
        Member[] overArchive=new Member[128];for(int i=0;i<128;i++){overArchive[i]=new Member(1,1,i+1);overArchive[i].length=8421376;}
        rejects(()->read(frame(overArchive,NAMES)),"aggregate archive cap despite individual valid lengths");
        check(read(frame(new Member[0],NAMES)).entries.isEmpty(),"empty complete mapping permitted");
        ownership(m,valid);
    }
    static void ownership(HistoryArchiveManifest m,byte[] valid)throws Exception {
        byte[] hash=m.entries.get(0).envelopeHashBytes();hash[0]^=1;
        check(m.entries.get(0).envelopeHashBytes()[0]==1,"entry hash defensive copy");
        for(byte[] binding:new byte[][]{m.registrationHashBytes(),m.policyHashBytes(),m.graphHashBytes()})binding[0]=99;
        check(m.registrationHashBytes()[0]==0&&m.policyHashBytes()[0]==0&&m.graphHashBytes()[0]==0,"binding defensive copies");
        try{m.entries.clear();throw new AssertionError("mutable list");}catch(UnsupportedOperationException expected){checks++;}
        try{m.registeredRoles.clear();throw new AssertionError("mutable map");}catch(UnsupportedOperationException expected){checks++;}
        class Input extends ByteArrayInputStream {boolean closed;int calls;Input(){super(valid);}public void close(){closed=true;}
            public synchronized int read(byte[] b,int o,int l){return calls++%2==0?0:super.read(b,o,Math.min(l,3));}}
        Input input=new Input();check(Arrays.equals(valid,write(HistoryArchiveManifest.read(input)))&&!input.closed,"zero/chunked input caller-owned");
        class Output extends ByteArrayOutputStream {boolean closed,flushed;public void close(){closed=true;}public void flush(){flushed=true;}}
        Output output=new Output();m.write(output);check(!output.closed&&!output.flushed&&Arrays.equals(valid,output.toByteArray()),"writer caller-owned exact bytes");
        rejects(()->HistoryArchiveManifest.read(null),"null input");rejects(()->m.write(null),"null output");
        rejects(()->m.write(new OutputStream(){public void write(int b)throws IOException{throw new IOException("injected");}}),"output failure");
        rejects(()->HistoryArchiveManifest.read(new InputStream(){public int read()throws IOException{throw new IOException("injected");}}),"input failure");
    }
    static final class Disk implements ProtectionStore.Durability {
        public void syncFile(FileDescriptor fd)throws IOException{fd.sync();}
        public void move(File a,File b)throws IOException{Files.move(a.toPath(),b.toPath(),StandardCopyOption.ATOMIC_MOVE);}
        public void syncDirectory(File dir)throws IOException{try(FileChannel c=FileChannel.open(dir.toPath(),StandardOpenOption.READ)){c.force(true);}}
    }
    static final class Fixture implements AutoCloseable {
        final Path parent=Files.createTempDirectory("manifest-native-");final Disk disk=new Disk();
        final ProtectionStore store=new ProtectionStore(parent.resolve("store").toFile(),disk,ProtectionStore.DEFAULT_QUOTA_BYTES);
        final HistoryCatalog catalog=new HistoryCatalog(parent.resolve("catalog").toFile(),store,disk);
        final HistoryIdentity identity=new HistoryIdentity(parent.resolve("identity").toFile(),disk);
        final HistoryIndex index=new HistoryIndex(store,catalog,identity,parent.resolve("index").toFile(),disk);
        final HistoryGraph graph=new HistoryGraph(store,catalog,identity,index,null);
        Fixture()throws Exception{}
        HistoryCapacity register()throws IOException{return HistoryCapacity.register(store,catalog,identity,index,new HistoryPolicy(64L<<20,20),disk);}
        HistoryControlStore.Inspection control()throws IOException{return new HistoryControlStore(parent.toFile()).inspect();}
        Path path(Entry e){return parent.resolve(e.role.nativeName).resolve(e.nativeId+"."+e.kind.nativeName);}
        public void close()throws IOException{try(java.util.stream.Stream<Path> paths=Files.walk(parent)){for(Path path:(Iterable<Path>)paths.sorted(Comparator.reverseOrder())::iterator)Files.delete(path);}}
    }
    /** Test-only synthetic DTO construction for malformed descriptor admission; never native validity evidence. */
    @SuppressWarnings("unchecked")
    static HistoryGraph.Graph synthetic(HistoryGraph.Graph base,List<ProtectionStore.StoredRecord> records,List<String> roles)throws Exception {
        Class<?> builderType=Class.forName("com.example.markdowndesk.HistoryGraph$Builder");
        java.lang.reflect.Constructor<?> make=builderType.getDeclaredConstructor();make.setAccessible(true);Object builder=make.newInstance();
        java.lang.reflect.Field entries=builderType.getDeclaredField("records");entries.setAccessible(true);
        ((List<ProtectionStore.StoredRecord>)entries.get(builder)).addAll(records);
        java.lang.reflect.Field names=builderType.getDeclaredField("roleNames");names.setAccessible(true);
        ((List<String>)names.get(builder)).addAll(roles);
        java.lang.reflect.Field policy=builderType.getDeclaredField("effective");policy.setAccessible(true);policy.set(builder,base.effectivePolicy);
        java.lang.reflect.Constructor<HistoryGraph.Graph> graph=HistoryGraph.Graph.class.getDeclaredConstructor(builderType,String.class,HistoryPolicy.class);
        graph.setAccessible(true);return graph.newInstance(builder,base.fingerprint,base.policy);
    }
    static void descriptorAdmission(HistoryGraph.Graph base,HistoryControlStore.Inspection control)throws Exception {
        String id="00000000-0000-0000-0000-000000000001",hash="00".repeat(32);
        ProtectionStore.StoredRecord good=new ProtectionStore.StoredRecord("store",id+".snapshot",id,"snapshot",45,hash);
        List<ProtectionStore.StoredRecord> bad=Arrays.asList(null,
            new ProtectionStore.StoredRecord("other",id+".snapshot",id,"snapshot",45,hash),
            new ProtectionStore.StoredRecord("store",id+".stage",id,"stage",45,hash),
            new ProtectionStore.StoredRecord("catalog",id+".snapshot",id,"snapshot",45,hash),
            new ProtectionStore.StoredRecord("store","elsewhere/"+id+".snapshot",id,"snapshot",45,hash),
            new ProtectionStore.StoredRecord("store",id+".complete",id,"snapshot",45,hash),
            new ProtectionStore.StoredRecord("store","1-1-1-1-1.snapshot","1-1-1-1-1","snapshot",45,hash),
            new ProtectionStore.StoredRecord("store",id+".snapshot",null,"snapshot",45,hash),
            new ProtectionStore.StoredRecord("store",id+".snapshot",id,"snapshot",44,hash),
            new ProtectionStore.StoredRecord("store",id+".snapshot",id,"snapshot",8421377,hash),
            new ProtectionStore.StoredRecord("store",id+".snapshot",id,"snapshot",45,"AB".repeat(32)),
            new ProtectionStore.StoredRecord("store",id+".snapshot",id,"snapshot",45,"0"),
            new ProtectionStore.StoredRecord("store",id+".snapshot",id,"snapshot",45,null));
        for(ProtectionStore.StoredRecord record:bad) {
            HistoryGraph.Graph malformed=synthetic(base,Arrays.asList(good,record),base.registeredRoleNames);int[] calls={0};
            rejects(()->HistoryArchiveManifest.freeze(malformed,control,()->{calls[0]++;return archiveId(calls[0]);}),"malformed typed native descriptor");
            check(calls[0]==0,"all descriptors validated before ID callbacks");
        }
        for(List<ProtectionStore.StoredRecord> records:Arrays.asList(Arrays.asList(good,good),Collections.nCopies(16381,good))) {
            HistoryGraph.Graph malformed=synthetic(base,records,base.registeredRoleNames);int[] calls={0};
            rejects(()->HistoryArchiveManifest.freeze(malformed,control,()->{calls[0]++;return archiveId(calls[0]);}),"duplicate or excessive complete inventory");
            check(calls[0]==0,"whole invalid inventory rejected without partial mapping IDs");
        }
        HistoryGraph.Graph wrongRoles=synthetic(base,Collections.singletonList(good),Arrays.asList("different","catalog","identity","index"));
        rejects(()->HistoryArchiveManifest.freeze(wrongRoles,control,UUID::randomUUID),"graph registered role binding mismatch");
        HistoryGraph.Graph sameContent=synthetic(base,Arrays.asList(good,new ProtectionStore.StoredRecord("store",id+".transaction",id,"transaction",45,hash)),base.registeredRoleNames);
        int[] calls={0};HistoryArchiveManifest frozen=HistoryArchiveManifest.freeze(sameContent,control,()->archiveId(++calls[0]));
        check(frozen.entries.size()==2&&calls[0]==2&&!frozen.entries.get(0).archiveId.equals(frozen.entries.get(1).archiveId),"identical descriptor hashes do not deduplicate native members");
    }
    static void nativeGraph()throws Exception {
        try(Fixture f=new Fixture();Fixture foreign=new Fixture()) {
            String history=f.identity.resolve("content://synthetic","tab");
            ProtectionStore.Snapshot a=f.store.putSnapshot("tab","content://synthetic","title",1,new byte[]{1,2},"original");
            ProtectionStore.Snapshot b=f.store.putSnapshot("tab","content://synthetic","title",1,new byte[]{1,2},"attempted");
            ProtectionStore.Transaction tx=f.store.prepare(a,b,a.hash);f.store.complete(tx);f.catalog.name(b.id,"named");f.index.recordVerified(history,tx.id);
            HistoryGraph.Graph legacy=f.graph.scan();rejects(()->HistoryArchiveManifest.freeze(legacy,f.control(),UUID::randomUUID),"legacy has no acknowledged policy");
            HistoryCapacity capacity=f.register();foreign.register();HistoryGraph.Graph graph=f.graph.scan();HistoryControlStore.Inspection control=f.control();
            descriptorAdmission(graph,control);
            int[] generated={0};HistoryArchiveManifest manifest=HistoryArchiveManifest.freeze(graph,control,()->archiveId(++generated[0]));
            check(generated[0]==graph.records.size()&&manifest.entries.size()==graph.records.size(),"all records enumerated once");
            check(manifest.bundleId.toString().equals(graph.effectivePolicy.bundleId)&&manifest.policyGeneration==graph.effectivePolicy.generation,"captured policy binding");
            check(HistoryStorage.hex(manifest.registrationHashBytes()).equals(control.registration.envelopeHash),"exact registration envelope hash");
            check(HistoryStorage.hex(manifest.policyHashBytes()).equals(graph.effectivePolicy.controlRecordHash)&&HistoryStorage.hex(manifest.graphHashBytes()).equals(graph.fingerprint),"policy and graph hashes");
            long total=0;EnumSet<Kind> kinds=EnumSet.noneOf(Kind.class);Set<UUID> ids=new HashSet<>();List<Entry> pair=new ArrayList<>();
            for(Entry entry:manifest.entries) {
                kinds.add(entry.kind);check(ids.add(entry.archiveId),"all archive IDs distinct");byte[] bytes=Files.readAllBytes(f.path(entry));total+=bytes.length;
                check(bytes.length==entry.envelopeBytes&&Arrays.equals(sha(bytes),entry.envelopeHashBytes()),"full native envelope length/hash");
                if(entry.nativeId.toString().equals(tx.id))pair.add(entry);
            }
            check(kinds.size()==6&&pair.size()==2&&!pair.get(0).archiveId.equals(pair.get(1).archiveId),"real preparation/completion same native UUID distinct archive IDs");
            check(total==manifest.nativeEnvelopeBytes&&manifest.entries.stream().filter(e->e.kind==Kind.SNAPSHOT).count()==2,"same content snapshots not merged");
            byte[] persisted=write(manifest);HistoryArchiveManifest replay=read(persisted);
            check(Arrays.equals(persisted,write(replay))&&generated[0]==graph.records.size(),"persisted retry does not generate IDs");
            List<ProtectionStore.StoredRecord> reversed=new ArrayList<>(graph.records);Collections.reverse(reversed);
            HistoryGraph.Graph reordered=synthetic(graph,reversed,graph.registeredRoleNames);int[] orderIds={0};
            check(Arrays.equals(persisted,write(HistoryArchiveManifest.freeze(reordered,control,()->archiveId(++orderIds[0])))),"canonical mapping independent of source enumeration order");
            int[] failedAt={0};rejects(()->HistoryArchiveManifest.freeze(graph,control,()->{if(++failedAt[0]==3)throw new IOException("third ID failed");return archiveId(failedAt[0]);}),"mid-generation failure");
            check(failedAt[0]==3,"ID failure aborts without subsequent callbacks or partial return");
            ByteArrayOutputStream archive=new ByteArrayOutputStream();int[] next={0};
            PrivateArchive.ArchiveSummary summary=PrivateArchive.write(()->{
                if(next[0]==replay.entries.size())return null;Entry e=replay.entries.get(next[0]++);
                return new PrivateArchive.SourceEntry(e.archiveId,e.kind.archiveType,e.envelopeBytes,()->Files.newInputStream(f.path(e)));
            },archive);
            check(summary.byteCount==manifest.archiveBytes&&archive.size()==64+58*manifest.entries.size()+total,"actual PrivateArchive v1 overhead formula");
            int[] received={0};PrivateArchive.read(new ByteArrayInputStream(archive.toByteArray()),entry->{
                Entry expected=replay.entries.get(received[0]++);check(entry.id.equals(expected.archiveId)&&entry.type==expected.kind.archiveType&&entry.length==expected.envelopeBytes,"actual archive typed descriptor");
                return OutputStream.nullOutputStream();
            });check(received[0]==manifest.entries.size(),"actual archive covers full mapping");
            for(IdSource bad:new IdSource[]{()->null,()->new UUID(0,1),()->archiveId(1),()->{throw new IOException("injected IDs");}})
                rejects(()->HistoryArchiveManifest.freeze(graph,control,bad),"bad/failing ID source aborts whole mapping");
            rejects(()->HistoryArchiveManifest.freeze(graph,foreign.control(),UUID::randomUUID),"foreign registered bundle");
            rejects(()->HistoryArchiveManifest.freeze(null,control,UUID::randomUUID),"missing graph");
            capacity.setPolicy(new HistoryPolicy(128L<<20,5));
            rejects(()->HistoryArchiveManifest.freeze(graph,f.control(),UUID::randomUUID),"new policy cannot bind old graph");
            check(Arrays.equals(persisted,write(replay)),"old frozen provenance unchanged by current policy");
            Files.write(f.parent.resolve("catalog").resolve(UUID.randomUUID()+".stage"),new byte[]{1});
            rejects(()->HistoryArchiveManifest.freeze(f.graph.scan(),f.control(),UUID::randomUUID),"retained native stage");
        }
    }
    public static void main(String[] args)throws Exception{handwritten();nativeGraph();System.out.println("History manifest: "+checks+" checks PASS");}
}
