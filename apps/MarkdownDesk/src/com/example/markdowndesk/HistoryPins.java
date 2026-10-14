package com.example.markdowndesk;

import java.io.*;
import java.nio.*;
import java.nio.channels.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.util.*;
import static com.example.markdowndesk.HistoryPinRecord.*;

/** Runtime-free durable pruning freeze. A receipt releases a pin, never asserts export success. */
final class HistoryPins {
    static final class ManifestIdentity {
        final Manifest value;
        private ManifestIdentity(Manifest value){this.value=value;}
        /** Count/domain are opaque caller metadata; this does not parse an export format. */
        static ManifestIdentity read(Domain domain,long count,InputStream input)throws IOException {
            if(input==null)throw new IOException("Original manifest required");
            MessageDigest hash=HistoryStorage.digest();byte[] buffer=new byte[8192];long length=0;int n;
            while((n=input.read(buffer))!=-1){if(n==0)throw new IOException("Manifest read made no progress");length=Math.addExact(length,n);if(length>MAX_MANIFEST_BYTES)throw new IOException("Manifest byte limit");hash.update(buffer,0,n);}
            return new ManifestIdentity(new Manifest(domain,count,length,HistoryStorage.hex(hash.digest())));
        }
    }
    private final ProtectionStore store;private final HistoryCatalog catalog;private final HistoryIdentity identity;private final HistoryIndex index;
    private final ProtectionStore.Durability durability;private final List<File> roots;private final File parent,controlRoot;private final Object monitor;
    private String acknowledged;private Map<File,BasicFileAttributes> acknowledgedFiles=Collections.emptyMap(),acknowledgedDirectories=Collections.emptyMap();
    private Map<String,String> retainedPins=Collections.emptyMap();
    private HistoryPins(ProtectionStore store,HistoryCatalog catalog,HistoryIdentity identity,HistoryIndex index,ProtectionStore.Durability durability)throws IOException {
        if(store==null||catalog==null||identity==null||index==null||durability==null||catalog.sourceStore()!=store||!index.uses(store,catalog,identity))throw new IOException("Invalid pin bundle");
        this.store=store;this.catalog=catalog;this.identity=identity;this.index=index;this.durability=durability;
        roots=Arrays.asList(store.storageRoot(),catalog.storageRoot(),identity.storageRoot(),index.storageRoot());HistoryStorage.siblings(roots.toArray(new File[0]));
        parent=roots.get(0).getParentFile();controlRoot=new File(parent,HistoryControlStore.ROOT_NAME);monitor=HistoryStorage.monitorFor(roots.get(0));
    }
    static HistoryPins open(ProtectionStore store,HistoryCatalog catalog,HistoryIdentity identity,HistoryIndex index,ProtectionStore.Durability durability)throws IOException {
        HistoryPins pins=new HistoryPins(store,catalog,identity,index,durability);pins.reconcile();return pins;
    }
    void reconcile()throws IOException {synchronized(monitor){try(HistoryStorage.MutationScope lease=HistoryStorage.administration(store.storageRoot())){reconcileLocked();}}}
    private void reconcileLocked()throws IOException {
        HistoryControlStore.Inspection before=control(true);HistoryPinRecord newlyClosed=null;
        if(!before.pins.stages.isEmpty()){
            if(before.pins.stages.size()!=1)throw new IOException("Conflicting pin stages");HistoryControlStore.Entry stage=before.pins.stages.get(0);
            if(stage.pin==null)throw new IOException("Partial pin stage retained; terminal repair required");
            HistoryPinRecord source=stage.pin.begin?stage.pin:find(before.pins.begins,stage.pin.operation);
            if(source==null)throw new IOException("Missing staged pin BEGIN");validateSources(source);
            publish(before,stage.pin.name(),HistoryPinRecord.encode(stage.pin),true,source);if(!stage.pin.begin)newlyClosed=source;before=control();
        }
        HistoryPinRecord protectedSource=before.pins.active!=null?before.pins.active:newlyClosed;
        Map<File,BasicFileAttributes> protectedFiles=captureSources(protectedSource);
        NativeEvidence nativeBefore=newlyClosed!=null&&newlyClosed.scope==Scope.ALL_BUNDLE?nativeEvidence():null;
        Map<File,BasicFileAttributes> stable=capture(before);Map<File,BasicFileAttributes> directories=captureDirectories();
        for(HistoryControlStore.Entry entry:before.entries)sync(file(entry.filename),read(file(entry.filename)),attributes(file(entry.filename)));
        durability.syncDirectory(parent);for(File root:roots)durability.syncDirectory(root);durability.syncDirectory(controlRoot);
        HistoryControlStore.Inspection after=control(true);unchanged(before,after,Collections.emptySet());checkCaptured(stable);checkDirectories(directories);
        checkCaptured(protectedFiles);if(protectedSource!=null)validateSources(protectedSource);checkNative(nativeBefore);
        acknowledge(after);
    }
    String begin(Purpose purpose,Scope scope,Collection<String> snapshotIds,ManifestIdentity manifest)throws IOException {
        synchronized(monitor){try(HistoryStorage.MutationScope lease=HistoryStorage.administration(store.storageRoot())){
            requireReady();HistoryControlStore.Inspection before=control();
            if(before.pins.active!=null||!before.pins.stages.isEmpty()||pruneBlocked(store.storageRoot(),before))throw new IOException("Existing pin requires closure/reconciliation");
            if(purpose==null||scope==null||snapshotIds==null||manifest==null||snapshotIds.size()>MAX_SOURCES)throw new IOException("Invalid pin request");
            List<Source> sources=new ArrayList<>();Set<String> seen=new TreeSet<>();for(String id:snapshotIds){HistoryMetadataIO.id(id);if(!seen.add(id))throw new IOException("Duplicate pinned snapshot");}
            if(scope==Scope.ALL_BUNDLE&&!seen.isEmpty())throw new IOException("ALL_BUNDLE has no subset descriptors");
            for(String id:seen){ProtectionStore.StoredRecord record=store.pinSource(id);sources.add(new Source(id,record.serializedBytes,record.envelopeHash));}
            HistoryPinRecord begin=HistoryPinRecord.begin(UUID.randomUUID(),before.registration,before.latest,purpose,scope,manifest.value,sources);byte[] bytes=HistoryPinRecord.encode(begin);
            if(before.actualBytes>HistoryControlStore.MAX_BYTES-bytes.length-CLOSE_BYTES||before.actualEntries>HistoryControlStore.MAX_ENTRIES-2)throw new IOException("Pin plus terminal reservation exhausts control allowance");
            publish(before,begin.name(),bytes,false,begin);acknowledge(control());return begin.operation.toString();
        }}
    }
    /** Exact input binding only: neither output progress nor provider authority is reconstructed. */
    HistoryPinRecord resume(String operationId,InputStream originalManifest)throws IOException {
        synchronized(monitor){try(HistoryStorage.MutationScope lease=HistoryStorage.administration(store.storageRoot())){
            HistoryControlStore.Inspection before=control();HistoryPinRecord active=active(before,operationId);requireAcknowledged(before);
            Map<File,BasicFileAttributes> stable=capture(before);Map<File,BasicFileAttributes> directories=captureDirectories();Map<File,BasicFileAttributes> protectedFiles=captureSources(active);
            manifest(active,originalManifest);HistoryControlStore.Inspection after=control();unchanged(before,after,Collections.emptySet());checkCaptured(stable);checkDirectories(directories);checkCaptured(protectedFiles);validateSources(active);return active;
        }}
    }
    void close(String operationId,Outcome outcome,InputStream originalManifest)throws IOException {
        synchronized(monitor){try(HistoryStorage.MutationScope lease=HistoryStorage.administration(store.storageRoot())){
            if(outcome==null)throw new IOException("Missing pin outcome");HistoryMetadataIO.id(operationId);UUID operation=UUID.fromString(operationId);
            HistoryControlStore.Inspection before=control();HistoryPinRecord begin=find(before.pins.begins,operation);if(begin==null)throw new IOException("Unknown pin");
            HistoryPinRecord closed=find(before.pins.closes,operation);
            if(closed!=null){if(closed.outcome!=outcome)throw new IOException("Conflicting immutable pin outcome");reconcileLocked();return;}
            byte[] expected=HistoryPinRecord.close(begin,outcome);
            if(!before.pins.stages.isEmpty()){
                if(before.pins.stages.size()!=1||before.pins.stages.get(0).pin==null||before.pins.stages.get(0).pin.begin
                        ||!before.pins.stages.get(0).pin.envelopeHash.equals(HistoryStorage.hex(HistoryStorage.digest().digest(expected))))throw new IOException("Partial/conflicting CLOSE retained");
                reconcileLocked();return;
            }
            requireReady();active(before,operationId);requireAcknowledged(before);
            Map<File,BasicFileAttributes> stable=capture(before);Map<File,BasicFileAttributes> directories=captureDirectories();
            Map<File,BasicFileAttributes> protectedFiles=captureSources(begin);NativeEvidence nativeBefore=begin.scope==Scope.ALL_BUNDLE?nativeEvidence():null;
            if(outcome==Outcome.RELEASED||originalManifest!=null)manifest(begin,originalManifest);
            validateSources(begin);
            requireReady();HistoryControlStore.Inspection after=control();unchanged(before,after,Collections.emptySet());checkCaptured(stable);checkDirectories(directories);checkCaptured(protectedFiles);validateSources(begin);checkNative(nativeBefore);
            if(after.pins.reservedBytes!=CLOSE_BYTES||after.pins.reservedEntries!=1)throw new IOException("Missing exact close reservation");
            publish(after,"pin-"+operation+".close",expected,false,begin);acknowledge(control());
        }}
    }
    private void manifest(HistoryPinRecord begin,InputStream input)throws IOException {
        Manifest actual=ManifestIdentity.read(begin.manifest.domain,begin.manifest.count,input).value;
        if(actual.length!=begin.manifest.length||!actual.hash.equals(begin.manifest.hash))throw new IOException("Original manifest identity mismatch");
    }
    private static HistoryPinRecord active(HistoryControlStore.Inspection control,String operation)throws IOException {
        HistoryMetadataIO.id(operation);HistoryPinRecord active=control.pins.active;
        if(active==null||!active.operation.toString().equals(operation)||!control.pins.stages.isEmpty())throw new IOException("No acknowledged active pin");return active;
    }
    private static HistoryPinRecord find(List<HistoryPinRecord> records,UUID operation){for(HistoryPinRecord record:records)if(record.operation.equals(operation))return record;return null;}
    private void requireReady()throws IOException {
        HistoryCapacity capacity=HistoryStorage.capacity(store.storageRoot());if(capacity==null)throw new IOException("Explicit ready capacity required");
        for(int i=0;i<roots.size();i++)capacity.requireRole(roots.get(i),i+1);
    }
    private HistoryControlStore.Inspection control()throws IOException {return control(false);}
    /** Existing closed audits can acknowledge durability beside a committed maintenance intent.
     * This never promotes intent evidence or grants a pin mutation/read projection. */
    private HistoryControlStore.Inspection control(boolean auditOnly)throws IOException {
        HistoryStorage.siblings(roots.toArray(new File[0]));HistoryControlStore.Inspection control=new HistoryControlStore(parent).inspect();
        requireRetained(store.storageRoot(),control);
        if(!control.chainComplete||!control.usageComplete||control.registration==null||control.latest==null||!control.stages.isEmpty()||!control.pins.valid
                ||control.actualBytes>HistoryControlStore.MAX_BYTES-control.pins.reservedBytes||control.actualEntries>HistoryControlStore.MAX_ENTRIES-control.pins.reservedEntries)throw new IOException("Incomplete pin control authority");
        if(control.hasMaintenance()&&!(auditOnly&&control.committedIntent!=null&&control.committedIntent.intent!=null&&control.intentStage==null
                &&control.pins.present&&control.pins.active==null&&control.pins.stages.isEmpty()))throw new IOException("Maintenance incompatible with pin operation");
        boolean marker=false;for(HistoryControlStore.Entry entry:control.entries)if(HistoryControlStore.ENROLLMENT_NAME.equals(entry.filename)&&control.registration.envelopeHash.equals(entry.envelopeHash))marker=true;
        if(!marker||control.registration.bootstrapQuotaBytes!=store.constructorQuotaBytes())throw new IOException("Invalid pin enrollment/bootstrap");
        for(int i=0;i<4;i++)if(!control.registration.roles.get(i).basename.equals(roots.get(i).getName()))throw new IOException("Foreign pin role bundle");return control;
    }
    private static final class NativeEvidence {
        final Map<String,String> records=new TreeMap<>();final Map<File,BasicFileAttributes> identities=new LinkedHashMap<>();
        long reservedBytes,reservedSlots;
    }
    private NativeEvidence nativeEvidence()throws IOException {
        NativeEvidence result=new NativeEvidence();ProtectionStore.Inventory inventory=store.pinInventory();
        if(!inventory.usageComplete)throw new IOException("Mandatory native accounting incomplete");
        for(String diagnostic:inventory.diagnostics)if(!diagnostic.endsWith("retained stage"))throw new IOException("Mandatory protection inventory invalid");
        result.reservedBytes=inventory.reservedCompletionBytes;result.reservedSlots=inventory.reservedCompletionSlots;
        nativeRecords(result,roots.get(0),inventory.records);
        HistoryStorage.Budget budget=new HistoryStorage.Budget(HistoryStorage.METADATA_BUDGET);
        for(int i=1;i<roots.size();i++){
            HistoryStorage.FilesView files=HistoryStorage.inspect(roots.get(i),i==1?"catalog":i==2?"identity":"index",HistoryMetadataIO.MAX_RECORD,HistoryMetadataIO.MAX_ENTRIES,budget);
            if(!files.usageComplete)throw new IOException("Native accounting incomplete");
            for(String diagnostic:files.diagnostics)if(!diagnostic.endsWith("retained stage")&&!diagnostic.endsWith("oversized entry"))throw new IOException("Native accounting invalid");
            nativeRecords(result,roots.get(i),files.records);
        }return result;
    }
    private static void nativeRecords(NativeEvidence evidence,File root,List<ProtectionStore.StoredRecord> records)throws IOException {
        for(ProtectionStore.StoredRecord record:records){File file=new File(root,record.filename);evidence.records.put(root.getName()+"/"+record.filename,record.serializedBytes+":"+record.envelopeHash);evidence.identities.put(file,attributes(file));}
    }
    private void checkNative(NativeEvidence before)throws IOException {
        if(before==null)return;NativeEvidence after=nativeEvidence();checkCaptured(before.identities);
        if(before.reservedBytes!=after.reservedBytes||before.reservedSlots!=after.reservedSlots||!before.records.equals(after.records))throw new IOException("Native state changed during pin callback");
    }
    private Map<File,BasicFileAttributes> captureSources(HistoryPinRecord begin)throws IOException {
        Map<File,BasicFileAttributes> result=new LinkedHashMap<>();
        if(begin!=null&&begin.scope==Scope.EXACT_SUBSET)for(Source source:begin.sources){File file=new File(store.storageRoot(),source.id+".snapshot");result.put(file,attributes(file));}
        if(begin!=null)validateSources(begin);checkCaptured(result);return result;
    }
    private void validateSources(HistoryPinRecord begin)throws IOException {
        if(begin.scope==Scope.EXACT_SUBSET)for(Source source:begin.sources){ProtectionStore.StoredRecord current=store.pinSource(source.id);
            if(source.length!=current.serializedBytes||!source.hash.equals(current.envelopeHash))throw new IOException("Changed pinned snapshot");}
    }
    private void publish(HistoryControlStore.Inspection before,String name,byte[] expected,boolean reuse,HistoryPinRecord sources)throws IOException {
        Map<File,BasicFileAttributes> stable=capture(before);Map<File,BasicFileAttributes> directories=captureDirectories();File stage=file(name+".stage"),destination=file(name);Map<File,BasicFileAttributes> protectedFiles=captureSources(sources);
        NativeEvidence nativeBefore=sources.scope==Scope.ALL_BUNDLE&&name.endsWith(".close")?nativeEvidence():null;
        try{attributes(destination);throw new IOException("Immutable pin destination exists");}catch(NoSuchFileException absent){/* genuine absence */}
        HistoryStorage.changed(store.storageRoot());BasicFileAttributes staged;
        if(reuse){staged=attributes(stage);sync(stage,expected,staged);}
        else{Files.createFile(stage.toPath());try(FileOutputStream out=new FileOutputStream(stage)){out.write(expected);out.flush();staged=attributes(stage);durability.syncFile(out.getFD());same(staged,attributes(stage));}}
        if(!Arrays.equals(expected,read(stage)))throw new IOException("Pin stage changed");checkCaptured(protectedFiles);validateSources(sources);checkNative(nativeBefore);
        durability.move(stage,destination);try{attributes(stage);throw new IOException("Pin move retained stage");}catch(NoSuchFileException absent){/* consumed */}
        same(staged,attributes(destination));durability.syncDirectory(controlRoot);
        HistoryControlStore.Inspection after=control();unchanged(before,after,new HashSet<>(Arrays.asList(name,name+".stage")));
        stable.remove(stage);checkCaptured(stable);checkDirectories(directories);same(staged,attributes(destination));
        if(!Arrays.equals(expected,read(destination)))throw new IOException("Committed pin bytes changed");checkCaptured(protectedFiles);validateSources(sources);checkNative(nativeBefore);
    }
    private void acknowledge(HistoryControlStore.Inspection control)throws IOException {
        if(!control.pins.stages.isEmpty())throw new IOException("Unreconciled pin stage");
        Map<File,BasicFileAttributes> files=capture(control),directories=captureDirectories();Map<String,String> records=pinRecords(control);
        HistoryStorage.changed(store.storageRoot());acknowledged=control.pins.fingerprint;acknowledgedFiles=Collections.unmodifiableMap(files);
        acknowledgedDirectories=Collections.unmodifiableMap(directories);retainedPins=Collections.unmodifiableMap(records);HistoryStorage.pins(store.storageRoot(),this);
    }
    private static Map<String,String> pinRecords(HistoryControlStore.Inspection control){
        Map<String,String> result=new TreeMap<>();for(HistoryControlStore.Entry entry:control.entries)if(entry.pin!=null&&!entry.stage)result.put(entry.filename,entry.length+":"+entry.envelopeHash);return result;
    }
    private boolean retains(HistoryControlStore.Inspection control){return pinRecords(control).entrySet().containsAll(retainedPins.entrySet());}

