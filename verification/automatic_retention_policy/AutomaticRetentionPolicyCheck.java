package com.example.markdowndesk;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.security.*;

/** Real codec/authority/filesystem regression fixtures; no cleanup runtime is invoked. */
public final class AutomaticRetentionPolicyCheck {
 static int checks;
 interface Work {void run()throws Exception;}
 static void ok(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
 static void rejects(Work w)throws Exception{try{w.run();throw new AssertionError("accepted");}catch(IOException expected){checks++;}}
 static final long Q=64L*1024*1024;
 static byte[] envelope(int type,byte[] body)throws Exception{
  ByteArrayOutputStream b=new ByteArrayOutputStream();DataOutputStream out=new DataOutputStream(b);
  out.writeInt(0x4d444843);out.writeInt(1);out.writeByte(type);out.writeInt(body.length);out.write(body);out.flush();out.write(MessageDigest.getInstance("SHA-256").digest(b.toByteArray()));return b.toByteArray();
 }
 static byte[] policy(UUID id,long generation,String hash,int count,Integer enabled,int type)throws Exception{
  ByteArrayOutputStream b=new ByteArrayOutputStream();DataOutputStream out=new DataOutputStream(b);
  out.writeLong(id.getMostSignificantBits());out.writeLong(id.getLeastSignificantBits());out.writeLong(generation);
  for(int i=0;i<32;i++)out.writeByte(Integer.parseInt(hash.substring(i*2,i*2+2),16));out.writeLong(Q);out.writeInt(count);if(enabled!=null)out.writeByte(enabled);return envelope(type,b.toByteArray());
 }
 static void codec()throws Exception{
  UUID id=UUID.fromString("00000000-0000-0000-0000-000000000001");String hash=String.join("",Collections.nCopies(64,"a"));
  byte[] legacy=policy(id,1,hash,20,null,2);
  ok(Arrays.equals(legacy,HistoryControlStore.encodePolicy(id,1,hash,Q,20)),"legacy bytes changed");
  ok(!HistoryControlStore.decode(legacy).automaticEnabled,"legacy count implies consent");
  for(boolean enabled:new boolean[]{false,true}){
   byte[] actual=HistoryControlStore.encodeAutomaticPolicy(id,1,hash,Q,20,enabled);
   ok(Arrays.equals(actual,policy(id,1,hash,20,enabled?1:0,3)),"variant independent bytes");
   ok(HistoryControlStore.decode(actual).automaticEnabled==enabled,"strict variant flag");
  }
  rejects(()->HistoryControlStore.decode(policy(id,1,hash,20,2,3)));
  rejects(()->HistoryControlStore.decode(policy(id,1,hash,0,1,3)));
  rejects(()->HistoryControlStore.decode(policy(id,1,hash,20,null,3)));
  rejects(()->HistoryControlStore.decode(policy(id,1,hash,20,0,2)));
  rejects(()->HistoryControlStore.decode(policy(id,1,hash,20,1,99)));
  rejects(()->HistoryControlStore.encodeAutomaticPolicy(id,1,hash,Q,0,true));
 }
 static void authority()throws Exception{
  HistoryCapacityCheck.Bundle b=new HistoryCapacityCheck.Bundle();HistoryCapacity c=b.register();
  ok(!c.policy().automaticEnabled&&c.policy().automaticCount==20&&c.policy().quotaBytes==Q,"default changed");
  Map<String,byte[]> original=HistoryCapacityCheck.capture(b.parent);
  ok(c.setPolicy(new HistoryPolicy(Q,20),false).generation==0,"explicit triple no-op appended");
  ok(HistoryCapacityCheck.same(original,HistoryCapacityCheck.capture(b.parent)),"no-op changed files");
  rejects(()->c.setPolicy(new HistoryPolicy(Q,0),true));
  ok(HistoryCapacityCheck.same(original,HistoryCapacityCheck.capture(b.parent)),"invalid enable changed files");
  HistoryCapacity.EffectivePolicy enabled=c.setPolicy(new HistoryPolicy(Q,20),true);
  ok(enabled.automaticEnabled&&enabled.generation==1,"opt-in missing");
  ok(c.inspect().controlActualEntries==3,"consent added extra slot");
  ok(c.setPolicy(new HistoryPolicy(Q,20),true).generation==1,"enabled no-op appended");
  HistoryCapacity reopened=b.open();ok(reopened.policy().automaticEnabled,"consent lost reopening");
  Map<String,byte[]> before=HistoryCapacityCheck.capture(b.parent);
  rejects(()->c.setPolicy(new HistoryPolicy(Q,20),true));
  ok(HistoryCapacityCheck.same(before,HistoryCapacityCheck.capture(b.parent)),"stale no-op changed files");
  HistoryCapacity.EffectivePolicy disabled=reopened.setPolicy(new HistoryPolicy(Q,20));
  ok(!disabled.automaticEnabled&&disabled.generation==2,"legacy call did not disable");
  byte[] written=Files.readAllBytes(b.parent.resolve("history_control/"+HistoryControlStore.policyFilename(2)));
  ok(Arrays.equals(written,HistoryControlStore.encodePolicy(UUID.fromString(enabled.bundleId),2,enabled.controlRecordHash,Q,20)),"legacy setter wire changed");
  ok(reopened.setPolicy(new HistoryPolicy(Q,20)).generation==3,"legacy append semantics changed");
 }
 static void faults()throws Exception{
  for(String fault:new String[]{"file","after-file","move","after-move","dir","after-dir"}){
   HistoryCapacityCheck.Bundle b=new HistoryCapacityCheck.Bundle();HistoryCapacity c=b.register();b.disk.fault=fault;
   rejects(()->c.setPolicy(new HistoryPolicy(Q,20),true));ok(c.inspect().mutationBlocked,"uncertainty unblocked "+fault);b.disk.fault="";
   ok(c.reconcile().automaticEnabled&&c.policy().generation==1,"variant stage/commit recovery "+fault);
   ok(c.reconcile().generation==1,"reconcile duplicate append");
  }
  HistoryCapacityCheck.Bundle b=new HistoryCapacityCheck.Bundle();HistoryCapacity c=b.register();
  byte[] invalid=policy(UUID.fromString(c.policy().bundleId),1,c.policy().controlRecordHash,20,2,3);
  Files.write(b.parent.resolve("history_control/"+HistoryControlStore.policyFilename(1)+".stage"),invalid);
  Map<String,byte[]> before=HistoryCapacityCheck.capture(b.parent);rejects(c::reconcile);
  ok(HistoryCapacityCheck.same(before,HistoryCapacityCheck.capture(b.parent)),"malformed stage modified");
 }
 static void noDeletionAndBindings()throws Exception{
  HistoryCapacityCheck.Bundle b=new HistoryCapacityCheck.Bundle();
  ProtectionStore.Snapshot original=HistoryCapacityCheck.put(b,"original"),edit=HistoryCapacityCheck.put(b,"attempted");
  ProtectionStore.Transaction transaction=b.store.prepare(original,edit,original.hash);b.store.complete(transaction);
  String history=b.identity.resolve("uri","doc");b.index.recordVerified(history,transaction.id);b.catalog.name(original.id,"named original");
  HistoryCapacity c=b.register();HistoryControlStore.Inspection before=new HistoryControlStore(b.parent.toFile()).inspect();
  PrivateOperationRecord.Binding old=binding(before);
  Map<String,byte[]> files=HistoryCapacityCheck.capture(b.parent);
  c.setPolicy(new HistoryPolicy(Q,5),true);
  Map<String,byte[]> afterFiles=HistoryCapacityCheck.capture(b.parent);
  for(String path:files.keySet())ok(Arrays.equals(files.get(path),afterFiles.get(path)),"policy altered existing bytes "+path);
  HistoryControlStore.Inspection after=new HistoryControlStore(b.parent.toFile()).inspect();
  ok(old.bound(after.registration,after.entries),"old hash-bound operation binding changed meaning");
  PrivateOperationRecord.Binding current=binding(after);ok(current.bound(after.registration,after.entries),"variant hash binding rejected");
  ok(!current.bound(before.registration,before.entries),"variant binding accepted missing policy");
  c.setPolicy(new HistoryPolicy(Q,5),false);HistoryControlStore.Inspection disabled=new HistoryControlStore(b.parent.toFile()).inspect();
  ok(old.bound(disabled.registration,disabled.entries)&&current.bound(disabled.registration,disabled.entries),"historical binding invalidated by consent change");
 }
 static PrivateOperationRecord.Binding binding(HistoryControlStore.Inspection inspection)throws Exception{
  HistoryControlStore.Registration r=inspection.registration;HistoryControlStore.Record p=inspection.latest;
  return new PrivateOperationRecord.Binding(UUID.randomUUID(),1,r.bundleId,r.envelopeHash,p.generation,p.envelopeHash,r.bootstrapQuotaBytes,p.quotaBytes,p.automaticCount,r.roles,r.envelopeHash,p.envelopeHash,0);
 }
 static void inventory()throws Exception{
  HistoryCapacityCheck.Bundle b=new HistoryCapacityCheck.Bundle();HistoryCapacity c=b.register();
  byte[] next=HistoryControlStore.encodeAutomaticPolicy(UUID.fromString(c.policy().bundleId),1,c.policy().controlRecordHash,Q,5,true);
  Path stage=b.parent.resolve("history_control/"+HistoryControlStore.policyFilename(1)+".stage");Files.write(stage,next);
  HistoryControlStore.Inspection inspection=new HistoryControlStore(b.parent.toFile()).inspect();
  ok(inspection.latest.generation==0&&!inspection.latest.automaticEnabled,"stage became effective consent");
  ok(inspection.stages.size()==1&&inspection.stages.get(0).state==HistoryControlStore.StageState.PROVISIONAL,"valid variant stage unrecognized");
  Map<String,byte[]> before=HistoryCapacityCheck.capture(b.parent);
  rejects(()->c.setPolicy(new HistoryPolicy(Q,20),false));
  ok(HistoryCapacityCheck.same(before,HistoryCapacityCheck.capture(b.parent)),"blocked triple no-op mutated stage");
  ok(c.reconcile().automaticEnabled&&c.policy().automaticCount==5,"explicit stage reconciliation");
  byte[] broken=HistoryControlStore.encodeAutomaticPolicy(UUID.fromString(c.policy().bundleId),2,String.join("",Collections.nCopies(64,"0")),Q,5,true);
  Files.write(b.parent.resolve("history_control/"+HistoryControlStore.policyFilename(2)),broken);
  inspection=new HistoryControlStore(b.parent.toFile()).inspect();
  ok(!inspection.chainComplete&&inspection.latest.generation==1,"variant bypassed hash chain");
  before=HistoryCapacityCheck.capture(b.parent);rejects(c::reconcile);
  ok(HistoryCapacityCheck.same(before,HistoryCapacityCheck.capture(b.parent)),"broken chain repaired implicitly");
 }
 // Exercise the production checked admission predicate at byte boundaries. A policy-only
 // chain hits the 256-entry limit before 1MiB, so this models accounted sibling occupancy;
 // the separate real-file over-limit fixture proves failure leaves no artifacts.
 static void byteBoundary()throws Exception{
  HistoryCapacityCheck.Bundle b=new HistoryCapacityCheck.Bundle();HistoryCapacity c=b.register();
  HistoryControlStore.Inspection original=new HistoryControlStore(b.parent.toFile()).inspect();
  long length=HistoryControlStore.encodeAutomaticPolicy(original.latest.bundleId,1,original.latest.envelopeHash,Q,20,true).length;
  java.lang.reflect.Constructor<HistoryControlStore.Inspection> ctor=HistoryControlStore.Inspection.class.getDeclaredConstructor(HistoryControlStore.State.class,HistoryControlStore.Registration.class,HistoryControlStore.Record.class,List.class,List.class,long.class,int.class,boolean.class,boolean.class,List.class);ctor.setAccessible(true);
  java.lang.reflect.Method admit=HistoryCapacity.class.getDeclaredMethod("admitControl",HistoryControlStore.Inspection.class,long.class,int.class);admit.setAccessible(true);
  for(int surplus:new int[]{0,1}){
   HistoryControlStore.Inspection near=ctor.newInstance(original.state,original.registration,original.latest,original.entries,original.stages,HistoryControlStore.MAX_BYTES-length+surplus,original.actualEntries,true,true,original.diagnostics);
   try{admit.invoke(null,near,length,1);ok(surplus==0,"byte overrun accepted");}
   catch(java.lang.reflect.InvocationTargetException failure){ok(surplus==1&&failure.getCause() instanceof IOException,"exact byte boundary refused");}
  }
 }
 static void limits()throws Exception{
  HistoryCapacityCheck.Bundle b=new HistoryCapacityCheck.Bundle();HistoryCapacity c=b.register();HistoryControlStore.Record last=new HistoryControlStore(b.parent.toFile()).inspect().latest;
  // Enrollment+registration occupy two shared slots. Build a fully valid near-full mixed chain.
  for(int i=1;i<=253;i++){
   byte[] bytes=i%2==0?HistoryControlStore.encodeAutomaticPolicy(last.bundleId,i,last.envelopeHash,Q,20,true):HistoryControlStore.encodePolicy(last.bundleId,i,last.envelopeHash,Q,20);
   Files.write(b.parent.resolve("history_control/"+HistoryControlStore.policyFilename(i)),bytes);last=HistoryControlStore.decode(bytes);
  }
  c=b.open();ok(c.inspect().controlActualEntries==255,"near-full entry fixture");
  ok(c.setPolicy(new HistoryPolicy(Q,20),true).generation==254,"last control slot refused");
  Map<String,byte[]> before=HistoryCapacityCheck.capture(b.parent);final HistoryCapacity full=c;
  rejects(()->full.setPolicy(new HistoryPolicy(Q,10),true));ok(HistoryCapacityCheck.same(before,HistoryCapacityCheck.capture(b.parent)),"entry exhaustion artifacts");
  HistoryCapacityCheck.Bundle bytes=new HistoryCapacityCheck.Bundle();HistoryCapacity byteCapacity=bytes.register();
  try(RandomAccessFile file=new RandomAccessFile(bytes.parent.resolve("history_control/unknown").toFile(),"rw")){file.setLength(HistoryControlStore.MAX_BYTES);}
  before=HistoryCapacityCheck.capture(bytes.parent);rejects(()->byteCapacity.setPolicy(new HistoryPolicy(Q,20),true));
  ok(HistoryCapacityCheck.same(before,HistoryCapacityCheck.capture(bytes.parent)),"byte exhaustion artifacts");
 }
 public static void main(String[] args)throws Exception{codec();authority();faults();noDeletionAndBindings();inventory();limits();byteBoundary();System.out.println("PASS automatic retention policy "+checks+" checks");}
}
