package com.example.markdowndesk;
import java.io.*;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.util.*;

/** Actual child process policy publication/recovery, with external SIGKILL control. */
public final class HistoryQuotaRestartProbe {
 static final long Q=64L*1024*1024;
 static final BufferedReader CONTROL=new BufferedReader(new InputStreamReader(System.in));
 static final class Disk implements ProtectionStore.Durability {
  boolean armed,initial;
  void boundary(String name)throws IOException{if(armed){System.out.println(name);System.out.flush();if(!"continue".equals(CONTROL.readLine()))throw new IOException("Missing continuation");}}
  public void syncFile(FileDescriptor fd)throws IOException{boundary("file-before");fd.sync();boundary("file-after");}
  public void move(File from,File to)throws IOException{boundary("move-before");Files.move(from.toPath(),to.toPath(),StandardCopyOption.ATOMIC_MOVE);boundary("move-after");}
  public void syncDirectory(File root)throws IOException{
   String name="directory";
   if(initial){name=root.getName().equals("history_capacity")?(Arrays.stream(root.list()).anyMatch(file->file.endsWith(".policy"))?"commit-directory":"policy-directory"):"parent-directory";}
   boundary(name+"-before");try(FileChannel channel=FileChannel.open(root.toPath(),StandardOpenOption.READ)){channel.force(true);}boundary(name+"-after");
  }
 }
 static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
 public static void main(String[] args)throws Exception{
  File parent=new File(args[1]);Disk disk=new Disk();ProtectionStore store=new ProtectionStore(new File(parent,"store"),disk,Q);
  HistoryCatalog catalog=new HistoryCatalog(new File(parent,"catalog"),store,disk);
  HistoryIdentity identity=new HistoryIdentity(new File(parent,"identity"),disk);
  HistoryIndex index=new HistoryIndex(store,catalog,identity,new File(parent,"index"),disk);
  if(args[0].equals("register")){
   store.putSnapshot("doc","uri","title",1,new byte[]{1,2,3},"attempted");disk.initial=true;disk.armed=true;disk.boundary("register-before");
   new HistoryPolicy(Q,20).configure(store,catalog,identity,index);throw new AssertionError("Parent must kill initial registration");
  }
  if(args[0].equals("initial-inspect")||args[0].equals("initial-recover")){
   check(store.snapshots().size()==1&&Arrays.equals(store.snapshots().get(0).bytes(),new byte[]{1,2,3}),"Initial registration changed native recovery");
   if(args[0].equals("initial-inspect")){
    File ledger=new File(parent,"history_capacity");boolean committed=ledger.isDirectory()&&Arrays.stream(ledger.list()).anyMatch(name->name.endsWith(".policy"));
    if(committed)check(HistoryPolicy.current(store).quotaBytes==Q&&HistoryPolicy.inspect(store).mutationBlocked,"Initial committed registry skipped recovery");
    else try{HistoryPolicy.current(store);throw new AssertionError("Incomplete registration exposed an active policy");}catch(IOException expected){}
   }else{
    new HistoryPolicy(Q,20).configure(store,catalog,identity,index);check(HistoryPolicy.current(store).quotaBytes==Q&&!HistoryPolicy.inspect(store).mutationBlocked,"Initial registration retry failed");
    check(new File(parent,"history_capacity").list().length==1,"Initial retry duplicated policy records");
   }
   System.out.println("PASS "+args[0]);return;
  }
  if(args[0].equals("change")){
   store.putSnapshot("doc","uri","title",1,new byte[]{1,2,3},"attempted");new HistoryPolicy(Q,20).configure(store,catalog,identity,index);
   disk.armed=true;store.setQuotaBytes(2*Q);throw new AssertionError("Parent must kill publication");
  }
  boolean staged=Arrays.stream(new File(parent,"history_capacity").list()).anyMatch(name->name.endsWith(".policy-stage"));
  check(store.snapshots().size()==1&&Arrays.equals(store.snapshots().get(0).bytes(),new byte[]{1,2,3}),"Native recovery changed");
  if(args[0].equals("inspect")){
   check(HistoryPolicy.current(store).quotaBytes==(staged?Q:2*Q),"Stage silently changed effective policy");
   check(HistoryPolicy.inspect(store).mutationBlocked,"Fresh process skipped explicit policy recovery");
   try{store.putSnapshot("doc","uri","title",1,new byte[]{4},"attempted");throw new AssertionError("Pending recovery admits write");}catch(IOException expected){}
   if(staged)try{store.recoverPolicy();throw new AssertionError("Recovery implicitly promotes stage");}catch(IOException expected){}
  }else if(args[0].equals("recover")){
   if(staged)store.setQuotaBytes(2*Q);else store.recoverPolicy();
   check(HistoryPolicy.current(store).quotaBytes==2*Q&&!HistoryPolicy.inspect(store).mutationBlocked,"Explicit exact policy retry did not recover");
   check(new File(parent,"history_capacity").list().length==2,"Recovery duplicated policy entries");
  }else throw new AssertionError("Unknown probe mode");
  System.out.println("PASS "+args[0]);
 }
}
