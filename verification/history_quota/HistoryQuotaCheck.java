package com.example.markdowndesk;
import java.io.*;
import java.lang.reflect.*;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.util.*;

public final class HistoryQuotaCheck {
 static final long MIB=1024L*1024, Q=64*MIB;
 static int checks,cases;
 interface Checked {void run()throws Exception;}
 static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
 static void fails(Checked action,String why)throws Exception{try{action.run();}catch(IOException expected){checks++;return;}throw new AssertionError(why);}
 static void test(String name,Checked action)throws Exception{action.run();cases++;System.out.println("PASS "+name);}
 interface DirectoryCheck {void check(File directory)throws IOException;}
 static class Disk implements ProtectionStore.Durability {
  String fail="";boolean moved;List<String> calls=new ArrayList<>();DirectoryCheck directoryCheck;
  public void syncFile(FileDescriptor fd)throws IOException{calls.add("file");if(fail.equals("file"))throw new IOException("file fault");fd.sync();}
  public void move(File from,File to)throws IOException{calls.add("move");if(fail.equals("move"))throw new IOException("move fault");Files.move(from.toPath(),to.toPath(),StandardCopyOption.ATOMIC_MOVE);moved=true;}
  public void syncDirectory(File dir)throws IOException{if(directoryCheck!=null)directoryCheck.check(dir);calls.add("dir");if(fail.equals("dir")&&moved)throw new IOException("directory fault");try(FileChannel c=FileChannel.open(dir.toPath(),StandardOpenOption.READ)){c.force(true);}}
 }
 static final class Fixture {
  final File parent=Files.createTempDirectory("history-quota-").toFile();final Disk disk=new Disk();
  final ProtectionStore store=new ProtectionStore(new File(parent,"store"),disk,Q);
  final HistoryCatalog catalog=new HistoryCatalog(new File(parent,"catalog"),store,disk);
  final HistoryIdentity identity=new HistoryIdentity(new File(parent,"identity"),disk);
  final HistoryIndex index=new HistoryIndex(store,catalog,identity,new File(parent,"index"),disk);
  Fixture()throws Exception{}
  void configure(long quota)throws Exception {
   // Reflection lets the first RED expose the missing behavior as an assertion, not a compiler error.
   try{HistoryPolicy.class.getMethod("configure",ProtectionStore.class,HistoryCatalog.class,HistoryIdentity.class,HistoryIndex.class)
     .invoke(new HistoryPolicy(quota,20),store,catalog,identity,index);}
   catch(NoSuchMethodException missing){throw new AssertionError("Shared four-root configuration API is missing",missing);}
   catch(InvocationTargetException failure){if(failure.getCause() instanceof IOException)throw (IOException)failure.getCause();throw failure;}
  }
  ProtectionStore.Snapshot put(byte[] data)throws IOException{return store.putSnapshot("doc","uri","title",1,data,"attempted");}
 }
 public static void main(String[] args)throws Exception {
  test("registered admission counts preexisting bytes in all four roots",()->{
   Fixture f=new Fixture();for(int i=0;i<7;i++)f.put(new byte[ProtectionStore.MAX_DOCUMENT_BYTES]);
   for(String role:Arrays.asList("catalog","identity","index"))for(int i=0;i<192;i++)Files.write(new File(new File(f.parent,role),UUID.randomUUID()+".stage").toPath(),new byte[16384]);
   f.put(new byte[]{1}); // Legacy constructor-only admission is deliberately unchanged before opt-in.
   f.configure(Q);long before=diskBytes(f.parent);fails(()->f.put(new byte[]{2}),"Shared quota omitted a registered metadata root");
   check(diskBytes(f.parent)==before,"Quota rejection changed retained bytes");
  });
  test("two handles share explicit policy and reject constructor or bundle conflicts",()->{
   Fixture f=new Fixture();ProtectionStore other=new ProtectionStore(new File(f.parent,"store"),f.disk,Q);f.configure(Q);
   setQuota(f.store,128*MIB);check(policy(other).quotaBytes==128*MIB,"Existing handle reset shared policy");
   ProtectionStore reopened=new ProtectionStore(new File(f.parent,"store"),f.disk,Q);check(policy(reopened).quotaBytes==128*MIB,"Reopened handle reset policy");
   fails(()->new ProtectionStore(new File(f.parent,"store"),f.disk,128*MIB),"Conflicting constructor quota accepted");
   fails(()->f.configure(Q),"Conflicting explicit configuration silently replaced policy");
   fails(()->new HistoryIdentity(new File(f.parent,"catalog"),f.disk),"Registered role alias accepted");
  });
  test("policy file or move failure retains previous policy and blocks ordinary writes",()->{
   for(String failure:Arrays.asList("file","move")){
    Fixture f=new Fixture();f.configure(Q);f.disk.fail=failure;f.disk.moved=false;
    fails(()->setQuota(f.store,128*MIB),"Failed policy persistence acknowledged");f.disk.fail="";
    check(policy(f.store).quotaBytes==Q,"Failed policy became effective");
    long retained=diskBytes(f.parent);fails(()->f.put(new byte[]{1}),"Pending policy permits ordinary mutation");
    check(diskBytes(f.parent)==retained,"Blocked write changes retained stage");setQuota(f.store,128*MIB);
    check(policy(f.store).quotaBytes==128*MIB,"Exact policy-stage retry did not commit");
   }
  });
  test("uncertain publication stays blocked until explicit durable recovery",()->{
   Fixture f=new Fixture();f.configure(Q);f.disk.fail="dir";f.disk.moved=false;
   fails(()->setQuota(f.store,128*MIB),"Uncertain directory publication acknowledged");f.disk.fail="";
   check(policy(f.store).quotaBytes==Q,"Uncertain publication silently replaced active policy");
   fails(()->f.put(new byte[]{1}),"Uncertain policy permits ordinary mutation");
   invoke(f.store,"recoverPolicy",new Class<?>[0]);check(policy(f.store).quotaBytes==128*MIB,"Explicit durable policy recovery failed");
  });
  test("lower quota preserves records and admits only exact reservation conversion",()->{
   Fixture f=new Fixture();f.configure(128*MIB);
   ProtectionStore.Snapshot original=f.store.putSnapshot("doc","uri","title",1,new byte[]{1},"original");
   ProtectionStore.Snapshot attempted=f.put(new byte[]{2});ProtectionStore.Transaction transaction=f.store.prepare(original,attempted,original.hash);
   for(int i=0;i<9;i++)f.put(new byte[ProtectionStore.MAX_DOCUMENT_BYTES]);
   Map<String,String> protectedBefore=hashes(new File(f.parent,"store"));setQuota(f.store,Q);
   check(hashes(new File(f.parent,"store")).equals(protectedBefore),"Lower quota rewrote protection records");
   fails(()->f.put(new byte[]{3}),"Over-quota snapshot admitted");
   fails(()->f.catalog.name(attempted.id,"name"),"Over-quota catalog admitted");
   fails(()->f.identity.resolve("other-uri","tab"),"Over-quota identity admitted");
   f.disk.fail="move";fails(()->f.store.complete(transaction),"Injected completion failure ignored");f.disk.fail="";
   long staged=diskBytes(f.parent);f.store.complete(transaction);check(diskBytes(f.parent)==staged,"Completion retry grew charged bytes");
   check(f.store.validate(transaction).verified,"Net-zero completion refused under lowered quota");
   check(hashes(new File(f.parent,"store")).entrySet().containsAll(protectedBefore.entrySet()),"Completion changed prior v1 bytes");
  });
  test("graph uses effective policy and charges the immutable policy ledger",()->{
   Fixture f=new Fixture();f.put(new byte[]{1});HistoryGraph graph=new HistoryGraph(f.store,f.catalog,f.identity,f.index,new HistoryPolicy(Q,20));
   f.configure(128*MIB);HistoryGraph.Graph before=graph.scan();
   check(before.policy.quotaBytes==128*MIB,"Constructor graph descriptor overrides effective policy");
   check(before.actualBytes==diskBytes(f.parent),"Graph omitted policy ledger bytes");
   check(before.records.stream().anyMatch(record->record.rootRole.equals("policy")),"Graph omitted policy proof records");
   HistoryPolicy.Capacity capacity=HistoryPolicy.inspect(f.store);check(capacity.actualBytes==before.actualBytes&&capacity.chargedBytes==before.chargedBytes,"Graph and shared admission disagree");
   setQuota(f.store,Q);check(!graph.scan().fingerprint.equals(before.fingerprint),"Policy change omitted from fingerprint");
   f.disk.fail="file";fails(()->setQuota(f.store,128*MIB),"Policy file failure ignored");f.disk.fail="";
   check(graph.scan().pruneBlocked&&!graph.scan().complete,"Pending policy authorizes graph proof");
  });
  test("corrupt policy blocks writes while native snapshots remain readable after reopen",()->{
   Fixture f=new Fixture();ProtectionStore.Snapshot snapshot=f.put(new byte[]{1,2,3});f.configure(Q);
   File ledger=new File(f.parent,"history_capacity");File committed=Arrays.stream(ledger.listFiles()).filter(file->file.getName().endsWith(".policy")).findFirst().get();
   byte[] bytes=Files.readAllBytes(committed.toPath());bytes[bytes.length-1]^=1;Files.write(committed.toPath(),bytes);
   fails(()->f.put(new byte[]{2}),"Corrupt policy permits mutation");
   ProtectionStore reopened=new ProtectionStore(new File(f.parent,"store"),f.disk,Q);
   check(Arrays.equals(reopened.snapshot(snapshot.id).bytes(),new byte[]{1,2,3}),"Optional policy corruption hides protected recovery");
   fails(()->reopened.putSnapshot("doc","uri","title",1,new byte[]{4},"attempted"),"Reopened store bypassed corrupt policy");
   HistoryGraph graph=new HistoryGraph(f.store,f.catalog,f.identity,f.index,new HistoryPolicy(Q,20));check(graph.scan().pruneBlocked,"Corrupt policy graph proof accepted");
  });
  test("partial policy stage is retained and requires no implicit promotion",()->{
   Fixture f=new Fixture();f.configure(Q);f.disk.fail="file";fails(()->setQuota(f.store,128*MIB),"File failure ignored");f.disk.fail="";
   File ledger=new File(f.parent,"history_capacity");File stage=Arrays.stream(ledger.listFiles()).filter(file->file.getName().endsWith(".policy-stage")).findFirst().get();
   try(RandomAccessFile out=new RandomAccessFile(stage,"rw")){out.setLength(17);}
   Map<String,String> before=hashes(ledger);fails(()->setQuota(f.store,128*MIB),"Partial policy-stage retry accepted");
   fails(()->f.store.recoverPolicy(),"Recovery promoted partial policy stage");check(policy(f.store).quotaBytes==Q&&before.equals(hashes(ledger)),"Partial stage changed or became policy");
  });
  test("preconfiguration alternate graph handles cannot omit registered roots",()->{
   Fixture f=new Fixture();HistoryCatalog alternateCatalog=new HistoryCatalog(new File(f.parent,"alternate_catalog"),f.store,f.disk);
   HistoryIndex alternateIndex=new HistoryIndex(f.store,alternateCatalog,f.identity,new File(f.parent,"alternate_index"),f.disk);
   HistoryGraph alternate=new HistoryGraph(f.store,alternateCatalog,f.identity,alternateIndex,new HistoryPolicy(Q,20));
   f.configure(Q);check(alternate.scan().pruneBlocked&&!alternate.scan().complete,"Old alternate bundle omitted charged registered roots");
  });
  test("all native metadata writers share policy and zero automatic count retains protection",()->{
   Fixture f=new Fixture();f.configure(128*MIB);String history=f.identity.resolve("uri","tab");
   ProtectionStore.Snapshot original=f.store.putSnapshot("doc","uri","title",1,new byte[]{1},"original");
   ProtectionStore.Snapshot attempted=f.put(new byte[]{2});ProtectionStore.Transaction transaction=f.store.prepare(original,attempted,original.hash);f.store.complete(transaction);
   for(int i=0;i<9;i++)f.put(new byte[ProtectionStore.MAX_DOCUMENT_BYTES]);new HistoryPolicy(Q,0).apply(f.store);
   fails(()->f.index.recordVerified(history,transaction.id),"Ownership metadata bypasses shared quota");
   check(HistoryPolicy.current(f.store).automaticCount==0&&f.store.validate(transaction).verified,"Automatic OFF changes mandatory protection");
   HistoryPolicy.Capacity capacity=HistoryPolicy.inspect(f.store);check(capacity.overQuota&&capacity.actualBytes==diskBytes(f.parent),"Reduced quota accounting lost bytes");
  });
  test("barrier driven two-handle admission cannot spend the last space twice",()->{
   Fixture f=new Fixture();ProtectionStore.Snapshot sample=f.put(new byte[]{1});long envelope=new File(new File(f.parent,"store"),sample.id+".snapshot").length();
   ProtectionStore second=new ProtectionStore(new File(f.parent,"store"),f.disk,Q);f.configure(Q);
   long padding=Q-HistoryPolicy.inspect(f.store).chargedBytes-envelope;
   while(padding>0){long part=Math.min(padding,ProtectionStore.MAX_DOCUMENT_BYTES);try(RandomAccessFile out=new RandomAccessFile(new File(new File(f.parent,"store"),UUID.randomUUID()+".stage"),"rw")){out.setLength(part);}padding-=part;}
   java.util.concurrent.CyclicBarrier barrier=new java.util.concurrent.CyclicBarrier(2);java.util.concurrent.ExecutorService pool=java.util.concurrent.Executors.newFixedThreadPool(2);
   try{
    java.util.List<java.util.concurrent.Future<Boolean>> results=new ArrayList<>();
    for(ProtectionStore store:Arrays.asList(f.store,second))results.add(pool.submit(()->{barrier.await();try{store.putSnapshot("doc","uri","title",1,new byte[]{2},"attempted");return true;}catch(IOException full){return false;}}));
    int admitted=0;for(java.util.concurrent.Future<Boolean> result:results)if(result.get(10,java.util.concurrent.TimeUnit.SECONDS))admitted++;
    check(admitted==1&&HistoryPolicy.inspect(f.store).chargedBytes==Q,"Concurrent handles overspent capacity");
   }finally{pool.shutdownNow();}
  });
  test("name index snapshot and scan contention shares one parent monitor",()->{
   Fixture f=new Fixture();f.configure(Q);String history=f.identity.resolve("uri","tab");
   ProtectionStore.Snapshot original=f.store.putSnapshot("doc","uri","title",1,new byte[]{1},"original");ProtectionStore.Snapshot attempted=f.put(new byte[]{2});
   ProtectionStore.Transaction transaction=f.store.prepare(original,attempted,original.hash);f.store.complete(transaction);
   HistoryGraph graph=new HistoryGraph(f.store,f.catalog,f.identity,f.index,new HistoryPolicy(Q,20));
   java.util.concurrent.CyclicBarrier barrier=new java.util.concurrent.CyclicBarrier(4);java.util.concurrent.ExecutorService pool=java.util.concurrent.Executors.newFixedThreadPool(4);
   try{
    java.util.List<java.util.concurrent.Future<?>> work=new ArrayList<>();
    work.add(pool.submit(()->{barrier.await();f.catalog.name(attempted.id,"named");return null;}));
    work.add(pool.submit(()->{barrier.await();f.index.recordVerified(history,transaction.id);return null;}));
    work.add(pool.submit(()->{barrier.await();f.put(new byte[]{3});return null;}));
    work.add(pool.submit(()->{barrier.await();check(graph.scan().complete,"Concurrent graph incomplete");return null;}));
    for(java.util.concurrent.Future<?> result:work)result.get(10,java.util.concurrent.TimeUnit.SECONDS);
    check(graph.scan().complete&&f.catalog.all().size()==1,"Contention corrupted publication");
   }finally{pool.shutdownNow();}
  });
  test("registered metadata caps and unknown entries fail before native mutation",()->{
   Fixture f=new Fixture();f.configure(128*MIB);File metadata=new File(f.parent,"identity");
   for(int i=0;i<512;i++)try(RandomAccessFile out=new RandomAccessFile(new File(metadata,UUID.randomUUID()+".stage"),"rw")){out.setLength(16384);}
   File extra=new File(metadata,UUID.randomUUID()+".stage");Files.write(extra.toPath(),new byte[]{1});long before=diskBytes(f.parent);
   fails(()->f.put(new byte[]{2}),"Metadata root cap bypassed by larger global quota");check(diskBytes(f.parent)==before,"Cap failure mutated bytes");
   Files.delete(extra.toPath());File unknown=new File(metadata,"unknown.bin");Files.write(unknown.toPath(),new byte[]{1});
   fails(()->f.put(new byte[]{2}),"Unknown registered entry permits admission");
  });
  test("registration invalidates source proof before its first directory sync callback",()->{
   Fixture f=new Fixture();f.put(new byte[]{1});int[] rejected={0};
   try{
    fails(()->f.store.withValidatedSources(sources->{
     f.disk.directoryCheck=directory->{try{sources.require(f.store);throw new AssertionError("Stale source proof survived policy mutation callback");}catch(IOException expired){rejected[0]++;}};
     new HistoryPolicy(Q,20).configure(f.store,f.catalog,f.identity,f.index);return null;
    }),"Outer operation accepted expired source proof");
   }finally{f.disk.directoryCheck=null;}
   check(rejected[0]>=3&&HistoryPolicy.current(f.store).quotaBytes==Q,"Registration proof invalidation not observed at directory boundaries");
  });
  test("policy publication callbacks cannot admit writes before durable acknowledgement",()->{
   Fixture f=new Fixture();f.configure(Q);int[] blocked={0};
   f.disk.directoryCheck=directory->{
    if(directory.getName().equals("history_capacity")){
     check(HistoryPolicy.current(f.store).quotaBytes==Q,"Unacknowledged policy became active in sync callback");
     try{f.put(new byte[]{1});throw new AssertionError("Policy sync callback admitted ordinary mutation");}catch(IOException expected){blocked[0]++;}
    }
   };
   try{setQuota(f.store,128*MIB);}finally{f.disk.directoryCheck=null;}
   check(blocked[0]==1&&HistoryPolicy.current(f.store).quotaBytes==128*MIB,"Durable policy acknowledgement incorrect");
  });
  test("initial registration retries parent durability after a retained directory",()->{
   Fixture f=new Fixture();int[] parentSyncs={0};
   f.disk.directoryCheck=directory->{if(directory.equals(f.parent)){parentSyncs[0]++;throw new IOException("Parent sync fault");}};
   fails(()->f.configure(Q),"Initial parent-sync failure acknowledged");
   fails(()->f.configure(Q),"Retry acknowledged without proving parent directory durable");
   check(parentSyncs[0]==2,"Retry skipped parent directory sync");
   f.disk.directoryCheck=null;f.configure(Q);check(HistoryPolicy.current(f.store).quotaBytes==Q,"Initial registration retry failed");
  });
  test("hostile policy entry matrix blocks admission and preserves every file",()->{
   for(String kind:Arrays.asList("symlink","directory","unknown","duplicate","gap","chain","oversized-stage","changed-stage")){
    Fixture f=new Fixture();f.put(new byte[]{1,2,3});f.configure(Q);File ledger=new File(f.parent,"history_capacity");
    File first=new File(ledger,policyFilename(0,".policy")),next=new File(ledger,policyFilename(1,".policy"));
    if(kind.equals("symlink"))Files.createSymbolicLink(next.toPath(),first.toPath());
    if(kind.equals("directory"))Files.createDirectory(next.toPath());
    if(kind.equals("unknown"))Files.write(new File(ledger,"unknown.record").toPath(),new byte[]{1});
    if(kind.equals("duplicate"))Files.copy(first.toPath(),new File(ledger,policyFilename(0,".policy-stage")).toPath());
    if(kind.equals("gap"))Files.move(first.toPath(),next.toPath());
    if(kind.equals("chain"))Files.write(next.toPath(),policyRecord(1,"0".repeat(64),new String[]{"store","catalog","identity","index"},2*Q));
    if(kind.equals("oversized-stage"))Files.write(new File(ledger,policyFilename(1,".policy-stage")).toPath(),new byte[4097]);
    if(kind.equals("changed-stage")){
     String previous=HistoryStorage.hex(HistoryStorage.digest().digest(Files.readAllBytes(first.toPath())));
     byte[] record=policyRecord(1,previous,new String[]{"store","catalog","identity","index"},2*Q);record[record.length-1]^=1;
     Files.write(new File(ledger,policyFilename(1,".policy-stage")).toPath(),record);
     fails(()->setQuota(f.store,2*Q),"Changed policy stage was promoted");
    }
    Map<String,String> nativeBefore=hashes(new File(f.parent,"store")),policyBefore=footprint(ledger);
    fails(()->f.put(new byte[]{4}),kind+" policy permits admission");
    HistoryGraph graph=new HistoryGraph(f.store,f.catalog,f.identity,f.index,new HistoryPolicy(Q,20));check(graph.scan().pruneBlocked,kind+" policy permits graph proof");
    check(nativeBefore.equals(hashes(new File(f.parent,"store")))&&policyBefore.equals(footprint(ledger)),kind+" failed admission changed retained records");
   }
  });
  test("policy ledger count and aggregate byte caps fail closed",()->{
   for(boolean entryCap:new boolean[]{true,false}){
    Fixture f=new Fixture();f.configure(Q);File ledger=new File(f.parent,"history_capacity");
    if(entryCap){for(int i=1;i<=256;i++)Files.write(new File(ledger,policyFilename(i,".policy")).toPath(),new byte[0]);}
    else{
     Files.delete(new File(ledger,policyFilename(0,".policy")).toPath());String previous="";
     String[] names={"a".repeat(128),"b".repeat(128),"c".repeat(128),"d".repeat(128)};
     for(int i=0;i<110;i++){byte[] record=policyRecord(i,previous,names,Q);Files.write(new File(ledger,policyFilename(i,".policy")).toPath(),record);previous=HistoryStorage.hex(HistoryStorage.digest().digest(record));}
    }
    Map<String,String> before=footprint(ledger);
    try{f.put(new byte[]{1});throw new AssertionError("Policy cap admitted mutation");}
    catch(IOException expected){check(expected.getMessage().contains(entryCap?"entry limit":"byte limit"),"Wrong policy cap failure: "+expected);}
    check(before.equals(footprint(ledger)),"Policy cap deleted retained records");
   }
  });
  System.out.println("History quota: "+cases+" cases / "+checks+" checks PASS");
 }
 static Object invoke(Object target,String name,Class<?>[] types,Object...args)throws Exception{
  try{return target.getClass().getMethod(name,types).invoke(target,args);}
  catch(NoSuchMethodException missing){throw new AssertionError("Missing shared quota API: "+name,missing);}
  catch(InvocationTargetException failure){if(failure.getCause() instanceof IOException)throw (IOException)failure.getCause();throw failure;}
 }
 static void setQuota(ProtectionStore store,long bytes)throws Exception{invoke(store,"setQuotaBytes",new Class<?>[]{long.class},bytes);}
 static HistoryPolicy policy(ProtectionStore store)throws Exception{
  try{return (HistoryPolicy)HistoryPolicy.class.getMethod("current",ProtectionStore.class).invoke(null,store);}
  catch(NoSuchMethodException missing){throw new AssertionError("Missing persisted policy read API",missing);}
  catch(InvocationTargetException failure){if(failure.getCause() instanceof IOException)throw (IOException)failure.getCause();throw failure;}
 }
 static Map<String,String> hashes(File root)throws Exception{Map<String,String> result=new TreeMap<>();for(File file:root.listFiles()){
  java.security.MessageDigest digest=java.security.MessageDigest.getInstance("SHA-256");try(InputStream in=new FileInputStream(file)){byte[] bytes=new byte[8192];int n;while((n=in.read(bytes))!=-1)digest.update(bytes,0,n);}
  result.put(file.getName(),HistoryStorage.hex(digest.digest()));}return result;}
 static String policyFilename(long sequence,String suffix){return String.format(Locale.ROOT,"%020d",sequence)+suffix;}
 static byte[] policyRecord(long sequence,String previous,String[] names,long quota)throws IOException{
  ByteArrayOutputStream bytes=new ByteArrayOutputStream();try(DataOutputStream out=new DataOutputStream(bytes)){
   out.writeInt(0x4d444851);out.writeInt(1);out.writeLong(sequence);out.writeLong(Q);out.writeLong(quota);out.writeInt(20);out.writeUTF(previous);
   for(String name:names)out.writeUTF(name);out.write(HistoryStorage.digest().digest(bytes.toByteArray()));
  }return bytes.toByteArray();
 }
 static Map<String,String> footprint(File root)throws IOException{
  Map<String,String> result=new TreeMap<>();for(File file:root.listFiles()){
   if(Files.isSymbolicLink(file.toPath()))result.put(file.getName(),"link:"+Files.readSymbolicLink(file.toPath()));
   else if(file.isDirectory())result.put(file.getName(),"directory");
   else result.put(file.getName(),HistoryStorage.hex(HistoryStorage.digest().digest(Files.readAllBytes(file.toPath()))));
  }return result;
 }
 static long diskBytes(File root)throws IOException{try(java.util.stream.Stream<Path> paths=Files.walk(root.toPath())){return paths.filter(Files::isRegularFile).mapToLong(p->{try{return Files.size(p);}catch(IOException e){throw new UncheckedIOException(e);}}).sum();}}
}
