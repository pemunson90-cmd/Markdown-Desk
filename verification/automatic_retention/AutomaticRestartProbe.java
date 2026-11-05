package com.example.markdowndesk;
import java.io.*;
import java.nio.file.*;
import java.util.*;
/** Finite named boundaries only; Python parent kills each child and reopens twice. */
public final class AutomaticRestartProbe {
 static void boundary(String actual,String wanted)throws IOException{
  if(actual.equals(wanted)){System.out.println("BOUNDARY "+actual);System.out.flush();while(System.in.read()!=-1){}throw new IOException("parent disconnected");}
 }
 public static void main(String[] args)throws Exception{
  String mode=args[0];Path root=Path.of(args[1]);ExecutorCheck.Bundle f=new ExecutorCheck.Bundle(root);
  if(mode.equals("init")){
   for(int i=0;i<7;i++){ProtectionStore.Transaction t=f.group(3,true);if(i==0)f.catalog.name(t.original.id,"protected name");}
   f.pending=f.group(2,false);f.capacity=HistoryCapacity.register(f.store,f.catalog,f.identity,f.index,new HistoryPolicy(64L<<20,5),f.disk);f.capacity.setPolicy(new HistoryPolicy(64L<<20,5),true);
   HistoryPruner.DeletePlan p=AutomaticRetentionCheck.plan(f).plan;for(ProtectionStore.StoredRecord m:p.members)System.out.println("REMOVE "+m.rootRole+"/"+m.filename);
   System.out.println("RESERVE "+f.capacity.inspect().completionReservedBytes);return;
  }
  if(mode.equals("reopen")){
   Map<String,String> before=f.capture();
   if(Files.exists(root.resolve("history_control/prune.intent.stage"))){AutomaticRetentionCheck.rejects(()->f.executor().replayAutomaticFloor());AutomaticRetentionCheck.ok(before.equals(f.capture()),"stage changed");System.out.println("BLOCKED");return;}
   f.capacity=f.executor().replayAutomaticFloor();AutomaticRetentionCheck.ok(!f.capacity.inspect().mutationBlocked&&f.capacity.inspect().completionReservedSlots==1,"terminal authority/reservation");
   AutomaticRetentionCheck.ok(f.store.snapshots().size()==14&&f.store.transactions().size()==7,"one group only");System.out.println("READY "+f.capacity.inspect().completionReservedBytes);return;
  }
  f.capacity=HistoryCapacity.open(f.store,f.catalog,f.identity,f.index,f.disk);String target=args[2];
  int[] member={-1};boolean[] memberSync={false},retired={false};
  f.disk.hook=(event,file)->{
   if(event.startsWith("file-")&&Files.exists(root.resolve("history_control/prune.intent.stage")))boundary("intent-"+event,target);
   if(event.startsWith("move-"))boundary("intent-"+event,target);
   if(event.startsWith("dir-")){
    if(memberSync[0]){boundary("member-"+member[0]+"-"+event,target);if(event.equals("dir-after"))memberSync[0]=false;}
    else if(retired[0]&&file.getName().equals("history_control"))boundary("retire-"+event,target);
    else if(member[0]==4&&!file.getName().equals("history_control"))boundary("retire-data-"+file.getName()+"-"+event,target);
    else if(member[0]<0&&Files.exists(root.resolve("history_control/prune.intent"))&&file.getName().equals("history_control"))boundary("intent-"+event,target);
   }
  };
  HistoryMaintenanceExecutor executor=f.executor((event,key)->{
   if(event==HistoryMaintenanceExecutor.Event.BEFORE_MEMBER_UNLINK){member[0]++;boundary("member-"+member[0]+"-before",target);}
   if(event==HistoryMaintenanceExecutor.Event.AFTER_MEMBER_UNLINK){boundary("member-"+member[0]+"-after",target);memberSync[0]=true;}
   if(event==HistoryMaintenanceExecutor.Event.BEFORE_INTENT_UNLINK)boundary("intent-unlink-before",target);
   if(event==HistoryMaintenanceExecutor.Event.AFTER_INTENT_UNLINK){retired[0]=true;boundary("intent-unlink-after",target);}
  });
  executor.executeAutomaticFloor(f.capacity,new HistoryMaintenanceExecutor.Cancellation());throw new AssertionError("unreached "+target);
 }
}
