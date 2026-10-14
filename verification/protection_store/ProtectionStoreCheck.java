package com.example.markdowndesk;

import java.io.*;
import java.nio.file.*;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.util.*;

public final class ProtectionStoreCheck {
 static int assertions, cases;
 interface Checked {void run() throws Exception;}
 static void check(boolean ok,String why) {assertions++;if(!ok)throw new AssertionError(why);}
 static void fails(Checked c,String why) throws Exception {try {c.run();}catch(IOException e){assertions++;return;}throw new AssertionError(why);}
 static void test(String name,Checked c) throws Exception {c.run();cases++;System.out.println("PASS "+name);}
 static class Durable implements ProtectionStore.Durability {
  String failure=""; List<String> calls=new ArrayList<>();
  public void syncFile(FileDescriptor fd)throws IOException {calls.add("file");if(failure.equals("file"))throw new IOException("injected file sync");fd.sync();}
  public void move(File from,File to)throws IOException {
   calls.add("move");
   if(failure.equals("move"))throw new IOException("injected move");
   Files.move(from.toPath(),to.toPath(),StandardCopyOption.ATOMIC_MOVE);
   if(failure.equals("validation"))try(RandomAccessFile f=new RandomAccessFile(to,"rw")){f.seek(f.length()-1);int old=f.read();f.seek(f.length()-1);f.write(old^1);}
  }
  public void syncDirectory(File dir)throws IOException {calls.add("dir");if(failure.equals("dir"))throw new IOException("injected directory sync");try(FileChannel c=FileChannel.open(dir.toPath(),StandardOpenOption.READ)){c.force(true);}}
 }
 static File fresh()throws IOException {return new File(Files.createTempDirectory("protection-").toFile(),"store");}
 static ProtectionStore store(File root,Durable d)throws IOException{return new ProtectionStore(root,d,ProtectionStore.DEFAULT_QUOTA_BYTES);}
 static ProtectionStore.Snapshot put(ProtectionStore s,byte[] b,String role)throws IOException{return s.putSnapshot("doc","content://private/doc","title",7,b,role);}
 static byte[] b(String s){return s.getBytes(StandardCharsets.UTF_8);}
 static File extension(File root,String ext){return Arrays.stream(root.listFiles()).filter(f->f.getName().endsWith(ext)).findFirst().orElseThrow(()->new AssertionError("missing "+ext));}
 static long disk(File root)throws IOException {try(java.util.stream.Stream<Path> files=Files.list(root.toPath())){return files.mapToLong(p->{try{return Files.size(p);}catch(IOException e){throw new UncheckedIOException(e);}}).sum();}}
 static void rehash(byte[] raw)throws Exception {byte[] hash=java.security.MessageDigest.getInstance("SHA-256").digest(Arrays.copyOf(raw,raw.length-32));System.arraycopy(hash,0,raw,raw.length-32,32);}
 public static void main(String[] args)throws Exception {
  if(args.length>0&&args[0].equals("write-failure")){
   File r=fresh();ProtectionStore s=store(r,new Durable());put(s,b("previous"),"attempted");
   fails(()->put(s,new byte[65536],"attempted"),"actual file write failure accepted");
   check(r.listFiles().length==2,"actual failed stage deleted");check(store(r,new Durable()).snapshots().size()==1,"restart lost earlier snapshot");
   System.out.println("ProtectionStore real OS write failure: "+assertions+" assertions PASS");return;
  }
  test("exact bytes and defensive copies",()->{
   File r=fresh();ProtectionStore s=store(r,new Durable());byte[] input=b("\ufeff  café\r\n😀 \n");byte[] expected=input.clone();
   ProtectionStore.Snapshot x=put(s,input,"attempted");input[0]=0;check(Arrays.equals(expected,x.bytes()),"input mutation");byte[] out=x.bytes();out[0]=0;check(Arrays.equals(expected,x.bytes()),"output mutation");
   check(x.documentId.equals("doc")&&x.uri.equals("content://private/doc")&&x.title.equals("title")&&x.revision==7&&x.role.equals("attempted")&&x.timestamp>0,"metadata");
   check(x.hash.length()==64,"hash");check(Arrays.equals(expected,store(r,new Durable()).snapshots().get(0).bytes()),"restart bytes");
  });
  test("orphans and transaction completion survive restart",()->{
   File r=fresh();ProtectionStore s=store(r,new Durable());ProtectionStore.Snapshot a=put(s,b("before"),"original"),z=put(s,b("edit"),"attempted");put(s,b("orphan"),"attempted");
   ProtectionStore.Transaction t=s.prepare(a,z,a.hash);check(!t.verified&&t.original.id.equals(a.id)&&t.attempted.id.equals(z.id),"links");
   ProtectionStore restarted=store(r,new Durable());check(restarted.snapshots().size()==3,"orphan hidden");check(!restarted.transactions().get(0).verified,"premature completion");
   byte[] prepared=Files.readAllBytes(extension(r,".transaction").toPath());s.complete(t);check(!t.verified,"mutable prepared object");check(store(r,new Durable()).transactions().get(0).verified,"completion lost");
   check(Arrays.equals(prepared,Files.readAllBytes(extension(r,".transaction").toPath())),"prepared overwritten");
  });
  test("transaction rejects incorrect identity role hash and missing committed bytes",()->{
   File r=fresh();ProtectionStore s=store(r,new Durable());ProtectionStore.Snapshot a=put(s,b("before"),"original"),z=put(s,b("edit"),"attempted");
   fails(()->s.prepare(a,z,z.hash),"wrong source hash");fails(()->s.prepare(z,a,a.hash),"wrong roles");
   ProtectionStore.Snapshot other=s.putSnapshot("other","content://private/doc","title",7,b("other"),"attempted");fails(()->s.prepare(a,other,a.hash),"cross document");
   ProtectionStore.Snapshot later=s.putSnapshot("doc","content://private/doc","title",8,b("later"),"attempted");fails(()->s.prepare(a,later,a.hash),"revision mismatch");
   Files.delete(new File(r,z.id+".snapshot").toPath());fails(()->s.prepare(a,z,a.hash),"missing link");
  });
  test("bounded document and metadata inputs",()->{
   ProtectionStore s=store(fresh(),new Durable());check(put(s,new byte[8388608],"attempted").bytes().length==8388608,"exact limit");
   fails(()->put(s,new byte[8388609],"attempted"),"oversize");fails(()->s.putSnapshot("doc","uri","x".repeat(5000),0,b("x"),"attempted"),"metadata unbounded");
   fails(()->put(s,b("x"),"unknown"),"unknown role");
  });
  test("invalid Unicode metadata rejects instead of replacing silently",()->{
   ProtectionStore s=store(fresh(),new Durable());fails(()->s.putSnapshot("doc","uri","\ud800",0,b("x"),"attempted"),"malformed metadata silently replaced");
  });
  test("quota accounts for actual staged and unrelated files without deletion",()->{
   File r=fresh();Durable d=new Durable();ProtectionStore s=store(r,d);put(s,b("retained"),"attempted");long existing=disk(r);
   ProtectionStore small=new ProtectionStore(r,d,existing);d.calls.clear();fails(()->put(small,b("new"),"attempted"),"quota ignored");check(d.calls.isEmpty(),"quota checked after durability mutation");check(disk(r)==existing,"retained changed");
   Files.write(new File(r,"interrupted.stage").toPath(),new byte[100]);ProtectionStore same=new ProtectionStore(r,d,existing+100);fails(()->put(same,b("new"),"attempted"),"stage quota ignored");check(same.snapshots().size()==1,"stage obscures retained envelope");
  });
  test("file sync atomic move directory sync order and failures retain evidence",()->{
   for(String failure:new String[]{"file","move","dir","validation"}){
    File r=fresh();Durable d=new Durable();ProtectionStore s=store(r,d);put(s,b("old"),"attempted");d.calls.clear();d.failure=failure;
    fails(()->put(s,b("new"),"attempted"),"failure accepted "+failure);check(r.listFiles().length==2,"failed evidence deleted "+failure);d.failure="";
    check(new File(r,Arrays.stream(r.list()).filter(n->n.endsWith(".snapshot")).findFirst().get()).isFile(),"old snapshot removed");
    if(!failure.equals("validation"))check(store(r,d).snapshots().size()>=1,"restart lost old snapshot");
   }
   Durable d=new Durable();ProtectionStore s=store(fresh(),d);d.calls.clear();put(s,b("x"),"attempted");check(d.calls.equals(Arrays.asList("file","move","dir")),"durability ordering "+d.calls);
  });
  test("root creation requires parent and root sync",()->{
   File r=fresh();Durable d=new Durable();store(r,d);check(d.calls.equals(Arrays.asList("dir","dir")),"root durability "+d.calls);
   Durable bad=new Durable();bad.failure="dir";fails(()->store(fresh(),bad),"root directory failure accepted");
  });
  test("snapshot corruption fails explicitly independently of transactions",()->{
   for(boolean oversized:new boolean[]{false,true}){
    File r=fresh();ProtectionStore s=store(r,new Durable());put(s,b("preserved"),"attempted");File f=extension(r,".snapshot");
    try(RandomAccessFile x=new RandomAccessFile(f,"rw")){if(oversized)x.setLength(20L*1024*1024);else {x.seek(x.length()-1);int old=x.read();x.seek(x.length()-1);x.write(old^1);}}
    fails(()->s.snapshots(),"corrupt snapshot hidden");check(s.transactions().isEmpty(),"transaction enumeration depends on unrelated snapshot");
   }
  });
  test("malformed length does not allocate attacker specified payload",()->{
   File r=fresh();ProtectionStore s=store(r,new Durable());put(s,b("x"),"attempted");try(RandomAccessFile x=new RandomAccessFile(extension(r,".snapshot"),"rw")){x.seek(9);x.writeInt(Integer.MAX_VALUE);}fails(()->s.snapshots(),"malformed length accepted");
  });
  test("inner hash and declared payload length validated after envelope checksum",()->{
   for(boolean hash:new boolean[]{true,false}){
    File r=fresh();ProtectionStore s=store(r,new Durable());ProtectionStore.Snapshot x=put(s,b("content"),"attempted");File f=extension(r,".snapshot");byte[] raw=Files.readAllBytes(f.toPath());
    if(hash){byte[] needle=b(x.hash);int at=-1;outer:for(int i=0;i<raw.length-needle.length;i++){for(int j=0;j<needle.length;j++)if(raw[i+j]!=needle[j])continue outer;at=i;break;}check(at>=0,"hash missing");raw[at]=(byte)(raw[at]=='0'?'1':'0');}
    else {int at=raw.length-32-7-4;java.nio.ByteBuffer.wrap(raw).putInt(at,8388609);}
    rehash(raw);Files.write(f.toPath(),raw);fails(()->s.snapshots(),"inner corruption accepted");
   }
  });
  test("completion quota is reserved before mutation and immutable retry is idempotent",()->{
   File r=fresh();ProtectionStore s=store(r,new Durable());ProtectionStore.Snapshot a=put(s,b("original"),"original"),e=put(s,b("edited"),"attempted");ProtectionStore.Transaction t=s.prepare(a,e,a.hash);s.complete(t);
   long completeSize=extension(r,".complete").length(),preparedSize=extension(r,".transaction").length();
   File q=fresh();Durable d=new Durable();ProtectionStore p=store(q,d);ProtectionStore.Snapshot o=put(p,b("original"),"original"),z=put(p,b("edited"),"attempted");long snapshots=disk(q);
   ProtectionStore insufficient=new ProtectionStore(q,d,snapshots+preparedSize+completeSize-1);d.calls.clear();fails(()->insufficient.prepare(o,z,o.hash),"completion not reserved");check(d.calls.isEmpty(),"reservation checked after commit");
   ProtectionStore exact=new ProtectionStore(q,d,snapshots+preparedSize+completeSize);ProtectionStore.Transaction prepared=exact.prepare(o,z,o.hash);
   fails(()->put(exact,b("consume reserved quota"),"attempted"),"completion reservation consumed");exact.complete(prepared);long size=disk(q);exact.complete(prepared);check(disk(q)==size,"duplicate outcome");check(exact.transactions().get(0).verified,"completion not verified");
  });
  test("completion durability failure retains prepared evidence",()->{
   for(String failure:new String[]{"file","move","dir","validation"}){
    File r=fresh();Durable d=new Durable();ProtectionStore s=store(r,d);ProtectionStore.Snapshot a=put(s,b("old"),"original"),z=put(s,b("new"),"attempted");ProtectionStore.Transaction t=s.prepare(a,z,a.hash);d.failure=failure;
    fails(()->s.complete(t),"completion failure ignored");check(r.listFiles().length==4,"completion evidence removed");d.failure="";check(store(r,d).snapshots().size()==2,"completion obscures recoverable bytes");
    if(failure.equals("validation"))fails(()->s.transactions(),"corrupt outcome hidden");
   }
  });
  test("revalidation rereads committed preparation and protected bytes",()->{
   File r=fresh();Durable d=new Durable();ProtectionStore s=store(r,d);ProtectionStore.Snapshot a=put(s,b("old"),"original"),z=put(s,b("new"),"attempted");ProtectionStore.Transaction t=s.prepare(a,z,a.hash);
   d.calls.clear();check(s.validate(t).id.equals(t.id),"valid prepared transaction rejected");check(d.calls.isEmpty(),"validation mutates durability state");
   File f=new File(r,a.id+".snapshot");byte[] before=Files.readAllBytes(f.toPath());Files.delete(f.toPath());fails(()->s.validate(t),"removed protection accepted");Files.write(f.toPath(),before);
   File p=extension(r,".transaction");byte[] bytes=Files.readAllBytes(p.toPath());bytes[bytes.length-1]^=1;Files.write(p.toPath(),bytes);fails(()->s.validate(t),"corrupt prepared record accepted");
  });
  test("transaction corruption and missing links explicitly fail",()->{
   File r=fresh();ProtectionStore s=store(r,new Durable());ProtectionStore.Snapshot a=put(s,b("old"),"original"),z=put(s,b("new"),"attempted");s.prepare(a,z,a.hash);
   Files.delete(new File(r,a.id+".snapshot").toPath());fails(()->s.transactions(),"missing referenced snapshot hidden");check(s.snapshots().size()==1,"independent snapshot enumeration lost orphan");
   File q=fresh();ProtectionStore t=store(q,new Durable());ProtectionStore.Snapshot o=put(t,b("old"),"original"),e=put(t,b("new"),"attempted");t.prepare(o,e,o.hash);try(RandomAccessFile x=new RandomAccessFile(extension(q,".transaction"),"rw")){x.setLength(5);}fails(()->t.transactions(),"transaction corruption hidden");check(t.snapshots().size()==2,"transactions obscure snapshots");
  });
  test("reject symlink root ancestor and every entry",()->{
   File r=fresh();Files.createDirectories(r.toPath());File link=new File(r.getParentFile(),"link");Files.createSymbolicLink(link.toPath(),r.toPath());fails(()->store(link,new Durable()),"symlink root");fails(()->store(new File(link,"child"),new Durable()),"symlink ancestor");
   ProtectionStore s=store(r,new Durable());put(s,b("x"),"attempted");Files.createSymbolicLink(new File(r,"rogue.stage").toPath(),new File(r.getParentFile(),"outside").toPath());fails(()->s.snapshots(),"symlink entry accepted");fails(()->put(s,b("new"),"attempted"),"symlink quota accepted");
  });
  test("entry capacity rejects mutation while reserving completion slots",()->{
   File r=fresh();Durable d=new Durable();ProtectionStore s=store(r,d);
   for(int i=0;i<65536;i++)Files.createFile(new File(r,i+".stage").toPath());
   d.calls.clear();fails(()->put(s,b("new"),"attempted"),"entry cap permits self-invalidating store");
   check(r.list().length==65536&&d.calls.isEmpty(),"entry exhaustion mutates directory");check(store(r,d).snapshots().isEmpty(),"at-cap enumeration invalid");
   File q=fresh();ProtectionStore p=store(q,new Durable());ProtectionStore.Snapshot a=put(p,b("old"),"original"),z=put(p,b("new"),"attempted");
   for(int i=0;i<65533;i++)Files.createFile(new File(q,i+".stage").toPath());
   fails(()->p.prepare(a,z,a.hash),"completion entry slot not reserved");check(q.list().length==65535,"prepare consumed unreserved entry");
   Files.delete(new File(q,"0.stage").toPath());ProtectionStore.Transaction t=p.prepare(a,z,a.hash);
   fails(()->put(p,b("consume completion entry"),"attempted"),"reserved completion slot consumed");p.complete(t);
   check(q.list().length==65536&&store(q,new Durable()).transactions().get(0).verified,"exact entry limit completion unavailable");
  });
  test("snapshot lookup and retained handles revalidate disk bytes",()->{
   File r=fresh();ProtectionStore s=store(r,new Durable());ProtectionStore.Snapshot a=put(s,b("protected"),"attempted");
   check(Arrays.equals(s.snapshot(a.id).bytes(),b("protected")),"lookup content mismatch");
   fails(()->s.snapshot("../other"),"lookup path traversal accepted");
   File f=new File(r,a.id+".snapshot");byte[] saved=Files.readAllBytes(f.toPath());saved[saved.length-1]^=1;Files.write(f.toPath(),saved);
   fails(()->a.bytes(),"cached handle masks disk corruption");fails(()->s.snapshot(a.id),"lookup masks disk corruption");
   Files.delete(f.toPath());fails(()->a.bytes(),"cached handle masks deletion");
  });
  test("version one snapshot envelope remains readable",()->{
   File r=fresh();ProtectionStore s=store(r,new Durable());String id=UUID.randomUUID().toString();byte[] payload=b("\ufeffold\r\nbytes😀");String hash=EditorHash.sha(payload);
   ByteArrayOutputStream body=new ByteArrayOutputStream();DataOutputStream o=new DataOutputStream(body);
   for(String v:new String[]{id,"doc","uri","title"}){byte[] value=b(v);o.writeInt(value.length);o.write(value);}o.writeLong(5);
   byte[] role=b("attempted");o.writeInt(role.length);o.write(role);o.writeLong(123);byte[] digest=b(hash);o.writeInt(digest.length);o.write(digest);o.writeInt(payload.length);o.write(payload);
   ByteArrayOutputStream envelope=new ByteArrayOutputStream();DataOutputStream outer=new DataOutputStream(envelope);outer.writeInt(0x4d445053);outer.writeInt(1);outer.writeByte(1);outer.writeInt(body.size());outer.write(body.toByteArray());outer.write(java.security.MessageDigest.getInstance("SHA-256").digest(envelope.toByteArray()));
   Files.write(new File(r,id+".snapshot").toPath(),envelope.toByteArray());ProtectionStore.Snapshot restored=s.snapshot(id);
   check(restored.byteCount==payload.length&&restored.timestamp==123&&restored.revision==5,"version one metadata changed");check(Arrays.equals(restored.bytes(),payload),"version one bytes changed");
  });
  System.out.println("ProtectionStore: "+cases+" cases / "+assertions+" assertions PASS");
 }
 static class EditorHash {static String sha(byte[] input)throws Exception {StringBuilder hex=new StringBuilder();for(byte x:java.security.MessageDigest.getInstance("SHA-256").digest(input))hex.append(String.format("%02x",x&255));return hex.toString();}}
}
