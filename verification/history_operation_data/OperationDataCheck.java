package com.example.markdowndesk;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import static com.example.markdowndesk.HistoryPinsCheck.*;

/** Independent wire fixtures exercise actual shared authority, never a fake ledger. */
public final class OperationDataCheck {
    static final class Wire {
        final UUID prep,map,descriptor,operation;
        final byte[] c,d,b,p,l,nativeMap; final HistoryControlStore.Inspection authority;
        Wire(Fixture f)throws Exception {this(f,false);}
        Wire(Fixture f,boolean golden)throws Exception {this(f,golden,null);}
        Wire(Fixture f,boolean golden,byte[] mapBytes)throws Exception {
            nativeMap=mapBytes==null?new byte[212]:mapBytes;long nativeCount=mapBytes==null?0:HistoryArchiveManifest.read(new ByteArrayInputStream(mapBytes)).entries.size();
            prep=id(1,golden);map=id(3,golden);descriptor=id(4,golden);operation=id(5,golden);
            authority=inspect(f);UUID bundle=golden?id(2,true):authority.registration.bundleId;
            String registration=golden?repeat(0x33):authority.registration.envelopeHash,policy=golden?repeat(0x44):authority.latest.envelopeHash;ByteArrayOutputStream bytes=new ByteArrayOutputStream();DataOutputStream out=new DataOutputStream(bytes);
            uuid(out,prep);out.writeByte(1);uuid(out,bundle);out.write(hash(registration));
            out.writeLong(authority.latest.generation);out.write(hash(policy));out.writeLong(authority.registration.bootstrapQuotaBytes);
            out.writeLong(authority.latest.quotaBytes);out.writeInt(golden?10:authority.latest.automaticCount);
            for(HistoryControlStore.Role role:authority.registration.roles){byte[] name=role.basename.getBytes("UTF-8");out.writeByte(role.id);out.writeInt(name.length);out.write(name);}
            out.write(golden?hash(repeat(0x11)):new byte[32]);out.write(golden?hash(repeat(0x22)):new byte[32]);out.writeByte(0);c=bytes.toByteArray();
            byte[] a=row(1,map,nativeMap.length,golden?hash(repeat(0x55)):digest(nativeMap));bytes.reset();out.write(c);out.write(a);out.writeInt(0);d=envelope(3,bytes.toByteArray());
            bytes.reset();uuid(out,operation);uuid(out,bundle);out.write(hash(registration));
            out.writeLong(authority.latest.generation);out.write(hash(policy));out.write(new byte[]{1,2,2});out.writeLong(nativeCount);out.writeLong(d.length);out.write(digest(d));out.writeInt(0);
            b=frame(0x4d44504e,1,bytes.toByteArray());
            bytes.reset();out.write(c);out.writeByte(3);out.writeInt(2);out.write(a);out.write(row(2,descriptor,d.length,digest(d)));out.write(b);p=envelope(1,bytes.toByteArray());
            bytes.reset();uuid(out,prep);uuid(out,operation);out.write(digest(p));out.write(digest(b));out.write(digest(d));l=envelope(2,bytes.toByteArray());
        }
        String reserve(){return "prep-"+prep+".reserve";}
        String begin(){return "pin-"+operation+".begin";}
        String link(){return "prep-"+prep+".link";}
        void persist(Fixture f)throws IOException{Files.write(f.parent.resolve("history_control/"+reserve()),p);}
    }
    static UUID id(int n,boolean fixed){return fixed?UUID.fromString(String.format("00000000-0000-4000-8000-%012d",n)):UUID.randomUUID();}
    static String repeat(int value){byte[] b=new byte[32];Arrays.fill(b,(byte)value);return HistoryStorage.hex(b);}
    static byte[] digest(byte[] b)throws IOException{return HistoryStorage.digest().digest(b);}
    static byte[] envelope(int type,byte[] body)throws IOException{return frame(0x4d444f50,type,body);}
    static byte[] frame(int magic,int type,byte[] body)throws IOException{
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();DataOutputStream out=new DataOutputStream(bytes);
        out.writeInt(magic);out.writeInt(1);out.writeByte(type);out.writeInt(body.length);out.write(body);out.write(digest(bytes.toByteArray()));return bytes.toByteArray();
    }
    static byte[] row(int kind,UUID id,long length,byte[] hash)throws IOException{
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();DataOutputStream out=new DataOutputStream(bytes);
        out.writeByte(kind);uuid(out,id);uuid(out,new UUID(0,0));out.writeInt(0);out.writeLong(length);out.write(hash);return bytes.toByteArray();
    }
    static void unknownNamespace()throws Exception {
        Fixture f=new Fixture();long nativeBytes=f.capacity.inspect().dataActualBytes;
        Path root=Files.createDirectory(f.parent.resolve("private_operations"));Files.write(root.resolve("unknown"),new byte[1024]);
        HistoryCapacity.Usage usage=f.capacity.inspect();
        check(usage.dataActualBytes==nativeBytes+1024,"physical operation bytes must be charged even when unknown");
        check(usage.mutationBlocked,"unknown operation evidence blocks native admission");
        check(!new HistoryGraph(f.store,f.catalog,f.identity,f.index,new HistoryPolicy(64L<<20,20)).scan().complete,"unknown operation evidence blocks every graph path");
        rejects(()->f.store.putSnapshot("t","u","n",3,new byte[]{9},"attempted"),"unknown operation evidence cannot fund mandatory writes");
        check(f.store.transactions().size()==1,"native recovery does not depend on operation ledger validity");
    }
    static void preparation()throws Exception {
        Fixture f=new Fixture();Wire wire=new Wire(f);wire.persist(f);
        HistoryControlStore.Inspection c=inspect(f);
        check(c.state==HistoryControlStore.State.VALIDATED,"funded PREPARE is recognized control, not unknown evidence");
        f.capacity.reconcile();HistoryCapacity.Usage before=f.capacity.inspect();
        check(!new HistoryGraph(f.store,f.catalog,f.identity,f.index,new HistoryPolicy(64L<<20,20)).scan().complete,"pending capture freezes pruning independently of BEGIN");
        check(before.controlReservedBytes==503&&before.controlReservedEntries==3,"PREPARE reserves BEGIN204 CLOSE126 LINK173");
        check(before.dataChargedBytes-before.dataActualBytes==f.store.inspect().reservedCompletionBytes+212+wire.d.length,"PREPARE charges committed future data");
        f.store.putSnapshot("t","u","n",3,new byte[]{9},"attempted");
        f.capacity.setPolicy(new HistoryPolicy(128L*1024*1024,10));
        check(f.capacity.inspect().controlReservedBytes==503,"policy advance preserves all credits despite stale capture");
        Files.write(f.parent.resolve("history_control/"+wire.begin()+".stage"),wire.b);
        HistoryCapacity.Usage staged=f.capacity.inspect();
        check(staged.controlReservedBytes==299&&staged.controlReservedEntries==2,"complete staged BEGIN derives CLOSE exactly once");
        rejects(()->f.capacity.reconcile(),"ordinary reconcile cannot promote prepared BEGIN");
        check(Files.exists(f.parent.resolve("history_control/"+wire.begin()+".stage")),"prepared stage preserved");
    }
    static void domainRouting()throws Exception {
        Fixture f=new Fixture();HistoryPins pins=HistoryPins.open(f.store,f.catalog,f.identity,f.index,f.disk);
        Wire w=new Wire(f);HistoryPins.ManifestIdentity manifest=HistoryPins.ManifestIdentity.read(HistoryPinRecord.Domain.LOCAL_CAPTURE_V1,0,new ByteArrayInputStream(w.d));
        rejects(()->pins.begin(HistoryPinRecord.Purpose.EXPORT,HistoryPinRecord.Scope.ALL_BUNDLE,Collections.emptyList(),manifest),"ordinary begin cannot publish local capture domain");
        check(inspect(f).pins.begins.isEmpty(),"domain rejection precedes publication");
        w.persist(f);Files.write(f.parent.resolve("history_control/"+w.begin()+".stage"),w.b);
        rejects(()->pins.reconcile(),"generic pins cannot promote prepared BEGIN before artifact proof");
        check(Files.exists(f.parent.resolve("history_control/"+w.begin()+".stage")),"prepared BEGIN stage remains untouched");
        Files.move(f.parent.resolve("history_control/"+w.begin()+".stage"),f.parent.resolve("history_control/"+w.begin()));
        pins.reconcile();HistoryOperations ops=pins.operations();
        rejects(()->ops.activateAcknowledged(w.operation.toString(),new ByteArrayInputStream(w.d)),"domain2 cannot activate before durable capture LINK");
    }
    static void codecs()throws Exception {
        Wire w=new Wire(new Fixture(),true);byte[][] vectors={w.d,w.b,w.p,w.l};
        String[] hashes={"990d77ac4677f257c9bc2e746e739232d5802b3f02c248c24210d55c45bd9b41","1548e74c29a6196e7e9c0aa81f85dbfcae71c75205013287e50dad3a97321959","a11eecf61d1bf1c61dba8825b65b65f2da50b66953959207faafef4e5c0b7f65","1cb4f91da7862cf36c37880de51a4d045f35c9bbcf889ffd4539512a36d7ecdd"};
        for(int i=0;i<vectors.length;i++)check(HistoryStorage.hex(digest(vectors[i])).equals(hashes[i]),"independent frozen golden "+i);
        for(byte[] vector:new byte[][]{w.d,w.p,w.l}){
            check(Arrays.equals(vector,PrivateOperationRecord.decode(vector).encoded()),"exact local frame decode");
            for(int n=0;n<vector.length;n++){byte[] truncated=Arrays.copyOf(vector,n);rejects(()->PrivateOperationRecord.decode(truncated),"all truncations reject");}
            for(int n=0;n<vector.length;n++){byte[] flipped=vector.clone();flipped[n]^=1;rejects(()->PrivateOperationRecord.decode(flipped),"all checksum mutations reject");}
        }
        for(int offset:new int[]{13+16,13+16+1+16+32,13+235,13+235+1}){
            byte[] changed=w.p.clone();changed[offset]=(byte)255;System.arraycopy(digest(Arrays.copyOf(changed,changed.length-32)),0,changed,changed.length-32,32);
            rejects(()->PrivateOperationRecord.decode(changed),"resigned invalid typed fields");
        }
    }
    static void prefixes()throws Exception {
        Fixture f=new Fixture();Wire w=new Wire(f);w.persist(f);Path root=f.parent.resolve("history_control"),stage=root.resolve(w.begin()+".stage");
        for(int n=0;n<=w.b.length;n++){
            Files.write(stage,Arrays.copyOf(w.b,n));HistoryCapacity.Usage usage=f.capacity.inspect();
            check(usage.controlReservedBytes==503-n&&usage.controlReservedEntries==2,"BEGIN prefix conservation "+n);
            check(usage.mutationBlocked,"BEGIN stage never grants ordinary readiness "+n);
        }
        Files.move(stage,root.resolve(w.begin()));stage=root.resolve(w.link()+".stage");
        for(int n=0;n<=w.l.length;n++){
            Files.write(stage,Arrays.copyOf(w.l,n));HistoryControlStore.Inspection c=inspect(f);
            check(c.state==HistoryControlStore.State.VALIDATED,"exact LINK prefix is optional "+n);
            check(c.controlReservedBytes==299-n&&c.controlReservedEntries==1,"LINK prefix conservation "+n);
            f.capacity.reconcile();f.store.putSnapshot("t","u","n",3,new byte[]{9},"attempted");
            check(Files.size(stage)==n&&!Files.exists(root.resolve(w.link())),"ordinary writers never repair LINK "+n);
        }
        Files.write(stage,new byte[]{99});check(inspect(f).state==HistoryControlStore.State.BLOCKED,"unknown LINK prefix blocks authority");
        rejects(()->f.capacity.setPolicy(new HistoryPolicy(128L<<20,10)),"unknown prefix cannot spend credits");
    }
    static void slotBoundary()throws Exception {
        Fixture f=new Fixture();Wire w=new Wire(f);w.persist(f);Path root=f.parent.resolve("history_control");
        while(inspect(f).actualEntries<253){HistoryControlStore.Inspection c=inspect(f);long next=c.latest.generation+1;Files.write(root.resolve(HistoryControlStore.policyFilename(next)),HistoryControlStore.encodePolicy(c.registration.bundleId,next,c.latest.envelopeHash,c.latest.quotaBytes,c.latest.automaticCount));}
        f.capacity.reconcile();check(f.capacity.inspect().controlActualEntries+f.capacity.inspect().controlReservedEntries==256,"full shared control slots");
        rejects(()->f.capacity.setPolicy(new HistoryPolicy(128L<<20,10)),"policy cannot consume preparation credit");
        HistoryControlStore.Inspection c=inspect(f);long next=c.latest.generation+1;Path policy=root.resolve(HistoryControlStore.policyFilename(next)+".stage");
        Files.write(policy,HistoryControlStore.encodePolicy(c.registration.bundleId,next,c.latest.envelopeHash,128L<<20,10));
        rejects(()->f.capacity.reconcile(),"policy stage cannot steal preparation credit");check(Files.exists(policy),"policy stage preserved at limit");Files.delete(policy);
        Files.write(root.resolve(w.begin()+".stage"),w.b);check(f.capacity.inspect().controlActualEntries+f.capacity.inspect().controlReservedEntries==256,"full BEGIN stage credit deduplicated");
    }
    static void retainedAndCallbacks()throws Exception {
        Fixture missing=new Fixture();Wire p=new Wire(missing);p.persist(missing);missing.capacity.reconcile();Files.delete(missing.parent.resolve("history_control/"+p.reserve()));
        check(missing.capacity.inspect().mutationBlocked,"known durable PREPARE omission never becomes empty legacy state");
        rejects(()->missing.capacity.reconcile(),"reopen cannot forget retained PREPARE");
        rejects(()->HistoryPins.open(missing.store,missing.catalog,missing.identity,missing.index,missing.disk),"pin audit cannot forget retained PREPARE");
        Fixture changed=new Fixture();changed.disk.hook=(event,file)->{if(event.equals("dir-after")&&file.getName().equals("history_control")){Path root=changed.parent.resolve("private_operations");Files.createDirectory(root);Files.write(root.resolve("foreign"),new byte[]{1});}};
        rejects(()->changed.capacity.reconcile(),"last callback must revalidate operation inventory");changed.disk.hook=null;
    }
    static void linkedLoss()throws Exception {
        for(boolean closed:new boolean[]{false,true})for(boolean partial:new boolean[]{false,true})for(String kind:new String[]{"map","descriptor"}){
            Fixture f=new Fixture();HistoryGraph.Graph graph=new HistoryGraph(f.store,f.catalog,f.identity,f.index,new HistoryPolicy(64L<<20,20)).scan();
            ByteArrayOutputStream map=new ByteArrayOutputStream();HistoryArchiveManifest.freeze(graph,inspect(f),UUID::randomUUID).write(map);
            Wire w=new Wire(f,false,map.toByteArray());w.persist(f);Path root=Files.createDirectory(f.parent.resolve("private_operations"));Path prep=Files.createDirectory(root.resolve(w.prep.toString()));
            Path nativeMap=prep.resolve("map-"+w.map+".native"),descriptor=prep.resolve("capture-"+w.descriptor+".descriptor");Files.write(nativeMap,w.nativeMap);Files.write(descriptor,w.d);
            Files.write(f.parent.resolve("history_control/"+w.begin()),w.b);Files.write(f.parent.resolve("history_control/"+w.link()),w.l);
            if(closed)Files.write(f.parent.resolve("history_control/pin-"+w.operation+".close"),HistoryPinRecord.close(HistoryPinRecord.decode(w.b),HistoryPinRecord.Outcome.CANCELLED));
            check(!f.capacity.inspect().mutationBlocked,"complete linked data accounted"); // Deliberately no remembered data baseline: cold discovery case.
            Path victim=kind.equals("map")?nativeMap:descriptor;byte[] content=Files.readAllBytes(victim);Files.delete(victim);
            if(partial)Files.write(victim.resolveSibling(victim.getFileName()+".stage"),Arrays.copyOf(content,content.length/2));
            check(f.capacity.inspect().mutationBlocked,"linked "+kind+" loss cannot become unmaterialized future credit, closed="+closed);
            rejects(()->f.store.putSnapshot("t","u","n",3,new byte[]{9},"attempted"),"lost linked data blocks admission");
        }
    }
    static void admissionBarriers()throws Exception {
        for(boolean pin:new boolean[]{false,true}){
            Fixture f=new Fixture();HistoryPins pins=HistoryPins.open(f.store,f.catalog,f.identity,f.index,f.disk);
            HistoryPins.ManifestIdentity manifest=HistoryPins.ManifestIdentity.read(HistoryPinRecord.Domain.NATIVE_HISTORY_V1,0,new ByteArrayInputStream(new byte[0]));
            long generation=inspect(f).latest.generation;Path namespace=Files.createDirectory(f.parent.resolve("private_operations"));Files.write(namespace.resolve("foreign"),new byte[]{1});
            rejects(()->{if(pin)pins.begin(HistoryPinRecord.Purpose.EXPORT,HistoryPinRecord.Scope.ALL_BUNDLE,Collections.emptyList(),manifest);else f.capacity.setPolicy(new HistoryPolicy(128L<<20,10));},"unknown operation data blocks control publication");
            check(inspect(f).latest.generation==generation&&inspect(f).pins.begins.isEmpty(),"admission must reject before any control publication");
        }
        for(boolean pin:new boolean[]{false,true}){
            Fixture f=new Fixture();HistoryPins pins=HistoryPins.open(f.store,f.catalog,f.identity,f.index,f.disk);
            HistoryPins.ManifestIdentity manifest=HistoryPins.ManifestIdentity.read(HistoryPinRecord.Domain.NATIVE_HISTORY_V1,0,new ByteArrayInputStream(new byte[0]));
            long generation=inspect(f).latest.generation;
            f.disk.hook=(event,file)->{if(event.equals("file-after")){Path namespace=f.parent.resolve("private_operations");if(!Files.exists(namespace)){Files.createDirectory(namespace);Files.write(namespace.resolve("foreign"),new byte[]{1});}}};
            rejects(()->{if(pin)pins.begin(HistoryPinRecord.Purpose.EXPORT,HistoryPinRecord.Scope.ALL_BUNDLE,Collections.emptyList(),manifest);else f.capacity.setPolicy(new HistoryPolicy(128L<<20,10));},"publication callback must revalidate operation data");
            check(inspect(f).latest.generation==generation&&inspect(f).pins.begins.isEmpty(),"file-sync callback mutation cannot promote stage");
        }
    }
    static void activeBarriers()throws Exception {
        for(boolean terminal:new boolean[]{false,true}){
            Fixture f=new Fixture();HistoryPins pins=HistoryPins.open(f.store,f.catalog,f.identity,f.index,f.disk);
            HistoryPins.ManifestIdentity manifest=HistoryPins.ManifestIdentity.read(HistoryPinRecord.Domain.NATIVE_HISTORY_V1,0,new ByteArrayInputStream(new byte[0]));
            String id=pins.begin(HistoryPinRecord.Purpose.EXPORT,HistoryPinRecord.Scope.ALL_BUNDLE,Collections.emptyList(),manifest);
            HistoryOperations.Quiescence proof=quiescent(pins,id);Path namespace=Files.createDirectory(f.parent.resolve("private_operations"));Files.write(namespace.resolve("foreign"),new byte[]{1});
            rejects(()->{if(terminal)pins.close(proof,HistoryPinRecord.Outcome.CANCELLED,null);else pins.resume(id,new ByteArrayInputStream(new byte[0]));},"active operation cannot escape unknown operation data");
            check(inspect(f).pins.closes.isEmpty(),"unknown operation data cannot release active protection");
        }
    }
    static void controlReplacement()throws Exception {
        Fixture f=new Fixture();Wire wire=new Wire(f);wire.persist(f);
        f.disk.hook=(event,file)->{if(event.equals("dir-after")&&file.getName().equals("history_control")){Path victim=file.toPath().resolve(wire.reserve()),replacement=file.toPath().resolve("replacement");Files.write(replacement,Files.readAllBytes(victim));Files.move(replacement,victim,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}};
        rejects(()->f.capacity.reconcile(),"same-byte PREPARE replacement after last callback cannot acknowledge authority");
    }
    static void recoveryAndReadBarriers()throws Exception {
        for(boolean duringCallback:new boolean[]{false,true}){
            Fixture f=new Fixture();HistoryPins pins=HistoryPins.open(f.store,f.catalog,f.identity,f.index,f.disk);
            HistoryPins.ManifestIdentity manifest=HistoryPins.ManifestIdentity.read(HistoryPinRecord.Domain.NATIVE_HISTORY_V1,0,new ByteArrayInputStream(new byte[0]));
            String id=pins.begin(HistoryPinRecord.Purpose.EXPORT,HistoryPinRecord.Scope.ALL_BUNDLE,Collections.emptyList(),manifest);
            HistoryOperations.Quiescence proof=quiescent(pins,id);
            byte[] prefix=Arrays.copyOf(HistoryPinRecord.close(inspect(f).pins.active,HistoryPinRecord.Outcome.CANCELLED),60);
            Path stage=f.parent.resolve("history_control/pin-"+id+".close.stage");Files.write(stage,prefix);
            if(duringCallback)f.disk.hook=(event,file)->{if(event.equals("dir-after")&&file.getName().equals("history_control"))foreign(f);};else foreign(f);
            rejects(()->pins.recoverClose(proof,HistoryPins.RecoveryChoice.CANCEL_UNBOUND,null),"unknown accounting blocks terminal recovery before append");
            f.disk.hook=null;check(Arrays.equals(prefix,Files.readAllBytes(stage)),"refused recovery preserves original CLOSE prefix");
            check(inspect(f).pins.closes.isEmpty(),"refused recovery cannot publish CLOSE");
        }
        Fixture f=new Fixture();HistoryPins pins=HistoryPins.open(f.store,f.catalog,f.identity,f.index,f.disk);
        HistoryPins.ManifestIdentity manifest=HistoryPins.ManifestIdentity.read(HistoryPinRecord.Domain.NATIVE_HISTORY_V1,0,new ByteArrayInputStream(new byte[0]));
        String id=pins.begin(HistoryPinRecord.Purpose.EXPORT,HistoryPinRecord.Scope.ALL_BUNDLE,Collections.emptyList(),manifest);
        InputStream callback=new InputStream(){public int read()throws IOException {try{foreign(f);}catch(Exception failure){throw new IOException(failure);}return -1;}};
        rejects(()->pins.resume(id,callback),"manifest callback cannot introduce unknown data before resume acknowledgment");
        Fixture historical=new Fixture();HistoryPins old=HistoryPins.open(historical.store,historical.catalog,historical.identity,historical.index,historical.disk);
        String closed=old.begin(HistoryPinRecord.Purpose.EXPORT,HistoryPinRecord.Scope.ALL_BUNDLE,Collections.emptyList(),manifest);
        old.close(quiescent(old,closed),HistoryPinRecord.Outcome.CANCELLED,null);foreign(historical);
        old.reconcile();check(inspect(historical).pins.closes.size()==1,"historical CLOSED audit remains source independent beside unknown data");
        rejects(()->historical.capacity.setPolicy(new HistoryPolicy(128L<<20,10)),"historical audit grants no new writer authority");
    }
    static void nativeAdmissionBarriers()throws Exception {
        for(String writer:new String[]{"snapshot","prepare","completion","catalog","identity","index","completion-retry","identity-retry","index-retry"}){
            for(String event:writer.endsWith("-retry")?new String[]{"dir-after"}:new String[]{"file-after","dir-after"}){
                Fixture f=new Fixture();String history=writer.startsWith("index")?f.identity.resolve("uri","tab"):null;
                if(writer.startsWith("index")||writer.equals("completion-retry"))f.store.complete(f.pending);
                if(writer.equals("index-retry"))f.index.recordVerified(history,f.pending.id);
                if(writer.equals("identity-retry"))f.identity.resolve("existing-uri","tab");
                String role=writer.equals("catalog")?"catalog":writer.startsWith("identity")?"identity":writer.startsWith("index")?"index":"store";
                long before=committedFiles(f.parent.resolve(role));boolean[] fired={false},moved={writer.endsWith("-retry")};
                f.disk.hook=(at,file)->{
                    if(at.equals("move-after"))moved[0]=true;
                    if(!fired[0]&&at.equals(event)&&(!event.equals("dir-after")||moved[0])&&(file==null||file.getName().equals(role))){fired[0]=true;foreign(f);}
                };
                rejects(()->{
                    switch(writer){
                        case "snapshot":f.store.putSnapshot("t","u","n",3,new byte[]{9},"attempted");break;
                        case "prepare":f.store.prepare(f.original,f.attempted,f.original.hash);break;
                        case "completion":case "completion-retry":f.store.complete(f.pending);break;
                        case "catalog":f.catalog.name(f.original.id,"callback test");break;
                        case "identity":f.identity.resolve("new-uri","tab");break;
                        case "identity-retry":f.identity.resolve("existing-uri","tab");break;
                        case "index":case "index-retry":f.index.recordVerified(history,f.pending.id);break;
                    }
                },writer+" must not acknowledge unknown accounting from "+event);
                f.disk.hook=null;check(fired[0],"native publication callback actually reached");
                long expected=before+(event.equals("dir-after")&&!writer.endsWith("-retry")?1:0);
                check(committedFiles(f.parent.resolve(role))==expected,"pre-move refusal retains stage; post-move refusal retains committed evidence");
                check(f.capacity.inspect().mutationBlocked,"unknown accounting blocks subsequent admission");
            }
        }
    }
    static long committedFiles(Path directory)throws IOException {
        try(java.util.stream.Stream<Path> files=Files.list(directory)){
            return files.filter(p->!p.toString().endsWith(".stage")&&!p.toString().endsWith(".completion-stage")).count();
        }
    }
    static void foreign(Fixture f)throws IOException {
        Path namespace=f.parent.resolve("private_operations");if(!Files.exists(namespace))Files.createDirectory(namespace);
        Files.write(namespace.resolve("foreign"),new byte[]{1});
    }
    static void policyStageBarrier()throws Exception {
        Fixture f=new Fixture();HistoryControlStore.Inspection before=inspect(f);
        Path stage=f.parent.resolve("history_control/"+HistoryControlStore.policyFilename(1)+".stage");
        Files.write(stage,HistoryControlStore.encodePolicy(before.registration.bundleId,1,before.latest.envelopeHash,128L<<20,10));
        Path namespace=Files.createDirectory(f.parent.resolve("private_operations"));Files.write(namespace.resolve("foreign"),new byte[]{1});
        rejects(()->f.capacity.reconcile(),"unknown operation data rejects retained policy-stage promotion");
        check(Files.exists(stage)&&inspect(f).latest.generation==0,"policy-stage refusal precedes promotion");
    }
    public static void main(String[] args)throws Exception {
        if(args.length==0||args[0].equals("unknown"))unknownNamespace();
        if(args.length==0||args[0].equals("prepare"))preparation();
        if(args.length==0||args[0].equals("domain"))domainRouting();
        if(args.length==0||args[0].equals("codec"))codecs();
        if(args.length==0||args[0].equals("prefix"))prefixes();
        if(args.length==0||args[0].equals("slots"))slotBoundary();
        if(args.length==0||args[0].equals("retained"))retainedAndCallbacks();
        if(args.length==0||args[0].equals("loss"))linkedLoss();
        if(args.length==0||args[0].equals("admission"))admissionBarriers();
        if(args.length==0||args[0].equals("active"))activeBarriers();
        if(args.length==0||args[0].equals("replacement"))controlReplacement();
        if(args.length==0||args[0].equals("policy-stage"))policyStageBarrier();
        if(args.length==0||args[0].equals("recovery-accounting"))recoveryAndReadBarriers();
        if(args.length==0||args[0].equals("native-admission"))nativeAdmissionBarriers();
        System.out.println("Operation data checks PASS "+checks);
    }
}