    private void requireAcknowledged(HistoryControlStore.Inspection control)throws IOException {
        HistoryPins acknowledged=HistoryStorage.pins(store.storageRoot());if(acknowledged==null||!acknowledged.matches(control))throw new IOException("Pin requires explicit durable reconciliation");
    }
    static void requireRetained(File root,HistoryControlStore.Inspection control)throws IOException {
        HistoryPins installed=HistoryStorage.pins(root);if(installed!=null&&!installed.retains(control))throw new IOException("Known immutable pin evidence missing or changed");
    }
    static boolean pruneBlocked(File root,HistoryControlStore.Inspection control)throws IOException {
        HistoryPins acknowledged=HistoryStorage.pins(root);if(acknowledged!=null&&!acknowledged.retains(control))return true;
        if(!control.pins.present)return false;if(!control.pins.valid||control.pins.active!=null||!control.pins.stages.isEmpty())return true;
        return acknowledged==null||!acknowledged.matches(control);
    }
    private boolean matches(HistoryControlStore.Inspection control){try{if(!Objects.equals(acknowledged,control.pins.fingerprint))return false;checkCaptured(acknowledgedFiles);checkDirectories(acknowledgedDirectories);return true;}catch(IOException invalid){return false;}}
    private File file(String name){return new File(HistoryControlStore.ENROLLMENT_NAME.equals(name)?parent:controlRoot,name);}
    private Map<File,BasicFileAttributes> capture(HistoryControlStore.Inspection control)throws IOException {
        Map<File,BasicFileAttributes> result=new LinkedHashMap<>();for(HistoryControlStore.Entry entry:control.entries)result.put(file(entry.filename),attributes(file(entry.filename)));return result;
    }
    private Map<File,BasicFileAttributes> captureDirectories()throws IOException {
        Map<File,BasicFileAttributes> result=new LinkedHashMap<>();result.put(parent,attributes(parent));result.put(controlRoot,attributes(controlRoot));for(File root:roots)result.put(root,attributes(root));return result;
    }
    private static void checkDirectories(Map<File,BasicFileAttributes> directories)throws IOException {for(Map.Entry<File,BasicFileAttributes> entry:directories.entrySet()){BasicFileAttributes now=attributes(entry.getKey()),before=entry.getValue();if(!now.isDirectory()||!Objects.equals(now.fileKey(),before.fileKey())||!now.creationTime().equals(before.creationTime()))throw new IOException("Pin role directory replaced");}}
    private static void checkCaptured(Map<File,BasicFileAttributes> captured)throws IOException {for(Map.Entry<File,BasicFileAttributes> entry:captured.entrySet())same(entry.getValue(),attributes(entry.getKey()));}
    private static void unchanged(HistoryControlStore.Inspection before,HistoryControlStore.Inspection after,Set<String> allowed)throws IOException {
        Map<String,String> a=new TreeMap<>(),b=new TreeMap<>();for(HistoryControlStore.Entry entry:before.entries)if(!allowed.contains(entry.filename))a.put(entry.filename,entry.length+":"+entry.envelopeHash);
        for(HistoryControlStore.Entry entry:after.entries)if(!allowed.contains(entry.filename))b.put(entry.filename,entry.length+":"+entry.envelopeHash);
        if(!a.equals(b))throw new IOException("Pin authority changed during callback");
    }
    private void sync(File file,byte[] expected,BasicFileAttributes before)throws IOException {
        if(!before.isRegularFile()||before.size()!=expected.length||!Arrays.equals(expected,read(file)))throw new IOException("Pin sync evidence changed");
        try(FileInputStream input=new FileInputStream(file)){byte[] bytes=new byte[expected.length];new DataInputStream(input).readFully(bytes);if(input.read()!=-1||!Arrays.equals(bytes,expected))throw new IOException("Pin readback changed");same(before,attributes(file));durability.syncFile(input.getFD());same(before,attributes(file));}
    }
    private static byte[] read(File file)throws IOException {
        BasicFileAttributes before=attributes(file);if(!before.isRegularFile()||before.size()<0||before.size()>(file.getName().equals(HistoryPruneIntent.INTENT_NAME)?HistoryPruneIntent.MAX_BYTES:MAX_BYTES))throw new IOException("Pin/control read bound");ByteBuffer bytes=ByteBuffer.allocate((int)before.size());
        try(SeekableByteChannel channel=Files.newByteChannel(file.toPath(),StandardOpenOption.READ,LinkOption.NOFOLLOW_LINKS)){while(bytes.hasRemaining()){int n=channel.read(bytes);if(n<=0)throw new IOException("Incomplete pin read");}if(channel.read(ByteBuffer.allocate(1))!=-1)throw new IOException("Pin grew");}same(before,attributes(file));return bytes.array();
    }
    private static BasicFileAttributes attributes(File file)throws IOException{return Files.readAttributes(file.toPath(),BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);}
    private static void same(BasicFileAttributes a,BasicFileAttributes b)throws IOException {if(!a.isRegularFile()||!b.isRegularFile()||a.size()!=b.size()||!Objects.equals(a.fileKey(),b.fileKey())||!a.lastModifiedTime().equals(b.lastModifiedTime())||!a.creationTime().equals(b.creationTime()))throw new IOException("Pin/control identity changed");}
}
