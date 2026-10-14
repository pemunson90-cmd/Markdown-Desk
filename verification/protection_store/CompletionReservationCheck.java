package com.example.markdowndesk;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Production completion admission and retry tests; v1 records are never rewritten. */
public final class CompletionReservationCheck {
 static int assertions,cases;
 interface Checked {void run() throws Exception;}
 static void check(boolean value,String why){assertions++;if(!value)throw new AssertionError(why);}
 static void fails(Checked action,String why)throws Exception {try{action.run();}catch(IOException expected){assertions++;return;}throw new AssertionError(why);}
 static void test(String name,Checked action)throws Exception{action.run();cases++;System.out.println("PASS "+name);}
 static final class Fixture {
  final File root=ProtectionStoreCheck.fresh();
  final ProtectionStoreCheck.Durable durability=new ProtectionStoreCheck.Durable();
  final ProtectionStore store=ProtectionStoreCheck.store(root,durability);
  final ProtectionStore.Snapshot original=ProtectionStoreCheck.put(store,ProtectionStoreCheck.b("original"),"original");
  final ProtectionStore.Snapshot attempted=ProtectionStoreCheck.put(store,ProtectionStoreCheck.b("attempted"),"attempted");
  final ProtectionStore.Transaction transaction=store.prepare(original,attempted,original.hash);
  final long reserved=store.inspect().reservedCompletionBytes;
  final long charged=ProtectionStoreCheck.disk(root)+reserved;
  final Map<String,byte[]> before=capture();
  Fixture()throws Exception{}
  File stage(){return new File(root,transaction.id+".completion-stage");}
  Map<String,byte[]> capture()throws IOException{Map<String,byte[]> result=new TreeMap<>();for(File f:root.listFiles())result.put(f.getName(),Files.readAllBytes(f.toPath()));return result;}
  void unchanged()throws Exception{for(Map.Entry<String,byte[]> e:before.entrySet())check(Arrays.equals(e.getValue(),Files.readAllBytes(new File(root,e.getKey()).toPath())),"v1 record changed: "+e.getKey());}
  ProtectionStore reopen(long quota)throws IOException{return new ProtectionStore(root,durability,quota);}
  void failMove()throws Exception{durability.failure="move";fails(()->store.complete(transaction),"move failure accepted");durability.failure="";}
 }
 public static void main(String[] args)throws Exception {
  test("failed completion consumes exactly its byte and slot reservation",()->{
   Fixture f=new Fixture();f.failMove();check(f.stage().isFile(),"completion must retain deterministic transaction-bound stage");
   ProtectionStore.Inventory inventory=f.store.inspect();check(inventory.actualBytes==f.charged,"stage byte charge differs from reservation");
   check(inventory.reservedCompletionBytes==0&&inventory.reservedCompletionSlots==0,"stage counted twice");
   check(inventory.pruneBlocked,"stage permits pruning");f.unchanged();
   ProtectionStore exact=f.reopen(f.charged);exact.complete(f.transaction);check(exact.validate(f.transaction).verified,"exact quota retry failed");
   check(!f.stage().exists()&&f.root.list().length==4,"retry duplicated stage");f.unchanged();
  });
  test("repeated failed exact-stage retries do not grow disk or use other credit",()->{
   Fixture f=new Fixture();f.failMove();Map<String,byte[]> staged=f.capture();f.durability.failure="move";
   for(int i=0;i<3;i++){fails(()->f.store.complete(f.transaction),"failed retry accepted");check(f.capture().keySet().equals(staged.keySet()),"retry allocated another stage");check(ProtectionStoreCheck.disk(f.root)==f.charged,"retry spent reservation again");}
   f.durability.failure="";f.reopen(f.charged).complete(f.transaction);f.unchanged();
  });
  test("partial stage retains unfilled reservation and blocks retry without writes",()->{
   Fixture f=new Fixture();f.failMove();try(RandomAccessFile out=new RandomAccessFile(f.stage(),"rw")){out.setLength(17);}
   long size=ProtectionStoreCheck.disk(f.root);ProtectionStore exact=f.reopen(f.charged);ProtectionStore.Inventory inventory=exact.inspect();
   check(inventory.reservedCompletionBytes==f.reserved-17&&inventory.reservedCompletionSlots==0,"partial reservation incorrect");
   f.durability.calls.clear();fails(()->exact.complete(f.transaction),"partial stage retry accepted");check(f.durability.calls.isEmpty(),"partial retry reached durability");
   check(ProtectionStoreCheck.disk(f.root)==size&&f.stage().length()==17,"partial stage modified");
   fails(()->ProtectionStoreCheck.put(exact,new byte[]{1},"attempted"),"unfilled reservation was spendable");f.unchanged();
  });
  test("changed oversized foreign and duplicate completion stages fail closed",()->{
   for(String kind:Arrays.asList("changed","oversized","foreign","duplicate")){
    Fixture f=new Fixture();f.failMove();
    if(kind.equals("changed")){byte[] bytes=Files.readAllBytes(f.stage().toPath());bytes[0]^=1;Files.write(f.stage().toPath(),bytes);}
    if(kind.equals("oversized"))try(RandomAccessFile out=new RandomAccessFile(f.stage(),"rw")){out.setLength(f.reserved+1);}
    if(kind.equals("foreign"))Files.move(f.stage().toPath(),new File(f.root,UUID.randomUUID()+".completion-stage").toPath());
    if(kind.equals("duplicate"))Files.copy(f.stage().toPath(),new File(f.root,f.transaction.id+".complete").toPath());
    Map<String,byte[]> saved=f.capture();fails(()->f.store.complete(f.transaction),kind+" stage accepted");
    fails(()->ProtectionStoreCheck.put(f.store,new byte[]{1},"attempted"),kind+" stage permits new admission");
    check(f.store.inspect().pruneBlocked,kind+" stage permits pruning");check(saved.keySet().equals(f.capture().keySet()),kind+" mutated entries");f.unchanged();
   }
  });
  test("legacy anonymous stages remain charged without completion credit",()->{
   Fixture f=new Fixture();Files.write(new File(f.root,UUID.randomUUID()+".stage").toPath(),new byte[11]);
   ProtectionStore.Inventory inventory=f.store.inspect();check(inventory.reservedCompletionBytes==f.reserved&&inventory.reservedCompletionSlots==1,"legacy stage got reservation credit");
   ProtectionStore exact=f.reopen(f.charged);fails(()->exact.complete(f.transaction),"legacy stage undercharged");check(f.root.list().length==4,"quota refusal wrote stage");f.unchanged();
  });
  test("reservation inventory remains readable below constructor quota",()->{
   Fixture f=new Fixture();ProtectionStore small=f.reopen(0);ProtectionStore.Inventory inventory=small.inspect();
   check(inventory.complete&&inventory.reservedCompletionBytes==f.reserved,"lower quota invalidates reservation inspection");
   fails(()->small.complete(f.transaction),"reduced quota exemption enabled before shared policy");f.unchanged();
  });
  test("exact-stage retry still requires file and directory sync",()->{
   Fixture f=new Fixture();f.failMove();f.durability.failure="file";fails(()->f.store.complete(f.transaction),"retry bypasses file sync");
   check(f.stage().isFile()&&f.root.list().length==4,"file sync failure changed stages");f.durability.failure="dir";
   fails(()->f.store.complete(f.transaction),"retry bypasses directory sync");f.durability.failure="";
   f.reopen(f.charged).complete(f.transaction);check(f.store.validate(f.transaction).verified,"directory retry failed");f.unchanged();
  });
  System.out.println("Completion reservations: "+cases+" cases, "+assertions+" assertions PASS");
 }
}
