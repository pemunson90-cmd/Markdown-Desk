package com.example.markdowndesk;

import java.io.*;
import java.nio.channels.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;

/** Read-only recovery projection for one committed intent. No publication, promotion or unlink. */
final class HistoryMaintenanceRecovery {
    static final class RecoveryView {
        final HistoryGraph.Graph graph;
        final String intentHash;
        private RecoveryView(HistoryGraph.Graph graph,String hash){this.graph=graph;intentHash=hash;}
    }
    private final ProtectionStore store;
    private final HistoryCatalog catalog;
    private final HistoryIdentity identity;
    private final HistoryIndex index;
    private final ProtectionStore.Durability durability;
    private final List<File> roots;
    private final File parent,controlRoot;
    private final Object monitor;
    private Map<String,BasicFileAttributes> memberIdentities;
    private final Map<File,BasicFileAttributes> controlIdentities=new LinkedHashMap<>(),directoryIdentities=new LinkedHashMap<>();
    private String registrationHash;
    private HistoryMaintenanceRecovery(ProtectionStore store,HistoryCatalog catalog,HistoryIdentity identity,HistoryIndex index,ProtectionStore.Durability durability)throws IOException {
        if(store==null||catalog==null||identity==null||index==null||durability==null||catalog.sourceStore()!=store||!index.uses(store,catalog,identity))throw new IOException("Invalid maintenance bundle");
        this.store=store;this.catalog=catalog;this.identity=identity;this.index=index;this.durability=durability;
        roots=Arrays.asList(store.storageRoot(),catalog.storageRoot(),identity.storageRoot(),index.storageRoot());
        HistoryStorage.siblings(roots.toArray(new File[0]));parent=roots.get(0).getParentFile();controlRoot=new File(parent,HistoryControlStore.ROOT_NAME);monitor=HistoryStorage.monitorFor(roots.get(0));
    }
    static RecoveryView open(ProtectionStore store,HistoryCatalog catalog,HistoryIdentity identity,HistoryIndex index,ProtectionStore.Durability durability)throws IOException {
        Object monitor=HistoryStorage.monitorFor(store.storageRoot());
        synchronized(monitor){try(HistoryStorage.MutationScope scope=HistoryStorage.administration(store.storageRoot())){return openLocked(store,catalog,identity,index,durability);}}
    }
    static RecoveryView openLocked(ProtectionStore store,HistoryCatalog catalog,HistoryIdentity identity,HistoryIndex index,ProtectionStore.Durability durability)throws IOException {
        HistoryStorage.requireMutation(store.storageRoot());HistoryStorage.requireOperational(store.storageRoot());
        HistoryMaintenanceRecovery recovery=new HistoryMaintenanceRecovery(store,catalog,identity,index,durability);
            HistoryControlStore.Inspection control=recovery.authority(null);
            recovery.registrationHash=control.registration.envelopeHash;
            HistoryTombstoneView candidate=new HistoryTombstoneView(recovery,control.committedIntent.intent);
            recovery.memberIdentities=recovery.memberIdentities(candidate);
            for(HistoryControlStore.Entry entry:control.entries){File file=recovery.controlFile(entry);recovery.controlIdentities.put(file,attributes(file));}
            for(File directory:recovery.roots)recovery.directoryIdentities.put(directory,attributes(directory));
            recovery.directoryIdentities.put(recovery.parent,attributes(recovery.parent));recovery.directoryIdentities.put(recovery.controlRoot,attributes(recovery.controlRoot));
            recovery.validate(candidate);
            // Callbacks get ordinary readers and the existing mutation lease. No provisional global view.
            recovery.sync(control);
            HistoryGraph.Graph finalGraph=recovery.validate(candidate); // no callback-capable path after this point
            HistoryStorage.installView(store.storageRoot(),candidate);
            return new RecoveryView(finalGraph,candidate.intent.envelopeHash);
    }
    HistoryTombstoneView validateInstalled(HistoryTombstoneView view)throws IOException {
        if(!Thread.holdsLock(monitor)||view.owner!=this)throw new IOException("Invalid maintenance capability");
        validate(view);return view;
    }
    private HistoryControlStore.Inspection authority(HistoryTombstoneView expected)throws IOException {return authority(expected,null);}
    private HistoryControlStore.Inspection authority(HistoryTombstoneView expected,HistoryMaintenanceExecutor.Retirement retirement)throws IOException {
        if(expected!=null&&expected.owner!=this)throw new IOException("Foreign maintenance capability");
        if(retirement!=null)retirement.require(expected);
        HistoryStorage.siblings(roots.toArray(new File[0]));
        HistoryControlStore.Inspection control=new HistoryControlStore(parent).inspect();
        if(retirement==null){if(HistoryPins.pruneBlocked(store.storageRoot(),control))throw new IOException("Maintenance blocked by operation pins");}
        else{HistoryPins.requireRetained(store.storageRoot(),control);if(!control.pins.valid||control.pins.active!=null||!control.pins.stages.isEmpty())throw new IOException("Terminal state blocked by operation pins");}
        if(!control.chainComplete||!control.usageComplete||control.registration==null||control.latest==null||!control.stages.isEmpty()
                ||control.intentStage!=null||(retirement==null?(control.committedIntent==null||control.committedIntent.intent==null):control.committedIntent!=null))
            throw new IOException("Incomplete maintenance authority");
        for(String diagnostic:control.diagnostics)if(!diagnostic.equals("control: retained maintenance"))throw new IOException("Invalid maintenance control inventory");
        HistoryControlStore.Registration registration=control.registration;
        if(registration.bootstrapQuotaBytes!=store.constructorQuotaBytes()||(registrationHash!=null&&!registrationHash.equals(registration.envelopeHash)))throw new IOException("Changed maintenance registration");
        for(int i=0;i<4;i++)if(!registration.roles.get(i).basename.equals(roots.get(i).getName()))throw new IOException("Foreign maintenance role");
        boolean enrolled=false;
        for(HistoryControlStore.Entry entry:control.entries)if(HistoryControlStore.ENROLLMENT_NAME.equals(entry.filename)&&registration.envelopeHash.equals(entry.envelopeHash)&&entry.record instanceof HistoryControlStore.Registration)enrolled=true;
        if(!enrolled)throw new IOException("Missing maintenance enrollment");
        HistoryPruneIntent intent=retirement==null?control.committedIntent.intent:expected.intent;HistoryPruneIntent.PolicyBinding policy=intent.policy;
        if(!policy.bundleId.equals(control.latest.bundleId.toString())||policy.generation!=control.latest.generation||!policy.envelopeHash.equals(control.latest.envelopeHash)
                ||policy.quotaBytes!=control.latest.quotaBytes||policy.automaticCount!=control.latest.automaticCount
                ||(expected!=null&&!expected.intent.envelopeHash.equals(intent.envelopeHash)))throw new IOException("Changed maintenance intent/policy binding");
        return control;
    }
    private HistoryGraph.Graph validate(HistoryTombstoneView view)throws IOException {return validate(view,null);}
    HistoryGraph.Graph validateTerminal(HistoryTombstoneView view,HistoryMaintenanceExecutor.Retirement retirement)throws IOException {return validate(view,retirement);}
    HistoryControlStore.Inspection terminalControl(HistoryTombstoneView view,HistoryMaintenanceExecutor.Retirement retirement)throws IOException {
        HistoryControlStore.Inspection control=authority(view,retirement);validateIdentities(view,retirement);return control;
    }
    private void validateIdentities(HistoryTombstoneView view,HistoryMaintenanceExecutor.Retirement retirement)throws IOException {
        for(Map.Entry<File,BasicFileAttributes> entry:controlIdentities.entrySet()){
            if(retirement!=null&&entry.getKey().equals(new File(controlRoot,HistoryPruneIntent.INTENT_NAME)))continue;
            if(!same(entry.getValue(),attributes(entry.getKey())))throw new IOException("Maintenance control replaced");
        }
        for(Map.Entry<File,BasicFileAttributes> entry:directoryIdentities.entrySet()){BasicFileAttributes now=attributes(entry.getKey()),before=entry.getValue();
            if(!now.isDirectory()||!Objects.equals(before.fileKey(),now.fileKey())||!before.creationTime().equals(now.creationTime()))throw new IOException("Maintenance role directory replaced");}
    }
    private HistoryGraph.Graph validate(HistoryTombstoneView view,HistoryMaintenanceExecutor.Retirement retirement)throws IOException {
        authority(view,retirement);validateIdentities(view,retirement);
        for(HistoryPruneIntent.Member member:view.intent.members){
            File file=memberFile(member);BasicFileAttributes before;
            try{before=attributes(file);}catch(NoSuchFileException absent){continue;}
            if(retirement!=null)throw new IOException("Retired closure member reappeared");
            BasicFileAttributes original=memberIdentities.get(member.key());
            if(original==null||!same(original,before))throw new IOException("Maintenance member replaced");
            exact(file,member,before);
            HistoryPruneIntent.Group group=group(view.intent,member);
            switch(member.role){case STORE:store.validateRemoved(member,group);break;case CATALOG:catalog.validateRemoved(member,group);break;case INDEX:index.validateRemoved(member,group);break;default:throw new IOException("Invalid removal role");}
            if(!same(before,attributes(file)))throw new IOException("Maintenance member changed during validation");
        }
        HistoryGraph.Graph graph=new HistoryGraph(store,catalog,identity,index,new HistoryPolicy(view.intent.policy.quotaBytes,view.intent.policy.automaticCount)).scan(view);
        if(!graph.complete||graph.pruneBlocked||!view.intent.unaffectedFingerprint.equals(unaffectedFingerprint(graph,view.intent.members)))throw new IOException("Changed or incomplete maintenance survivors");
        Map<String,ProtectionStore.StoredRecord> records=new HashMap<>();for(ProtectionStore.StoredRecord record:graph.records)records.put(record.rootRole+"/"+record.filename,record);
        for(HistoryPruneIntent.Group anchor:view.intent.anchors){
            for(HistoryPruneIntent.Member member:anchor.records)match(member,records.get(member.key()));
            boolean found=false;for(HistoryGraph.Ownership ownership:graph.ownerships)if(anchor.ownershipId.equals(ownership.id)&&anchor.historyId.equals(ownership.historyId)
                    &&anchor.transactionId.equals(ownership.transactionId)&&anchor.sequence==ownership.sequence&&anchor.originalSnapshotId.equals(ownership.originalSnapshotId)&&anchor.attemptedSnapshotId.equals(ownership.attemptedSnapshotId))found=true;
            if(!found)throw new IOException("Changed maintenance anchor");
        }
        int identities=0;for(ProtectionStore.StoredRecord record:graph.records)if(record.rootRole.equals("identity"))identities++;
        if(identities!=view.intent.identityEnvelopes.size())throw new IOException("Changed maintenance identity inventory");
        for(HistoryPruneIntent.Member member:view.intent.identityEnvelopes)match(member,records.get(member.key()));
        for(HistoryPruneIntent.IdentityProof proof:view.intent.identities){
            boolean found=false;for(HistoryIdentity.Descriptor descriptor:graph.identities)if(proof.id.equals(descriptor.id)&&Objects.equals(proof.exactUri,descriptor.exactUri)
                    &&Objects.equals(proof.tabId,descriptor.tabId)&&proof.origins.equals(new HashSet<>(descriptor.origins)))found=true;
            if(!found)throw new IOException("Changed maintenance routing");
        }
        authority(view,retirement);return graph;
    }
    private static HistoryPruneIntent.Group group(HistoryPruneIntent intent,HistoryPruneIntent.Member member)throws IOException {
        for(HistoryPruneIntent.Group group:intent.groups)for(HistoryPruneIntent.Member candidate:group.records)if(candidate.key().equals(member.key()))return group;
        throw new IOException("Missing maintenance group proof");
    }
    private File memberFile(HistoryPruneIntent.Member member){return new File(roots.get(member.role.code-1),member.id+"."+member.kind.wire);}
    private Map<String,BasicFileAttributes> memberIdentities(HistoryTombstoneView view)throws IOException {
        Map<String,BasicFileAttributes> result=new HashMap<>();
        for(HistoryPruneIntent.Member member:view.intent.members)try{result.put(member.key(),attributes(memberFile(member)));}catch(NoSuchFileException absent){/* only genuine absence */}
        return Collections.unmodifiableMap(result);
    }
    private static void exact(File file,HistoryPruneIntent.Member member,BasicFileAttributes before)throws IOException {
        if(!before.isRegularFile()||before.size()!=member.serializedBytes)throw new IOException("Changed maintenance member size/type");
        MessageDigest digest=HistoryStorage.digest();long count=0;
        try(InputStream in=Channels.newInputStream(Files.newByteChannel(file.toPath(),StandardOpenOption.READ,LinkOption.NOFOLLOW_LINKS))){
            byte[] buffer=new byte[8192];int n;while((n=in.read(buffer))!=-1){count=Math.addExact(count,n);if(count>member.serializedBytes)throw new IOException("Maintenance member grew");digest.update(buffer,0,n);}
        }
        if(count!=member.serializedBytes||!member.envelopeHash.equals(HistoryStorage.hex(digest.digest()))||!same(before,attributes(file)))throw new IOException("Changed maintenance member envelope");
    }
    private static void match(HistoryPruneIntent.Member expected,ProtectionStore.StoredRecord actual)throws IOException {
        if(actual==null||expected.serializedBytes!=actual.serializedBytes||!expected.envelopeHash.equals(actual.envelopeHash)||!expected.kind.wire.equals(actual.kind))throw new IOException("Changed maintenance proof envelope");
    }
    private File controlFile(HistoryControlStore.Entry entry){return new File(HistoryControlStore.ENROLLMENT_NAME.equals(entry.filename)?parent:controlRoot,entry.filename);}
    private void sync(HistoryControlStore.Inspection control)throws IOException {
        // Sync existing control through read-only descriptors; validate exact binding again afterwards.
        for(HistoryControlStore.Entry entry:control.entries){
            File file=controlFile(entry);
            BasicFileAttributes before=attributes(file);
            if(!before.isRegularFile()||before.size()!=entry.length)throw new IOException("Changed control sync type/size");
            try(FileInputStream in=new FileInputStream(file)){
                MessageDigest digest=HistoryStorage.digest();byte[] buffer=new byte[8192];long count=0;int n;
                while((n=in.read(buffer))!=-1){count+=n;if(count>entry.length)throw new IOException("Control grew during sync");digest.update(buffer,0,n);}
                if(count!=entry.length||!entry.envelopeHash.equals(HistoryStorage.hex(digest.digest()))||!same(before,attributes(file)))throw new IOException("Control changed during readback");
                durability.syncFile(in.getFD());if(!same(before,attributes(file)))throw new IOException("Control replaced during sync");
            }
        }
        durability.syncDirectory(parent);for(File root:roots)durability.syncDirectory(root);durability.syncDirectory(controlRoot);
    }
    private static BasicFileAttributes attributes(File file)throws IOException{return Files.readAttributes(file.toPath(),BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);}
    private static boolean same(BasicFileAttributes a,BasicFileAttributes b){return a.isRegularFile()&&b.isRegularFile()&&a.size()==b.size()&&Objects.equals(a.fileKey(),b.fileKey())&&a.creationTime().equals(b.creationTime())&&a.lastModifiedTime().equals(b.lastModifiedTime());}

