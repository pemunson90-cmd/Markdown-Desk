package com.example.markdowndesk;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.util.*;

/** Operation data belongs to the registered native parent, never a separate capacity authority. */
final class PrivateOperationStore {
    static final String ROOT="private_operations";
    static final int MAX_ENTRIES=65536;
    static final class Control {
        final List<PrivateOperationRecord> preparations=new ArrayList<>();
        final Map<UUID,HistoryControlStore.Entry> links=new HashMap<>();
        final Set<UUID> terminalCancelled=new HashSet<>();
        int unfinished;
        boolean valid=true,ready=true,present,pending;
        long reservedBytes;int reservedEntries;
    }
    static Control control(List<HistoryControlStore.Entry> entries,HistoryControlStore.Registration registration,HistoryPinRecord.Inventory pins)throws IOException {
        Control result=new Control();Map<String,HistoryControlStore.Entry> byName=new HashMap<>();Map<UUID,PrivateOperationRecord> preparations=new HashMap<>();Set<UUID> operations=new HashSet<>();
        for(HistoryControlStore.Entry e:entries)if(e.filename!=null)byName.put(e.filename,e);
        for(HistoryControlStore.Entry e:entries){
            if(!operationEntry(e))continue;result.present=true;
            String name=committed(e);
            if(name.endsWith(".reserve")){
                PrivateOperationRecord p=e.operation;
                if(p==null||p.type!=PrivateOperationRecord.PREPARE||!p.name().equals(name)||!p.binding.bound(registration,entries)||preparations.put(p.preparation,p)!=null||!operations.add(p.operation)){result.valid=false;continue;}
                result.preparations.add(p);if(e.stage)result.ready=false;
            }
        }
        int pending=0;
        for(PrivateOperationRecord p:result.preparations){
            HistoryControlStore.Entry reserve=byName.get(p.name()),reserveStage=byName.get(p.name()+".stage");
            HistoryControlStore.Entry begin=byName.get(p.begin.name()),beginStage=byName.get(p.begin.name()+".stage"),close=byName.get("pin-"+p.operation+".close"),closeStage=byName.get("pin-"+p.operation+".close.stage");
            if(reserve!=null&&reserveStage!=null||begin!=null&&beginStage!=null){result.valid=false;continue;}
            byte[] expectedBegin=HistoryPinRecord.encode(p.begin);
            boolean fullBegin=false;
            if(begin!=null){if(!Objects.equals(begin.envelopeHash,p.beginHash)||begin.pin==null){result.valid=false;continue;}fullBegin=true;}
            else if(beginStage!=null){
                if(!beginStage.pinPrefix(expectedBegin)){result.valid=false;continue;}
                result.reservedBytes=add(result.reservedBytes,expectedBegin.length-beginStage.length);
                fullBegin=beginStage.length==expectedBegin.length&&beginStage.pin!=null;
            }else{result.reservedBytes=add(result.reservedBytes,expectedBegin.length);result.reservedEntries++;}
            // A complete BEGIN.stage already participates in the native pin inventory's CLOSE credit.
            if(!fullBegin){result.reservedBytes=add(result.reservedBytes,HistoryPinRecord.CLOSE_BYTES);result.reservedEntries++;}
            PrivateOperationRecord expectedLink=PrivateOperationRecord.link(p);HistoryControlStore.Entry link=byName.get(expectedLink.name()),stage=byName.get(expectedLink.name()+".stage");
            if(link!=null&&stage!=null){result.valid=false;continue;}
            if(link!=null||stage!=null){
                HistoryControlStore.Entry entry=link!=null?link:stage;
                if(begin==null||reserve==null||!entry.operationPrefix(expectedLink.encoded())||link!=null&&!expectedLink.envelopeHash.equals(entry.envelopeHash)){result.valid=false;continue;}
                result.links.put(p.preparation,entry);result.reservedBytes=add(result.reservedBytes,PrivateOperationRecord.LINK_BYTES-entry.length);
            }else{result.reservedBytes=add(result.reservedBytes,PrivateOperationRecord.LINK_BYTES);result.reservedEntries++;}
            if(reserveStage!=null&&(begin!=null||beginStage!=null||link!=null||stage!=null||close!=null||closeStage!=null))result.valid=false;
            boolean cancelled=reserve!=null&&reserveStage==null&&begin!=null&&beginStage==null&&close!=null&&closeStage==null
                    &&link==null&&stage==null&&close.pin!=null&&close.pin.outcome==HistoryPinRecord.Outcome.CANCELLED
                    &&HistoryPinRecord.matches(p.begin,close.pin);
            if(cancelled)result.terminalCancelled.add(p.preparation);
            if(link==null||close==null){pending++;if(!cancelled)result.unfinished++;}
        }
        for(HistoryControlStore.Entry e:entries){
            if(operationEntry(e)&&committed(e).endsWith(".link")){
                boolean found=false;for(PrivateOperationRecord p:result.preparations)if(committed(e).equals("prep-"+p.preparation+".link"))found=true;
                if(!found)result.valid=false;
            }
            if(e.pin!=null&&e.pin.begin&&e.pin.manifest.domain==HistoryPinRecord.Domain.LOCAL_CAPTURE_V1&&!operations.contains(e.pin.operation))result.valid=false;
        }
        if(result.unfinished>1)result.valid=false;result.pending=pending!=0;result.ready&=result.valid;
        result.preparations.sort(Comparator.comparing(p->p.preparation.toString()));return result;
    }
    static boolean operationEntry(HistoryControlStore.Entry e){return e.filename!=null&&PrivateOperationRecord.knownName(committed(e));}
    private static String committed(HistoryControlStore.Entry e){return e.stage?e.filename.substring(0,e.filename.length()-6):e.filename;}

