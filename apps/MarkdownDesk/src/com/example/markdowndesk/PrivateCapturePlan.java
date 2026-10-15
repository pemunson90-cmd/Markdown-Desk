package com.example.markdowndesk;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.charset.CodingErrorAction;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.util.*;

/** Immutable record assembly only. No PREPARE publication, source copy or consumer authority. */
final class PrivateCapturePlan {
    final HistoryArchiveManifest nativeMap;
    final NativeCaptureFingerprint nativeSources;
    final PreparationSources sourceOwner;
    final List<PrivateFramePreflight.ClosedFrame> privateFrames;
    final PrivateOperationRecord prepare,descriptor,link;
    final String initialLedgerHash,initialControlHash;
    final long artifactBytes;final PortableInputs portableInputs;
    private static final long FIXED_METADATA_BYTES=256L<<10,FRAME_METADATA_BYTES=4096;

    /**
     * Caller already charges the live graph/map/control/ledger and preflight/capture
     * owners to this SAME budget. Reserve assembly records/copies before allocation;
     * NativeCaptureFingerprint separately charges its native proof workspace here.
     * The mandatory native helper reads exact existing files; assembly otherwise
     * only streams immutable metadata, with no provider/source callbacks or writes.
     */
    static PrivateCapturePlan create(File store,File catalog,File identity,File index,
            HistoryArchiveManifest frozen,HistoryGraph.Graph graph,HistoryControlStore.Inspection control,
            PrivateOperationStore.Ledger baseLedger,PreparationSources owner,UUID preparation,UUID operation,
            UUID mapId,UUID descriptorId,List<PrivateFramePreflight.ClosedFrame> frames,
            long bootstrapQuotaBytes,HistoryStorage.Budget sharedBudget)throws IOException {
        HistoryStorage.requireMutation(store);PreparationSources.rejectParticipant();HistoryOperations.rejectParticipant();
        if(owner==null||sharedBudget==null||frames==null||frames.size()>14)throw invalid("Missing/bounded assembly inputs");
        owner.requireIdleBindingLocked(store,preparation,sharedBudget);
        sharedBudget.charge(FIXED_METADATA_BYTES+FRAME_METADATA_BYTES*frames.size());
        if(frozen==null||graph==null||control==null||baseLedger==null||!graph.complete||!graph.usageComplete||(graph.pruneBlocked&&!graph.historicalCancelledOnly)
                ||!graph.diagnostics.isEmpty()||graph.effectivePolicy==null||control.state!=HistoryControlStore.State.VALIDATED
                ||!control.chainComplete||!control.usageComplete||!control.diagnostics.isEmpty()||!control.stages.isEmpty()
                ||control.hasMaintenance()||!control.pins.valid||control.pins.active!=null||!control.pins.stages.isEmpty()
                ||!control.operations.valid||!control.operations.ready||control.registration==null||control.latest==null
                ||graph.controlActualBytes!=control.actualBytes||graph.controlActualEntries!=control.actualEntries)
            throw invalid("Incomplete initial graph/control authority");
        validateLedger(baseLedger,graph,control);
        if(baseLedger.pending)PrivateOperationStore.requireHistoricalCaptureOwnerLocked(store,control,baseLedger,owner,preparation,sharedBudget);
        String controlHash=initialControlHash(control);
        List<PrivateFramePreflight.ClosedFrame> retained=Collections.unmodifiableList(new ArrayList<>(frames));
        Set<UUID> generated=new HashSet<>();generated.add(frozen.bundleId);
        unique(generated,preparation);unique(generated,operation);unique(generated,mapId);unique(generated,descriptorId);
        int previous=0,tabs=0,recovery=0,preferences=0;Set<String> sourceTabIds=new HashSet<>();
        List<PrivateOperationRecord.Artifact> privateRows=new ArrayList<>(retained.size());
        for(PrivateFramePreflight.ClosedFrame frame:retained){
            if(frame==null||!frame.belongsTo(owner))throw invalid("Foreign private preflight owner");
            int kind=frame.kind.wireId;
            if(kind<previous)throw invalid("Noncanonical private frame order");previous=kind;
            if(frame.kind==PrivateStateCodec.Kind.SESSION_TAB){
                if(frame.ordinal!=tabs++||tabs>12||!canonicalHash(frame.sourceTabIdHash)||!sourceTabIds.add(frame.sourceTabIdHash))throw invalid("Noncanonical/duplicate source tab");
            }else if(frame.kind==PrivateStateCodec.Kind.RECOVERY){
                if(frame.ordinal!=0||++recovery>1||frame.sourceTabIdHash!=null)throw invalid("Noncanonical recovery");
            }else if(frame.kind==PrivateStateCodec.Kind.PREFERENCES){
                if(frame.ordinal!=0||++preferences>1||frame.sourceTabIdHash!=null)throw invalid("Noncanonical preferences");
            }else throw invalid("Unknown private frame kind");
            unique(generated,frame.itemId);unique(generated,frame.artifactId);
            privateRows.add(new PrivateOperationRecord.Artifact(kind,frame.artifactId,frame.itemId,frame.ordinal,frame.envelopeBytes,frame.envelopeHash));
        }
        int phase=retained.isEmpty()?1:2,flags=phase==1?0:2+recovery;
        if(phase==2&&preferences!=1)throw invalid("Phase B requires preferences");
        // Manifest construction/read already proved uniqueness among immutable outer
        // IDs. Stream against the <=33 new/bundle IDs: no second 16K-member index.
        for(HistoryArchiveManifest.Entry e:frozen.entries)if(generated.contains(e.archiveId))throw invalid("Native outer/archive ID collision");
        for(HistoryPinRecord begin:control.pins.begins)if(generated.contains(begin.operation))throw invalid("Existing operation ID collision");
        for(PrivateOperationRecord existing:control.operations.preparations){
            if(generated.contains(existing.preparation))throw invalid("Existing preparation ID collision");
        }
        if(baseLedger.entries.containsKey(preparation.toString()))throw invalid("Preparation directory already exists");
        NativeCaptureFingerprint nativeProof=NativeCaptureFingerprint.capture(frozen,graph,control,store,catalog,identity,index,bootstrapQuotaBytes,sharedBudget,owner,preparation);
        String ledgerHash=initialLedgerHash(baseLedger);
        HashSink map=new HashSink();frozen.write(map);
        if(map.length!=frozen.serializedBytes)throw invalid("Frozen map serialized length mismatch");
        PrivateOperationRecord.Binding binding=new PrivateOperationRecord.Binding(preparation,phase,frozen.bundleId,
            HistoryStorage.hex(frozen.registrationHashBytes()),frozen.policyGeneration,HistoryStorage.hex(frozen.policyHashBytes()),
            bootstrapQuotaBytes,frozen.quotaBytes,frozen.automaticCount,control.registration.roles,nativeProof.fingerprint,ledgerHash,flags);
        PrivateOperationRecord.Artifact mapRow=new PrivateOperationRecord.Artifact(1,mapId,PrivateOperationRecord.ZERO,0,map.length,map.hash());
        PrivateOperationRecord descriptor=PrivateOperationRecord.descriptor(binding,mapRow,privateRows);
        PrivateOperationRecord.Artifact descriptorRow=new PrivateOperationRecord.Artifact(2,descriptorId,PrivateOperationRecord.ZERO,0,descriptor.encoded().length,descriptor.envelopeHash);
        List<PrivateOperationRecord.Artifact> all=new ArrayList<>(2+privateRows.size());all.add(mapRow);all.add(descriptorRow);all.addAll(privateRows);
        long bytes=0;for(PrivateOperationRecord.Artifact row:all)bytes=add(bytes,row.length);
        HistoryPinRecord begin=HistoryPinRecord.begin(operation,control.registration,control.latest,HistoryPinRecord.Purpose.EXPORT,HistoryPinRecord.Scope.ALL_BUNDLE,
            new HistoryPinRecord.Manifest(HistoryPinRecord.Domain.LOCAL_CAPTURE_V1,frozen.entries.size()+retained.size(),descriptorRow.length,descriptor.envelopeHash),Collections.emptyList());
        boolean namespacePresent=baseLedger.entries.containsKey(PrivateOperationStore.ROOT);
        PrivateOperationRecord prepare=PrivateOperationRecord.prepare(binding,namespacePresent?2:3,all,begin);
        PrivateOperationRecord link=PrivateOperationRecord.link(prepare);
        admit(graph.chargedBytes,bytes,frozen.quotaBytes,control.actualBytes,control.controlReservedBytes,prepare.encoded().length,
            add(control.actualEntries,control.controlReservedEntries),baseLedger.occupiedEntries,baseLedger.reservedEntries,namespacePresent?1:2,all.size());
        return new PrivateCapturePlan(frozen,nativeProof,owner,retained,prepare,descriptor,link,ledgerHash,controlHash,bytes,null);
    }
    private PrivateCapturePlan(HistoryArchiveManifest map,NativeCaptureFingerprint proof,PreparationSources owner,List<PrivateFramePreflight.ClosedFrame> frames,
            PrivateOperationRecord prepare,PrivateOperationRecord descriptor,PrivateOperationRecord link,String ledger,String control,long bytes,PortableInputs portableInputs){
        nativeMap=map;nativeSources=proof;sourceOwner=owner;privateFrames=frames;this.prepare=prepare;this.descriptor=descriptor;this.link=link;initialLedgerHash=ledger;initialControlHash=control;artifactBytes=bytes;this.portableInputs=portableInputs;
    }
    /** Real portable record assembly. Inputs were measured outside this monitor and own no P. */
    static PrivateCapturePlan createPortable(File store,File catalog,File identity,File index,
            HistoryArchiveManifest frozen,HistoryGraph.Graph graph,HistoryControlStore.Inspection control,
            PrivateOperationStore.Ledger ledger,PreparationSources owner,UUID preparation,UUID operation,
            UUID mapId,UUID descriptorId,List<PrivateFramePreflight.ClosedFrame> frames,long bootstrap,
            PortableSelection selection,PortableInputs inputs,HistoryStorage.Budget budget)throws IOException {
        HistoryStorage.requireMutation(store);PreparationSources.rejectParticipant();HistoryOperations.rejectParticipant();
        if(inputs==null)throw invalid("Missing actual portable inputs");
        inputs.require(store,catalog,identity,index,frozen,graph,control,ledger,owner,preparation,operation,mapId,descriptorId,frames,bootstrap,selection,budget);
        budget.charge(FIXED_METADATA_BYTES+FRAME_METADATA_BYTES*frames.size());
        inputs.baseline.requireOriginalLocked(control,ledger,owner,budget);
        try(NativeCaptureFingerprint.PhysicalProof proof=inputs.nativeProof.prove(control,graph.effectivePolicy)) {
            inputs.validateJoins();
            PrivateOperationRecord.Binding binding=PrivateOperationRecord.Binding.portable(preparation,frozen.bundleId,
                    HistoryStorage.hex(frozen.registrationHashBytes()),frozen.policyGeneration,HistoryStorage.hex(frozen.policyHashBytes()),
                    bootstrap,frozen.quotaBytes,frozen.automaticCount,control.registration.roles,inputs.nativeProof.fingerprint,initialLedgerHash(ledger),2+(selection.recovery==null?0:1));
            List<PrivateOperationRecord.Artifact> tail=new ArrayList<>();
            tail.add(PrivateOperationRecord.Artifact.portable(3,inputs.inventoryId,PrivateOperationRecord.ZERO,0,inputs.inventoryBytes.length(),inputs.inventoryBytes.hash()));
            tail.add(PrivateOperationRecord.Artifact.portable(4,inputs.evidenceId,PrivateOperationRecord.ZERO,0,inputs.capsuleLength,inputs.capsuleHash));
            tail.add(PrivateOperationRecord.Artifact.portable(5,inputs.catalogId,PrivateOperationRecord.ZERO,0,inputs.catalogBytes.length(),inputs.catalogBytes.hash()));
            for(PrivateFramePreflight.ClosedFrame f:frames)tail.add(PrivateOperationRecord.Artifact.portable(f.kind.wireId,f.artifactId,f.itemId,f.ordinal,f.envelopeBytes,f.envelopeHash));
            PrivateOperationRecord.Artifact map=PrivateOperationRecord.Artifact.portable(1,mapId,PrivateOperationRecord.ZERO,0,inputs.mapLength,inputs.mapHash);
            PrivateOperationRecord descriptor=PrivateOperationRecord.descriptorPortable(binding,inputs.inventoryOuterId,map,tail);
            PrivateOperationRecord.Artifact descriptorRow=PrivateOperationRecord.Artifact.portable(2,descriptorId,PrivateOperationRecord.ZERO,0,descriptor.encoded().length,descriptor.envelopeHash);
            List<PrivateOperationRecord.Artifact> all=new ArrayList<>();all.add(map);all.add(descriptorRow);all.addAll(tail);
            HistoryPinRecord begin=HistoryPinRecord.beginPortable(operation,control.registration,control.latest,
                    new HistoryPinRecord.Manifest(HistoryPinRecord.Domain.PORTABLE_CAPTURE_V1,inputs.items.size(),descriptorRow.length,descriptorRow.hash));
            boolean present=ledger.entries.containsKey(PrivateOperationStore.ROOT);
            PrivateOperationRecord prepare=PrivateOperationRecord.preparePortable(binding,present?2:3,all,begin),link=PrivateOperationRecord.linkPortable(prepare);
            long bytes=0;for(PrivateOperationRecord.Artifact a:all)bytes=add(bytes,a.length);
            admitPortable(graph.chargedBytes,bytes,frozen.quotaBytes,control,prepare,ledger,present?1:2,all.size());
            proof.recheck(control,graph.effectivePolicy);
            return new PrivateCapturePlan(frozen,inputs.nativeProof,owner,inputs.frames,prepare,descriptor,link,initialLedgerHash(ledger),initialControlHash(control),bytes,inputs);
        }
    }
    private static void admitPortable(long data,long artifacts,long quota,HistoryControlStore.Inspection control,
            PrivateOperationRecord p,PrivateOperationStore.Ledger ledger,int directories,int slots)throws IOException {
        if(slots<6||slots>19||add(data,artifacts)>quota
                ||add(add(control.actualBytes,control.controlReservedBytes),add(p.encoded().length,204+173+126))>HistoryControlStore.MAX_BYTES
                ||add(add(control.actualEntries,control.controlReservedEntries),4)>HistoryControlStore.MAX_ENTRIES
                ||add(add(ledger.occupiedEntries,ledger.reservedEntries),directories+slots)>PrivateOperationStore.MAX_ENTRIES)
            throw invalid("Insufficient portable capture funding");
    }