    /** Stable logical domain: no control usage, physical usage, caller profile or constructor hint. */
    static String unaffectedFingerprint(HistoryGraph.Graph graph,Collection<?> closure)throws IOException {
        if(graph==null||!graph.complete||closure==null)throw new IOException("Incomplete survivor graph");
        Set<String> removed=new HashSet<>();for(Object value:closure){
            if(value instanceof HistoryPruneIntent.Member)removed.add(((HistoryPruneIntent.Member)value).key());
            else if(value instanceof ProtectionStore.StoredRecord){ProtectionStore.StoredRecord record=(ProtectionStore.StoredRecord)value;removed.add(record.rootRole+"/"+record.filename);}
            else throw new IOException("Invalid survivor closure descriptor");
        }
        MessageDigest digest=HistoryStorage.digest();
        try(DataOutputStream out=new DataOutputStream(new DigestOutputStream(new OutputStream(){@Override public void write(int value){} @Override public void write(byte[] bytes,int offset,int length){}},digest))){
            text(out,"history-maintenance-unaffected-v1");out.writeInt(graph.registeredRoleNames.size());for(String name:graph.registeredRoleNames)text(out,name);
            List<String> records=new ArrayList<>(),edges=new ArrayList<>();
            for(ProtectionStore.StoredRecord record:graph.records)if(!removed.contains(record.rootRole+"/"+record.filename))records.add(record.rootRole+"/"+record.filename+":"+record.serializedBytes+":"+record.envelopeHash);
            Collections.sort(records);out.writeInt(records.size());for(String record:records)text(out,record);
            for(ProtectionStore.Transaction transaction:graph.transactions)if(!removed.contains("store/"+transaction.id+".transaction"))edges.add("transaction/"+transaction.id+":"+transaction.original.id+":"+transaction.attempted.id+":"+transaction.verified);
            for(HistoryCatalog.Checkpoint checkpoint:graph.checkpoints)if(!removed.contains("catalog/"+checkpoint.id+".checkpoint"))edges.add("checkpoint/"+checkpoint.id+":"+checkpoint.snapshotId);
            for(HistoryGraph.Ownership ownership:graph.ownerships)if(!removed.contains("index/"+ownership.id+".index"))edges.add("index/"+ownership.id+":"+ownership.historyId+":"+ownership.transactionId+":"+ownership.sequence+":"+ownership.originalSnapshotId+":"+ownership.attemptedSnapshotId);
            Collections.sort(edges);out.writeInt(edges.size());for(String edge:edges)text(out,edge);
            List<HistoryIdentity.Descriptor> identities=new ArrayList<>(graph.identities);identities.sort(Comparator.comparing(value->value.id));out.writeInt(identities.size());
            for(HistoryIdentity.Descriptor identity:identities){text(out,identity.id);text(out,identity.exactUri);text(out,identity.tabId);List<String> origins=new ArrayList<>(identity.origins);Collections.sort(origins);out.writeInt(origins.size());for(String origin:origins)text(out,origin);}
            // Valid removed groups are complete; outstanding reservations belong exclusively to survivors.
            for(ProtectionStore.Transaction transaction:graph.transactions)if(removed.contains("store/"+transaction.id+".transaction")&&!transaction.verified)throw new IOException("Cannot project unresolved reservation");
            out.writeLong(graph.reservedCompletionBytes);out.writeLong(graph.reservedCompletionSlots);
        }
        return HistoryStorage.hex(digest.digest());
    }
    private static void text(DataOutputStream out,String value)throws IOException {if(value==null){out.writeInt(-1);return;}byte[] bytes=value.getBytes(StandardCharsets.UTF_8);out.writeInt(bytes.length);out.write(bytes);}
}
