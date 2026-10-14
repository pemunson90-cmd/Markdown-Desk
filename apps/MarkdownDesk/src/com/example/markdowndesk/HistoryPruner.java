package com.example.markdowndesk;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/**
 * Pure previews over freshly validated native history, with no executor or file mutation.
 * automaticCount is a desired maximum: capacity pressure may reclaim older groups within
 * that count, while the newest indexed group of each history always survives.
 * Foundation protectedSnapshotIds are not deletion authority; eligibility is derived here
 * from verified exclusive ownership, native reference closure, names and surviving anchors.
 */
public final class HistoryPruner {
    public enum BlockedReason { INVALID_GRAPH, MISSING_POLICY, INVALID_REQUEST, CAPACITY_SHORTFALL,
        UNKNOWN_CHECKPOINT, INCOMPLETE_SELECTION, UNOWNED_SELECTION, NEWEST_ANCHOR, SHARED_SNAPSHOT, PLAN_LIMIT }
    public static final class SnapshotProof {
        public final String id,documentId,uri,title,hash,role;
        public final long revision,timestamp;
        public final int byteCount;
        private SnapshotProof(ProtectionStore.Snapshot s){id=s.id;documentId=s.documentId;uri=s.uri;title=s.title;hash=s.hash;role=s.role;revision=s.revision;timestamp=s.timestamp;byteCount=s.byteCount;}
    }
    public static final class SurvivorProof {
        public final String historyId,transactionId,ownershipId,expectedHash,exactUri,tabId;
        public final long sequence;
        public final SnapshotProof original,attempted;
        public final List<String> origins;
        public final List<ProtectionStore.StoredRecord> records;
        private SurvivorProof(Group g){historyId=g.owner.historyId;transactionId=g.tx.id;ownershipId=g.owner.id;sequence=g.owner.sequence;expectedHash=g.tx.expectedHash;
            original=new SnapshotProof(g.tx.original);attempted=new SnapshotProof(g.tx.attempted);exactUri=g.identity.exactUri;tabId=g.identity.tabId;origins=freeze(g.identity.origins);records=freeze(g.records);}
    }
    public static final class GroupProof {
        public final String historyId,transactionId,ownershipId,expectedHash,originalSnapshotId,attemptedSnapshotId;
        public final long sequence;
        public final SnapshotProof original,attempted;
        public final SurvivorProof survivor;
        public final List<ProtectionStore.StoredRecord> records;
        private GroupProof(Group g,Group anchor){historyId=g.owner.historyId;transactionId=g.tx.id;ownershipId=g.owner.id;expectedHash=g.tx.expectedHash;sequence=g.owner.sequence;
            originalSnapshotId=g.tx.original.id;attemptedSnapshotId=g.tx.attempted.id;original=new SnapshotProof(g.tx.original);attempted=new SnapshotProof(g.tx.attempted);
            survivor=new SurvivorProof(anchor);records=freeze(g.records);}
    }
    public static final class DeletePlan {
        public final boolean blocked;
        /** A successful count-only batch has older count-excess groups left for a fresh later plan. */
        public final boolean moreCountMaintenance;
        public final Set<BlockedReason> blockedReasons;
        public final String graphFingerprint,proofHash;
        public final HistoryPolicy policy;
        public final long requestedBytes,eligibleBytes,protectedBytes,chargedBytes,reclaimedBytes;
        /** Canonical preview proof bytes plus128 reserved future framing bytes; no journal format exists yet. */
        public final long proofBytes;
        public final int requestedStoreEntries,reclaimedStoreEntries;
        public final long eligibleStoreEntries,protectedStoreEntries;
        public final Map<String,Integer> reclaimedEntriesByRoot;
        public final List<ProtectionStore.StoredRecord> members,routingRecords;
        public final List<GroupProof> groups;
        public final List<HistoryCatalog.Checkpoint> selectedCheckpoints;
        private final HistoryGraph source;
        private final boolean automatic;
        private final Set<String> selectedIds;
        private DeletePlan(HistoryGraph source,HistoryGraph.Graph graph,boolean automatic,long bytes,int entries,Set<String> ids,Set<BlockedReason> reasons,
                long eligibleBytes,long eligibleEntries,List<GroupProof> groups,List<HistoryCatalog.Checkpoint> names,List<ProtectionStore.StoredRecord> routing,String proofHash,long proofBytes,boolean moreCountMaintenance){
            this.source=source;graphFingerprint=graph.fingerprint;policy=graph.policy;chargedBytes=graph.chargedBytes;this.automatic=automatic;selectedIds=freezeSet(ids);
            requestedBytes=bytes;requestedStoreEntries=entries;this.eligibleBytes=eligibleBytes;eligibleStoreEntries=eligibleEntries;
            protectedBytes=Math.max(0,chargedBytes-eligibleBytes);long storeEntries=graph.reservedCompletionSlots;
            for(ProtectionStore.StoredRecord record:graph.records)if("store".equals(record.rootRole))storeEntries++;
            protectedStoreEntries=Math.max(0,storeEntries-eligibleEntries);blocked=!reasons.isEmpty();blockedReasons=freezeSet(reasons);this.moreCountMaintenance=!blocked&&moreCountMaintenance;
            this.groups=blocked?Collections.emptyList():freeze(groups);selectedCheckpoints=blocked?Collections.emptyList():freeze(names);routingRecords=blocked?Collections.emptyList():freeze(routing);
            List<ProtectionStore.StoredRecord> members=new ArrayList<>();Map<String,Integer> freed=new TreeMap<>();long reclaimed=0;
            for(GroupProof group:this.groups)for(ProtectionStore.StoredRecord record:group.records){members.add(record);reclaimed+=record.serializedBytes;freed.merge(record.rootRole,1,Integer::sum);}
            this.members=freeze(members);reclaimedEntriesByRoot=Collections.unmodifiableMap(freed);reclaimedBytes=reclaimed;reclaimedStoreEntries=freed.getOrDefault("store",0);
            this.proofHash=blocked?null:proofHash;this.proofBytes=blocked?0:proofBytes;
        }
    }
    private static final class Group {
        final ProtectionStore.Transaction tx;
        final HistoryGraph.Ownership owner;
        final HistoryIdentity.Descriptor identity;
        final List<ProtectionStore.StoredRecord> records=new ArrayList<>();
        final List<HistoryCatalog.Checkpoint> names=new ArrayList<>();
        boolean shared;
        int rank;
        Group(ProtectionStore.Transaction tx,HistoryGraph.Ownership owner,HistoryIdentity.Descriptor identity){this.tx=tx;this.owner=owner;this.identity=identity;}
    }
    private final HistoryGraph graph;
    public HistoryPruner(HistoryGraph graph){if(graph==null)throw new IllegalArgumentException("Missing history graph");this.graph=graph;}
    /**
     * Minimum bytes and STORE slots to reclaim, not incoming write size. Caller computes
     * deficits from the same proof. Positive requests select globally oldest eligible groups
     * until both minima are met; failure to fit one bounded plan grants no partial selection.
     * (0,0) selects one bounded oldest count-excess batch; moreCountMaintenance requests a
     * fresh plan after a future executor finishes that exact batch.
     */
    public DeletePlan planAutomatic(long requiredBytes,int requiredEntries)throws IOException {return plan(graph.scan(),true,requiredBytes,requiredEntries,Collections.emptySet());}
    /** All names on every selected group must be selected; the fixed newest same-history anchor survives. */
    public DeletePlan planSelected(Set<String> checkpointIds)throws IOException {
        HistoryGraph.Graph current=graph.scan();
        if(checkpointIds!=null&&checkpointIds.size()>256)return result(current,false,0,0,Collections.emptySet(),
                EnumSet.of(BlockedReason.PLAN_LIMIT),0,0,Collections.emptyList(),Collections.emptyList(),Collections.emptyList(),null,0);
        return plan(current,false,0,0,checkpointIds==null?null:new HashSet<>(checkpointIds));
    }
    /** Comparison only; true is not deletion authorization. Future executor must rescan under the shared monitor. */
    public boolean isCurrent(DeletePlan previous)throws IOException {
        if(previous==null||previous.blocked||previous.source!=graph)return false;
        HistoryGraph.Graph current;
        try{current=graph.scan();}catch(IOException invalid){return false;}
        if(!current.complete||current.pruneBlocked||!Objects.equals(previous.graphFingerprint,current.fingerprint))return false;
        DeletePlan refreshed=plan(current,previous.automatic,previous.requestedBytes,previous.requestedStoreEntries,previous.selectedIds);
        return !refreshed.blocked&&Objects.equals(previous.proofHash,refreshed.proofHash);
    }
    private DeletePlan plan(HistoryGraph.Graph g,boolean automatic,long bytes,int entries,Set<String> selected)throws IOException {
        EnumSet<BlockedReason> reasons=EnumSet.noneOf(BlockedReason.class);
        Set<String> ids=new TreeSet<>();
        if(selected==null||selected.contains(null)||bytes<0||entries<0)reasons.add(BlockedReason.INVALID_REQUEST);
        else ids.addAll(selected);
        if(!g.complete||g.pruneBlocked||!g.usageComplete)reasons.add(BlockedReason.INVALID_GRAPH);
        if(g.policy==null)reasons.add(BlockedReason.MISSING_POLICY);
        if(!reasons.isEmpty())return result(g,automatic,bytes,entries,ids,reasons,0,0,Collections.emptyList(),Collections.emptyList(),Collections.emptyList(),null,0);
        Map<String,ProtectionStore.StoredRecord> records=new HashMap<>();
        for(ProtectionStore.StoredRecord record:g.records) {
            String key=record.rootRole+"/"+record.filename;
            if(record.serializedBytes<0||record.envelopeHash==null||records.put(key,record)!=null)reasons.add(BlockedReason.INVALID_GRAPH);
        }
        Map<String,ProtectionStore.Transaction> transactions=new HashMap<>();Map<String,Integer> incoming=new HashMap<>();
        for(ProtectionStore.Transaction tx:g.transactions){
            if(transactions.put(tx.id,tx)!=null)reasons.add(BlockedReason.INVALID_GRAPH);
            incoming.merge(tx.original.id,1,Integer::sum);incoming.merge(tx.attempted.id,1,Integer::sum);
        }
        Map<String,HistoryIdentity.Descriptor> histories=new HashMap<>();
        for(HistoryIdentity.Descriptor identity:g.identities)if(histories.put(identity.id,identity)!=null)reasons.add(BlockedReason.INVALID_GRAPH);
        List<Group> groups=new ArrayList<>();Map<String,Group> byTransaction=new HashMap<>();Map<String,List<Group>> bySnapshot=new HashMap<>();
        Map<String,List<Group>> byHistory=new HashMap<>();Set<Long> sequences=new HashSet<>();
        for(HistoryGraph.Ownership owner:g.ownerships){
            ProtectionStore.Transaction tx=transactions.get(owner.transactionId);HistoryIdentity.Descriptor identity=histories.get(owner.historyId);
            if(tx==null||!tx.verified||identity==null||!identity.matches(tx.original)||!identity.matches(tx.attempted)
                    ||!owner.originalSnapshotId.equals(tx.original.id)||!owner.attemptedSnapshotId.equals(tx.attempted.id)
                    ||owner.sequence<=0||!sequences.add(owner.sequence)||byTransaction.containsKey(owner.transactionId)){
                reasons.add(BlockedReason.INVALID_GRAPH);continue;
            }
            Group group=new Group(tx,owner,identity);
            group.shared=incoming.getOrDefault(tx.original.id,0)!=1||incoming.getOrDefault(tx.attempted.id,0)!=1;
            group.records.add(required(records,"store",tx.id,"complete"));group.records.add(required(records,"store",tx.id,"transaction"));
            group.records.add(required(records,"store",tx.original.id,"snapshot"));group.records.add(required(records,"store",tx.attempted.id,"snapshot"));
            group.records.add(required(records,"index",owner.id,"index"));
            if(group.records.contains(null))reasons.add(BlockedReason.INVALID_GRAPH);
            groups.add(group);byTransaction.put(tx.id,group);byHistory.computeIfAbsent(owner.historyId,key->new ArrayList<>()).add(group);
            bySnapshot.computeIfAbsent(tx.original.id,key->new ArrayList<>()).add(group);bySnapshot.computeIfAbsent(tx.attempted.id,key->new ArrayList<>()).add(group);
        }
        Map<String,HistoryCatalog.Checkpoint> names=new HashMap<>();
        for(HistoryCatalog.Checkpoint name:g.checkpoints){
            if(names.put(name.id,name)!=null)reasons.add(BlockedReason.INVALID_GRAPH);
            for(Group group:bySnapshot.getOrDefault(name.snapshotId,Collections.emptyList())) {
                group.names.add(name);group.records.add(required(records,"catalog",name.id,"checkpoint"));
                if(group.records.get(group.records.size()-1)==null)reasons.add(BlockedReason.INVALID_GRAPH);
            }
        }
        if(!reasons.isEmpty())return result(g,automatic,bytes,entries,ids,reasons,0,0,Collections.emptyList(),Collections.emptyList(),Collections.emptyList(),null,0);
        Comparator<Group> oldest=Comparator.comparingLong((Group group)->group.owner.sequence).thenComparing(group->group.tx.id);
        Map<String,Group> anchors=new HashMap<>();
        for(Map.Entry<String,List<Group>> history:byHistory.entrySet()){
            history.getValue().sort(oldest.reversed());anchors.put(history.getKey(),history.getValue().get(0));
            for(int i=0;i<history.getValue().size();i++)history.getValue().get(i).rank=i+1;
        }
        groups.sort(oldest);
        List<Group> eligible=new ArrayList<>();long eligibleBytes=0,eligibleEntries=0;
        for(Group group:groups)if(group!=anchors.get(group.owner.historyId)&&!group.shared&&(!automatic||group.names.isEmpty())){
            eligible.add(group);for(ProtectionStore.StoredRecord record:group.records){eligibleBytes=Math.addExact(eligibleBytes,record.serializedBytes);if("store".equals(record.rootRole))eligibleEntries++;}
        }
        List<Group> chosen=new ArrayList<>();List<HistoryCatalog.Checkpoint> selectedNames=new ArrayList<>();
        if(automatic){
            if(bytes>eligibleBytes||entries>eligibleEntries)reasons.add(BlockedReason.CAPACITY_SHORTFALL);
            else {
                long reclaimed=0;int storeSlots=0;
                boolean pressure=bytes>0||entries>0;
                for(Group group:eligible){
                    if(pressure&&(reclaimed>=bytes&&storeSlots>=entries))break;
                    if(pressure||group.rank>Math.max(1,g.policy.automaticCount)){
                        chosen.add(group);reclaimed=Math.addExact(reclaimed,bytes(group));storeSlots+=4;
                    }
                }
            }
        }else {
            if(ids.isEmpty())reasons.add(BlockedReason.INVALID_REQUEST);
            Set<String> chosenIds=new HashSet<>();
            for(String id:ids){
                HistoryCatalog.Checkpoint name=names.get(id);
                if(name==null){reasons.add(BlockedReason.UNKNOWN_CHECKPOINT);continue;}
                List<Group> owners=bySnapshot.get(name.snapshotId);
                if(owners==null||owners.isEmpty()){reasons.add(BlockedReason.UNOWNED_SELECTION);continue;}
                if(owners.size()!=1){reasons.add(BlockedReason.SHARED_SNAPSHOT);continue;}
                Group group=owners.get(0);
                if(group.shared)reasons.add(BlockedReason.SHARED_SNAPSHOT);
                if(group==anchors.get(group.owner.historyId))reasons.add(BlockedReason.NEWEST_ANCHOR);
                for(HistoryCatalog.Checkpoint pin:group.names)if(!ids.contains(pin.id))reasons.add(BlockedReason.INCOMPLETE_SELECTION);
                if(chosenIds.add(group.tx.id))chosen.add(group);
                selectedNames.add(name);
            }
        }
        chosen.sort(oldest);selectedNames.sort(Comparator.comparing(name->name.id));
        List<GroupProof> proofs=new ArrayList<>();List<ProtectionStore.StoredRecord> routing=new ArrayList<>();
        boolean moreCount=false;
        if(reasons.isEmpty()){
            // Preserve all exact routing/provenance envelopes; descriptors do not expose FORK record IDs.
            if(!chosen.isEmpty())for(ProtectionStore.StoredRecord record:g.records)if("identity".equals(record.rootRole))routing.add(record);
            Set<String> unique=new HashSet<>();int memberCount=0;
            for(Group group:chosen){
                GroupProof candidate=new GroupProof(group,anchors.get(group.owner.historyId));
                boolean fits=memberCount+candidate.records.size()<=256;
                if(fits&&automatic){
                    proofs.add(candidate);
                    try{encode(g,automatic,false,bytes,entries,ids,proofs,selectedNames,routing);}
                    catch(ProofLimit tooLarge){fits=false;}
                    proofs.remove(proofs.size()-1);
                }
                if(!fits){
                    if(automatic&&bytes==0&&entries==0&&!proofs.isEmpty())moreCount=true;
                    else{reasons.add(BlockedReason.PLAN_LIMIT);if(automatic&&(bytes>0||entries>0))reasons.add(BlockedReason.CAPACITY_SHORTFALL);}
                    break;
                }
                proofs.add(candidate);
                for(ProtectionStore.StoredRecord record:candidate.records){memberCount++;if(!unique.add(record.rootRole+"/"+record.filename))reasons.add(BlockedReason.INVALID_GRAPH);}
            }
        }
        String proofHash=null;long proofBytes=0;
        if(reasons.isEmpty()){
            try{Proof encoded=encode(g,automatic,moreCount,bytes,entries,ids,proofs,selectedNames,routing);proofHash=encoded.hash;proofBytes=encoded.bytes;}
            catch(ProofLimit tooLarge){reasons.add(BlockedReason.PLAN_LIMIT);}
        }
        return result(g,automatic,bytes,entries,ids,reasons,eligibleBytes,eligibleEntries,proofs,selectedNames,routing,proofHash,proofBytes,moreCount);
    }
    private DeletePlan result(HistoryGraph.Graph g,boolean automatic,long bytes,int entries,Set<String> ids,Set<BlockedReason> reasons,
            long eligibleBytes,long eligibleEntries,List<GroupProof> groups,List<HistoryCatalog.Checkpoint> names,List<ProtectionStore.StoredRecord> routing,String proofHash,long proofBytes){
        return result(g,automatic,bytes,entries,ids,reasons,eligibleBytes,eligibleEntries,groups,names,routing,proofHash,proofBytes,false);
    }
    private DeletePlan result(HistoryGraph.Graph g,boolean automatic,long bytes,int entries,Set<String> ids,Set<BlockedReason> reasons,
            long eligibleBytes,long eligibleEntries,List<GroupProof> groups,List<HistoryCatalog.Checkpoint> names,List<ProtectionStore.StoredRecord> routing,String proofHash,long proofBytes,boolean moreCount){
        return new DeletePlan(graph,g,automatic,bytes,entries,ids,reasons,eligibleBytes,eligibleEntries,groups,names,routing,proofHash,proofBytes,moreCount);
    }
    private static ProtectionStore.StoredRecord required(Map<String,ProtectionStore.StoredRecord> records,String role,String id,String kind){
        ProtectionStore.StoredRecord record=records.get(role+"/"+id+"."+kind);
        return record!=null&&id.equals(record.id)&&kind.equals(record.kind)?record:null;
    }
    private static long bytes(Group group){long bytes=0;for(ProtectionStore.StoredRecord record:group.records)bytes=Math.addExact(bytes,record.serializedBytes);return bytes;}
    private static final class Proof {final String hash;final long bytes;Proof(String hash,long bytes){this.hash=hash;this.bytes=bytes;}}
    private static final class ProofLimit extends IOException {}
    private static final class BoundedProof extends OutputStream {
        // 128 bytes reserved for future intent magic/version/length/checksum framing.
        long bytes=128;final MessageDigest digest;
        BoundedProof()throws IOException {digest=HistoryStorage.digest();}
        @Override public void write(int value)throws IOException {if(++bytes>65536)throw new ProofLimit();digest.update((byte)value);}
        @Override public void write(byte[] values,int offset,int count)throws IOException {if(count>65536-bytes)throw new ProofLimit();bytes+=count;digest.update(values,offset,count);}
    }
    private static Proof encode(HistoryGraph.Graph graph,boolean automatic,boolean moreCount,long bytes,int entries,Set<String> ids,List<GroupProof> groups,
            List<HistoryCatalog.Checkpoint> names,List<ProtectionStore.StoredRecord> routing)throws IOException {
        BoundedProof proof=new BoundedProof();DataOutputStream out=new DataOutputStream(proof);
        text(out,"history-prune-preview-v1");text(out,graph.fingerprint);out.writeLong(graph.policy.quotaBytes);out.writeInt(graph.policy.automaticCount);
        out.writeBoolean(automatic);out.writeBoolean(moreCount);out.writeLong(bytes);out.writeInt(entries);out.writeInt(ids.size());for(String id:ids)text(out,id);
        out.writeInt(groups.size());
        for(GroupProof group:groups){
            text(out,group.historyId);text(out,group.transactionId);text(out,group.ownershipId);text(out,group.expectedHash);out.writeLong(group.sequence);
            snapshot(out,group.original);snapshot(out,group.attempted);records(out,group.records);
            SurvivorProof survivor=group.survivor;text(out,survivor.historyId);text(out,survivor.transactionId);text(out,survivor.ownershipId);text(out,survivor.expectedHash);out.writeLong(survivor.sequence);
            snapshot(out,survivor.original);snapshot(out,survivor.attempted);records(out,survivor.records);text(out,survivor.exactUri);text(out,survivor.tabId);
            out.writeInt(survivor.origins.size());for(String origin:survivor.origins)text(out,origin);
        }
        out.writeInt(names.size());for(HistoryCatalog.Checkpoint name:names){text(out,name.id);text(out,name.snapshotId);text(out,name.documentId);text(out,name.snapshotHash);text(out,name.label);out.writeLong(name.createdAt);}
        records(out,routing);out.flush();return new Proof(HistoryStorage.hex(proof.digest.digest()),proof.bytes);
    }
    private static void snapshot(DataOutputStream out,SnapshotProof s)throws IOException {
        text(out,s.id);text(out,s.documentId);text(out,s.uri);text(out,s.title);text(out,s.hash);text(out,s.role);out.writeLong(s.revision);out.writeLong(s.timestamp);out.writeInt(s.byteCount);
    }
    private static void records(DataOutputStream out,List<ProtectionStore.StoredRecord> records)throws IOException {
        out.writeInt(records.size());for(ProtectionStore.StoredRecord record:records){text(out,record.rootRole);text(out,record.filename);text(out,record.id);text(out,record.kind);out.writeLong(record.serializedBytes);text(out,record.envelopeHash);}
    }
    private static void text(DataOutputStream out,String value)throws IOException {if(value==null){out.writeInt(-1);return;}byte[] utf8=value.getBytes(StandardCharsets.UTF_8);out.writeInt(utf8.length);out.write(utf8);}
    private static <T> List<T> freeze(Collection<T> values){return Collections.unmodifiableList(new ArrayList<>(values));}
    private static <T> Set<T> freezeSet(Collection<T> values){return Collections.unmodifiableSet(new LinkedHashSet<>(values));}
}
