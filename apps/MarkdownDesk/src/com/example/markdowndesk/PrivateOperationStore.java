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
        private File originalRoot;
        private BasicFileAttributes originalRootAttributes;
        private HistoryControlStore.Inspection originalControl;
        private HistoryStorage.Budget originalBudget;
        final List<String> diagnostics=new ArrayList<>();
        final Map<String,String> entries=new TreeMap<>();
        final Map<String,String> controlIdentities=new TreeMap<>();
        void invalid(String reason){complete=false;diagnostics.add("operation: "+reason);}
    }
    static Ledger inspect(File nativeRoot,HistoryControlStore.Inspection control,HistoryStorage.Budget budget)throws IOException {
        return inspect(nativeRoot,control,budget,null);
    }
    /** Only the private cancellation proof may account selected stale bounded bytes as retained evidence. */
    private static Ledger inspect(File nativeRoot,HistoryControlStore.Inspection control,HistoryStorage.Budget budget,UUID cancellation)throws IOException {
        // Deliberately bind the existing native monitor; monitorFor(private_operations/UUID) is a different authority.
        Object monitor=HistoryStorage.monitorFor(nativeRoot);if(!Thread.holdsLock(monitor))throw new IOException("Native parent monitor required");
        budget.charge(HistoryStorage.operationEvidenceBytes(nativeRoot));
        budget.charge(1024L+4L*nativeRoot.getPath().length()); // retained original-root provenance before allocation
        Ledger ledger=new Ledger();ledger.originalRoot=HistoryStorage.canonicalRoot(nativeRoot);ledger.originalRootAttributes=attributes(ledger.originalRoot.toPath());ledger.originalControl=control;ledger.originalBudget=budget;ledger.pending=control.operations.pending;ledger.present=control.operations.present;
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
                                else if(f.size()!=wanted.length||!ledger.entries.get(key).endsWith(":"+wanted.hash)){
                                    if(cancellation!=null&&name.equals(cancellation.toString())&&!linkedArtifacts.contains(canonical)){
                                        ledger.reservedBytes=add(ledger.reservedBytes,wanted.length-f.size());ledger.pending=true;
                                    }else ledger.invalid("changed artifact");
                                }
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
        if(ledger.present)ledger.fingerprint=fingerprint(ledger,control);
        return ledger;
    }
    private static boolean canonicalAbsent(Ledger l,HistoryControlStore.Inspection c) {
        return !l.present&&l.complete&&!l.pending&&l.diagnostics.isEmpty()&&l.fingerprint.isEmpty()
            &&l.actualBytes==0&&l.reservedBytes==0&&l.occupiedEntries==0&&l.reservedEntries==0
            &&l.entries.isEmpty()&&l.controlIdentities.isEmpty()&&c.chainComplete&&c.usageComplete
            &&c.diagnostics.isEmpty()&&c.operations.valid&&!c.operations.present&&!c.operations.pending;
    }
    /** One canonical present-ledger serialization, shared with Plan validation and witness output. */
    static void writeFingerprintInput(Ledger l,HistoryControlStore.Inspection c,DataOutputStream out)throws IOException {
        if(!l.present)throw new IOException("Absent ledger has no fingerprint stream");
        out.writeUTF("private-operation-ledger-v1");
        for(Map.Entry<String,String> e:l.controlIdentities.entrySet()){out.writeUTF(e.getKey());out.writeUTF(e.getValue());}
        for(Map.Entry<String,String> e:l.entries.entrySet()){out.writeUTF(e.getKey());out.writeUTF(e.getValue());}
        for(HistoryControlStore.Entry e:c.entries)if(operationEntry(e)){out.writeUTF(e.filename);out.writeLong(e.length);out.writeUTF(e.envelopeHash==null?"":e.envelopeHash);out.writeBoolean(e.stage);}
        out.writeLong(l.actualBytes);out.writeLong(l.reservedBytes);out.writeLong(l.occupiedEntries);out.writeLong(l.reservedEntries);
        out.writeLong(c.controlReservedBytes);out.writeInt(c.controlReservedEntries);out.writeBoolean(l.complete);
    }
    static String fingerprint(Ledger l,HistoryControlStore.Inspection c)throws IOException {
        MessageDigest digest=HistoryStorage.digest();
        try(DataOutputStream out=new DataOutputStream(new java.security.DigestOutputStream(new OutputStream(){public void write(int b){}public void write(byte[] b,int off,int len){}},digest))){writeFingerprintInput(l,c,out);}
        return HistoryStorage.hex(digest.digest());
    }
    static void writeOriginalInput(Ledger l,HistoryControlStore.Inspection c,HistoryStorage.Budget budget,PrivateCapturePlan.HashInput input)throws IOException {
        try {
            DataOutputStream out=input.writer(budget);
            if(l==null||c==null||l.originalControl!=c||l.originalBudget!=budget)throw new IOException("Missing original ledger/control/budget binding");
            if(l.present){writeFingerprintInput(l,c,out);input.seal(l.fingerprint);}
            else {
                if(!canonicalAbsent(l,c))throw new IOException("Unproven canonical ledger absence");
                input.seal(HistoryStorage.hex(HistoryStorage.digest().digest()));
            }
        }catch(IOException|RuntimeException|Error failure){if(input!=null)input.invalidate();throw failure;}
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
    /** Lease-local, one-use cancellation authority. It never proves source/artifact completeness. */
    static final class ColdCancellation {
        final File root;final PrivateOperationRecord preparation;final byte[] begin,close;
        private final long refreshBytes,controlBytes,controlEntries;
        private final Ledger original;
        private final Map<String,String> controls=new TreeMap<>();
        private final Map<Path,BasicFileAttributes> physical=new LinkedHashMap<>();
        private final Map<String,byte[]> own=new TreeMap<>();
        private final Map<String,BasicFileAttributes> ownPhysical=new TreeMap<>();
        private final Set<String> mutable=new HashSet<>();
        private HistoryOperations.Claim claim;private List<HistoryOperations.Claim> audits=Collections.emptyList();private boolean used;
        private ColdCancellation(File root,UUID id,HistoryStorage.Budget budget,boolean promote,List<File> roles,long bootstrap)throws IOException {
            HistoryStorage.requireMutation(root);HistoryOperations.rejectParticipant();
            if(id==null||budget==null)throw new IOException("Missing cold cancellation binding");
            this.root=HistoryStorage.canonicalRoot(root);
            budget.charge((4L<<20)+65536); // one bounded control decode and retained DTO workspace
            HistoryControlStore.Inspection c=new HistoryControlStore(root.getParentFile()).inspect();
            PrivateOperationRecord selected=null;
            for(PrivateOperationRecord p:c.operations.preparations)if(p.preparation.equals(id))selected=p;
            if(selected==null)throw new IOException("Missing exact typed PREPARE; partial evidence retained");
            preparation=selected;begin=HistoryPinRecord.encode(selected.begin);close=HistoryPinRecord.close(selected.begin,HistoryPinRecord.Outcome.CANCELLED);
            if(begin.length!=204||close.length!=126)throw new IOException("Noncanonical cancellation template");
            HistoryOperations.requireColdCancellationLocked(root,selected.begin,null);
            HistoryOperations.requireColdCancellationAuditsLocked(root,c,selected.begin,Collections.emptyList());
            HistoryStorage.siblings(roles.toArray(new File[0]));
            if(c.registration==null||c.registration.bootstrapQuotaBytes!=bootstrap||roles.size()!=4)throw new IOException("Foreign cold cancellation bundle");
            boolean marker=false;
            for(HistoryControlStore.Entry e:c.entries)if(HistoryControlStore.ENROLLMENT_NAME.equals(e.filename)&&c.registration.envelopeHash.equals(e.envelopeHash))marker=true;
            if(!marker)throw new IOException("Cold cancellation enrollment missing");
            for(int i=0;i<roles.size();i++){
                if(!c.registration.roles.get(i).basename.equals(roles.get(i).getName()))throw new IOException("Cold cancellation role mismatch");
                physical.put(roles.get(i).toPath(),attributes(roles.get(i).toPath()));
            }
            HistoryPins.requireRetained(root,c);
            original=inspect(root,c,budget,selected.preparation);
            if(!original.complete)throw new IOException("Incomplete cancellation accounting: "+original.diagnostics);
            // Admit all retained anchors and the reusable refresh BEFORE retaining baseline maps.
            controlBytes=add(c.actualBytes,c.controlReservedBytes);controlEntries=add(c.actualEntries,c.controlReservedEntries);
            budget.charge(4096L+2048L*(original.entries.size()+c.entries.size()+roles.size()+2L)+2048L*6);
            long artifactCount=0;for(PrivateOperationRecord p:c.operations.preparations)artifactCount=add(artifactCount,p.artifacts.size());
            refreshBytes=add((4L<<20)+65536,add(HistoryStorage.operationEvidenceBytes(root),
                    add(4096L+4096L*original.entries.size(),4096L*c.entries.size()+4096L*c.operations.preparations.size()+512L*artifactCount)));
            budget.charge(refreshBytes);
            String reserve=selected.name(),bn=selected.begin.name(),cn="pin-"+selected.operation+".close";
            mutable.add(reserve);mutable.add(reserve+".stage");mutable.add(bn);mutable.add(bn+".stage");mutable.add(cn);mutable.add(cn+".stage");
            for(HistoryControlStore.Entry e:c.entries){
                if(e.filename==null)throw new IOException("Unknown control evidence retained");
                Path path=root.getParentFile().toPath().resolve(HistoryControlStore.ENROLLMENT_NAME.equals(e.filename)?"":HistoryControlStore.ROOT_NAME).resolve(e.filename);
                if(mutable.contains(e.filename)){
                    byte[] expected=e.filename.startsWith(reserve)?selected.encoded():e.filename.startsWith(bn)?begin:close;
                    byte[] bytes=controlBytes(path,expected.length);
                    if(bytes.length>expected.length||!Arrays.equals(bytes,Arrays.copyOf(expected,bytes.length))
                            ||!e.stage&&bytes.length!=expected.length)throw new IOException("Conflicting cancellation control retained");
                    if(e.stage&&!e.filename.startsWith(cn)&&(!promote||bytes.length!=expected.length))throw new IOException("PREPARE/BEGIN stage requires explicit complete typed promotion");
                    own.put(e.filename,bytes);ownPhysical.put(e.filename,attributes(path));
                }else{
                    if(e.stage)throw new IOException("Foreign staged control excludes cold cancellation");
                    controls.put(e.filename,e.length+":"+e.envelopeHash);physical.put(path,attributes(path));
                }
            }
            if(!own.containsKey(reserve)&&!own.containsKey(reserve+".stage"))throw new IOException("Missing persisted PREPARE");
            for(String key:original.entries.keySet()){
                Path path=root.getParentFile().toPath().resolve(ROOT);
                if(!key.equals(ROOT))path=path.resolve(key);
                physical.put(path,attributes(path));
            }
            for(File directory:Arrays.asList(root.getParentFile(),new File(root.getParentFile(),HistoryControlStore.ROOT_NAME)))physical.put(directory.toPath(),attributes(directory.toPath()));
            validate();
        }
        private HistoryControlStore.Inspection validate()throws IOException {
            HistoryStorage.requireMutation(root);HistoryOperations.requireColdCancellationLocked(root,preparation.begin,claim);
            for(HistoryOperations.Claim audit:audits)audit.validate();
            HistoryStorage.Budget scratch=new HistoryStorage.Budget(refreshBytes);scratch.charge((4L<<20)+65536);
            HistoryControlStore.Inspection c=new HistoryControlStore(root.getParentFile()).inspect();
            HistoryPins.requireRetained(root,c);
            HistoryOperations.requireColdCancellationAuditsLocked(root,c,preparation.begin,audits);
            if(!c.chainComplete||!c.usageComplete||c.registration==null||c.latest==null||!c.operations.valid||!c.pins.valid||c.hasMaintenance()
                    ||!c.stages.isEmpty()||c.operations.links.containsKey(preparation.preparation)
                    ||c.pins.active!=null&&!c.pins.active.operation.equals(preparation.operation)
                    ||c.diagnostics.stream().anyMatch(d->!d.equals("control: retained pin stage"))
                    ||c.actualBytes>HistoryControlStore.MAX_BYTES-c.controlReservedBytes||c.actualEntries>HistoryControlStore.MAX_ENTRIES-c.controlReservedEntries
                    ||add(c.actualBytes,c.controlReservedBytes)!=controlBytes||add(c.actualEntries,c.controlReservedEntries)!=controlEntries)
                throw new IOException("Cold cancellation authority/funding changed");
            Map<String,String> old=new TreeMap<>();Set<String> found=new HashSet<>();
            for(HistoryControlStore.Entry e:c.entries){
                if(mutable.contains(e.filename)){
                    byte[] expected=own.get(e.filename);Path path=controlPath(e.filename);
                    if(expected==null||!Arrays.equals(expected,controlBytes(path,expected.length)))throw new IOException("Unexpected own cancellation transition");
                    physicalSame(ownPhysical.get(e.filename),attributes(path));found.add(e.filename);
                }else old.put(e.filename,e.length+":"+e.envelopeHash);
            }
            if(!found.equals(own.keySet())||!old.equals(controls))throw new IOException("Cold cancellation immutable control changed");
            for(Map.Entry<Path,BasicFileAttributes> e:physical.entrySet()){
                BasicFileAttributes now=attributes(e.getKey()),before=e.getValue();
                if(before.isDirectory()){
                    if(!now.isDirectory()||now.isSymbolicLink()||before.fileKey()==null||!Objects.equals(before.fileKey(),now.fileKey())||!before.creationTime().equals(now.creationTime()))throw new IOException("Cold cancellation role/namespace changed");
                }else physicalSame(before,now);
            }
            Ledger l=inspect(root,c,scratch,preparation.preparation);
            if(!l.complete||original.actualBytes!=l.actualBytes||original.reservedBytes!=l.reservedBytes
                    ||original.occupiedEntries!=l.occupiedEntries||original.reservedEntries!=l.reservedEntries||!original.entries.equals(l.entries))
                throw new IOException("Cold cancellation old data changed");
            // PREPARE promotion changes its name, never its exact envelope or identity.
            Map<String,String> a=new TreeMap<>(original.controlIdentities),b=new TreeMap<>(l.controlIdentities);
            a.remove(preparation.name());a.remove(preparation.name()+".stage");b.remove(preparation.name());b.remove(preparation.name()+".stage");
            if(!a.equals(b))throw new IOException("Cold cancellation old operation identity changed");
            return c;
        }
        private static byte[] controlBytes(Path path,int maximum)throws IOException {
            BasicFileAttributes before=attributes(path);
            if(!before.isRegularFile()||before.isSymbolicLink()||before.size()<0||before.size()>maximum)throw new IOException("Cold control read bounds/type changed");
            ByteBuffer bytes=ByteBuffer.allocate((int)before.size());
            try(SeekableByteChannel input=Files.newByteChannel(path,StandardOpenOption.READ,LinkOption.NOFOLLOW_LINKS)){
                while(bytes.hasRemaining())if(input.read(bytes)<=0)throw new IOException("Cold control truncated");
                if(input.read(ByteBuffer.allocate(1))!=-1)throw new IOException("Cold control grew");
            }
            physicalSame(before,attributes(path));return bytes.array();
        }
        private static void physicalSame(BasicFileAttributes before,BasicFileAttributes after)throws IOException {
            same(before,after);if(!before.creationTime().equals(after.creationTime()))throw new IOException("Cold cancellation physical evidence replaced");
        }
        private Path controlPath(String name){return root.getParentFile().toPath().resolve(HistoryControlStore.ROOT_NAME).resolve(name);}
        void consumeLocked()throws IOException {if(used)throw new IOException("Cold cancellation capability consumed");used=true;validate();}
        void claimLocked(HistoryOperations operations)throws IOException {
            validate();audits=operations.claimCancellationAuditsLocked(preparation.begin);
            claim=operations.claimColdCancellationLocked(preparation.begin);validate();
        }
        void finishLocked()throws IOException {validate();if(claim==null)throw new IOException("Missing cold terminal claim");claim.finish();for(HistoryOperations.Claim audit:audits)audit.finish();}
        void releaseLocked(){if(claim!=null)claim.close();for(HistoryOperations.Claim audit:audits)audit.close();}
        boolean present(String name){return own.containsKey(name);}
        byte[] bytes(String name){return own.get(name);}
        void stageLocked(String name,byte[] bytes,BasicFileAttributes anchor)throws IOException {
            if(own.containsKey(name))throw new IOException("Existing cancellation stage cannot be rewritten");
            own.put(name,bytes);ownPhysical.put(name,anchor);validate();
        }
        void appendedLocked(String name,byte[] bytes)throws IOException {
            BasicFileAttributes before=ownPhysical.get(name),now=attributes(controlPath(name));
            if(before.fileKey()==null||!Objects.equals(before.fileKey(),now.fileKey())||!before.creationTime().equals(now.creationTime()))throw new IOException("Cancellation CLOSE prefix inode changed");
            own.put(name,bytes);ownPhysical.put(name,now);validate();
        }
        void promoteLocked(String name,PreparationSources.Publication move)throws IOException {
            validate();String stage=name+".stage";byte[] bytes=own.remove(stage);BasicFileAttributes anchor=ownPhysical.remove(stage);
            if(bytes==null||own.containsKey(name))throw new IOException("Invalid cancellation promotion");
            own.put(name,bytes);ownPhysical.put(name,anchor);callbackLocked(move);
        }
        void callbackLocked(PreparationSources.Publication callback)throws IOException {
            try{HistoryOperations.cancellationCallbackLocked(callback);}
            catch(IOException|RuntimeException|Error failure){
                try{validate();}catch(IOException|RuntimeException|Error changed){failure.addSuppressed(changed);}throw failure;
            }
            validate();
        }
        void checkLocked()throws IOException {validate();}
    }
    static ColdCancellation coldCancellationLocked(File root,UUID preparation,HistoryStorage.Budget budget,boolean promote,List<File> roles,long bootstrap)throws IOException {
        return new ColdCancellation(root,preparation,budget,promote,roles,bootstrap);
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
    /** Shared future receipt/quarantine root. First producer supports proved absence only. */
    static final String RETAINED_ROOT="private_retained";
    private static final UUID PORTABLE_ZERO=new UUID(0,0);
    private static final String PORTABLE_EMPTY="e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";
    private static final String PORTABLE_NIL=String.join("",Collections.nCopies(64,"0"));

    /** Original physical source inventory only; no publication, pin or terminal capability. */
    static PortableBaseline capturePortableBaselineLocked(File nativeRoot,HistoryControlStore.Inspection control,
            Ledger ledger,PreparationSources owner,UUID preparation,HistoryStorage.Budget budget)throws IOException {
        HistoryStorage.requireMutation(nativeRoot);PreparationSources.rejectParticipant();HistoryOperations.rejectParticipant();
        File root=HistoryStorage.canonicalRoot(nativeRoot);
        if(control==null||ledger==null||owner==null||budget==null||ledger.originalControl!=control
                ||ledger.originalBudget!=budget||!root.equals(ledger.originalRoot))throw new IOException("Foreign original portable baseline");
        owner.requireIdleBindingLocked(root,preparation,budget);
        BasicFileAttributes originalRoot=ledger.originalRootAttributes,currentRoot=attributes(root.toPath());
        if(originalRoot==null||!originalRoot.isDirectory()||!currentRoot.isDirectory()||currentRoot.isSymbolicLink()
                ||originalRoot.fileKey()==null||!Objects.equals(originalRoot.fileKey(),currentRoot.fileKey())
                ||!originalRoot.creationTime().equals(currentRoot.creationTime()))throw new IOException("Replaced original portable root");
        if(control.state!=HistoryControlStore.State.VALIDATED||!control.chainComplete||!control.usageComplete
                ||!control.diagnostics.isEmpty()||!control.stages.isEmpty()||control.hasMaintenance()
                ||!control.pins.valid||control.pins.active!=null||!control.pins.stages.isEmpty()
                ||!control.operations.valid||!control.operations.ready||!ledger.complete||!ledger.diagnostics.isEmpty()
                ||control.registration==null||control.latest==null)throw new IOException("Incomplete original portable baseline");
        if(ledger.pending) {
            if(attributesOrAbsent(root.getParentFile().toPath().resolve(ROOT))!=null)
                requireHistoricalCaptureOwnerLocked(root,control,ledger,owner,preparation,budget);
            else if(hasHistoricalOwnerLocked(root,control))throw new IOException("Historical source owner remains live");
            // An absent funded root is classified below from actual committed cancelled controls.
            // This additive read inventory does not relax generic namespace/admission predicates.
        }
        budget.charge(256L<<10); // one reusable64KiB hash buffer, control/reference tables and scan temporaries
        PortableBaseline result=new PortableBaseline(root,control,ledger,owner,preparation,budget);
        result.capture();return result;
    }

    static final class PortableBaseline {
        private final File root,parent;private final HistoryControlStore.Inspection control;private final Ledger ledger;
        private final PreparationSources owner;private final UUID preparation;private final HistoryStorage.Budget budget;
        private final List<BaselineNode> nodes=new ArrayList<>();
        private final Map<String,BaselineNode> controls=new HashMap<>();
        private final Map<String,String> operationEntries=new TreeMap<>(),controlIdentities=new TreeMap<>();
        private final Map<Path,BasicFileAttributes> directories=new LinkedHashMap<>();
        private List<PrivateOperationEvidence.Row> rows;private boolean originalOperationRootAbsent,sourceOnlyRootAbsence;
        private final byte[] scratch=new byte[65536];
        private String controlHash,ledgerHash;private volatile BaselineCursor active;private volatile boolean failed;
        private PortableBaseline(File root,HistoryControlStore.Inspection c,Ledger l,PreparationSources owner,
                UUID preparation,HistoryStorage.Budget budget) {
            this.root=root;parent=root.getParentFile();control=c;ledger=l;this.owner=owner;this.preparation=preparation;this.budget=budget;
        }
        List<PrivateOperationEvidence.Row> rows(){return rows;}
        /** Source evidence only; generic Graph/capture admission still refuses this prefix. */
        boolean sourceOnlyMissingRoot(){return sourceOnlyRootAbsence;}
        private void capture()throws IOException {
            retainedAbsent();directory(root.toPath());directory(parent.toPath());directory(parent.toPath().resolve(HistoryControlStore.ROOT_NAME));
            List<PrivateOperationRecord> prepares=new ArrayList<>();Map<UUID,UUID> pinOwners=new HashMap<>();
            for(HistoryControlStore.Entry e:control.entries)if(e.operation!=null&&e.operation.type==PrivateOperationRecord.PREPARE){
                if(e.operation.preparation.equals(preparation)||pinOwners.put(e.operation.operation,e.operation.preparation)!=null)throw new IOException("Conflicting portable baseline preparation");
                prepares.add(e.operation);
            }
            Map<UUID,HistoryPinRecord> begins=new HashMap<>(),closes=new HashMap<>();
            for(HistoryControlStore.Entry e:control.entries){
                if(e.filename==null||e.stage||e.envelopeHash==null)throw new IOException("Unclassified portable control");
                UUID own=PORTABLE_ZERO,node=PORTABLE_ZERO;int role,kind;
                if(e.record!=null){role=4;kind=HistoryControlStore.ENROLLMENT_NAME.equals(e.filename)?1:e.record instanceof HistoryControlStore.Registration?2:3;}
                else if(e.operation!=null){role=1;kind=e.operation.type;own=e.operation.preparation;node=own;}
                else if(e.pin!=null){
                    role=1;kind=e.pin.begin?3:4;node=e.pin.operation;own=pinOwners.getOrDefault(node,PORTABLE_ZERO);
                    if((e.pin.begin?begins:closes).put(node,e.pin)!=null)throw new IOException("Duplicate original pin");
                    if(PORTABLE_ZERO.equals(own)&&e.pin.begin&&e.pin.manifest.domain!=HistoryPinRecord.Domain.NATIVE_HISTORY_V1)throw new IOException("Unowned nonlegacy pin");
                }else throw new IOException("Unknown portable control kind");
                Path path=kind==1&&role==4?parent.toPath().resolve(e.filename):parent.toPath().resolve(HistoryControlStore.ROOT_NAME).resolve(e.filename);
                BaselineNode n=file(role,own,node,kind,e.filename,e.length,e.envelopeHash,path,false,false);
                if(controls.put(e.filename,n)!=null)throw new IOException("Duplicate control basename");
                if(operationEntry(e)){BasicFileAttributes a=n.anchor;controlIdentities.put(e.filename,a.fileKey()+":"+a.creationTime()+":"+a.lastModifiedTime()+":"+a.size());}
            }
            if(begins.size()!=closes.size())throw new IOException("Unmatched original pin");
            for(HistoryPinRecord begin:begins.values()){
                HistoryPinRecord close=closes.get(begin.operation);
                if(close==null||!HistoryPinRecord.matches(begin,close))throw new IOException("Unclosed original pin");
            }
            Path operationRoot=parent.toPath().resolve(ROOT);BasicFileAttributes rootAttributes=attributesOrAbsent(operationRoot);
            originalOperationRootAbsent=rootAttributes==null;sourceOnlyRootAbsence=originalOperationRootAbsent&&!prepares.isEmpty();boolean rootFunded=false;
            if(rootAttributes!=null||!prepares.isEmpty())addDirectory(operationRoot,PORTABLE_ZERO,ROOT,rootAttributes);
            for(PrivateOperationRecord p:prepares){
                HistoryPinRecord begin=begins.get(p.operation),close=closes.get(p.operation);
                if(begin==null||close==null||!p.beginHash.equals(begin.envelopeHash))throw new IOException("Original preparation pin mismatch");
                PrivateOperationRecord link=PrivateOperationRecord.link(p);BaselineNode presentLink=controls.get(link.name());
                boolean cancelled=presentLink==null&&close.outcome==HistoryPinRecord.Outcome.CANCELLED;
                rootFunded|=(p.directoryMask&1)!=0;
                if(sourceOnlyRootAbsence&&!cancelled)throw new IOException("Absent funded root requires every original preparation cancelled");
                if(presentLink==null){if(!cancelled)throw new IOException("Unlinked noncancelled preparation");missingControl(p.preparation,link);}
                else if(!presentLink.row.actualHash.equals(link.envelopeHash))throw new IOException("Original LINK mismatch");
                Path directory=operationRoot.resolve(p.preparation.toString());addDirectory(directory,p.preparation,p.preparation.toString(),attributesOrAbsent(directory));
                for(PrivateOperationRecord.Artifact a:p.artifacts){
                    Path finalPath=directory.resolve(a.filename()),stagePath=directory.resolve(a.filename()+".stage");
                    BasicFileAttributes committed=attributesOrAbsent(finalPath),stage=attributesOrAbsent(stagePath);
                    if(committed!=null&&stage!=null||stage!=null&&!cancelled)throw new IOException("Conflicting original artifact");
                    file(2,p.preparation,a.id,a.kind,a.filename()+(stage==null?"":".stage"),a.length,a.hash,
                        stage==null?finalPath:stagePath,stage!=null,cancelled);
                }
            }
            if(sourceOnlyRootAbsence&&!rootFunded)throw new IOException("Absent original root has no typed slot funding");
            enumerateControl();enumerateOperations(operationRoot,rootAttributes);
            if(!operationEntries.equals(ledger.entries)||!controlIdentities.equals(ledger.controlIdentities))throw new IOException("Original physical ledger identities changed");
            reconcile();controlHash=PrivateCapturePlan.initialControlHash(control);ledgerHash=PrivateCapturePlan.initialLedgerHash(ledger);
            if(ledger.present&&!ledger.fingerprint.equals(fingerprint(ledger,control)))throw new IOException("Original ledger fingerprint changed");
            nodes.sort((a,b)->{int c=Integer.compare(a.row.role,b.row.role);if(c==0)c=PrivateBundleInventory.compare(a.row.ownerPreparation,b.row.ownerPreparation);return c!=0?c:a.row.name.compareTo(b.row.name);});
            budget.charge(256L+16L*nodes.size());List<PrivateOperationEvidence.Row> result=new ArrayList<>(nodes.size());
            for(BaselineNode n:nodes)result.add(n.row);rows=Collections.unmodifiableList(result);
        }
        private void charge(String name)throws IOException {budget.charge(2048L+4L*(parent.getPath().length()+name.length()+128));}
        private BaselineNode file(int role,UUID own,UUID node,int kind,String name,long declared,String expected,Path path,
                boolean stage,boolean missingAllowed)throws IOException {
            charge(name);BasicFileAttributes a=attributesOrAbsent(path);int state;
            long actual=0,future;String actualHash=PORTABLE_EMPTY;
            if(a==null){if(!missingAllowed)throw new IOException("Missing original file");state=0;}
            else{
                if(!a.isRegularFile()||a.isSymbolicLink()||a.fileKey()==null||a.size()>declared||!stage&&a.size()!=declared)throw new IOException("Original file shape/length");
                actual=a.size();actualHash=hashFile(path,a,scratch);state=stage?3:2;
                if(!stage&&!expected.equals(actualHash))throw new IOException("Original file commitment changed");
            }
            future=declared-actual;
            PrivateOperationEvidence.Row row=new PrivateOperationEvidence.Row(role,own,node,kind,state,name,declared,expected,actual,actualHash,future,a==null?1:0,0);
            BaselineNode n=new BaselineNode(row,path,a);nodes.add(n);
            if(role==2&&a!=null)operationEntries.put(own+"/"+name,"file:"+a.size()+":"+a.fileKey()+":"+actualHash);
            return n;
        }
        private void missingControl(UUID own,PrivateOperationRecord link)throws IOException {
            charge(link.name());Path path=parent.toPath().resolve(HistoryControlStore.ROOT_NAME).resolve(link.name());
            if(attributesOrAbsent(path)!=null||attributesOrAbsent(path.resolveSibling(link.name()+".stage"))!=null)throw new IOException("Conflicting original missing LINK");
            nodes.add(new BaselineNode(new PrivateOperationEvidence.Row(1,own,own,2,0,link.name(),link.encoded().length,link.envelopeHash,0,PORTABLE_EMPTY,link.encoded().length,1,0),path,null));
        }
        private void addDirectory(Path path,UUID own,String name,BasicFileAttributes a)throws IOException {
            charge(name);
            if(a!=null){if(!a.isDirectory()||a.isSymbolicLink()||a.fileKey()==null)throw new IOException("Original operation directory shape");directories.put(path,a);operationEntries.put(name,"dir:"+a.fileKey());}
            nodes.add(new BaselineNode(new PrivateOperationEvidence.Row(3,own,own,0,a==null?0:1,name,0,PORTABLE_NIL,0,PORTABLE_EMPTY,0,a==null?1:0,0),path,a));
        }
        private void directory(Path path)throws IOException {BasicFileAttributes a=attributes(path);if(!a.isDirectory()||a.isSymbolicLink()||a.fileKey()==null)throw new IOException("Invalid original parent directory");directories.put(path,a);}
        private void retainedAbsent()throws IOException {if(attributesOrAbsent(parent.toPath().resolve(RETAINED_ROOT))!=null)throw new IOException("Retained namespace requires actual receipt admission");}
        private void enumerateControl()throws IOException {
            int count=0;
            try(DirectoryStream<Path> files=Files.newDirectoryStream(parent.toPath().resolve(HistoryControlStore.ROOT_NAME))){
                for(Path path:files){String name=path.getFileName().toString();BaselineNode n=controls.get(name);
                    if(n==null||n.path.getParent().equals(parent.toPath()))throw new IOException("Unknown original control member");count++;}
            }catch(DirectoryIteratorException e){throw new IOException("Original control enumeration failed",e);}
            if(count!=controls.size()-1)throw new IOException("Original control membership changed");
            if(attributesOrAbsent(parent.toPath().resolve(HistoryControlStore.ENROLLMENT_STAGE))!=null)throw new IOException("Staged original enrollment");
        }
        private void enumerateOperations(Path operationRoot,BasicFileAttributes rootAttributes)throws IOException {
            if(rootAttributes==null)return;
            Set<String> actual=new HashSet<>();
            try(DirectoryStream<Path> dirs=Files.newDirectoryStream(operationRoot)){
                for(Path dir:dirs){String name=dir.getFileName().toString();if(!operationEntries.containsKey(name)||!directories.containsKey(dir))throw new IOException("Unknown original operation directory");actual.add(name);
                    try(DirectoryStream<Path> files=Files.newDirectoryStream(dir)){for(Path file:files){String key=name+"/"+file.getFileName();if(!operationEntries.containsKey(key))throw new IOException("Unknown original operation artifact");actual.add(key);}}
                }
            }catch(DirectoryIteratorException e){throw new IOException("Original operation enumeration failed",e);}
            if(actual.size()!=operationEntries.size()-1)throw new IOException("Original operation membership changed");
        }
        private void reconcile()throws IOException {
            long data=0,dataFuture=0,dataSlots=0,dataFutureSlots=0,controlBytes=0,controlFuture=0,controlSlots=0,controlFutureSlots=0;
            for(BaselineNode n:nodes){PrivateOperationEvidence.Row r=n.row;
                if(r.role==2||r.role==3){data=add(data,r.actualLength);dataFuture=add(dataFuture,r.futureBytes);dataSlots+=r.state==0?0:1;dataFutureSlots+=r.futureSlots;}
                else{controlBytes=add(controlBytes,r.actualLength);controlFuture=add(controlFuture,r.futureBytes);controlSlots+=r.state==0?0:1;controlFutureSlots+=r.futureSlots;}}
            if(data!=ledger.actualBytes||dataFuture!=ledger.reservedBytes||dataSlots!=ledger.occupiedEntries||dataFutureSlots!=ledger.reservedEntries
                    ||controlBytes!=control.actualBytes||controlFuture!=control.controlReservedBytes||controlSlots!=control.actualEntries||controlFutureSlots!=control.controlReservedEntries)
                throw new IOException("Original portable accounting does not reconcile");
        }
        synchronized void requireOriginalLocked(HistoryControlStore.Inspection expectedControl,Ledger expectedLedger,PreparationSources expectedOwner,
                HistoryStorage.Budget expectedBudget)throws IOException {
            HistoryStorage.requireMutation(root);
            if(failed||expectedControl!=control||expectedLedger!=ledger||expectedOwner!=owner||expectedBudget!=budget||active!=null)throw new IOException("Foreign/busy original portable baseline");
            owner.requireIdleBindingLocked(root,preparation,budget);retainedAbsent();
            if(!controlHash.equals(PrivateCapturePlan.initialControlHash(control))||!ledgerHash.equals(PrivateCapturePlan.initialLedgerHash(ledger)))throw new IOException("Original portable bindings changed");
            if(!operationEntries.equals(ledger.entries)||!controlIdentities.equals(ledger.controlIdentities)
                    ||ledger.present&&!ledger.fingerprint.equals(fingerprint(ledger,control)))throw new IOException("Original ledger DTO changed");
            reconcile();enumerateControl();enumerateOperations(parent.toPath().resolve(ROOT),attributesOrAbsent(parent.toPath().resolve(ROOT)));
            for(BaselineNode n:nodes){n.verify();if(n.anchor!=null&&n.anchor.isRegularFile()
                    &&!n.row.actualHash.equals(hashFile(n.path,n.anchor,scratch)))throw new IOException("Original raw bytes changed");}
            verifyDirectories();
        }
        private void verifyDirectories()throws IOException {
            retainedAbsent();
            if(originalOperationRootAbsent&&attributesOrAbsent(parent.toPath().resolve(ROOT))!=null)throw new IOException("Originally absent operation root appeared");
            for(Map.Entry<Path,BasicFileAttributes> e:directories.entrySet()){
                BasicFileAttributes a=attributes(e.getKey());if(!a.isDirectory()||a.isSymbolicLink()||!Objects.equals(a.fileKey(),e.getValue().fileKey())
                        ||!a.creationTime().equals(e.getValue().creationTime()))throw new IOException("Original source directory replaced");}
        }
        /** One normal Sources admission per complete iteration, including >64 baseline rows. */
        synchronized BaselineCursor open()throws IOException {
            if(failed||active!=null)throw new IOException("Original portable cursor busy/failed");
            budget.charge(8192);BaselineCursor cursor=new BaselineCursor();active=cursor;
            try{cursor.reader=owner.open(()->cursor.raw);return cursor;}
            catch(IOException|RuntimeException|Error e){failed=true;throw e;}
        }
        final class BaselineCursor implements AutoCloseable {
            private final Thread thread=Thread.currentThread();private final RawBaselineStream raw=new RawBaselineStream();
            private PreparationSources.Reader reader;private int at=-1;private boolean rowOpened,closed;
            private RowInput row;
            private void ready()throws IOException {if(Thread.currentThread()!=thread||closed||failed)throw new IOException("Original portable cursor unavailable");}
            PrivateOperationEvidence.Row next()throws IOException {
                ready();if(row!=null||at>=0&&at<nodes.size()&&!rowOpened)throw new IOException("Original row must reach EOF and close before advance");
                rowOpened=false;at++;return at<nodes.size()?nodes.get(at).row:null;
            }
            InputStream openRaw()throws IOException {return openRaw(at>=0&&at<nodes.size()?nodes.get(at).row:null);}
            InputStream openRaw(PrivateOperationEvidence.Row expected)throws IOException {
                ready();if(at<0||at>=nodes.size()||rowOpened||expected!=nodes.get(at).row)throw new IOException("Foreign or already opened original row");
                budget.charge(512);raw.select(nodes.get(at));row=new RowInput(nodes.get(at).row.actualLength);rowOpened=true;return row;
            }
            public void close()throws IOException {
                if(Thread.currentThread()!=thread)throw new IOException("Original cursor thread mismatch");if(closed)return;
                // Do not drop a failed row/channel: Reader remains registered until actual closure succeeds.
                reader.close();closed=true;synchronized(PortableBaseline.this){active=null;}
            }
            private final class RowInput extends InputStream {
                long left;boolean done;private final byte[] one=new byte[1];
                RowInput(long length){left=length;}
                public int read()throws IOException {int n=read(one,0,1);return n<0?-1:one[0]&255;}
                public int read(byte[] b,int off,int len)throws IOException {
                    ready();if(done)throw new IOException("Original row closed");if(len==0)return 0;if(left==0)return -1;
                    try{int n=reader.read(b,off,(int)Math.min(Math.min(left,len),65536));if(n<=0)throw new IOException("Truncated/stalled original raw source");left-=n;return n;}
                    catch(IOException|RuntimeException|Error e){failed=true;throw e;}
                }
                public void close()throws IOException {
                    if(done)return;if(Thread.currentThread()!=thread)throw new IOException("Original row thread mismatch");
                    if(left!=0)throw new IOException("Original raw row not fully consumed");
                    try{owner.consumerCallback(()->raw.finish());done=true;row=null;}
                    catch(IOException|RuntimeException|Error e){failed=true;throw e;}
                }
            }
            private final class RawBaselineStream extends InputStream {
                private BaselineNode selected;private InputStream input;private MessageDigest hash;private long read;
                void select(BaselineNode n)throws IOException {if(selected!=null)throw new IOException("Previous physical row remains open");selected=n;hash=HistoryStorage.digest();read=0;}
                public int read()throws IOException {throw new IOException("Bounded original bulk source only");}
                public int read(byte[] b,int off,int len)throws IOException {
                    if(selected==null)throw new IOException("No original physical row");
                    if(input==null){verifyDirectories();selected.verify();input=Files.newInputStream(selected.path,StandardOpenOption.READ,LinkOption.NOFOLLOW_LINKS);}
                    int n=input.read(b,off,len);if(n>0){hash.update(b,off,n);read+=n;}return n;
                }
                void finish()throws IOException {
                    if(selected==null)throw new IOException("No original raw row");
                    if(read!=selected.row.actualLength)throw new IOException("Original raw row incomplete");
                    if(input!=null){input.close();input=null;}
                    selected.verify();verifyDirectories();
                    if(!selected.row.actualHash.equals(HistoryStorage.hex(hash.digest())))throw new IOException("Original physical raw hash changed");
                    selected=null;hash=null;
                }
                public void close()throws IOException {
                    if(input!=null){input.close();input=null;}
                    if(selected!=null){selected.verify();verifyDirectories();}
                    // An incomplete row can be abandoned only by closing the entire owned cursor.
                    selected=null;hash=null;
                }
            }
        }
    }
    private static final class BaselineNode {
        final PrivateOperationEvidence.Row row;final Path path;final BasicFileAttributes anchor;
        BaselineNode(PrivateOperationEvidence.Row row,Path path,BasicFileAttributes anchor){this.row=row;this.path=path;this.anchor=anchor;}
        void verify()throws IOException {
            BasicFileAttributes now=attributesOrAbsent(path);
            if(anchor==null){if(now!=null)throw new IOException("Originally absent portable source appeared");}
            else{if(now==null||now.isSymbolicLink())throw new IOException("Original portable source disappeared");
                if(anchor.isDirectory()){
                    if(!now.isDirectory()||!Objects.equals(anchor.fileKey(),now.fileKey()))throw new IOException("Original portable directory replaced");
                }else same(anchor,now);
                if(!anchor.creationTime().equals(now.creationTime()))throw new IOException("Original portable source identity changed");}
        }
    }
    private static String hashFile(Path path,BasicFileAttributes anchor,byte[] scratch)throws IOException {
        MessageDigest hash=HistoryStorage.digest();long total=0;
        try(InputStream input=Files.newInputStream(path,StandardOpenOption.READ,LinkOption.NOFOLLOW_LINKS)){
            int n;while((n=input.read(scratch))!=-1){if(n==0)throw new IOException("Original raw source stalled");total=add(total,n);if(total>anchor.size())throw new IOException("Original raw source grew");hash.update(scratch,0,n);}
        }same(anchor,attributes(path));if(total!=anchor.size())throw new IOException("Original raw source truncated");return HistoryStorage.hex(hash.digest());
    }

}
