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
  String failure=""; List<String> calls=new ArrayList<>(); Checked onFile;
  public void syncFile(FileDescriptor fd)throws IOException {calls.add("file");if(onFile!=null){Checked action=onFile;onFile=null;try{action.run();}catch(Exception e){throw new IOException(e);}}if(failure.equals("file"))throw new IOException("injected file sync");fd.sync();if(failure.equals("after-file"))throw new IOException("injected after file sync");}
  public void move(File from,File to)throws IOException {
   calls.add("move");
   if(failure.equals("move"))throw new IOException("injected move");
   Files.move(from.toPath(),to.toPath(),StandardCopyOption.ATOMIC_MOVE);
   if(failure.equals("after-move"))throw new IOException("injected after move");
   if(failure.equals("validation"))try(RandomAccessFile f=new RandomAccessFile(to,"rw")){f.seek(f.length()-1);int old=f.read();f.seek(f.length()-1);f.write(old^1);}
  }
  public void syncDirectory(File dir)throws IOException {calls.add("dir");if(failure.equals("dir"))throw new IOException("injected directory sync");try(FileChannel c=FileChannel.open(dir.toPath(),StandardOpenOption.READ)){c.force(true);}if(failure.equals("after-dir"))throw new IOException("injected after directory sync");}
 }
 static File fresh()throws IOException {return new File(Files.createTempDirectory("protection-").toFile(),"store");}
 static ProtectionStore store(File root,Durable d)throws IOException{return new ProtectionStore(root,d,ProtectionStore.DEFAULT_QUOTA_BYTES);}
 static ProtectionStore.Snapshot put(ProtectionStore s,byte[] b,String role)throws IOException{return s.putSnapshot("doc","content://private/doc","title",7,b,role);}
 static byte[] b(String s){return s.getBytes(StandardCharsets.UTF_8);}
 static File extension(File root,String ext){return Arrays.stream(root.listFiles()).filter(f->f.getName().endsWith(ext)).findFirst().orElseThrow(()->new AssertionError("missing "+ext));}
 static long disk(File root)throws IOException {try(java.util.stream.Stream<Path> files=Files.list(root.toPath())){return files.mapToLong(p->{try{return Files.size(p);}catch(IOException e){throw new UncheckedIOException(e);}}).sum();}}
 static void rehash(byte[] raw)throws Exception {byte[] hash=java.security.MessageDigest.getInstance("SHA-256").digest(Arrays.copyOf(raw,raw.length-32));System.arraycopy(hash,0,raw,raw.length-32,32);}
 public static void main(String[] args)throws Exception {
  if(args.length>0&&args[0].equals("completion-seed")){
   File r=new File(args[1]);ProtectionStore s=store(r,new Durable());String doc="d".repeat(4096);
   ProtectionStore.Snapshot a=s.putSnapshot(doc,"uri","title",1,b("old"),"original"),z=s.putSnapshot(doc,"uri","title",1,b("new"),"attempted");s.prepare(a,z,a.hash);return;
  }
  if(args.length>0&&args[0].equals("completion-write-failure")){
   File r=new File(args[1]);Durable d=new Durable();ProtectionStore s=store(r,d);ProtectionStore.Transaction t=s.transactions().get(0);byte[] expected=completionBytes(r,t);ProtectionStore.Inventory before=s.inspect();
   d.calls.clear();fails(()->s.complete(t),"actual completion write failure accepted");File stage=new File(r,t.id+".completion-stage");byte[] partial=Files.readAllBytes(stage.toPath());check(partial.length==Integer.parseInt(args[2])&&partial.length<expected.length,"physical completion did not leave expected prefix");check(Arrays.equals(partial,Arrays.copyOf(expected,partial.length)),"physical partial content changed");check(d.calls.isEmpty(),"partial write reached durability");
   for(int retry=0;retry<3;retry++)fails(()->s.complete(t),"physical partial stage retried");ProtectionStore.Inventory after=s.inspect();check(after.pruneBlocked&&after.reservedCompletionBytes==expected.length-partial.length&&after.reservedCompletionSlots==0,"physical prefix accounting");check(after.actualBytes+after.reservedCompletionBytes==before.actualBytes+before.reservedCompletionBytes&&r.list().length==4,"physical retry growth");check(Arrays.equals(partial,Files.readAllBytes(stage.toPath())),"physical partial stage overwritten");check(Arrays.equals(t.original.bytes(),b("old"))&&Arrays.equals(t.attempted.bytes(),b("new")),"physical completion lost protected bytes");System.out.println("ProtectionStore actual completion write limit "+args[2]+": "+assertions+" assertions PASS");return;
  }
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
  test("failed completion uses one stage and conserves exact byte and slot charge",()->{
   for(String failure:new String[]{"file","after-file","move"}){
    File r=fresh();Durable d=new Durable();ProtectionStore s=store(r,d);ProtectionStore.Snapshot a=put(s,b("old"),"original"),z=put(s,b("new"),"attempted");ProtectionStore.Transaction t=s.prepare(a,z,a.hash);
    ProtectionStore.Inventory before=s.inspect();long charge=before.actualBytes+before.reservedCompletionBytes,slots=before.records.size()+before.reservedCompletionSlots;d.failure=failure;
    for(int attempt=0;attempt<4;attempt++){fails(()->s.complete(t),"injected completion accepted");check(r.list().length==4,"repeated failure grew files");check(new File(r,t.id+".completion-stage").isFile(),"completion stage is not bound to preparation");check(Arrays.stream(r.list()).noneMatch(n->n.endsWith(".stage")),"anonymous completion stage created");}
    d.failure="";ProtectionStore.Inventory staged=s.inspect();check(staged.pruneBlocked&&staged.reservedCompletionBytes==0&&staged.reservedCompletionSlots==0,"full stage did not consume reservation");check(staged.actualBytes+staged.reservedCompletionBytes==charge&&staged.records.size()+staged.reservedCompletionSlots==slots,"stage charge growth");
    File stage=new File(r,t.id+".completion-stage");byte[] retained=Files.readAllBytes(stage.toPath());java.nio.file.attribute.BasicFileAttributes retainedAttrs=Files.readAttributes(stage.toPath(),java.nio.file.attribute.BasicFileAttributes.class);Object key=retainedAttrs.fileKey();
    d.calls.clear();d.onFile=()->{check(Arrays.equals(retained,Files.readAllBytes(stage.toPath())),"retry rewrote or truncated stage");check(Objects.equals(key,Files.readAttributes(stage.toPath(),java.nio.file.attribute.BasicFileAttributes.class).fileKey()),"retry replaced stage");check(retainedAttrs.lastModifiedTime().equals(Files.getLastModifiedTime(stage.toPath())),"retry rewrote stage bytes");};s.complete(t);check(d.calls.equals(Arrays.asList("file","move","dir")),"full retry omitted durability");
    ProtectionStore.Inventory done=s.inspect();check(done.complete&&done.actualBytes==charge&&done.reservedCompletionBytes==0&&done.records.size()==slots,"completion charge growth");check(Arrays.equals(retained,Files.readAllBytes(new File(r,t.id+".complete").toPath())),"retry changed v1 completion bytes");
   }
  });
  test("prefix stages reserve only remaining bytes but reject every retry without rewriting",()->{
   for(int count:new int[]{0,1,44,100}){
    File r=fresh();Durable d=new Durable();ProtectionStore s=store(r,d);ProtectionStore.Snapshot a=put(s,b("old"),"original"),z=put(s,b("new"),"attempted");ProtectionStore.Transaction t=s.prepare(a,z,a.hash);ProtectionStore.Inventory before=s.inspect();
    byte[] expected=completionBytes(r,t);byte[] prefix=Arrays.copyOf(expected,count);File stage=new File(r,t.id+".completion-stage");Files.write(stage.toPath(),prefix);
    ProtectionStore.Inventory partial=s.inspect();check(partial.pruneBlocked&&partial.reservedCompletionBytes==expected.length-count&&partial.reservedCompletionSlots==0,"prefix reservation incorrect");check(partial.actualBytes+partial.reservedCompletionBytes==before.actualBytes+before.reservedCompletionBytes,"prefix charge growth");
    d.calls.clear();for(int attempt=0;attempt<3;attempt++)fails(()->s.complete(t),"partial completion appended/retried");check(d.calls.isEmpty()&&Arrays.equals(prefix,Files.readAllBytes(stage.toPath()))&&r.list().length==4,"partial evidence changed");
   }
  });
  test("altered oversized foreign orphan and unbound stages never receive credit or mutate",()->{
   for(String bad:new String[]{"altered","oversized","foreign","orphan","unbound"}){
    File r=fresh();Durable d=new Durable();ProtectionStore s=store(r,d);ProtectionStore.Snapshot a=put(s,b("old"),"original"),z=put(s,b("new"),"attempted");ProtectionStore.Transaction t=s.prepare(a,z,a.hash);byte[] expected=completionBytes(r,t);
    String name=t.id+".completion-stage";byte[] content=expected.clone();if(bad.equals("altered"))content[0]^=1;if(bad.equals("oversized"))content=Arrays.copyOf(content,content.length+1);if(bad.equals("foreign"))content=Files.readAllBytes(new File(r,t.id+".transaction").toPath());if(bad.equals("orphan"))name=UUID.randomUUID()+".completion-stage";if(bad.equals("unbound"))name="unbound.completion-stage";
    File stage=new File(r,name);Files.write(stage.toPath(),content);byte[] retained=content.clone();long before=disk(r);ProtectionStore.Inventory invalid=s.inspect();check(invalid.pruneBlocked&&invalid.reservedCompletionBytes==expected.length&&invalid.reservedCompletionSlots==1,"invalid stage credited "+bad);
    d.calls.clear();fails(()->s.complete(t),"bad stage allowed completion "+bad);fails(()->put(s,b("write"),"attempted"),"bad stage allowed admission "+bad);check(d.calls.isEmpty()&&disk(r)==before&&Arrays.equals(retained,Files.readAllBytes(stage.toPath())),"bad stage mutated "+bad);
   }
  });
  test("complete plus deterministic stage and corrupt committed records fail closed",()->{
   for(String bad:new String[]{"duplicate","complete","preparation","source"}){
    File r=fresh();Durable d=new Durable();ProtectionStore s=store(r,d);ProtectionStore.Snapshot a=put(s,b("old"),"original"),z=put(s,b("new"),"attempted");ProtectionStore.Transaction t=s.prepare(a,z,a.hash);s.complete(t);byte[] complete=Files.readAllBytes(new File(r,t.id+".complete").toPath());
    File stage=new File(r,t.id+".completion-stage");Files.write(stage.toPath(),complete);if(!bad.equals("duplicate")){File corrupted=new File(r,bad.equals("complete")?t.id+".complete":bad.equals("preparation")?t.id+".transaction":a.id+".snapshot");byte[] raw=Files.readAllBytes(corrupted.toPath());raw[raw.length-1]^=1;Files.write(corrupted.toPath(),raw);}
    long before=disk(r);check(s.inspect().pruneBlocked,"corrupt duplicate stage graph accepted");d.calls.clear();fails(()->s.complete(t),"inconsistent completion accepted "+bad);fails(()->put(s,b("new"),"attempted"),"inconsistent admission accepted "+bad);check(d.calls.isEmpty()&&disk(r)==before&&Arrays.equals(complete,Files.readAllBytes(stage.toPath())),"inconsistent stage changed");
   }
  });
  test("completion checks immutable per-handle quota including existing full stage",()->{
   for(boolean staged:new boolean[]{false,true}){
    File r=fresh();Durable d=new Durable();ProtectionStore high=store(r,d);ProtectionStore.Snapshot a=put(high,b("old"),"original"),z=put(high,b("new"),"attempted");ProtectionStore.Transaction t=high.prepare(a,z,a.hash);if(staged){d.failure="file";fails(()->high.complete(t),"fault failed");d.failure="";}
    ProtectionStore.Inventory inventory=high.inspect();long charge=inventory.actualBytes+inventory.reservedCompletionBytes;ProtectionStore low=new ProtectionStore(r,d,charge-1);ProtectionStore.Inventory lower=low.inspect();check(lower.reservedCompletionBytes==inventory.reservedCompletionBytes,"reservation incorrectly depends on handle quota");long before=disk(r);d.calls.clear();fails(()->low.complete(t),"low quota granted future completion exemption");check(d.calls.isEmpty()&&disk(r)==before,"low quota changed bytes");high.complete(t);check(high.validate(t).verified,"low handle reset high quota");
   }
  });
  test("already committed completion remains idempotent under lower handle quota",()->{
   File r=fresh();Durable d=new Durable();ProtectionStore s=store(r,d);ProtectionStore.Snapshot a=put(s,b("old"),"original"),z=put(s,b("new"),"attempted");ProtectionStore.Transaction t=s.prepare(a,z,a.hash);s.complete(t);byte[] prepared=Files.readAllBytes(new File(r,t.id+".transaction").toPath()),completed=Files.readAllBytes(new File(r,t.id+".complete").toPath());long before=disk(r);
   ProtectionStore low=new ProtectionStore(r,d,0);d.calls.clear();low.complete(t);check(d.calls.equals(Arrays.asList("dir"))&&disk(r)==before,"verified idempotent completion re-admitted below lower quota");check(Arrays.equals(prepared,Files.readAllBytes(new File(r,t.id+".transaction").toPath()))&&Arrays.equals(completed,Files.readAllBytes(new File(r,t.id+".complete").toPath())),"idempotent v1 envelopes changed");
  });
  test("recursive same-target completion callback is rejected across handles",()->{
   File r=fresh();Durable d=new Durable();ProtectionStore s=store(r,d),other=store(r,d);ProtectionStore.Snapshot a=put(s,b("old"),"original"),z=put(s,b("new"),"attempted");ProtectionStore.Transaction t=s.prepare(a,z,a.hash);
   d.onFile=()->fails(()->other.complete(t),"same target recursively completed");s.complete(t);check(r.list().length==4&&s.validate(t).verified,"outer completion damaged by recursive callback");
  });
  test("full retry rejects bytes or identity changed by durability callback before move",()->{
   for(boolean replacement:new boolean[]{false,true}){
    File r=fresh();Durable d=new Durable();ProtectionStore s=store(r,d);ProtectionStore.Snapshot a=put(s,b("old"),"original"),z=put(s,b("new"),"attempted");ProtectionStore.Transaction t=s.prepare(a,z,a.hash);d.failure="file";fails(()->s.complete(t),"seed failure not exercised");d.failure="";File stage=new File(r,t.id+".completion-stage");byte[] retained=Files.readAllBytes(stage.toPath());byte[] changed=retained.clone();changed[0]^=1;
    d.calls.clear();d.onFile=()->{if(replacement){File alternate=new File(r,"replacement");Files.write(alternate.toPath(),retained);Files.move(alternate.toPath(),stage.toPath(),StandardCopyOption.REPLACE_EXISTING);}else Files.write(stage.toPath(),changed);};fails(()->s.complete(t),"changed stage moved");check(d.calls.equals(Arrays.asList("file"))&&stage.isFile()&&!new File(r,t.id+".complete").exists(),"changed stage reached move or discarded");check(Arrays.equals(replacement?retained:changed,Files.readAllBytes(stage.toPath())),"changed stage evidence overwritten");
   }
  });
  test("new completion stage rejects identity replacement during file sync",()->{
   File r=fresh();Durable d=new Durable();ProtectionStore s=store(r,d);ProtectionStore.Snapshot a=put(s,b("old"),"original"),z=put(s,b("new"),"attempted");ProtectionStore.Transaction t=s.prepare(a,z,a.hash);File stage=new File(r,t.id+".completion-stage");byte[] expected=completionBytes(r,t);
   d.calls.clear();d.onFile=()->{File replacement=new File(r,"replacement");Files.write(replacement.toPath(),expected);Files.move(replacement.toPath(),stage.toPath(),StandardCopyOption.REPLACE_EXISTING);};fails(()->s.complete(t),"unsynced replacement published by new stage");check(d.calls.equals(Arrays.asList("file"))&&stage.isFile()&&!new File(r,t.id+".complete").exists(),"replacement moved or discarded");check(Arrays.equals(expected,Files.readAllBytes(stage.toPath())),"new stage replacement evidence changed");
  });
  test("completion publication and failed publication invalidate validated source operation",()->{
   for(boolean failure:new boolean[]{false,true}){
    File r=fresh();Durable d=new Durable();ProtectionStore s=store(r,d);ProtectionStore.Snapshot a=put(s,b("old"),"original"),z=put(s,b("new"),"attempted");ProtectionStore.Transaction t=s.prepare(a,z,a.hash);if(failure)d.failure="file";
    fails(()->s.withValidatedSources(context->{try{s.complete(t);}catch(IOException expected){if(!failure)throw expected;}context.snapshot(s,a.id);return null;}),"completion failed to expire source context");check(r.list().length==4,"publication or retained attempt lost bytes");
   }
  });
  test("completion failures after move or directory sync retry committed bytes",()->{
   for(String failure:new String[]{"after-move","dir","after-dir"}){
    File r=fresh();Durable d=new Durable();ProtectionStore s=store(r,d);ProtectionStore.Snapshot a=put(s,b("old"),"original"),z=put(s,b("new"),"attempted");ProtectionStore.Transaction t=s.prepare(a,z,a.hash);byte[] expected=completionBytes(r,t);d.failure=failure;fails(()->s.complete(t),"late completion failure accepted");
    check(!new File(r,t.id+".completion-stage").exists()&&Arrays.equals(expected,Files.readAllBytes(new File(r,t.id+".complete").toPath())),"late failure lost exact completion");long size=disk(r);d.failure="";d.calls.clear();s.complete(t);check(d.calls.equals(Arrays.asList("dir"))&&disk(r)==size&&s.validate(t).verified,"late retry mutated or failed");
   }
  });
  test("legacy anonymous stage remains fully charged without completion credit",()->{
   File r=fresh();Durable d=new Durable();ProtectionStore s=store(r,d);ProtectionStore.Snapshot a=put(s,b("old"),"original"),z=put(s,b("new"),"attempted");ProtectionStore.Transaction t=s.prepare(a,z,a.hash);ProtectionStore.Inventory before=s.inspect();byte[] expected=completionBytes(r,t);File legacy=new File(r,UUID.randomUUID()+".stage");Files.write(legacy.toPath(),expected);
   ProtectionStore.Inventory staged=s.inspect();check(staged.actualBytes==before.actualBytes+expected.length&&staged.reservedCompletionBytes==expected.length&&staged.reservedCompletionSlots==1,"legacy stage inferred as credit");ProtectionStore tight=new ProtectionStore(r,d,staged.actualBytes+expected.length-1);fails(()->tight.complete(t),"legacy stage escaped quota");s.complete(t);check(Arrays.equals(expected,Files.readAllBytes(legacy.toPath()))&&r.list().length==5,"legacy stage discarded or consumed");
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
   fails(()->put(p,b("consume completion entry"),"attempted"),"reserved completion slot consumed");
   Durable completionFault=new Durable();ProtectionStore failing=new ProtectionStore(q,completionFault,ProtectionStore.DEFAULT_QUOTA_BYTES);completionFault.failure="file";
   for(int retry=0;retry<3;retry++)fails(()->failing.complete(t),"exact-cap completion fault not exercised");check(q.list().length==65536,"exact-cap stage retry growth");p.complete(t);
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
 static byte[] completionBytes(File root,ProtectionStore.Transaction t)throws Exception {
  byte[] raw=Files.readAllBytes(new File(root,t.id+".transaction").toPath());raw[8]=3;rehash(raw);return raw;
 }
 static class EditorHash {static String sha(byte[] input)throws Exception {StringBuilder hex=new StringBuilder();for(byte x:java.security.MessageDigest.getInstance("SHA-256").digest(input))hex.append(String.format("%02x",x&255));return hex.toString();}}
}
