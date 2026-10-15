package com.example.markdowndesk;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;

/** Explicit private single-process history authority. No UI activation or deletion. */
public final class HistoryCapacity {
    public static final class EffectivePolicy {
        public final String bundleId,controlRecordHash;
        public final long generation,quotaBytes;
        public final int automaticCount;
        EffectivePolicy(HistoryControlStore.Record record){bundleId=record.bundleId.toString();generation=record.generation;controlRecordHash=record.envelopeHash;quotaBytes=record.quotaBytes;automaticCount=record.automaticCount;}
    }
    public static final class Usage {
        public final long dataActualBytes,completionReservedBytes,completionReservedSlots,dataChargedBytes,controlActualBytes;
        public final int controlActualEntries,controlReservedEntries;
        public final long controlReservedBytes;
        public final long operationActualBytes,operationReservedBytes,operationOccupiedEntries,operationReservedEntries;
        public final boolean usageComplete,mutationBlocked,overQuota;
        public final List<String> diagnostics;
        private Usage(long actual,long reserved,long slots,HistoryControlStore.Inspection control,boolean complete,boolean blocked,long quota,List<String> diagnostics)throws IOException{
            this(actual,reserved,slots,control,complete,blocked,quota,diagnostics,0,0,0,0);
        }
        private Usage(long actual,long reserved,long slots,HistoryControlStore.Inspection control,boolean complete,boolean blocked,long quota,List<String> diagnostics,long opActual,long opReserved,long opOccupied,long opSlots)throws IOException{
            operationActualBytes=opActual;operationReservedBytes=opReserved;operationOccupiedEntries=opOccupied;operationReservedEntries=opSlots;
            dataActualBytes=actual;completionReservedBytes=reserved;completionReservedSlots=slots;
            controlReservedBytes=control.controlReservedBytes;controlReservedEntries=control.controlReservedEntries;
            dataChargedBytes=add(add(actual,reserved),opReserved);controlActualBytes=control.actualBytes;controlActualEntries=control.actualEntries;
            usageComplete=complete&&control.usageComplete;mutationBlocked=blocked||!usageComplete;overQuota=dataChargedBytes>quota;
            this.diagnostics=Collections.unmodifiableList(new ArrayList<>(diagnostics));
        }
    }
    private final ProtectionStore store;
    private final HistoryCatalog catalog;
    private final HistoryIdentity identity;
    private final HistoryIndex index;
    private final List<File> roots;
    private final File parent,controlRoot;
    private final Object monitor;
    private final ProtectionStore.Durability durability;
    private final HistoryControlStore control;
    private EffectivePolicy acknowledged;
    private boolean ready;
    private HistoryCapacity(ProtectionStore store,HistoryCatalog catalog,HistoryIdentity identity,HistoryIndex index,ProtectionStore.Durability durability)throws IOException{
        if(store==null||catalog==null||identity==null||index==null||durability==null||catalog.sourceStore()!=store||!index.uses(store,catalog,identity))throw new IOException("Invalid complete history bundle");
        this.store=store;this.catalog=catalog;this.identity=identity;this.index=index;this.durability=durability;
        roots=Arrays.asList(store.storageRoot(),catalog.storageRoot(),identity.storageRoot(),index.storageRoot());HistoryStorage.siblings(roots.toArray(new File[0]));
        parent=roots.get(0).getParentFile();controlRoot=new File(parent,HistoryControlStore.ROOT_NAME);
        for(File root:roots){if(root.equals(controlRoot)||!Files.isDirectory(root.toPath(),LinkOption.NOFOLLOW_LINKS))throw new IOException("Invalid existing role root");}
        monitor=HistoryStorage.monitorFor(roots.get(0));control=new HistoryControlStore(parent);
    }
    public static HistoryCapacity register(ProtectionStore store,HistoryCatalog catalog,HistoryIdentity identity,HistoryIndex index,HistoryPolicy initial,ProtectionStore.Durability durability)throws IOException{
        HistoryCapacity candidate=new HistoryCapacity(store,catalog,identity,index,durability);
        synchronized(candidate.monitor){try(HistoryStorage.MutationScope scope=HistoryStorage.administration(store.storageRoot())){
            HistoryStorage.requireOperational(store.storageRoot());if(initial==null)throw new IOException("Missing initial policy");HistoryControlStore.Inspection inspection=candidate.control.inspect();
            if(inspection.state!=HistoryControlStore.State.UNREGISTERED){
                if(inspection.registration==null)throw new IOException("Registration incomplete; explicit reconciliation required");candidate.validateBundle(inspection.registration);
                if(inspection.registration.quotaBytes!=initial.quotaBytes||inspection.registration.automaticCount!=initial.automaticCount)throw new IOException("Conflicting initial registration policy");
                HistoryCapacity current=HistoryStorage.capacity(store.storageRoot());if(current!=null){current.validateBundle(inspection.registration);current.requireReady();return current;}
                HistoryStorage.capacity(store.storageRoot(),candidate);candidate.reconcileLocked();return candidate;
            }
            List<HistoryControlStore.Role> roles=new ArrayList<>();for(int i=0;i<4;i++)roles.add(new HistoryControlStore.Role(i+1,candidate.roots.get(i).getName()));
            byte[] bytes=HistoryControlStore.encodeRegistration(UUID.randomUUID(),store.constructorQuotaBytes(),roles,initial.quotaBytes,initial.automaticCount);
            Usage usage=candidate.ledger(inspection);if(!usage.usageComplete||usage.mutationBlocked)throw new IOException("Incomplete registration inventory");
            admitControl(inspection,Math.multiplyExact(bytes.length,2L),2);
            HistoryStorage.capacity(store.storageRoot(),candidate);HistoryStorage.changed(store.storageRoot());
            candidate.publishAt(candidate.parent,HistoryControlStore.ENROLLMENT_NAME,bytes,false);
            Files.createDirectory(candidate.controlRoot.toPath());durability.syncDirectory(candidate.parent);durability.syncDirectory(candidate.controlRoot);
            candidate.publish(HistoryControlStore.REGISTRATION_NAME,bytes,false);candidate.reconcileLocked();return candidate;
        }}
    }
    public static HistoryCapacity open(ProtectionStore store,HistoryCatalog catalog,HistoryIdentity identity,HistoryIndex index,ProtectionStore.Durability durability)throws IOException {
        Object monitor=HistoryStorage.monitorFor(store.storageRoot());synchronized(monitor){try(HistoryStorage.MutationScope scope=HistoryStorage.administration(store.storageRoot())){return openLocked(store,catalog,identity,index,durability);}}
    }
    static HistoryCapacity openLocked(ProtectionStore store,HistoryCatalog catalog,HistoryIdentity identity,HistoryIndex index,ProtectionStore.Durability durability)throws IOException {
        HistoryStorage.requireMutation(store.storageRoot());HistoryStorage.requireOperational(store.storageRoot());
        HistoryCapacity candidate=new HistoryCapacity(store,catalog,identity,index,durability);
        if(candidate.control.inspect().state==HistoryControlStore.State.UNREGISTERED)throw new IOException("History bundle is not registered");
        HistoryStorage.capacity(store.storageRoot(),candidate);candidate.reconcileLocked();return candidate;
    }
    static HistoryCapacity prepareTerminalLocked(HistoryMaintenanceExecutor.Retirement retirement,ProtectionStore store,HistoryCatalog catalog,HistoryIdentity identity,HistoryIndex index,ProtectionStore.Durability durability)throws IOException {
        HistoryStorage.requireMutation(store.storageRoot());retirement.require(retirement.view);HistoryCapacity candidate=new HistoryCapacity(store,catalog,identity,index,durability);retirement.requireBundle(candidate.roots);
        HistoryControlStore.Inspection before=retirement.control();candidate.validateBundle(before.registration);candidate.validateEnrollment(before);admitControl(before,0,0);
        for(HistoryControlStore.Entry entry:before.entries){File file=new File(HistoryControlStore.ENROLLMENT_NAME.equals(entry.filename)?candidate.parent:candidate.controlRoot,entry.filename);candidate.syncExisting(file,candidate.read(file));}
        durability.syncDirectory(candidate.parent);for(File root:candidate.roots)durability.syncDirectory(root);durability.syncDirectory(candidate.controlRoot);
        HistoryGraph.Graph graph=retirement.validate();HistoryControlStore.Inspection after=retirement.control();
        if(!graph.complete||!graph.usageComplete||!before.latest.envelopeHash.equals(after.latest.envelopeHash))throw new IOException("Incomplete terminal capacity proof");
        // The all-absent validated graph reports physical inventory and surviving completion credit.
        new Usage(graph.actualBytes,graph.reservedCompletionBytes,graph.reservedCompletionSlots,after,true,false,after.latest.quotaBytes,Collections.emptyList(),graph.operationActualBytes,graph.operationReservedBytes,graph.operationOccupiedEntries,graph.operationReservedEntries);
        candidate.acknowledged=new EffectivePolicy(after.latest);candidate.ready=true;return candidate;
    }
    void requireTerminalBinding(HistoryMaintenanceExecutor.Retirement retirement)throws IOException {
        retirement.require(retirement.view);retirement.requireBundle(roots);HistoryPruneIntent.PolicyBinding policy=retirement.view.intent.policy;
        if(!ready||acknowledged==null||!acknowledged.bundleId.equals(policy.bundleId)||acknowledged.generation!=policy.generation||!acknowledged.controlRecordHash.equals(policy.envelopeHash)
                ||acknowledged.quotaBytes!=policy.quotaBytes||acknowledged.automaticCount!=policy.automaticCount)throw new IOException("Foreign terminal capacity candidate");
    }
    public EffectivePolicy policy()throws IOException{synchronized(monitor){if(acknowledged==null)throw new IOException("No acknowledged policy");return acknowledged;}}
    public Usage inspect()throws IOException{synchronized(monitor){HistoryControlStore.Inspection inspection=control.inspect();Usage usage=ledger(inspection);
        return new Usage(usage.dataActualBytes,usage.completionReservedBytes,usage.completionReservedSlots,inspection,usage.usageComplete,
            usage.mutationBlocked||!authorityMatches(inspection),acknowledged==null?Long.MAX_VALUE:acknowledged.quotaBytes,usage.diagnostics,usage.operationActualBytes,usage.operationReservedBytes,usage.operationOccupiedEntries,usage.operationReservedEntries);}}
    public EffectivePolicy setPolicy(HistoryPolicy requested)throws IOException{
        synchronized(monitor){try(HistoryStorage.MutationScope scope=HistoryStorage.administration(store.storageRoot())){return setPolicyLocked(requested);}}
    }
    private EffectivePolicy setPolicyLocked(HistoryPolicy requested)throws IOException{
        requireReady();Usage usage=inspect();if(usage.mutationBlocked||!usage.usageComplete)throw new IOException("Incomplete policy accounting");if(requested==null)throw new IOException("Missing requested policy");
        HistoryControlStore.Inspection inspection=control.inspect();long generation=HistoryControlStore.nextGeneration(inspection.latest.generation);
        byte[] bytes=HistoryControlStore.encodePolicy(inspection.latest.bundleId,generation,inspection.latest.envelopeHash,requested.quotaBytes,requested.automaticCount);
        admitControl(inspection,bytes.length,1);ready=false;
        publish(HistoryControlStore.policyFilename(generation),bytes,false);return reconcileLocked();
    }
    public EffectivePolicy reconcile()throws IOException{synchronized(monitor){try(HistoryStorage.MutationScope scope=HistoryStorage.administration(store.storageRoot())){return reconcileLocked();}}}
    private EffectivePolicy reconcileLocked()throws IOException{
        HistoryStorage.requireMutation(store.storageRoot());HistoryStorage.requireOperational(store.storageRoot());ready=false;HistoryControlStore.Inspection inspection=control.inspect();HistoryPins.requireRetained(store.storageRoot(),inspection);HistoryStorage.requireOperationControl(store.storageRoot(),inspection);
        if(!inspection.operations.ready||!inspection.pins.valid||!inspection.pins.stages.isEmpty())throw new IOException("Pins require explicit reconciliation");
        admitControl(inspection,0,0); // Retained policy stages already occupy their physical slot/bytes.
        if(inspection.hasMaintenance())throw new IOException("Maintenance requires dedicated recovery");
        if(inspection.state==HistoryControlStore.State.UNREGISTERED)throw new IOException("Missing registration");
        HistoryControlStore.Registration binding=inspection.registration;
        if(binding==null&&inspection.stages.size()==1&&inspection.stages.get(0).record instanceof HistoryControlStore.Registration)binding=(HistoryControlStore.Registration)inspection.stages.get(0).record;
        validateEnrollment(inspection,binding);
        if(!inspection.stages.isEmpty()){
            if(inspection.stages.size()!=1||inspection.stages.get(0).state!=HistoryControlStore.StageState.PROVISIONAL)throw new IOException("Invalid retained control stage");
            HistoryControlStore.StageDescriptor stage=inspection.stages.get(0);
            if(stage.record instanceof HistoryControlStore.Registration)validateBundle((HistoryControlStore.Registration)stage.record);
            else{if(!inspection.chainComplete||inspection.registration==null)throw new IOException("Invalid policy stage chain");validateBundle(inspection.registration);}
            byte[] bytes=read(new File(controlRoot,stage.filename));
            if(!HistoryControlStore.decode(bytes).envelopeHash.equals(stage.envelopeHash))throw new IOException("Control stage changed");
            publish(stage.filename.substring(0,stage.filename.length()-6),bytes,true);inspection=control.inspect();
        }
        if(inspection.state!=HistoryControlStore.State.VALIDATED||inspection.registration==null)throw new IOException("Control authority invalid");
        validateBundle(inspection.registration);validateEnrollment(inspection);Usage usage=ledger(inspection);if(!usage.usageComplete||usage.mutationBlocked)throw new IOException("Capacity inventory incomplete");
        PrivateOperationStore.Ledger operationBefore=PrivateOperationStore.inspect(store.storageRoot(),inspection,new HistoryStorage.Budget(HistoryStorage.METADATA_BUDGET));
        // Constructors under durable opt-in are read-only; reconciliation restores their sync guarantee.
        for(HistoryControlStore.Entry entry:inspection.entries){File file=new File(HistoryControlStore.ENROLLMENT_NAME.equals(entry.filename)?parent:controlRoot,entry.filename);syncExisting(file,read(file));}
        durability.syncDirectory(parent);for(File root:roots)durability.syncDirectory(root);durability.syncDirectory(controlRoot);
        HistoryControlStore.Inspection verified=control.inspect();if(verified.state!=HistoryControlStore.State.VALIDATED||!verified.latest.envelopeHash.equals(inspection.latest.envelopeHash))throw new IOException("Control changed during reconciliation");
        HistoryPins.requireRetained(store.storageRoot(),verified);validateEnrollment(verified);
        PrivateOperationStore.Ledger operationAfter=PrivateOperationStore.inspect(store.storageRoot(),verified,new HistoryStorage.Budget(HistoryStorage.METADATA_BUDGET));
        if(!operationAfter.complete||!operationBefore.fingerprint.equals(operationAfter.fingerprint))throw new IOException("Operation inventory changed during reconciliation");
        HistoryStorage.rememberOperationEvidence(store.storageRoot(),verified,operationAfter);acknowledged=new EffectivePolicy(verified.latest);ready=true;HistoryStorage.capacity(store.storageRoot(),this);HistoryStorage.changed(store.storageRoot());return acknowledged;
    }
    private void validateBundle(HistoryControlStore.Registration registration)throws IOException{
        if(registration.bootstrapQuotaBytes!=store.constructorQuotaBytes())throw new IOException("Conflicting bootstrap hint");
        for(int i=0;i<4;i++)if(!registration.roles.get(i).basename.equals(roots.get(i).getName()))throw new IOException("Conflicting registered roles");
    }
    private void validateEnrollment(HistoryControlStore.Inspection inspection)throws IOException{validateEnrollment(inspection,inspection.registration);}
    private void validateEnrollment(HistoryControlStore.Inspection inspection,HistoryControlStore.Registration binding)throws IOException{
        if(binding==null)throw new IOException("Missing enrollment registration");
        for(HistoryControlStore.Entry entry:inspection.entries)if(HistoryControlStore.ENROLLMENT_NAME.equals(entry.filename)&&entry.record instanceof HistoryControlStore.Registration&&entry.envelopeHash.equals(binding.envelopeHash))return;
        throw new IOException("Missing durable enrollment binding");
    }
    EffectivePolicy graphPolicy(HistoryControlStore.Inspection inspection,File... graphRoots){return roots.equals(Arrays.asList(graphRoots))&&authorityMatches(inspection)?acknowledged:null;}
    private boolean authorityMatches(HistoryControlStore.Inspection inspection){try{HistoryStorage.requireOperational(store.storageRoot());HistoryPins.requireRetained(store.storageRoot(),inspection);HistoryStorage.requireOperationControl(store.storageRoot(),inspection);validateEnrollment(inspection);}catch(IOException invalid){return false;}return ready&&acknowledged!=null&&isCurrentAuthority()&&inspection.state==HistoryControlStore.State.VALIDATED&&inspection.latest!=null&&acknowledged.controlRecordHash.equals(inspection.latest.envelopeHash);}
    private boolean isCurrentAuthority(){try{return HistoryStorage.capacity(store.storageRoot())==this;}catch(IOException failure){return false;}}
    private void requireReady()throws IOException{if(!authorityMatches(control.inspect())){ready=false;throw new IOException("History authority requires reconciliation");}}
    void requireWriter(File root)throws IOException{HistoryStorage.requireMutation(root);if(!roots.contains(root))throw new IOException("Unregistered history writer");requireReady();}
    void requireStoreHandle(File root)throws IOException{if(!roots.get(0).equals(root))throw new IOException("Foreign protection policy handle");}
    void requireRole(File root,int role)throws IOException{requireWriter(root);if(role<1||role>4||!roots.get(role-1).equals(root))throw new IOException("Foreign history writer role");}
    private Usage ledger(HistoryControlStore.Inspection inspection)throws IOException{
        long actual=0,reserved=0,slots=0;boolean complete=true;List<String> diagnostics=new ArrayList<>(inspection.diagnostics);
        ProtectionStore.Inventory inventory=store.inspect();actual=inventory.actualBytes;reserved=inventory.reservedCompletionBytes;slots=inventory.reservedCompletionSlots;complete=inventory.usageComplete;
        for(String diagnostic:inventory.diagnostics)if(!diagnostic.endsWith("retained stage")){diagnostics.add(diagnostic);complete=false;}
        HistoryStorage.Budget budget=new HistoryStorage.Budget(HistoryStorage.METADATA_BUDGET);
        for(int i=1;i<4;i++){
            String role=i==1?"catalog":i==2?"identity":"index";HistoryStorage.FilesView files=HistoryStorage.inspect(roots.get(i),role,HistoryMetadataIO.MAX_RECORD,HistoryMetadataIO.MAX_ENTRIES,budget);
            actual=add(actual,files.bytes);complete&=files.usageComplete;
            if(files.bytes>HistoryMetadataIO.MAX_BYTES)diagnostics.add("capacity: metadata root byte limit");
            // Optional native size/semantic damage is not missing accounting. Its
            // own writer and graph still reject it; mandatory protection charges it.
            for(String diagnostic:files.diagnostics){diagnostics.add(diagnostic);
                if(!diagnostic.endsWith("retained stage")&&!diagnostic.endsWith("oversized entry"))complete=false;
            }
        }
        PrivateOperationStore.Ledger operations=null;try{operations=PrivateOperationStore.inspect(store.storageRoot(),inspection,budget);
            actual=add(actual,operations.actualBytes);complete&=operations.complete;diagnostics.addAll(operations.diagnostics);
        }catch(IOException invalid){complete=false;diagnostics.add("capacity: operation accounting unavailable");}
        return new Usage(actual,reserved,slots,inspection,complete,!complete,acknowledged==null?Long.MAX_VALUE:acknowledged.quotaBytes,diagnostics,operations==null?0:operations.actualBytes,operations==null?0:operations.reservedBytes,operations==null?0:operations.occupiedEntries,operations==null?0:operations.reservedEntries);
    }
    void admit(File root,long additionalBytes,long additionalReservedBytes,int additionalSlots,int additionalReservedSlots)throws IOException {
        requireWriter(root);if(additionalBytes<0||additionalReservedBytes<0||additionalSlots<0||additionalReservedSlots<0)throw new IOException("Invalid capacity request");
        Usage usage=inspect();if(usage.mutationBlocked||!usage.usageComplete)throw new IOException("Incomplete history capacity proof");
        if(add(usage.dataChargedBytes,add(additionalBytes,additionalReservedBytes))>acknowledged.quotaBytes)throw new IOException("Shared history quota exhausted");
        checkRootCapacity(root,additionalBytes,additionalSlots,additionalReservedSlots,usage.completionReservedSlots);
    }
    void admitCompletion(ProtectionStore.CompletionConversion conversion)throws IOException {
        requireWriter(store.storageRoot());conversion.require(store);Usage usage=inspect();
        if(usage.mutationBlocked||!usage.usageComplete)throw new IOException("Incomplete completion capacity proof");
        // Exact freshly validated native conversion has zero net byte/slot growth. No other write receives this exception.
        checkRootCapacity(store.storageRoot(),0,0,0,usage.completionReservedSlots);conversion.require(store);
    }
    private void checkRootCapacity(File root,long bytes,int slots,int reservedSlots,long completionSlots)throws IOException {
        int position=roots.indexOf(root);if(position<0)throw new IOException("Unregistered capacity root");String role=position==0?"store":position==1?"catalog":position==2?"identity":"index";
        HistoryStorage.FilesView files=HistoryStorage.inspect(root,role,position==0?ProtectionStore.MAX_DOCUMENT_BYTES+32768L:HistoryMetadataIO.MAX_RECORD,
            position==0?65536:HistoryMetadataIO.MAX_ENTRIES,new HistoryStorage.Budget(HistoryStorage.METADATA_BUDGET));
        if(!files.usageComplete)throw new IOException("Incomplete root accounting");long occupied=add(files.records.size(),position==0?completionSlots:0);
        if(add(occupied,add(slots,reservedSlots))>(position==0?65536:HistoryMetadataIO.MAX_ENTRIES))throw new IOException("History root entry capacity exhausted");
        if(position!=0&&add(files.bytes,bytes)>HistoryMetadataIO.MAX_BYTES)throw new IOException("History metadata root byte capacity exhausted");
    }
    private static void admitControl(HistoryControlStore.Inspection inspection,long bytes,int entries)throws IOException{
        if(!inspection.usageComplete||!inspection.pins.valid||!inspection.operations.valid||add(add(inspection.actualBytes,inspection.controlReservedBytes),bytes)>HistoryControlStore.MAX_BYTES||add(add(inspection.actualEntries,inspection.controlReservedEntries),entries)>HistoryControlStore.MAX_ENTRIES)throw new IOException("Shared control allowance exhausted");
    }
    private void publish(String name,byte[] expected,boolean reuse)throws IOException{
        publishAt(controlRoot,name,expected,reuse);
    }
    private void publishAt(File directory,String name,byte[] expected,boolean reuse)throws IOException{
        HistoryStorage.requireMutation(store.storageRoot());PrivateOperationStore.Ledger operationBefore=PrivateOperationStore.completeDataLocked(store.storageRoot());File stage=new File(directory,name+".stage"),destination=new File(directory,name);
        if(Files.exists(destination.toPath(),LinkOption.NOFOLLOW_LINKS))throw new IOException("Immutable control destination exists");
        HistoryStorage.changed(store.storageRoot());
        if(reuse)syncExisting(stage,expected);
        else{Files.createFile(stage.toPath());try(FileOutputStream stream=new FileOutputStream(stage)){stream.write(expected);stream.flush();BasicFileAttributes before=attributes(stage);durability.syncFile(stream.getFD());same(stage,before);}}
        if(!Arrays.equals(expected,read(stage)))throw new IOException("Control stage changed before move");
        PrivateOperationStore.unchangedDataLocked(store.storageRoot(),operationBefore);
        durability.move(stage,destination);if(Files.exists(stage.toPath(),LinkOption.NOFOLLOW_LINKS))throw new IOException("Control move retained stage");durability.syncDirectory(directory);
        if(!Arrays.equals(expected,read(destination)))throw new IOException("Committed control validation failed");
        PrivateOperationStore.unchangedDataLocked(store.storageRoot(),operationBefore);
    }
    private void syncExisting(File file,byte[] expected)throws IOException{
        BasicFileAttributes before=attributes(file);if(!Arrays.equals(expected,read(file)))throw new IOException("Control bytes changed");
        try(FileInputStream stream=new FileInputStream(file)){byte[] bytes=new byte[expected.length];new DataInputStream(stream).readFully(bytes);if(stream.read()!=-1||!Arrays.equals(bytes,expected))throw new IOException("Control bytes changed before sync");same(file,before);durability.syncFile(stream.getFD());same(file,before);}
    }
    private static byte[] read(File file)throws IOException{
        BasicFileAttributes before=attributes(file);if(!before.isRegularFile()||before.size()<0||before.size()>HistoryControlStore.MAX_RECORD)throw new IOException("Invalid control read size");
        ByteBuffer buffer=ByteBuffer.allocate((int)before.size());try(SeekableByteChannel channel=Files.newByteChannel(file.toPath(),StandardOpenOption.READ,LinkOption.NOFOLLOW_LINKS)){while(buffer.hasRemaining())if(channel.read(buffer)<0)throw new IOException("Truncated control record");if(channel.read(ByteBuffer.allocate(1))!=-1)throw new IOException("Control grew");}same(file,before);return buffer.array();
    }
    private static BasicFileAttributes attributes(File file)throws IOException{return Files.readAttributes(file.toPath(),BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);}
    private static void same(File file,BasicFileAttributes before)throws IOException{BasicFileAttributes after=attributes(file);if(!before.isRegularFile()||!after.isRegularFile()||before.size()!=after.size()||!Objects.equals(before.fileKey(),after.fileKey())||!before.lastModifiedTime().equals(after.lastModifiedTime()))throw new IOException("Control identity changed");}
    private static long add(long a,long b)throws IOException{try{return Math.addExact(a,b);}catch(ArithmeticException failure){throw new IOException("History capacity overflow",failure);}}
}
