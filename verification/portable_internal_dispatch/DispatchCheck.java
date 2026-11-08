package com.example.markdowndesk;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Real registered control namespace; structural controls only, no Writer or activation claim. */
public final class DispatchCheck {
 static int checks;
 static final UUID ZERO=new UUID(0,0);
 interface Task {void run()throws Exception;}
 static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
 static void reject(Task task,String why)throws Exception{try{task.run();throw new AssertionError("Accepted "+why);}catch(IOException expected){checks++;}}
 static String hash(byte[] raw){return EvidenceCheck.sha(raw);}
 static byte[] version(byte[] raw,int v){byte[] out=raw.clone();out[7]=(byte)v;return checksum(out);}
 static byte[] checksum(byte[] out){byte[] sum=EvidenceCheck.hash(hash(Arrays.copyOf(out,out.length-32)));System.arraycopy(sum,0,out,out.length-32,32);return out;}
 static PrivateOperationRecord portable(BaselineCheck.F f)throws Exception {return portable(f,UUID.randomUUID());}
 static PrivateOperationRecord portable(BaselineCheck.F f,UUID operation)throws Exception {
  HistoryControlStore.Inspection c=f.control();UUID prep=UUID.randomUUID();String h=hash(new byte[]{7});
  PrivateOperationRecord.Binding b=PrivateOperationRecord.Binding.portable(prep,c.registration.bundleId,c.registration.envelopeHash,c.latest.generation,c.latest.envelopeHash,
   c.registration.bootstrapQuotaBytes,c.latest.quotaBytes,c.latest.automaticCount,c.registration.roles,h,h,2);
  List<PrivateOperationRecord.Artifact> rows=new ArrayList<>();
  rows.add(PrivateOperationRecord.Artifact.portable(1,UUID.randomUUID(),ZERO,0,1,h));
  rows.add(PrivateOperationRecord.Artifact.portable(3,UUID.randomUUID(),ZERO,0,144,h));
  rows.add(PrivateOperationRecord.Artifact.portable(4,UUID.randomUUID(),ZERO,0,56,h));
  rows.add(PrivateOperationRecord.Artifact.portable(5,UUID.randomUUID(),ZERO,0,246,h));
  rows.add(PrivateOperationRecord.Artifact.portable(8,UUID.randomUUID(),UUID.randomUUID(),0,1,h));
  PrivateOperationRecord d=PrivateOperationRecord.descriptorPortable(b,UUID.randomUUID(),rows.get(0),rows.subList(1,rows.size()));
  rows.add(1,PrivateOperationRecord.Artifact.portable(2,UUID.randomUUID(),ZERO,0,d.encoded().length,d.envelopeHash));
  HistoryPinRecord begin=HistoryPinRecord.beginPortable(operation,c.registration,c.latest,
   new HistoryPinRecord.Manifest(HistoryPinRecord.Domain.PORTABLE_CAPTURE_V1,2,d.encoded().length,d.envelopeHash));
  return PrivateOperationRecord.preparePortable(b,3,rows,begin);
 }
 static Path control(BaselineCheck.F f){return f.parent.resolve(HistoryControlStore.ROOT_NAME);}
 static void install(BaselineCheck.F f,PrivateOperationRecord p,boolean linked,boolean closed)throws Exception {
  Files.write(control(f).resolve(p.name()),p.encoded());Files.write(control(f).resolve(p.begin.name()),HistoryPinRecord.encode(p.begin));
  if(linked){PrivateOperationRecord link=PrivateOperationRecord.linkPortable(p);Files.write(control(f).resolve(link.name()),link.encoded());}
  if(closed)Files.write(control(f).resolve("pin-"+p.operation+".close"),HistoryPinRecord.close(p.begin,HistoryPinRecord.Outcome.CANCELLED));
 }
 static void diskDispatch()throws Exception {
  try(BaselineCheck.F f=new BaselineCheck.F()) {
   PrivateOperationRecord p=portable(f);install(f,p,true,true);HistoryControlStore.Inspection c=f.control();
   check(c.operations.valid&&c.operations.ready&&c.pins.valid&&c.state==HistoryControlStore.State.VALIDATED,"closed portable exact typed controls accepted internally");
   check(c.operations.links.get(p.preparation).operation.version==2,"LINK retains v2");
   check(c.controlReservedBytes==0&&c.controlReservedEntries==0,"closed linked controls retain no invented credits");
   reject(()->PrivateOperationRecord.decode(p.encoded()),"generic MDOP v2");
   reject(()->HistoryPinRecord.decode(HistoryPinRecord.encode(p.begin)),"generic domain3 BEGIN");
   reject(()->PrivateOperationRecord.link(p),"generic LINK factory");
   reject(()->HistoryPinRecord.begin(p.operation,c.registration,c.latest,HistoryPinRecord.Purpose.EXPORT,HistoryPinRecord.Scope.ALL_BUNDLE,p.begin.manifest,Collections.emptyList()),"generic BEGIN factory");
   Path link=control(f).resolve("prep-"+p.preparation+".link");Files.write(link,version(PrivateOperationRecord.linkPortable(p).encoded(),1));
   check(!f.control().operations.valid,"v1 LINK cannot join portable P");
   Files.write(link,PrivateOperationRecord.linkPortable(p).encoded());byte[] close=HistoryPinRecord.close(p.begin,HistoryPinRecord.Outcome.CANCELLED);close[29]^=1;Files.write(control(f).resolve("pin-"+p.operation+".close"),checksum(close));
   check(!f.control().pins.valid,"CLOSE must bind exact prepared BEGIN");
   Files.write(control(f).resolve("pin-"+p.operation+".close"),HistoryPinRecord.close(p.begin,HistoryPinRecord.Outcome.CANCELLED));
   byte[] domain2=HistoryPinRecord.encode(p.begin);domain2[119]=2;Files.write(control(f).resolve(p.begin.name()),checksum(domain2));
   check(!f.control().pins.valid&&!f.control().operations.valid,"same-operation domain2 BEGIN cannot join portable P");
   Files.write(control(f).resolve(p.begin.name()),HistoryPinRecord.encode(p.begin));Files.write(control(f).resolve(p.name()),version(p.encoded(),3));
   check(!f.control().operations.valid,"unknown MDOP version refused internally");
  }
  try(BaselineCheck.F f=new BaselineCheck.F()) {
   PrivateOperationRecord p=portable(f);install(f,p,false,true);HistoryControlStore.Inspection c=f.control();
   check(c.operations.valid&&c.operations.ready&&c.operations.terminalCancelled.contains(p.preparation),"closed preLINK cancellation classified");
   check(c.operations.reservedBytes==173&&c.operations.reservedEntries==1,"unused exact v2 LINK credit retained");
   Object baseline=f.baseline();List<PrivateOperationEvidence.Row> rows=BaselineCheck.rows(baseline);
   String linkHash=PrivateOperationRecord.linkPortable(p).envelopeHash;
   check(rows.stream().anyMatch(r->r.role==1&&r.kind==2&&r.state==0&&r.ownerPreparation.equals(p.preparation)&&r.declaredHash.equals(linkHash)),"baseline missing LINK uses v2 commitment");
   Map<String,byte[]> copied=new HashMap<>();try(AutoCloseable cursor=(AutoCloseable)BaselineCheck.call(baseline,"open",new Class<?>[0])) {
    Object row;while((row=BaselineCheck.call(cursor,"next",new Class<?>[0]))!=null){PrivateOperationEvidence.Row r=(PrivateOperationEvidence.Row)row;
     try(InputStream in=(InputStream)BaselineCheck.call(cursor,"openRaw",new Class<?>[0])){copied.put(r.name,read(in));}}
   }
   check(Arrays.equals(copied.get(p.name()),p.encoded())&&Arrays.equals(copied.get(p.begin.name()),HistoryPinRecord.encode(p.begin)),"second baseline retains original portable raw controls");
  }
 }
 static byte[] read(InputStream input)throws IOException {ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] b=new byte[4096];int n;while((n=input.read(b))!=-1)out.write(b,0,n);return out.toByteArray();}
 static void blockedPrefixes()throws Exception {
  for(int prefix=0;prefix<4;prefix++)try(BaselineCheck.F f=new BaselineCheck.F()) {
   HistoryPins ordinary=HistoryPins.open(f.store,f.catalog,f.identity,f.index,f.disk);
   PrivateOperationRecord p=portable(f);install(f,p,prefix==1,false);
   if(prefix==2)Files.move(control(f).resolve(p.begin.name()),control(f).resolve(p.begin.name()+".stage"));
   if(prefix==3){Files.delete(control(f).resolve(p.begin.name()));Files.move(control(f).resolve(p.name()),control(f).resolve(p.name()+".stage"));}
   HistoryControlStore.Inspection c=f.control();check(c.operations.valid&&c.pins.valid,"portable prefix accounting remains typed "+prefix);
   check(!c.operations.ready&&c.state==HistoryControlStore.State.BLOCKED,"portable prefix has no ordinary readiness "+prefix);
   reject(()->HistoryPins.forRecovery(f.store,f.catalog,f.identity,f.index,f.disk),"generic Pins recovery "+prefix);
   reject(()->HistoryPins.open(f.store,f.catalog,f.identity,f.index,f.disk),"generic Pins reconcile "+prefix);
   final int[] reads={0};reject(()->ordinary.resume(p.operation.toString(),new InputStream(){public int read(){reads[0]++;return -1;}}),"existing ordinary handle cannot resume portable "+prefix);
   check(reads[0]==0,"generic resume refuses before original-manifest callback");
   f.locked(()->reject(()->PrivateOperationStore.coldCancellationLocked(f.store.storageRoot(),p.preparation,f.budget,true,
    Arrays.asList(f.store.storageRoot(),f.catalog.storageRoot(),f.identity.storageRoot(),f.index.storageRoot()),64L<<20),"v1 cancellation cannot acquire portable publication authority"));
  }
  try(BaselineCheck.F f=new BaselineCheck.F()) {
   PrivateOperationRecord p=portable(f);install(f,p,false,true);Files.delete(control(f).resolve(p.name()));
   check(!f.control().pins.valid&&!f.control().operations.valid,"unowned domain3 pair refused");
  }
 }
 static EvidenceCheck.Item item(PrivateOperationRecord p,int kind,byte[] raw,UUID owner){UUID node=kind<=2?owner:p.operation;
  String name=kind==1?p.name():kind==2?"prep-"+owner+".link":"pin-"+p.operation+(kind==3?".begin":".close");
  return new EvidenceCheck.Item(EvidenceCheck.row(1,owner,node,kind,2,name,raw.length,hash(raw),raw.length,hash(raw),0,0,0),raw);
 }
 static List<EvidenceCheck.Item> evidence(BaselineCheck.F f,PrivateOperationRecord p,boolean linked)throws Exception {
  List<EvidenceCheck.Item> rows=new ArrayList<>();rows.add(item(p,1,p.encoded(),p.preparation));
  if(linked)rows.add(item(p,2,PrivateOperationRecord.linkPortable(p).encoded(),p.preparation));
  rows.add(item(p,3,HistoryPinRecord.encode(p.begin),p.preparation));rows.add(item(p,4,HistoryPinRecord.close(p.begin,HistoryPinRecord.Outcome.CANCELLED),p.preparation));
  byte[] reg=Files.readAllBytes(f.parent.resolve(HistoryControlStore.ENROLLMENT_NAME));
  rows.add(EvidenceCheck.context(1,HistoryControlStore.ENROLLMENT_NAME,reg));rows.add(EvidenceCheck.context(2,HistoryControlStore.REGISTRATION_NAME,reg));
  String[] names={"baseline-c0","baseline-ledger","baseline-graph","baseline-native"};for(int i=0;i<4;i++)rows.add(EvidenceCheck.context(i+4,names[i],new byte[]{(byte)i}));
  rows.sort((a,b)->{int c=Integer.compare(a.row.role,b.row.role);if(c==0)c=PrivateBundleInventory.compare(a.row.ownerPreparation,b.row.ownerPreparation);return c!=0?c:a.row.name.compareTo(b.row.name);});return rows;
 }
 static void evidenceRoundtrip()throws Exception {
  try(BaselineCheck.F f=new BaselineCheck.F()) {
   PrivateOperationRecord p=portable(f);List<EvidenceCheck.Item> rows=evidence(f,p,true);byte[] wire=EvidenceCheck.fixture(rows);Map<String,byte[]> retained=new HashMap<>();
   try(PrivateOperationEvidence.Summary summary=PrivateOperationEvidence.read(new ByteArrayInputStream(wire),r->new ByteArrayOutputStream(){public void close(){retained.put(r.name,toByteArray());}},new PrivateBundleInventory.MetadataBudget())){check(summary.rowCount==rows.size(),"portable controls accepted as inert evidence");}
   check(Arrays.equals(retained.get(p.name()),p.encoded())&&Arrays.equals(retained.get("prep-"+p.preparation+".link"),PrivateOperationRecord.linkPortable(p).encoded()),"evidence sink retains v2 P/LINK byte exact");
   ByteArrayOutputStream out=new ByteArrayOutputStream();try(PrivateOperationEvidence.Summary ignored=PrivateOperationEvidence.write(EvidenceCheck.source(rows),out,new PrivateBundleInventory.MetadataBudget())){}
   check(Arrays.equals(wire,out.toByteArray()),"second-capture evidence preserves portable controls byte exact");
   List<EvidenceCheck.Item> bad=new ArrayList<>(rows);int link=index(bad,2);bad.set(link,item(p,2,version(PrivateOperationRecord.linkPortable(p).encoded(),1),p.preparation));rejectBoth(bad,"v1 LINK/v2 P mismatch");
   bad=new ArrayList<>(rows);int pin=index(bad,3);byte[] begin=HistoryPinRecord.encode(p.begin);begin[119]=2;bad.set(pin,item(p,3,checksum(begin),p.preparation));rejectBoth(bad,"domain2 BEGIN/v2 P mismatch");
   bad=new ArrayList<>(rows);int close=index(bad,4);byte[] raw=HistoryPinRecord.close(p.begin,HistoryPinRecord.Outcome.CANCELLED);raw[29]^=1;bad.set(close,item(p,4,checksum(raw),p.preparation));rejectBoth(bad,"owned CLOSE exact BEGIN binding");
   bad=new ArrayList<>(rows);bad.remove(index(bad,1));rejectBoth(bad,"committed owned controls missing P");
   bad=new ArrayList<>(rows);bad.remove(index(bad,3));rejectBoth(bad,"committed LINK/CLOSE missing BEGIN");
   bad=new ArrayList<>(rows);pin=index(bad,3);bad.set(pin,item(p,3,HistoryPinRecord.encode(p.begin),UUID.randomUUID()));sort(bad);rejectBoth(bad,"wrong owner annotation");
   bad=new ArrayList<>(rows);bad.set(index(bad,3),item(p,3,HistoryPinRecord.encode(p.begin),ZERO));bad.set(index(bad,4),item(p,4,HistoryPinRecord.close(p.begin,HistoryPinRecord.Outcome.CANCELLED),ZERO));sort(bad);rejectBoth(bad,"unowned portable pair");
   List<EvidenceCheck.Item> cancelled=evidence(f,p,false);EvidenceCheck.read(EvidenceCheck.fixture(cancelled));checks++;
   PrivateOperationRecord other=portable(f,p.operation);bad=new ArrayList<>(rows);bad.add(item(other,1,other.encoded(),other.preparation));sort(bad);rejectBoth(bad,"two P claim one operation");
   byte[] prefix=new byte[]{'M','D','O'};PrivateOperationEvidence.Row stage=EvidenceCheck.row(1,p.preparation,p.preparation,1,3,p.name()+".stage",p.encoded().length,p.envelopeHash,prefix.length,hash(prefix),p.encoded().length-prefix.length,0,0);
   List<EvidenceCheck.Item> inert=new ArrayList<>();inert.add(new EvidenceCheck.Item(stage,prefix));for(EvidenceCheck.Item i:rows)if(i.row.role==4)inert.add(i);
   byte[] unknown=EvidenceCheck.fixture(inert);Map<String,byte[]> stageRaw=new HashMap<>();try(PrivateOperationEvidence.Summary ignored=PrivateOperationEvidence.read(new ByteArrayInputStream(unknown),r->new ByteArrayOutputStream(){public void close(){stageRaw.put(r.name,toByteArray());}},new PrivateBundleInventory.MetadataBudget())){}
   check(Arrays.equals(stageRaw.get(stage.name),prefix),"partial P stays UNKNOWN byte-exact evidence without typed adoption");
  }
 }
 static int index(List<EvidenceCheck.Item> rows,int kind){for(int i=0;i<rows.size();i++)if(rows.get(i).row.role==1&&rows.get(i).row.kind==kind)return i;throw new AssertionError();}
 static void sort(List<EvidenceCheck.Item> rows){rows.sort((a,b)->{int c=Integer.compare(a.row.role,b.row.role);if(c==0)c=PrivateBundleInventory.compare(a.row.ownerPreparation,b.row.ownerPreparation);return c!=0?c:a.row.name.compareTo(b.row.name);});}
 static void rejectBoth(List<EvidenceCheck.Item> rows,String why)throws Exception {
  PrivateBundleInventory.MetadataBudget budget=new PrivateBundleInventory.MetadataBudget();
  reject(()->{try(PrivateOperationEvidence.Summary ignored=PrivateOperationEvidence.read(new ByteArrayInputStream(EvidenceCheck.fixture(rows)),null,budget)){}},why+" reader");
  reject(()->{try(PrivateOperationEvidence.Summary ignored=PrivateOperationEvidence.write(EvidenceCheck.source(rows),new ByteArrayOutputStream(),budget)){}},why+" writer");
  check(budget.usedBytes()==0,"failed dispatch releases evidence reservation");
 }
 public static void main(String[] args)throws Exception {
  BaselineCheck.capture=PrivateOperationStore.class.getDeclaredMethod("capturePortableBaselineLocked",File.class,HistoryControlStore.Inspection.class,PrivateOperationStore.Ledger.class,PreparationSources.class,UUID.class,HistoryStorage.Budget.class);BaselineCheck.capture.setAccessible(true);
  diskDispatch();blockedPrefixes();evidenceRoundtrip();System.out.println("PortableInternalDispatch PASS "+checks+" checks");
 }
}
