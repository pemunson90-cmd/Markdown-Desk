package com.example.markdowndesk;
import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import static com.example.markdowndesk.ScopedArchiveCheck.*;

/** Independent byte packing comes from the reviewed fixtures; new expectations never use codecs. */
public final class WireCheck {
 static PrivateArchive.LegacySpool spool() {
  return new PrivateArchive.LegacySpool(){
   public OutputStream begin(PrivateArchive.Entry e){return new OutputStream(){public void write(int b){}};}
   public void finish(PrivateArchive.Entry e,OutputStream o,boolean good){ok(good,"v1 verified payload");}
  };
 }
 static void legacy()throws Exception {
  byte[] p=prefs(); ByteArrayOutputStream out=new ByteArrayOutputStream();
  PrivateArchive.write(new PrivateArchive.ArchiveSource(){boolean done;public PrivateArchive.SourceEntry next(){if(done)return null;done=true;return new PrivateArchive.SourceEntry(q(4),PrivateArchive.Type.PREFERENCES,p.length,()->new ByteArrayInputStream(p));}},out);
  byte[] expected=pack(o->{byte[] prefix=pack(v->{v.write("MDESKBACKUP\r\n\032\n".getBytes("US-ASCII"));v.writeInt(1);frame(v,8,q(4),p);});o.write(prefix);o.writeByte(0);o.writeInt(1);o.writeLong(prefix.length);o.write(sha(prefix));});
  ok(Arrays.equals(expected,out.toByteArray()),"v1 golden unchanged");
  PrivateBundleInventory.MetadataBudget budget=new PrivateBundleInventory.MetadataBudget(1L<<20);
  try(PrivateBundleInventory.Reservation coordinator=budget.reserve(1024);PrivateArchive.PortableResult result=PrivateArchive.readPortable(new ByteArrayInputStream(expected),null,spool(),budget)) {
   ok(result.inventory==null&&result.sourceSummary.entryCount==1,"small v1 shares nonempty budget");
  }
  ok(budget.usedBytes()==0,"v1 logical workspace settlement");
 }
 static byte[] evidenceInventory(int schema,long payloadLength,int type)throws Exception {
  return evidenceInventory(schema,payloadLength,type,sha(new byte[]{1,2,3}));
 }
 static byte[] evidenceInventory(int schema,long payloadLength,int type,byte[] hash)throws Exception {
  return checked(pack(o->{int L=237;o.write("MDBUNDL\n".getBytes("US-ASCII"));o.writeInt(schema);o.writeInt(L);uuid(o,q(1));uuid(o,q(2));o.writeInt(1);o.writeInt(1);o.writeInt(0);o.writeInt(0);o.writeLong(payloadLength);o.writeLong(122+L+58+payloadLength);
   uuid(o,q(2));o.writeByte(1);o.writeByte(3);o.writeInt(0);o.writeInt(1);o.writeByte(0);o.writeInt(0);o.writeByte(0);
   uuid(o,q(4));uuid(o,q(2));o.writeByte(type);o.writeLong(payloadLength);o.write(hash);uuid(o,q(6));o.writeInt(0);
  }));
 }
 static byte[] v3(byte[] inv,int version)throws Exception {
  byte[] prefix=pack(o->{o.write("MDESKBACKUP\r\n\032\n".getBytes("US-ASCII"));o.writeInt(version);frame(o,10,q(3),inv);frame(o,11,q(4),new byte[]{1,2,3});});
  return pack(o->{o.write(prefix);o.writeByte(0);o.writeInt(2);o.writeLong(prefix.length);o.write(sha(prefix));});
 }
 static Object invoke(String name,Class<?>[] types,Object...args)throws Exception {
  try{return PrivateBundleInventory.class.getMethod(name,types).invoke(null,args);}
  catch(NoSuchMethodException e){throw new AssertionError("Missing schema2 inventory API",e);}
  catch(InvocationTargetException e){if(e.getCause() instanceof IOException)throw (IOException)e.getCause();throw e;}
 }
 static PrivateBundleInventory parse2(byte[] inv,PrivateBundleInventory.MetadataBudget b)throws Exception {
  return (PrivateBundleInventory)invoke("readSchema2",new Class<?>[]{InputStream.class,PrivateBundleInventory.MetadataBudget.class},new ByteArrayInputStream(inv),b);
 }
 static void scoped()throws Exception {
  byte[] inv=evidenceInventory(2,3,11), wire=v3(inv,3);
  PrivateBundleInventory.MetadataBudget budget=new PrivateBundleInventory.MetadataBudget();
  try(PrivateBundleInventory inventory=parse2(inv,budget)) {
   ByteArrayOutputStream output=new ByteArrayOutputStream();inventory.write(output);
   ok(Arrays.equals(inv,output.toByteArray()),"schema2 independent golden roundtrip");
   ok(inventory.entries().get(0).identity.equals(q(6)),"evidence identity");
  }
  final int[] callbacks={0};
  PrivateArchive.ScopedSink sink=new PrivateArchive.ScopedSink(){public OutputStream begin(PrivateBundleInventory.DirectoryEntry e){callbacks[0]++;return new ByteArrayOutputStream();}public void finish(PrivateBundleInventory.DirectoryEntry e,OutputStream o,boolean verified){ok(verified,"framing only payload verified");}};
  try(PrivateArchive.PortableResult result=PrivateArchive.readPortable(new ByteArrayInputStream(wire),sink,null,budget)) {
   ok(result.inventory.entries().get(0).type==11&&callbacks[0]==1,"v3 evidence framing dispatch");
  }
  PrivateArchive.Type type=Enum.valueOf(PrivateArchive.Type.class,"OPERATION_EVIDENCE");
  ByteArrayOutputStream output=new ByteArrayOutputStream();
  PrivateArchive.ArchiveSource source=new PrivateArchive.ArchiveSource(){boolean done;public PrivateArchive.SourceEntry next(){if(done)return null;done=true;return new PrivateArchive.SourceEntry(q(4),type,3,()->new ByteArrayInputStream(new byte[]{1,2,3}));}};
  try{PrivateArchive.class.getMethod("writeV3",UUID.class,PrivateArchive.StreamOpener.class,PrivateArchive.ArchiveSource.class,OutputStream.class,PrivateBundleInventory.MetadataBudget.class).invoke(null,q(3),(PrivateArchive.StreamOpener)()->new ByteArrayInputStream(inv),source,output,budget);}
  catch(NoSuchMethodException e){throw new AssertionError("Missing writeV3 API",e);}
  ok(Arrays.equals(wire,output.toByteArray()),"v3 independent golden output");
  fails(()->PrivateBundleInventory.read(new ByteArrayInputStream(inv),budget),"schema1 reader rejects schema2");
  fails(()->parse2(evidenceInventory(1,3,11),budget),"schema2 reader rejects schema1");
  fails(()->PrivateBundleInventory.read(new ByteArrayInputStream(evidenceInventory(1,3,11)),budget),"schema1 rejects evidence type");
  fails(()->parse2(evidenceInventory(2,3,10),budget),"schema2 rejects second inventory type");
  fails(()->parse2(evidenceInventory(2,1L<<30,11),budget),"capsule individual cap cannot evade total1GiB");
  fails(()->parse2(evidenceInventory(2,(1L<<30)+1,11),budget),"capsule individual over cap");
  fails(()->PrivateArchive.readPortable(new ByteArrayInputStream(v3(inv,2)),sink,null,budget),"v2 rejects schema2/type11");
  fails(()->PrivateArchive.readPortable(new ByteArrayInputStream(v3(inventory(false),3)),sink,null,budget),"v3 rejects schema1");
  fails(()->PrivateArchive.readPortable(new ByteArrayInputStream(v3(inv,4)),sink,null,budget),"future archive version");
  fails(()->PrivateArchive.read(new ByteArrayInputStream(wire),spool()),"v1 rejects v3");
  fails(()->PrivateArchive.read(new ByteArrayInputStream(v3(inv,1)),spool()),"v1 rejects type10/type11");
  fails(()->PrivateArchive.writeV2(q(3),()->new ByteArrayInputStream(inv),()->null,new ByteArrayOutputStream(),budget),"v2 writer rejects schema2");
  byte[] badIdentity=inv.clone();Arrays.fill(badIdentity,185,201,(byte)0);final byte[] foreignIdentity=recheck(badIdentity);
  fails(()->parse2(foreignIdentity,budget),"evidence identity must be generated");
  fails(()->PrivateArchive.write(()->new PrivateArchive.SourceEntry(q(4),type,3,()->new ByteArrayInputStream(new byte[]{1,2,3})),new ByteArrayOutputStream()),"v1 writer rejects evidence");
  for(int cut:new int[]{0,18,19,76,wire.length-33,wire.length-1}) {final int end=cut;fails(()->PrivateArchive.readPortable(new ByteArrayInputStream(Arrays.copyOf(wire,end)),sink,null,budget),"v3 truncation");}
  ok(budget.usedBytes()==0,"v3 failures release logical owners");
  for(boolean mixed:new boolean[]{false,true}) {
   byte[] old=inventory(mixed);ByteArrayOutputStream oldOut=new ByteArrayOutputStream();
   PrivateArchive.writeV2(q(3),()->new ByteArrayInputStream(old),FramingCheck.source(mixed,false,new int[1]),oldOut,budget);
   ok(Arrays.equals(archive(old,mixed),oldOut.toByteArray()),"v2 bitwise compatibility");
  }
 }
 static void shared()throws Exception {
  long[] charged={1024};long maximum=16L<<20;
  PrivateBundleInventory.MetadataBudget b=new PrivateBundleInventory.MetadataBudget(maximum,n->{if(n<0||n>maximum-charged[0])throw new IOException("parent exhausted");charged[0]+=n;});
  long held;
  try(PrivateBundleInventory inv=PrivateBundleInventory.read(new ByteArrayInputStream(inventory(false)),b)){held=charged[0];ok(held>1024,"inventory account debits original parent");}
  ok(b.usedBytes()==0&&charged[0]==held,"close never refunds parent");
  try(PrivateBundleInventory.Reservation token=b.reserve(10)){token.grow(20);ok(b.usedBytes()==30,"single grown owner");}
  ok(charged[0]==held+30,"growth debits same parent");
  byte[] inv=inventory(false);long[] denied={0};
  PrivateBundleInventory.MetadataBudget exhausted=new PrivateBundleInventory.MetadataBudget(maximum,n->{denied[0]++;throw new IOException("parent exhausted");});
  fails(()->PrivateBundleInventory.read(new ByteArrayInputStream(inv),exhausted),"parent failure blocks account allocation");
  ok(exhausted.usedBytes()==0&&denied[0]==1,"parent failure creates no local credit");
  ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] payload=prefs();PrivateArchive.write(new PrivateArchive.ArchiveSource(){boolean done;public PrivateArchive.SourceEntry next(){if(done)return null;done=true;return new PrivateArchive.SourceEntry(q(4),PrivateArchive.Type.PREFERENCES,payload.length,()->new ByteArrayInputStream(payload));}},out);
  final int[] reads={0},begins={0};
  fails(()->PrivateArchive.readPortable(new InputStream(){public int read(){reads[0]++;return -1;}},null,spool(),new PrivateBundleInventory.MetadataBudget((128L<<10)-1)),"fixed reader workspace precharge");
  ok(reads[0]==0,"fixed refusal precedes caller read");
  PrivateArchive.LegacySpool counted=new PrivateArchive.LegacySpool(){public OutputStream begin(PrivateArchive.Entry e){begins[0]++;return new ByteArrayOutputStream();}};
  fails(()->PrivateArchive.readPortable(new ByteArrayInputStream(out.toByteArray()),null,counted,new PrivateBundleInventory.MetadataBudget((128L<<10)+383)),"index precharge before first callback");
  ok(begins[0]==0,"index refusal precedes staging callback");
  try(PrivateArchive.PortableResult r=PrivateArchive.readPortable(new ByteArrayInputStream(out.toByteArray()),null,spool(),new PrivateBundleInventory.MetadataBudget((128L<<10)+384))){ok(r.sourceSummary.entryCount==1,"index exact boundary");}
  long[] lifetime={0};long parentLimit=3*((128L<<10)+384)-1;
  PrivateBundleInventory.MetadataBudget repeated=new PrivateBundleInventory.MetadataBudget(16L<<20,n->{if(n>parentLimit-lifetime[0])throw new IOException("original lifetime exhausted");lifetime[0]+=n;});
  for(int i=0;i<2;i++)try(PrivateArchive.PortableResult ignored=PrivateArchive.readPortable(new ByteArrayInputStream(out.toByteArray()),null,spool(),repeated)){}
  long settled=lifetime[0];ok(repeated.usedBytes()==0&&settled==2*((128L<<10)+384),"settled readers keep monotonic parent debits");
  fails(()->PrivateArchive.readPortable(new ByteArrayInputStream(out.toByteArray()),null,counted,repeated),"later reader cannot gain fresh full allowance");
  ok(lifetime[0]>settled&&repeated.usedBytes()==0,"failed index keeps original workspace debit without local leak");
 }
 public static void main(String[] args)throws Exception {legacy();shared();scoped();System.out.println("PASS portable wire checks="+checks);}

}
