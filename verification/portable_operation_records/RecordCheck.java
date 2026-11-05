package com.example.markdowndesk;

import java.io.*;
import java.security.*;
import java.util.*;
import static com.example.markdowndesk.PrivateOperationRecord.*;

/** Expected envelopes are packed directly from the accepted wire fields, never by the codecs. */
public final class RecordCheck {
 static int checks;
 static final UUID ZERO=new UUID(0,0), PREP=id(1), OP=id(2), BUNDLE=id(3), OUTER=id(20);
 static final String H=hex(sha(new byte[]{1,2,3}));
 static final String[] NAMES={"snapshots","catalog","identity","index"};
 static HistoryControlStore.Registration registration;
 interface Pack {void run(DataOutputStream out)throws Exception;}
 interface Task {void run()throws Exception;}
 static UUID id(long n){return new UUID(0x123456789abc4000L,0x8000000000000000L|n);}
 static byte[] pack(Pack p)throws Exception{ByteArrayOutputStream out=new ByteArrayOutputStream();p.run(new DataOutputStream(out));return out.toByteArray();}
 static byte[] sha(byte[] b){try{return MessageDigest.getInstance("SHA-256").digest(b);}catch(Exception e){throw new AssertionError(e);}}
 static String hex(byte[] b){StringBuilder out=new StringBuilder();for(byte v:b)out.append(String.format(Locale.ROOT,"%02x",v&255));return out.toString();}
 static void uuid(DataOutputStream out,UUID id)throws IOException{out.writeLong(id.getMostSignificantBits());out.writeLong(id.getLeastSignificantBits());}
 static void hash(DataOutputStream out,String h)throws IOException{for(int i=0;i<64;i+=2)out.writeByte(Integer.parseInt(h.substring(i,i+2),16));}
 static byte[] envelope(int magic,int version,int type,byte[] body)throws Exception{return pack(out->{byte[] prefix=pack(o->{o.writeInt(magic);o.writeInt(version);o.writeByte(type);o.writeInt(body.length);o.write(body);});out.write(prefix);out.write(sha(prefix));});}
 static byte[] mdop(int version,int type,byte[] body)throws Exception{return envelope(0x4d444f50,version,type,body);}
 static void check(boolean yes,String why){checks++;if(!yes)throw new AssertionError(why);}
 static void rejects(Task t,String why)throws Exception{try{t.run();throw new AssertionError("Accepted "+why);}catch(IOException expected){checks++;}}
 static List<HistoryControlStore.Role> roles()throws IOException{List<HistoryControlStore.Role> r=new ArrayList<>();for(int i=0;i<4;i++)r.add(new HistoryControlStore.Role(i+1,NAMES[i]));return r;}
 static Binding binding(int phase,int flags)throws Exception {
  return phase==3?Binding.portable(PREP,BUNDLE,registration.envelopeHash,0,registration.envelopeHash,64L<<20,64L<<20,20,roles(),H,H,flags):
   new Binding(PREP,phase,BUNDLE,registration.envelopeHash,0,registration.envelopeHash,64L<<20,64L<<20,20,roles(),H,H,flags);
 }
 static byte[] bindingWire(int phase,int flags)throws Exception{return pack(o->{uuid(o,PREP);o.writeByte(phase);uuid(o,BUNDLE);hash(o,registration.envelopeHash);o.writeLong(0);hash(o,registration.envelopeHash);o.writeLong(64L<<20);o.writeLong(64L<<20);o.writeInt(20);
  for(int i=0;i<4;i++){byte[] name=NAMES[i].getBytes("UTF-8");o.writeByte(i+1);o.writeInt(name.length);o.write(name);}hash(o,H);hash(o,H);o.writeByte(flags);});}
 static Artifact artifact(int kind,long length)throws Exception{return Artifact.portable(kind,id(9+kind),kind<=5?ZERO:id(24+kind),0,length,H);}
 static void artifactWire(DataOutputStream o,Artifact a)throws Exception{o.writeByte(a.kind);uuid(o,a.id);uuid(o,a.item);o.writeInt(a.ordinal);o.writeLong(a.length);hash(o,a.hash);}
 static List<Artifact> frames()throws Exception{return Arrays.asList(artifact(6,100),artifact(7,100),artifact(8,100));}
 static List<Artifact> dpRows(long evidenceLength)throws Exception{List<Artifact> r=new ArrayList<>();r.add(artifact(3,516));r.add(artifact(4,evidenceLength));r.add(artifact(5,440));r.addAll(frames());return r;}
 static byte[] descriptorWire(int version,int phase,int flags,UUID outer,Artifact map,List<Artifact> rows)throws Exception {
  return mdop(version,3,pack(o->{o.write(bindingWire(phase,flags));if(version==2)uuid(o,outer);artifactWire(o,map);o.writeInt(rows.size());for(Artifact a:rows)artifactWire(o,a);}));
 }
 static byte[] pinWire(int domain,long count,long length,String h,int purpose,int scope)throws Exception {
  return envelope(0x4d44504e,1,1,pack(o->{uuid(o,OP);uuid(o,BUNDLE);hash(o,registration.envelopeHash);o.writeLong(0);hash(o,registration.envelopeHash);o.writeByte(purpose);o.writeByte(scope);o.writeByte(domain);o.writeLong(count);o.writeLong(length);hash(o,h);o.writeInt(0);}));
 }
 static byte[] prepareWire(int version,int phase,int flags,int dirs,List<Artifact> rows,byte[] begin)throws Exception {
  return mdop(version,1,pack(o->{o.write(bindingWire(phase,flags));o.writeByte(dirs);o.writeInt(rows.size());for(Artifact a:rows)artifactWire(o,a);o.write(begin);}));
 }
 static byte[] linkWire(int version,byte[] prepare,byte[] begin,String descriptor)throws Exception {
  return mdop(version,2,pack(o->{uuid(o,PREP);uuid(o,OP);hash(o,hex(sha(prepare)));hash(o,hex(sha(begin)));hash(o,descriptor);}));
 }
 static void oldGoldens()throws Exception {
  for(int phase:new int[]{1,2}) {
   int flags=phase==1?0:3;List<Artifact> privateRows=phase==1?Collections.emptyList():frames();Artifact map=artifact(1,100);
   byte[] dp=descriptorWire(1,phase,flags,null,map,privateRows);PrivateOperationRecord actual=descriptor(binding(phase,flags),map,privateRows);
   check(Arrays.equals(dp,actual.encoded()),"v1 descriptor golden phase"+phase);
   Artifact descriptor=new Artifact(2,id(11),ZERO,0,dp.length,hex(sha(dp)));List<Artifact> rows=new ArrayList<>();rows.add(map);rows.add(descriptor);rows.addAll(privateRows);
   byte[] begin=pinWire(2,privateRows.size(),dp.length,hex(sha(dp)),1,2);HistoryPinRecord pin=HistoryPinRecord.begin(OP,registration,registration,HistoryPinRecord.Purpose.EXPORT,HistoryPinRecord.Scope.ALL_BUNDLE,
    new HistoryPinRecord.Manifest(HistoryPinRecord.Domain.LOCAL_CAPTURE_V1,privateRows.size(),dp.length,hex(sha(dp))),Collections.emptyList());
   check(Arrays.equals(begin,HistoryPinRecord.encode(pin)),"domain2 BEGIN unchanged");
   byte[] p=prepareWire(1,phase,flags,3,rows,begin);PrivateOperationRecord prep=prepare(binding(phase,flags),3,rows,pin);
   check(Arrays.equals(p,prep.encoded()),"v1 P unchanged");check(Arrays.equals(linkWire(1,p,begin,descriptor.hash),link(prep).encoded()),"v1 L unchanged");
   byte[] close=envelope(0x4d44504e,1,2,pack(o->{uuid(o,OP);hash(o,hex(sha(begin)));hash(o,descriptor.hash);o.writeByte(2);}));
   check(Arrays.equals(close,HistoryPinRecord.close(pin,HistoryPinRecord.Outcome.CANCELLED)),"domain2 CLOSE unchanged");
   rejects(()->decodePortable(p),"explicit portable rejects legacy P");rejects(()->HistoryPinRecord.decodePortable(begin),"explicit portable rejects domain2");
  }
  byte[] nativeBegin=pinWire(1,0,0,H,1,2);check(Arrays.equals(nativeBegin,HistoryPinRecord.encode(HistoryPinRecord.decode(nativeBegin))),"domain1 golden unchanged");
 }
 static void portable()throws Exception {
  Binding b=binding(3,3);Artifact map=artifact(1,100);List<Artifact> rest=dpRows(56);byte[] dp=descriptorWire(2,3,3,OUTER,map,rest);
  PrivateOperationRecord d=descriptorPortable(b,OUTER,map,rest);check(Arrays.equals(dp,d.encoded())&&d.version==2&&d.inventoryOuterId.equals(OUTER),"portable Dp independent golden");
  check(dp.length==65+bindingWire(3,3).length+77*7,"Dp size formula");
  Artifact descriptor=Artifact.portable(2,id(11),ZERO,0,dp.length,hex(sha(dp)));List<Artifact> rows=new ArrayList<>();rows.add(map);rows.add(descriptor);rows.addAll(rest);
  byte[] begin=pinWire(3,4,dp.length,descriptor.hash,1,2);HistoryPinRecord pin=HistoryPinRecord.beginPortable(OP,registration,registration,new HistoryPinRecord.Manifest(HistoryPinRecord.Domain.PORTABLE_CAPTURE_V1,4,dp.length,descriptor.hash));
  check(begin.length==204&&Arrays.equals(begin,HistoryPinRecord.encode(pin)),"domain3 BEGIN golden204");
  byte[] p=prepareWire(2,3,3,3,rows,begin);PrivateOperationRecord prep=preparePortable(b,3,rows,pin);check(Arrays.equals(p,prep.encoded()),"portable P golden");
  check(p.length==254+bindingWire(3,3).length+77*8,"P size formula");
  byte[] l=linkWire(2,p,begin,descriptor.hash);check(l.length==173&&Arrays.equals(l,linkPortable(prep).encoded()),"portable L golden173");
  check(Arrays.equals(prepareWire(2,3,3,2,rows,begin),preparePortable(b,2,rows,pin).encoded()),"directory mask2 remains valid");
  byte[] c=HistoryPinRecord.close(pin,HistoryPinRecord.Outcome.RELEASED);check(c.length==126&&HistoryPinRecord.matches(pin,HistoryPinRecord.decode(c)),"portable CLOSE126 exact begin binding");
  check(artifact(3,144).filename().endsWith(".bundle")&&artifact(4,56).filename().endsWith(".capsule")&&artifact(5,52).filename().endsWith(".catalog"),"canonical portable filenames");
  rejects(()->decode(dp),"generic MDOP reader rejects v2");rejects(()->HistoryPinRecord.decode(begin),"generic pin reader rejects domain3");
  rejects(()->HistoryPinRecord.begin(OP,registration,registration,HistoryPinRecord.Purpose.EXPORT,HistoryPinRecord.Scope.ALL_BUNDLE,pin.manifest,Collections.emptyList()),"generic BEGIN rejects domain3");
  rejects(()->descriptor(b, map,rest),"generic descriptor rejects portable");rejects(()->prepare(b,3,rows,pin),"generic P rejects portable");rejects(()->link(prep),"generic link rejects portable");
  rejects(()->decodePortable(descriptorWire(2,2,3,OUTER,map,rest)),"v2 phase2");rejects(()->decode(descriptorWire(1,3,3,null,map,rest)),"v1 phase3");
  rejects(()->descriptorPortable(b, map.id,map,rest),"outer artifact collision");rejects(()->descriptorPortable(b, rest.get(3).item,map,rest),"outer private identity collision");
  List<Artifact> missing=new ArrayList<>(rest);missing.remove(1);rejects(()->descriptorPortable(b,OUTER,map,missing),"missing evidence row");
  List<Artifact> unordered=new ArrayList<>(rest);Collections.swap(unordered,0,1);rejects(()->descriptorPortable(b,OUTER,map,unordered),"artifact order");
  List<Artifact> duplicate=new ArrayList<>(rest);duplicate.add(1,rest.get(0));rejects(()->descriptorPortable(b,OUTER,map,duplicate),"duplicate kind/id");
  rejects(()->Artifact.portable(4,id(13),id(99),0,56,H),"metadata item must zero");rejects(()->Artifact.portable(4,id(13),ZERO,1,56,H),"metadata ordinal zero");
  check(Artifact.portable(4,id(13),ZERO,0,1L<<30,H).length==1L<<30,"evidence individual1GiB cap");rejects(()->Artifact.portable(4,id(13),ZERO,0,(1L<<30)+1,H),"evidence over cap");
  rejects(()->new Artifact(4,id(13),ZERO,0,56,H),"old artifact constructor rejects portable kind");
  rejects(()->descriptorPortable(binding(3,2),OUTER,map,rest),"recovery flag mismatch");rejects(()->binding(3,1),"preferences required");
  rejects(()->decodePortable(prepareWire(2,3,3,1,rows,begin)),"directory mask");
  rejects(()->decodePortable(prepareWire(2,3,3,3,rows,pinWire(2,4,dp.length,descriptor.hash,1,2))),"P requires domain3");
  rejects(()->decodePortable(prepareWire(2,3,3,3,rows,pinWire(3,4,dp.length,H,1,2))),"P exact descriptor hash");
  rejects(()->decodePortable(prepareWire(2,3,3,3,rows,pinWire(3,3,dp.length,descriptor.hash,1,2))),"ordinary count includes all private and evidence");
  rejects(()->HistoryPinRecord.decodePortable(pinWire(3,4,dp.length,descriptor.hash,2,2)),"domain3 EXPORT only");
  rejects(()->HistoryPinRecord.decodePortable(pinWire(3,16381,dp.length,descriptor.hash,1,2)),"portable ordinary count bound");
  rejects(()->HistoryPinRecord.decodePortable(pinWire(3,4,16385,descriptor.hash,1,2)),"portable Dp bound");
  List<Artifact> tooBig=new ArrayList<>(rest);tooBig.set(0,artifact(3,16L<<20));rejects(()->descriptorPortable(b,OUTER,map,tooBig),"combined serialized metadata");
  // Actual capsule cap cannot fit in the shared archive even before native payloads.
  List<Artifact> huge=dpRows(1L<<30);byte[] hd=descriptorWire(2,3,3,OUTER,map,huge);Artifact ha=Artifact.portable(2,id(11),ZERO,0,hd.length,hex(sha(hd)));List<Artifact> hr=new ArrayList<>();hr.add(map);hr.add(ha);hr.addAll(huge);
  rejects(()->decodePortable(prepareWire(2,3,3,3,hr,pinWire(3,4,hd.length,ha.hash,1,2))),"known payload lower bound exceeds1GiB");
  maxRows(b,map);
  byte[] negative=mdop(2,3,pack(o->{o.write(bindingWire(3,3));uuid(o,OUTER);artifactWire(o,map);o.writeInt(-1);}));
  rejects(()->decodePortable(negative),"unsigned count outside representable range");
  rejects(()->decodePortable(mdop(3,3,Arrays.copyOfRange(dp,13,dp.length-32))),"unknown MDOP version");
  byte[] mismatch=mdop(2,2,Arrays.copyOfRange(l,13,l.length-33));rejects(()->decodePortable(mismatch),"exact LINK length required");
  byte[] badBinding=Arrays.copyOfRange(dp,13,dp.length-32);
  // Foreign persisted bundle remains data; malformed generated preparation identity must reject.
  Arrays.fill(badBinding,0,16,(byte)0);rejects(()->decodePortable(mdop(2,3,badBinding)),"generated preparation UUID required");
  byte[] utf=Arrays.copyOfRange(dp,13,dp.length-32);utf[130]=(byte)255;rejects(()->decodePortable(mdop(2,3,utf)),"strict role UTF8");
  byte[] corrupt=dp.clone();corrupt[corrupt.length-1]^=1;rejects(()->decodePortable(corrupt),"checksum");
  for(int cut:new int[]{0,13,45,dp.length-33,dp.length-1}){final int end=cut;rejects(()->decodePortable(Arrays.copyOf(dp,end)),"Dp truncation");}
  byte[] trailing=Arrays.copyOf(dp,dp.length+1);rejects(()->decodePortable(trailing),"trailing record");
 }
 static void maxRows(Binding b,Artifact map)throws Exception {
  List<Artifact> r=new ArrayList<>();r.add(artifact(3,1539));r.add(artifact(4,56));r.add(artifact(5,1507));
  for(int i=0;i<12;i++)r.add(Artifact.portable(6,id(100+i),id(200+i),i,100,H));r.add(artifact(7,100));r.add(artifact(8,100));
  byte[] d=descriptorPortable(b,OUTER,map,r).encoded();check(r.size()==17&&d.length==65+bindingWire(3,3).length+77*18,"maximum Dp rows and14 private frames");
  Artifact a=Artifact.portable(2,id(11),ZERO,0,d.length,hex(sha(d)));List<Artifact> p=new ArrayList<>();p.add(map);p.add(a);p.addAll(r);
  HistoryPinRecord pin=HistoryPinRecord.decodePortable(pinWire(3,15,d.length,a.hash,1,2));check(preparePortable(b,3,p,pin).artifacts.size()==19,"maximum portable P19 rows");
  List<Artifact> oversized=new ArrayList<>(r);oversized.add(artifact(8,100));rejects(()->descriptorPortable(b,OUTER,map,oversized),"Dp row count over17 before encoding");
  List<Artifact> excessP=new ArrayList<>(p);excessP.add(artifact(8,100));rejects(()->preparePortable(b,3,excessP,pin),"P row count over19 before encoding");
  rejects(()->Artifact.portable(6,id(119),id(219),12,100,H),"thirteenth tab ordinal");
  List<Artifact> collision=new ArrayList<>(r);collision.set(3,Artifact.portable(6,id(100),map.id,0,100,H));rejects(()->descriptorPortable(b,OUTER,map,collision),"private item aliases metadata artifact");
  List<Artifact> gap=new ArrayList<>(r);gap.remove(4);rejects(()->descriptorPortable(b,OUTER,map,gap),"tab ordinal gap");
 }
 public static void main(String[] args)throws Exception {
  registration=(HistoryControlStore.Registration)HistoryControlStore.decode(HistoryControlStore.encodeRegistration(BUNDLE,64L<<20,roles(),64L<<20,20));
  oldGoldens();portable();System.out.println("PortableOperationRecord PASS "+checks+" checks");
 }
}
