package com.example.markdowndesk;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/**
 * Pure, fresh-scan retention previews. These plans grant no unlink authority: a future executor
 * must rescan under the common storage lock and include live operation pins/maintenance state.
 * Capacity counts serialized envelopes across roots, never payload sizes or admission credit.
 */
public final class HistoryPruner {
    public static final int MAX_MEMBERS=256, MAX_PROOF_BYTES=64*1024, MAX_EXTERNAL_PINS=256;
    /** Floor previews retain the configured count; pressure previews may retain only fixed anchors. */
    public enum Mode { AUTOMATIC_FLOOR, AUTOMATIC_PRESSURE, SELECTED }
    public enum BlockedReason {
        INVALID_REQUEST, MISSING_POLICY, INCOMPLETE_GRAPH, UNKNOWN_EXTERNAL_PIN,
        AUTOMATIC_DISABLED, UNKNOWN_CHECKPOINT, PARTIAL_CHECKPOINT_SELECTION,
        NEWEST_ANCHOR, PROTECTED_GROUP, CAPACITY_SHORTFALL, PLAN_LIMIT, ACCOUNTING_OVERFLOW
    }
    /** Complete directed closure of each removed/surviving group, using logical roles and UUIDs. */
    public static final class Reference {
        public final String sourceRole, sourceId, sourceKind, targetRole, targetId, targetKind;
        private Reference(String role,String id,String kind,String targetRole,String targetId,String targetKind){
            sourceRole=role;sourceId=id;sourceKind=kind;this.targetRole=targetRole;this.targetId=targetId;this.targetKind=targetKind;
        }
    }
    /** Validated index ownership, exact pair closure and full serialized member descriptors. */
    public static final class GroupProof {
        public final String historyId, ownershipId, transactionId, originalSnapshotId, attemptedSnapshotId;
        public final long sequence;
        public final List<ProtectionStore.StoredRecord> records;
        public final List<Reference> references;
        public final Set<String> checkpointIds;
        private GroupProof(HistoryGraph.Ownership ownership,List<ProtectionStore.StoredRecord> records,
                           List<Reference> references,Collection<String> names){
            historyId=ownership.historyId;ownershipId=ownership.id;transactionId=ownership.transactionId;
            originalSnapshotId=ownership.originalSnapshotId;attemptedSnapshotId=ownership.attemptedSnapshotId;
            sequence=ownership.sequence;this.records=freeze(records);this.references=freeze(references);checkpointIds=freezeSet(names);
        }
    }
    public static final class DeletePlan {
        public final boolean automatic;
        public final Mode mode;
        public final long requiredBytes, requiredEntries, bytes, entries;
        public final HistoryPolicy policy;
        public final String graphFingerprint, proofFingerprint;
        /** Exact canonical proof serialization size; no payloads or arbitrary filesystem paths. */
        public final int proofBytes;
        public final Set<String> selectedCheckpointIds, externalProtectedSnapshotIds;
        public final List<ProtectionStore.StoredRecord> members;
        public final List<GroupProof> groups, anchors;
        /** Immutable routing/provenance descriptors for removed histories; all identity envelopes
         * and every unaffected reference remain bound by graphFingerprint. Identity is never a member. */
        public final List<HistoryIdentity.Descriptor> identities;
        public final Map<String,Long> entriesByRoot, bytesByRoot;
        private DeletePlan(Mode mode,long requiredBytes,long requiredEntries,HistoryGraph.Graph graph,
                           Collection<String> selected,Collection<String> pins,List<GroupProof> groups,
                           List<GroupProof> anchors,List<HistoryIdentity.Descriptor> identities,
                           List<ProtectionStore.StoredRecord> members,Capacity capacity,
                           Map<String,Long> entriesByRoot,Map<String,Long> bytesByRoot)throws IOException {
            this.mode=mode;automatic=mode!=Mode.SELECTED;this.requiredBytes=requiredBytes;this.requiredEntries=requiredEntries;
            bytes=capacity.bytes;entries=capacity.entries;policy=graph.policy;graphFingerprint=graph.fingerprint;
            selectedCheckpointIds=freezeSet(selected);externalProtectedSnapshotIds=freezeSet(pins);
            this.groups=freeze(groups);this.anchors=freeze(anchors);this.identities=freeze(identities);this.members=freeze(members);
            this.entriesByRoot=Collections.unmodifiableMap(new TreeMap<>(entriesByRoot));this.bytesByRoot=Collections.unmodifiableMap(new TreeMap<>(bytesByRoot));
            ProofOutput proof=new ProofOutput();MessageDigest digest=HistoryStorage.digest();
            try(DataOutputStream out=new DataOutputStream(new java.security.DigestOutputStream(proof,digest))){writeProof(out,this);}
            proofBytes=proof.count;proofFingerprint=HistoryStorage.hex(digest.digest());
        }
    }
    /** A blocked result never contains a partial plan. Eligible capacity describes ALL qualifying
     * groups before proof/member bounds; protected capacity includes outstanding store reservations. */
    public static final class Result {
        public final DeletePlan plan;
        public final BlockedReason blockedReason;
        public final long requiredBytes, requiredEntries, eligibleBytes, eligibleEntries, protectedBytes, protectedEntries;
        private Result(DeletePlan plan,BlockedReason blocked,long requiredBytes,long requiredEntries,
                       Capacity eligible,long protectedBytes,long protectedEntries){
            this.plan=plan;blockedReason=blocked;this.requiredBytes=requiredBytes;this.requiredEntries=requiredEntries;
            eligibleBytes=eligible.bytes;eligibleEntries=eligible.entries;this.protectedBytes=protectedBytes;this.protectedEntries=protectedEntries;
        }
    }
    private static final class Capacity {
        long bytes,entries;
        void add(ProtectionStore.StoredRecord r){bytes=Math.addExact(bytes,r.serializedBytes);entries=Math.addExact(entries,1);}
        void add(Capacity c){bytes=Math.addExact(bytes,c.bytes);entries=Math.addExact(entries,c.entries);}
    }
    private static final class Candidate {
        final HistoryGraph.Ownership ownership;
        final ProtectionStore.Transaction transaction;
        final List<HistoryCatalog.Checkpoint> names=new ArrayList<>();
        final Capacity capacity=new Capacity();
        GroupProof proof;
        boolean exclusive;
        Candidate(HistoryGraph.Ownership ownership,ProtectionStore.Transaction t){this.ownership=ownership;transaction=t;}
    }
    private static final Comparator<Candidate> ORDER=Comparator.comparingLong(c->c.ownership.sequence);
    private static final class Model {
        final HistoryGraph.Graph graph;
        final Map<String,ProtectionStore.StoredRecord> records=new HashMap<>();
        final Map<String,ProtectionStore.Transaction> transactions=new HashMap<>();
        final Map<String,List<String>> incoming=new HashMap<>();
        final Map<String,Candidate> byTransaction=new HashMap<>();
        final Map<String,Candidate> anchors=new TreeMap<>();
        final Map<String,List<Candidate>> histories=new TreeMap<>();
        final Map<String,HistoryCatalog.Checkpoint> checkpoints=new HashMap<>();
        final Map<String,HistoryIdentity.Descriptor> identities=new TreeMap<>();
        Model(HistoryGraph.Graph graph)throws IOException {
            this.graph=graph;
            for(ProtectionStore.StoredRecord r:graph.records){
                if(r.id==null||r.serializedBytes<0||r.envelopeHash==null||!r.envelopeHash.matches("[0-9a-f]{64}")
                        ||records.put(key(r.rootRole,r.filename),r)!=null)throw new IOException("Incomplete envelope proof");
            }
            for(HistoryIdentity.Descriptor identity:graph.identities)if(identities.put(identity.id,identity)!=null)throw new IOException("Duplicate identity");
            for(ProtectionStore.Transaction t:graph.transactions){
                if(transactions.put(t.id,t)!=null)throw new IOException("Duplicate transaction");
                incoming.computeIfAbsent(t.original.id,ignored->new ArrayList<>()).add(t.id);
                incoming.computeIfAbsent(t.attempted.id,ignored->new ArrayList<>()).add(t.id);
            }
            for(HistoryGraph.Ownership ownership:graph.ownerships){
                ProtectionStore.Transaction t=transactions.get(ownership.transactionId);
                HistoryIdentity.Descriptor identity=identities.get(ownership.historyId);
                if(t==null||!t.verified||identity==null||!identity.matches(t.original)||!identity.matches(t.attempted)
                        ||!ownership.originalSnapshotId.equals(t.original.id)||!ownership.attemptedSnapshotId.equals(t.attempted.id))throw new IOException("Invalid ownership proof");
                Candidate c=new Candidate(ownership,t);
                if(byTransaction.put(t.id,c)!=null)throw new IOException("Conflicting ownership");
                histories.computeIfAbsent(ownership.historyId,ignored->new ArrayList<>()).add(c);
                c.exclusive=incoming.get(t.original.id).size()==1&&incoming.get(t.attempted.id).size()==1;
            }
            for(List<Candidate> history:histories.values()){history.sort(ORDER);Candidate anchor=history.get(history.size()-1);anchors.put(anchor.ownership.historyId,anchor);}
            for(HistoryCatalog.Checkpoint checkpoint:graph.checkpoints){
                if(checkpoints.put(checkpoint.id,checkpoint)!=null)throw new IOException("Duplicate checkpoint");
                for(String transaction:incoming.getOrDefault(checkpoint.snapshotId,Collections.emptyList())){
                    Candidate c=byTransaction.get(transaction);if(c!=null)c.names.add(checkpoint);
                }
            }
            for(Candidate c:byTransaction.values()){
                c.names.sort(Comparator.comparing(n->n.id));c.proof=proof(c,true);
                for(ProtectionStore.StoredRecord r:c.proof.records)c.capacity.add(r);
            }
        }
        GroupProof proof(Candidate c,boolean names)throws IOException {
            HistoryGraph.Ownership o=c.ownership;
            List<ProtectionStore.StoredRecord> members=new ArrayList<>();
            members.add(record("store",o.originalSnapshotId,"snapshot"));members.add(record("store",o.attemptedSnapshotId,"snapshot"));
            members.add(record("store",o.transactionId,"transaction"));members.add(record("store",o.transactionId,"complete"));members.add(record("index",o.id,"index"));
            List<Reference> refs=new ArrayList<>();
            refs.add(new Reference("store",o.transactionId,"complete","store",o.transactionId,"transaction"));
            for(String snapshot:Arrays.asList(o.originalSnapshotId,o.attemptedSnapshotId)){
                refs.add(new Reference("store",o.transactionId,"transaction","store",snapshot,"snapshot"));
                // Native completion and index both bind the two sources directly as well.
                refs.add(new Reference("store",o.transactionId,"complete","store",snapshot,"snapshot"));
                refs.add(new Reference("index",o.id,"index","store",snapshot,"snapshot"));
            }
            refs.add(new Reference("index",o.id,"index","store",o.transactionId,"transaction"));
            refs.add(new Reference("index",o.id,"index","identity",o.historyId,"identity"));
            Set<String> selected=new TreeSet<>();
            if(names)for(HistoryCatalog.Checkpoint checkpoint:c.names){
                members.add(record("catalog",checkpoint.id,"checkpoint"));selected.add(checkpoint.id);
                refs.add(new Reference("catalog",checkpoint.id,"checkpoint","store",checkpoint.snapshotId,"snapshot"));
            }
            members.sort(RECORD_ORDER);return new GroupProof(o,members,refs,selected);
        }
        ProtectionStore.StoredRecord record(String role,String id,String kind)throws IOException {
            ProtectionStore.StoredRecord r=records.get(key(role,id+"."+kind));
            if(r==null||!kind.equals(r.kind)||!id.equals(r.id))throw new IOException("Missing closure envelope");return r;
        }
    }
    private static final Comparator<ProtectionStore.StoredRecord> RECORD_ORDER=Comparator.comparing((ProtectionStore.StoredRecord r)->r.rootRole).thenComparing(r->r.filename);
    private final HistoryGraph graph;
    private final Set<String> externalPins;
    public HistoryPruner(HistoryGraph graph){this(graph,Collections.emptySet());}
    /** External pin IDs must be canonical UUIDs; their snapshots must exist in each fresh scan.
     * Construction copies the set. This caller-supplied snapshot never substitutes for live pins. */
    public HistoryPruner(HistoryGraph graph,Collection<String> externalProtectedSnapshotIds){
        if(graph==null||externalProtectedSnapshotIds==null||externalProtectedSnapshotIds.size()>MAX_EXTERNAL_PINS)throw new IllegalArgumentException("Invalid planner/pin snapshot");
        Set<String> pins=new TreeSet<>();for(String id:externalProtectedSnapshotIds){if(!validId(id)||!pins.add(id))throw new IllegalArgumentException("Invalid/duplicate external pin");}
        this.graph=graph;externalPins=freezeSet(pins);
    }
    public Result planAutomatic(long requiredBytes,long requiredEntries)throws IOException {
        if(requiredBytes<0||requiredEntries<0)return unavailable(BlockedReason.INVALID_REQUEST,requiredBytes,requiredEntries);
        return plan(Mode.AUTOMATIC_FLOOR,requiredBytes,requiredEntries,Collections.emptySet());
    }
    /** Explicit quota-pressure preview: count is a desired target, while newest indexed pairs
     * remain fixed anchors. The caller supplies actual capacity demand; this pure plan does not
     * perform admission, acquire live pins, or authorize deletion. Count zero disables this mode. */
    public Result planAutomaticPressure(long requiredBytes,long requiredEntries)throws IOException {
        if(requiredBytes<0||requiredEntries<0)return unavailable(BlockedReason.INVALID_REQUEST,requiredBytes,requiredEntries);
        return plan(Mode.AUTOMATIC_PRESSURE,requiredBytes,requiredEntries,Collections.emptySet());
    }
    public Result planSelected(Collection<String> checkpointIds)throws IOException {
        if(checkpointIds==null||checkpointIds.isEmpty()||checkpointIds.size()>MAX_MEMBERS)return unavailable(BlockedReason.INVALID_REQUEST,0,0);
        Set<String> selection=new TreeSet<>();for(String id:checkpointIds)if(!validId(id)||!selection.add(id))return unavailable(BlockedReason.INVALID_REQUEST,0,0);
        return plan(Mode.SELECTED,0,0,selection);
    }
    private Result plan(Mode mode,long requiredBytes,long requiredEntries,Set<String> selection)throws IOException {
        boolean automatic=mode!=Mode.SELECTED;
        HistoryGraph.Graph fresh;
        try{fresh=graph.scan();}catch(IOException invalid){return unavailable(BlockedReason.INCOMPLETE_GRAPH,requiredBytes,requiredEntries);}
        if(!fresh.complete||fresh.pruneBlocked)return unavailable(BlockedReason.INCOMPLETE_GRAPH,requiredBytes,requiredEntries);
        if(fresh.policy==null)return unavailable(BlockedReason.MISSING_POLICY,requiredBytes,requiredEntries);
        try{
            Model m;
            try{m=new Model(fresh);}catch(IOException invalid){return unavailable(BlockedReason.INCOMPLETE_GRAPH,requiredBytes,requiredEntries);}
            Set<String> sourceIds=new HashSet<>();for(ProtectionStore.Snapshot snapshot:fresh.snapshots)sourceIds.add(snapshot.id);
            if(!sourceIds.containsAll(externalPins))return unavailable(BlockedReason.UNKNOWN_EXTERNAL_PIN,requiredBytes,requiredEntries);
            if(automatic&&fresh.policy.automaticCount==0)return result(null,BlockedReason.AUTOMATIC_DISABLED,requiredBytes,requiredEntries,new Capacity(),fresh);
            Set<String> retained=new HashSet<>();
            for(List<Candidate> history:m.histories.values()){
                int keep=mode==Mode.AUTOMATIC_FLOOR?Math.max(1,fresh.policy.automaticCount):1;
                for(int i=Math.max(0,history.size()-keep);i<history.size();i++)retained.add(history.get(i).transaction.id);
            }
            List<Candidate> eligible=new ArrayList<>();
            if(automatic){
                for(Candidate c:m.byTransaction.values())if(!retained.contains(c.transaction.id)&&safe(c)&&c.names.isEmpty())eligible.add(c);
            }else{
                Set<Candidate> selectedGroups=new HashSet<>();
                for(String id:selection){
                    HistoryCatalog.Checkpoint name=m.checkpoints.get(id);if(name==null)return result(null,BlockedReason.UNKNOWN_CHECKPOINT,requiredBytes,requiredEntries,new Capacity(),fresh);
                    List<String> incoming=m.incoming.getOrDefault(name.snapshotId,Collections.emptyList());
                    if(incoming.size()!=1)return result(null,BlockedReason.PROTECTED_GROUP,0,0,new Capacity(),fresh);
                    Candidate c=m.byTransaction.get(incoming.get(0));
                    if(c==null)return result(null,BlockedReason.PROTECTED_GROUP,0,0,new Capacity(),fresh);
                    selectedGroups.add(c);
                }
                for(Candidate c:selectedGroups){
                    if(retained.contains(c.transaction.id))return result(null,BlockedReason.NEWEST_ANCHOR,0,0,new Capacity(),fresh);
                    if(!safe(c))return result(null,BlockedReason.PROTECTED_GROUP,0,0,new Capacity(),fresh);
                    if(!selection.containsAll(c.proof.checkpointIds))return result(null,BlockedReason.PARTIAL_CHECKPOINT_SELECTION,0,0,new Capacity(),fresh);
                    eligible.add(c);
                }
            }
            eligible.sort(ORDER);Capacity available=new Capacity();for(Candidate c:eligible)available.add(c.capacity);
            if(available.bytes<requiredBytes||available.entries<requiredEntries)return result(null,BlockedReason.CAPACITY_SHORTFALL,requiredBytes,requiredEntries,available,fresh);
            List<Candidate> chosen=new ArrayList<>();Capacity capacity=new Capacity();
            for(Candidate c:eligible){
                if(automatic&&capacity.bytes>=requiredBytes&&capacity.entries>=requiredEntries)break;
                capacity.add(c.capacity);chosen.add(c);
                if(capacity.entries>MAX_MEMBERS)return result(null,BlockedReason.PLAN_LIMIT,requiredBytes,requiredEntries,available,fresh);
            }
            List<GroupProof> groups=new ArrayList<>(),anchors=new ArrayList<>();List<HistoryIdentity.Descriptor> identities=new ArrayList<>();
            Set<String> chosenHistories=new TreeSet<>();List<ProtectionStore.StoredRecord> members=new ArrayList<>();
            Map<String,Long> entryCounts=new TreeMap<>(),byteCounts=new TreeMap<>();
            for(Candidate c:chosen){groups.add(c.proof);chosenHistories.add(c.ownership.historyId);members.addAll(c.proof.records);}
            for(String history:chosenHistories){
                Candidate anchor=m.anchors.get(history);
                // A fully validated newest indexed pair is required in exactly this history.
                if(anchor==null||chosen.contains(anchor))return result(null,BlockedReason.PROTECTED_GROUP,requiredBytes,requiredEntries,available,fresh);
                anchors.add(m.proof(anchor,false));identities.add(m.identities.get(history));
            }
            members.sort(RECORD_ORDER);Set<String> unique=new HashSet<>();
            for(ProtectionStore.StoredRecord r:members){
                if(!unique.add(key(r.rootRole,r.filename)))return result(null,BlockedReason.INCOMPLETE_GRAPH,requiredBytes,requiredEntries,available,fresh);
                entryCounts.put(r.rootRole,Math.addExact(entryCounts.getOrDefault(r.rootRole,0L),1));
                byteCounts.put(r.rootRole,Math.addExact(byteCounts.getOrDefault(r.rootRole,0L),r.serializedBytes));
            }
            try{
                DeletePlan plan=new DeletePlan(mode,requiredBytes,requiredEntries,fresh,selection,externalPins,groups,anchors,identities,members,capacity,entryCounts,byteCounts);
                return result(plan,null,requiredBytes,requiredEntries,available,fresh);
            }catch(ProofLimit exceeded){return result(null,BlockedReason.PLAN_LIMIT,requiredBytes,requiredEntries,available,fresh);}
        }catch(ArithmeticException overflow){return unavailable(BlockedReason.ACCOUNTING_OVERFLOW,requiredBytes,requiredEntries);}
    }
    private boolean safe(Candidate c){return c.exclusive&&!externalPins.contains(c.transaction.original.id)&&!externalPins.contains(c.transaction.attempted.id);}
    private static Result unavailable(BlockedReason reason,long bytes,long entries){return new Result(null,reason,bytes,entries,new Capacity(),0,0);}
    private static Result result(DeletePlan plan,BlockedReason reason,long bytes,long entries,Capacity eligible,HistoryGraph.Graph graph){
        long protectedBytes=Math.subtractExact(graph.chargedBytes,eligible.bytes);
        long protectedEntries=Math.subtractExact(Math.addExact(graph.records.size(),graph.reservedCompletionSlots),eligible.entries);
        if(protectedBytes<0||protectedEntries<0)throw new ArithmeticException("Invalid capacity closure");
        return new Result(plan,reason,bytes,entries,eligible,protectedBytes,protectedEntries);
    }
    private static String key(String role,String file){return role+"/"+file;}
    private static boolean validId(String id){try{return id!=null&&UUID.fromString(id).toString().equals(id);}catch(IllegalArgumentException invalid){return false;}}
    private static <T> List<T> freeze(Collection<T> values){return Collections.unmodifiableList(new ArrayList<>(values));}
    private static <T> Set<T> freezeSet(Collection<T> values){return Collections.unmodifiableSet(new LinkedHashSet<>(values));}
    private static final class ProofLimit extends IOException {private static final long serialVersionUID=1L;}
    /** Count/hash canonical proof without allocating an encoded proof-sized array. */
    private static final class ProofOutput extends OutputStream {
        int count;
        @Override public void write(int b)throws IOException{charge(1);}
        @Override public void write(byte[] b,int offset,int length)throws IOException{charge(length);}
        void charge(int n)throws IOException{if(n>MAX_PROOF_BYTES-count)throw new ProofLimit();count+=n;}
    }
    private static void writeProof(DataOutputStream out,DeletePlan plan)throws IOException {
        // Preserve existing floor/selected v1 proof bytes. Pressure has its own domain and mode;
        // an otherwise identical selection cannot be mistaken for a legacy floor preview.
        if(plan.mode==Mode.AUTOMATIC_PRESSURE){text(out,"history-retention-pressure-preview-v1");text(out,plan.mode.name());}
        else text(out,"history-retention-preview-v1");
        text(out,plan.graphFingerprint);out.writeBoolean(plan.automatic);
        out.writeLong(plan.requiredBytes);out.writeLong(plan.requiredEntries);out.writeLong(plan.bytes);out.writeLong(plan.entries);
        out.writeLong(plan.policy.quotaBytes);out.writeInt(plan.policy.automaticCount);
        strings(out,plan.externalProtectedSnapshotIds);strings(out,plan.selectedCheckpointIds);
        out.writeInt(plan.members.size());for(ProtectionStore.StoredRecord r:plan.members)record(out,r);
        out.writeInt(plan.groups.size());for(GroupProof g:plan.groups)group(out,g);
        out.writeInt(plan.anchors.size());for(GroupProof g:plan.anchors)group(out,g);
        out.writeInt(plan.identities.size());for(HistoryIdentity.Descriptor identity:plan.identities){
            text(out,identity.id);text(out,identity.exactUri);text(out,identity.tabId);strings(out,new TreeSet<>(identity.origins));
        }
        out.writeInt(plan.entriesByRoot.size());for(String role:plan.entriesByRoot.keySet()){text(out,role);out.writeLong(plan.entriesByRoot.get(role));out.writeLong(plan.bytesByRoot.get(role));}
    }
    private static void group(DataOutputStream out,GroupProof g)throws IOException {
        text(out,g.historyId);text(out,g.ownershipId);text(out,g.transactionId);text(out,g.originalSnapshotId);text(out,g.attemptedSnapshotId);out.writeLong(g.sequence);
        strings(out,g.checkpointIds);out.writeInt(g.records.size());for(ProtectionStore.StoredRecord r:g.records)record(out,r);
        out.writeInt(g.references.size());for(Reference ref:g.references){text(out,ref.sourceRole);text(out,ref.sourceId);text(out,ref.sourceKind);text(out,ref.targetRole);text(out,ref.targetId);text(out,ref.targetKind);}
    }
    private static void record(DataOutputStream out,ProtectionStore.StoredRecord r)throws IOException {text(out,r.rootRole);text(out,r.filename);text(out,r.id);text(out,r.kind);out.writeLong(r.serializedBytes);text(out,r.envelopeHash);}
    private static void strings(DataOutputStream out,Collection<String> values)throws IOException {out.writeInt(values.size());for(String value:values)text(out,value);}
    private static void text(DataOutputStream out,String value)throws IOException {if(value==null){out.writeInt(-1);return;}byte[] bytes=value.getBytes(StandardCharsets.UTF_8);out.writeInt(bytes.length);out.write(bytes);}
}
