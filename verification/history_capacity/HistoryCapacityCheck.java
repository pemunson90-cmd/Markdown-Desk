package com.example.markdowndesk;
import java.io.*;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.util.*;
public final class HistoryCapacityCheck {
 static int checks; interface Task {void run()throws Exception;}
 static void check(boolean good,String why){checks++;if(!good)throw new AssertionError(why);}
 static void rejects(Task work,String why)throws Exception{try{work.run();throw new AssertionError(why);}catch(IOException expected){checks++;}}
 static class Disk implements ProtectionStore.Durability {
  String fault="";int faultSkip;String callbackEvent="";Task callback;boolean controlOnly=true;
  void event(String name)throws IOException{if(callback!=null&&(callbackEvent.isEmpty()||callbackEvent.equals(name))){Task action=callback;callback=null;try{action.run();}catch(Exception e){throw new IOException(e);}}if(fault.equals(name)&&faultSkip--<=0)throw new IOException("injected "+name);}
  public void syncFile(FileDescriptor fd)throws IOException{event("file");fd.sync();event("after-file");}
  public void move(File a,File b)throws IOException{event("move");Files.move(a.toPath(),b.toPath(),StandardCopyOption.ATOMIC_MOVE);event("after-move");}
  public void syncDirectory(File dir)throws IOException{if(!controlOnly||dir.getName().equals("history_control"))event("dir");try(FileChannel c=FileChannel.open(dir.toPath(),StandardOpenOption.READ)){c.force(true);}if(!controlOnly||dir.getName().equals("history_control"))event("after-dir");}
 }
 static class Bundle {
  final Path parent;final Disk disk;final ProtectionStore store;final HistoryCatalog catalog;final HistoryIdentity identity;final HistoryIndex index;
  Bundle()throws Exception{this(Files.createTempDirectory("capacity-"),new Disk(),ProtectionStore.DEFAULT_QUOTA_BYTES);}
  Bundle(Path p,Disk d,long hint)throws Exception{parent=p;disk=d;store=new ProtectionStore(p.resolve("store").toFile(),d,hint);catalog=new HistoryCatalog(p.resolve("catalog").toFile(),store,d);identity=new HistoryIdentity(p.resolve("identity").toFile(),d);index=new HistoryIndex(store,catalog,identity,p.resolve("index").toFile(),d);}
  HistoryCapacity register()throws IOException{return HistoryCapacity.register(store,catalog,identity,index,new HistoryPolicy(64L*1024*1024,20),disk);}
  HistoryCapacity open()throws IOException{return HistoryCapacity.open(store,catalog,identity,index,disk);}
 }
 static Map<String,byte[]> capture(Path p)throws IOException{Map<String,byte[]> m=new TreeMap<>();try(java.util.stream.Stream<Path> paths=Files.walk(p)){for(Path f:(Iterable<Path>)paths.filter(x->Files.isRegularFile(x,LinkOption.NOFOLLOW_LINKS))::iterator){java.security.MessageDigest digest=HistoryStorage.digest();try(InputStream in=Files.newInputStream(f)){byte[] block=new byte[8192];int n;while((n=in.read(block))!=-1)digest.update(block,0,n);}m.put(p.relativize(f).toString(),digest.digest());}}return m;}
 static boolean same(Map<String,byte[]> a,Map<String,byte[]> b){if(!a.keySet().equals(b.keySet()))return false;for(String k:a.keySet())if(!Arrays.equals(a.get(k),b.get(k)))return false;return true;}
 static void policy()throws Exception{
  Bundle b=new Bundle();b.store.putSnapshot("doc",null,"title",1,new byte[]{1,2},"attempted");HistoryCapacity c=b.register();check(Files.isRegularFile(b.parent.resolve("history_enrollment.control")),"durable parent enrollment missing");check(c.policy().generation==0&&c.policy().quotaBytes==64L*1024*1024,"initial effective policy");Map<String,byte[]> before=capture(b.parent);check(b.register().policy().controlRecordHash.equals(c.policy().controlRecordHash)&&same(before,capture(b.parent)),"idempotent registration");
  rejects(()->HistoryCapacity.register(b.store,b.catalog,b.identity,b.index,new HistoryPolicy(128L*1024*1024,20),b.disk),"initial configuration conflict");check(c.setPolicy(new HistoryPolicy(128L*1024*1024,5)).generation==1,"first policy generation");check(c.policy().automaticCount==5&&c.inspect().controlActualEntries==3,"control usage");Bundle reopen=new Bundle(b.parent,new Disk(),ProtectionStore.DEFAULT_QUOTA_BYTES);HistoryCapacity opened=reopen.open();check(opened.policy().generation==1&&opened.policy().quotaBytes==128L*1024*1024,"explicit open authority");rejects(()->new Bundle(b.parent,new Disk(),1),"conflicting bootstrap hint");
  for(String fault:new String[]{"file","after-file","move","after-move","dir","after-dir"}){
   Bundle f=new Bundle();HistoryCapacity controller=f.register();f.disk.fault=fault;rejects(()->controller.setPolicy(new HistoryPolicy(128L*1024*1024,5)),"policy fault accepted "+fault);check(controller.inspect().mutationBlocked,"uncertain publication not blocked "+fault);f.disk.fault="";Map<String,byte[]> retained=capture(f.parent);check(controller.reconcile().generation==1,"reconcile actual disk policy "+fault);check(controller.reconcile().generation==1,"reconcile allocated second generation "+fault);check(controller.inspect().usageComplete&&!controller.inspect().mutationBlocked,"reconcile authority unavailable");check(capture(f.parent).size()<=retained.size(),"reconcile grew files");
  }
  Bundle partial=new Bundle();HistoryCapacity pc=partial.register();Path stage=partial.parent.resolve("history_control/policy-0000000000000001.control.stage");Files.write(stage,new byte[]{1});Map<String,byte[]> bytes=capture(partial.parent);rejects(pc::reconcile,"partial policy stage reconciled");check(same(bytes,capture(partial.parent)),"partial stage rewritten");
  Bundle empty=new Bundle();Files.createDirectory(empty.parent.resolve("history_control"));Bundle recovery=new Bundle(empty.parent,new Disk(),ProtectionStore.DEFAULT_QUOTA_BYTES);rejects(recovery::open,"empty control fell back");
  Bundle regFail=new Bundle();regFail.disk.fault="file";regFail.disk.faultSkip=1;rejects(regFail::register,"registration fault accepted");regFail.disk.fault="";check(regFail.open().policy().generation==0,"registration stage reconcile failed");
 }
 static void fill(Bundle b,long additional)throws IOException{
  while(additional>0){long size=Math.min(additional,ProtectionStore.MAX_DOCUMENT_BYTES);Path stage=b.parent.resolve("store").resolve(UUID.randomUUID()+".stage");try(RandomAccessFile file=new RandomAccessFile(stage.toFile(),"rw")){file.setLength(size);}additional-=size;}
 }
 static ProtectionStore.Snapshot put(Bundle b,String role)throws IOException{return b.store.putSnapshot("doc","uri","title",1,new byte[]{1,2},role);}
 static void admission()throws Exception{
  Bundle blocked=new Bundle();Files.createDirectory(blocked.parent.resolve("history_control"));Map<String,byte[]> before=capture(blocked.parent);rejects(()->put(blocked,"attempted"),"empty control legacy mutation bypass");check(same(before,capture(blocked.parent)),"blocked write created stage");
  Bundle b=new Bundle();ProtectionStore.Snapshot original=put(b,"original"),edit=put(b,"attempted");ProtectionStore.Transaction t=b.store.prepare(original,edit,original.hash);String history=b.identity.resolve("uri","doc");b.store.complete(t);b.index.recordVerified(history,t.id);HistoryCapacity c=b.register();
  ProtectionStore tiny=new ProtectionStore(b.parent.resolve("store").toFile(),b.disk,ProtectionStore.DEFAULT_QUOTA_BYTES);tiny.putSnapshot("doc",null,null,1,new byte[]{3},"attempted");ProtectionStore.Transaction second=tiny.prepare(original,edit,original.hash);tiny.complete(second);check(c.inspect().dataActualBytes>0,"shared authority not available");
  for(Task nested:new Task[]{()->put(b,"attempted"),()->b.store.prepare(original,edit,original.hash),()->b.store.complete(t),()->b.catalog.name(edit.id,"nested"),()->b.identity.resolve("new-uri","tab"),()->b.identity.forkAfterVerifiedSaveAs(history,"dest-uri"),()->b.index.recordVerified(history,t.id),()->c.setPolicy(new HistoryPolicy(128L*1024*1024,5)),()->new ProtectionStore(b.parent.resolve("new-root").toFile(),b.disk,ProtectionStore.DEFAULT_QUOTA_BYTES)}){
   b.disk.callback=()->{Map<String,byte[]> current=capture(b.parent);rejects(nested,"nested mutation accepted");check(same(current,capture(b.parent)),"nested mutation changed files");check(b.store.snapshot(edit.id).byteCount==2,"read-only nested validation rejected");};put(b,"attempted");
  }
  Path optional=b.parent.resolve("catalog").resolve(UUID.randomUUID()+".stage");Files.write(optional,new byte[]{9});check(!c.inspect().mutationBlocked,"known optional stage blocks native capacity");put(b,"attempted");check(new HistoryGraph(b.store,b.catalog,b.identity,b.index,null).scan().pruneBlocked,"optional stage stopped blocking pruning");Files.delete(optional);
  long available=c.policy().quotaBytes-c.inspect().dataChargedBytes;fill(b,available);check(!c.inspect().mutationBlocked&&c.inspect().dataChargedBytes==c.policy().quotaBytes,"exact shared charge");before=capture(b.parent);Map<String,byte[]> full=before;rejects(()->put(b,"attempted"),"aggregate full quota bypass");rejects(()->b.catalog.name(edit.id,"extra"),"catalog bypass");rejects(()->b.identity.resolve("other-uri","tab"),"identity bypass");check(same(full,capture(b.parent)),"rejected admission created files");
  Path unknown=b.parent.resolve("index/unknown");Files.write(unknown,new byte[]{1});check(c.inspect().mutationBlocked,"unknown metadata grants capacity");Files.delete(unknown);
 }
 static void integration()throws Exception{
  Bundle b=new Bundle();ProtectionStore.Snapshot original=put(b,"original"),edit=put(b,"attempted");
  ProtectionStore foreignStore=new ProtectionStore(b.parent.resolve("foreign-store").toFile(),b.disk,ProtectionStore.DEFAULT_QUOTA_BYTES);
  HistoryCatalog foreignCatalog=new HistoryCatalog(b.parent.resolve("foreign-catalog").toFile(),foreignStore,b.disk);HistoryIdentity foreignIdentity=new HistoryIdentity(b.parent.resolve("foreign-identity").toFile(),b.disk);HistoryIndex foreignIndex=new HistoryIndex(foreignStore,foreignCatalog,foreignIdentity,b.parent.resolve("foreign-index").toFile(),b.disk);
  HistoryCapacity c=b.register();c.setPolicy(new HistoryPolicy(128L*1024*1024,5));
  HistoryGraph.Graph foreign=new HistoryGraph(foreignStore,foreignCatalog,foreignIdentity,foreignIndex,null).scan();check(foreign.effectivePolicy==null&&foreign.pruneBlocked,"foreign graph exposes registered authority");
  HistoryGraph graph=new HistoryGraph(b.store,b.catalog,b.identity,b.index,new HistoryPolicy(1024L*1024*1024,50));
  HistoryGraph.Graph first=graph.scan();check(first.effectivePolicy!=null&&first.effectivePolicy.quotaBytes==128L*1024*1024&&first.effectivePolicy.automaticCount==5,"desired profile replaced authority");
  check(first.desiredProfile.automaticCount==50&&first.controlActualEntries==3&&first.controlActualBytes==c.inspect().controlActualBytes,"graph omitted control accounting");
  ProtectionStore other=new ProtectionStore(b.parent.resolve("store").toFile(),b.disk,ProtectionStore.DEFAULT_QUOTA_BYTES);
  ProtectionStore.Transaction tx=other.prepare(original,edit,original.hash),late=other.prepare(original,edit,original.hash);fill(b,64L*1024*1024+1-c.inspect().dataChargedBytes);
  c.setPolicy(new HistoryPolicy(64L*1024*1024,0));check(c.inspect().overQuota,"lower quota rejected existing data");long charged=c.inspect().dataChargedBytes;
  Map<String,byte[]> before=capture(b.parent);rejects(()->put(b,"attempted"),"over-quota snapshot bypass");rejects(()->b.store.prepare(original,edit,original.hash),"over-quota preparation bypass");rejects(()->b.catalog.name(edit.id,"extra"),"over-quota catalog bypass");rejects(()->b.identity.resolve("new","tab"),"over-quota identity bypass");check(same(before,capture(b.parent)),"over-quota reject changed files");
  other.complete(tx);check(c.inspect().dataChargedBytes==charged,"completion grew charged usage");
  HistoryGraph.Graph second=graph.scan();check(second.effectivePolicy.generation==2&&second.effectivePolicy.automaticCount==0&&!second.fingerprint.equals(first.fingerprint),"graph policy generation unbound");
  Bundle fresh=new Bundle(b.parent,new Disk(),ProtectionStore.DEFAULT_QUOTA_BYTES);HistoryCapacity replacement=fresh.open();check(replacement.policy().generation==2,"open reset authority");b.store.complete(late);check(replacement.inspect().dataChargedBytes==charged,"old handle conversion under replacement authority grew usage");b.store.complete(tx);check(c.inspect().mutationBlocked,"superseded controller still authoritative");
 }
 static void raceAndLimits()throws Exception{
  Bundle b=new Bundle();HistoryCapacity c=b.register();long initial=c.inspect().dataChargedBytes;put(b,"attempted");long cost=c.inspect().dataChargedBytes-initial;
  fill(b,c.policy().quotaBytes-c.inspect().dataChargedBytes-cost);
  ProtectionStore other=new ProtectionStore(b.parent.resolve("store").toFile(),b.disk,ProtectionStore.DEFAULT_QUOTA_BYTES);
  java.util.concurrent.CountDownLatch ready=new java.util.concurrent.CountDownLatch(2),fire=new java.util.concurrent.CountDownLatch(1);
  java.util.concurrent.atomic.AtomicInteger winners=new java.util.concurrent.atomic.AtomicInteger(),failures=new java.util.concurrent.atomic.AtomicInteger();
  List<Throwable> unexpected=Collections.synchronizedList(new ArrayList<>());List<Thread> threads=new ArrayList<>();
  for(ProtectionStore handle:new ProtectionStore[]{b.store,other}){Thread worker=new Thread(()->{ready.countDown();try{fire.await();handle.putSnapshot("doc","uri","title",1,new byte[]{1,2},"attempted");winners.incrementAndGet();}catch(IOException exhausted){failures.incrementAndGet();}catch(Throwable failure){unexpected.add(failure);}});threads.add(worker);worker.start();}
  ready.await();fire.countDown();for(Thread thread:threads)thread.join(10000);check(threads.stream().noneMatch(Thread::isAlive)&&unexpected.isEmpty(),"race did not finish");check(winners.get()==1&&failures.get()==1,"last capacity race had multiple/no winners");check(c.inspect().dataChargedBytes==c.policy().quotaBytes,"last capacity charge incorrect");
  Bundle nativeFull=new Bundle();HistoryCapacity nc=nativeFull.register();for(int i=0;i<HistoryMetadataIO.MAX_ENTRIES;i++)Files.createFile(nativeFull.parent.resolve("identity").resolve(UUID.randomUUID()+".stage"));
  check(!nc.inspect().mutationBlocked,"full known optional stage root hides accounting");Map<String,byte[]> before=capture(nativeFull.parent);rejects(()->nativeFull.identity.resolve("new","tab"),"metadata entry cap bypass");check(same(before,capture(nativeFull.parent)),"native rejected write created stage");put(nativeFull,"attempted");
  Bundle bytesFull=new Bundle();HistoryCapacity bc=bytesFull.register();for(int i=0;i<HistoryMetadataIO.MAX_BYTES/HistoryMetadataIO.MAX_RECORD;i++)try(RandomAccessFile stage=new RandomAccessFile(bytesFull.parent.resolve("catalog").resolve(UUID.randomUUID()+".stage").toFile(),"rw")){stage.setLength(HistoryMetadataIO.MAX_RECORD);}
  ProtectionStore.Snapshot edit=put(bytesFull,"attempted");before=capture(bytesFull.parent);rejects(()->bytesFull.catalog.name(edit.id,"full"),"metadata native byte cap bypass");check(same(before,capture(bytesFull.parent)),"native bytes reject created stage");check(!bc.inspect().mutationBlocked,"optional native limit prevents mandatory protection");
 }
 static void completionFailures()throws Exception{
  Bundle b=new Bundle();ProtectionStore.Snapshot original=put(b,"original"),edit=put(b,"attempted");ProtectionStore.Transaction tx=b.store.prepare(original,edit,original.hash);HistoryCapacity c=b.register();c.setPolicy(new HistoryPolicy(128L*1024*1024,20));fill(b,64L*1024*1024+1-c.inspect().dataChargedBytes);c.setPolicy(new HistoryPolicy(64L*1024*1024,20));
  long charged=c.inspect().dataChargedBytes;b.disk.fault="move";rejects(()->b.store.complete(tx),"completion move fault missing");b.disk.fault="";check(c.inspect().dataChargedBytes==charged,"full completion stage duplicated reservation");
  Path stage=b.parent.resolve("store").resolve(tx.id+".completion-stage");byte[] exact=Files.readAllBytes(stage);b.store.complete(tx);check(c.inspect().dataChargedBytes==charged&&!Files.exists(stage),"full matching stage retry grew usage");check(Arrays.equals(exact,Files.readAllBytes(b.parent.resolve("store").resolve(tx.id+".complete"))),"retry rewrote completion envelope");
  for(String fault:new String[]{"partial","changed","source","reservation","uncertain"}){
   Bundle f=new Bundle();ProtectionStore.Snapshot o=put(f,"original"),e=put(f,"attempted");ProtectionStore.Transaction t=f.store.prepare(o,e,o.hash);HistoryCapacity fc=f.register();
   if(fault.equals("partial")||fault.equals("changed")){f.disk.fault="move";rejects(()->f.store.complete(t),"stage injection failed");f.disk.fault="";Path p=f.parent.resolve("store").resolve(t.id+".completion-stage");byte[] saved=Files.readAllBytes(p);if(fault.equals("partial"))saved=Arrays.copyOf(saved,1);else saved[saved.length-1]^=1;Files.write(p,saved);}
   if(fault.equals("source")){Path p=f.parent.resolve("store").resolve(e.id+".snapshot");byte[] damaged=Files.readAllBytes(p);damaged[damaged.length-1]^=1;Files.write(p,damaged);}
   if(fault.equals("reservation"))Files.delete(f.parent.resolve("store").resolve(t.id+".transaction"));
   if(fault.equals("uncertain")){f.disk.fault="move";rejects(()->fc.setPolicy(new HistoryPolicy(128L*1024*1024,5)),"policy uncertainty injection failed");f.disk.fault="";}
   Map<String,byte[]> retained=capture(f.parent);rejects(()->f.store.complete(t),"invalid conversion accepted "+fault);check(same(retained,capture(f.parent)),"invalid conversion modified bytes "+fault);
  }
 }
 static void controlLimits()throws Exception{
  Bundle b=new Bundle();ProtectionStore.Snapshot o=put(b,"original"),e=put(b,"attempted");ProtectionStore.Transaction tx=b.store.prepare(o,e,o.hash);HistoryCapacity c=b.register();
  HistoryControlStore.Inspection inspection=new HistoryControlStore(b.parent.toFile()).inspect();String previous=inspection.latest.envelopeHash;
  for(int generation=1;generation<=254;generation++){byte[] record=HistoryControlStore.encodePolicy(inspection.registration.bundleId,generation,previous,64L*1024*1024,20);Files.write(b.parent.resolve("history_control").resolve(HistoryControlStore.policyFilename(generation)),record);previous=HistoryControlStore.decode(record).envelopeHash;}
  check(c.reconcile().generation==254&&c.inspect().controlActualEntries==256,"control allowance excludes enrollment");Map<String,byte[]> retained=capture(b.parent);rejects(()->c.setPolicy(new HistoryPolicy(128L*1024*1024,5)),"full control allowance admitted policy");check(same(retained,capture(b.parent)),"control pre-stage rejection changed disk");b.store.complete(tx);put(b,"attempted");check(!c.inspect().mutationBlocked,"full control allowance blocks data");
  Bundle binding=new Bundle();HistoryCapacity bound=binding.register();Path marker=binding.parent.resolve("history_enrollment.control");byte[] enrolled=Files.readAllBytes(marker);binding.disk.callbackEvent="dir";binding.disk.callback=()->Files.delete(marker);rejects(bound::reconcile,"reconcile acknowledged missing final enrollment");Files.write(marker,enrolled);binding.disk.callbackEvent="";check(bound.reconcile().generation==0,"binding reconciliation did not recover");
  Bundle malformed=new Bundle();HistoryCapacity mc=malformed.register();Files.write(malformed.parent.resolve("history_enrollment.control"),new byte[]{1});retained=capture(malformed.parent);rejects(()->put(malformed,"attempted"),"malformed marker live handle bypass");check(same(retained,capture(malformed.parent)),"malformed marker changed data");check(mc.inspect().mutationBlocked,"malformed marker authority unblocked");
 }
 static void writerRoles()throws Exception{
  Bundle b=new Bundle();ProtectionStore wrongStore=new ProtectionStore(b.parent.resolve("catalog").toFile(),b.disk,ProtectionStore.DEFAULT_QUOTA_BYTES);HistoryIdentity wrongIdentity=new HistoryIdentity(b.parent.resolve("index").toFile(),b.disk);
  ProtectionStore foreign=new ProtectionStore(b.parent.resolve("foreign-store").toFile(),b.disk,ProtectionStore.DEFAULT_QUOTA_BYTES);ProtectionStore.Snapshot o=foreign.putSnapshot("foreign","uri","title",1,new byte[]{1},"original"),e=foreign.putSnapshot("foreign","uri","title",1,new byte[]{2},"attempted");ProtectionStore.Transaction tx=foreign.prepare(o,e,o.hash);foreign.complete(tx);
  HistoryCatalog wrongCatalog=new HistoryCatalog(b.parent.resolve("catalog").toFile(),foreign,b.disk);HistoryIdentity foreignIdentity=new HistoryIdentity(b.parent.resolve("foreign-identity").toFile(),b.disk);String history=foreignIdentity.resolve("uri","foreign");HistoryIndex wrongIndex=new HistoryIndex(foreign,wrongCatalog,foreignIdentity,b.parent.resolve("index").toFile(),b.disk);
  HistoryCapacity registered=b.register();Map<String,byte[]> retained=capture(b.parent);
  rejects(()->wrongStore.setQuotaBytes(128L*1024*1024),"wrong-role store changed policy");check(registered.policy().generation==0,"foreign setter changed generation");
  rejects(()->wrongStore.putSnapshot("doc",null,null,1,new byte[]{1},"attempted"),"protection handle writes catalog role");rejects(()->wrongIdentity.resolve("uri","tab"),"identity handle writes index role");rejects(()->wrongCatalog.name(e.id,"foreign"),"catalog accepts foreign store dependency");rejects(()->wrongIndex.recordVerified(history,tx.id),"index accepts foreign dependencies");check(same(retained,capture(b.parent)),"foreign writer changed registered bytes");
 }
 static void optionalDamageAndLegacy()throws Exception{
  Bundle b=new Bundle();ProtectionStore tiny=new ProtectionStore(b.parent.resolve("store").toFile(),b.disk,1);rejects(()->tiny.putSnapshot("doc",null,null,1,new byte[]{1},"attempted"),"legacy small handle inherited large quota");
  ProtectionStore.Snapshot edit=put(b,"attempted");HistoryCatalog.Checkpoint checkpoint=b.catalog.name(edit.id,"pin");HistoryCapacity c=b.register();tiny.putSnapshot("doc",null,null,1,new byte[]{1},"attempted");
  Path record=b.parent.resolve("catalog").resolve(checkpoint.id+".checkpoint");byte[] bytes=Files.readAllBytes(record);bytes[bytes.length-1]^=1;Files.write(record,bytes);
  check(c.inspect().usageComplete&&!c.inspect().mutationBlocked,"optional semantic damage grants incomplete accounting");check(new HistoryGraph(b.store,b.catalog,b.identity,b.index,null).scan().pruneBlocked,"damaged optional semantics permitted pruning");
  Map<String,byte[]> retained=capture(b.parent);rejects(()->b.catalog.name(edit.id,"new"),"damaged catalog accepted own write");check(same(retained,capture(b.parent)),"damaged optional writer changed files");put(b,"attempted");check(c.reconcile().generation==0,"optional semantic damage blocks mandatory reconciliation");
  Path oversized=b.parent.resolve("catalog").resolve(UUID.randomUUID()+".stage");try(RandomAccessFile stage=new RandomAccessFile(oversized.toFile(),"rw")){stage.setLength(HistoryMetadataIO.MAX_BYTES+1);}
  check(c.inspect().usageComplete&&!c.inspect().mutationBlocked,"fully accounted optional native damage blocks mandatory protection");put(b,"attempted");
 }
 public static void main(String[] args)throws Exception{
  String mode=args.length==0?"policy":args[0];
  switch(mode){
   case "policy":policy();break;
   case "admission":writerRoles();admission();raceAndLimits();optionalDamageAndLegacy();break;
   case "integration":integration();completionFailures();controlLimits();break;
   default:throw new IllegalArgumentException("Unknown capacity test group");
  }
  System.out.println("HistoryCapacity "+mode+": "+checks+" checks PASS");
 }
}
