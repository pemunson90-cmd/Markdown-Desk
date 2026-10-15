package com.example.markdowndesk;

import java.io.*;
import java.nio.*;
import java.nio.channels.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Private single-process history coordination. A JVM monitor is not a process lock. */
final class HistoryStorage {
    private static final ConcurrentHashMap<String,State> MONITORS=new ConcurrentHashMap<>();
    private static final class State {long generation;Thread mutator;HistoryCapacity capacity;HistoryTombstoneView tombstones;HistoryPins pins;HistoryOperations operations;PreparationSources preparationSources;HistoryMaintenanceExecutor.Retirement retirement;Map<String,String> operationControl=Collections.emptyMap(),operationData=Collections.emptyMap();long operationEvidenceBytes;}
    static final long METADATA_BUDGET=16L*1024*1024;
    private HistoryStorage() {}
    static void requireOperationControl(File root,HistoryControlStore.Inspection control)throws IOException {
        State state=(State)monitorFor(root);if(!Thread.holdsLock(state))throw new IOException("History monitor required");
        for(Map.Entry<String,String> retained:state.operationControl.entrySet()){
            boolean found=false;for(HistoryControlStore.Entry e:control.entries)if(retained.getKey().equals(e.filename)&&retained.getValue().equals(e.envelopeHash)&&!e.stage)found=true;
            if(!found)throw new IOException("Retained operation control disappeared/changed");
        }
    }
    static long operationEvidenceBytes(File root)throws IOException{return ((State)monitorFor(root)).operationEvidenceBytes;}
    static void requireOperationEvidence(File root,HistoryControlStore.Inspection control,PrivateOperationStore.Ledger ledger)throws IOException {
        requireOperationControl(root,control);State state=(State)monitorFor(root);
        for(Map.Entry<String,String> e:state.operationData.entrySet())if(!Objects.equals(e.getValue(),ledger.entries.get(e.getKey())))throw new IOException("Retained operation data disappeared/changed");
    }
    static void rememberOperationEvidence(File root,HistoryControlStore.Inspection control,PrivateOperationStore.Ledger ledger)throws IOException {
        requireMutation(root);requireOperationEvidence(root,control,ledger);if(!ledger.complete||!control.operations.valid)throw new IOException("Incomplete operation evidence");
        Map<String,String> records=new TreeMap<>(),files=new TreeMap<>();long bytes=0;
        for(HistoryControlStore.Entry e:control.entries)if(!e.stage&&PrivateOperationStore.operationEntry(e)){records.put(e.filename,e.envelopeHash);bytes=PrivateOperationStore.add(bytes,1024L+e.filename.length()*2L);}
        for(Map.Entry<String,String> e:ledger.entries.entrySet())if(!e.getKey().endsWith(".stage")){files.put(e.getKey(),e.getValue());bytes=PrivateOperationStore.add(bytes,1024L+e.getKey().length()*2L);}
        State state=(State)monitorFor(root);state.operationControl=Collections.unmodifiableMap(records);state.operationData=Collections.unmodifiableMap(files);state.operationEvidenceBytes=bytes;
    }
    static File canonicalRoot(File root)throws IOException {
        if(root==null)throw new IOException("Missing history root");
        File absolute=root.getAbsoluteFile();Path path=absolute.toPath();
        for(Path p=path;p!=null;p=p.getParent())if(Files.isSymbolicLink(p))throw new IOException("History path alias");
        if(!path.normalize().equals(path)||!absolute.getCanonicalFile().equals(absolute))throw new IOException("History root is not canonical");
        File parent=absolute.getParentFile();
        if(parent==null||!Files.isDirectory(parent.toPath(),LinkOption.NOFOLLOW_LINKS))throw new IOException("Missing private history parent");
        return absolute;
    }
    static Object monitorFor(File root)throws IOException {
        File canonical=canonicalRoot(root);return MONITORS.computeIfAbsent(canonical.getParent(),unused->new State());
    }
    static HistoryCapacity capacity(File root)throws IOException{return ((State)monitorFor(root)).capacity;}
    static void capacity(File root,HistoryCapacity value)throws IOException{requireMutation(root);((State)monitorFor(root)).capacity=value;}
    static HistoryOperations operations(File root)throws IOException{return ((State)monitorFor(root)).operations;}
    static void operations(File root,HistoryOperations operations)throws IOException {
        State state=(State)monitorFor(root);if(!Thread.holdsLock(state)||state.operations!=null)throw new IOException("Operation coordinator already installed or unlocked");state.operations=operations;
    }
    static PreparationSources preparationSources(File root)throws IOException {
        State state=(State)monitorFor(root);if(!Thread.holdsLock(state))throw new IOException("History monitor required");return state.preparationSources;
    }
    static void preparationSources(File root,PreparationSources value)throws IOException {
        State state=(State)monitorFor(root);if(!Thread.holdsLock(state)||state.preparationSources!=null||value==null)throw new IOException("Preparation owner already installed or unlocked");state.preparationSources=value;
    }
    static HistoryPins pins(File root)throws IOException{return ((State)monitorFor(root)).pins;}
    static void pins(File root,HistoryPins pins)throws IOException{requireMutation(root);((State)monitorFor(root)).pins=pins;}
    static HistoryTombstoneView installedView(File root)throws IOException{return ((State)monitorFor(root)).tombstones;}
    static HistoryMaintenanceExecutor.Retirement retirement(File root)throws IOException{return ((State)monitorFor(root)).retirement;}
    static void requireOperational(File root)throws IOException{if(retirement(root)!=null)throw new IOException("Maintenance retirement requires explicit replay");}
    static void startRetirement(File root,HistoryMaintenanceExecutor.Retirement capability)throws IOException {
        requireMutation(root);State state=(State)monitorFor(root);
        if(state.retirement!=null||state.tombstones!=capability.view)throw new IOException("Foreign retirement capability");
        changed(root);state.retirement=capability;
    }
    static void finishRetirement(File root,HistoryMaintenanceExecutor.Retirement capability,HistoryCapacity candidate)throws IOException {
        requireMutation(root);State state=(State)monitorFor(root);
        if(state.retirement!=capability||state.tombstones!=capability.view||candidate==null)throw new IOException("Foreign retirement completion");
        candidate.requireTerminalBinding(capability);changed(root);state.capacity=candidate;state.tombstones=null;state.retirement=null;
    }
    static ProtectionStore.StoredRecord snapshotRecord(File root,String id)throws IOException {
        Path path=root.toPath().resolve(id+".snapshot");BasicFileAttributes before=attrs(path);
        if(!before.isRegularFile()||before.size()<45||before.size()>ProtectionStore.MAX_DOCUMENT_BYTES+32768L)throw new IOException("Invalid pin source envelope");
        return new ProtectionStore.StoredRecord("store",id+".snapshot",id,"snapshot",before.size(),hash(path,before));
    }
    static HistoryTombstoneView readView(File root)throws IOException {
        State state=(State)monitorFor(root);if(!Thread.holdsLock(state))throw new IOException("History monitor required");
        if(state.retirement!=null)throw new IOException("Maintenance retirement requires explicit replay");
        if(state.tombstones!=null)return state.tombstones.revalidate();
        Path control=canonicalRoot(root).getParentFile().toPath().resolve(HistoryControlStore.ROOT_NAME);
        BasicFileAttributes directory;
        try{directory=Files.readAttributes(control,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);}
        catch(NoSuchFileException absent){return null;}
        if(!directory.isDirectory())throw new IOException("Invalid history control root");
        for(String name:Arrays.asList(HistoryPruneIntent.INTENT_NAME,HistoryPruneIntent.STAGE_NAME)){
            try{Files.readAttributes(control.resolve(name),BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);}
            catch(NoSuchFileException absent){continue;}
            throw new IOException("Maintenance requires validated read projection");
        }
        return null;
    }
    static void installView(File root,HistoryTombstoneView view)throws IOException {
        requireMutation(root);requireOperational(root);if(view==null)throw new IOException("Exact retirement capability required to clear view");changed(root);((State)monitorFor(root)).tombstones=view;
    }
    static boolean controlled(File root)throws IOException {
        File canonical=canonicalRoot(root);
        for(String name:Arrays.asList(HistoryControlStore.ENROLLMENT_NAME,HistoryControlStore.ENROLLMENT_STAGE)){
            try{Files.readAttributes(new File(canonical.getParentFile(),name).toPath(),BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);return true;}
            catch(NoSuchFileException absent){/* genuine absence only */}
        }
        Path control=new File(canonical.getParentFile(),HistoryControlStore.ROOT_NAME).toPath();
        try{Files.readAttributes(control,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);return true;}
        catch(NoSuchFileException absent){if(capacity(root)!=null)throw new IOException("Registered control root disappeared",absent);return false;}
    }
    static final class MutationScope implements AutoCloseable {
        private State state;
        MutationScope(State state){this.state=state;}
        public void close(){if(state!=null){state.mutator=null;state=null;}}
    }
    static MutationScope administration(File root)throws IOException {
        State state=(State)monitorFor(root);if(!Thread.holdsLock(state))throw new IOException("History monitor required");
        if(state.mutator!=null)throw new IOException("Nested history mutation rejected");
        state.mutator=Thread.currentThread();return new MutationScope(state);
    }
    static MutationScope mutation(File root)throws IOException {
        MutationScope scope=administration(root);
        try{requireOperational(root);if(controlled(root)){HistoryCapacity authority=capacity(root);if(authority==null)throw new IOException("History control requires explicit open");authority.requireWriter(root);}return scope;}
        catch(IOException|RuntimeException failure){scope.close();throw failure;}
    }
    static MutationScope mutation(File root,int role)throws IOException{
        MutationScope scope=mutation(root);
        try{requireRole(root,role);return scope;}catch(IOException|RuntimeException failure){scope.close();throw failure;}
    }
    static void requireRole(File root,int role)throws IOException{
        requireMutation(root);
        if(controlled(root)){HistoryCapacity authority=capacity(root);if(authority==null)throw new IOException("History role requires explicit open");authority.requireRole(root,role);}
    }
    static void requireMutation(File root)throws IOException {
        State state=(State)monitorFor(root);if(!Thread.holdsLock(state)||state.mutator!=Thread.currentThread())throw new IOException("History mutation scope required");
    }
    /** Native writers keep operation evidence stable across their external durability callbacks. */
    static PrivateOperationStore.Ledger operationWriteProof(File root)throws IOException {
        requireMutation(root);return controlled(root)?PrivateOperationStore.completeDataLocked(root):null;
    }
    static void verifyOperationWrite(File root,PrivateOperationStore.Ledger proof)throws IOException {
        requireMutation(root);
        if(proof==null){if(controlled(root))throw new IOException("History authority appeared during legacy write");return;}
        if(!controlled(root))throw new IOException("History authority disappeared during write");
        PrivateOperationStore.unchangedDataLocked(root,proof);
    }
    static boolean constructorReadOnly(File root,int role,Long bootstrap)throws IOException {
        requireMutation(root);if(!controlled(root))return false;
        if(!Files.isDirectory(root.toPath(),LinkOption.NOFOLLOW_LINKS))throw new IOException("Controlled roots must already exist");
        HistoryControlStore.Inspection control=new HistoryControlStore(root.getParentFile()).inspect();
        HistoryControlStore.Registration registration=control.registration;
        if(registration==null&&control.stages.size()==1&&control.stages.get(0).record instanceof HistoryControlStore.Registration)registration=(HistoryControlStore.Registration)control.stages.get(0).record;
        if(registration!=null){if(!registration.roles.get(role-1).basename.equals(root.getName()))throw new IOException("Unlisted controlled root");
            if(bootstrap!=null&&bootstrap.longValue()!=registration.bootstrapQuotaBytes)throw new IOException("Conflicting constructor bootstrap hint");}
        return true;
    }
    static long generation(File root)throws IOException {
        State state=(State)monitorFor(root);if(!Thread.holdsLock(state))throw new IOException("History monitor required");return state.generation;
    }
    static void changed(File root)throws IOException {
        State state=(State)monitorFor(root);if(!Thread.holdsLock(state))throw new IOException("History monitor required");
        try{state.generation=Math.addExact(state.generation,1);}catch(ArithmeticException failure){throw new IOException("History generation exhausted",failure);}
    }
    static void siblings(File... roots)throws IOException {
        Set<File> seen=new HashSet<>();File parent=null;
        for(File root:roots){File canonical=canonicalRoot(root);if(parent==null)parent=canonical.getParentFile();
            if(!parent.equals(canonical.getParentFile())||!seen.add(canonical))throw new IOException("History roles require distinct direct siblings");}
    }
    static final class BudgetExceeded extends IOException {
        BudgetExceeded(){super("History metadata budget exceeded");}
    }
    static final class Budget {
        private long remaining;
        Budget(long maximum)throws IOException {if(maximum<0||maximum>METADATA_BUDGET)throw new IOException("Invalid history metadata budget");remaining=maximum;}
        void charge(long amount)throws IOException {if(amount<0||amount>remaining)throw new BudgetExceeded();remaining-=amount;}
    }
    static final class FilesView {
        final List<ProtectionStore.StoredRecord> records=new ArrayList<>();
        final List<String> diagnostics=new ArrayList<>();long bytes;int physicalEntries;boolean usageComplete=true;
    }
    static FilesView inspect(File root,String role,long maxFile,int maxEntries,Budget budget)throws IOException {
        FilesView view=new FilesView();
        try{
            canonicalRoot(root);
            if(!Files.isDirectory(root.toPath(),LinkOption.NOFOLLOW_LINKS))throw new IOException("Missing history directory");
            try(DirectoryStream<Path> paths=Files.newDirectoryStream(root.toPath())){
                for(Path path:paths){
                    if(view.records.size()>=maxEntries){view.diagnostics.add(role+": entry limit");view.usageComplete=false;break;}
                    String name=path.getFileName().toString();
                    try{budget.charge(512L+name.length()*2L);}catch(IOException exceeded){view.diagnostics.add(role+": metadata budget");view.usageComplete=false;break;}
                    long size=0;String hash=null,kind="unknown",id=null;boolean validName=false;
                    try{
                        BasicFileAttributes attrs=attrs(path);size=attrs.size();
                        if(!attrs.isRegularFile()){view.diagnostics.add(role+": nonregular entry");view.usageComplete=false;}
                        else {
                            view.bytes=Math.addExact(view.bytes,size);
                            int dot=name.lastIndexOf('.');if(dot>0){id=name.substring(0,dot);kind=name.substring(dot+1);try{if(!UUID.fromString(id).toString().equals(id))throw new IOException("Invalid UUID");validName=true;}catch(IOException|IllegalArgumentException invalid){id=null;}}
                            if(!validName||!known(role,kind))view.diagnostics.add(role+": unknown entry");
                            long limit=maxFile;
                            if("store".equals(role)&&("transaction".equals(kind)||"complete".equals(kind)||"completion-stage".equals(kind)))limit=Math.min(limit,32768);
                            if(!validName||!known(role,kind))limit=Math.min(limit,16384);
                            if(size<0||size>limit)view.diagnostics.add(role+": oversized entry");
                            else hash=hash(path,attrs);
                            if("stage".equals(kind)||"completion-stage".equals(kind))view.diagnostics.add(role+": retained stage");
                        }
                    }catch(IOException|ArithmeticException failure){view.diagnostics.add(role+": unreadable entry");view.usageComplete=false;}
                    view.records.add(new ProtectionStore.StoredRecord(role,name,id,kind,size,hash));
                }
            }
        }catch(IOException|DirectoryIteratorException failure){view.diagnostics.add(role+": inventory unavailable");view.usageComplete=false;}
        view.records.sort(Comparator.comparing(record->record.filename));return view;
    }
    private static boolean known(String role,String kind){return "stage".equals(kind)||("store".equals(role)?Arrays.asList("snapshot","transaction","complete","completion-stage").contains(kind):("catalog".equals(role)?"checkpoint":role).equals(kind));}
    private static BasicFileAttributes attrs(Path path)throws IOException {return Files.readAttributes(path,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);}
    private static String hash(Path path,BasicFileAttributes before)throws IOException {
        MessageDigest digest=digest();ByteBuffer bytes=ByteBuffer.allocate(8192);long read=0;
        try(SeekableByteChannel channel=Files.newByteChannel(path,StandardOpenOption.READ,LinkOption.NOFOLLOW_LINKS)){
            while(read<before.size()){bytes.clear();bytes.limit((int)Math.min(bytes.capacity(),before.size()-read));int n=channel.read(bytes);if(n<0)throw new IOException("Truncated inventory file");if(n==0)continue;read+=n;bytes.flip();digest.update(bytes);}
            bytes.clear();bytes.limit(1);if(channel.read(bytes)!=-1)throw new IOException("Inventory file grew");
        }
        BasicFileAttributes after=attrs(path);
        if(!after.isRegularFile()||before.size()!=after.size()||!Objects.equals(before.fileKey(),after.fileKey())||!before.lastModifiedTime().equals(after.lastModifiedTime()))throw new IOException("Inventory file changed");
        return hex(digest.digest());
    }
    static MessageDigest digest()throws IOException {try{return MessageDigest.getInstance("SHA-256");}catch(NoSuchAlgorithmException e){throw new IOException("SHA-256 unavailable",e);}}
    static String hex(byte[] bytes){StringBuilder result=new StringBuilder(bytes.length*2);for(byte b:bytes)result.append(Character.forDigit((b&255)>>>4,16)).append(Character.forDigit(b&15,16));return result.toString();}
}
