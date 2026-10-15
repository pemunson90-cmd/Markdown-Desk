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
    final long artifactBytes;
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
        if(frozen==null||graph==null||control==null||baseLedger==null||!graph.complete||!graph.usageComplete||graph.pruneBlocked
                ||!graph.diagnostics.isEmpty()||graph.effectivePolicy==null||control.state!=HistoryControlStore.State.VALIDATED
                ||!control.chainComplete||!control.usageComplete||!control.diagnostics.isEmpty()||!control.stages.isEmpty()
                ||control.hasMaintenance()||!control.pins.valid||control.pins.active!=null||!control.pins.stages.isEmpty()
                ||!control.operations.valid||!control.operations.ready||control.operations.pending||control.registration==null||control.latest==null
                ||graph.controlActualBytes!=control.actualBytes||graph.controlActualEntries!=control.actualEntries)
            throw invalid("Incomplete initial graph/control authority");
        validateLedger(baseLedger,graph,control);
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
        NativeCaptureFingerprint nativeProof=NativeCaptureFingerprint.capture(frozen,graph,control,store,catalog,identity,index,bootstrapQuotaBytes,sharedBudget);
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
        return new PrivateCapturePlan(frozen,nativeProof,owner,retained,prepare,descriptor,link,ledgerHash,controlHash,bytes);
    }
    private PrivateCapturePlan(HistoryArchiveManifest map,NativeCaptureFingerprint proof,PreparationSources owner,List<PrivateFramePreflight.ClosedFrame> frames,
            PrivateOperationRecord prepare,PrivateOperationRecord descriptor,PrivateOperationRecord link,String ledger,String control,long bytes){
        nativeMap=map;nativeSources=proof;sourceOwner=owner;privateFrames=frames;this.prepare=prepare;this.descriptor=descriptor;this.link=link;initialLedgerHash=ledger;initialControlHash=control;artifactBytes=bytes;
    }
    private static void validateLedger(PrivateOperationStore.Ledger ledger,HistoryGraph.Graph graph,HistoryControlStore.Inspection control)throws IOException {
        if(!ledger.complete||ledger.pending||!ledger.diagnostics.isEmpty()||ledger.actualBytes<0||ledger.reservedBytes<0||ledger.occupiedEntries<0||ledger.reservedEntries<0
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
        // Recompute the existing ledger domain exactly; reject mutated DTO tables
        // carrying a stale fingerprint/counter tuple. No new namespace traversal.
        MessageDigest digest=HistoryStorage.digest();
        try(DataOutputStream out=new DataOutputStream(new DigestOutputStream(discard(),digest))){
            out.writeUTF("private-operation-ledger-v1");
            for(Map.Entry<String,String> e:ledger.controlIdentities.entrySet()){out.writeUTF(e.getKey());out.writeUTF(e.getValue());}
            for(Map.Entry<String,String> e:ledger.entries.entrySet()){out.writeUTF(e.getKey());out.writeUTF(e.getValue());}
            for(HistoryControlStore.Entry e:control.entries)if(PrivateOperationStore.operationEntry(e)){out.writeUTF(e.filename);out.writeLong(e.length);out.writeUTF(e.envelopeHash==null?"":e.envelopeHash);out.writeBoolean(e.stage);}
            out.writeLong(ledger.actualBytes);out.writeLong(ledger.reservedBytes);out.writeLong(ledger.occupiedEntries);out.writeLong(ledger.reservedEntries);
            out.writeLong(control.controlReservedBytes);out.writeInt(control.controlReservedEntries);out.writeBoolean(ledger.complete);
        }
        if(!ledger.fingerprint.equals(HistoryStorage.hex(digest.digest())))throw invalid("Initial operation ledger fingerprint changed");
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
        return HistoryStorage.hex(hash.digest());
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
