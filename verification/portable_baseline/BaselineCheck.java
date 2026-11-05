package com.example.markdowndesk;

import java.io.*;
import java.lang.reflect.*;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.util.*;

/** Real registered backend and raw filesystem fixtures, no caller-supplied source factories. */
public final class BaselineCheck {
 static int checks;static Method capture;
 interface Work{void run()throws Exception;}
 static void check(boolean yes,String why){checks++;if(!yes)throw new AssertionError(why);}
 static void reject(Work w,String why)throws Exception{try{w.run();throw new AssertionError("Accepted "+why);}catch(IOException expected){checks++;}}
 static Object call(Object target,String name,Class<?>[] types,Object...args)throws Exception{
  try{Method m=target.getClass().getDeclaredMethod(name,types);m.setAccessible(true);return m.invoke(target,args);}catch(InvocationTargetException e){if(e.getCause() instanceof Exception)throw (Exception)e.getCause();throw (Error)e.getCause();}
 }
 static final class Disk implements ProtectionStore.Durability {
  public void syncFile(FileDescriptor fd)throws IOException{fd.sync();}
  public void syncDirectory(File dir)throws IOException{try(FileChannel c=FileChannel.open(dir.toPath(),StandardOpenOption.READ)){c.force(true);}}
  public void move(File a,File b)throws IOException{Files.move(a.toPath(),b.toPath(),StandardCopyOption.ATOMIC_MOVE);}
 }
 static final class F implements AutoCloseable {
  final Path parent=Files.createTempDirectory("portable-baseline-");final Disk disk=new Disk();
  final ProtectionStore store=new ProtectionStore(parent.resolve("store").toFile(),disk,64L<<20);
  final HistoryCatalog catalog=new HistoryCatalog(parent.resolve("catalog").toFile(),store,disk);
  final HistoryIdentity identity=new HistoryIdentity(parent.resolve("identity").toFile(),disk);
  final HistoryIndex index=new HistoryIndex(store,catalog,identity,parent.resolve("index").toFile(),disk);
  final HistoryCapacity capacity=HistoryCapacity.register(store,catalog,identity,index,new HistoryPolicy(64L<<20,20),disk);
  final HistoryStorage.Budget budget=new HistoryStorage.Budget(HistoryStorage.METADATA_BUDGET);
  final UUID prep=UUID.randomUUID();final PreparationSources owner=PreparationSources.forPreparation(store.storageRoot(),prep,budget,PreparationSources.newProducer());
  F()throws Exception{}
  void locked(Work w)throws Exception{synchronized(HistoryStorage.monitorFor(store.storageRoot())){try(HistoryStorage.MutationScope lease=HistoryStorage.administration(store.storageRoot())){w.run();}}}
  HistoryControlStore.Inspection control()throws IOException{return new HistoryControlStore(parent.toFile()).inspect();}
  Object capture(HistoryControlStore.Inspection c,PrivateOperationStore.Ledger l)throws Exception{
   try{return capture.invoke(null,store.storageRoot(),c,l,owner,prep,budget);}catch(InvocationTargetException e){throw (Exception)e.getCause();}
  }
  Object baseline()throws Exception{final Object[] r={null};locked(()->{HistoryControlStore.Inspection c=control();r[0]=capture(c,PrivateOperationStore.inspect(store.storageRoot(),c,budget));});return r[0];}
  public void close()throws IOException{try(java.util.stream.Stream<Path> p=Files.walk(parent)){for(Path x:(Iterable<Path>)p.sorted(Comparator.reverseOrder())::iterator)Files.delete(x);}}
 }
 @SuppressWarnings("unchecked") static List<PrivateOperationEvidence.Row> rows(Object b)throws Exception{return (List<PrivateOperationEvidence.Row>)call(b,"rows",new Class<?>[0]);}
 static void empty()throws Exception{try(F f=new F()){
  Object b=f.baseline();List<PrivateOperationEvidence.Row> rows=rows(b);
  check(rows.size()==2&&rows.stream().allMatch(r->r.role==4),"real enrollment+registration; no fabricated absent-root credit");
  try(AutoCloseable cursor=(AutoCloseable)call(b,"open",new Class<?>[0])){
   Object row;int n=0;while((row=call(cursor,"next",new Class<?>[0]))!=null){PrivateOperationEvidence.Row r=(PrivateOperationEvidence.Row)row;
    try(InputStream in=(InputStream)call(cursor,"openRaw",new Class<?>[0]);ByteArrayOutputStream out=new ByteArrayOutputStream()){
     byte[] block=new byte[4096];int k;while((k=in.read(block))!=-1)out.write(block,0,k);
     Path path=r.kind==1?f.parent.resolve(r.name):f.parent.resolve("history_control").resolve(r.name);
     check(Arrays.equals(out.toByteArray(),Files.readAllBytes(path)),"byte-exact raw context "+r.name);n++;
    }
   }check(n==2,"complete concrete cursor");
  }
  f.locked(()->{HistoryControlStore.Inspection c=f.control();PrivateOperationStore.Ledger l=PrivateOperationStore.inspect(f.store.storageRoot(),c,f.budget);
   reject(()->f.capture(c,new PrivateOperationStore.Ledger()),"forged ledger DTO");
   reject(()->f.capture(f.control(),l),"foreign control snapshot identity");
  });
 }}
 static void retainedAndBudget()throws Exception {
  for(int kind=0;kind<3;kind++)try(F f=new F()) {
   Path retained=f.parent.resolve("private_retained");
   if(kind==0)Files.createDirectory(retained);else if(kind==1)Files.write(retained,new byte[]{1});else Files.createSymbolicLink(retained,f.parent.resolve("missing"));
   reject(()->f.baseline(),"present/unclassified retained namespace "+kind);
  }
  try(F f=new F()) {
   Object b=f.baseline();Files.createDirectory(f.parent.resolve("private_retained"));
   try(AutoCloseable cursor=(AutoCloseable)call(b,"open",new Class<?>[0])) {
    call(cursor,"next",new Class<?>[0]);InputStream raw=(InputStream)call(cursor,"openRaw",new Class<?>[0]);
    reject(()->raw.read(),"retained root appearing before read");
    reject(()->cursor.close(),"outer close cannot discard changed-root uncertainty");
    f.owner.freeze();f.locked(()->reject(()->f.owner.claimLocked(),"changed-root reader still owned"));
    Files.delete(f.parent.resolve("private_retained"));cursor.close();
   }
  }
  try(F f=new F()) {
   f.locked(()->{HistoryControlStore.Inspection c=f.control();PrivateOperationStore.Ledger l=PrivateOperationStore.inspect(f.store.storageRoot(),c,f.budget);
    HistoryStorage.Budget foreign=new HistoryStorage.Budget(HistoryStorage.METADATA_BUDGET);
    reject(()->{try{capture.invoke(null,f.store.storageRoot(),c,l,f.owner,f.prep,foreign);}catch(InvocationTargetException e){throw (Exception)e.getCause();}},"foreign original Budget");
    f.budget.charge(HistoryStorage.METADATA_BUDGET-(64L<<10));reject(()->f.capture(c,l),"metadata refusal before baseline workspace");
   });
  }
 }
 static PrivateOperationRecord cancelled(F f,int bytes,boolean directory)throws Exception {return cancelled(f,bytes,directory,512);}
 static PrivateOperationRecord cancelled(F f,int bytes,boolean directory,long declared)throws Exception {return cancelled(f,bytes,directory,declared,true);}
 static PrivateOperationRecord cancelled(F f,int bytes,boolean directory,long declared,boolean rootPresent)throws Exception {
  HistoryControlStore.Inspection c=f.control();UUID prep=UUID.randomUUID();String zero=String.join("",Collections.nCopies(64,"0"));
  PrivateOperationRecord.Binding binding=new PrivateOperationRecord.Binding(prep,1,c.registration.bundleId,c.registration.envelopeHash,c.latest.generation,c.latest.envelopeHash,
   64L<<20,64L<<20,20,c.registration.roles,zero,zero,0);
  PrivateOperationRecord.Artifact map=new PrivateOperationRecord.Artifact(1,UUID.randomUUID(),PrivateOperationRecord.ZERO,0,declared,zero);
  PrivateOperationRecord dp=PrivateOperationRecord.descriptor(binding,map,Collections.emptyList());
  PrivateOperationRecord.Artifact d=new PrivateOperationRecord.Artifact(2,UUID.randomUUID(),PrivateOperationRecord.ZERO,0,dp.encoded().length,dp.envelopeHash);
  HistoryPinRecord pin=HistoryPinRecord.begin(UUID.randomUUID(),c.registration,c.latest,HistoryPinRecord.Purpose.EXPORT,HistoryPinRecord.Scope.ALL_BUNDLE,
   new HistoryPinRecord.Manifest(HistoryPinRecord.Domain.LOCAL_CAPTURE_V1,0,d.length,d.hash),Collections.emptyList());
  PrivateOperationRecord p=PrivateOperationRecord.prepare(binding,3,Arrays.asList(map,d),pin);
  Path control=f.parent.resolve("history_control");Files.write(control.resolve(p.name()),p.encoded());Files.write(control.resolve(pin.name()),HistoryPinRecord.encode(pin));Files.write(control.resolve("pin-"+pin.operation+".close"),HistoryPinRecord.close(pin,HistoryPinRecord.Outcome.CANCELLED));
  Path operationRoot=f.parent.resolve("private_operations");if(rootPresent)Files.createDirectories(operationRoot);
  if(directory){Path dir=operationRoot.resolve(prep.toString());Files.createDirectory(dir);if(bytes>=0){byte[] raw=new byte[65536];Arrays.fill(raw,(byte)7);try(OutputStream out=Files.newOutputStream(dir.resolve(map.filename()+".stage"))){for(int left=bytes;left>0;left-=Math.min(left,raw.length))out.write(raw,0,Math.min(left,raw.length));}}}
  if(rootPresent){f.capacity.reconcile();HistoryPins.open(f.store,f.catalog,f.identity,f.index,f.disk);}return p;
 }
 static void cancelledPrefixes()throws Exception {
  for(int bytes:new int[]{0,1,511,512})try(F f=new F()) {
   PrivateOperationRecord p=cancelled(f,bytes,true);Object b=f.baseline();List<PrivateOperationEvidence.Row> rows=rows(b);
   PrivateOperationEvidence.Row stage=rows.stream().filter(r->r.role==2&&r.kind==1).findFirst().get();
   check(stage.state==3&&stage.actualLength==bytes&&stage.futureBytes==512-bytes&&stage.futureSlots==0&&stage.relation==0,"UNKNOWN original stage prefix "+bytes);
   check(rows.stream().anyMatch(r->r.role==2&&r.kind==2&&r.state==0&&r.futureSlots==1),"missing final descriptor credit preserved");
   check(rows.stream().anyMatch(r->r.role==1&&r.kind==2&&r.state==0&&r.futureBytes==173&&r.futureSlots==1),"missing LINK credit preserved");
   consume(b);f.locked(()->f.owner.requireIdleBindingLocked(f.store.storageRoot(),f.prep,f.budget));
   check(Files.size(f.parent.resolve("private_operations/"+p.preparation+"/"+p.artifact(1).filename()+".stage"))==bytes,"old prefix unchanged");
  }
  try(F f=new F()) {cancelled(f,-1,false);Object b=f.baseline();check(rows(b).stream().anyMatch(r->r.role==3&&r.state==0&&r.futureSlots==1),"declared missing preparation directory credit");consume(b);}
 }
 static void consume(Object baseline)throws Exception {
  try(AutoCloseable cursor=(AutoCloseable)call(baseline,"open",new Class<?>[0])) {Object row;byte[] scratch=new byte[65536];
   while((row=call(cursor,"next",new Class<?>[0]))!=null){try(InputStream in=(InputStream)call(cursor,"openRaw",new Class<?>[]{PrivateOperationEvidence.Row.class},row)){while(in.read(scratch)!=-1){}}}
  }
 }
 static void manyRowsAndIdentity()throws Exception {
  try(F f=new F()) {
   HistoryControlStore.Inspection c=f.control();String hash=c.latest.envelopeHash;Path root=f.parent.resolve("history_control");
   for(int i=1;i<=70;i++){byte[] p=HistoryControlStore.encodePolicy(c.registration.bundleId,i,hash,64L<<20,20);Files.write(root.resolve(HistoryControlStore.policyFilename(i)),p);hash=PrivateOperationRecord.sha(p);}
   f.capacity.reconcile();Object b=f.baseline();check(rows(b).size()==72,"all policy predecessors exceed64 baseline rows");
   try(AutoCloseable cursor=(AutoCloseable)call(b,"open",new Class<?>[0])) {
    PrivateOperationEvidence.Row r=(PrivateOperationEvidence.Row)call(cursor,"next",new Class<?>[0]);
    reject(()->call(cursor,"openRaw",new Class<?>[]{PrivateOperationEvidence.Row.class},rows(b).get(1)),"wrong source row identity");
    InputStream in=(InputStream)call(cursor,"openRaw",new Class<?>[0]);reject(()->call(cursor,"next",new Class<?>[0]),"advance while raw row open");
    reject(()->in.close(),"row must reach EOF before close/advance");byte[] block=new byte[4096];while(in.read(block)!=-1){}in.close();
    while(call(cursor,"next",new Class<?>[0])!=null){try(InputStream next=(InputStream)call(cursor,"openRaw",new Class<?>[0])){while(next.read(block)!=-1){}}}
   }
   f.locked(()->f.owner.requireIdleBindingLocked(f.store.storageRoot(),f.prep,f.budget));checks++;
  }
 }
 static void replacement()throws Exception {
  try(F f=new F()) {
   Object b=f.baseline();PrivateOperationEvidence.Row first=rows(b).get(0);Path path=f.parent.resolve(first.name);Path saved=path.resolveSibling(first.name+".saved");
   Files.move(path,saved);Files.copy(saved,path);
   try(AutoCloseable cursor=(AutoCloseable)call(b,"open",new Class<?>[0])){
    call(cursor,"next",new Class<?>[0]);InputStream in=(InputStream)call(cursor,"openRaw",new Class<?>[0]);
    reject(()->in.read(),"same bytes replaced physical source");reject(()->cursor.close(),"changed identity keeps reader owned");
    f.owner.freeze();f.locked(()->reject(()->f.owner.claimLocked(),"replacement uncertainty blocks claim"));
    Files.delete(path);Files.move(saved,path);cursor.close();
   }
  }
 }
 static void legacyAndRevalidation()throws Exception {
  try(F f=new F()) {
   HistoryControlStore.Inspection initial=f.control();
   HistoryPinRecord pin=HistoryPinRecord.begin(UUID.randomUUID(),initial.registration,initial.latest,HistoryPinRecord.Purpose.EXPORT,HistoryPinRecord.Scope.ALL_BUNDLE,
    new HistoryPinRecord.Manifest(HistoryPinRecord.Domain.NATIVE_HISTORY_V1,0,0,PrivateOperationRecord.sha(new byte[0])),Collections.emptyList());
   Files.write(f.parent.resolve("history_control").resolve(pin.name()),HistoryPinRecord.encode(pin));
   Files.write(f.parent.resolve("history_control/pin-"+pin.operation+".close"),HistoryPinRecord.close(pin,HistoryPinRecord.Outcome.CANCELLED));
   Object b=f.baseline();check(rows(b).stream().filter(r->r.role==1&&r.ownerPreparation.equals(new UUID(0,0))&&r.nodeId.equals(pin.operation)).count()==2,"canonical closed legacy pair retains two exact slots");consume(b);
   final Object[] baseline={null};final HistoryControlStore.Inspection[] c={null};final PrivateOperationStore.Ledger[] l={null};
   f.locked(()->{c[0]=f.control();l[0]=PrivateOperationStore.inspect(f.store.storageRoot(),c[0],f.budget);baseline[0]=f.capture(c[0],l[0]);});
   f.locked(()->call(baseline[0],"requireOriginalLocked",new Class<?>[]{HistoryControlStore.Inspection.class,PrivateOperationStore.Ledger.class,PreparationSources.class,HistoryStorage.Budget.class},c[0],l[0],f.owner,f.budget));checks++;
   Path enrollment=f.parent.resolve("history_enrollment.control");byte[] raw=Files.readAllBytes(enrollment);java.nio.file.attribute.FileTime time=Files.getLastModifiedTime(enrollment);raw[0]^=1;Files.write(enrollment,raw);Files.setLastModifiedTime(enrollment,time);
   f.locked(()->reject(()->call(baseline[0],"requireOriginalLocked",new Class<?>[]{HistoryControlStore.Inspection.class,PrivateOperationStore.Ledger.class,PreparationSources.class,HistoryStorage.Budget.class},c[0],l[0],f.owner,f.budget),"changed raw bytes despite restored mtime"));
  }
  try(F f=new F();F other=new F()) {
   f.locked(()->{HistoryControlStore.Inspection c=f.control();PrivateOperationStore.Ledger l=PrivateOperationStore.inspect(f.store.storageRoot(),c,f.budget);
    other.locked(()->reject(()->{try{capture.invoke(null,other.store.storageRoot(),c,l,other.owner,other.prep,f.budget);}catch(InvocationTargetException e){throw (Exception)e.getCause();}},"foreign inspected primary root"));
   });
  }
 }
 static void committedAndUnknown()throws Exception {
  try(F f=new F()) {
   PrivateOperationRecord p=cancelled(f,1,true);Path dir=f.parent.resolve("private_operations/"+p.preparation);
   byte[] descriptor=PrivateOperationRecord.descriptor(p.binding,p.artifact(1),Collections.emptyList()).encoded();Files.write(dir.resolve(p.artifact(2).filename()),descriptor);
   Object b=f.baseline();check(rows(b).stream().anyMatch(r->r.role==2&&r.kind==2&&r.state==2&&r.futureBytes==0&&r.actualHash.equals(p.artifact(2).hash)),"old committed final bytes retained");consume(b);
   check(!Files.exists(f.parent.resolve("history_control/prep-"+f.prep+".reserve")),"baseline creates no new P");
  }
  try(F f=new F()) {
   cancelled(f,1,true);Files.write(f.parent.resolve("private_operations/unknown"),new byte[]{1});reject(()->f.baseline(),"unclassified physical operation member");
  }
  try(F f=new F()) {
   Object b=f.baseline();try(AutoCloseable cursor=(AutoCloseable)call(b,"open",new Class<?>[0])) {
    reject(()->call(b,"open",new Class<?>[0]),"only one original cursor");
    call(cursor,"next",new Class<?>[0]);reject(()->call(cursor,"next",new Class<?>[0]),"unconsumed unopened row cannot be skipped");
   }
   consume(b);checks++;
  }
 }
 static void fundedRootAbsence()throws Exception {
  try(F f=new F()) {
   cancelled(f,-1,false,512,false);
   final Object[] baseline={null};
   f.locked(()->{HistoryControlStore.Inspection c=f.control();PrivateOperationStore.Ledger l=PrivateOperationStore.inspect(f.store.storageRoot(),c,f.budget);
    check(l.complete&&l.reservedEntries==4,"actual funded missing root/prep/artifact slots");
    baseline[0]=f.capture(c,l);
    reject(()->PrivateOperationStore.requireHistoricalCaptureOwnerLocked(f.store.storageRoot(),c,l,f.owner,f.prep,f.budget),"generic namespace admission remains blocked");
   });
   Object b=baseline[0];check((Boolean)call(b,"sourceOnlyMissingRoot",new Class<?>[0]),"funded absence is source-only inventory");
   check(rows(b).stream().filter(r->r.role==2||r.role==3).mapToInt(r->r.futureSlots).sum()==4,"all real missing slot credits conserved");consume(b);
   Files.createDirectory(f.parent.resolve("private_operations"));
   try(AutoCloseable cursor=(AutoCloseable)call(b,"open",new Class<?>[0])) {
    call(cursor,"next",new Class<?>[0]);InputStream in=(InputStream)call(cursor,"openRaw",new Class<?>[0]);
    reject(()->in.read(),"funded missing root reappearance");reject(()->cursor.close(),"reappearance uncertainty retained");Files.delete(f.parent.resolve("private_operations"));cursor.close();
   }
  }
  try(F f=new F()) {
   PrivateOperationRecord p=cancelled(f,-1,false,512,false);
   f.locked(()->{HistoryControlStore.Inspection c=f.control();PrivateOperationStore.Ledger l=PrivateOperationStore.inspect(f.store.storageRoot(),c,f.budget);
    reject(()->{try{capture.invoke(null,f.store.storageRoot(),c,l,f.owner,p.preparation,f.budget);}catch(InvocationTargetException e){throw (Exception)e.getCause();}},"old owner/preparation cannot read as new owner");
   });
  }
 }
 static void actualOwnerAndRoot()throws Exception {
  try(F f=new F()) {
   Object b=f.baseline();
   try(PreparationSources.Reader live=f.owner.open(()->Files.newInputStream(f.parent.resolve("history_enrollment.control")))) {
    reject(()->f.baseline(),"actual live owned reader prevents baseline assembly");
    reject(()->PreparationSources.forPreparation(f.store.storageRoot(),UUID.randomUUID(),f.budget,PreparationSources.newProducer()),"another preparation cannot replace an unfinished source owner");
   }
   consume(b);
  }
  try(F f=new F()) {
   f.locked(()->{HistoryControlStore.Inspection c=f.control();PrivateOperationStore.Ledger l=PrivateOperationStore.inspect(f.store.storageRoot(),c,f.budget);
    Path old=f.parent.resolve("original-store");Files.move(f.store.storageRoot().toPath(),old);Files.createDirectory(f.store.storageRoot().toPath());
    reject(()->f.capture(c,l),"same primary path with replaced root inode");
   });
  }
 }
 static byte[] held;
 static void large()throws Exception {
  held=new byte[36<<20];held[held.length-1]=1;
  try(F f=new F()) {int size=(9<<20)+3;cancelled(f,size,true,size+1L);Object b=f.baseline();consume(b);
   PrivateOperationEvidence.Row stage=rows(b).stream().filter(r->r.role==2&&r.kind==1).findFirst().get();
   check(stage.actualLength==size&&stage.futureBytes==1&&stage.relation==0&&held[held.length-1]==1,"large UNKNOWN source streamed with36MiB held in48MiB heap");
  }
 }
 public static void main(String[] args)throws Exception {
  try{capture=PrivateOperationStore.class.getDeclaredMethod("capturePortableBaselineLocked",File.class,HistoryControlStore.Inspection.class,PrivateOperationStore.Ledger.class,PreparationSources.class,UUID.class,HistoryStorage.Budget.class);capture.setAccessible(true);}
  catch(NoSuchMethodException missing){throw new AssertionError("Missing actual original portable baseline inventory",missing);}
  if(args.length==1&&args[0].equals("large")){large();System.out.println("Portable baseline large PASS "+checks+" checks");return;}
  empty();retainedAndBudget();cancelledPrefixes();manyRowsAndIdentity();replacement();legacyAndRevalidation();committedAndUnknown();fundedRootAbsence();actualOwnerAndRoot();System.out.println("Portable baseline PASS "+checks+" checks");
 }
}
