package com.example.markdowndesk;
import java.io.*;
import java.nio.file.*;
import java.util.*;
public final class NativeTerminalValidationCheck {
 static int checks;
 interface Work {void run()throws Exception;}
 static void check(boolean b,String s){checks++;if(!b)throw new AssertionError(s);}
 static void fails(Work w)throws Exception{try{w.run();throw new AssertionError("accepted");}catch(IOException expected){checks++;}}
 static final class Disk implements ProtectionStore.Durability {
  boolean failMove;
  public void syncFile(FileDescriptor fd)throws IOException{fd.sync();}
  public void syncDirectory(File f){}
  public void move(File a,File b)throws IOException{if(failMove)throw new IOException("fixture");Files.move(a.toPath(),b.toPath());}
 }
 static final class F {
  final File root=Files.createTempDirectory("native-terminal-").resolve("store").toFile();
  final Disk disk=new Disk();final ProtectionStore store=new ProtectionStore(root,disk,64L<<20);
  final Object lock=HistoryStorage.monitorFor(root);
  final ProtectionStore.Snapshot a=store.putSnapshot("doc","uri","title",1,new byte[]{1},"original"),b=store.putSnapshot("doc","uri","title",1,new byte[]{2},"attempted");
  final ProtectionStore.Transaction tx=store.prepare(a,b,a.hash);
  final HistoryStorage.Budget budget=new HistoryStorage.Budget(ProtectionStore.PIN_NATIVE_WORKSPACE_BYTES);
  final ProtectionStore.PinNativeWorkspace workspace=ProtectionStore.PinNativeWorkspace.reserve(store,budget);
  F()throws Exception{}
  void locked(Work w)throws Exception{synchronized(lock){try(HistoryStorage.MutationScope m=HistoryStorage.administration(root)){w.run();}}}
  void compare()throws Exception{locked(()->{
   ProtectionStore.Inventory old=store.pinInventory();boolean accept=old.usageComplete&&old.diagnostics.stream().allMatch(s->s.endsWith("retained stage"));
   Map<String,String> seen=new TreeMap<>();
   try{ProtectionStore.PinNativeTotals n=store.validatePinNativeLocked(workspace,(r,a)->{check(a.fileKey()!=null,"actual identity");seen.put(r.filename,r.serializedBytes+":"+r.envelopeHash);});
    check(accept,"stream accepted old refusal");check(n.actualBytes==old.actualBytes&&n.occupiedEntries==old.occupiedEntries,"accounting");
    check(n.reservedCompletionBytes==old.reservedCompletionBytes&&n.reservedCompletionSlots==old.reservedCompletionSlots,"reservation");
    Map<String,String> expected=new TreeMap<>();for(ProtectionStore.StoredRecord r:old.records)expected.put(r.filename,r.serializedBytes+":"+r.envelopeHash);check(expected.equals(seen),"members");
   }catch(IOException rejected){check(!accept,"stream refused valid inventory: "+rejected);}
  });}
  File stage(){return new File(root,tx.id+".completion-stage");}
  void stageFull()throws Exception{disk.failMove=true;fails(()->store.complete(tx));disk.failMove=false;}
 }
 static void allocationRace()throws Exception {
  F f=new F();File original=new File(f.root,f.tx.id+".transaction");
  File racing=new File(original.getPath()) {
   int calls;
   @Override public Path toPath(){if(++calls==2)try(RandomAccessFile out=new RandomAccessFile(original,"rw")){out.setLength(ProtectionStore.MAX_DOCUMENT_BYTES);}catch(IOException e){throw new UncheckedIOException(e);}return original.toPath();}
  };
  java.lang.reflect.Method reader=ProtectionStore.class.getDeclaredMethod("readBody",File.class,byte.class);reader.setAccessible(true);
  try {reader.invoke(f.store,racing,(byte)2);throw new AssertionError("race accepted");}
  catch(java.lang.reflect.InvocationTargetException failure){if(!(failure.getCause() instanceof IOException))throw new AssertionError("allocation race must refuse before allocation",failure.getCause());}
  System.out.println("transaction growth between attribute samples under8MiB heap PASS");
 }
 static void distinctWorkspaceReentry()throws Exception {
  F f=new F();ProtectionStore.PinNativeWorkspace second=ProtectionStore.PinNativeWorkspace.reserve(f.store,new HistoryStorage.Budget(ProtectionStore.PIN_NATIVE_WORKSPACE_BYTES));
  f.locked(()->{f.store.validatePinNativeLocked(f.workspace,(r,a)->{try{fails(()->f.store.validatePinNativeLocked(second,(x,y)->{}));}catch(IOException e){throw e;}catch(Exception e){throw new AssertionError(e);}});f.store.validatePinNativeLocked(second,(r,a)->{});f.store.validatePinNativeLocked(f.workspace,(r,a)->{});});
  System.out.println("distinct funded workspace same-store reentry rejected; outer/reuse PASS");
 }
 public static void main(String[] args)throws Exception {
  if(args.length>0&&args[0].equals("race")){allocationRace();return;}
  if(args.length>0&&args[0].equals("reentry")){distinctWorkspaceReentry();return;}
  if(args.length>0&&args[0].equals("probe")){File root=new File(args[1]);ProtectionStore store=new ProtectionStore(root,new Disk(),64L<<20);ProtectionStore.PinNativeWorkspace w=ProtectionStore.PinNativeWorkspace.reserve(store,new HistoryStorage.Budget(ProtectionStore.PIN_NATIVE_WORKSPACE_BYTES));synchronized(HistoryStorage.monitorFor(root)){try(HistoryStorage.MutationScope m=HistoryStorage.administration(root)){store.validatePinNativeLocked(w,(r,a)->{});}}System.out.println("8MiB snapshot under8MiB heap PASS");return;}
  F f=new F();f.compare();f.store.putSnapshot("extra",null,null,0,new byte[]{3},"original");f.compare();
  f.locked(()->{f.store.validatePinNativeLocked(f.workspace,(r,a)->{});fails(()->f.store.validatePinNativeLocked(f.workspace,(r,a)->HistoryStorage.changed(f.root)));fails(()->f.budget.charge(1));fails(()->f.store.validatePinNativeLocked(f.workspace,(r,a)->{throw new IOException("visitor");}));f.store.validatePinNativeLocked(f.workspace,(r,a)->{});fails(()->f.store.validatePinNativeLocked(f.workspace,(r,a)->f.store.validatePinNativeLocked(f.workspace,(x,y)->{})));});
  fails(()->f.store.validatePinNativeLocked(f.workspace,(r,a)->{}));F foreign=new F();foreign.locked(()->fails(()->foreign.store.validatePinNativeLocked(f.workspace,(r,a)->{})));
  HistoryStorage.Budget small=new HistoryStorage.Budget(ProtectionStore.PIN_NATIVE_WORKSPACE_BYTES-1);fails(()->ProtectionStore.PinNativeWorkspace.reserve(f.store,small));small.charge(ProtectionStore.PIN_NATIVE_WORKSPACE_BYTES-1);
  for(String mode:Arrays.asList("full","partial","corrupt","orphan","orphan-complete","complete","mismatch","missing-source","snapshot","transaction","stage","unknown","oversized")){
   F g=new F();
   if(Arrays.asList("full","partial","corrupt","orphan").contains(mode)){g.stageFull();if(mode.equals("partial"))try(RandomAccessFile out=new RandomAccessFile(g.stage(),"rw")){out.setLength(17);}if(mode.equals("corrupt")){byte[] bytes=Files.readAllBytes(g.stage().toPath());bytes[0]^=1;Files.write(g.stage().toPath(),bytes);}if(mode.equals("orphan"))Files.move(g.stage().toPath(),new File(g.root,UUID.randomUUID()+".completion-stage").toPath());}
   if(mode.equals("orphan-complete")){g.store.complete(g.tx);Files.move(new File(g.root,g.tx.id+".complete").toPath(),new File(g.root,UUID.randomUUID()+".complete").toPath());}
   if(mode.equals("complete"))g.store.complete(g.tx);
   if(mode.equals("missing-source"))Files.delete(new File(g.root,g.a.id+".snapshot").toPath());
   if(mode.equals("mismatch")){g.store.complete(g.tx);byte[] bytes=Files.readAllBytes(new File(g.root,g.tx.id+".complete").toPath());bytes[14]^=1;Files.write(new File(g.root,g.tx.id+".complete").toPath(),bytes);}
   if(mode.equals("snapshot")||mode.equals("transaction")){File file=new File(g.root,(mode.equals("snapshot")?g.a.id:g.tx.id)+"."+mode);byte[] bytes=Files.readAllBytes(file.toPath());bytes[14]^=1;Files.write(file.toPath(),bytes);}
   if(mode.equals("stage"))Files.write(new File(g.root,UUID.randomUUID()+".stage").toPath(),new byte[]{1,2});
   if(mode.equals("unknown"))Files.write(new File(g.root,"foreign").toPath(),new byte[]{1});
   if(mode.equals("oversized"))try(RandomAccessFile out=new RandomAccessFile(new File(g.root,UUID.randomUUID()+".completion-stage"),"rw")){out.setLength(32769);}
   g.compare();
  }
  if(args.length>0&&args[0].equals("large")){F g=new F();byte[] payload=new byte[ProtectionStore.MAX_DOCUMENT_BYTES];g.store.putSnapshot("large",null,null,0,payload,"original");payload=null;System.gc();g.locked(()->g.store.validatePinNativeLocked(g.workspace,(r,a)->{}));Files.writeString(Path.of(args[1]),g.root.getPath());}
  System.out.println("Native terminal validation: "+checks+" assertions PASS");
 }
}
