package com.example.markdowndesk;
import java.io.*;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.util.*;
public final class HistoryCapacityCheck {
 static int checks; interface Task {void run()throws Exception;}
 static void check(boolean good,String why){checks++;if(!good)throw new AssertionError(why);}
 static void rejects(Task work,String why)throws Exception{try{work.run();throw new AssertionError(why);}catch(IOException expected){checks++;}}
 static class Disk implements ProtectionStore.Durability {
  String fault="";Task callback;boolean controlOnly=true;
  void event(String name)throws IOException{if(callback!=null){Task action=callback;callback=null;try{action.run();}catch(Exception e){throw new IOException(e);}}if(fault.equals(name))throw new IOException("injected "+name);}
  public void syncFile(FileDescriptor fd)throws IOException{event("file");fd.sync();event("after-file");}
  public void move(File a,File b)throws IOException{event("move");Files.move(a.toPath(),b.toPath(),StandardCopyOption.ATOMIC_MOVE);event("after-move");}
  public void syncDirectory(File dir)throws IOException{if(!controlOnly||dir.getName().equals("history_control"))event("dir");try(FileChannel c=FileChannel.open(dir.toPath(),StandardOpenOption.READ)){c.force(true);}if(!controlOnly||dir.getName().equals("history_control"))event("after-dir");}
 }
 static class Bundle {
  final Path parent;final Disk disk;final ProtectionStore store;final HistoryCatalog catalog;final HistoryIdentity identity;final HistoryIndex index;
  Bundle()throws Exception{this(Files.createTempDirectory("capacity-"),new Disk(),ProtectionStore.DEFAULT_QUOTA_BYTES);}
  Bundle(Path p,Disk d,long hint)throws Exception{parent=p;disk=d;store=new ProtectionStore(p.resolve("store").toFile(),d,hint);catalog=new HistoryCatalog(p.resolve("catalog").toFile(),store,d);identity=new HistoryIdentity(p.resolve("identity").toFile(),d);index=new HistoryIndex(store,catalog,identity,p.resolve("index").toFile(),d);}
  HistoryCapacity register()throws IOException{return HistoryCapacity.register(store,catalog,identity,index,new HistoryPolicy(64L*1024*1024,20),disk);}
  HistoryCapacity open()throws IOException{return HistoryCapacity.open(store,catalog,identity,index,disk);}
 }
 static Map<String,byte[]> capture(Path p)throws IOException{Map<String,byte[]> m=new TreeMap<>();try(java.util.stream.Stream<Path> paths=Files.walk(p)){for(Path f:(Iterable<Path>)paths.filter(x->Files.isRegularFile(x,LinkOption.NOFOLLOW_LINKS))::iterator)m.put(p.relativize(f).toString(),Files.readAllBytes(f));}return m;}
 static boolean same(Map<String,byte[]> a,Map<String,byte[]> b){if(!a.keySet().equals(b.keySet()))return false;for(String k:a.keySet())if(!Arrays.equals(a.get(k),b.get(k)))return false;return true;}
 static void policy()throws Exception{
  Bundle b=new Bundle();b.store.putSnapshot("doc",null,"title",1,new byte[]{1,2},"attempted");HistoryCapacity c=b.register();check(c.policy().generation==0&&c.policy().quotaBytes==64L*1024*1024,"initial effective policy");Map<String,byte[]> before=capture(b.parent);check(b.register().policy().controlRecordHash.equals(c.policy().controlRecordHash)&&same(before,capture(b.parent)),"idempotent registration");
  rejects(()->HistoryCapacity.register(b.store,b.catalog,b.identity,b.index,new HistoryPolicy(128L*1024*1024,20),b.disk),"initial configuration conflict");check(c.setPolicy(new HistoryPolicy(128L*1024*1024,5)).generation==1,"first policy generation");check(c.policy().automaticCount==5&&c.inspect().controlActualEntries==2,"control usage");Bundle reopen=new Bundle(b.parent,new Disk(),ProtectionStore.DEFAULT_QUOTA_BYTES);HistoryCapacity opened=reopen.open();check(opened.policy().generation==1&&opened.policy().quotaBytes==128L*1024*1024,"explicit open authority");rejects(()->new Bundle(b.parent,new Disk(),1),"conflicting bootstrap hint");
  for(String fault:new String[]{"file","after-file","move","after-move","dir","after-dir"}){
   Bundle f=new Bundle();HistoryCapacity controller=f.register();f.disk.fault=fault;rejects(()->controller.setPolicy(new HistoryPolicy(128L*1024*1024,5)),"policy fault accepted "+fault);check(controller.inspect().mutationBlocked,"uncertain publication not blocked "+fault);f.disk.fault="";Map<String,byte[]> retained=capture(f.parent);check(controller.reconcile().generation==1,"reconcile actual disk policy "+fault);check(controller.reconcile().generation==1,"reconcile allocated second generation "+fault);check(controller.inspect().usageComplete&&!controller.inspect().mutationBlocked,"reconcile authority unavailable");check(capture(f.parent).size()<=retained.size(),"reconcile grew files");
  }
  Bundle partial=new Bundle();HistoryCapacity pc=partial.register();Path stage=partial.parent.resolve("history_control/policy-0000000000000001.control.stage");Files.write(stage,new byte[]{1});Map<String,byte[]> bytes=capture(partial.parent);rejects(pc::reconcile,"partial policy stage reconciled");check(same(bytes,capture(partial.parent)),"partial stage rewritten");
  Bundle empty=new Bundle();Files.createDirectory(empty.parent.resolve("history_control"));Bundle recovery=new Bundle(empty.parent,new Disk(),ProtectionStore.DEFAULT_QUOTA_BYTES);rejects(recovery::open,"empty control fell back");
  Bundle regFail=new Bundle();regFail.disk.fault="file";rejects(regFail::register,"registration fault accepted");regFail.disk.fault="";check(regFail.open().policy().generation==0,"registration stage reconcile failed");
 }
 public static void main(String[] args)throws Exception{policy();System.out.println("HistoryCapacity policy: "+checks+" checks PASS");}
}
