package com.example.markdowndesk;
import java.io.*;
import java.nio.file.*;
import java.util.*;
public final class AutomaticRetentionCheck {
 static int checks;
 interface Work{void run()throws Exception;}
 static void ok(boolean b,String why){checks++;if(!b)throw new AssertionError(why);}
 static void rejects(Work w)throws Exception{try{w.run();throw new AssertionError("unexpected acceptance");}catch(IOException expected){checks++;}}
 static ExecutorCheck.Bundle fixture(int count,boolean enabled)throws Exception{
  ExecutorCheck.Bundle f=new ExecutorCheck.Bundle();for(int i=0;i<count;i++){ProtectionStore.Transaction t=f.group(3,true);if(i==0)f.old=t;f.anchor=t;}
  f.capacity=HistoryCapacity.register(f.store,f.catalog,f.identity,f.index,new HistoryPolicy(64L<<20,5),f.disk);
  if(enabled)f.capacity.setPolicy(new HistoryPolicy(64L<<20,5),true);return f;
 }
 static HistoryPruner.Result plan(ExecutorCheck.Bundle f)throws Exception{return new HistoryPruner(f.graph()).planAutomaticFloorBatch();}
 static void sweep()throws Exception{
  ExecutorCheck.Bundle f=fixture(6,true);HistoryPruner.DeletePlan p=plan(f).plan;
  ok(p!=null&&p.groups.size()==1&&p.members.size()==5&&p.requiredBytes==0&&p.requiredEntries==0,"one-group floor shape");
  ok(p.groups.get(0).transactionId.equals(f.old.id),"oldest chosen");
  ok(new HistoryPruner(f.graph()).planAutomatic(0,0).plan.members.isEmpty(),"old demand planner changed");
  Map<String,String> before=f.capture();long bytes=p.bytes;
  HistoryMaintenanceExecutor.AutomaticResult result=f.executor().executeAutomaticFloor(f.capacity,new HistoryMaintenanceExecutor.Cancellation());f.capacity=result.capacity;
  ok(result.status==HistoryMaintenanceExecutor.AutomaticStatus.COMPLETED&&result.deletedGroups==1&&result.deletedBytes==bytes,"completion totals");
  Set<String> victims=new HashSet<>();for(ProtectionStore.StoredRecord m:p.members)victims.add(m.rootRole+"/"+m.filename);
  Map<String,String> after=f.capture();ok(after.size()==before.size()-5,"surplus mutation");for(String key:before.keySet())if(!victims.contains(key))ok(before.get(key).equals(after.get(key)),"survivor changed "+key);
  ok(plan(f).blockedReason==HistoryPruner.BlockedReason.NO_WORK,"floor not reached");
  ok(f.executor().executeAutomaticFloor(f.capacity,new HistoryMaintenanceExecutor.Cancellation()).status==HistoryMaintenanceExecutor.AutomaticStatus.NO_WORK,"no-work status");
 }
 static void protectedAndDisabled()throws Exception{
  ExecutorCheck.Bundle f=fixture(7,true);f.catalog.name(f.old.original.id,"keep");HistoryPruner.DeletePlan p=plan(f).plan;
  ok(!p.groups.get(0).transactionId.equals(f.old.id),"named old victim");
  HistoryPruner.Result pinned=new HistoryPruner(f.graph(),Collections.singleton(p.groups.get(0).originalSnapshotId)).planAutomaticFloorBatch();ok(pinned.blockedReason==HistoryPruner.BlockedReason.NO_WORK,"external pin ignored");
  f.pending=f.group(1,false);long reserve=f.capacity.inspect().completionReservedBytes;Map<String,String> before=f.capture();
  HistoryMaintenanceExecutor.AutomaticResult r=f.executor().executeAutomaticFloor(f.capacity,new HistoryMaintenanceExecutor.Cancellation());f.capacity=r.capacity;
  ok(r.status==HistoryMaintenanceExecutor.AutomaticStatus.COMPLETED,"unrelated pending prevented safe group");
  ok(f.capacity.inspect().completionReservedBytes==reserve&&f.store.transactions().stream().anyMatch(t->t.id.equals(f.pending.id)),"pending reservation lost");
  ok(before.get("store/"+f.old.original.id+".snapshot").equals(f.capture().get("store/"+f.old.original.id+".snapshot")),"named bytes changed");
  ExecutorCheck.Bundle off=fixture(6,false);before=off.capture();
  ok(off.executor().executeAutomaticFloor(off.capacity,new HistoryMaintenanceExecutor.Cancellation()).status==HistoryMaintenanceExecutor.AutomaticStatus.DISABLED,"legacy consent inferred");
  ok(before.equals(off.capture()),"disabled changed files");
  off.capacity.setPolicy(new HistoryPolicy(64L<<20,5),false);before=off.capture();
  ok(off.executor().executeAutomaticFloor(off.capacity,new HistoryMaintenanceExecutor.Cancellation()).status==HistoryMaintenanceExecutor.AutomaticStatus.DISABLED,"explicit disabled ignored");ok(before.equals(off.capture()),"disabled variant changed files");
 }
 static void admissionAndCancellation()throws Exception{
  ExecutorCheck.Bundle f=fixture(6,true);HistoryCapacity stale=f.capacity;f.capacity=HistoryCapacity.open(f.store,f.catalog,f.identity,f.index,f.disk);rejects(()->f.executor().executeAutomaticFloor(stale,new HistoryMaintenanceExecutor.Cancellation()));
  HistoryMaintenanceExecutor.Cancellation cancel=new HistoryMaintenanceExecutor.Cancellation();cancel.cancel();Map<String,String> before=f.capture();
  ok(f.executor().executeAutomaticFloor(f.capacity,cancel).status==HistoryMaintenanceExecutor.AutomaticStatus.CANCELLED_BEFORE_PUBLICATION,"prepublication cancellation");ok(before.equals(f.capture()),"cancel artifact");
  HistoryMaintenanceExecutor.Cancellation late=new HistoryMaintenanceExecutor.Cancellation();f.disk.hook=(event,file)->{if(Files.exists(f.parent.resolve("history_control/prune.intent.stage")))late.cancel();};
  ok(f.executor().executeAutomaticFloor(f.capacity,late).status==HistoryMaintenanceExecutor.AutomaticStatus.COMPLETED&&late.isCancelled(),"late cancel abandoned journal");
  ExecutorCheck.Bundle stage=fixture(6,true);Files.write(stage.parent.resolve("history_control/unknown.stage"),new byte[]{1});before=stage.capture();rejects(()->stage.executor().executeAutomaticFloor(stage.capacity,new HistoryMaintenanceExecutor.Cancellation()));ok(before.equals(stage.capture()),"uncertain stage artifacts");
 }
 static void replacementAndReentry()throws Exception{
  ExecutorCheck.Bundle f=fixture(6,true);boolean[] fired={false};
  HistoryMaintenanceExecutor executor=f.executor((event,key)->{if(!fired[0]&&event==HistoryMaintenanceExecutor.Event.BEFORE_MEMBER_UNLINK){fired[0]=true;Path victim=f.parent.resolve(key);byte[] same=Files.readAllBytes(victim);Path tmp=victim.resolveSibling("replacement");Files.write(tmp,same);Files.move(tmp,victim,StandardCopyOption.REPLACE_EXISTING);}});
  rejects(()->executor.executeAutomaticFloor(f.capacity,new HistoryMaintenanceExecutor.Cancellation()));ok(f.control().committedIntent!=null,"replacement discarded journal");try(java.util.stream.Stream<Path> files=Files.list(f.parent.resolve("store"))){ok(files.filter(p->p.toString().endsWith(".snapshot")).count()==12,"replacement caused deletion");}
  ExecutorCheck.Bundle nested=fixture(6,true);boolean[] checked={false};nested.disk.hook=(event,file)->{if(!checked[0]&&Files.exists(nested.parent.resolve("history_control/prune.intent.stage"))){checked[0]=true;try{rejects(()->nested.executor().executeAutomaticFloor(nested.capacity,new HistoryMaintenanceExecutor.Cancellation()));rejects(()->nested.executor().replayAutomaticFloor());}catch(Exception e){throw new IOException(e);}}};
  ok(nested.executor().executeAutomaticFloor(nested.capacity,new HistoryMaintenanceExecutor.Cancellation()).status==HistoryMaintenanceExecutor.AutomaticStatus.COMPLETED&&checked[0],"reentry damaged outer pass");
 }
 static void nativeGuards()throws Exception{
  ExecutorCheck.Bundle zero=fixture(6,true);zero.capacity.setPolicy(new HistoryPolicy(64L<<20,0),false);ok(plan(zero).blockedReason==HistoryPruner.BlockedReason.AUTOMATIC_DISABLED,"zero floor accepted");
  for(String fault:new String[]{"pin","unknown","policy-stage","orphan","unindexed","shared"}){
   ExecutorCheck.Bundle f=fixture(6,true);
   if(fault.equals("pin")){HistoryPins pins=HistoryPins.open(f.store,f.catalog,f.identity,f.index,f.disk);pins.begin(HistoryPinRecord.Purpose.EXPORT,HistoryPinRecord.Scope.ALL_BUNDLE,Collections.emptySet(),HistoryPins.ManifestIdentity.read(HistoryPinRecord.Domain.NATIVE_HISTORY_V1,0,new ByteArrayInputStream(new byte[0])));}
   if(fault.equals("unknown"))Files.write(f.parent.resolve("store/unknown"),new byte[]{1});
   if(fault.equals("policy-stage"))Files.write(f.parent.resolve("history_control/"+HistoryControlStore.policyFilename(2)+".stage"),new byte[]{1});
   String retained=null;
   if(fault.equals("orphan")){ProtectionStore.Snapshot orphan=f.store.putSnapshot("orphan",null,null,1,new byte[]{4},"original");retained="store/"+orphan.id+".snapshot";}
   if(fault.equals("unindexed")){ProtectionStore.Transaction t=f.group(2,false);f.store.complete(t);retained="store/"+t.id+".transaction";}
   if(fault.equals("shared")){ProtectionStore.Snapshot extra=f.store.putSnapshot("tab","uri","title",1,new byte[]{3},"attempted");ProtectionStore.Transaction t=f.store.prepare(f.old.original,extra,f.old.original.hash);f.store.complete(t);f.index.recordVerified(f.identity.resolve("uri","tab"),t.id);retained="store/"+f.old.original.id+".snapshot";}
   Map<String,String> before=f.capture();
   if(retained==null){rejects(()->f.executor().executeAutomaticFloor(f.capacity,new HistoryMaintenanceExecutor.Cancellation()));ok(before.equals(f.capture()),"blocked native evidence changed "+fault);}
   else{HistoryPruner.DeletePlan p=plan(f).plan;for(ProtectionStore.StoredRecord m:p.members)ok(!(m.rootRole+"/"+m.filename).equals(retained),"protected victim "+fault);f.capacity=f.executor().executeAutomaticFloor(f.capacity,new HistoryMaintenanceExecutor.Cancellation()).capacity;ok(before.get(retained).equals(f.capture().get(retained)),"protected native changed "+fault);}
  }
  ExecutorCheck.Bundle two=fixture(6,true);String h=two.identity.resolve("uri2","tab2");
  for(int i=0;i<6;i++){ProtectionStore.Snapshot a=two.store.putSnapshot("tab2","uri2",null,1,new byte[]{1},"original"),b=two.store.putSnapshot("tab2","uri2",null,1,new byte[]{2},"attempted");ProtectionStore.Transaction t=two.store.prepare(a,b,a.hash);two.store.complete(t);two.index.recordVerified(h,t.id);}
  HistoryPruner.DeletePlan first=plan(two).plan;String firstHistory=first.groups.get(0).historyId;two.capacity=two.executor().executeAutomaticFloor(two.capacity,new HistoryMaintenanceExecutor.Cancellation()).capacity;
  HistoryPruner.DeletePlan second=plan(two).plan;ok(second!=null&&!second.groups.get(0).historyId.equals(firstHistory),"per-history floor not retained");
  two.capacity=two.executor().executeAutomaticFloor(two.capacity,new HistoryMaintenanceExecutor.Cancellation()).capacity;ok(plan(two).blockedReason==HistoryPruner.BlockedReason.NO_WORK,"two-history floor failed");
 }
 static void fullControlAndNoIntent()throws Exception{
  ExecutorCheck.Bundle noIntent=fixture(6,false);Map<String,String> before=noIntent.capture();noIntent.capacity=noIntent.executor().replayAutomaticFloor();ok(before.equals(noIntent.capture())&&!noIntent.capacity.inspect().mutationBlocked,"no-intent replay inferred deletion");
  ExecutorCheck.Bundle full=fixture(6,true);HistoryControlStore.Record prior=full.control().latest;
  for(int generation=2;generation<=254;generation++){byte[] bytes=HistoryControlStore.encodeAutomaticPolicy(prior.bundleId,generation,prior.envelopeHash,64L<<20,5,true);Files.write(full.parent.resolve("history_control/"+HistoryControlStore.policyFilename(generation)),bytes);prior=HistoryControlStore.decode(bytes);}
  full.capacity=HistoryCapacity.open(full.store,full.catalog,full.identity,full.index,full.disk);ok(full.capacity.inspect().controlActualEntries==256,"full-control fixture");before=full.capture();rejects(()->full.executor().executeAutomaticFloor(full.capacity,new HistoryMaintenanceExecutor.Cancellation()));ok(before.equals(full.capture()),"intent entry exhaustion created artifacts");
 }
 static void memory()throws Exception{
  ExecutorCheck.Bundle f=new ExecutorCheck.Bundle();f.old=f.group(ProtectionStore.MAX_DOCUMENT_BYTES,true);for(int i=0;i<5;i++)f.anchor=f.group(3,true);
  f.capacity=HistoryCapacity.register(f.store,f.catalog,f.identity,f.index,new HistoryPolicy(64L<<20,5),f.disk);f.capacity.setPolicy(new HistoryPolicy(64L<<20,5),true);
  byte[] pressure=new byte[32*1024*1024];HistoryMaintenanceExecutor.AutomaticResult result=f.executor().executeAutomaticFloor(f.capacity,new HistoryMaintenanceExecutor.Cancellation());
  ok(result.status==HistoryMaintenanceExecutor.AutomaticStatus.COMPLETED&&result.capacity.inspect().dataActualBytes<1024*1024,"large pair not streamed/removed");ok(pressure[pressure.length-1]==0,"32MiB live pressure survives");
 }
 static void survivorAndPolicyReplacement()throws Exception{
  for(String fault:new String[]{"survivor","policy","root"}){
   ExecutorCheck.Bundle f=fixture(6,true);boolean[] fired={false};
   HistoryMaintenanceExecutor executor=f.executor((event,key)->{if(!fired[0]&&event==HistoryMaintenanceExecutor.Event.BEFORE_MEMBER_UNLINK){fired[0]=true;
    if(fault.equals("root")){Path root=f.parent.resolve("store"),tmp=f.parent.resolve("store-moved");Files.move(root,tmp);Files.createDirectory(root);try(java.util.stream.Stream<Path> children=Files.list(tmp)){for(Path child:(Iterable<Path>)children::iterator)Files.move(child,root.resolve(child.getFileName()));}Files.delete(tmp);}
    else{Path file=fault.equals("policy")?f.parent.resolve("history_control/"+HistoryControlStore.policyFilename(1)):f.parent.resolve("store/"+f.anchor.original.id+".snapshot");byte[] bytes=Files.readAllBytes(file);if(fault.equals("policy"))bytes[bytes.length-1]^=1;Path replacement=file.resolveSibling("replacement");Files.write(replacement,bytes);Files.move(replacement,file,StandardCopyOption.REPLACE_EXISTING);}
   }});
   rejects(()->executor.executeAutomaticFloor(f.capacity,new HistoryMaintenanceExecutor.Cancellation()));try(java.util.stream.Stream<Path> files=Files.list(f.parent.resolve("store"))){ok(files.filter(p->p.toString().endsWith(".snapshot")).count()==12,"replacement deleted native "+fault);}ok(fired[0],"replacement not reached");
  }
 }
 static HistoryPruneIntent intent(ExecutorCheck.Bundle f,HistoryPruner.DeletePlan p)throws Exception{
  HistoryCapacity.EffectivePolicy policy=f.capacity.policy();return HistoryPruneIntent.create(p,f.graph().scan(),new HistoryPruneIntent.PolicyBinding(policy.bundleId,policy.generation,policy.controlRecordHash,policy.quotaBytes,policy.automaticCount),ExecutorCheck.auth(p),HistoryMaintenanceRecovery.unaffectedFingerprint(f.graph().scan(),p.members));
 }
 static void replayGuards()throws Exception{
  for(String mode:new String[]{"legacy","disabled","pressure","selected"}){
   ExecutorCheck.Bundle f=fixture(6,!mode.equals("legacy"));if(mode.equals("disabled"))f.capacity.setPolicy(new HistoryPolicy(64L<<20,5),false);HistoryPruner.DeletePlan p;
   if(mode.equals("pressure"))p=new HistoryPruner(f.graph()).planAutomaticPressure(1,0).plan;
   else if(mode.equals("selected")){String name=f.catalog.name(f.old.original.id,"selected").id;p=new HistoryPruner(f.graph()).planSelected(Collections.singleton(name)).plan;}
   else p=plan(f).plan;
   HistoryPruneIntent i=intent(f,p);Files.write(f.parent.resolve("history_control/prune.intent"),HistoryPruneIntent.encode(i));Map<String,String> before=f.capture();
   rejects(()->f.executor().replayAutomaticFloor());ok(before.equals(f.capture()),"wrong replay modified "+mode);
  }
  ExecutorCheck.Bundle auto=fixture(6,true);HistoryMaintenanceExecutor automatic=auto.executor((event,key)->{if(event==HistoryMaintenanceExecutor.Event.AFTER_INTENT_UNLINK)throw new IOException("after intent unlink");});
  rejects(()->automatic.executeAutomaticFloor(auto.capacity,new HistoryMaintenanceExecutor.Cancellation()));
  ok(HistoryStorage.retirement(auto.store.storageRoot())!=null&&!Files.exists(auto.parent.resolve("history_control/prune.intent")),"missing automatic retirement fixture");
  Map<String,String> before=auto.capture();rejects(()->auto.executor().replay());ok(before.equals(auto.capture()),"selected replay tunneled floor retirement");auto.capacity=auto.executor().replayAutomaticFloor();ok(!auto.capacity.inspect().mutationBlocked,"floor retirement retry failed");
  ExecutorCheck.Bundle selected=new ExecutorCheck.Bundle();selected.prepare(3,false);
  HistoryMaintenanceExecutor chosen=selected.executor((event,key)->{if(event==HistoryMaintenanceExecutor.Event.AFTER_INTENT_UNLINK)throw new IOException("after intent unlink");});
  rejects(()->chosen.execute(selected.capacity,selected.plan,ExecutorCheck.auth(selected.plan)));before=selected.capture();
  rejects(()->selected.executor().replayAutomaticFloor());ok(before.equals(selected.capture()),"floor replay tunneled selected retirement");selected.capacity=selected.executor().replay();ok(!selected.capacity.inspect().mutationBlocked,"selected retirement retry failed");
 }
 static void finiteFaults()throws Exception{
  for(String event:new String[]{"file-before","file-after","move-before","move-after","dir-before","dir-after"}){
   ExecutorCheck.Bundle f=fixture(6,true);Map<String,String> original=f.capture();boolean[] fired={false};
   f.disk.hook=(at,file)->{boolean publication=at.startsWith("move-")||at.startsWith("file-")&&Files.exists(f.parent.resolve("history_control/prune.intent.stage"))||at.startsWith("dir-")&&file.getName().equals("history_control")&&Files.exists(f.parent.resolve("history_control/prune.intent"));if(!fired[0]&&at.equals(event)&&publication){fired[0]=true;throw new IOException("injected publication");}};
   rejects(()->f.executor().executeAutomaticFloor(f.capacity,new HistoryMaintenanceExecutor.Cancellation()));ok(fired[0],"fault not reached");f.disk.hook=null;
   if(f.control().intentStage!=null){Map<String,String> retained=f.capture();rejects(()->f.executor().replayAutomaticFloor());ok(retained.equals(f.capture()),"stage replay altered bytes");for(String path:original.keySet())ok(original.get(path).equals(retained.get(path)),"stage removed native");}
   else{f.capacity=f.executor().replayAutomaticFloor();ok(f.store.snapshots().size()==10&&!f.capacity.inspect().mutationBlocked,"publication replay failed");}
  }
  for(HistoryMaintenanceExecutor.Event event:HistoryMaintenanceExecutor.Event.values()){
   int repetitions=event==HistoryMaintenanceExecutor.Event.BEFORE_MEMBER_UNLINK||event==HistoryMaintenanceExecutor.Event.AFTER_MEMBER_UNLINK?5:1;
   for(int victim=0;victim<repetitions;victim++){
    ExecutorCheck.Bundle f=fixture(6,true);int[] seen={0};final int wanted=victim;
    HistoryMaintenanceExecutor executor=f.executor((at,key)->{if(at==event&&seen[0]++==wanted)throw new IOException("injected boundary");});
    rejects(()->executor.executeAutomaticFloor(f.capacity,new HistoryMaintenanceExecutor.Cancellation()));
    f.capacity=f.executor().replayAutomaticFloor();ok(f.store.snapshots().size()==10&&!f.capacity.inspect().mutationBlocked,"boundary replay failed "+event+victim);
   }
  }
 }
 public static void main(String[] args)throws Exception{if(args.length>0){memory();System.out.println("PASS automatic retention memory "+checks+" checks");return;}sweep();protectedAndDisabled();admissionAndCancellation();replacementAndReentry();replayGuards();finiteFaults();nativeGuards();survivorAndPolicyReplacement();fullControlAndNoIntent();System.out.println("PASS automatic retention "+checks+" checks");}
}
