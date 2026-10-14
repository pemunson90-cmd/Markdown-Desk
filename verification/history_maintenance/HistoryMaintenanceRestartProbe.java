package com.example.markdowndesk;
import java.io.*;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.util.*;

/** Parent kills this actual publisher at durable enrollment boundaries. */
public final class HistoryMaintenanceRestartProbe {
 static final long Q=64L*1024*1024;
 static final BufferedReader CONTROL=new BufferedReader(new InputStreamReader(System.in));
 static final class Disk implements ProtectionStore.Durability {
  boolean armed;
  void boundary(String point)throws IOException{if(armed){System.out.println(point);System.out.flush();if(!"continue".equals(CONTROL.readLine()))throw new IOException("Missing continuation");}}
  public void syncFile(FileDescriptor fd)throws IOException{boundary("file-before");fd.sync();boundary("file-after");}
  public void move(File from,File to)throws IOException{boundary("move-before");Files.move(from.toPath(),to.toPath(),StandardCopyOption.ATOMIC_MOVE);boundary("move-after");}
  public void syncDirectory(File root)throws IOException{
   String point=root.getName().equals("history_maintenance")?"maintenance-directory":root.getName().equals("history_capacity")?"policy-directory":"parent-directory";
   boundary(point+"-before");try(FileChannel channel=FileChannel.open(root.toPath(),StandardOpenOption.READ)){channel.force(true);}boundary(point+"-after");
  }
 }
 static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
 public static void main(String[] args)throws Exception{
  File parent=new File(args[1]),maintenance=new File(parent,"history_maintenance");Disk disk=new Disk();
  ProtectionStore store=new ProtectionStore(new File(parent,"store"),disk,Q);
  HistoryCatalog catalog=new HistoryCatalog(new File(parent,"catalog"),store,disk);
  HistoryIdentity identity=new HistoryIdentity(new File(parent,"identity"),disk);
  HistoryIndex index=new HistoryIndex(store,catalog,identity,new File(parent,"index"),disk);
  if(args[0].equals("seed-staged")){
   store.putSnapshot("doc","uri","title",1,new byte[]{1,2,3},"attempted");new HistoryPolicy(Q,20).configure(store,catalog,identity,index);
   Files.createDirectory(maintenance.toPath());Files.write(new File(maintenance,UUID.randomUUID()+".prune-stage").toPath(),new byte[17]);
   HistoryMaintenance.enroll(store,maintenance);System.out.println("PASS seed-staged");return;
  }
  if(args[0].equals("enroll")){
   store.putSnapshot("doc","uri","title",1,new byte[]{1,2,3},"attempted");new HistoryPolicy(Q,20).configure(store,catalog,identity,index);
   disk.armed=true;disk.boundary("enroll-before");HistoryMaintenance.enroll(store,maintenance);throw new AssertionError("Parent must kill enrollment");
  }
  check(store.snapshots().size()==1&&Arrays.equals(store.snapshots().get(0).bytes(),new byte[]{1,2,3}),"Native recovery changed");
  if(args[0].equals("inspect-staged")){
   HistoryMaintenance.Inventory inventory=HistoryMaintenance.inspect(store);
   check(inventory.enrolled&&inventory.actualBytes==17&&inventory.occupiedEntries==1&&inventory.usageComplete&&inventory.pruneBlocked,"Fresh process dropped retained maintenance stage");
   check(HistoryPolicy.inspect(store).maintenanceActualBytes==17,"Fresh capacity omitted maintenance stage");
   check(new HistoryGraph(store,catalog,identity,index,null).scan().pruneBlocked,"Staged maintenance allowed proof");
  }else if(args[0].equals("inspect")){
   check(HistoryPolicy.current(store).quotaBytes==Q,"Maintenance altered effective quota");
   check(HistoryPolicy.inspect(store).mutationBlocked,"Fresh JVM bypassed recovery");
   check(new HistoryGraph(store,catalog,identity,index,null).scan().pruneBlocked,"Fresh JVM allowed unconfirmed graph");
   try{store.putSnapshot("doc","uri","title",1,new byte[]{4},"attempted");throw new AssertionError("Recovery pending admitted mutation");}catch(IOException expected){}
  }else if(args[0].equals("recover")){
   HistoryMaintenance.enroll(store,maintenance);HistoryMaintenance.Inventory inventory=HistoryMaintenance.inspect(store);
   check(inventory.enrolled&&!inventory.pruneBlocked&&inventory.actualBytes==0,"Explicit maintenance retry failed");
   check(!HistoryPolicy.inspect(store).mutationBlocked,"Explicit recovery remains blocked");
   check(new File(parent,"history_capacity").list().length==2,"Enrollment duplicated policy records");
  }else throw new AssertionError("Unknown mode");
  System.out.println("PASS "+args[0]);
 }
}
