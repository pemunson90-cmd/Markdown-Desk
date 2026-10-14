package com.example.markdowndesk;

import java.io.*;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;

/** Actual codec and disk fixtures; no writer/admission or source-string assertions. */
public final class HistoryControlCodecCheck {
    private static final UUID BUNDLE=UUID.fromString("b80a3489-e4fb-4e4f-8a4c-90abace10cc1");
    private static final long MIB=1024L*1024, QUOTA=64*MIB, BOOTSTRAP=12345;
    private static int assertions, cases;
    private interface Checked {void run()throws Exception;}
    private static void check(boolean condition,String message){assertions++;if(!condition)throw new AssertionError(message);}
    private static void reject(Checked action,String message)throws Exception {
        try{action.run();}catch(IOException expected){assertions++;return;}throw new AssertionError(message);
    }
    private static void test(String name,Checked action)throws Exception{action.run();cases++;System.out.println("PASS "+name);}
    private static List<HistoryControlStore.Role> roles()throws IOException {
        return Arrays.asList(new HistoryControlStore.Role(1,"protection"),new HistoryControlStore.Role(2,"catalog"),
            new HistoryControlStore.Role(3,"identity"),new HistoryControlStore.Role(4,"index"));
    }
    private static byte[] registration()throws IOException{return HistoryControlStore.encodeRegistration(BUNDLE,BOOTSTRAP,roles(),QUOTA,20);}
    private static byte[] policy(long gen,String previous)throws IOException{return HistoryControlStore.encodePolicy(BUNDLE,gen,previous,128*MIB,5);}
    private static byte[] sha(byte[] bytes)throws Exception{return MessageDigest.getInstance("SHA-256").digest(bytes);}
    private static String hash(byte[] bytes)throws Exception{StringBuilder b=new StringBuilder();for(byte v:sha(bytes))b.append(String.format(Locale.ROOT,"%02x",v&255));return b.toString();}
    private static byte[] rehash(byte[] bytes)throws Exception {
        byte[] result=bytes.clone();System.arraycopy(sha(Arrays.copyOf(result,result.length-32)),0,result,result.length-32,32);return result;
    }
    private static byte[] rawRegistration(int[] ids,byte[][] names,long generation,long bootstrap,long quota,int count)throws Exception {
        ByteArrayOutputStream body=new ByteArrayOutputStream();DataOutputStream out=new DataOutputStream(body);
        out.writeLong(BUNDLE.getMostSignificantBits());out.writeLong(BUNDLE.getLeastSignificantBits());out.writeLong(generation);
        for(int i=0;i<4;i++){out.writeByte(ids[i]);out.writeInt(names[i].length);out.write(names[i]);}
        out.writeLong(bootstrap);out.writeLong(quota);out.writeInt(count);return frame(1,body.toByteArray());
    }
    private static byte[][] names(){return new byte[][]{"protection".getBytes(StandardCharsets.UTF_8),"catalog".getBytes(StandardCharsets.UTF_8),"identity".getBytes(StandardCharsets.UTF_8),"index".getBytes(StandardCharsets.UTF_8)};}
    private static byte[] frame(int type,byte[] body)throws Exception {
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();DataOutputStream out=new DataOutputStream(bytes);
        out.writeInt(0x4d444843);out.writeInt(1);out.writeByte(type);out.writeInt(body.length);out.write(body);out.flush();out.write(sha(bytes.toByteArray()));return bytes.toByteArray();
    }
    private static final class Fixture {
        final Path parent,root;final HistoryControlStore store;
        Fixture(boolean control)throws Exception {
            parent=Files.createTempDirectory("control-codec-").toRealPath();root=parent.resolve("history_control");
            for(String name:new String[]{"protection","catalog","identity","index"})Files.createDirectory(parent.resolve(name));
            if(control)Files.createDirectory(root);store=new HistoryControlStore(parent.toFile());
        }
        void put(String name,byte[] bytes)throws IOException{Files.write(root.resolve(name),bytes,StandardOpenOption.CREATE_NEW);}
        void registered()throws Exception{put("registration.control",registration());}
        HistoryControlStore.Inspection inspect()throws IOException{return store.inspect();}
        void unchanged(Checked action)throws Exception {
            Map<String,String> before=tree(parent);action.run();check(before.equals(tree(parent)),"inspection mutated fixture");
        }
    }
    private static Map<String,String> tree(Path parent)throws Exception {
        Map<String,String> result=new TreeMap<>();try(java.util.stream.Stream<Path> stream=Files.walk(parent)){
            for(Iterator<Path> it=stream.iterator();it.hasNext();){Path p=it.next();String name=parent.relativize(p).toString();
                if(Files.isSymbolicLink(p))result.put(name,"link:"+Files.readSymbolicLink(p));
                else if(Files.isRegularFile(p,LinkOption.NOFOLLOW_LINKS))result.put(name,hash(Files.readAllBytes(p)));
                else result.put(name,"directory");}
        }return result;
    }
    private static void blocked(Fixture f,String message)throws Exception {
        f.unchanged(()->{HistoryControlStore.Inspection i=f.inspect();check(i.state==HistoryControlStore.State.BLOCKED,message);check(!i.diagnostics.isEmpty(),"blocked diagnostic absent");});
    }
    public static void main(String[] args)throws Exception {
        test("golden envelope and separate bootstrap quota",()->{
            byte[] bytes=registration();check(Arrays.equals(bytes,rawRegistration(new int[]{1,2,3,4},names(),0,BOOTSTRAP,QUOTA,20)),"exact registration wire bytes");
            HistoryControlStore.Registration r=(HistoryControlStore.Registration)HistoryControlStore.decode(bytes);
            check(r.bundleId.equals(BUNDLE)&&r.generation==0,"registration identity");check(r.bootstrapQuotaBytes==BOOTSTRAP&&r.quotaBytes==QUOTA,"bootstrap separately persisted");
            check(r.envelopeHash.equals(hash(bytes))&&r.serializedBytes==bytes.length,"whole-envelope descriptor hash");
            try{r.roles.add(new HistoryControlStore.Role(1,"other"));throw new AssertionError("roles mutable");}catch(UnsupportedOperationException expected){assertions++;}
        });
        test("golden policy envelope binds full previous record hash",()->{
            byte[] previous=registration(),encoded=policy(1,hash(previous));
            ByteArrayOutputStream bytes=new ByteArrayOutputStream();DataOutputStream out=new DataOutputStream(bytes);
            out.writeLong(BUNDLE.getMostSignificantBits());out.writeLong(BUNDLE.getLeastSignificantBits());out.writeLong(1);
            out.write(sha(previous));out.writeLong(128*MIB);out.writeInt(5);
            check(Arrays.equals(encoded,frame(2,bytes.toByteArray())),"exact policy wire bytes");
            HistoryControlStore.Policy p=(HistoryControlStore.Policy)HistoryControlStore.decode(encoded);
            check(p.generation==1&&p.previousHash.equals(hash(previous))&&p.quotaBytes==128*MIB&&p.automaticCount==5,"policy fields");
            check(p.envelopeHash.equals(hash(encoded)),"policy whole-envelope hash");
            Fixture wrong=new Fixture(true);wrong.registered();wrong.put(HistoryControlStore.policyFilename(1),policy(1,hash(Arrays.copyOf(previous,previous.length-32))));
            blocked(wrong,"framing checksum digest is not full previous envelope digest");
        });
        test("valid chain sorted by generation regardless directory/timestamps",()->{
            Fixture f=new Fixture(true);byte[] reg=registration(),one=policy(1,hash(reg)),two=policy(2,hash(one));
            f.put(HistoryControlStore.policyFilename(2),two);f.put("registration.control",reg);f.put(HistoryControlStore.policyFilename(1),one);
            Files.setLastModifiedTime(f.root.resolve(HistoryControlStore.policyFilename(2)),java.nio.file.attribute.FileTime.fromMillis(1));
            f.unchanged(()->{HistoryControlStore.Inspection i=f.inspect();check(i.state==HistoryControlStore.State.VALIDATED&&i.chainComplete&&i.usageComplete,"valid complete chain");
                check(i.latest.generation==2&&i.latest.envelopeHash.equals(hash(two)),"latest generation/full hash");check(i.actualBytes==reg.length+one.length+two.length&&i.actualEntries==3,"actual accounting");});
        });
        test("absent root unregistered; empty root blocked",()->{
            Fixture f=new Fixture(false);f.unchanged(()->{check(f.inspect().state==HistoryControlStore.State.UNREGISTERED,"absent root legacy discovery");check(!Files.exists(f.root),"no mkdir");});
            Files.createDirectory(f.root);blocked(f,"empty existing root cannot fall back");
        });
        test("strict framing length version type checksum and trailing bytes",()->{
            byte[] good=registration();for(int offset:new int[]{0,4,8,9}){byte[] bad=good.clone();bad[offset]^=0x40;reject(()->HistoryControlStore.decode(rehash(bad)),"bad header accepted "+offset);}
            byte[] huge=good.clone();ByteBuffer.wrap(huge).putInt(9,Integer.MAX_VALUE);reject(()->HistoryControlStore.decode(rehash(huge)),"untrusted length");
            reject(()->HistoryControlStore.decode(Arrays.copyOf(good,good.length-1)),"truncation");
            reject(()->HistoryControlStore.decode(Arrays.copyOf(good,good.length+1)),"envelope trailing");
            byte[] checksum=good.clone();checksum[checksum.length-1]^=1;reject(()->HistoryControlStore.decode(checksum),"checksum");
            byte[] body=Arrays.copyOfRange(good,13,good.length-32);reject(()->HistoryControlStore.decode(frame(1,Arrays.copyOf(body,body.length+1))),"body trailing");
            reject(()->HistoryControlStore.decode(new byte[16385]),"maximum envelope");
        });
        test("roles order uniqueness strict UTF8 direct names",()->{
            for(int[] ids:new int[][]{{1,1,3,4},{2,1,3,4},{1,2,3,9}})reject(()->HistoryControlStore.decode(rawRegistration(ids,names(),0,BOOTSTRAP,QUOTA,20)),"role IDs");
            for(String name:new String[]{"",".","..","../x","/absolute","x/y","x\\y","raw:uri","x\u0000","x\n","x\u007f"}){
                byte[][] n=names();n[0]=name.getBytes(StandardCharsets.UTF_8);reject(()->HistoryControlStore.decode(rawRegistration(new int[]{1,2,3,4},n,0,BOOTSTRAP,QUOTA,20)),"unsafe role name "+name.length());}
            byte[][] same=names();same[1]=same[0];reject(()->HistoryControlStore.decode(rawRegistration(new int[]{1,2,3,4},same,0,BOOTSTRAP,QUOTA,20)),"duplicate roots");
            byte[][] malformed=names();malformed[0]=new byte[]{(byte)0xc0,(byte)0xaf};reject(()->HistoryControlStore.decode(rawRegistration(new int[]{1,2,3,4},malformed,0,BOOTSTRAP,QUOTA,20)),"malformed UTF8");
            byte[][] longName=names();longName[0]=new byte[65];Arrays.fill(longName[0],(byte)'x');reject(()->HistoryControlStore.decode(rawRegistration(new int[]{1,2,3,4},longName,0,BOOTSTRAP,QUOTA,20)),"byte name limit");
            reject(()->new HistoryControlStore.Role(1,"\ud800"),"strict encode surrogate");
            byte[] negative=registration();ByteBuffer.wrap(negative).putInt(38,-1);reject(()->HistoryControlStore.decode(rehash(negative)),"negative string length");
        });
        test("UTF8 byte boundaries and every accepted policy choice",()->{
            String ascii="x".repeat(64),unicode="é".repeat(32);
            check(new HistoryControlStore.Role(1,ascii).basename.equals(ascii),"64 ASCII bytes accepted");
            check(new HistoryControlStore.Role(1,unicode).basename.equals(unicode),"64 multibyte UTF8 bytes accepted");
            reject(()->new HistoryControlStore.Role(1,"é".repeat(33)),"UTF8 bytes rather than characters bound");
            for(long q:new long[]{64*MIB,128*MIB,256*MIB,512*MIB,1024*MIB})for(int count:new int[]{0,5,10,20,50}){
                HistoryControlStore.Registration r=(HistoryControlStore.Registration)HistoryControlStore.decode(HistoryControlStore.encodeRegistration(BUNDLE,0,roles(),q,count));
                check(r.quotaBytes==q&&r.automaticCount==count&&r.bootstrapQuotaBytes==0,"accepted policy choices");
            }
        });
        test("quota choices registration generation and checked next generation",()->{
            for(long q:new long[]{0,1,63*MIB,Long.MAX_VALUE})reject(()->HistoryControlStore.encodeRegistration(BUNDLE,BOOTSTRAP,roles(),q,20),"quota choice");
            for(int count:new int[]{-1,1,21,Integer.MAX_VALUE})reject(()->HistoryControlStore.encodeRegistration(BUNDLE,BOOTSTRAP,roles(),QUOTA,count),"count choice");
            reject(()->HistoryControlStore.encodeRegistration(BUNDLE,-1,roles(),QUOTA,20),"negative bootstrap");
            reject(()->HistoryControlStore.decode(rawRegistration(new int[]{1,2,3,4},names(),1,BOOTSTRAP,QUOTA,20)),"registration generation");
            check(HistoryControlStore.nextGeneration(0)==1&&HistoryControlStore.nextGeneration(Long.MAX_VALUE-1)==Long.MAX_VALUE,"checked next generation");
            reject(()->HistoryControlStore.nextGeneration(Long.MAX_VALUE),"generation overflow");reject(()->HistoryControlStore.nextGeneration(-1),"negative prior generation");
            reject(()->policy(0,hash(registration())),"policy zero");reject(()->policy(-1,hash(registration())),"policy negative");
            reject(()->policy(1,"00"),"previous hash length");
            check(HistoryControlStore.policyFilename(1).equals("policy-0000000000000001.control"),"deterministic filename");
            check(HistoryControlStore.stageFilename("registration.control").equals("registration.control.stage"),"deterministic stage");
        });
        test("missing broken foreign and forked chains retain prefix",()->{
            for(int scenario=0;scenario<6;scenario++){
                Fixture f=new Fixture(true);f.registered();String regHash=hash(registration());
                if(scenario==0)f.put(HistoryControlStore.policyFilename(2),policy(2,regHash));
                if(scenario==1)f.put(HistoryControlStore.policyFilename(1),policy(1,hash(new byte[]{1})));
                if(scenario==2)f.put(HistoryControlStore.policyFilename(1),HistoryControlStore.encodePolicy(UUID.randomUUID(),1,regHash,QUOTA,20));
                if(scenario==3){f.put(HistoryControlStore.policyFilename(1),policy(1,regHash));f.put(HistoryControlStore.policyFilename(2),policy(1,regHash));}
                if(scenario==4){f.put(HistoryControlStore.policyFilename(1),policy(1,regHash));f.put("policy-000000000000000A.control",policy(10,regHash));}
                if(scenario==5)f.put(HistoryControlStore.policyFilename(Long.MAX_VALUE),policy(Long.MAX_VALUE,regHash));
                blocked(f,"invalid chain "+scenario);check(!f.inspect().chainComplete&&f.inspect().registration!=null,"validated registration prefix retained");
            }
        });
        test("full next stage provisional never effective; partial unknown stale conflicting stages blocked",()->{
            Fixture f=new Fixture(true);f.registered();byte[] next=policy(1,hash(registration()));
            String stage=HistoryControlStore.stageFilename(HistoryControlStore.policyFilename(1));f.put(stage,next);
            f.unchanged(()->{HistoryControlStore.Inspection i=f.inspect();check(i.state==HistoryControlStore.State.BLOCKED&&i.chainComplete,"stage blocks otherwise valid chain");
                check(i.latest.generation==0&&i.stages.size()==1,"stage not adopted");check(i.stages.get(0).state==HistoryControlStore.StageState.PROVISIONAL,"next stage descriptor");
                check(i.actualBytes==registration().length+next.length&&i.actualEntries==2,"stage fully accounted");});
            for(int scenario=0;scenario<7;scenario++){
                Fixture bad=new Fixture(true);bad.registered();
                if(scenario==0)bad.put(stage,Arrays.copyOf(next,20));
                if(scenario==1)bad.put("mystery.stage",next);
                if(scenario==2)bad.put(HistoryControlStore.stageFilename(HistoryControlStore.policyFilename(2)),policy(2,hash(registration())));
                if(scenario==3){bad.put(HistoryControlStore.policyFilename(1),next);bad.put(stage,next);}
                if(scenario==4){bad.put(stage,next);bad.put(HistoryControlStore.stageFilename(HistoryControlStore.policyFilename(2)),policy(2,hash(next)));}
                if(scenario==5)bad.put(stage,policy(1,hash(new byte[]{1})));
                if(scenario==6)bad.put(stage,HistoryControlStore.encodePolicy(UUID.randomUUID(),1,hash(registration()),QUOTA,20));
                blocked(bad,"invalid stage "+scenario);for(HistoryControlStore.StageDescriptor s:bad.inspect().stages)check(s.state!=HistoryControlStore.StageState.PROVISIONAL,"invalid retained stage not retryable");
            }
            Fixture reg=new Fixture(true);reg.put("registration.control.stage",registration());blocked(reg,"registration stage retained");
            check(reg.inspect().registration==null&&reg.inspect().latest==null,"registration stage no authority");
            check(reg.inspect().stages.get(0).state==HistoryControlStore.StageState.PROVISIONAL,"complete registration stage inspection");
            Files.delete(reg.parent.resolve("index"));blocked(reg,"registration stage requires complete existing physical roots");
            check(reg.inspect().stages.get(0).state==HistoryControlStore.StageState.INVALID,"registration stage missing physical role not retryable");
        });
        test("roles physically existing canonical and control root alias rejected",()->{
            Fixture f=new Fixture(true);f.registered();Files.delete(f.parent.resolve("catalog"));blocked(f,"missing physical role");
            Fixture alias=new Fixture(true);byte[][] n=names();n[0]="history_control".getBytes(StandardCharsets.UTF_8);alias.put("registration.control",rawRegistration(new int[]{1,2,3,4},n,0,BOOTSTRAP,QUOTA,20));blocked(alias,"control aliases role");
            Fixture links=new Fixture(true);links.registered();Files.delete(links.parent.resolve("index"));Files.createSymbolicLink(links.parent.resolve("index"),links.parent.resolve("identity"));blocked(links,"role symlink");
            Fixture file=new Fixture(true);file.registered();Files.delete(file.parent.resolve("identity"));Files.write(file.parent.resolve("identity"),new byte[]{1});blocked(file,"role regular file");
            Path parent=Files.createTempDirectory("control-parent-").toRealPath(),link=parent.resolve("link");Files.createSymbolicLink(link,f.parent);
            reject(()->new HistoryControlStore(link.toFile()),"symlink trusted parent");reject(()->new HistoryControlStore(new File(f.parent.toFile(),"../"+f.parent.getFileName())),"noncanonical parent");
        });
        test("root and entry symlinks nonregular unknown sanitized",()->{
            Fixture root=new Fixture(false);Files.createSymbolicLink(root.root,root.parent.resolve("index"));blocked(root,"control symlink");
            Fixture entries=new Fixture(true);entries.registered();Files.createSymbolicLink(entries.root.resolve(HistoryControlStore.policyFilename(1)),entries.root.resolve("registration.control"));blocked(entries,"entry symlink");
            Fixture directory=new Fixture(true);directory.registered();Files.createDirectory(directory.root.resolve(HistoryControlStore.policyFilename(1)));blocked(directory,"entry directory");check(!directory.inspect().usageComplete,"nonregular incomplete accounting");
            Fixture unknown=new Fixture(true);unknown.registered();unknown.put("secret-document-uri.txt",new byte[]{7,8});blocked(unknown,"unknown files");
            HistoryControlStore.Inspection i=unknown.inspect();check(i.usageComplete&&i.actualBytes==registration().length+2,"unknown actual bytes counted");
            check(!i.diagnostics.toString().contains("secret-document"),"diagnostic sanitization");for(HistoryControlStore.Entry entry:i.entries)check(entry.filename==null||!entry.filename.contains("secret-document"),"descriptor sanitization");
        });
        test("oversized files lengths and inventories bounded without mutation",()->{
            Fixture f=new Fixture(true);f.registered();f.put(HistoryControlStore.policyFilename(1),new byte[16385]);blocked(f,"oversized record");check(f.inspect().actualBytes==registration().length+16385,"oversized counted");
            Fixture bytes=new Fixture(true);bytes.registered();try(RandomAccessFile out=new RandomAccessFile(bytes.root.resolve("oversized.stage").toFile(),"rw")){out.setLength(2*MIB);}
            blocked(bytes,"one shared control byte budget");check(bytes.inspect().usageComplete&&bytes.inspect().actualBytes==registration().length+2*MIB,"complete actual byte usage even over cap");
            Fixture entries=new Fixture(true);entries.registered();for(int k=1;k<=256;k++)entries.put("unknown-"+k,new byte[0]);
            blocked(entries,"single entry cap");check(!entries.inspect().usageComplete&&entries.inspect().actualEntries==256,"bounded capped inventory not credited complete");
            Fixture max=new Fixture(true);max.registered();byte[] previous=registration();for(int k=1;k<=255;k++){byte[] record=policy(k,hash(previous));max.put(HistoryControlStore.policyFilename(k),record);previous=record;}
            HistoryControlStore.Inspection i=max.inspect();check(i.state==HistoryControlStore.State.VALIDATED&&i.actualEntries==256&&i.latest.generation==255,"exactly cap valid inspection");
        });
        test("large sparse lengths do not allocate according to disk",()->{
            Fixture f=new Fixture(true);f.registered();Path oversized=f.root.resolve(HistoryControlStore.policyFilename(1));
            long length=512*MIB;try(RandomAccessFile out=new RandomAccessFile(oversized.toFile(),"rw")){out.setLength(length);}
            HistoryControlStore.Inspection i=f.inspect();check(i.state==HistoryControlStore.State.BLOCKED&&i.usageComplete,"huge sparse disk entry bounded at 64MiB heap");
            check(i.actualBytes==length+registration().length&&Files.size(oversized)==length,"actual huge length retained");
        });
        System.out.println("History control codec: "+cases+" cases / "+assertions+" assertions PASS (64MiB heap)");
    }
}
