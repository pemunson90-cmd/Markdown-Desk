package com.example.markdowndesk;
import java.io.*;
import java.security.*;
import java.util.*;
/** Independent fixture packing: never calls a production serializer to create expected bytes. */
public final class ScopedArchiveCheck {
 static int checks;
 static void ok(boolean x,String why){checks++;if(!x)throw new AssertionError(why);}
 static byte[] sha(byte[] x)throws Exception{return MessageDigest.getInstance("SHA-256").digest(x);}
 static String hex(byte[] x){StringBuilder s=new StringBuilder();for(byte b:x)s.append(String.format("%02x",b&255));return s.toString();}
 static UUID q(long n){return new UUID(0x4000L,0x8000000000000000L|n);}
 static void uuid(DataOutputStream o,UUID u)throws Exception{o.writeLong(u.getMostSignificantBits());o.writeLong(u.getLeastSignificantBits());}
 interface Pack {void put(DataOutputStream out)throws Exception;}
 static byte[] pack(Pack p)throws Exception{ByteArrayOutputStream b=new ByteArrayOutputStream();p.put(new DataOutputStream(b));return b.toByteArray();}
 static byte[] checked(byte[] body)throws Exception{return pack(o->{o.write(body);o.write(sha(body));});}
 static void string(DataOutputStream o,String s)throws Exception {if(s==null)o.writeInt(-1);else{byte[] b=s.getBytes("UTF-8");o.writeInt(b.length);o.write(b);}}
 static byte[] snapshot()throws Exception{
  byte[] payload={(byte)239,(byte)187,(byte)191,13,10};
  byte[] body=pack(o->{string(o,"00000000-0000-0000-0000-000000000007");string(o,"legacy-recovery");string(o,null);string(o,"");o.writeLong(0);string(o,"attempted");o.writeLong(0);string(o,hex(sha(payload)));o.writeInt(5);o.write(payload);});
  return checked(pack(o->{o.writeInt(0x4d445053);o.writeInt(1);o.writeByte(1);o.writeInt(body.length);o.write(body);}));
 }
 static byte[] prefs()throws Exception{return checked(pack(o->{o.write("MDSTATE\n".getBytes("US-ASCII"));o.writeInt(1);o.writeByte(8);o.writeInt(4);o.writeInt(0);o.writeInt(0);}));}
 static byte[] inventory(boolean mixed)throws Exception{
  byte[][] payloads=mixed?new byte[][]{snapshot(),prefs()}:new byte[0][];
  return checked(pack(o->{int n=payloads.length,L=144+93*n,P=0;for(byte[] p:payloads)P+=p.length;
   o.write("MDBUNDL\n".getBytes("US-ASCII"));o.writeInt(1);o.writeInt(L);uuid(o,q(1));uuid(o,q(2));o.writeInt(1);o.writeInt(n);o.writeInt(0);o.writeInt(0);o.writeLong(P);o.writeLong(122+L+58*n+P);
   uuid(o,q(2));o.writeByte(1);o.writeByte(3);o.writeInt(0);o.writeInt(n);o.writeByte(0);o.writeInt(0);o.writeByte(0);
   for(int i=0;i<n;i++){uuid(o,q(4+i));uuid(o,q(2));o.writeByte(i==0?1:8);o.writeLong(payloads[i].length);o.write(sha(payloads[i]));uuid(o,i==0?new UUID(0,7):q(6));o.writeInt(0);}
  }));
 }
 static void frame(DataOutputStream o,int type,UUID id,byte[] p)throws Exception{o.writeByte(1);o.writeByte(type);uuid(o,id);o.writeLong(p.length);o.write(sha(p));o.write(p);}
 static byte[] archive(byte[] inv,boolean mixed)throws Exception{
  byte[] prefix=pack(o->{o.write("MDESKBACKUP\r\n\032\n".getBytes("US-ASCII"));o.writeInt(2);frame(o,10,q(3),inv);if(mixed){frame(o,1,q(4),snapshot());frame(o,8,q(5),prefs());}});
  return pack(o->{o.write(prefix);o.writeByte(0);o.writeInt(mixed?3:1);o.writeLong(prefix.length);o.write(sha(prefix));});
 }
 interface Attempt {void run()throws Exception;}
 static void fails(Attempt a,String why)throws Exception {checks++;try{a.run();}catch(IOException expected){return;}throw new AssertionError("Expected rejection: "+why);}
 static byte[] recheck(byte[] b)throws Exception {System.arraycopy(sha(Arrays.copyOf(b,b.length-32)),0,b,b.length-32,32);return b;}
 public static void main(String[] args)throws Exception{
  byte[] empty=inventory(false),mixed=inventory(true),a=archive(empty,false),b=archive(mixed,true);
  ok(empty.length==144&&a.length==266&&mixed.length==330&&b.length==843,"independent lengths");
  ok(hex(sha(a)).equals("610ef92d1a44d0d330d85b17ed2e594fbb925d437ec0aee57d173f3dd85173f2"),"empty golden");
  ok(hex(sha(b)).equals("9be88cf0fe6ee6b1a4fb1355fde9dcb2467495a0f87ba003a633fe713b46e3ac"),"mixed golden");
  PrivateBundleInventory.MetadataBudget budget=new PrivateBundleInventory.MetadataBudget();
  for(byte[] inv:new byte[][]{empty,mixed}) {
   try(PrivateBundleInventory parsed=PrivateBundleInventory.read(new ByteArrayInputStream(inv),budget)) {
    ok(parsed.serializedLength==inv.length,"parsed length");ok(budget.usedBytes()>0,"retained budget");
    ByteArrayOutputStream out=new ByteArrayOutputStream();parsed.write(out);ok(Arrays.equals(inv,out.toByteArray()),"canonical roundtrip");
   }
   ok(budget.usedBytes()==0,"released budget");
   for(int n=0;n<inv.length;n++){final int cut=n;fails(()->PrivateBundleInventory.read(new ByteArrayInputStream(Arrays.copyOf(inv,cut)),budget),"inventory truncation");ok(budget.usedBytes()==0,"failed parse releases");}
  }
  byte[] bad=empty.clone();bad[106]=2;bad=recheck(bad);final byte[] badMarker=bad;
  fails(()->PrivateBundleInventory.read(new ByteArrayInputStream(badMarker),budget),"optional marker");
  bad=mixed.clone();bad[143]=99;bad=recheck(bad);final byte[] missingScope=bad;
  fails(()->PrivateBundleInventory.read(new ByteArrayInputStream(missingScope),budget),"missing scope");
  try(PrivateBundleInventory.Reservation held=budget.reserve(16L<<20)) {fails(()->PrivateBundleInventory.read(new ByteArrayInputStream(empty),budget),"held shared budget");}
  ok(budget.usedBytes()==0,"budget final");
  FramingCheck.run();
  System.out.println("PASS scoped checks="+checks);
 }
}
