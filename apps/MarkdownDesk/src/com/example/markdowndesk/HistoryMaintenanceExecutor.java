package com.example.markdowndesk;

import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;

/** Explicit selected retention and opt-in one-group floor retention; no pressure authority. */
final class HistoryMaintenanceExecutor {
    static final class Cancellation {
        private volatile boolean cancelled;
        void cancel(){cancelled=true;}
        boolean isCancelled(){return cancelled;}
    }
    enum AutomaticStatus {DISABLED,NO_WORK,CANCELLED_BEFORE_PUBLICATION,COMPLETED}
    static final class AutomaticResult {
        final AutomaticStatus status;final HistoryCapacity capacity;final long deletedBytes;final int deletedGroups;
        private AutomaticResult(AutomaticStatus status,HistoryCapacity capacity,long bytes,int groups){this.status=status;this.capacity=capacity;deletedBytes=bytes;deletedGroups=groups;}
    }
    enum Event {BEFORE_MEMBER_UNLINK,AFTER_MEMBER_UNLINK,BEFORE_INTENT_UNLINK,AFTER_INTENT_UNLINK}
    interface Boundary {void at(Event event,String memberKey)throws IOException;}
    private final ProtectionStore store;private final HistoryCatalog catalog;private final HistoryIdentity identity;private final HistoryIndex index;
    private final ProtectionStore.Durability durability;private final Boundary boundary;
    private final List<File> roots;private final File parent,controlRoot;private final Object monitor;
    HistoryMaintenanceExecutor(ProtectionStore store,HistoryCatalog catalog,HistoryIdentity identity,HistoryIndex index,ProtectionStore.Durability durability)throws IOException {
        this(store,catalog,identity,index,durability,(event,key)->{});
    }
    HistoryMaintenanceExecutor(ProtectionStore store,HistoryCatalog catalog,HistoryIdentity identity,HistoryIndex index,ProtectionStore.Durability durability,Boundary boundary)throws IOException {
        if(store==null||catalog==null||identity==null||index==null||durability==null||boundary==null||catalog.sourceStore()!=store||!index.uses(store,catalog,identity))throw new IOException("Invalid executor bundle");
        this.store=store;this.catalog=catalog;this.identity=identity;this.index=index;this.durability=durability;this.boundary=boundary;
        roots=Arrays.asList(store.storageRoot(),catalog.storageRoot(),identity.storageRoot(),index.storageRoot());HistoryStorage.siblings(roots.toArray(new File[0]));
        parent=roots.get(0).getParentFile();controlRoot=new File(parent,HistoryControlStore.ROOT_NAME);monitor=HistoryStorage.monitorFor(store.storageRoot());
    }
    HistoryCapacity execute(HistoryCapacity authority,HistoryPruner.DeletePlan preview,HistoryPruneIntent.Authorization authorization)throws IOException {
        synchronized(monitor){try(HistoryStorage.MutationScope lease=HistoryStorage.administration(store.storageRoot())){
            HistoryStorage.requireOperational(store.storageRoot());selected(preview,authorization);
            if(authority==null||HistoryStorage.capacity(store.storageRoot())!=authority)throw new IOException("Current installed authority required");
            for(int i=0;i<roots.size();i++)authority.requireRole(roots.get(i),i+1);
            HistoryCapacity.EffectivePolicy policy=authority.policy();HistoryControlStore.Inspection control=control(policy);
            if(control.hasMaintenance()||HistoryStorage.installedView(store.storageRoot())!=null||HistoryPins.pruneBlocked(store.storageRoot(),control))throw new IOException("Maintenance or pins require reconciliation");
            if(preview.policy==null||preview.policy.quotaBytes!=policy.quotaBytes||preview.policy.automaticCount!=policy.automaticCount)throw new IOException("Preview is not effective policy");
            Stable stable=new Stable(control);
            HistoryPolicy effective=new HistoryPolicy(policy.quotaBytes,policy.automaticCount);HistoryGraph source=new HistoryGraph(store,catalog,identity,index,effective);
            HistoryGraph.Graph graph=source.scan();HistoryPruner.DeletePlan fresh=new HistoryPruner(source,preview.externalProtectedSnapshotIds).planSelected(authorization.selectedCheckpointIds).plan;
            if(fresh==null||!graph.complete||!graph.fingerprint.equals(fresh.graphFingerprint)||!Arrays.equals(proof(preview),proof(fresh)))throw new IOException("Stale selected authorization");
            selected(fresh,authorization);stable.full(graph);stable.control(control(policy),false);
            HistoryPruneIntent intent=HistoryPruneIntent.create(fresh,graph,new HistoryPruneIntent.PolicyBinding(policy.bundleId,policy.generation,policy.controlRecordHash,policy.quotaBytes,policy.automaticCount),authorization,HistoryMaintenanceRecovery.unaffectedFingerprint(graph,fresh.members));
            byte[] encoded=HistoryPruneIntent.encode(intent);
            if(control.actualBytes+control.controlReservedBytes>HistoryControlStore.MAX_BYTES-encoded.length||control.actualEntries+control.controlReservedEntries>=HistoryControlStore.MAX_ENTRIES)throw new IOException("Intent exhausts shared control allowance");
            publish(authority,policy,graph,stable,intent,encoded);
            stable.bindIntent(new HistoryControlStore(parent).inspect());
            HistoryMaintenanceRecovery.openLocked(store,catalog,identity,index,durability);
            return remove(HistoryStorage.installedView(store.storageRoot()),stable,Removal.selected(intent));
        }}
    }
    HistoryCapacity replay()throws IOException {
        synchronized(monitor){try(HistoryStorage.MutationScope lease=HistoryStorage.administration(store.storageRoot())){
            Retirement retirement=HistoryStorage.retirement(store.storageRoot());
            if(retirement!=null){retirement.removal.mode(HistoryPruner.Mode.SELECTED);retirement.requireBundle(roots);return retire(retirement);}
            HistoryControlStore.Inspection control=new HistoryControlStore(parent).inspect();requireBootstrap(control);
            if(control.intentStage!=null)throw new IOException("Retained intent stages require separate repair");
            if(control.committedIntent==null){
                if(HistoryStorage.installedView(store.storageRoot())!=null)throw new IOException("Installed intent evidence disappeared");
                return HistoryCapacity.openLocked(store,catalog,identity,index,durability);
            }
            HistoryPruneIntent intent=control.committedIntent.intent;if(intent==null||intent.mode!=HistoryPruner.Mode.SELECTED)throw new IOException("Only committed selected intent is replayable");
            Stable stable=new Stable(control);
            HistoryPins.reconcileMaintenanceAuditsLocked(store,catalog,identity,index,durability,intent,null);
            HistoryMaintenanceRecovery.openLocked(store,catalog,identity,index,durability);
            return remove(HistoryStorage.installedView(store.storageRoot()),stable,Removal.selected(intent));
        }}
    }
    AutomaticResult executeAutomaticFloor(HistoryCapacity authority,Cancellation cancellation)throws IOException {
        synchronized(monitor){try(HistoryStorage.MutationScope lease=HistoryStorage.administration(store.storageRoot())){
            HistoryStorage.requireOperational(store.storageRoot());
            if(cancellation==null||authority==null||HistoryStorage.capacity(store.storageRoot())!=authority)throw new IOException("Current installed authority and cancellation required");
            for(int i=0;i<roots.size();i++)authority.requireRole(roots.get(i),i+1);
            HistoryCapacity.EffectivePolicy policy=authority.policy();HistoryControlStore.Inspection control=control(policy);
            if(control.hasMaintenance()||HistoryStorage.installedView(store.storageRoot())!=null||HistoryPins.pruneBlocked(store.storageRoot(),control))throw new IOException("Maintenance or pins require reconciliation");
            if(!policy.automaticEnabled||policy.automaticCount==0)return new AutomaticResult(AutomaticStatus.DISABLED,authority,0,0);
            if(cancellation.isCancelled())return new AutomaticResult(AutomaticStatus.CANCELLED_BEFORE_PUBLICATION,authority,0,0);
            Stable stable=new Stable(control);
            HistoryGraph source=new HistoryGraph(store,catalog,identity,index,new HistoryPolicy(policy.quotaBytes,policy.automaticCount));
            HistoryGraph.Graph graph=source.scan();HistoryPruner.Result result=new HistoryPruner(source).planAutomaticFloorBatch();
            if(!graph.complete||graph.pruneBlocked)throw new IOException("Incomplete automatic graph");
            HistoryPruner.DeletePlan fresh=result.plan;
            if(fresh==null){
                if(result.blockedReason!=HistoryPruner.BlockedReason.NO_WORK)throw new IOException("Automatic plan blocked");
                stable.full(graph);stable.control(control(policy),false);
                return new AutomaticResult(AutomaticStatus.NO_WORK,authority,0,0);
            }
            if(!graph.fingerprint.equals(fresh.graphFingerprint))throw new IOException("Automatic graph changed");
            stable.full(graph);stable.control(control(policy),false);
            HistoryPruneIntent.Authorization evidence=new HistoryPruneIntent.Authorization(fresh.proofFingerprint,Collections.emptySet());
            HistoryPruneIntent intent=HistoryPruneIntent.create(fresh,graph,new HistoryPruneIntent.PolicyBinding(policy.bundleId,policy.generation,policy.controlRecordHash,policy.quotaBytes,policy.automaticCount),evidence,HistoryMaintenanceRecovery.unaffectedFingerprint(graph,fresh.members));
            Removal removal=Removal.floor(intent);removal.require(control(policy));byte[] encoded=HistoryPruneIntent.encode(intent);
            if(control.actualBytes+control.controlReservedBytes>HistoryControlStore.MAX_BYTES-encoded.length||control.actualEntries+control.controlReservedEntries>=HistoryControlStore.MAX_ENTRIES)throw new IOException("Intent exhausts shared control allowance");
            if(cancellation.isCancelled())return new AutomaticResult(AutomaticStatus.CANCELLED_BEFORE_PUBLICATION,authority,0,0);
            // Cutoff before first stage creation. Thereafter complete this one journal attempt;
            // cancellation never guesses whether a throwing move committed or rolls back evidence.
            publish(authority,policy,graph,stable,intent,encoded);
            stable.bindIntent(new HistoryControlStore(parent).inspect());
            HistoryMaintenanceRecovery.openLocked(store,catalog,identity,index,durability);
            HistoryCapacity terminal=remove(HistoryStorage.installedView(store.storageRoot()),stable,removal);
            return new AutomaticResult(AutomaticStatus.COMPLETED,terminal,intent.bytes,1);
        }}
    }
    /** Explicit restricted replay of inherited committed floor authorization, never a new plan. */
    HistoryCapacity replayAutomaticFloor()throws IOException {
        synchronized(monitor){try(HistoryStorage.MutationScope lease=HistoryStorage.administration(store.storageRoot())){
            Retirement retirement=HistoryStorage.retirement(store.storageRoot());
            if(retirement!=null){retirement.removal.mode(HistoryPruner.Mode.AUTOMATIC_FLOOR);retirement.requireBundle(roots);return retire(retirement);}
            HistoryControlStore.Inspection control=new HistoryControlStore(parent).inspect();requireBootstrap(control);
            if(control.intentStage!=null)throw new IOException("Retained intent stages require separate repair");
            if(control.committedIntent==null){
                if(HistoryStorage.installedView(store.storageRoot())!=null)throw new IOException("Installed intent evidence disappeared");
                return HistoryCapacity.openLocked(store,catalog,identity,index,durability);
            }
            HistoryPruneIntent intent=control.committedIntent.intent;Removal removal=Removal.floor(intent);removal.require(control);
            Stable stable=new Stable(control);
            HistoryPins.reconcileMaintenanceAuditsLocked(store,catalog,identity,index,durability,intent,null);
            removal.require(new HistoryControlStore(parent).inspect());
            HistoryMaintenanceRecovery.openLocked(store,catalog,identity,index,durability);
            return remove(HistoryStorage.installedView(store.storageRoot()),stable,removal);
        }}
    }
    /** Private exact-intent capability; no caller mode or preview grants unlink authority. */
    private static final class Removal {
        private final HistoryPruneIntent intent;
        private Removal(HistoryPruneIntent intent){this.intent=intent;}
        static Removal selected(HistoryPruneIntent intent)throws IOException {
            if(intent==null||intent.mode!=HistoryPruner.Mode.SELECTED)throw new IOException("Selected removal authority required");return new Removal(intent);
        }
        static Removal floor(HistoryPruneIntent intent)throws IOException {
            if(intent==null||intent.mode!=HistoryPruner.Mode.AUTOMATIC_FLOOR||intent.groups.size()!=1||intent.members.size()!=5||!intent.selectedCheckpointIds.isEmpty()||!intent.groups.get(0).checkpointIds.isEmpty()||intent.requiredBytes!=0||intent.requiredEntries!=0)throw new IOException("One-group floor authority required");return new Removal(intent);
        }
        void mode(HistoryPruner.Mode expected)throws IOException {if(intent.mode!=expected)throw new IOException("Foreign removal mode");}
        void view(HistoryTombstoneView view)throws IOException {
            if(view==null||!intent.envelopeHash.equals(view.intent.envelopeHash)||intent.mode!=view.intent.mode)throw new IOException("Foreign removal intent");
        }
        void current(File parent)throws IOException {if(intent.mode==HistoryPruner.Mode.AUTOMATIC_FLOOR)require(new HistoryControlStore(parent).inspect());}
        void require(HistoryControlStore.Inspection control)throws IOException {
            if(intent.mode==HistoryPruner.Mode.SELECTED)return;
            HistoryControlStore.Record policy=control.latest;HistoryPruneIntent.PolicyBinding expected=intent.policy;
            if(!control.chainComplete||!control.usageComplete||control.registration==null||policy==null||!control.stages.isEmpty()||control.intentStage!=null||!control.pins.valid||control.pins.active!=null||!control.pins.stages.isEmpty()||!control.operations.ready||control.operations.pending
                    ||control.actualBytes>HistoryControlStore.MAX_BYTES-control.controlReservedBytes||control.actualEntries>HistoryControlStore.MAX_ENTRIES-control.controlReservedEntries
                    ||!policy.automaticEnabled||policy.automaticCount<=0||!policy.bundleId.toString().equals(expected.bundleId)||policy.generation!=expected.generation||!policy.envelopeHash.equals(expected.envelopeHash)||policy.quotaBytes!=expected.quotaBytes||policy.automaticCount!=expected.automaticCount)throw new IOException("Automatic consent or policy changed");
            for(String diagnostic:control.diagnostics)if(!diagnostic.equals("control: retained maintenance"))throw new IOException("Incomplete automatic control");
        }
    }
    private static void selected(HistoryPruner.DeletePlan plan,HistoryPruneIntent.Authorization authorization)throws IOException {
        if(plan==null||authorization==null||plan.mode!=HistoryPruner.Mode.SELECTED||plan.requiredBytes!=0||plan.requiredEntries!=0||plan.members.isEmpty()
                ||!plan.proofFingerprint.equals(authorization.previewFingerprint)||!plan.selectedCheckpointIds.equals(authorization.selectedCheckpointIds))throw new IOException("Exact selected authorization required");
    }
    private static byte[] proof(HistoryPruner.DeletePlan plan)throws IOException {ByteArrayOutputStream bytes=new ByteArrayOutputStream();HistoryPruner.writeCanonicalProof(plan,bytes);return bytes.toByteArray();}
    private HistoryControlStore.Inspection control(HistoryCapacity.EffectivePolicy expected)throws IOException {
        HistoryControlStore.Inspection control=new HistoryControlStore(parent).inspect();requireBootstrap(control);HistoryPins.requireRetained(store.storageRoot(),control);
        if(!control.chainComplete||!control.usageComplete||control.registration==null||control.latest==null||!control.stages.isEmpty()||!control.pins.valid||control.pins.active!=null||!control.pins.stages.isEmpty()
                ||control.actualBytes+control.controlReservedBytes>HistoryControlStore.MAX_BYTES||control.actualEntries+control.controlReservedEntries>HistoryControlStore.MAX_ENTRIES)throw new IOException("Incomplete executor authority");
        for(String diagnostic:control.diagnostics)if(!diagnostic.equals("control: retained maintenance"))throw new IOException("Conflicting executor control evidence");
        if(!expected.bundleId.equals(control.latest.bundleId.toString())||expected.generation!=control.latest.generation||!expected.controlRecordHash.equals(control.latest.envelopeHash)
                ||expected.quotaBytes!=control.latest.quotaBytes||expected.automaticCount!=control.latest.automaticCount||expected.automaticEnabled!=control.latest.automaticEnabled)throw new IOException("Executor policy changed");
        return control;
    }
    private void requireBootstrap(HistoryControlStore.Inspection control)throws IOException {
        if(control.registration==null||control.registration.bootstrapQuotaBytes!=store.constructorQuotaBytes())throw new IOException("Conflicting executor bootstrap hint");
    }
    private void publish(HistoryCapacity authority,HistoryCapacity.EffectivePolicy policy,HistoryGraph.Graph graph,Stable stable,HistoryPruneIntent intent,byte[] bytes)throws IOException {
        File stage=new File(controlRoot,HistoryPruneIntent.STAGE_NAME),target=new File(controlRoot,HistoryPruneIntent.INTENT_NAME);
        absent(stage);absent(target);HistoryStorage.changed(store.storageRoot());Files.createFile(stage.toPath());BasicFileAttributes identity;
        try(FileOutputStream out=new FileOutputStream(stage)){out.write(bytes);out.flush();identity=attrs(stage);durability.syncFile(out.getFD());same(identity,attrs(stage));}
        publication(authority,policy,graph,stable,intent,stage,identity,true);
        durability.move(stage,target);absent(stage);same(identity,attrs(target));durability.syncDirectory(controlRoot);
        publication(authority,policy,graph,stable,intent,target,identity,false);
    }
    private void publication(HistoryCapacity authority,HistoryCapacity.EffectivePolicy policy,HistoryGraph.Graph graph,Stable stable,HistoryPruneIntent intent,File file,BasicFileAttributes identity,boolean stage)throws IOException {
        if(HistoryStorage.capacity(store.storageRoot())!=authority)throw new IOException("Superseded publication authority");
        HistoryControlStore.Inspection control=control(policy);HistoryControlStore.Entry entry=stage?control.intentStage:control.committedIntent;
        if(entry==null||entry.intent==null||!intent.envelopeHash.equals(entry.envelopeHash)||(stage?control.committedIntent!=null:control.intentStage!=null))throw new IOException("Intent publication changed");
        same(identity,attrs(file));stable.control(control,true);stable.full(graph);
    }
    private HistoryCapacity remove(HistoryTombstoneView view,Stable stable,Removal removal)throws IOException {
        removal.view(view);removal.current(parent);
        Set<String> closure=keys(view.intent);List<HistoryPruneIntent.Member> members=new ArrayList<>(view.intent.members);
        members.sort(Comparator.comparingInt((HistoryPruneIntent.Member member)->order(member.kind)).thenComparing(HistoryPruneIntent.Member::key));
        for(HistoryPruneIntent.Member member:members){
            File victim=file(member);boundary.at(Event.BEFORE_MEMBER_UNLINK,member.key());
            validate(view,stable,closure,removal);BasicFileAttributes present;
            try{present=attrs(victim);}catch(NoSuchFileException absent){continue;}
            if(!present.isRegularFile())throw new IOException("Nonregular maintenance victim");
            // No adapter, observer or callback between exact full proof above and this direct unlink.
            HistoryStorage.changed(store.storageRoot());Files.delete(victim.toPath());
            boundary.at(Event.AFTER_MEMBER_UNLINK,member.key());durability.syncDirectory(victim.getParentFile());
        }
        validate(view,stable,closure,removal);allAbsent(view.intent,roots);Retirement retirement=new Retirement(view,stable,roots,removal);
        HistoryStorage.startRetirement(store.storageRoot(),retirement);return retire(retirement);
    }
    private void validate(HistoryTombstoneView view,Stable stable,Set<String> closure,Removal removal)throws IOException {
        removal.view(view);removal.current(parent);
        if(HistoryStorage.installedView(store.storageRoot())!=view)throw new IOException("Installed maintenance capability changed");
        view.revalidate();stable.nativeIdentities(closure);HistoryControlStore.Inspection control=new HistoryControlStore(parent).inspect();stable.control(control,false);removal.require(control);
    }
    private HistoryCapacity retire(Retirement retirement)throws IOException {
        retirement.require(retirement.view);retirement.requireBundle(roots);requireBootstrap(new HistoryControlStore(parent).inspect());File intentFile=new File(controlRoot,HistoryPruneIntent.INTENT_NAME);
        boolean present;try{attrs(intentFile);present=true;}catch(NoSuchFileException absent){present=false;}
        if(present){
            retirement.present();for(File root:roots)durability.syncDirectory(root);
            boundary.at(Event.BEFORE_INTENT_UNLINK,HistoryPruneIntent.INTENT_NAME);
            retirement.present(); // includes exact intent inode, closure absence and all survivor evidence
            HistoryStorage.changed(store.storageRoot());Files.delete(intentFile.toPath());
            boundary.at(Event.AFTER_INTENT_UNLINK,HistoryPruneIntent.INTENT_NAME);
        }
        retirement.control();durability.syncDirectory(controlRoot);retirement.control();
        HistoryPins.reconcileMaintenanceAuditsLocked(store,catalog,identity,index,durability,retirement.view.intent,retirement);
        retirement.validate();HistoryCapacity candidate=HistoryCapacity.prepareTerminalLocked(retirement,store,catalog,identity,index,durability);
        HistoryStorage.finishRetirement(store.storageRoot(),retirement,candidate);return candidate;
    }
    /** Shared exact capability survives intent-unlink uncertainty; it is never a blanket bypass flag. */
    static final class Retirement {
        final HistoryTombstoneView view;private final Stable stable;private final List<File> roots;private final File root;private final Removal removal;
        private Retirement(HistoryTombstoneView view,Stable stable,List<File> roots,Removal removal){this.view=view;this.stable=stable;this.roots=new ArrayList<>(roots);root=roots.get(0);this.removal=removal;}
        void require(HistoryTombstoneView expected)throws IOException {
            HistoryStorage.requireMutation(root);
            if(expected!=view||HistoryStorage.retirement(root)!=this||HistoryStorage.installedView(root)!=view)throw new IOException("Foreign retirement capability");
        }
        void requireBundle(List<File> actual)throws IOException {if(!roots.equals(actual))throw new IOException("Foreign retirement roots");}
        void present()throws IOException {require(view);removal.view(view);view.revalidate();allAbsent(view.intent,roots);stable.nativeIdentities(keys(view.intent));HistoryControlStore.Inspection control=new HistoryControlStore(root.getParentFile()).inspect();stable.control(control,false);removal.require(control);}
        HistoryControlStore.Inspection control()throws IOException {
            require(view);allAbsent(view.intent,roots);HistoryControlStore.Inspection control=view.owner.terminalControl(view,this);
            stable.nativeIdentities(keys(view.intent));stable.control(control,true);removal.view(view);removal.require(control);return control;
        }
        HistoryGraph.Graph validate()throws IOException {control();HistoryGraph.Graph graph=view.owner.validateTerminal(view,this);control();return graph;}
    }
    private final class Stable {
        private final Map<File,BasicFileAttributes> nativeFiles=new LinkedHashMap<>(),controlFiles=new LinkedHashMap<>(),directories=new LinkedHashMap<>();
        private final Map<String,String> nativeRecords,controlRecords=new TreeMap<>();
        private final String operationFingerprint;
        Stable(HistoryControlStore.Inspection control)throws IOException {
            PrivateOperationStore.Ledger operations=PrivateOperationStore.inspect(store.storageRoot(),control,new HistoryStorage.Budget(HistoryStorage.METADATA_BUDGET));
            if(!operations.complete||operations.pending)throw new IOException("Incomplete operation ledger");operationFingerprint=operations.fingerprint;
            List<ProtectionStore.StoredRecord> records=raw();nativeRecords=recordMap(records);
            for(ProtectionStore.StoredRecord record:records){File file=logicalFile(record.rootRole,record.filename);nativeFiles.put(file,attrs(file));}
            for(HistoryControlStore.Entry entry:control.entries){if(entry.filename==null)throw new IOException("Unknown control entry");File file=controlFile(entry.filename);controlFiles.put(file,attrs(file));controlRecords.put(entry.filename,entry.length+":"+entry.envelopeHash);}
            directories.put(parent,attrs(parent));directories.put(controlRoot,attrs(controlRoot));for(File root:roots)directories.put(root,attrs(root));
        }
        void bindIntent(HistoryControlStore.Inspection control)throws IOException {
            HistoryControlStore.Entry intent=control.committedIntent;if(intent==null||control.intentStage!=null)throw new IOException("Missing published intent");
            controlFiles.put(controlFile(intent.filename),attrs(controlFile(intent.filename)));controlRecords.put(intent.filename,intent.length+":"+intent.envelopeHash);
        }
        void full(HistoryGraph.Graph graph)throws IOException {
            nativeIdentities(Collections.emptySet());Map<String,String> current=recordMap(raw());
            if(!nativeRecords.equals(current)||!current.equals(recordMap(graph.records)))throw new IOException("Native graph changed during callback");
        }
        void nativeIdentities(Set<String> removed)throws IOException {
            directories();for(Map.Entry<File,BasicFileAttributes> entry:nativeFiles.entrySet()){
                BasicFileAttributes now;try{now=attrs(entry.getKey());}catch(NoSuchFileException absent){String key=logicalKey(entry.getKey());if(removed.contains(key))continue;throw absent;}
                same(entry.getValue(),now);
            }
        }
        void control(HistoryControlStore.Inspection current,boolean changingIntent)throws IOException {
            directories();PrivateOperationStore.Ledger operations=PrivateOperationStore.inspect(store.storageRoot(),current,new HistoryStorage.Budget(HistoryStorage.METADATA_BUDGET));
            if(!operations.complete||operations.pending||!operationFingerprint.equals(operations.fingerprint))throw new IOException("Changed operation ledger");
            Map<String,String> records=new TreeMap<>();for(HistoryControlStore.Entry entry:current.entries){
                if(changingIntent&&(HistoryPruneIntent.INTENT_NAME.equals(entry.filename)||HistoryPruneIntent.STAGE_NAME.equals(entry.filename)))continue;
                records.put(entry.filename,entry.length+":"+entry.envelopeHash);
            }
            Map<String,String> expected=new TreeMap<>(controlRecords);if(changingIntent){expected.remove(HistoryPruneIntent.INTENT_NAME);expected.remove(HistoryPruneIntent.STAGE_NAME);}
            if(!expected.equals(records))throw new IOException("Maintenance control inventory changed");
            for(Map.Entry<File,BasicFileAttributes> entry:controlFiles.entrySet()){
                if(changingIntent&&entry.getKey().equals(new File(controlRoot,HistoryPruneIntent.INTENT_NAME)))continue;same(entry.getValue(),attrs(entry.getKey()));
            }
        }
        private void directories()throws IOException {for(Map.Entry<File,BasicFileAttributes> entry:directories.entrySet()){BasicFileAttributes before=entry.getValue(),now=attrs(entry.getKey());if(!now.isDirectory()||!Objects.equals(before.fileKey(),now.fileKey())||!before.creationTime().equals(now.creationTime()))throw new IOException("Maintenance root replaced");}}
    }
    private List<ProtectionStore.StoredRecord> raw()throws IOException {
        List<ProtectionStore.StoredRecord> records=new ArrayList<>();HistoryStorage.Budget budget=new HistoryStorage.Budget(HistoryStorage.METADATA_BUDGET);
        for(int i=0;i<roots.size();i++){HistoryStorage.FilesView files=HistoryStorage.inspect(roots.get(i),role(i),i==0?ProtectionStore.MAX_DOCUMENT_BYTES+32768L:HistoryMetadataIO.MAX_RECORD,i==0?65536:HistoryMetadataIO.MAX_ENTRIES,budget);
            if(!files.usageComplete||!files.diagnostics.isEmpty())throw new IOException("Incomplete executor native inventory");records.addAll(files.records);}
        return records;
    }
    private static Map<String,String> recordMap(List<ProtectionStore.StoredRecord> records){Map<String,String> result=new TreeMap<>();for(ProtectionStore.StoredRecord record:records)result.put(record.rootRole+"/"+record.filename,record.serializedBytes+":"+record.envelopeHash);return result;}
    private File logicalFile(String role,String filename)throws IOException {for(int i=0;i<4;i++)if(role(i).equals(role))return new File(roots.get(i),filename);throw new IOException("Unknown logical role");}
    private String logicalKey(File file)throws IOException {for(int i=0;i<4;i++)if(roots.get(i).equals(file.getParentFile()))return role(i)+"/"+file.getName();throw new IOException("Foreign logical file");}
    private File controlFile(String name){return new File(HistoryControlStore.ENROLLMENT_NAME.equals(name)?parent:controlRoot,name);}
    private File file(HistoryPruneIntent.Member member){return new File(roots.get(member.role.code-1),member.id+"."+member.kind.wire);}
    private static Set<String> keys(HistoryPruneIntent intent){Set<String> result=new HashSet<>();for(HistoryPruneIntent.Member member:intent.members)result.add(member.key());return result;}
    private static String role(int index){return index==0?"store":index==1?"catalog":index==2?"identity":"index";}
    private static int order(HistoryPruneIntent.Kind kind){switch(kind){case CHECKPOINT:return 0;case INDEX:return 1;case COMPLETE:return 2;case TRANSACTION:return 3;case SNAPSHOT:return 4;default:return 5;}}
    private static void allAbsent(HistoryPruneIntent intent,List<File> roots)throws IOException {
        for(HistoryPruneIntent.Member member:intent.members)absent(new File(roots.get(member.role.code-1),member.id+"."+member.kind.wire));
    }
    private static void absent(File file)throws IOException {try{attrs(file);throw new IOException("Expected absent maintenance path");}catch(NoSuchFileException absent){}}
    private static BasicFileAttributes attrs(File file)throws IOException{return Files.readAttributes(file.toPath(),BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);}
    private static void same(BasicFileAttributes before,BasicFileAttributes after)throws IOException {if(!before.isRegularFile()||!after.isRegularFile()||before.size()!=after.size()||!Objects.equals(before.fileKey(),after.fileKey())||!before.creationTime().equals(after.creationTime())||!before.lastModifiedTime().equals(after.lastModifiedTime()))throw new IOException("Maintenance file identity changed");}
}
