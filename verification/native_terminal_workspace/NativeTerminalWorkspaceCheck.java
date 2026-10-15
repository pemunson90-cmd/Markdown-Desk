package com.example.markdowndesk;

import java.io.*;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.*;
import java.util.*;

/** Real filesystem/validator checks; no Pins integration or whole-heap claim. */
public final class NativeTerminalWorkspaceCheck {
 static int checks;
 interface Work {void run()throws Exception;}
 static void yes(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
 static void no(Work work,String why)throws Exception {try{work.run();throw new AssertionError("accepted "+why);}catch(IOException expected){checks++;}}
 static Object field(Object owner,String name)throws Exception{Field f=owner.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(owner);}
 static long used(HistoryStorage.Budget b)throws Exception{return HistoryStorage.METADATA_BUDGET-(Long)field(b,"remaining");}
 static final class Disk implements ProtectionStore.Durability {
  int calls;boolean failMove;
  public void syncFile(FileDescriptor fd)throws IOException{calls++;fd.sync();}
  public void syncDirectory(File dir){calls++;}
  public void move(File a,File b)throws IOException{calls++;if(failMove)throw new IOException("fixture move failure");Files.move(a.toPath(),b.toPath());}
 }
 static final class F implements AutoCloseable {
  final Path parent=Files.createTempDirectory("terminal-workspace-");final Disk disk=new Disk();
  final ProtectionStore store=new ProtectionStore(parent.resolve("store").toFile(),disk,64L<<20);
  final HistoryCatalog catalog=new HistoryCatalog(parent.resolve("catalog").toFile(),store,disk);
  final HistoryIdentity identity=new HistoryIdentity(parent.resolve("identity").toFile(),disk);
  final HistoryIndex index=new HistoryIndex(store,catalog,identity,parent.resolve("index").toFile(),disk);
  final File[] roots={store.storageRoot(),catalog.storageRoot(),identity.storageRoot(),index.storageRoot()};
  final HistoryStorage.Budget budget=new HistoryStorage.Budget(HistoryStorage.METADATA_BUDGET);
  F()throws Exception{}
  NativeTerminalWorkspace reserve(int n)throws IOException{return NativeTerminalWorkspace.reserve(store,catalog,identity,index,budget,n);}
  ProtectionStore.Snapshot append()throws IOException{return store.putSnapshot("doc","uri","title",1,new byte[]{1,2,3},"original");}
  Path metadata(int role,String kind,int size)throws Exception {Path p=roots[role].toPath().resolve(UUID.randomUUID()+"."+kind);try(RandomAccessFile out=new RandomAccessFile(p.toFile(),"rw")){out.setLength(size);}return p;}
  void locked(Work work)throws Exception{synchronized(HistoryStorage.monitorFor(roots[0])){try(HistoryStorage.MutationScope scope=HistoryStorage.administration(roots[0])){work.run();}}}
  Map<String,String> state()throws Exception {Map<String,String> m=new TreeMap<>();for(int r=0;r<4;r++)try(DirectoryStream<Path> paths=Files.newDirectoryStream(roots[r].toPath())){for(Path p:paths)m.put(r+"/"+p.getFileName(),Files.size(p)+":"+HistoryStorage.hex(HistoryStorage.digest().digest(Files.readAllBytes(p))));}return m;}
  public void close()throws IOException{try(java.util.stream.Stream<Path> paths=Files.walk(parent)){for(Path p:(Iterable<Path>)paths.sorted(Comparator.reverseOrder())::iterator)Files.delete(p);}}
 }
 static void cleared(NativeTerminalWorkspace w)throws Exception {
  for(Object slot:(Object[])field(w,"slots")){yes(field(slot,"key")==null&&field(slot,"name")==null&&field(slot,"hash")==null&&field(slot,"modified")==null,"slot references cleared");}
  for(Object root:(Object[])field(w,"anchors"))yes(root==null,"root anchor cleared");
 }
 static void captured(F f,NativeTerminalWorkspace w,int expected)throws Exception {
  yes((Integer)field(w,"count")==expected,"exact captured current count");
  Object[] slots=(Object[])field(w,"slots");
  for(int n=0;n<expected;n++){
   Object slot=slots[n];int role=(Integer)field(slot,"role");Path path=f.roots[role].toPath().resolve((String)field(slot,"name"));
   BasicFileAttributes attrs=Files.readAttributes(path,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);
   yes(attrs.isRegularFile()&&Objects.equals(attrs.fileKey(),field(slot,"key"))&&attrs.size()==(Long)field(slot,"length")&&attrs.lastModifiedTime().equals(field(slot,"modified")),"exact current member attributes");
  }
  Object[] anchors=(Object[])field(w,"anchors");
  for(int role=0;role<4;role++){
   BasicFileAttributes saved=(BasicFileAttributes)anchors[role],now=Files.readAttributes(f.roots[role].toPath(),BasicFileAttributes.class);
   yes(saved.isDirectory()&&Objects.equals(saved.fileKey(),now.fileKey())&&saved.creationTime().equals(now.creationTime()),"exact current role anchors");
  }
 }
 static void admission()throws Exception {
  try(F f=new F()){
   int n=4;long max=0,sum=0;for(File root:f.roots){long len=root.getPath().getBytes(StandardCharsets.UTF_8).length;max=Math.max(max,len);sum+=len;}
   long expected=(512L<<10)+(64L<<10)+4*sum+n*(2048+4*(max+1+128));long before=used(f.budget);
   NativeTerminalWorkspace w=f.reserve(n);yes(used(f.budget)-before==expected,"concrete prepaid debit");
   yes(((Object[])field(w,"slots")).length==n,"admitted table capacity");yes(((byte[])field(w,"scratch")).length==8192,"fixed hash scratch");
   for(Field member:NativeTerminalWorkspace.class.getDeclaredFields())if(member.getType()==byte[].class){member.setAccessible(true);yes(((byte[])member.get(w)).length<=65536,"no payload array");}
   f.append();f.metadata(1,"checkpoint",5);f.metadata(2,"identity",7);f.metadata(3,"index",9);
   long prepaid=used(f.budget);Map<String,String> state=f.state();int calls=f.disk.calls;
   f.locked(()->{try(NativeTerminalWorkspace.Proof p=w.captureLocked()){captured(f,w,n);p.recheckLocked();no(w::captureLocked,"nested table");p.recheckLocked();}});
   yes(used(f.budget)==prepaid&&f.disk.calls==calls&&f.state().equals(state),"capture/recheck no debit or writes");cleared(w);
   ProtectionStore.Snapshot extra=f.append();Map<String,String> over=f.state();int overCalls=f.disk.calls;
   f.locked(()->no(w::captureLocked,"N+1"));yes(f.state().equals(over)&&f.disk.calls==overCalls,"overflow no writes/callbacks");cleared(w);
   Files.delete(f.roots[0].toPath().resolve(extra.id+".snapshot"));f.locked(()->{try(NativeTerminalWorkspace.Proof p=w.captureLocked()){p.recheckLocked();}});yes(used(f.budget)==prepaid,"retry same original debit");
   no(()->NativeTerminalWorkspace.reserve(f.store,f.catalog,f.identity,f.index,null,n),"null funding");
   no(()->NativeTerminalWorkspace.reserve(f.store,f.catalog,f.identity,f.index,f.budget,-1),"negative capacity");
   HistoryStorage.Budget tooSmall=new HistoryStorage.Budget(expected-1);no(()->NativeTerminalWorkspace.reserve(f.store,f.catalog,f.identity,f.index,tooSmall,n),"underfunded table");
  }
 }
 static void changes()throws Exception {
  for(String mode:Arrays.asList("append","remove","hash","inode","root","store-root-alias","reorder"))try(F f=new F()){
   f.append();Path metadata=f.metadata(1,"checkpoint",12);f.metadata(1,"checkpoint",3);f.metadata(1,"stage",2);NativeTerminalWorkspace w=f.reserve(8);
   f.locked(()->{try(NativeTerminalWorkspace.Proof p=w.captureLocked()){
    if(mode.equals("append"))f.metadata(2,"identity",1);
    if(mode.equals("remove"))Files.delete(metadata);
    if(mode.equals("hash")){FileTime time=Files.getLastModifiedTime(metadata);try(RandomAccessFile out=new RandomAccessFile(metadata.toFile(),"rw")){out.write(7);}Files.setLastModifiedTime(metadata,time);}
    if(mode.equals("inode")){BasicFileAttributes before=Files.readAttributes(metadata,BasicFileAttributes.class);Path replacement=f.parent.resolve("replacement");Files.copy(metadata,replacement);Files.setLastModifiedTime(replacement,before.lastModifiedTime());Files.move(replacement,metadata,StandardCopyOption.REPLACE_EXISTING);yes(!Objects.equals(before.fileKey(),Files.readAttributes(metadata,BasicFileAttributes.class).fileKey()),"fixture distinct inode");}
    if(mode.equals("root")){Path old=f.parent.resolve("old-catalog");Files.move(f.roots[1].toPath(),old);Files.createDirectory(f.roots[1].toPath());}
    if(mode.equals("store-root-alias")){Path old=f.parent.resolve("old-store");Files.move(f.roots[0].toPath(),old);Files.createSymbolicLink(f.roots[0].toPath(),old);}
    if(mode.equals("reorder")){Path temporary=f.parent.resolve("temporary");Files.move(metadata,temporary);Files.move(temporary,metadata);p.recheckLocked();p.recheckLocked();}
    else no(p::recheckLocked,"callback "+mode);
   }});cleared(w);
  }
 }
 static void rolesAndReservations()throws Exception {
  try(F f=new F()){
   f.append();f.metadata(1,"stage",4);Path oversized=f.metadata(2,"identity",HistoryMetadataIO.MAX_RECORD+1);
   NativeTerminalWorkspace w=f.reserve(8);f.locked(()->{try(NativeTerminalWorkspace.Proof p=w.captureLocked()){
    boolean found=false;for(Object slot:(Object[])field(w,"slots"))if(oversized.getFileName().toString().equals(field(slot,"name"))){found=true;yes(field(slot,"hash")==null,"oversized preserves null hash");}yes(found,"oversized retained");p.recheckLocked();
   }});
   Path unknown=f.metadata(3,"foreign",1);f.locked(()->no(w::captureLocked,"unknown metadata"));Files.delete(unknown);
   Path link=f.roots[3].toPath().resolve(UUID.randomUUID()+".index");Files.createSymbolicLink(link,oversized);f.locked(()->no(w::captureLocked,"metadata symlink"));Files.delete(link);
   f.locked(()->{try(NativeTerminalWorkspace.Proof p=w.captureLocked()){p.recheckLocked();}});
  }
  try(F f=new F()){
   ProtectionStore.Snapshot a=f.append(),b=f.store.putSnapshot("doc","uri","title",1,new byte[]{4},"attempted");ProtectionStore.Transaction tx=f.store.prepare(a,b,a.hash);NativeTerminalWorkspace w=f.reserve(8);
   f.disk.failMove=true;no(()->f.store.complete(tx),"fixture retained completion stage");f.disk.failMove=false;
   Path stage=f.roots[0].toPath().resolve(tx.id+".completion-stage");long full=Files.size(stage);
   try(RandomAccessFile out=new RandomAccessFile(stage.toFile(),"rw")){out.setLength(17);}
   f.locked(()->{try(NativeTerminalWorkspace.Proof p=w.captureLocked()){p.recheckLocked();try(RandomAccessFile out=new RandomAccessFile(stage.toFile(),"rw")){out.setLength(16);}no(p::recheckLocked,"partial reservation changed");}});
   try(RandomAccessFile out=new RandomAccessFile(stage.toFile(),"rw")){out.seek(0);out.write(0);}
   Map<String,String> corrupt=f.state();f.locked(()->no(w::captureLocked,"corrupt completion prefix"));yes(f.state().equals(corrupt),"invalid reservation no writes");yes(full>17,"real completion prefix");
  }
 }
 static void keyEquality()throws Exception {
  try(F f=new F()){
   f.append();NativeTerminalWorkspace w=f.reserve(2);
   f.locked(()->{try(NativeTerminalWorkspace.Proof p=w.captureLocked()){
    Object slot=((Object[])field(w,"slots"))[0];String name=(String)field(slot,"name");Object saved=field(slot,"key");
    Object reread=Files.readAttributes(f.roots[0].toPath().resolve(name),BasicFileAttributes.class).fileKey();
    yes(saved!=reread&&Objects.equals(saved,reread),"provider distinct key objects compare equal");p.recheckLocked();
    Field key=slot.getClass().getDeclaredField("key");key.setAccessible(true);
    // Test-only opaque key collision: identical rendering/hash must not replace equals.
    key.set(slot,new Object(){public String toString(){return saved.toString();}public int hashCode(){return saved.hashCode();}public boolean equals(Object other){return false;}});
    no(p::recheckLocked,"key rendering/hash collision");
   }});cleared(w);
  }
 }
 static void leases()throws Exception {
  try(F f=new F()){
   f.append();NativeTerminalWorkspace w=f.reserve(4);NativeTerminalWorkspace.Proof[] saved={null};
   no(w::captureLocked,"unlocked capture");no(()->HistoryStorage.mutationIdentityLocked(f.roots[0]),"unlocked mutation identity");
   f.locked(()->{Object identity=HistoryStorage.mutationIdentityLocked(f.roots[0]);yes(identity==HistoryStorage.mutationIdentityLocked(f.roots[0]),"stable opaque lease identity");saved[0]=w.captureLocked();});
   no(saved[0]::recheckLocked,"unlocked recheck");yes(field(w,"active")==saved[0],"unlocked caller cannot revoke proof");saved[0].close();cleared(w);
   f.locked(()->saved[0]=w.captureLocked());f.locked(()->no(saved[0]::recheckLocked,"new same-thread lease"));yes(field(w,"active")==null,"owner stale lease invalidates proof");saved[0].close();cleared(w);
   long prepaid=used(f.budget);
   synchronized(HistoryStorage.monitorFor(f.roots[0])){
    try(HistoryStorage.MutationScope scope=HistoryStorage.administration(f.roots[0])){saved[0]=w.captureLocked();}
    no(saved[0]::recheckLocked,"owner monitor held but original lease ended");
    yes(field(w,"active")==null,"ended owner lease clears active proof");cleared(w);
    try(HistoryStorage.MutationScope retry=HistoryStorage.administration(f.roots[0]);NativeTerminalWorkspace.Proof p=w.captureLocked()){p.recheckLocked();}
   }
   yes(used(f.budget)==prepaid,"ended-lease retry spends no new credit");
   f.locked(()->{try(NativeTerminalWorkspace.Proof p=w.captureLocked()){
    final Throwable[] failure={null};Thread other=new Thread(()->{try{p.recheckLocked();failure[0]=new AssertionError("foreign thread accepted");}catch(IOException expected){}catch(Throwable t){failure[0]=t;}});other.start();other.join();yes(failure[0]==null,"foreign thread refused");p.recheckLocked();
   }});
   f.locked(()->{NativeTerminalWorkspace.Proof p=w.captureLocked();p.close();no(p::recheckLocked,"closed proof");});cleared(w);
  }
 }
 public static void main(String[] args)throws Exception {admission();changes();rolesAndReservations();keyEquality();leases();System.out.println("Native terminal workspace: "+checks+" checks PASS; bounded current-member evidence only, no Pins integration/whole-heap proof");}
}
