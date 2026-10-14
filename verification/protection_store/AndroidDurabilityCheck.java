package com.example.markdowndesk;
import android.system.Os;
import android.system.OsConstants;
import java.io.*;
import java.nio.file.*;
import java.util.*;
/** Exercises actual AndroidDurability with a labelled host Os boundary double. */
public final class AndroidDurabilityCheck {
 static int assertions;
 interface Action{void run()throws Exception;}
 static void check(boolean ok,String why){assertions++;if(!ok)throw new AssertionError(why);}
 static IOException fails(Action a)throws Exception{try{a.run();}catch(IOException e){assertions++;return e;}throw new AssertionError("failure accepted");}
 static void reset(){Os.calls.clear();Os.failure="";}
 public static void main(String[] args)throws Exception {
  AndroidDurability d=new AndroidDurability();File dir=Files.createTempDirectory("android-durability-").toFile();
  reset();d.syncDirectory(dir);check(Os.calls.equals(Arrays.asList("open","fstat","fsync","close")),"directory ordering");check((Os.flags&OsConstants.O_NOFOLLOW)!=0&&(Os.flags&OsConstants.O_CLOEXEC)!=0,"directory flags");
  for(String failure:new String[]{"open","fstat","fsync","close","fsync,close"}) {
   reset();Os.failure=failure;IOException e=fails(()->d.syncDirectory(dir));
   check(failure.equals("open")?!Os.calls.contains("close"):Os.calls.get(Os.calls.size()-1).equals("close"),"descriptor closure "+failure);
   if(failure.equals("fsync,close"))check(e.getSuppressed().length==1,"close masks fsync failure");
  }
  File regular=new File(dir,"regular");Files.write(regular.toPath(),new byte[]{1});reset();fails(()->d.syncDirectory(regular));check(Os.calls.equals(Arrays.asList("open","fstat","close")),"regular file accepted as directory");
  File link=new File(dir,"link");Files.createSymbolicLink(link.toPath(),dir.toPath());reset();fails(()->d.syncDirectory(link));check(Os.calls.equals(Collections.singletonList("open")),"symlink directory follow");
  try(FileOutputStream f=new FileOutputStream(regular)){reset();d.syncFile(f.getFD());check(Os.calls.equals(Collections.singletonList("fsync")),"file sync bypass");Os.failure="fsync";fails(()->d.syncFile(f.getFD()));}
  File stage=new File(dir,"stage"),destination=new File(dir,"destination");Files.write(stage.toPath(),new byte[]{2});reset();d.move(stage,destination);check(!stage.exists()&&Files.readAllBytes(destination.toPath())[0]==2,"atomic move failed");check(Os.calls.equals(Collections.singletonList("rename")),"move bypasses Os");
  Files.write(stage.toPath(),new byte[]{3});reset();fails(()->d.move(stage,destination));check(Os.calls.isEmpty()&&Files.readAllBytes(destination.toPath())[0]==2,"immutable destination overwritten");
  reset();Os.failure="rename";fails(()->d.move(stage,new File(dir,"failed")));check(stage.exists(),"failed rename loses stage");
  reset();fails(()->d.move(stage,new File(dir.getParentFile(),"other")));check(Os.calls.isEmpty(),"cross directory move allowed");
  System.out.println("AndroidDurability host boundary double: "+assertions+" assertions PASS");
 }
}
