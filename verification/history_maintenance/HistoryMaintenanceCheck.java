package com.example.markdowndesk;
import java.io.*;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;

public final class HistoryMaintenanceCheck {
 static int checks,cases;
 interface Checked {void run()throws Exception;}
 static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
 static void test(String name,Checked action)throws Exception{action.run();cases++;System.out.println("PASS "+name);}
 static void enroll(ProtectionStore store,File root)throws Exception {
  try{Class.forName("com.example.markdowndesk.HistoryMaintenance").getMethod("enroll",ProtectionStore.class,File.class).invoke(null,store,root);}
  catch(ClassNotFoundException|NoSuchMethodException missing){throw new AssertionError("Explicit maintenance enrollment API is missing",missing);}
  catch(InvocationTargetException failure){if(failure.getCause() instanceof IOException)throw (IOException)failure.getCause();throw failure;}
 }
 static void fails(Checked action,String message)throws Exception{try{action.run();}catch(IOException expected){checks++;return;}throw new AssertionError(message);}
 static HistoryQuotaCheck.Fixture fixture()throws Exception{HistoryQuotaCheck.Fixture f=new HistoryQuotaCheck.Fixture();f.configure(HistoryQuotaCheck.Q);return f;}
 static File root(HistoryQuotaCheck.Fixture f){return new File(f.parent,"history_maintenance");}
 static Object inspect(ProtectionStore store)throws Exception{return Class.forName("com.example.markdowndesk.HistoryMaintenance").getMethod("inspect",ProtectionStore.class).invoke(null,store);}
 static long value(Object inventory,String name)throws Exception{return inventory.getClass().getField(name).getLong(inventory);}
 static boolean flag(Object inventory,String name)throws Exception{return inventory.getClass().getField(name).getBoolean(inventory);}
 public static void main(String[] args)throws Exception {
  test("maintenanceOptInPreservesV1AndCountsExistingStages",()->{
   HistoryQuotaCheck.Fixture f=new HistoryQuotaCheck.Fixture();f.put(new byte[]{1,2,3});f.configure(HistoryQuotaCheck.Q);
   Map<String,String> nativeBefore=HistoryQuotaCheck.hashes(new File(f.parent,"store")),policyBefore=HistoryQuotaCheck.hashes(new File(f.parent,"history_capacity"));
   File maintenance=new File(f.parent,"history_maintenance");Files.createDirectory(maintenance.toPath());
   File stage=new File(maintenance,UUID.randomUUID()+".prune-stage");Files.write(stage.toPath(),new byte[17]);
   new ProtectionStore(new File(f.parent,"store"),f.disk,HistoryQuotaCheck.Q);
   check(policyBefore.equals(HistoryQuotaCheck.hashes(new File(f.parent,"history_capacity"))),"Constructor upgraded registration");
   enroll(f.store,maintenance);
   for(int i=0;i<2;i++){
    ProtectionStore reopened=new ProtectionStore(new File(f.parent,"store"),f.disk,HistoryQuotaCheck.Q);Object inventory=inspect(reopened);
    check(value(inventory,"actualBytes")==17&&value(inventory,"occupiedEntries")==1,"Maintenance stage omitted");
    check(value(inventory,"reservedPruneBytes")==65536&&value(inventory,"reservedPruneSlots")==1,"Prune reserve missing");
    check(flag(inventory,"pruneBlocked")&&flag(inventory,"usageComplete"),"Stage must block proof without hiding known usage");
    check(HistoryPolicy.inspect(reopened).actualBytes==HistoryQuotaCheck.diskBytes(f.parent)-17,"Maintenance charged ordinary quota");
   }
   check(HistoryQuotaCheck.hashes(new File(f.parent,"store")).equals(nativeBefore),"Native bytes changed");
   check(HistoryQuotaCheck.hashes(new File(f.parent,"history_capacity")).entrySet().containsAll(policyBefore.entrySet()),"V1 policy bytes changed");
   check(Files.size(stage.toPath())==17,"Enrollment removed stage");
  });
  test("graph and capacity expose separate maintenance proof",()->{
   HistoryQuotaCheck.Fixture f=fixture();HistoryGraph graph=new HistoryGraph(f.store,f.catalog,f.identity,f.index,new HistoryPolicy(HistoryQuotaCheck.Q,20));String before=graph.scan().fingerprint;
   enroll(f.store,root(f));HistoryGraph.Graph empty=graph.scan();check(empty.complete&&!empty.fingerprint.equals(before),"Enrollment missing from proof");
   Files.write(new File(root(f),UUID.randomUUID()+".operation-stage").toPath(),new byte[31]);
   HistoryGraph.Graph pending=graph.scan();check(pending.pruneBlocked&&!pending.complete,"Maintenance stage omitted from graph");
   check(pending.records.stream().anyMatch(record->record.rootRole.equals("maintenance")&&record.serializedBytes==31),"Maintenance descriptor missing");
   check(value(pending,"maintenanceActualBytes")==31&&value(HistoryPolicy.inspect(f.store),"maintenanceActualBytes")==31,"Separate maintenance accounting missing");
   check(pending.actualBytes==HistoryQuotaCheck.diskBytes(f.parent)-31,"Maintenance counted twice");
   fails(()->f.put(new byte[]{1}),"Pending maintenance admitted snapshot");fails(()->new HistoryPolicy(128*HistoryQuotaCheck.MIB,20).apply(f.store),"Pending maintenance admitted policy mutation");
  });
  test("later policies preserve binding and v2 to v1 chain regression fails closed",()->{
   HistoryQuotaCheck.Fixture f=fixture();enroll(f.store,root(f));new HistoryPolicy(128*HistoryQuotaCheck.MIB,5).apply(f.store);
   File latest=new File(new File(f.parent,"history_capacity"),"00000000000000000002.policy");byte[] original=Files.readAllBytes(latest.toPath());
   check(java.nio.ByteBuffer.wrap(original).getInt(4)==2,"Policy update dropped v2 binding");check(flag(inspect(f.store),"enrolled"),"Policy update forgot enrollment");
   // Forge a valid checksummed v1 successor with identical roots/choices and chain hash.
   byte[] regressed;try(DataInputStream in=new DataInputStream(new ByteArrayInputStream(original));ByteArrayOutputStream bytes=new ByteArrayOutputStream();DataOutputStream out=new DataOutputStream(bytes)){
    out.writeInt(in.readInt());in.readInt();out.writeInt(1);for(int i=0;i<3;i++)out.writeLong(in.readLong());out.writeInt(in.readInt());out.writeUTF(in.readUTF());for(int i=0;i<4;i++)out.writeUTF(in.readUTF());out.write(HistoryStorage.digest().digest(bytes.toByteArray()));regressed=bytes.toByteArray();
   }
   Files.write(latest.toPath(),regressed);fails(()->f.put(new byte[]{1}),"Version regression admitted write");
   check(new HistoryGraph(f.store,f.catalog,f.identity,f.index,null).scan().pruneBlocked,"Version regression allowed proof");
  });
  test("explicit enrollment rejects missing configuration aliases symlinks and mixed parents",()->{
   HistoryQuotaCheck.Fixture unconfigured=new HistoryQuotaCheck.Fixture();fails(()->enroll(unconfigured.store,root(unconfigured)),"Implicit four-root migration");
   HistoryQuotaCheck.Fixture f=fixture();for(String role:Arrays.asList("store","catalog","identity","index","history_capacity"))fails(()->enroll(f.store,new File(f.parent,role)),"Aliased maintenance root accepted");
   File elsewhere=new File(Files.createTempDirectory("maintenance-other-").toFile(),"history_maintenance");fails(()->enroll(f.store,elsewhere),"Mixed parent accepted");
   Files.createSymbolicLink(root(f).toPath(),new File(f.parent,"catalog").toPath());fails(()->enroll(f.store,root(f)),"Symlink accepted");
   check(!flag(inspect(f.store),"enrolled"),"Rejected root became enrolled");
  });
  test("hard byte and entry bounds preserve evidence and block admission",()->{
   for(boolean byteLimit:new boolean[]{false,true}){
    HistoryQuotaCheck.Fixture f=fixture();enroll(f.store,root(f));
    int count=byteLimit?17:257;for(int i=0;i<count;i++)Files.write(new File(root(f),UUID.randomUUID()+".operation-stage").toPath(),new byte[byteLimit?65536:1]);
    Map<String,String> before=HistoryQuotaCheck.hashes(root(f));Object inventory=inspect(f.store);
    check(!flag(inventory,"usageComplete")&&flag(inventory,"pruneBlocked"),"Hard limit accepted");check(value(inventory,"occupiedEntries")<=256,"Unbounded inventory");
    fails(()->f.put(new byte[]{1}),"Limit bypassed admission");check(before.equals(HistoryQuotaCheck.hashes(root(f))),"Bound rejection removed evidence");
   }
  });
  test("parent sync fault retries durability and retains old effective policy",()->{
   HistoryQuotaCheck.Fixture f=fixture();final int[] parents={0};f.disk.directoryCheck=directory->{if(directory.equals(f.parent)&&++parents[0]==1)throw new IOException("parent fault");};
   fails(()->enroll(f.store,root(f)),"Parent fault acknowledged");check(!flag(inspect(f.store),"enrolled"),"Unpublished enrollment effective");
   check(HistoryPolicy.inspect(f.store).mutationBlocked,"Failed enrollment not pending");
   enroll(f.store,root(f));check(parents[0]==2,"Retry skipped parent sync");enroll(f.store,root(f));check(parents[0]==3,"Repeated enrollment skipped parent sync");
  });
  test("file move and final directory enrollment failures retain prior binding",()->{
   for(String fault:Arrays.asList("file","move","dir")){
    HistoryQuotaCheck.Fixture f=fixture();f.disk.moved=false;f.disk.fail=fault;
    fails(()->enroll(f.store,root(f)),"Enrollment fault acknowledged");check(!flag(inspect(f.store),"enrolled"),"Failed enrollment silently became effective");
    check(HistoryPolicy.inspect(f.store).mutationBlocked,"Failed enrollment allowed mutation");Map<String,String> retained=HistoryQuotaCheck.hashes(new File(f.parent,"history_capacity"));f.disk.fail="";
    enroll(f.store,root(f));check(flag(inspect(f.store),"enrolled"),"Explicit enrollment retry failed");
    check(HistoryQuotaCheck.hashes(new File(f.parent,"history_capacity")).size()==2,"Enrollment retry grew ledger");
   }
  });
  test("typed corrupt unknown oversized and nonregular records stay bounded and blocked",()->{
   for(String kind:Arrays.asList("valid","corrupt","unknown","oversized","symlink","directory")){
    HistoryQuotaCheck.Fixture f=fixture();enroll(f.store,root(f));String id=UUID.randomUUID().toString();File record=new File(root(f),id+".operation");
    if(kind.equals("symlink"))Files.createSymbolicLink(record.toPath(),new File(f.parent,"store").toPath());
    else if(kind.equals("directory"))Files.createDirectory(record.toPath());
    else if(kind.equals("oversized"))try(RandomAccessFile out=new RandomAccessFile(record,"rw")){out.setLength(65537);}
    else if(kind.equals("unknown"))Files.write(new File(root(f),id+".snapshot").toPath(),new byte[7]);
    else {ByteArrayOutputStream bytes=new ByteArrayOutputStream();DataOutputStream out=new DataOutputStream(bytes);UUID uuid=UUID.fromString(id);
     out.writeInt(0x4d44484d);out.writeInt(1);out.writeInt(1);out.writeLong(uuid.getMostSignificantBits());out.writeLong(uuid.getLeastSignificantBits());out.writeInt(0);out.write(HistoryStorage.digest().digest(bytes.toByteArray()));byte[] envelope=bytes.toByteArray();if(kind.equals("corrupt"))envelope[envelope.length-1]^=1;Files.write(record.toPath(),envelope);
    }
    Object inventory=inspect(f.store);check(flag(inventory,"pruneBlocked"),"Hostile or uninterpreted record authorized pruning");
    HistoryGraph.Graph graph=new HistoryGraph(f.store,f.catalog,f.identity,f.index,null).scan();check(graph.pruneBlocked,"Graph omitted hostile inventory");
    if(kind.equals("oversized"))check(graph.records.stream().filter(r->r.rootRole.equals("maintenance")).allMatch(r->r.envelopeHash==null),"Oversized entry materialized");
    fails(()->f.put(new byte[]{1}),"Hostile inventory admitted mutation");
   }
  });
  test("reserved prune bytes and entry remain unavailable to ordinary maintenance",()->{
   for(boolean bytes:new boolean[]{true,false}){
    HistoryQuotaCheck.Fixture f=fixture();enroll(f.store,root(f));int count=bytes?15:255;
    for(int i=0;i<count;i++)Files.write(new File(root(f),UUID.randomUUID()+".operation-stage").toPath(),new byte[bytes?65536:0]);
    Object inventory=inspect(f.store);check(flag(inventory,"usageComplete"),"Reserve boundary hides exact usage");
    check(value(inventory,bytes?"availableOperationBytes":"availableOperationSlots")==0,"Prune reserve was spendable");
    Files.write(new File(root(f),UUID.randomUUID()+".operation-stage").toPath(),new byte[bytes?1:0]);
    inventory=inspect(f.store);check(flag(inventory,"usageComplete")&&flag(inventory,"pruneBlocked"),"Reserve shortage not blocked");
   }
  });
  test("maintenance blocks net zero completion conversion",()->{
   HistoryQuotaCheck.Fixture f=fixture();ProtectionStore.Snapshot original=f.store.putSnapshot("doc","uri","title",1,new byte[]{1},"original"),attempted=f.put(new byte[]{2});
   ProtectionStore.Transaction transaction=f.store.prepare(original,attempted,original.hash);enroll(f.store,root(f));
   Files.write(new File(root(f),UUID.randomUUID()+".prune-stage").toPath(),new byte[3]);Map<String,String> before=HistoryQuotaCheck.hashes(new File(f.parent,"store"));
   fails(()->f.store.complete(transaction),"Net zero exemption bypassed maintenance freeze");check(before.equals(HistoryQuotaCheck.hashes(new File(f.parent,"store"))),"Blocked completion changed bytes");
  });
  test("partial staged upgrade is never promoted or replaced",()->{
   HistoryQuotaCheck.Fixture f=fixture();f.disk.fail="file";fails(()->enroll(f.store,root(f)),"File fault acknowledged");f.disk.fail="";
   File stage=new File(new File(f.parent,"history_capacity"),"00000000000000000001.policy-stage");try(RandomAccessFile out=new RandomAccessFile(stage,"rw")){out.setLength(17);}
   Map<String,String> before=HistoryQuotaCheck.hashes(new File(f.parent,"history_capacity"));
   fails(()->enroll(f.store,root(f)),"Partial upgrade promoted");fails(()->f.store.recoverPolicy(),"Generic recovery promoted stage");
   fails(()->new HistoryPolicy(128*HistoryQuotaCheck.MIB,20).apply(f.store),"Policy update replaced pending binding");
   check(before.equals(HistoryQuotaCheck.hashes(new File(f.parent,"history_capacity"))),"Partial policy evidence changed");check(!flag(inspect(f.store),"enrolled"),"Incomplete stage activated maintenance");
  });
  test("all enrollment callbacks invalidate graph proofs including repeated enrollment",()->{
   HistoryQuotaCheck.Fixture f=fixture();HistoryGraph graph=new HistoryGraph(f.store,f.catalog,f.identity,f.index,null);int[] callbacks={0};boolean[] inspecting={false};
   f.disk.directoryCheck=directory->{if(inspecting[0])return;inspecting[0]=true;try{check(graph.scan().pruneBlocked,"Enrollment callback saw complete graph");callbacks[0]++;}catch(Exception failure){throw new IOException(failure);}finally{inspecting[0]=false;}};
   enroll(f.store,root(f));check(callbacks[0]>=3,"Initial durability callbacks missing");callbacks[0]=0;
   enroll(f.store,root(f));check(callbacks[0]>=3,"Repeated enrollment linkage was not proved");
  });
  System.out.println("History maintenance: "+cases+" cases, "+checks+" checks");
 }
}
