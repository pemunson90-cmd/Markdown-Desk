package com.example.markdowndesk;

import java.io.*;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Killed by the parent at real retry durability boundaries; each check runs in a fresh JVM. */
public final class CompletionRestartProbe {
 static final byte[] ORIGINAL="\ufefforiginal\r\n雪".getBytes(StandardCharsets.UTF_8);
 static final byte[] ATTEMPTED="\ufeffattempted\r\n🧭".getBytes(StandardCharsets.UTF_8);
 static final BufferedReader CONTROL=new BufferedReader(new InputStreamReader(System.in,StandardCharsets.UTF_8));
 static final class Disk implements ProtectionStore.Durability {
  boolean armed,failMove;
  void boundary(String name)throws IOException {
   if(!armed)return;System.out.println(name);System.out.flush();
   if(!"continue".equals(CONTROL.readLine()))throw new IOException("Missing continuation");
  }
  public void syncFile(FileDescriptor fd)throws IOException {boundary("file-before");fd.sync();boundary("file-after");}
  public void move(File from,File to)throws IOException {
   if(failMove)throw new IOException("Setup retained exact stage");
   boundary("move-before");Files.move(from.toPath(),to.toPath(),StandardCopyOption.ATOMIC_MOVE);boundary("move-after");
  }
  public void syncDirectory(File root)throws IOException {
   boundary("directory-before");try(FileChannel channel=FileChannel.open(root.toPath(),StandardOpenOption.READ)){channel.force(true);}boundary("directory-after");
  }
 }
 static void check(boolean value,String why){if(!value)throw new AssertionError(why);}
 public static void main(String[] args)throws Exception {
  File root=new File(args[1]);Disk disk=new Disk();ProtectionStore store=new ProtectionStore(root,disk,ProtectionStore.DEFAULT_QUOTA_BYTES);
  if(args[0].equals("create")){
   ProtectionStore.Snapshot a=store.putSnapshot("doc","content://fixture/doc","title",1,ORIGINAL,"original");
   ProtectionStore.Snapshot z=store.putSnapshot("doc","content://fixture/doc","title",1,ATTEMPTED,"attempted");
   ProtectionStore.Transaction t=store.prepare(a,z,a.hash);disk.failMove=true;
   try{store.complete(t);throw new AssertionError("Expected setup failure");}catch(IOException expected){}
   check(new File(root,t.id+".completion-stage").isFile(),"No exact retained stage");return;
  }
  ProtectionStore.Transaction t=store.transactions().get(0);
  if(args[0].equals("retry")){disk.armed=true;store.complete(t);throw new AssertionError("Parent must kill retry");}
  ProtectionStore.Inventory inventory=store.inspect();
  check(inventory.usageComplete&&inventory.reservedCompletionBytes==0&&inventory.reservedCompletionSlots==0,"Stage/commit double charged");
  check(root.list().length==4,"Retry grew entries");
  check(Arrays.equals(ORIGINAL,t.original.bytes())&&Arrays.equals(ATTEMPTED,t.attempted.bytes()),"Protected bytes changed");
  boolean moved=args[2].equals("move-after")||args[2].startsWith("directory-");
  if(args[0].equals("check"))check(t.verified==moved,"Completion state differs from kill boundary");
  if(args[0].equals("finish")){
   store.complete(t);store.complete(t);check(store.validate(t).verified,"Retry completion failed");
   check(!new File(root,t.id+".completion-stage").exists()&&root.list().length==4,"Finished retry retained extra stage");
  }
  System.out.println("PASS "+args[0]+" "+args[2]);
 }
}
