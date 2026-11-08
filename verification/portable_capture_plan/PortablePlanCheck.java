package com.example.markdowndesk;

import java.io.*;
import java.lang.reflect.*;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;

/** Real same-owner producer fixtures. Frame payloads are generated bounded streams, never spools. */
public final class PortablePlanCheck {
 static int checks;static long smallUsed,largeUsed,minimumUsed,fullSelectionUsed;
 interface Task{void run()throws Exception;}
 static void check(boolean yes,String why){checks++;if(!yes)throw new AssertionError(why);}
 static void reject(Task work,String why)throws Exception{try{work.run();throw new AssertionError("Accepted "+why);}catch(IOException expected){checks++;}}
 static OutputStream discard(){return new OutputStream(){public void write(int b){}public void write(byte[] b,int o,int n){}};}
 static byte[] bytes(InputStream in)throws IOException{try(InputStream own=in;ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[4096];int n;while((n=own.read(b))!=-1)out.write(b,0,n);return out.toByteArray();}}
 static long used(HistoryStorage.Budget b)throws Exception{Field f=HistoryStorage.Budget.class.getDeclaredField("remaining");f.setAccessible(true);return HistoryStorage.METADATA_BUDGET-f.getLong(b);}
 static final class Disk implements ProtectionStore.Durability {
  public void syncFile(FileDescriptor fd)throws IOException{fd.sync();}
  public void move(File a,File b)throws IOException{Files.move(a.toPath(),b.toPath(),StandardCopyOption.ATOMIC_MOVE);}
  public void syncDirectory(File dir)throws IOException{try(FileChannel c=FileChannel.open(dir.toPath(),StandardOpenOption.READ)){c.force(true);}}
 }
 static PrivateStateCodec.Tab tab(String id){return new PrivateStateCodec.Tab(id,"Draft "+id,new PrivateStateCodec.Provenance(null,null,-1,false),false,true,false,0,0,0,0,0,"","",false,false,false);}
 static Map<String,Object> prefs(){Map<String,Object> p=new LinkedHashMap<>();p.put("line_numbers",true);p.put("typewriter",false);p.put("launch_splash",true);p.put("ui_theme",8);p.put("word_goal",100);p.put("font_size",16f);p.put("preview_font_size",16f);p.put("split_ratio",0.5f);return p;}
 static InputStream repeated(int count){return new InputStream(){int left=count;public int read(){if(left==0)return -1;left--;return 'x';}public int read(byte[] b,int o,int n){if(n==0)return 0;if(left==0)return -1;n=Math.min(n,left);Arrays.fill(b,o,o+n,(byte)'x');left-=n;return n;}};}
 /** Independent payload framing: codec supplies bounded metadata only, payload/checksum streamed. */
 static InputStream tabFrame(PrivateStateCodec.Tab tab,int size)throws IOException {
  ByteArrayOutputStream out=new ByteArrayOutputStream();PrivateStateCodec.writeTab(tab,0,new ByteArrayInputStream(new byte[0]),out);
  byte[] prefix=Arrays.copyOf(out.toByteArray(),out.size()-32);for(int i=0;i<4;i++)prefix[17+i]=(byte)(size>>>(24-8*i));
  MessageDigest hash=HistoryStorage.digest();hash.update(prefix);byte[] b=new byte[65536];Arrays.fill(b,(byte)'x');for(int left=size;left>0;left-=Math.min(left,b.length))hash.update(b,0,Math.min(left,b.length));
  return new SequenceInputStream(Collections.enumeration(Arrays.asList(new ByteArrayInputStream(prefix),repeated(size),new ByteArrayInputStream(hash.digest()))));
 }
 static final class F implements AutoCloseable {
  final Path parent=Files.createTempDirectory("portable-plan-");final Disk disk=new Disk();
  final ProtectionStore store=new ProtectionStore(parent.resolve("store").toFile(),disk,64L<<20);
  final HistoryCatalog catalog=new HistoryCatalog(parent.resolve("catalog").toFile(),store,disk);
  final HistoryIdentity identity=new HistoryIdentity(parent.resolve("identity").toFile(),disk);
  final HistoryIndex index=new HistoryIndex(store,catalog,identity,parent.resolve("index").toFile(),disk);
  final HistoryCapacity capacity=HistoryCapacity.register(store,catalog,identity,index,new HistoryPolicy(64L<<20,20),disk);
  final HistoryGraph scanner=new HistoryGraph(store,catalog,identity,index,null);
  final HistoryStorage.Budget budget=new HistoryStorage.Budget(HistoryStorage.METADATA_BUDGET);
  final UUID prep=UUID.randomUUID(),op=UUID.randomUUID(),mapId=UUID.randomUUID(),descriptorId=UUID.randomUUID();
  final PreparationSources owner=PreparationSources.forPreparation(store.storageRoot(),prep,budget,PreparationSources.newProducer());
  PrivateCapturePlan.HashInput graphInput;HistoryGraph.Graph graph;HistoryControlStore.Inspection control;PrivateOperationStore.Ledger ledger;
  PortableSelection selection;List<PrivateFramePreflight.ClosedFrame> frames;PrivateCapturePlan.PortableInputs inputs;
  F()throws Exception{}
  void locked(Task work)throws Exception{synchronized(HistoryStorage.monitorFor(store.storageRoot())){try(HistoryStorage.MutationScope lease=HistoryStorage.administration(store.storageRoot())){work.run();}}}
  void scan()throws Exception {graphInput=PrivateCapturePlan.HashInput.create(budget,128<<10);locked(()->{graph=scanner.scan(budget,graphInput);control=graph.originalControl(budget,graphInput);ledger=graph.originalLedger(budget,graphInput);check(graph.complete,"real original Graph complete "+graph.diagnostics);});}
  void selection(int payload,int tabs)throws Exception {selection(payload,tabs,false);}
  void selection(int payload,int tabs,boolean withRecovery)throws Exception {
   PrivateStateCodec.Recovery recovery=withRecovery?new PrivateStateCodec.Recovery("Recovery",new PrivateStateCodec.Provenance(null,null,-1,false),false,0,0,1):null;
   List<PrivateStateCodec.Tab> selected=new ArrayList<>();for(int i=0;i<tabs;i++)selected.add(tab(i==0?"A":i==1?"B":"T"+i));selection=PortableSelection.freeze(owner,budget,selected,tabs==0?null:selected.get(tabs-1).sourceTabId,recovery,prefs());
   PrivateFramePreflight.Workspace ws=PrivateFramePreflight.create(owner,budget);frames=new ArrayList<>();for(int i=0;i<tabs;i++){final PrivateStateCodec.Tab t=selected.get(i);frames.add(ws.attempt(PrivateStateCodec.Kind.SESSION_TAB,i,UUID.randomUUID(),UUID.randomUUID(),()->tabFrame(t,payload)).run());}
   if(recovery!=null){ByteArrayOutputStream recovered=new ByteArrayOutputStream();PrivateStateCodec.writeRecovery(recovery,0,new ByteArrayInputStream(new byte[0]),recovered);byte[] recoveredFrame=recovered.toByteArray();frames.add(ws.attempt(PrivateStateCodec.Kind.RECOVERY,0,UUID.randomUUID(),UUID.randomUUID(),()->new ByteArrayInputStream(recoveredFrame)).run());}
   ByteArrayOutputStream out=new ByteArrayOutputStream();PrivateStateCodec.writePreferences(prefs(),out);byte[] frame=out.toByteArray();frames.add(ws.attempt(PrivateStateCodec.Kind.PREFERENCES,0,UUID.randomUUID(),UUID.randomUUID(),()->new ByteArrayInputStream(frame)).run());
  }
  PrivateCapturePlan.PortableInputs capture(HistoryGraph.Graph g,PrivateCapturePlan.HashInput witness,UUID operation,UUID map)throws IOException{return PrivateCapturePlan.PortableInputs.captureLocked(store.storageRoot(),catalog.storageRoot(),identity.storageRoot(),index.storageRoot(),g,witness,owner,prep,operation,map,descriptorId,frames,64L<<20,selection,budget);}
  void capture()throws Exception{locked(()->inputs=capture(graph,graphInput,op,mapId));}
  PrivateCapturePlan plan()throws IOException{return PrivateCapturePlan.createPortable(store.storageRoot(),catalog.storageRoot(),identity.storageRoot(),index.storageRoot(),inputs.nativeMap,graph,control,ledger,owner,prep,op,mapId,descriptorId,frames,64L<<20,selection,inputs,budget);}
  void absentP()throws Exception {check(!Files.exists(parent.resolve("history_control/prep-"+prep+".reserve")),"no PREPARE publication");try(java.util.stream.Stream<Path> paths=Files.list(parent.resolve("history_control"))){check(paths.noneMatch(p->p.getFileName().toString().equals("prep-"+prep+".reserve")),"no side effect in control");}}
  public void close()throws IOException{if(inputs!=null)inputs.close();if(graphInput!=null)graphInput.close();try(java.util.stream.Stream<Path> paths=Files.walk(parent)){for(Path p:(Iterable<Path>)paths.sorted(Comparator.reverseOrder())::iterator)Files.delete(p);}}
 }
 static void joins(F f,PrivateCapturePlan p)throws Exception {
  check(p.prepare.binding.phase==3&&p.prepare.begin.manifest.domain==HistoryPinRecord.Domain.PORTABLE_CAPTURE_V1,"real phase3/domain3");
  check(p.portableInputs==f.inputs&&p.nativeMap==f.inputs.nativeMap&&p.sourceOwner==f.owner,"exact retained originals");
  for(PrivateOperationRecord r:Arrays.asList(p.prepare,p.descriptor,p.link))check(Arrays.equals(r.encoded(),PrivateOperationRecord.decodePortable(r.encoded()).encoded()),"explicit portable record roundtrip");
  reject(()->PrivateOperationRecord.decode(p.prepare.encoded()),"generic portable activation blocked");
  check(p.prepare.artifact(4).length>0&&p.prepare.artifact(5).length==52+97L*p.prepare.begin.manifest.count,"real capsule/catalog artifact dimensions");
  check(p.prepare.begin.manifest.hash.equals(p.descriptor.envelopeHash)&&p.prepare.artifact(2).hash.equals(p.descriptor.envelopeHash),"Dp BEGIN P exact acyclic hash");
  long total=0;for(PrivateOperationRecord.Artifact a:p.prepare.artifacts)total+=a.length;check(total==p.artifactBytes,"exact artifact funding sum");
  byte[] inventory=bytes(f.inputs.openInventory());check(inventory.length==p.prepare.artifact(3).length&&PrivateOperationRecord.sha(inventory).equals(p.prepare.artifact(3).hash),"I exact artifact hash/length");
  PrivateBundleInventory i=f.inputs.inventory();check(i.schema==2&&i.entries().size()==p.prepare.begin.manifest.count,"I schema/count");
  if(f.selection.tabs.size()==2){PrivateBundleInventory.Scope scope=i.scopes().get(0);UUID active=scope.activeEntryId;PrivateBundleInventory.DirectoryEntry b=i.entries().stream().filter(e->e.id.equals(active)).findFirst().get();check(b.identity.equals(f.frames.get(1).itemId)&&scope.tabs.get(1).equals(active),"A/B active B maps to B item and ordered outer");}
  try(PrivateSourceCatalog catalog=PrivateSourceCatalog.read(f.inputs.openCatalog(),new PrivateBundleInventory.MetadataBudget(16L<<20))){check(catalog.rows.size()==i.entries().size(),"catalog complete ordinary membership");for(int n=0;n<catalog.rows.size();n++){PrivateSourceCatalog.Row r=catalog.rows.get(n);PrivateBundleInventory.DirectoryEntry e=i.entries().get(n);check(r.outer.equals(e.id)&&r.length==e.length&&r.hash.equals(e.sha256),"catalog entry exact join");if(r.route==2)check(f.frames.stream().anyMatch(frame->frame.artifactId.equals(r.member)&&frame.itemId.equals(e.identity)),"private source actual frame coordinate");if(r.route==1)check(p.nativeMap.entries.stream().anyMatch(nativeRow->nativeRow.archiveId.equals(r.outer)&&nativeRow.nativeId.equals(r.member)),"native archive UUID reused");}}
  f.absentP();
 }
 static void smallAndLarge()throws Exception {
  for(int payload:new int[]{0,PrivateStateCodec.MAX_PAYLOAD_BYTES})try(F f=new F()){
   if(payload==0)f.store.putSnapshot("tab","uri","original",1,new byte[]{1},"original");
   f.scan();f.selection(payload,2);f.capture();f.inputs.measure();final PrivateCapturePlan[] result={null};f.locked(()->result[0]=f.plan());joins(f,result[0]);
   long used=used(f.budget);check(used<HistoryStorage.METADATA_BUDGET,"same original16MiB budget succeeds");if(payload==0)smallUsed=used;else largeUsed=used;
   check(!Files.exists(f.parent.resolve("private_operations")),"assembly creates no operation tree");
   if(payload==0){ByteArrayOutputStream capsule=new ByteArrayOutputStream();try(PrivateOperationEvidence.Summary s=PrivateOperationEvidence.write(f.inputs.capsuleSource(),capsule,f.inputs.metadata)){check(s.hash.equals(result[0].prepare.artifact(4).hash),"second real cursor exact capsule");}
    Map<String,byte[]> raws=new HashMap<>();try(PrivateOperationEvidence.Summary s=PrivateOperationEvidence.read(new ByteArrayInputStream(capsule.toByteArray()),row->new ByteArrayOutputStream(){public void close(){raws.put(row.name,toByteArray());}},new PrivateBundleInventory.MetadataBudget(16L<<20))){check(s.rowCount==6,"zero-old-operation mandatory raw context plus four witnesses");}
    check(PrivateOperationRecord.sha(raws.get("baseline-c0")).equals(result[0].initialControlHash),"actual C0 preimage");check(PrivateOperationRecord.sha(raws.get("baseline-graph")).equals(f.graph.fingerprint),"actual original nullable-profile Graph preimage");check(PrivateOperationRecord.sha(raws.get("baseline-native")).equals(result[0].nativeSources.fingerprint),"actual native preimage");check(raws.get("baseline-ledger").length==0,"absent L0 is empty, no invented root credit");
   }
  }
 }
 static void minimum()throws Exception{try(F f=new F()){f.scan();f.selection(0,0);f.capture();f.inputs.measure();f.locked(()->joins(f,f.plan()));minimumUsed=used(f.budget);}}
 static void provenanceAndSelection()throws Exception{
  try(F f=new F()){f.scan();f.selection(0,2);f.locked(()->{HistoryGraph.Graph publicGraph=f.scanner.scan();check(publicGraph.fingerprint.equals(f.graph.fingerprint),"equal public graph hash fixture");reject(()->f.capture(publicGraph,f.graphInput,f.op,f.mapId),"public graph lacks funded original provenance");PrivateCapturePlan.HashInput foreign=PrivateCapturePlan.HashInput.create(f.budget,128<<10);reject(()->f.capture(f.graph,foreign,f.op,f.mapId),"foreign original witness identity");foreign.close();reject(()->f.capture(f.graph,f.graphInput,f.mapId,f.mapId),"generated ID collision");});
   List<PrivateFramePreflight.ClosedFrame> original=f.frames;f.frames=new ArrayList<>(original);Collections.swap(f.frames,0,1);f.locked(()->reject(()->f.capture(f.graph,f.graphInput,f.op,f.mapId),"swapped selected source order"));f.frames=original;
   try(F other=new F()){PortableSelection selected=f.selection;f.selection=PortableSelection.freeze(other.owner,other.budget,Arrays.asList(tab("A"),tab("B")),"B",null,prefs());f.locked(()->reject(()->f.capture(f.graph,f.graphInput,f.op,f.mapId),"foreign frozen selection owner"));f.selection=selected;}
   f.capture();f.locked(()->reject(()->f.inputs.measure(),"measurement under history monitor"));
   try(PrivateOperationEvidence.Cursor cursor=f.inputs.capsuleSource().open()){PrivateOperationEvidence.Row row=cursor.next();reject(()->cursor.next(),"unopened row cannot advance");InputStream raw=cursor.openRaw();reject(()->cursor.next(),"open row cannot advance");reject(()->raw.close(),"partial row close retains actual handle");byte[] buffer=new byte[4096];while(raw.read(buffer)!=-1){}raw.close();reject(()->cursor.openRaw(),"same row cannot reopen");while((row=cursor.next())!=null){try(InputStream in=cursor.openRaw()){while(in.read(buffer)!=-1){}}}}
   f.inputs.measure();HistoryControlStore.Inspection originalControl=f.control;f.budget.charge(1L<<20);f.control=new HistoryControlStore(f.parent.toFile()).inspect();f.locked(()->reject(()->f.plan(),"equal-value foreign control snapshot identity"));f.control=originalControl;

  }
 }
 static void selectionAndBudget()throws Exception {
  try(F f=new F()){reject(()->PortableSelection.freeze(f.owner,f.budget,Arrays.asList(tab("A"),tab("A")),"A",null,prefs()),"duplicate selected source");reject(()->PortableSelection.freeze(f.owner,f.budget,Arrays.asList(tab("A")),"B",null,prefs()),"active outside selected source");}
  try(F f=new F()){f.scan();f.selection(0,0);long remain=HistoryStorage.METADATA_BUDGET-used(f.budget);f.budget.charge(remain-(64<<10));f.locked(()->reject(()->f.capture(f.graph,f.graphInput,f.op,f.mapId),"original metadata exhaustion before P"));f.absentP();}
 }
 static PrivateOperationRecord cancelled(F f,boolean rootPresent)throws Exception {
  HistoryControlStore.Inspection c=new HistoryControlStore(f.parent.toFile()).inspect();UUID prep=UUID.randomUUID();String nil=String.join("",Collections.nCopies(64,"0"));
  PrivateOperationRecord.Binding b=new PrivateOperationRecord.Binding(prep,1,c.registration.bundleId,c.registration.envelopeHash,c.latest.generation,c.latest.envelopeHash,64L<<20,64L<<20,20,c.registration.roles,nil,nil,0);
  PrivateOperationRecord.Artifact map=new PrivateOperationRecord.Artifact(1,UUID.randomUUID(),PrivateOperationRecord.ZERO,0,512,nil);PrivateOperationRecord d=PrivateOperationRecord.descriptor(b,map,Collections.emptyList());PrivateOperationRecord.Artifact dr=new PrivateOperationRecord.Artifact(2,UUID.randomUUID(),PrivateOperationRecord.ZERO,0,d.encoded().length,d.envelopeHash);
  HistoryPinRecord pin=HistoryPinRecord.begin(UUID.randomUUID(),c.registration,c.latest,HistoryPinRecord.Purpose.EXPORT,HistoryPinRecord.Scope.ALL_BUNDLE,new HistoryPinRecord.Manifest(HistoryPinRecord.Domain.LOCAL_CAPTURE_V1,0,dr.length,dr.hash),Collections.emptyList());PrivateOperationRecord p=PrivateOperationRecord.prepare(b,3,Arrays.asList(map,dr),pin);
  Path control=f.parent.resolve("history_control");Files.write(control.resolve(p.name()),p.encoded());Files.write(control.resolve(pin.name()),HistoryPinRecord.encode(pin));Files.write(control.resolve("pin-"+pin.operation+".close"),HistoryPinRecord.close(pin,HistoryPinRecord.Outcome.CANCELLED));
  if(rootPresent){Path dir=Files.createDirectories(f.parent.resolve("private_operations/"+prep));Files.write(dir.resolve(map.filename()+".stage"),new byte[]{7,8,9});f.capacity.reconcile();HistoryPins.open(f.store,f.catalog,f.identity,f.index,f.disk);}return p;
 }
 static void oldSourcesAndFunding()throws Exception {
  try(F f=new F()){PrivateOperationRecord old=cancelled(f,true);f.scan();f.selection(0,0);f.capture();PrivateOperationEvidence.Row stage=f.inputs.baseline.rows().stream().filter(r->r.role==2&&r.kind==1).findFirst().get();check(stage.state==3&&stage.relation==0&&stage.actualLength==3&&stage.futureBytes==509,"cancelled UNKNOWN prefix and exact retained credit");check(f.inputs.baseline.rows().stream().anyMatch(r->r.role==2&&r.kind==2&&r.state==0&&r.futureSlots==1),"missing declared descriptor retains real reservation");f.inputs.measure();final PrivateCapturePlan[] p={null};f.locked(()->p[0]=f.plan());joins(f,p[0]);
   ByteArrayOutputStream capsule=new ByteArrayOutputStream();try(PrivateOperationEvidence.Summary sum=PrivateOperationEvidence.write(f.inputs.capsuleSource(),capsule,f.inputs.metadata)){check(sum.hash.equals(p[0].prepare.artifact(4).hash),"old-control capsule stable");}final byte[][] prefix={null};try(PrivateOperationEvidence.Summary sum=PrivateOperationEvidence.read(new ByteArrayInputStream(capsule.toByteArray()),row->new ByteArrayOutputStream(){public void close(){if(row.name.equals(stage.name)&&row.ownerPreparation.equals(old.preparation))prefix[0]=toByteArray();}},new PrivateBundleInventory.MetadataBudget(16L<<20))){check(Arrays.equals(prefix[0],new byte[]{7,8,9}),"actual failed bytes copied exactly");}
   Path path=f.parent.resolve("private_operations/"+old.preparation+"/"+old.artifact(1).filename()+".stage");Files.write(path,new byte[]{7,8,10});f.locked(()->reject(()->f.plan(),"changed original partial stage after measurement"));Files.write(path,new byte[]{7,8,9});Path retained=f.parent.resolve("test-original-stage");Files.move(path,retained);Files.write(path,new byte[]{7,8,9});f.locked(()->reject(()->f.plan(),"byte-identical physical stage replacement"));Files.delete(path);Files.move(retained,path);
  }
  try(F f=new F()){cancelled(f,false);f.graphInput=PrivateCapturePlan.HashInput.create(f.budget,128<<10);f.locked(()->{f.graph=f.scanner.scan(f.budget,f.graphInput);f.control=f.graph.originalControl(f.budget,f.graphInput);f.ledger=f.graph.originalLedger(f.budget,f.graphInput);});f.selection(0,0);f.locked(()->reject(()->f.capture(f.graph,f.graphInput,f.op,f.mapId),"funded absent root remains source-only, no B1 admission"));f.absentP();}
  try(F f=new F()){f.scan();f.selection(PrivateStateCodec.MAX_PAYLOAD_BYTES,9);f.capture();f.inputs.measure();f.locked(()->reject(()->f.plan(),"actual72MiB private frames exceed64MiB primary quota before P"));f.absentP();}
  try(F f=new F()){HistoryControlStore.Inspection c=new HistoryControlStore(f.parent.toFile()).inspect();String previous=c.latest.envelopeHash;for(int n=1;n<=251;n++){byte[] policy=HistoryControlStore.encodePolicy(c.registration.bundleId,n,previous,64L<<20,20);Files.write(f.parent.resolve("history_control/"+HistoryControlStore.policyFilename(n)),policy);previous=PrivateOperationRecord.sha(policy);}f.capacity.reconcile();f.scan();f.selection(0,0);f.capture();f.inputs.measure();f.locked(()->reject(()->f.plan(),"actual valid control253 plus four new slots exceeds funding"));f.absentP();}
 }
 static void localGolden()throws Exception {
  try(F f=new F()){f.scan();f.locked(()->{HistoryArchiveManifest map=HistoryArchiveManifest.freeze(f.graph,f.control,UUID::randomUUID);PrivateCapturePlan p=PrivateCapturePlan.create(f.store.storageRoot(),f.catalog.storageRoot(),f.identity.storageRoot(),f.index.storageRoot(),map,f.graph,f.control,f.ledger,f.owner,f.prep,f.op,f.mapId,f.descriptorId,Collections.emptyList(),64L<<20,f.budget);
   check(p.portableInputs==null&&p.prepare.version==1&&p.prepare.binding.phase==1&&p.prepare.begin.manifest.domain==HistoryPinRecord.Domain.LOCAL_CAPTURE_V1,"existing local create remains v1/domain2");
   ByteArrayOutputStream body=new ByteArrayOutputStream();DataOutputStream out=new DataOutputStream(body);PrivateOperationRecord.Binding b=p.descriptor.binding;uuid(out,b.preparation);out.writeByte(1);uuid(out,b.bundle);out.write(hex(b.registrationHash));out.writeLong(b.generation);out.write(hex(b.policyHash));out.writeLong(b.bootstrapQuota);out.writeLong(b.quota);out.writeInt(b.automaticCount);for(HistoryControlStore.Role r:b.roles){byte[] name=r.basename.getBytes(StandardCharsets.UTF_8);out.writeByte(r.id);out.writeInt(name.length);out.write(name);}out.write(hex(b.sourceHash));out.write(hex(b.ledgerHash));out.writeByte(0);PrivateOperationRecord.Artifact a=p.descriptor.artifact(1);out.writeByte(1);uuid(out,a.id);uuid(out,PrivateOperationRecord.ZERO);out.writeInt(0);out.writeLong(a.length);out.write(hex(a.hash));out.writeInt(0);
   ByteArrayOutputStream golden=new ByteArrayOutputStream();DataOutputStream env=new DataOutputStream(golden);env.writeInt(0x4d444f50);env.writeInt(1);env.writeByte(3);env.writeInt(body.size());env.write(body.toByteArray());env.write(HistoryStorage.digest().digest(golden.toByteArray()));check(Arrays.equals(golden.toByteArray(),p.descriptor.encoded()),"independent original local-v1 descriptor golden");check(p.initialLedgerHash.equals("d85d1350026fd0c27784a3126cd3adff553265dc45b173b353b13c049645825e"),"original absent ledger golden");});f.absentP();}
 }
 static PrivateFramePreflight.ClosedFrame actualFrame(F f,PrivateStateCodec.Kind kind,int ordinal,PrivateStateCodec.Tab tab,PrivateStateCodec.Recovery recovery,Map<String,Object> prefs)throws Exception {
  boolean bom=kind==PrivateStateCodec.Kind.SESSION_TAB?tab.utf8Bom:kind==PrivateStateCodec.Kind.RECOVERY&&recovery.utf8Bom;
  byte[] payload=bom?new byte[]{(byte)239,(byte)187,(byte)191,'x','x','x','x'}:new byte[]{'x','x','x','x'};ByteArrayOutputStream out=new ByteArrayOutputStream();
  if(kind==PrivateStateCodec.Kind.SESSION_TAB)PrivateStateCodec.writeTab(tab,payload.length,new ByteArrayInputStream(payload),out);
  else if(kind==PrivateStateCodec.Kind.RECOVERY)PrivateStateCodec.writeRecovery(recovery,payload.length,new ByteArrayInputStream(payload),out);
  else PrivateStateCodec.writePreferences(prefs,out);
  byte[] raw=out.toByteArray();PrivateFramePreflight.Workspace ws=PrivateFramePreflight.create(f.owner,f.budget);
  return ws.attempt(kind,ordinal,UUID.randomUUID(),UUID.randomUUID(),()->new ByteArrayInputStream(raw)).run();
 }
 static void scalarBindings()throws Exception {
  // One original owner; each actual replacement is independently preflighted, never a DTO claim.
  try(F f=new F()){f.scan();f.selection(0,1,true);List<PrivateFramePreflight.ClosedFrame> original=f.frames;
   for(String fault:new String[]{"empty","missing","changed"}){Map<String,Object> observed=new LinkedHashMap<>(prefs());if(fault.equals("empty"))observed.clear();if(fault.equals("missing"))observed.remove("word_goal");if(fault.equals("changed"))observed.put("word_goal",101);f.frames=new ArrayList<>(original);f.frames.set(2,actualFrame(f,PrivateStateCodec.Kind.PREFERENCES,0,null,null,observed));f.locked(()->reject(()->f.capture(f.graph,f.graphInput,f.op,f.mapId),"actual preference scalar mismatch "+fault));}
   f.frames=original;
  }
  // Each workspace is512KiB, so use a fresh small fixture per independent scalar
  // mutation instead of retaining unsupported extra preflight workspaces in one owner.
  for(int field=0;field<19;field++)try(F f=new F()){f.scan();f.selection(0,1);PrivateStateCodec.Tab a=f.selection.tabs.get(0);int at=field;
   PrivateStateCodec.Provenance p=new PrivateStateCodec.Provenance(at==2?"content://changed":a.provenance.sourceUri,at==3?String.join("",Collections.nCopies(64,"a")):a.provenance.sourceBaselineHash,at==4?1:a.provenance.sourceBaselineModified,at==5||a.provenance.sourceReadOnly);
   PrivateStateCodec.Tab changed=new PrivateStateCodec.Tab(at==0?"foreign":a.sourceTabId,at==1?"changed":a.displayName,p,at==6,at==7?!a.sourceDirty:a.sourceDirty,at==8?!a.editorLocked:a.editorLocked,at==9?1:0,at==10?1:0,at==11?1:0,at==12?1:0,at==13?1:0,at==14?"find":"",at==15?"replace":"",at==16,at==17,at==18);
   f.frames.set(0,actualFrame(f,PrivateStateCodec.Kind.SESSION_TAB,0,changed,null,null));f.locked(()->reject(()->f.capture(f.graph,f.graphInput,f.op,f.mapId),"actual tab scalar mismatch field"+at));
  }
  for(int field=0;field<9;field++)try(F f=new F()){f.scan();f.selection(0,0,true);PrivateStateCodec.Recovery a=f.selection.recovery;int at=field;
   PrivateStateCodec.Provenance p=new PrivateStateCodec.Provenance(at==1?"content://changed":null,at==2?String.join("",Collections.nCopies(64,"a")):null,at==3?1:-1,at==4);
   PrivateStateCodec.Recovery changed=new PrivateStateCodec.Recovery(at==0?"changed":a.displayName,p,at==5,at==6?1:0,at==7?1:0,at==8?2:1);
   f.frames.set(0,actualFrame(f,PrivateStateCodec.Kind.RECOVERY,0,null,changed,null));f.locked(()->reject(()->f.capture(f.graph,f.graphInput,f.op,f.mapId),"actual recovery scalar mismatch field"+at));
  }
  try(F f=new F()){f.scan();f.selection(0,12,true);f.capture();f.inputs.measure();f.locked(()->{PrivateCapturePlan p=f.plan();check(p.privateFrames.size()==14&&p.prepare.artifacts.size()==19,"all12 tabs+recovery+eight preferences funded under original budget");});fullSelectionUsed=used(f.budget);}
 }
 static void witnessCleanup()throws Exception {
  try(F f=new F()){f.scan();f.selection(0,0);f.capture();PrivateOperationEvidence.Cursor cursor=f.inputs.capsuleSource().open();PrivateOperationEvidence.Row row;byte[] scratch=new byte[4096];boolean found=false;
   while((row=cursor.next())!=null){InputStream raw=cursor.openRaw();if(row.name.equals("baseline-graph")){found=true;f.graphInput.close();reject(()->raw.read(),"invalidated borrowed graph witness read");f.inputs.retrySourceClose();f.inputs.retrySourceClose();reject(()->cursor.next(),"abandoned cursor never advances/completes row");f.owner.freeze();f.locked(()->check(f.owner.claimLocked()!=null,"whole failed-witness cleanup actually releases Sources owner"));break;}while(raw.read(scratch)!=-1){}raw.close();}check(found,"actual graph witness reached");
  }
  try(F f=new F()){f.scan();f.selection(0,0);f.capture();PrivateOperationEvidence.Cursor cursor=f.inputs.capsuleSource().open();PrivateOperationEvidence.Row selected;InputStream raw=null;byte[] scratch=new byte[4096];while((selected=cursor.next())!=null){raw=cursor.openRaw();if(selected.kind==1&&selected.name.equals(HistoryControlStore.ENROLLMENT_NAME))break;while(raw.read(scratch)!=-1){}raw.close();}check(selected!=null&&raw.read()!=-1,"actual partial physical row opened");Path retained=Files.createDirectory(f.parent.resolve(PrivateOperationStore.RETAINED_ROOT));reject(()->cursor.close(),"actual physical closure validation uncertainty retained");reject(()->cursor.next(),"abandonment failure blocks further iteration");f.owner.freeze();f.locked(()->reject(()->f.owner.claimLocked(),"uncertain actual physical cursor still owned"));Files.delete(retained);f.inputs.retrySourceClose();f.locked(()->check(f.owner.claimLocked()!=null,"retry closes retained actual physical reader"));}
 }
 static void stateWireGoldens()throws Exception {
  byte[] payload="x".getBytes(StandardCharsets.UTF_8);ByteArrayOutputStream metadata=new ByteArrayOutputStream();DataOutputStream out=new DataOutputStream(metadata);text(out,"Draft A");out.writeInt(-1);out.writeInt(-1);out.writeLong(-1);out.writeBoolean(false);out.writeBoolean(false);out.writeInt(0);out.writeInt(0);text(out,"A");out.writeBoolean(true);out.writeBoolean(false);out.writeInt(0);out.writeInt(0);out.writeInt(0);text(out,"");text(out,"");out.writeBoolean(false);out.writeBoolean(false);out.writeBoolean(false);
  ByteArrayOutputStream actual=new ByteArrayOutputStream();PrivateStateCodec.writeTab(tab("A"),1,new ByteArrayInputStream(payload),actual);check(Arrays.equals(stateWire(6,metadata.toByteArray(),payload),actual.toByteArray()),"independent unchanged v1 tab canonical writer");
  metadata.reset();out=new DataOutputStream(metadata);text(out,"Recovery");out.writeInt(-1);out.writeInt(-1);out.writeLong(-1);out.writeBoolean(false);out.writeBoolean(false);out.writeInt(0);out.writeInt(0);out.writeLong(1);actual.reset();PrivateStateCodec.writeRecovery(new PrivateStateCodec.Recovery("Recovery",new PrivateStateCodec.Provenance(null,null,-1,false),false,0,0,1),1,new ByteArrayInputStream(payload),actual);check(Arrays.equals(stateWire(7,metadata.toByteArray(),payload),actual.toByteArray()),"independent unchanged v1 recovery canonical writer");
  metadata.reset();out=new DataOutputStream(metadata);out.writeInt(8);String[] keys={"font_size","launch_splash","line_numbers","preview_font_size","split_ratio","typewriter","ui_theme","word_goal"};int[] types={3,1,1,3,3,1,2,2};int[] values={Float.floatToIntBits(16f),1,1,Float.floatToIntBits(16f),Float.floatToIntBits(.5f),0,8,100};for(int i=0;i<8;i++){text(out,keys[i]);out.writeByte(types[i]);if(types[i]==1)out.writeByte(values[i]);else out.writeInt(values[i]);}actual.reset();PrivateStateCodec.writePreferences(prefs(),actual);check(Arrays.equals(stateWire(8,metadata.toByteArray(),new byte[0]),actual.toByteArray()),"independent unchanged v1 preference canonical writer");
 }
 static void text(DataOutputStream out,String value)throws IOException{byte[] b=value.getBytes(StandardCharsets.UTF_8);out.writeInt(b.length);out.write(b);}
 static byte[] stateWire(int kind,byte[] metadata,byte[] payload)throws IOException{ByteArrayOutputStream bytes=new ByteArrayOutputStream();DataOutputStream out=new DataOutputStream(bytes);out.write("MDSTATE\n".getBytes(StandardCharsets.US_ASCII));out.writeInt(1);out.writeByte(kind);out.writeInt(metadata.length);out.writeInt(payload.length);out.write(metadata);out.write(payload);out.write(HistoryStorage.digest().digest(bytes.toByteArray()));return bytes.toByteArray();}
 static void catalog()throws Exception {
  UUID outer=UUID.randomUUID(),owner=UUID.randomUUID(),member=UUID.randomUUID();String hash=PrivateOperationRecord.sha(new byte[]{7});PrivateSourceCatalog.Row row=new PrivateSourceCatalog.Row(outer,2,owner,member,0,17,hash);
  ByteArrayOutputStream encoded=new ByteArrayOutputStream();try(PrivateSourceCatalog c=PrivateSourceCatalog.create(Collections.singletonList(row),new PrivateBundleInventory.MetadataBudget(16L<<20))){c.write(encoded);}
  ByteArrayOutputStream golden=new ByteArrayOutputStream();DataOutputStream out=new DataOutputStream(golden);out.write("MDSRC01\n".getBytes(StandardCharsets.US_ASCII));out.writeInt(1);out.writeInt(149);out.writeInt(1);uuid(out,outer);out.writeByte(2);uuid(out,owner);uuid(out,member);out.writeLong(0);out.writeLong(17);out.write(hex(hash));golden.write(HistoryStorage.digest().digest(golden.toByteArray()));check(Arrays.equals(encoded.toByteArray(),golden.toByteArray()),"independent97-byte-row catalog golden");
  byte[] bytes=encoded.toByteArray();try(PrivateSourceCatalog c=PrivateSourceCatalog.read(new FilterInputStream(new ByteArrayInputStream(bytes)){boolean zero;public int read(byte[] b,int o,int n)throws IOException{zero=!zero;return zero?0:super.read(b,o,n);}},new PrivateBundleInventory.MetadataBudget(16L<<20))){check(c.rows.size()==1,"catalog zero-progress bounded fallback");}
  for(int cut:new int[]{0,19,100,148})reject(()->PrivateSourceCatalog.read(new ByteArrayInputStream(Arrays.copyOf(bytes,cut)),new PrivateBundleInventory.MetadataBudget(16L<<20)),"catalog truncation "+cut);
  byte[] bad=bytes.clone();bad[40]^=1;reject(()->PrivateSourceCatalog.read(new ByteArrayInputStream(bad),new PrivateBundleInventory.MetadataBudget(16L<<20)),"catalog checksum");reject(()->PrivateSourceCatalog.create(Arrays.asList(row,row),new PrivateBundleInventory.MetadataBudget(16L<<20)),"duplicate outer");reject(()->new PrivateSourceCatalog.Row(outer,2,owner,member,1,17,hash),"own artifact offset");
 }
 static void uuid(DataOutputStream out,UUID id)throws IOException{out.writeLong(id.getMostSignificantBits());out.writeLong(id.getLeastSignificantBits());}
 static byte[] hex(String h){byte[] b=new byte[32];for(int i=0;i<32;i++)b[i]=(byte)Integer.parseInt(h.substring(2*i,2*i+2),16);return b;}
 public static void main(String[] args)throws Exception{smallAndLarge();minimum();provenanceAndSelection();selectionAndBudget();oldSourcesAndFunding();localGolden();catalog();scalarBindings();witnessCleanup();stateWireGoldens();System.out.println("Portable capture plan: "+checks+" checks PASS; original parent bytes minimum="+minimumUsed+", small="+smallUsed+", two8MiBtabs="+largeUsed+", all14frames="+fullSelectionUsed);}
}