    static final class Ledger {
        long actualBytes,reservedBytes,occupiedEntries,reservedEntries;
        boolean complete=true,present,pending;
        String fingerprint="";
        final List<String> diagnostics=new ArrayList<>();
        final Map<String,String> entries=new TreeMap<>();
        final Map<String,String> controlIdentities=new TreeMap<>();
        void invalid(String reason){complete=false;diagnostics.add("operation: "+reason);}
    }
    static Ledger inspect(File nativeRoot,HistoryControlStore.Inspection control,HistoryStorage.Budget budget)throws IOException {
        // Deliberately bind the existing native monitor; monitorFor(private_operations/UUID) is a different authority.
        Object monitor=HistoryStorage.monitorFor(nativeRoot);if(!Thread.holdsLock(monitor))throw new IOException("Native parent monitor required");
        budget.charge(HistoryStorage.operationEvidenceBytes(nativeRoot));
        Ledger ledger=new Ledger();ledger.pending=control.operations.pending;ledger.present=control.operations.present;
        if(!control.operations.valid)ledger.invalid("invalid commitments");
        Path root=HistoryStorage.canonicalRoot(nativeRoot).getParentFile().toPath().resolve(ROOT);
        Map<String,PrivateOperationRecord.Artifact> expected=new HashMap<>();Set<String> directories=new HashSet<>(),linkedArtifacts=new HashSet<>();boolean rootFunded=false;
        for(PrivateOperationRecord p:control.operations.preparations){
            budget.charge(2048L+512L*p.artifacts.size());directories.add(p.preparation.toString());rootFunded|=(p.directoryMask&1)!=0;
            HistoryControlStore.Entry link=control.operations.links.get(p.preparation);
            for(PrivateOperationRecord.Artifact a:p.artifacts){String key=p.preparation+"/"+a.filename();expected.put(key,a);if(link!=null&&!link.stage)linkedArtifacts.add(key);}
        }
        BasicFileAttributes rootAttributes=attributesOrAbsent(root);
        if(rootAttributes==null){if(!directories.isEmpty()){ledger.reservedEntries=1+directories.size();if(!rootFunded)ledger.invalid("required namespace disappeared");} }
        else{
            ledger.present=true;count(ledger,budget,ROOT,rootAttributes,root,false);
            if(!rootAttributes.isDirectory()||rootAttributes.isSymbolicLink())ledger.invalid("non-directory namespace");
            else{
                try(DirectoryStream<Path> children=Files.newDirectoryStream(root)){
                    for(Path child:children){BasicFileAttributes a=attributes(child);String name=child.getFileName().toString();count(ledger,budget,name,a,child,false);
                        if(!directories.contains(name)||!a.isDirectory()||a.isSymbolicLink()){ledger.invalid("unknown namespace entry");continue;}
                        directories.remove(name);
                        try(DirectoryStream<Path> files=Files.newDirectoryStream(child)){
                            for(Path file:files){BasicFileAttributes f=attributes(file);String key=name+"/"+file.getFileName();
                                boolean stage=key.endsWith(".stage");String canonical=stage?key.substring(0,key.length()-6):key;
                                PrivateOperationRecord.Artifact wanted=expected.remove(canonical);
                                count(ledger,budget,key,f,file,wanted!=null&&f.isRegularFile()&&f.size()<=wanted.length);
                                if(wanted==null||!f.isRegularFile()||f.isSymbolicLink()||f.size()>wanted.length){ledger.invalid("unknown/conflicting artifact");continue;}
                                if(stage){ledger.reservedBytes=add(ledger.reservedBytes,wanted.length-f.size());ledger.pending=true;if(linkedArtifacts.contains(canonical))ledger.invalid("linked artifact replaced by stage");}
                                else if(f.size()!=wanted.length||!ledger.entries.get(key).endsWith(":"+wanted.hash))ledger.invalid("changed artifact");
                            }
                        }catch(DirectoryIteratorException invalid){ledger.invalid("unreadable preparation");}
                        same(a,attributes(child));
                    }
                }catch(DirectoryIteratorException invalid){ledger.invalid("unreadable namespace");}
                same(rootAttributes,attributes(root));ledger.reservedEntries=add(ledger.reservedEntries,directories.size());
                if(control.operations.preparations.isEmpty())ledger.invalid("unfunded namespace");
            }
        }
        for(Map.Entry<String,PrivateOperationRecord.Artifact> missing:expected.entrySet()){ledger.reservedBytes=add(ledger.reservedBytes,missing.getValue().length);ledger.reservedEntries=add(ledger.reservedEntries,1);if(linkedArtifacts.contains(missing.getKey()))ledger.invalid("linked artifact disappeared");}
        if(add(ledger.occupiedEntries,ledger.reservedEntries)>MAX_ENTRIES)ledger.invalid("namespace slot limit");
        for(HistoryControlStore.Entry entry:control.entries)if(operationEntry(entry)){
            budget.charge(512L+entry.filename.length()*2L);
            Path path=nativeRoot.getParentFile().toPath().resolve(HistoryControlStore.ROOT_NAME).resolve(entry.filename);
            BasicFileAttributes a=attributes(path);
            if(!a.isRegularFile()||a.isSymbolicLink()||a.fileKey()==null||a.size()!=entry.length)throw new IOException("Changed operation control identity");
            ledger.controlIdentities.put(entry.filename,a.fileKey()+":"+a.creationTime()+":"+a.lastModifiedTime()+":"+a.size());
        }
        HistoryStorage.requireOperationEvidence(nativeRoot,control,ledger);
        if(ledger.present){MessageDigest digest=HistoryStorage.digest();try(DataOutputStream out=new DataOutputStream(new java.security.DigestOutputStream(new OutputStream(){public void write(int b){}public void write(byte[] b,int o,int n){}},digest))){
            out.writeUTF("private-operation-ledger-v1");for(Map.Entry<String,String> e:ledger.controlIdentities.entrySet()){out.writeUTF(e.getKey());out.writeUTF(e.getValue());}for(Map.Entry<String,String> e:ledger.entries.entrySet()){out.writeUTF(e.getKey());out.writeUTF(e.getValue());}
            for(HistoryControlStore.Entry e:control.entries)if(operationEntry(e)){out.writeUTF(e.filename);out.writeLong(e.length);out.writeUTF(e.envelopeHash==null?"":e.envelopeHash);out.writeBoolean(e.stage);}out.writeLong(ledger.actualBytes);out.writeLong(ledger.reservedBytes);out.writeLong(ledger.occupiedEntries);out.writeLong(ledger.reservedEntries);out.writeLong(control.controlReservedBytes);out.writeInt(control.controlReservedEntries);out.writeBoolean(ledger.complete);
        }ledger.fingerprint=HistoryStorage.hex(digest.digest());}
        return ledger;
    }
    /** Typed accounting classification only. Actual new-owner admission is separate. */
    static boolean historicalCancelledOnly(HistoryControlStore.Inspection c,Ledger l) {
        return historicalRemainder(c,l,null);
    }
    private static boolean historicalRemainder(HistoryControlStore.Inspection c,Ledger l,UUID selected) {
        if(!l.complete||!l.pending||!l.diagnostics.isEmpty()||!c.chainComplete||!c.usageComplete||!c.diagnostics.isEmpty()
                ||!c.operations.valid||!c.operations.ready||!c.pins.valid||!c.pins.stages.isEmpty()||!c.stages.isEmpty()||c.hasMaintenance()
                ||c.operations.terminalCancelled.isEmpty()||!namespacePresent(l)
                ||(selected==null?c.pins.active!=null:c.pins.active==null||!selected.equals(c.pins.active.operation)))return false;
        for(PrivateOperationRecord p:c.operations.preparations){
            if(p.operation.equals(selected))continue;
            if(c.operations.terminalCancelled.contains(p.preparation))continue;
            HistoryControlStore.Entry link=c.operations.links.get(p.preparation);boolean closed=false;
            for(HistoryPinRecord close:c.pins.closes)if(HistoryPinRecord.matches(p.begin,close))closed=true;
            if(link==null||link.stage||!closed)return false;
        }
        return true;
    }
    private static boolean namespacePresent(Ledger l){String root=l.entries.get(ROOT);return root!=null&&root.startsWith("dir:")&&!root.equals("dir:null");}
    static void requireHistoricalCaptureOwnerLocked(File root,HistoryControlStore.Inspection c,Ledger l,
            PreparationSources requesting,UUID preparation,HistoryStorage.Budget budget)throws IOException {
        HistoryStorage.requireMutation(root);
        if(!historicalCancelledOnly(c,l)||requesting==null||c.operations.terminalCancelled.contains(preparation))throw new IOException("Historical cancellation is not eligible for this capture");
        requesting.requireIdleBindingLocked(root,preparation,budget);
        if(hasHistoricalOwnerLocked(root,c))throw new IOException("Historical preparation owner is still registered");
    }
    private static boolean hasHistoricalOwnerLocked(File root,HistoryControlStore.Inspection c)throws IOException {
        PreparationSources owner=HistoryStorage.preparationSources(root);if(owner==null)return false;
        for(UUID id:c.operations.terminalCancelled)if(owner.isPreparationLocked(id))return true;return false;
    }
    /** A terminal historical stage is immutable; current unfinished prefixes are not. */
    static boolean retainHistoricalStageLocked(File root,HistoryControlStore.Inspection c,Ledger l,String key)throws IOException {
        HistoryStorage.requireMutation(root);
        if(!l.complete||!l.diagnostics.isEmpty()||!namespacePresent(l)||!c.chainComplete||!c.usageComplete||!c.diagnostics.isEmpty()
                ||!c.operations.valid||!c.pins.valid||hasHistoricalOwnerLocked(root,c))return false;
        for(PrivateOperationRecord p:c.operations.preparations)if(c.operations.terminalCancelled.contains(p.preparation))
            for(PrivateOperationRecord.Artifact a:p.artifacts)if(key.equals(p.preparation+"/"+a.filename()+".stage"))return true;
        return false;
    }
    /** Immutable declared sources for an authentic same-process completed capture. No cold adoption. */
    static final class CapturedData {
        final HistoryPinRecord begin;final HistoryStorage.Budget budget;
        final Map<UUID,CapturedSource> sources;final byte[] descriptor;
        CapturedData(HistoryPinRecord begin,HistoryStorage.Budget budget,Map<UUID,CapturedSource> sources,byte[] descriptor){
            this.begin=begin;this.budget=budget;this.sources=Collections.unmodifiableMap(sources);this.descriptor=descriptor;
        }
    }
    static final class CapturedSource {
        final Path path;final long length;final String hash;private final BasicFileAttributes anchor;
        CapturedSource(Path path,long length,String hash)throws IOException {
            HistoryStorage.canonicalRoot(path.getParent().toFile());this.path=path;this.length=length;this.hash=hash;
            anchor=attributes(path);verify();
            if(!hash.equals(fileHash(path,anchor)))throw new IOException("Changed captured envelope");verify();
        }
        void verify()throws IOException {
            HistoryStorage.canonicalRoot(path.getParent().toFile());BasicFileAttributes now=attributes(path);
            if(!now.isRegularFile()||now.isSymbolicLink()||now.size()!=length||now.fileKey()==null
                    ||!anchor.creationTime().equals(now.creationTime()))throw new IOException("Changed captured source identity");
            same(anchor,now);
        }
    }
    static CapturedData capturedLocked(File root,PrivateCaptureWriter.CapturedBinding captured,
            HistoryStorage.Budget budget,HistoryControlStore.Inspection c)throws IOException {
        HistoryStorage.requireMutation(root);captured.requireBudget(budget);
        if(!root.equals(captured.root)||c.pins.active==null||!captured.beginHash.equals(c.pins.active.envelopeHash)
                ||!c.pins.stages.isEmpty()||!c.operations.valid||!c.operations.ready)throw new IOException("Capture is not an active committed binding");
        PrivateOperationRecord p=null;
        for(PrivateOperationRecord candidate:c.operations.preparations)if(candidate.preparation.equals(captured.preparation))p=candidate;
        HistoryControlStore.Entry link=c.operations.links.get(captured.preparation);
        if(p==null||!captured.operation.equals(p.operation)||!captured.prepareHash.equals(p.envelopeHash)
                ||!captured.beginHash.equals(p.beginHash)||!captured.descriptorHash.equals(p.descriptorHash)
                ||link==null||link.stage||!captured.linkHash.equals(link.envelopeHash))throw new IOException("Missing exact completed capture records");
        for(HistoryControlStore.Entry e:c.entries)if(e.stage||e.pin!=null&&!e.pin.begin&&captured.operation.equals(e.pin.operation))throw new IOException("Capture has staged/terminal evidence");
        Ledger ledger=inspect(root,c,budget);
        if(!ledger.complete)throw new IOException("Capture data not complete");
        for(PrivateOperationRecord.Artifact a:p.artifacts){String key=p.preparation+"/"+a.filename(),value=ledger.entries.get(key);
            if(value==null||!value.startsWith("file:"+a.length+":")||!value.endsWith(":"+a.hash)||ledger.entries.containsKey(key+".stage"))throw new IOException("Selected capture artifact is not final");}
        if(ledger.reservedBytes!=0||ledger.reservedEntries!=0){
            if(!historicalRemainder(c,ledger,p.operation)||HistoryStorage.preparationSources(root)!=null)throw new IOException("Remaining reservations lack eligible historical cancellation");
        }
        PrivateOperationRecord.Artifact mapRow=p.artifact(1),descriptorRow=p.artifact(2);
        long upper=Math.min(HistoryArchiveManifest.MAX_ENTRIES,mapRow.length/74);
        long rowBytes=2048L+4L*(root.getParentFile().getPath().length()+160);
        budget.charge(add(4096L,Math.multiplyExact(rowBytes,upper+p.artifacts.size())));
        Path directory=root.getParentFile().toPath().resolve(ROOT).resolve(p.preparation.toString());
        Path descriptorPath=directory.resolve(descriptorRow.filename());BasicFileAttributes before=attributes(descriptorPath);
        if(!before.isRegularFile()||before.isSymbolicLink()||before.fileKey()==null||before.size()!=descriptorRow.length||before.size()>PrivateOperationRecord.MAX_CONTROL)throw new IOException("Descriptor bounds changed");
        byte[] descriptor=new byte[(int)before.size()];
        try(InputStream input=Files.newInputStream(descriptorPath,StandardOpenOption.READ,LinkOption.NOFOLLOW_LINKS)){
            new DataInputStream(input).readFully(descriptor);if(input.read()!=-1)throw new IOException("Descriptor grew");
        }same(before,attributes(descriptorPath));
        if(!descriptorRow.hash.equals(PrivateOperationRecord.sha(descriptor)))throw new IOException("Descriptor bytes changed");
        PrivateOperationRecord d=PrivateOperationRecord.decode(descriptor);
        if(d.type!=PrivateOperationRecord.DESCRIPTOR||!Arrays.equals(PrivateOperationRecord.bindingBytes(p.binding),PrivateOperationRecord.bindingBytes(d.binding))
                ||d.artifacts.size()!=p.artifacts.size()-1)throw new IOException("Descriptor table changed");
        for(PrivateOperationRecord.Artifact a:d.artifacts){PrivateOperationRecord.Artifact match=null;for(PrivateOperationRecord.Artifact b:p.artifacts)if(a.id.equals(b.id))match=b;
            if(match==null||a.kind!=match.kind||a.ordinal!=match.ordinal||!a.item.equals(match.item)||a.length!=match.length||!a.hash.equals(match.hash))throw new IOException("Descriptor row changed");}
        HistoryArchiveManifest map;Path mapPath=directory.resolve(mapRow.filename());before=attributes(mapPath);
        try(InputStream input=Files.newInputStream(mapPath,StandardOpenOption.READ,LinkOption.NOFOLLOW_LINKS)){map=HistoryArchiveManifest.read(input);}
        same(before,attributes(mapPath));
        if(!mapRow.hash.equals(fileHash(mapPath,before))||map.serializedBytes!=mapRow.length||!map.bundleId.equals(p.binding.bundle)
                ||map.policyGeneration!=p.binding.generation||map.quotaBytes!=p.binding.quota||map.automaticCount!=p.binding.automaticCount
                ||!HistoryStorage.hex(map.registrationHashBytes()).equals(p.binding.registrationHash)
                ||!HistoryStorage.hex(map.policyHashBytes()).equals(p.binding.policyHash)
                ||p.begin.manifest.count!=map.entries.size()+p.artifacts.size()-2)throw new IOException("Native map binding changed");
        Map<UUID,CapturedSource> sources=new HashMap<>();
        for(HistoryArchiveManifest.Entry e:map.entries){String basename=map.registeredRoles.get(e.role);
            if(!basename.equals(p.binding.roles.get(e.role.wireId-1).basename))throw new IOException("Captured native role changed");
            Path path=root.getParentFile().toPath().resolve(basename).resolve(e.nativeId+"."+e.kind.nativeName);
            if(sources.put(e.archiveId,new CapturedSource(path,e.envelopeBytes,e.envelopeHash()))!=null)throw new IOException("Duplicate source item");}
        for(PrivateOperationRecord.Artifact a:p.artifacts)if(a.kind>=6&&sources.put(a.item,new CapturedSource(directory.resolve(a.filename()),a.length,a.hash))!=null)throw new IOException("Duplicate source item");
        return new CapturedData(p.begin,budget,sources,descriptor);
    }
    /** Fresh operation-data proof for control writers; no native-source dereference. */
    static Ledger completeDataLocked(File nativeRoot)throws IOException {
        Ledger ledger=inspect(nativeRoot,new HistoryControlStore(nativeRoot.getParentFile()).inspect(),new HistoryStorage.Budget(HistoryStorage.METADATA_BUDGET));
        if(!ledger.complete)throw new IOException("Incomplete operation data accounting");return ledger;
    }
    static void unchangedDataLocked(File nativeRoot,Ledger before)throws IOException {
        Ledger after=completeDataLocked(nativeRoot);
        if(before.actualBytes!=after.actualBytes||before.reservedBytes!=after.reservedBytes||before.occupiedEntries!=after.occupiedEntries
                ||before.reservedEntries!=after.reservedEntries||!before.entries.equals(after.entries)||!before.controlIdentities.equals(after.controlIdentities))throw new IOException("Operation data changed during control publication");
    }
    private static void count(Ledger ledger,HistoryStorage.Budget budget,String key,BasicFileAttributes a,Path path,boolean verifiedLength)throws IOException {
        if(ledger.occupiedEntries>=MAX_ENTRIES)throw new IOException("Operation namespace entry bound");budget.charge(1024L+key.length()*2L);ledger.occupiedEntries++;
        String value;
        if(a.isRegularFile()){ledger.actualBytes=add(ledger.actualBytes,a.size());value="file:"+a.size()+":"+a.fileKey()+":"+(verifiedLength?fileHash(path,a):"unvalidated");}
        else value=(a.isDirectory()?"dir:":"unknown:")+a.fileKey();ledger.entries.put(key,value);
    }
    static String fileHash(Path path,BasicFileAttributes before)throws IOException {
        MessageDigest digest=HistoryStorage.digest();long count=0;ByteBuffer buffer=ByteBuffer.allocate(65536);
        try(SeekableByteChannel channel=Files.newByteChannel(path,StandardOpenOption.READ,LinkOption.NOFOLLOW_LINKS)){
            while(true){buffer.clear();int n=channel.read(buffer);if(n<0)break;if(n==0)throw new IOException("Operation read stalled");count=add(count,n);if(count>before.size())throw new IOException("Operation grew during read");digest.update(buffer.array(),0,n);}
        }same(before,attributes(path));if(count!=before.size())throw new IOException("Operation length changed");return HistoryStorage.hex(digest.digest());
    }
    static BasicFileAttributes attributes(Path p)throws IOException{return Files.readAttributes(p,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);}
    static BasicFileAttributes attributesOrAbsent(Path p)throws IOException{try{return attributes(p);}catch(NoSuchFileException absent){return null;}}
    static void same(BasicFileAttributes before,BasicFileAttributes after)throws IOException{if(before.fileKey()==null||!Objects.equals(before.fileKey(),after.fileKey())||before.size()!=after.size()||before.isDirectory()!=after.isDirectory()||before.isRegularFile()!=after.isRegularFile()||!before.lastModifiedTime().equals(after.lastModifiedTime()))throw new IOException("Changed operation identity");}
    static long add(long a,long b)throws IOException{try{return Math.addExact(a,b);}catch(ArithmeticException invalid){throw new IOException("Operation accounting overflow",invalid);}}
}
