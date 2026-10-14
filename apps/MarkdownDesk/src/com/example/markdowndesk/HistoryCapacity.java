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
        private EffectivePolicy(HistoryControlStore.Record record){bundleId=record.bundleId.toString();generation=record.generation;controlRecordHash=record.envelopeHash;quotaBytes=record.quotaBytes;automaticCount=record.automaticCount;}
    }
    public static final class Usage {
        public final long dataActualBytes,completionReservedBytes,completionReservedSlots,dataChargedBytes,controlActualBytes;
        public final int controlActualEntries;
        public final boolean usageComplete,mutationBlocked,overQuota;
        public final List<String> diagnostics;
        private Usage(long actual,long reserved,long slots,HistoryControlStore.Inspection control,boolean complete,boolean blocked,long quota,List<String> diagnostics)throws IOException{
            dataActualBytes=actual;completionReservedBytes=reserved;completionReservedSlots=slots;
            dataChargedBytes=add(actual,reserved);controlActualBytes=control.actualBytes;controlActualEntries=control.actualEntries;
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
            if(initial==null)throw new IOException("Missing initial policy");HistoryControlStore.Inspection inspection=candidate.control.inspect();
            if(inspection.state!=HistoryControlStore.State.UNREGISTERED){
                if(inspection.registration==null)throw new IOException("Registration incomplete; explicit reconciliation required");candidate.validateBundle(inspection.registration);
                if(inspection.registration.quotaBytes!=initial.quotaBytes||inspection.registration.automaticCount!=initial.automaticCount)throw new IOException("Conflicting initial registration policy");
                HistoryCapacity current=HistoryStorage.capacity(store.storageRoot());if(current!=null){current.validateBundle(inspection.registration);current.requireReady();return current;}
                HistoryStorage.capacity(store.storageRoot(),candidate);candidate.reconcileLocked();return candidate;
            }
            List<HistoryControlStore.Role> roles=new ArrayList<>();for(int i=0;i<4;i++)roles.add(new HistoryControlStore.Role(i+1,candidate.roots.get(i).getName()));
            byte[] bytes=HistoryControlStore.encodeRegistration(UUID.randomUUID(),store.constructorQuotaBytes(),roles,initial.quotaBytes,initial.automaticCount);
            Usage usage=candidate.ledger(inspection);if(!usage.usageComplete||usage.mutationBlocked)throw new IOException("Incomplete registration inventory");
            HistoryStorage.capacity(store.storageRoot(),candidate);HistoryStorage.changed(store.storageRoot());
            Files.createDirectory(candidate.controlRoot.toPath());durability.syncDirectory(candidate.parent);durability.syncDirectory(candidate.controlRoot);
            candidate.publish(HistoryControlStore.REGISTRATION_NAME,bytes,false);candidate.reconcileLocked();return candidate;
        }}
    }
    public static HistoryCapacity open(ProtectionStore store,HistoryCatalog catalog,HistoryIdentity identity,HistoryIndex index,ProtectionStore.Durability durability)throws IOException{
        HistoryCapacity candidate=new HistoryCapacity(store,catalog,identity,index,durability);
        synchronized(candidate.monitor){try(HistoryStorage.MutationScope scope=HistoryStorage.administration(store.storageRoot())){
            if(candidate.control.inspect().state==HistoryControlStore.State.UNREGISTERED)throw new IOException("History bundle is not registered");
            HistoryStorage.capacity(store.storageRoot(),candidate);candidate.reconcileLocked();return candidate;
        }}
    }
    public EffectivePolicy policy()throws IOException{synchronized(monitor){if(acknowledged==null)throw new IOException("No acknowledged policy");return acknowledged;}}
    public Usage inspect()throws IOException{synchronized(monitor){HistoryControlStore.Inspection inspection=control.inspect();Usage usage=ledger(inspection);
        return new Usage(usage.dataActualBytes,usage.completionReservedBytes,usage.completionReservedSlots,inspection,usage.usageComplete,
            usage.mutationBlocked||!authorityMatches(inspection),acknowledged==null?Long.MAX_VALUE:acknowledged.quotaBytes,usage.diagnostics);}}
    public EffectivePolicy setPolicy(HistoryPolicy requested)throws IOException{
        synchronized(monitor){try(HistoryStorage.MutationScope scope=HistoryStorage.administration(store.storageRoot())){return setPolicyLocked(requested);}}
    }
    private EffectivePolicy setPolicyLocked(HistoryPolicy requested)throws IOException{
        requireReady();if(requested==null)throw new IOException("Missing requested policy");
        HistoryControlStore.Inspection inspection=control.inspect();long generation=HistoryControlStore.nextGeneration(inspection.latest.generation);
        byte[] bytes=HistoryControlStore.encodePolicy(inspection.latest.bundleId,generation,inspection.latest.envelopeHash,requested.quotaBytes,requested.automaticCount);
        admitControl(inspection,bytes.length,1);ready=false;
        publish(HistoryControlStore.policyFilename(generation),bytes,false);return reconcileLocked();
    }
    public EffectivePolicy reconcile()throws IOException{synchronized(monitor){try(HistoryStorage.MutationScope scope=HistoryStorage.administration(store.storageRoot())){return reconcileLocked();}}}
    private EffectivePolicy reconcileLocked()throws IOException{
        ready=false;HistoryControlStore.Inspection inspection=control.inspect();
        if(inspection.state==HistoryControlStore.State.UNREGISTERED)throw new IOException("Missing registration");
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
        validateBundle(inspection.registration);Usage usage=ledger(inspection);if(!usage.usageComplete||usage.mutationBlocked)throw new IOException("Capacity inventory incomplete");
        // Constructors under durable opt-in are read-only; reconciliation restores their sync guarantee.
        for(HistoryControlStore.Entry entry:inspection.entries)syncExisting(new File(controlRoot,entry.filename),read(new File(controlRoot,entry.filename)));
        durability.syncDirectory(parent);for(File root:roots)durability.syncDirectory(root);durability.syncDirectory(controlRoot);
        HistoryControlStore.Inspection verified=control.inspect();if(verified.state!=HistoryControlStore.State.VALIDATED||!verified.latest.envelopeHash.equals(inspection.latest.envelopeHash))throw new IOException("Control changed during reconciliation");
        acknowledged=new EffectivePolicy(verified.latest);ready=true;HistoryStorage.capacity(store.storageRoot(),this);HistoryStorage.changed(store.storageRoot());return acknowledged;
    }
    private void validateBundle(HistoryControlStore.Registration registration)throws IOException{
        if(registration.bootstrapQuotaBytes!=store.constructorQuotaBytes())throw new IOException("Conflicting bootstrap hint");
        for(int i=0;i<4;i++)if(!registration.roles.get(i).basename.equals(roots.get(i).getName()))throw new IOException("Conflicting registered roles");
    }
    private boolean authorityMatches(HistoryControlStore.Inspection inspection){return ready&&acknowledged!=null&&HistoryStorageCapacityIdentity()&&inspection.state==HistoryControlStore.State.VALIDATED&&inspection.latest!=null&&acknowledged.controlRecordHash.equals(inspection.latest.envelopeHash);}
    private boolean HistoryStorageCapacityIdentity(){try{return HistoryStorage.capacity(store.storageRoot())==this;}catch(IOException failure){return false;}}
    private void requireReady()throws IOException{if(!authorityMatches(control.inspect())){ready=false;throw new IOException("History authority requires reconciliation");}}
    void requireWriter(File root)throws IOException{HistoryStorage.requireMutation(root);if(!roots.contains(root))throw new IOException("Unregistered history writer");requireReady();}
    private Usage ledger(HistoryControlStore.Inspection inspection)throws IOException{
        long actual=0,reserved=0,slots=0;boolean complete=true;List<String> diagnostics=new ArrayList<>();
        ProtectionStore.Inventory inventory=store.inspect();actual=inventory.actualBytes;reserved=inventory.reservedCompletionBytes;slots=inventory.reservedCompletionSlots;complete=inventory.usageComplete;
        for(String diagnostic:inventory.diagnostics)if(!diagnostic.endsWith("retained stage")){diagnostics.add(diagnostic);complete=false;}
        HistoryStorage.Budget budget=new HistoryStorage.Budget(HistoryStorage.METADATA_BUDGET);
        for(int i=1;i<4;i++){
            String role=i==1?"catalog":i==2?"identity":"index";HistoryStorage.FilesView files=HistoryStorage.inspect(roots.get(i),role,HistoryMetadataIO.MAX_RECORD,HistoryMetadataIO.MAX_ENTRIES,budget);
            actual=add(actual,files.bytes);complete&=files.usageComplete;
            if(files.bytes>HistoryMetadataIO.MAX_BYTES){diagnostics.add("capacity: metadata root byte limit");complete=false;}
            for(String diagnostic:files.diagnostics)if(!diagnostic.endsWith("retained stage")){diagnostics.add(diagnostic);complete=false;}
        }
        return new Usage(actual,reserved,slots,inspection,complete,!complete,acknowledged==null?Long.MAX_VALUE:acknowledged.quotaBytes,diagnostics);
    }
    private static void admitControl(HistoryControlStore.Inspection inspection,long bytes,int entries)throws IOException{
        if(!inspection.usageComplete||add(inspection.actualBytes,bytes)>HistoryControlStore.MAX_BYTES||add(inspection.actualEntries,entries)>HistoryControlStore.MAX_ENTRIES)throw new IOException("Shared control allowance exhausted");
    }
    private void publish(String name,byte[] expected,boolean reuse)throws IOException{
        HistoryStorage.requireMutation(store.storageRoot());File stage=new File(controlRoot,HistoryControlStore.stageFilename(name)),destination=new File(controlRoot,name);
        if(Files.exists(destination.toPath(),LinkOption.NOFOLLOW_LINKS))throw new IOException("Immutable control destination exists");
        HistoryStorage.changed(store.storageRoot());
        if(reuse)syncExisting(stage,expected);
        else{Files.createFile(stage.toPath());try(FileOutputStream stream=new FileOutputStream(stage)){stream.write(expected);stream.flush();BasicFileAttributes before=attributes(stage);durability.syncFile(stream.getFD());same(stage,before);}}
        if(!Arrays.equals(expected,read(stage)))throw new IOException("Control stage changed before move");
        durability.move(stage,destination);if(Files.exists(stage.toPath(),LinkOption.NOFOLLOW_LINKS))throw new IOException("Control move retained stage");durability.syncDirectory(controlRoot);
        if(!Arrays.equals(expected,read(destination)))throw new IOException("Committed control validation failed");
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
