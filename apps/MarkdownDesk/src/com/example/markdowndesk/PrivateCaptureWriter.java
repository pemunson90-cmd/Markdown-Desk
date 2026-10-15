package com.example.markdowndesk;

import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.security.MessageDigest;

/** Durable local capture writer. PREPARE alone never acknowledges a captured bundle. */
final class PrivateCaptureWriter {
    private final File root, controlRoot;
    private final PrivateCapturePlan plan;
    private final ProtectionStore.Durability durability;
    private final Transition transition;
    private final long refreshBytes;
    private final HistoryStorage.Budget budget;
    private boolean nativeWritten;
    private boolean failed;

    private PrivateCaptureWriter(PrivateCapturePlan plan, File root,
            ProtectionStore.Durability durability, HistoryStorage.Budget budget) throws IOException {
        this.root = HistoryStorage.canonicalRoot(root);
        this.controlRoot = new File(root.getParentFile(), HistoryControlStore.ROOT_NAME);
        this.plan = plan; this.durability = durability; this.budget=budget;
        plan.sourceOwner.requireIdleBindingLocked(root, plan.prepare.preparation, budget);
        // This is a prepaid scratch owner, never another independent16MiB allowance.
        // Control decoding retains <=1MiB physical envelopes with bounded clones/DTOs;
        // reserve6MiB plus per-ledger tables before obtaining any refresh snapshot.
        long tables = PrivateOperationStore.add(4096L * (plan.prepare.artifacts.size() + 8),
                HistoryStorage.operationEvidenceBytes(root));
        refreshBytes = PrivateOperationStore.add(6L << 20, tables);
        budget.charge(refreshBytes);
        budget.charge(8192L + 4096L * HistoryControlStore.MAX_ENTRIES);
        Fresh first = refresh();
        if (!first.ledger.complete || first.ledger.pending || !first.control.operations.valid
                || !first.control.operations.ready || first.control.operations.pending
                || first.control.state != HistoryControlStore.State.VALIDATED
                || first.control.pins.active != null || !first.control.stages.isEmpty()
                || !PrivateCapturePlan.initialLedgerHash(first.ledger).equals(plan.initialLedgerHash)
                || !PrivateCapturePlan.initialControlHash(first.control).equals(plan.initialControlHash))
            throw new IOException("Capture initial ledger no longer matches its preparation");
        budget.charge(4096L * first.ledger.entries.size() + 2048L * first.ledger.controlIdentities.size());
        transition = new Transition(first);
    }
    /** The complete internal output lifetime stays under this caller's primary lease. */
    static PrivateCaptureWriter prepareLocked(PrivateCapturePlan plan, File root,
            ProtectionStore.Durability durability, HistoryStorage.Budget budget) throws IOException {
        HistoryStorage.requireMutation(root); HistoryOperations.rejectParticipant();
        if (plan == null || durability == null || budget == null)
            throw new IOException("Missing capture writer inputs");
        PrivateCaptureWriter writer = new PrivateCaptureWriter(plan, root, durability, budget);
        try { writer.publishPrepareLocked(); return writer; }
        catch (IOException | RuntimeException | Error failure) {
            writer.failed = true;
            // No later same-process attempt may guess that a failed internal FD or
            // publication is quiescent. Cold recovery is a separate typed protocol.
            plan.sourceOwner.invalidateLocked();
            throw failure;
        }
    }
    private void publishPrepareLocked() throws IOException {
        HistoryCapacity capacity = HistoryStorage.capacity(root);
        if (capacity == null) throw new IOException("Capture requires acknowledged shared capacity");
        capacity.requireWriter(root);
        File stage = new File(controlRoot, plan.prepare.name() + ".stage");
        File target = new File(controlRoot, plan.prepare.name());
        if (PrivateOperationStore.attributesOrAbsent(stage.toPath()) != null
                || PrivateOperationStore.attributesOrAbsent(target.toPath()) != null)
            throw new IOException("Capture PREPARE destination already exists");
        try (NativeCaptureFingerprint.PhysicalProof source = sourceProof(capacity)) {
            byte[] bytes = plan.prepare.encoded();
            HistoryStorage.changed(root);
            Files.createFile(stage.toPath());
            BasicFileAttributes created = PrivateOperationStore.attributes(stage.toPath());
            try (FileOutputStream output = new FileOutputStream(stage)) {
                output.write(bytes); output.flush();
                BasicFileAttributes written = PrivateOperationStore.attributes(stage.toPath());
                Physical.sameIdentity(created, written);
                transition.recordPrepareStage(written);
                verify(source, capacity);
                plan.sourceOwner.publicationCallbackLocked(()->durability.syncFile(output.getFD()));
                verify(source, capacity);
            }
            verify(source, capacity);
            // The expected committed identity comes from the known stage BEFORE
            // the callback. Never adopt whatever a move callback leaves behind.
            transition.recordPreparePromotion();
            plan.sourceOwner.publicationCallbackLocked(()->durability.move(stage, target));
            verify(source, capacity);
            plan.sourceOwner.publicationCallbackLocked(()->durability.syncDirectory(controlRoot));
            verify(source, capacity);
        }
    }
    /** Copies only immutable native-map/descriptor metadata; no BEGIN or capture result. */
    void writeNativeLocked() throws IOException {
        HistoryStorage.requireMutation(root); HistoryOperations.rejectParticipant();
        if(failed||nativeWritten)throw new IOException("Native capture metadata already written or attempt failed");
        plan.sourceOwner.requireIdleBindingLocked(root,plan.prepare.preparation,budget);
        HistoryCapacity capacity=HistoryStorage.capacity(root);
        if(capacity==null)throw new IOException("Capture requires acknowledged shared capacity");
        capacity.requireWriter(root);
        try(NativeCaptureFingerprint.PhysicalProof source=sourceProof(capacity)) {
            File namespace=new File(root.getParentFile(),PrivateOperationStore.ROOT);
            if((plan.prepare.directoryMask&1)!=0) {
                HistoryStorage.changed(root);Files.createDirectory(namespace.toPath());
                transition.recordDirectory(PrivateOperationStore.ROOT,PrivateOperationStore.attributes(namespace.toPath()));
                verify(source,capacity);plan.sourceOwner.publicationCallbackLocked(()->durability.syncDirectory(root.getParentFile()));verify(source,capacity);
            }
            File preparation=new File(namespace,plan.prepare.preparation.toString());
            HistoryStorage.changed(root);Files.createDirectory(preparation.toPath());
            transition.recordDirectory(plan.prepare.preparation.toString(),PrivateOperationStore.attributes(preparation.toPath()));
            verify(source,capacity);plan.sourceOwner.publicationCallbackLocked(()->durability.syncDirectory(namespace));verify(source,capacity);
            writeMetadata(plan.prepare.artifact(1),preparation,source,capacity);
            writeMetadata(plan.prepare.artifact(2),preparation,source,capacity);
            nativeWritten=true;
        }catch(IOException|RuntimeException|Error failure) { failed=true;plan.sourceOwner.invalidateLocked();throw failure; }
    }
    private void writeMetadata(PrivateOperationRecord.Artifact artifact,File preparation,
            NativeCaptureFingerprint.PhysicalProof source,HistoryCapacity capacity) throws IOException {
        if(artifact==null||(artifact.kind!=1&&artifact.kind!=2))throw new IOException("Exact native metadata artifact required");
        checked();
        File stage=new File(preparation,artifact.filename()+".stage"),target=new File(preparation,artifact.filename());
        if(PrivateOperationStore.attributesOrAbsent(stage.toPath())!=null||PrivateOperationStore.attributesOrAbsent(target.toPath())!=null)
            throw new IOException("Native metadata destination already exists");
        HistoryStorage.changed(root);Files.createFile(stage.toPath());
        BasicFileAttributes created=PrivateOperationStore.attributes(stage.toPath());
        MessageDigest digest=HistoryStorage.digest();final long[] count={0};
        try(FileOutputStream file=new FileOutputStream(stage)) {
            BufferedOutputStream buffered=new BufferedOutputStream(file,65536);
            OutputStream tracked=new OutputStream() {
                public void write(int value)throws IOException {
                    long next=PrivateOperationStore.add(count[0],1);if(next>artifact.length)throw new IOException("Native metadata exceeds its immutable artifact");
                    buffered.write(value);digest.update((byte)value);count[0]=next;
                }
                public void write(byte[] bytes,int offset,int length)throws IOException {
                    long next=PrivateOperationStore.add(count[0],length);
                    if(next>artifact.length)throw new IOException("Native metadata exceeds its immutable artifact");
                    buffered.write(bytes,offset,length);digest.update(bytes,offset,length);count[0]=next;
                }
            };
            if(artifact.kind==1)plan.nativeMap.write(tracked);else tracked.write(plan.descriptor.encoded());
            buffered.flush();file.flush();
            if(count[0]!=artifact.length||!artifact.hash.equals(HistoryStorage.hex(digest.digest())))
                throw new IOException("Native metadata differs from its immutable artifact");
            BasicFileAttributes written=PrivateOperationStore.attributes(stage.toPath());Physical.sameIdentity(created,written);
            transition.recordArtifactStage(artifact,written);verify(source,capacity);
            plan.sourceOwner.publicationCallbackLocked(()->durability.syncFile(file.getFD()));verify(source,capacity);
        }
        verify(source,capacity);transition.recordArtifactPromotion(artifact);
        plan.sourceOwner.publicationCallbackLocked(()->durability.move(stage,target));verify(source,capacity);
        plan.sourceOwner.publicationCallbackLocked(()->durability.syncDirectory(preparation));verify(source,capacity);
    }
    private NativeCaptureFingerprint.PhysicalProof sourceProof(HistoryCapacity capacity) throws IOException {
        Fresh fresh = checked(); return plan.nativeSources.prove(fresh.control, capacity.policy());
    }
    private void verify(NativeCaptureFingerprint.PhysicalProof source, HistoryCapacity capacity) throws IOException {
        Fresh fresh = checked(); source.recheck(fresh.control, capacity.policy());
    }
    private Fresh checked() throws IOException {
        HistoryStorage.requireMutation(root);
        if (failed) throw new IOException("Capture writer attempt failed");
        Fresh fresh = refresh(); transition.verify(fresh); return fresh;
    }
    /** Snapshots remain local to one refresh call chain; no rows escape the writer. */
    private Fresh refresh() throws IOException {
        HistoryStorage.requireMutation(root);
        HistoryStorage.Budget prepaid = new HistoryStorage.Budget(refreshBytes);
        prepaid.charge(4L << 20); // prepaid control decode/DTO/clone workspace
        HistoryControlStore.Inspection control = new HistoryControlStore(root.getParentFile()).inspect();
        PrivateOperationStore.Ledger ledger = PrivateOperationStore.inspect(root, control, prepaid);
        return new Fresh(control, ledger);
    }
    private static final class Fresh {
        final HistoryControlStore.Inspection control;
        final PrivateOperationStore.Ledger ledger;
        Fresh(HistoryControlStore.Inspection control, PrivateOperationStore.Ledger ledger) { this.control=control; this.ledger=ledger; }
    }
    private static final class Physical {
        final Object key;
        final long size;
        final java.nio.file.attribute.FileTime modified, created;
        Physical(BasicFileAttributes a) throws IOException {
            if (!a.isRegularFile() || a.isSymbolicLink() || a.fileKey()==null)
                throw new IOException("Capture control entry is not an owned regular file");
            key=a.fileKey(); size=a.size(); modified=a.lastModifiedTime(); created=a.creationTime();
        }
        void verify(BasicFileAttributes a) throws IOException {
            if (!a.isRegularFile() || a.isSymbolicLink() || !Objects.equals(key,a.fileKey())
                    || size!=a.size() || !modified.equals(a.lastModifiedTime()) || !created.equals(a.creationTime()))
                throw new IOException("Capture control bytes/identity changed");
        }
        String ledgerIdentity() { return key+":"+created+":"+modified+":"+size; }
        static void sameIdentity(BasicFileAttributes before,BasicFileAttributes after) throws IOException {
            if (before.fileKey()==null || !after.isRegularFile() || after.isSymbolicLink()
                    || !Objects.equals(before.fileKey(),after.fileKey()))
                throw new IOException("Capture stage identity changed while writing");
        }
    }
    private static final class Expected {
        final long bytes; final String hash; final boolean stage; final Physical physical;
        Expected(HistoryControlStore.Entry entry,Physical physical) { bytes=entry.length;hash=entry.envelopeHash;stage=entry.stage;this.physical=physical; }
    }
    /** Explicit PREPARE transitions only. No ignored-prefix or accept-current-state API. */
    private final class Transition {
        private final Map<String,Expected> controls=new TreeMap<>();
        private final Map<String,String> data, operationControls;
        private final Map<String,BasicFileAttributes> dataIdentities=new TreeMap<>();
        private final Map<String,OwnedData> ownData=new TreeMap<>();
        private long ownBytes;
        private final class OwnedData {
            final BasicFileAttributes attributes;final String value;final PrivateOperationRecord.Artifact artifact;
            OwnedData(BasicFileAttributes attributes,PrivateOperationRecord.Artifact artifact)throws IOException {
                this.attributes=attributes;this.artifact=artifact;
                if(artifact==null) {directory(attributes,attributes);value="dir:"+attributes.fileKey();}
                else {new Physical(attributes);if(attributes.size()!=artifact.length)throw new IOException("Incomplete owned metadata stage");value="file:"+artifact.length+":"+attributes.fileKey()+":"+artifact.hash;}
            }
        }
        private final long actual, reserved, occupied, slots, controlActual, controlReserved;
        private final int controlCount, controlSlots;
        private final boolean initialPresent;
        private final BasicFileAttributes controlDirectory, parentDirectory;
        private int phase; //0 baseline,1 exact full PREPARE.stage,2 committed PREPARE
        private Physical preparationIdentity;
        Transition(Fresh initial) throws IOException {
            HistoryControlStore.Inspection c=initial.control; PrivateOperationStore.Ledger l=initial.ledger;
            if (!l.complete || !c.chainComplete || !c.usageComplete || !c.diagnostics.isEmpty() || c.hasMaintenance())
                throw new IOException("Incomplete capture baseline");
            controlDirectory=PrivateOperationStore.attributes(controlRoot.toPath());
            parentDirectory=PrivateOperationStore.attributes(root.getParentFile().toPath());
            directory(controlDirectory,controlDirectory);directory(parentDirectory,parentDirectory);
            for(HistoryControlStore.Entry e:c.entries) {
                if(e.filename==null||e.stage||e.envelopeHash==null||controls.containsKey(e.filename))
                    throw new IOException("Uncertain capture baseline control entry");
                controls.put(e.filename,new Expected(e,new Physical(PrivateOperationStore.attributes(file(e.filename).toPath()))));
            }
            data=Collections.unmodifiableMap(new TreeMap<>(l.entries));
            operationControls=Collections.unmodifiableMap(new TreeMap<>(l.controlIdentities));
            for(String key:data.keySet())dataIdentities.put(key,PrivateOperationStore.attributes(dataFile(key).toPath()));
            initialPresent=l.present;actual=l.actualBytes;reserved=l.reservedBytes;occupied=l.occupiedEntries;slots=l.reservedEntries;
            controlActual=c.actualBytes;controlReserved=c.controlReservedBytes;controlCount=c.actualEntries;controlSlots=c.controlReservedEntries;
            verify(initial);
        }
        private File file(String name) {
            return HistoryControlStore.ENROLLMENT_NAME.equals(name)?new File(root.getParentFile(),name):new File(controlRoot,name);
        }
        private File dataFile(String key) {
            File namespace=new File(root.getParentFile(),PrivateOperationStore.ROOT);
            return PrivateOperationStore.ROOT.equals(key)?namespace:new File(namespace,key);
        }
        private String ownName() { return plan.prepare.name()+(phase==1?".stage":""); }
        void recordPrepareStage(BasicFileAttributes written) throws IOException {
            if(phase!=0||written.size()!=plan.prepare.encoded().length)throw new IOException("Invalid owned PREPARE stage transition");
            preparationIdentity=new Physical(written);phase=1;
        }
        void recordPreparePromotion() throws IOException {
            if(phase!=1)throw new IOException("Invalid owned PREPARE promotion");phase=2;
        }
        void recordDirectory(String key,BasicFileAttributes attributes)throws IOException {
            boolean namespace=PrivateOperationStore.ROOT.equals(key);
            if(phase!=2||namespace&&(plan.prepare.directoryMask&1)==0
                    ||!namespace&&!plan.prepare.preparation.toString().equals(key)
                    ||data.containsKey(key)||ownData.containsKey(key))throw new IOException("Unfunded or repeated capture directory");
            ownData.put(key,new OwnedData(attributes,null));
        }
        void recordArtifactStage(PrivateOperationRecord.Artifact artifact,BasicFileAttributes written)throws IOException {
            String key=plan.prepare.preparation+"/"+artifact.filename()+".stage";
            if(phase!=2||!plan.prepare.artifacts.contains(artifact)||!ownData.containsKey(plan.prepare.preparation.toString())
                    ||data.containsKey(key)||ownData.containsKey(key)||ownData.containsKey(key.substring(0,key.length()-6)))
                throw new IOException("Invalid owned metadata stage transition");
            ownData.put(key,new OwnedData(written,artifact));ownBytes=PrivateOperationStore.add(ownBytes,artifact.length);
        }
        void recordArtifactPromotion(PrivateOperationRecord.Artifact artifact)throws IOException {
            String key=plan.prepare.preparation+"/"+artifact.filename()+".stage";
            OwnedData old=ownData.get(key);
            if(old==null||old.artifact!=artifact)throw new IOException("Foreign metadata stage promotion");
            ownData.remove(key);ownData.put(key.substring(0,key.length()-6),old);
        }
        void verify(Fresh fresh) throws IOException {
            HistoryControlStore.Inspection c=fresh.control;PrivateOperationStore.Ledger l=fresh.ledger;
            directory(controlDirectory,PrivateOperationStore.attributes(controlRoot.toPath()));
            directory(parentDirectory,PrivateOperationStore.attributes(root.getParentFile().toPath()));
            if(!l.complete||!c.chainComplete||!c.usageComplete||!c.operations.valid||!c.pins.valid
                    ||!c.diagnostics.isEmpty()||c.hasMaintenance()||c.pins.active!=null
                    ||c.entries.size()!=controls.size()+(phase==0?0:1))
                throw new IOException("Uncertain or foreign capture transition");
            for(HistoryControlStore.Entry e:c.entries) {
                Expected old=controls.get(e.filename);
                if(old!=null) {
                    if(e.length!=old.bytes||e.stage!=old.stage||!Objects.equals(e.envelopeHash,old.hash))
                        throw new IOException("Existing capture control changed");
                    old.physical.verify(PrivateOperationStore.attributes(file(e.filename).toPath()));
                } else {
                    if(phase==0||!ownName().equals(e.filename)||e.stage!=(phase==1)
                            ||e.length!=preparationIdentity.size||!plan.prepare.envelopeHash.equals(e.envelopeHash))
                        throw new IOException("Unexpected capture control publication");
                    preparationIdentity.verify(PrivateOperationStore.attributes(file(e.filename).toPath()));
                }
            }
            long futureEntries=phase==0?0:plan.prepare.artifacts.size()+1L+((plan.prepare.directoryMask&1)!=0?1:0);
            if(l.entries.size()!=data.size()+ownData.size()
                    ||l.actualBytes!=PrivateOperationStore.add(actual,ownBytes)||l.occupiedEntries!=PrivateOperationStore.add(occupied,ownData.size())
                    ||l.reservedBytes!=PrivateOperationStore.add(reserved,(phase==0?0:plan.artifactBytes)-ownBytes)
                    ||l.reservedEntries!=PrivateOperationStore.add(slots,futureEntries-ownData.size())
                    ||l.present!=(phase!=0||initialPresent)||l.pending!=(phase!=0))
                throw new IOException("Capture data or credit ownership changed");
            for(Map.Entry<String,String> e:data.entrySet())if(!e.getValue().equals(l.entries.get(e.getKey())))throw new IOException("Existing capture data changed");
            for(Map.Entry<String,OwnedData> e:ownData.entrySet()) {
                OwnedData expected=e.getValue();
                if(!expected.value.equals(l.entries.get(e.getKey())))throw new IOException("Owned capture data changed");
                BasicFileAttributes now=PrivateOperationStore.attributes(dataFile(e.getKey()).toPath());
                if(expected.artifact==null)directory(expected.attributes,now);else new Physical(expected.attributes).verify(now);
            }
            for(Map.Entry<String,BasicFileAttributes> e:dataIdentities.entrySet()) {
                BasicFileAttributes now=PrivateOperationStore.attributes(dataFile(e.getKey()).toPath());
                if(e.getValue().isDirectory())directory(e.getValue(),now);
                else {PrivateOperationStore.same(e.getValue(),now);if(!e.getValue().creationTime().equals(now.creationTime()))throw new IOException("Old capture data physically replaced");}
            }
            if(l.controlIdentities.size()!=operationControls.size()+(phase==0?0:1))throw new IOException("Foreign operation control identity");
            for(Map.Entry<String,String> e:operationControls.entrySet())if(!e.getValue().equals(l.controlIdentities.get(e.getKey())))throw new IOException("Old operation control replaced");
            if(phase!=0&&!preparationIdentity.ledgerIdentity().equals(l.controlIdentities.get(ownName())))throw new IOException("Owned PREPARE replaced");
            if(c.actualBytes!=PrivateOperationStore.add(controlActual,phase==0?0:preparationIdentity.size)
                    ||c.actualEntries!=controlCount+(phase==0?0:1)
                    ||c.controlReservedBytes!=PrivateOperationStore.add(controlReserved,phase==0?0:PrivateOperationRecord.BEGIN_BYTES+HistoryPinRecord.CLOSE_BYTES+PrivateOperationRecord.LINK_BYTES)
                    ||c.controlReservedEntries!=controlSlots+(phase==0?0:3))throw new IOException("Capture control credit conversion changed");
        }
        private void directory(BasicFileAttributes before,BasicFileAttributes after) throws IOException {
            // Directory time defaults may follow its modified time. Our own
            // publications change that time; bind directory type/key instead.
            if(!before.isDirectory()||before.fileKey()==null||!after.isDirectory()||after.isSymbolicLink()
                    ||!Objects.equals(before.fileKey(),after.fileKey()))
                throw new IOException("Capture directory replaced");
        }
    }
}