    /** Concrete frozen producer inputs; no public DTO/source-path constructor or authority. */
    static final class PortableInputs implements AutoCloseable {
        final HistoryArchiveManifest nativeMap;final PrivateOperationStore.PortableBaseline baseline;
        final UUID inventoryId,inventoryOuterId,evidenceId,catalogId,scopeId,evidenceOuterId,evidenceItemId;
        final List<PrivateFramePreflight.ClosedFrame> frames;
        final PrivateBundleInventory.MetadataBudget metadata;
        final NativeCaptureFingerprint nativeProof;
        private final File store,catalog,identity,index;private final HistoryGraph.Graph graph;
        private final HistoryControlStore.Inspection control;private final PrivateOperationStore.Ledger ledger;
        private final PreparationSources owner;private final UUID preparation,operation,mapId,descriptorId;
        private final PortableSelection selection;private final HistoryStorage.Budget budget;private final long bootstrap;
        private final HashInput graphInput,c0Input,ledgerInput,nativeInput;
        private final List<UUID> privateOuters=new ArrayList<>();
        private final List<Item> items=new ArrayList<>();private final List<EvidenceSlot> evidence=new ArrayList<>();
        private HashInput inventoryBytes,catalogBytes;private PrivateBundleInventory inventory;
        private long capsuleLength,capsuleMetadata,mapLength;private String capsuleHash,mapHash;
        private boolean measured,measuring,failed,closed;private EvidenceCursor pending;
        private PortableInputs(File store,File catalog,File identity,File index,HistoryArchiveManifest map,HistoryGraph.Graph graph,
                HistoryControlStore.Inspection control,PrivateOperationStore.Ledger ledger,PreparationSources owner,
                UUID preparation,UUID operation,UUID mapId,UUID descriptorId,List<PrivateFramePreflight.ClosedFrame> frames,
                PortableSelection selection,long bootstrap,HistoryStorage.Budget budget,HashInput graphInput,
                PrivateOperationStore.PortableBaseline baseline,NativeCaptureFingerprint proof,Set<UUID> ids)throws IOException {
            this.store=store;this.catalog=catalog;this.identity=identity;this.index=index;nativeMap=map;this.graph=graph;this.control=control;this.ledger=ledger;
            this.owner=owner;this.preparation=preparation;this.operation=operation;this.mapId=mapId;this.descriptorId=descriptorId;
            this.frames=Collections.unmodifiableList(new ArrayList<>(frames));this.selection=selection;this.bootstrap=bootstrap;this.budget=budget;
            this.graphInput=graphInput;this.baseline=baseline;nativeProof=proof;
            metadata=new PrivateBundleInventory.MetadataBudget(HistoryStorage.METADATA_BUDGET,budget::charge);
            inventoryId=newId(ids);inventoryOuterId=newId(ids);evidenceId=newId(ids);catalogId=newId(ids);scopeId=newId(ids);evidenceOuterId=newId(ids);evidenceItemId=newId(ids);
            for(PrivateFramePreflight.ClosedFrame f:frames)privateOuters.add(newId(ids));
            int c0Bound="MDOP-initial-control-v1\n".length()+4;for(HistoryControlStore.Entry e:control.entries)c0Bound+=45+e.filename.getBytes(StandardCharsets.UTF_8).length;
            c0Input=HashInput.create(budget,c0Bound);writeInitialControlInput(control,budget,c0Input);
            HashSink ledgerSize=new HashSink();if(ledger.present)PrivateOperationStore.writeFingerprintInput(ledger,control,new DataOutputStream(ledgerSize));
            ledgerInput=HashInput.create(budget,metadataLength(ledgerSize.length));PrivateOperationStore.writeOriginalInput(ledger,control,budget,ledgerInput);
            long bound=4096+512L*map.entries.size();for(File root:Arrays.asList(store,catalog,identity,index))bound=add(bound,4L*root.getPath().length());
            nativeInput=HashInput.create(budget,metadataLength(bound));proof.writeOriginalInput(budget,nativeInput);
            HashSink mapSink=new HashSink();map.write(mapSink);mapLength=mapSink.length;mapHash=mapSink.hash();
            for(PrivateOperationEvidence.Row row:baseline.rows())evidence.add(new EvidenceSlot(row,null));
            addWitness(4,"baseline-c0",c0Input);addWitness(5,"baseline-ledger",ledgerInput);addWitness(6,"baseline-graph",graphInput);addWitness(7,"baseline-native",nativeInput);
            evidence.sort((a,b)->{int c=Integer.compare(a.row.role,b.row.role);if(c==0)c=PrivateBundleInventory.compare(a.row.ownerPreparation,b.row.ownerPreparation);return c!=0?c:a.row.name.compareTo(b.row.name);});
            capsuleLength=56;capsuleMetadata=56;
            for(EvidenceSlot slot:evidence){long row=138+slot.row.name.getBytes(StandardCharsets.UTF_8).length;capsuleLength=add(capsuleLength,add(row,slot.row.actualLength));
                capsuleMetadata=add(capsuleMetadata,add(row,slot.row.role==1||slot.row.role==4?slot.row.actualLength:0));}
            int count=map.entries.size()+frames.size()+1;if(count>16380)throw invalid("Portable ordinary entry limit");
            long payload=add(add(map.nativeEnvelopeBytes,capsuleLength),privateLength(frames));
            if(add(add(122+inventoryLength(count),58L*count),payload)>PrivateArchive.MAX_ARCHIVE_BYTES)throw invalid("Complete portable archive exceeds1GiB");
        }
        /** Original scan alone supplies funded Graph/control/ledger; map allocation is prepaid here. */
        static PortableInputs captureLocked(File store,File catalog,File identity,File index,HistoryGraph.Graph graph,
                HashInput graphInput,PreparationSources owner,UUID preparation,UUID operation,UUID mapId,UUID descriptorId,
                List<PrivateFramePreflight.ClosedFrame> frames,long bootstrap,PortableSelection selection,HistoryStorage.Budget budget)throws IOException {
            HistoryStorage.requireMutation(store);PreparationSources.rejectParticipant();HistoryOperations.rejectParticipant();
            if(graph==null||graphInput==null||owner==null||budget==null||selection==null||frames==null||frames.size()>14)throw invalid("Missing portable original inputs");
            graph.requireOriginalCapture(budget,graphInput);
            HistoryControlStore.Inspection control=graph.originalControl(budget,graphInput);PrivateOperationStore.Ledger ledger=graph.originalLedger(budget,graphInput);
            owner.requireIdleBindingLocked(store,preparation,budget);selection.require(owner,budget,frames);
            PrivateOperationStore.PortableBaseline baseline=PrivateOperationStore.capturePortableBaselineLocked(store,control,ledger,owner,preparation,budget);
            if(baseline.sourceOnlyMissingRoot())throw invalid("Missing funded root remains source-only; admission is not implemented");
            if(!graph.complete||!graph.usageComplete||(graph.pruneBlocked&&!graph.historicalCancelledOnly)||!graph.diagnostics.isEmpty()||graph.effectivePolicy==null)throw invalid("Incomplete portable original graph");
            validateLedger(ledger,graph,control);
            budget.charge(FIXED_METADATA_BYTES+FRAME_METADATA_BYTES*frames.size()+2048L*(graph.records.size()+1));
            Set<UUID> ids=new HashSet<>();ids.add(control.registration.bundleId);unique(ids,preparation);unique(ids,operation);unique(ids,mapId);unique(ids,descriptorId);
            for(PrivateFramePreflight.ClosedFrame f:frames){unique(ids,f.itemId);unique(ids,f.artifactId);}
            for(HistoryPinRecord p:control.pins.begins)if(ids.contains(p.operation))throw invalid("Existing operation ID collision");
            for(PrivateOperationRecord p:control.operations.preparations)if(ids.contains(p.preparation))throw invalid("Existing preparation ID collision");
            long mapBound=add(128L<<10,HistoryArchiveManifest.INDEX_BASE_BYTES+HistoryArchiveManifest.INDEX_BYTES_PER_ENTRY*(long)graph.records.size());
            for(HistoryControlStore.Role role:control.registration.roles)mapBound=add(mapBound,4L*role.basename.length());
            budget.charge(mapBound);
            HistoryArchiveManifest map=HistoryArchiveManifest.freeze(graph,control,()->newId(ids));
            NativeCaptureFingerprint proof=NativeCaptureFingerprint.capture(map,graph,control,store,catalog,identity,index,bootstrap,budget,owner,preparation);
            return new PortableInputs(store,catalog,identity,index,map,graph,control,ledger,owner,preparation,operation,mapId,descriptorId,frames,selection,bootstrap,budget,graphInput,baseline,proof,ids);
        }
        private void addWitness(int kind,String name,HashInput input)throws IOException {
            if(input.budget!=budget)throw invalid("Foreign original witness budget");
            evidence.add(new EvidenceSlot(new PrivateOperationEvidence.Row(4,PrivateOperationRecord.ZERO,PrivateOperationRecord.ZERO,kind,2,name,input.length(),input.hash(),input.length(),input.hash(),0,0,0),input));
        }
        /** Pushes the actual raw sources only outside history. No provider or pre-P spool. */
        void measure()throws IOException {
            if(Thread.holdsLock(HistoryStorage.monitorFor(store))||closed||failed||measured||measuring)throw invalid("Portable measurement requires fresh outside-monitor inputs");
            measuring=true;
            try {
                HashSink capsule=new HashSink();
                try(PrivateOperationEvidence.Summary summary=PrivateOperationEvidence.write(capsuleSource(),capsule,metadata)){
                    capsuleHash=capsule.hash();if(summary.length!=capsuleLength||capsule.length!=capsuleLength||!capsuleHash.equals(summary.hash))throw invalid("Capsule measurement mismatch");}
                buildItems();
                int length=inventoryLength(items.size());inventoryBytes=freezeMetadata(budget,length,this::writeInventory);
                try(InputStream in=inventoryBytes.open()){inventory=PrivateBundleInventory.readSchema2(in,metadata);}
                List<PrivateSourceCatalog.Row> rows=new ArrayList<>(items.size());for(Item item:items)rows.add(item.source);
                try(PrivateSourceCatalog catalog=PrivateSourceCatalog.create(rows,metadata)) {
                    catalogBytes=freezeMetadata(budget,metadataLength(catalog.length),catalog::write);
                }
                if(add(add(add(mapLength,inventoryBytes.length()),catalogBytes.length()),add(capsuleMetadata,descriptorLength()))>HistoryStorage.METADATA_BUDGET)
                    throw invalid("Combined serialized portable metadata limit");
                measured=true;validateJoins();
            }catch(IOException|RuntimeException|Error failure){failed=true;throw failure;}finally{measuring=false;}
        }
        PrivateOperationEvidence.Source capsuleSource(){return new PrivateOperationEvidence.Source(){
            public long length(){return capsuleLength;}public int rowCount(){return evidence.size();}
            public PrivateOperationEvidence.Cursor open()throws IOException {
                if(closed||failed||pending!=null||Thread.holdsLock(HistoryStorage.monitorFor(store)))throw invalid("Portable evidence source unavailable");
                budget.charge(4096);EvidenceCursor cursor=new EvidenceCursor();pending=cursor;cursor.physical=baseline.open();return cursor;
            }
        };}
        /** Retry only the actual retained cursor; never clear uncertain Source ownership. */
        void retrySourceClose()throws IOException {if(pending!=null)pending.close();}
        private final class EvidenceCursor implements PrivateOperationEvidence.Cursor {
            private PrivateOperationStore.PortableBaseline.BaselineCursor physical;private int at=-1;private EvidenceSlot current;private InputStream raw,witnessReader;private boolean ended,abandoning,rowOpened,rowDone;
            public PrivateOperationEvidence.Row next()throws IOException {
                if(ended||abandoning||raw!=null||current!=null&&!rowDone)throw invalid("Evidence row must reach EOF and close before advance");at++;rowOpened=false;rowDone=false;
                if(at>=evidence.size()){current=null;if(physical.next()!=null)throw invalid("Extra original evidence row");return null;}
                current=evidence.get(at);if(current.witness==null&&physical.next()!=current.row)throw invalid("Original evidence identity/order changed");return current.row;
            }
            public InputStream openRaw()throws IOException {
                if(ended||abandoning||current==null||raw!=null||rowOpened)throw invalid("No unopened evidence row");budget.charge(512);
                InputStream input=current.witness==null?physical.openRaw(current.row):current.witness.open();
                if(current.witness!=null)witnessReader=input;rowOpened=true;
                raw=new FilterInputStream(input){private boolean done;private long left=current.row.actualLength;
                    private void ready()throws IOException {if(ended||abandoning||done)throw invalid("Evidence row reader closed/abandoned");}
                    public int read()throws IOException {ready();int n=super.read();if(n>=0)left--;return n;}
                    public int read(byte[] b,int off,int len)throws IOException {ready();int n=in.read(b,off,len);if(n>0)left-=n;return n;}
                    public void close()throws IOException {if(done||ended)return;ready();if(left!=0||in.read()!=-1)throw invalid("Evidence row not completely consumed");super.close();witnessReader=null;done=true;rowDone=true;raw=null;}
                };return raw;
            }
            public void close()throws IOException {
                if(ended)return;abandoning=true;
                // Whole-cursor abandonment proves actual closure, never row EOF/hash or
                // completion. Keep the exact delegate until its close succeeds for retry.
                if(witnessReader!=null){witnessReader.close();witnessReader=null;raw=null;}
                if(physical!=null)physical.close();ended=true;pending=null;raw=null;
            }
        }
        private void buildItems()throws IOException {
            for(HistoryArchiveManifest.Entry e:nativeMap.entries){int type=e.kind==HistoryArchiveManifest.Kind.INDEX?9:e.kind.wireId;
                items.add(new Item(e.archiveId,type,e.nativeId,e.envelopeBytes,e.envelopeHash(),new PrivateSourceCatalog.Row(e.archiveId,1,nativeMap.bundleId,e.nativeId,0,e.envelopeBytes,e.envelopeHash())));}
            for(int i=0;i<frames.size();i++){PrivateFramePreflight.ClosedFrame f=frames.get(i);UUID outer=privateOuters.get(i);
                items.add(new Item(outer,f.kind.wireId,f.itemId,f.envelopeBytes,f.envelopeHash,new PrivateSourceCatalog.Row(outer,2,preparation,f.artifactId,0,f.envelopeBytes,f.envelopeHash)));}
            items.add(new Item(evidenceOuterId,11,evidenceItemId,capsuleLength,capsuleHash,new PrivateSourceCatalog.Row(evidenceOuterId,3,preparation,evidenceId,0,capsuleLength,capsuleHash)));
            items.sort((a,b)->PrivateBundleInventory.compare(a.outer,b.outer));
        }
        private int inventoryLength(int count)throws IOException {
            return metadataLength(144L+93L*count+16L*selection.tabs.size()+(selection.tabs.isEmpty()?0:16)+68);
        }
        private long descriptorLength(){return 65L+210+control.registration.roles.stream().mapToInt(r->r.basename.getBytes(StandardCharsets.UTF_8).length).sum()+77L*(4+frames.size());}
        private void writeInventory(OutputStream output)throws IOException {
            MessageDigest hash=HistoryStorage.digest();DataOutputStream out=new DataOutputStream(new DigestOutputStream(output,hash));
            int length=inventoryLength(items.size());long payload=0;for(Item item:items)payload=add(payload,item.length);
            out.write("MDBUNDL\n".getBytes(StandardCharsets.US_ASCII));out.writeInt(2);out.writeInt(length);putUuid(out,preparation);putUuid(out,scopeId);
            out.writeInt(1);out.writeInt(items.size());out.writeInt(0);out.writeInt(0);out.writeLong(payload);out.writeLong(122L+length+58L*items.size()+payload);
            putUuid(out,scopeId);out.writeByte(1);out.writeByte(3);out.writeInt(0);out.writeInt(items.size());
            int active=selection.activeOrdinal();out.writeByte(active<0?0:1);if(active>=0)putUuid(out,privateOuters.get(active));
            out.writeInt(selection.tabs.size());for(int i=0;i<selection.tabs.size();i++)putUuid(out,privateOuters.get(i));
            out.writeByte(1);putUuid(out,nativeMap.bundleId);out.writeLong(nativeMap.policyGeneration);out.writeLong(nativeMap.quotaBytes);out.writeInt(nativeMap.automaticCount);PrivateBundleInventory.putHash(out,HistoryStorage.hex(nativeMap.policyHashBytes()));
            for(Item item:items){putUuid(out,item.outer);putUuid(out,scopeId);out.writeByte(item.type);out.writeLong(item.length);PrivateBundleInventory.putHash(out,item.hash);putUuid(out,item.identity);out.writeInt(0);}
            output.write(hash.digest());
        }
        private void require(File store,File catalog,File identity,File index,HistoryArchiveManifest map,HistoryGraph.Graph graph,
                HistoryControlStore.Inspection control,PrivateOperationStore.Ledger ledger,PreparationSources owner,UUID prep,UUID op,UUID mapId,UUID descriptorId,
                List<PrivateFramePreflight.ClosedFrame> frames,long bootstrap,PortableSelection selection,HistoryStorage.Budget budget)throws IOException {
            if(closed||failed||!measured||measuring||pending!=null||!this.store.equals(store)||!this.catalog.equals(catalog)||!this.identity.equals(identity)||!this.index.equals(index)
                    ||nativeMap!=map||this.graph!=graph||this.control!=control||this.ledger!=ledger||this.owner!=owner||this.selection!=selection||this.budget!=budget
                    ||!preparation.equals(prep)||!operation.equals(op)||!this.mapId.equals(mapId)||!this.descriptorId.equals(descriptorId)||this.bootstrap!=bootstrap||frames==null||frames.size()!=this.frames.size())throw invalid("Foreign/unmeasured original portable inputs");
            for(int i=0;i<frames.size();i++)if(this.frames.get(i)!=frames.get(i))throw invalid("Replaced portable preflight result");
            graph.requireOriginalCapture(budget,graphInput);selection.require(owner,budget,frames);owner.requireIdleBindingLocked(store,prep,budget);
        }
        void validateJoins()throws IOException {
            if(inventory==null||inventory.entries().size()!=items.size()||inventory.schema!=2||!inventory.preparationId.equals(preparation)||!inventory.rootScopeId.equals(scopeId))throw invalid("Portable inventory binding");
            for(int i=0;i<items.size();i++){Item a=items.get(i);PrivateBundleInventory.DirectoryEntry b=inventory.entries().get(i);
                if(!a.outer.equals(b.id)||a.type!=b.type||!a.identity.equals(b.identity)||a.length!=b.length||!a.hash.equals(b.sha256)||!scopeId.equals(b.scopeId))throw invalid("Portable inventory membership");}
            PrivateBundleInventory.Scope scope=inventory.scopes().get(0);int active=selection.activeOrdinal();
            if(!Objects.equals(scope.activeEntryId,active<0?null:privateOuters.get(active))||scope.tabs.size()!=selection.tabs.size())throw invalid("Portable active/tab binding");
            try(InputStream in=catalogBytes.open();PrivateSourceCatalog parsed=PrivateSourceCatalog.read(in,metadata)){
                if(parsed.rows.size()!=items.size())throw invalid("Catalog ordinary count");
                for(int i=0;i<items.size();i++){PrivateSourceCatalog.Row a=items.get(i).source,b=parsed.rows.get(i);
                    if(!a.outer.equals(b.outer)||a.route!=b.route||!a.owner.equals(b.owner)||!a.member.equals(b.member)||a.offset!=b.offset||a.length!=b.length||!a.hash.equals(b.hash))throw invalid("Catalog source join");}
            }
        }
        InputStream openInventory()throws IOException {if(!measured||closed||failed)throw invalid("Portable inventory unavailable");return inventoryBytes.open();}
        InputStream openCatalog()throws IOException {if(!measured||closed||failed)throw invalid("Portable catalog unavailable");return catalogBytes.open();}
        PrivateBundleInventory inventory(){return inventory;}
        public void close()throws IOException {
            if(pending!=null||measuring)throw invalid("Actual portable source cursor remains owned");if(closed)return;
            if(inventory!=null)inventory.close();if(inventoryBytes!=null)inventoryBytes.close();if(catalogBytes!=null)catalogBytes.close();
            c0Input.close();ledgerInput.close();nativeInput.close();closed=true;
        }
    }
    private static final class EvidenceSlot {
        final PrivateOperationEvidence.Row row;final HashInput witness;EvidenceSlot(PrivateOperationEvidence.Row row,HashInput witness){this.row=row;this.witness=witness;}
    }
    private static final class Item {
        final UUID outer,identity;final int type;final long length;final String hash;final PrivateSourceCatalog.Row source;
        Item(UUID outer,int type,UUID identity,long length,String hash,PrivateSourceCatalog.Row source){this.outer=outer;this.type=type;this.identity=identity;this.length=length;this.hash=hash;this.source=source;}
    }
    private interface MetadataWriter {void write(OutputStream output)throws IOException;}
    private static HashInput freezeMetadata(HistoryStorage.Budget budget,int length,MetadataWriter writer)throws IOException {
        HashSink expected=new HashSink();writer.write(expected);if(expected.length!=length)throw invalid("Measured portable metadata length");
        HashInput result=HashInput.create(budget,length);
        try{writer.write(result.writer(budget));result.seal(expected.hash());return result;}catch(IOException|RuntimeException|Error failure){result.invalidate();throw failure;}
    }
    private static UUID newId(Set<UUID> ids)throws IOException {UUID id=UUID.randomUUID();unique(ids,id);return id;}
    private static int metadataLength(long length)throws IOException {if(length<0||length>HistoryStorage.METADATA_BUDGET)throw invalid("Portable metadata bound");return (int)length;}
    private static long privateLength(List<PrivateFramePreflight.ClosedFrame> frames)throws IOException {long n=0;for(PrivateFramePreflight.ClosedFrame f:frames)n=add(n,f.envelopeBytes);return n;}
    private static void putUuid(DataOutputStream out,UUID id)throws IOException {out.writeLong(id.getMostSignificantBits());out.writeLong(id.getLeastSignificantBits());}

    private static void validateLedger(PrivateOperationStore.Ledger ledger,HistoryGraph.Graph graph,HistoryControlStore.Inspection control)throws IOException {
        if(!ledger.complete||(ledger.pending&&!PrivateOperationStore.historicalCancelledOnly(control,ledger))||!ledger.diagnostics.isEmpty()||ledger.actualBytes<0||ledger.reservedBytes<0||ledger.occupiedEntries<0||ledger.reservedEntries<0
                ||ledger.occupiedEntries!=ledger.entries.size()||add(ledger.occupiedEntries,ledger.reservedEntries)>PrivateOperationStore.MAX_ENTRIES
                ||graph.operationActualBytes!=ledger.actualBytes||graph.operationReservedBytes!=ledger.reservedBytes
                ||graph.operationOccupiedEntries!=ledger.occupiedEntries||graph.operationReservedEntries!=ledger.reservedEntries
                ||!Objects.equals(graph.operationFingerprint,ledger.fingerprint)
                ||graph.actualBytes<ledger.actualBytes||graph.chargedBytes!=add(add(graph.actualBytes,graph.reservedCompletionBytes),ledger.reservedBytes))
            throw invalid("Incomplete/nonmatching initial operation ledger");
        if(!ledger.present){
            if(!"".equals(ledger.fingerprint)||ledger.actualBytes!=0||ledger.reservedBytes!=0||ledger.occupiedEntries!=0||ledger.reservedEntries!=0
                    ||!ledger.entries.isEmpty()||!ledger.controlIdentities.isEmpty()||control.operations.present)throw invalid("Noncanonical absent ledger");
            return;
        }
        String rootIdentity=ledger.entries.get(PrivateOperationStore.ROOT);
        if(!canonicalHash(ledger.fingerprint)||rootIdentity==null||!rootIdentity.startsWith("dir:"))throw invalid("Noncanonical present ledger");
        for(Map.Entry<String,String> e:ledger.entries.entrySet())if(e.getKey()==null||e.getValue()==null||e.getKey().isEmpty()||e.getValue().isEmpty())throw invalid("Noncanonical operation entry");
        for(Map.Entry<String,String> e:ledger.controlIdentities.entrySet())if(e.getKey()==null||e.getValue()==null||e.getKey().isEmpty()||e.getValue().isEmpty())throw invalid("Noncanonical control identity");
        if(!ledger.fingerprint.equals(PrivateOperationStore.fingerprint(ledger,control)))
            throw invalid("Initial operation ledger fingerprint changed");
    }
    /**
     * Transient original-C0 binding. The writer must compare freshly inspected
     * control BEFORE its first PREPARE and before adopting a physical baseline.
     * This does not replace full accounting/current authority checks or bind old
     * inode identity across discovery. The persistent map.graphHash is unchanged.
     * Its <=256-reference sort table/UTF8 temporaries fit assembly's fixed reserve;
     * other callers must prepay equivalent transient workspace in their budget.
     */
    static String initialControlHash(HistoryControlStore.Inspection control)throws IOException {
        if(control==null||control.entries.size()>HistoryControlStore.MAX_ENTRIES
                ||control.entries.size()!=control.actualEntries)throw invalid("Invalid original control entry count");
        HistoryControlStore.Entry[] entries=control.entries.toArray(new HistoryControlStore.Entry[control.entries.size()]);
        for(HistoryControlStore.Entry e:entries)if(e==null||e.filename==null||e.filename.isEmpty()||e.filename.length()>255
                ||e.length<0||!canonicalHash(e.envelopeHash))throw invalid("Noncanonical original control entry");
        Arrays.sort(entries,Comparator.comparing(e->e.filename));
        MessageDigest hash=HistoryStorage.digest();
        try(DataOutputStream out=new DataOutputStream(new DigestOutputStream(discard(),hash))){
            writeInitialControlInput(entries,out);
        }
        return HistoryStorage.hex(hash.digest());
    }
    static void writeInitialControlInput(HistoryControlStore.Inspection control,HistoryStorage.Budget budget,HashInput input)throws IOException {
        try {
            DataOutputStream out=input.writer(budget);
            // Validate before allocating the bounded sort table; same serializer as the digest.
            String expected=initialControlHash(control);
            HistoryControlStore.Entry[] entries=control.entries.toArray(new HistoryControlStore.Entry[control.entries.size()]);
            Arrays.sort(entries,Comparator.comparing(e->e.filename));
            writeInitialControlInput(entries,out);input.seal(expected);
        }catch(IOException|RuntimeException|Error failure){if(input!=null)input.invalidate();throw failure;}
    }
    private static void writeInitialControlInput(HistoryControlStore.Entry[] entries,DataOutputStream out)throws IOException {
        out.write("MDOP-initial-control-v1\n".getBytes(StandardCharsets.US_ASCII));out.writeInt(entries.length);
        String previous=null;
        for(HistoryControlStore.Entry e:entries){
            if(e.filename.equals(previous))throw invalid("Duplicate original control name");previous=e.filename;
            ByteBuffer name=StandardCharsets.UTF_8.newEncoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).encode(CharBuffer.wrap(e.filename));
            if(name.remaining()>255)throw invalid("Original control name exceeds bound");
            out.writeInt(name.remaining());out.write(name.array(),name.arrayOffset()+name.position(),name.remaining());out.writeLong(e.length);
            for(int i=0;i<32;i++)out.writeByte((Character.digit(e.envelopeHash.charAt(2*i),16)<<4)|Character.digit(e.envelopeHash.charAt(2*i+1),16));
            out.writeByte(e.stage?1:0);
        }
    }
    /** Canonical domain conversion; caller first validates complete canonical ledger evidence. */
    static String initialLedgerHash(PrivateOperationStore.Ledger ledger)throws IOException {
        MessageDigest hash=HistoryStorage.digest();hash.update("MDOP-initial-ledger-v1\n".getBytes(StandardCharsets.US_ASCII));hash.update((byte)(ledger.present?1:0));
        if(ledger.present){byte[] bytes=new byte[32];for(int i=0;i<32;i++)bytes[i]=(byte)Integer.parseInt(ledger.fingerprint.substring(i*2,i*2+2),16);hash.update(bytes);}
        return HistoryStorage.hex(hash.digest());
    }
    private static void admit(long data,long artifacts,long quota,long control,long controlCredit,long prepareBytes,long controlSlots,
            long operationUsed,long operationReserved,int directories,int artifactSlots)throws IOException {
        if(data<0||artifacts<0||quota<0||control<0||controlCredit<0||prepareBytes<=0||controlSlots<0||operationUsed<0||operationReserved<0
                ||directories<1||directories>2||artifactSlots<2||artifactSlots>16)throw invalid("Invalid funding units");
        if(add(data,artifacts)>quota||add(add(control,controlCredit),add(prepareBytes,PrivateOperationRecord.BEGIN_BYTES+HistoryPinRecord.CLOSE_BYTES+PrivateOperationRecord.LINK_BYTES))>HistoryControlStore.MAX_BYTES
                ||add(controlSlots,4)>HistoryControlStore.MAX_ENTRIES||add(add(operationUsed,operationReserved),directories+artifactSlots)>PrivateOperationStore.MAX_ENTRIES)
            throw invalid("Insufficient capture funding");
    }
    /** Prepaid internal metadata source only. No filesystem, callbacks or protocol authority. */
    static final class HashInput implements AutoCloseable {
        private static final int PAGE=65536;
        // Covers digest/UTF encoders, writeUTF's <=65535B carry, tables and stream wrappers.
        private static final long WORKSPACE=256L<<10;
        private final HistoryStorage.Budget budget;
        private final Thread thread=Thread.currentThread();
        private final int maximum;
        private byte[][] pages;
        private final MessageDigest digest;
        private int state,count; // 0 empty,1 writing,2 sealed,3 invalid/closed
        private String hash;
        private boolean reader;
        private HashInput(HistoryStorage.Budget budget,int maximum,int pageCount)throws IOException {
            this.budget=budget;this.maximum=maximum;
            pages=new byte[pageCount][];digest=HistoryStorage.digest();
        }
        static HashInput create(HistoryStorage.Budget budget,int maximum)throws IOException {
            if(budget==null||maximum<0||maximum>HistoryStorage.METADATA_BUDGET)throw invalid("Invalid witness bound/budget");
            int n=(int)((maximum+(long)PAGE-1)/PAGE);
            budget.charge(WORKSPACE+maximum+64L*n); // before owner, table, digest or any page
            return new HashInput(budget,maximum,n);
        }
        synchronized void requireEmpty(HistoryStorage.Budget supplied)throws IOException {
            if(state!=0||thread!=Thread.currentThread()||budget!=supplied)throw invalid("Foreign or consumed witness writer");
        }
        synchronized DataOutputStream writer(HistoryStorage.Budget supplied)throws IOException {
            requireEmpty(supplied);state=1;
            return new DataOutputStream(new OutputStream(){
                public void write(int b)throws IOException {
                    try{append(b);}catch(IOException|RuntimeException|Error failure){invalidate();throw failure;}
                }
                public void write(byte[] b,int off,int len)throws IOException {
                    try{append(b,off,len);}catch(IOException|RuntimeException|Error failure){invalidate();throw failure;}
                }
            });
        }
        private void writing(int n)throws IOException {
            if(state!=1||thread!=Thread.currentThread()||n<0||n>maximum-count)throw invalid("Witness output exceeds its prepaid bound");
        }
        private synchronized void append(int b)throws IOException {
            writing(1);int at=count/PAGE;if(pages[at]==null)pages[at]=new byte[Math.min(PAGE,maximum-at*PAGE)];
            pages[at][count%PAGE]=(byte)b;count++;digest.update((byte)b);
        }
        private synchronized void append(byte[] b,int off,int len)throws IOException {
            if(b==null||off<0||len<0||off>b.length-len)throw new IOException("Invalid witness write");
            writing(len);int remaining=len;
            while(remaining>0){int at=count/PAGE,offset=count%PAGE;
                if(pages[at]==null)pages[at]=new byte[Math.min(PAGE,maximum-at*PAGE)];
                int n=Math.min(remaining,pages[at].length-offset);System.arraycopy(b,off,pages[at],offset,n);
                digest.update(b,off,n);count+=n;off+=n;remaining-=n;
            }
        }
        synchronized void seal(String expected)throws IOException {
            writing(0);hash=HistoryStorage.hex(digest.digest());
            if(!hash.equals(expected)){invalidate();throw invalid("Canonical witness fingerprint mismatch");}state=2;
        }
        synchronized void invalidate(){state=3;pages=null;hash=null;}
        private void sealed()throws IOException {if(state!=2)throw invalid("Witness is not sealed");}
        synchronized long length()throws IOException {sealed();return count;}
        synchronized String hash()throws IOException {sealed();return hash;}
        synchronized InputStream open()throws IOException {
            sealed();if(reader)throw invalid("Witness reader already open");
            budget.charge(256); // each reader is charged before allocation; never refunded
            InputStream result=new InputStream(){
                private int position;private boolean closed;
                private void ready()throws IOException {sealed();if(closed)throw invalid("Witness reader closed");}
                public int read()throws IOException {synchronized(HashInput.this){ready();if(position==count)return -1;int n=pages[position/PAGE][position%PAGE]&255;position++;return n;}}
                public int read(byte[] b,int off,int len)throws IOException {synchronized(HashInput.this){
                    ready();if(b==null)throw new NullPointerException();if(off<0||len<0||off>b.length-len)throw new IndexOutOfBoundsException();
                    if(len==0)return 0;if(position==count)return -1;
                    int n=Math.min(Math.min(len,count-position),PAGE-position%PAGE);
                    System.arraycopy(pages[position/PAGE],position%PAGE,b,off,n);position+=n;return n;
                }}
                public void close(){synchronized(HashInput.this){if(!closed){closed=true;reader=false;}}}
            };
            reader=true;return result;
        }
        public synchronized void close(){invalidate();} // metadata ownership ends; budget stays debited
    }
    private static final class HashSink extends OutputStream {
        final MessageDigest digest=HistoryStorage.digest();long length;
        HashSink()throws IOException{}
        public void write(int b)throws IOException{length=add(length,1);digest.update((byte)b);}
        public void write(byte[] bytes,int offset,int count)throws IOException{length=add(length,count);digest.update(bytes,offset,count);}
        String hash(){return HistoryStorage.hex(digest.digest());}
    }
    private static OutputStream discard(){return new OutputStream(){public void write(int b){}public void write(byte[] b,int o,int n){}};}
    private static void unique(Set<UUID> used,UUID id)throws IOException {if(id==null||id.version()!=4||id.variant()!=2||!used.add(id))throw invalid("Noncanonical/duplicate generated ID");}
    private static boolean canonicalHash(String value){if(value==null||value.length()!=64)return false;for(int i=0;i<64;i++){char c=value.charAt(i);if(!(c>='0'&&c<='9'||c>='a'&&c<='f'))return false;}return true;}
    private static long add(long a,long b)throws IOException{try{return Math.addExact(a,b);}catch(ArithmeticException failure){throw new IOException("Capture funding overflow",failure);}}
    private static IOException invalid(String message){return new IOException("Private capture plan: "+message);}
}
