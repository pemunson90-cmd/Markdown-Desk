package com.example.markdowndesk;

import java.io.*;
import java.util.*;

/** Optional append-only ownership/order metadata; actual ProtectionStore completions are authoritative. */
public final class HistoryIndex {
    public enum Status {INDEXED_VERIFIED, UNINDEXED_VERIFIED, PENDING, ORPHAN}
    public static final class Version {
        public final String snapshotId, groupId, documentId, uri, hash, sourceKind;
        public final long revision, timestamp, sequence;
        public final int byteCount;
        public final Status status;
        public final boolean verified;
        /** This backend grants no pruning eligibility, including when optional indexing succeeds. */
        public final boolean protectedFromPruning=true;
        public final List<HistoryCatalog.Checkpoint> names;
        private Version(ProtectionStore.Snapshot snapshot,String groupId,long sequence,Status status,List<HistoryCatalog.Checkpoint> names){
            snapshotId=snapshot.id;this.groupId=groupId;documentId=snapshot.documentId;uri=snapshot.uri;hash=snapshot.hash;
            sourceKind=snapshot.role;revision=snapshot.revision;timestamp=snapshot.timestamp;byteCount=snapshot.byteCount;
            this.sequence=sequence;this.status=status;verified=status==Status.INDEXED_VERIFIED||status==Status.UNINDEXED_VERIFIED;
            this.names=Collections.unmodifiableList(new ArrayList<>(names));
        }
    }
    static final class Ownership {
        final String id, historyId, transactionId;
        final long sequence;
        final Metadata original,attempted;
        Ownership(String id,String historyId,String transactionId,long sequence,Metadata original,Metadata attempted){
            this.id=id;this.historyId=historyId;this.transactionId=transactionId;this.sequence=sequence;this.original=original;this.attempted=attempted;
        }
    }
    /** Full source metadata; never holds a payload or a Snapshot handle. */
    static final class Metadata {
        final String id,documentId,uri,title,hash,role;
        final long revision,timestamp;
        final int size;
        Metadata(ProtectionStore.Snapshot snapshot){
            id=snapshot.id;documentId=snapshot.documentId;uri=snapshot.uri;title=snapshot.title;hash=snapshot.hash;role=snapshot.role;
            revision=snapshot.revision;timestamp=snapshot.timestamp;size=snapshot.byteCount;
        }
        Metadata(DataInputStream in)throws IOException {
            id=HistoryMetadataIO.string(in);documentId=HistoryMetadataIO.string(in);uri=nullable(in);title=nullable(in);
            hash=HistoryMetadataIO.string(in);role=HistoryMetadataIO.string(in);revision=in.readLong();timestamp=in.readLong();size=in.readInt();
        }
        void write(DataOutputStream out)throws IOException {
            HistoryMetadataIO.string(out,id);HistoryMetadataIO.string(out,documentId);nullable(out,uri);nullable(out,title);
            HistoryMetadataIO.string(out,hash);HistoryMetadataIO.string(out,role);out.writeLong(revision);out.writeLong(timestamp);out.writeInt(size);
        }
        boolean matches(ProtectionStore.Snapshot snapshot){
            return id.equals(snapshot.id)&&documentId.equals(snapshot.documentId)&&Objects.equals(uri,snapshot.uri)&&Objects.equals(title,snapshot.title)
                    &&hash.equals(snapshot.hash)&&role.equals(snapshot.role)&&revision==snapshot.revision&&timestamp==snapshot.timestamp&&size==snapshot.byteCount;
        }
    }
    private static final class Sources {
        final Map<String,ProtectionStore.Transaction> groups=new LinkedHashMap<>();
        final Map<String,ProtectionStore.Snapshot> snapshots=new LinkedHashMap<>();
        final Map<String,List<HistoryCatalog.Checkpoint>> names=new HashMap<>();
        final Map<String,HistoryIdentity.Descriptor> histories=new HashMap<>();
    }
    File storageRoot(){return io.root;}
    boolean uses(ProtectionStore source,HistoryCatalog names,HistoryIdentity routes){return store==source&&catalog==names&&identity==routes;}
    List<Ownership> ownershipsAgainst(ProtectionStore.ValidatedSources validated)throws IOException {return Collections.unmodifiableList(readAll(sources(validated)));}
    private final ProtectionStore store;
    private final HistoryCatalog catalog;
    private final HistoryIdentity identity;
    private final HistoryMetadataIO io;
    public HistoryIndex(ProtectionStore store,HistoryCatalog catalog,HistoryIdentity identity,File root,ProtectionStore.Durability durability)throws IOException {
        if(store==null||catalog==null||identity==null)throw new IOException("Invalid history index configuration");
        Object parentMonitor=HistoryStorage.monitorFor(root);
        if(parentMonitor!=HistoryStorage.monitorFor(store.storageRoot())||parentMonitor!=HistoryStorage.monitorFor(catalog.storageRoot())
                ||parentMonitor!=HistoryStorage.monitorFor(identity.storageRoot())||catalog.sourceStore()!=store)
            throw new IOException("Index dependencies require same private parent and source");
        this.store=store;this.catalog=catalog;this.identity=identity;
        io=new HistoryMetadataIO(root,".index",0x4d444858,durability);
        synchronized(io.lock){try(HistoryStorage.MutationScope scope=HistoryStorage.administration(io.root)){if(!HistoryStorage.controlled(io.root))readAll(sources());}}
    }
    /**
     * Use the native URI bound to historyId, never a content hash to infer identity.
     * Call after successful save. Any IOException is an optional history error; the caller must
     * still report the successful provider outcome. A returned coordinator handle can be stale:
     * this API deliberately validates actual store records to establish verified completion.
     */
    public void recordVerified(String historyId,String transactionId)throws IOException {
        HistoryMetadataIO.id(historyId);HistoryMetadataIO.id(transactionId);
        synchronized(io.lock){try(HistoryStorage.MutationScope scope=HistoryStorage.mutation(io.root,4)){
            HistoryStorage.requireRole(store.storageRoot(),1);HistoryStorage.requireRole(catalog.storageRoot(),2);HistoryStorage.requireRole(identity.storageRoot(),3);
            Sources sources=sources();List<Ownership> records=readAll(sources);
            ProtectionStore.Transaction transaction=sources.groups.get(transactionId);
            HistoryIdentity.Descriptor descriptor=history(sources,historyId);
            if(transaction==null||!transaction.verified||!descriptor.matches(transaction.original)||!descriptor.matches(transaction.attempted))
                throw new IOException("History ownership requires matching freshly verified transaction");
            long max=0;
            for(Ownership record:records){
                if(record.transactionId.equals(transactionId)){
                    if(!record.historyId.equals(historyId))throw new IOException("Transaction already owned by different history");
                    return;
                }max=Math.max(max,record.sequence);
            }
            if(max==Long.MAX_VALUE)throw new IOException("History sequence exhausted");
            Ownership record=new Ownership(UUID.randomUUID().toString(),historyId,transactionId,max+1,new Metadata(transaction.original),new Metadata(transaction.attempted));
            byte[] bytes=io.encode(out->{HistoryMetadataIO.string(out,record.id);HistoryMetadataIO.string(out,record.historyId);
                HistoryMetadataIO.string(out,record.transactionId);out.writeLong(record.sequence);record.original.write(out);record.attempted.write(out);});
            // Fresh source/identity/catalog validation immediately before optional publication.
            io.commit(record.id,bytes,()->validate(record,sources()));readAll(sources());
        }}
    }
    /** Complete validated graph before filtering. Indexed order is persisted sequence; timestamps display only. */
    public List<Version> list(String historyId)throws IOException {
        HistoryMetadataIO.id(historyId);
        synchronized(io.lock){
            Sources sources=sources();List<Ownership> records=readAll(sources);HistoryIdentity.Descriptor descriptor=history(sources,historyId);
            Map<String,Ownership> byGroup=new HashMap<>();for(Ownership record:records)byGroup.put(record.transactionId,record);
            List<Version> result=new ArrayList<>();Set<String> grouped=new HashSet<>();
            for(ProtectionStore.Transaction transaction:sources.groups.values()){
                grouped.add(transaction.original.id);grouped.add(transaction.attempted.id);
                if(!descriptor.matches(transaction.original))continue;
                Ownership record=byGroup.get(transaction.id);
                Status status=!transaction.verified?Status.PENDING:record==null?Status.UNINDEXED_VERIFIED:Status.INDEXED_VERIFIED;
                long sequence=record==null?0:record.sequence;
                result.add(version(transaction.original,transaction.id,sequence,status,sources));
                result.add(version(transaction.attempted,transaction.id,sequence,status,sources));
            }
            for(ProtectionStore.Snapshot snapshot:sources.snapshots.values())if(!grouped.contains(snapshot.id)&&descriptor.matches(snapshot))
                result.add(version(snapshot,null,0,Status.ORPHAN,sources));
            result.sort(Comparator.comparing((Version v)->v.status.ordinal()).thenComparingLong(v->v.sequence)
                    .thenComparing(v->v.groupId==null?v.snapshotId:v.groupId).thenComparing(v->"original".equals(v.sourceKind)?0:1).thenComparing(v->v.snapshotId));
            return Collections.unmodifiableList(result);
        }
    }
    private static Version version(ProtectionStore.Snapshot snapshot,String groupId,long sequence,Status status,Sources sources){
        return new Version(snapshot,groupId,sequence,status,sources.names.getOrDefault(snapshot.id,Collections.emptyList()));
    }
    private Sources sources()throws IOException {return store.withValidatedSources(this::sources);}
    private Sources sources(ProtectionStore.ValidatedSources validated)throws IOException {
        validated.require(store);if(!validated.inventory.complete)throw new IOException("Incomplete source inventory");
        Sources sources=new Sources();
        for(HistoryIdentity.Descriptor descriptor:identity.descriptors())sources.histories.put(descriptor.id,descriptor);
        for(ProtectionStore.Transaction transaction:validated.inventory.transactions){
            if(sources.groups.put(transaction.id,transaction)!=null)throw new IOException("Duplicate source transaction");
        }
        for(ProtectionStore.Snapshot snapshot:validated.inventory.snapshots){
            if(sources.snapshots.put(snapshot.id,snapshot)!=null)throw new IOException("Duplicate source snapshot");
        }
        for(ProtectionStore.Transaction transaction:sources.groups.values())
            if(!new Metadata(transaction.original).matches(requiredSnapshot(sources,transaction.original.id))
                    ||!new Metadata(transaction.attempted).matches(requiredSnapshot(sources,transaction.attempted.id)))throw new IOException("Source graph changed during enumeration");
        // Catalog's full scan detects invalid checkpoints for all documents, even before filtering.
        for(HistoryCatalog.Checkpoint checkpoint:catalog.allAgainstValidatedSnapshots(validated)){
            ProtectionStore.Snapshot snapshot=requiredSnapshot(sources,checkpoint.snapshotId);
            if(!checkpoint.documentId.equals(snapshot.documentId)||!checkpoint.snapshotHash.equals(snapshot.hash))throw new IOException("Checkpoint graph mismatch");
            sources.names.computeIfAbsent(checkpoint.snapshotId,ignored->new ArrayList<>()).add(checkpoint);
        }return sources;
    }
    private List<Ownership> readAll(Sources sources)throws IOException {
        List<Ownership> records=new ArrayList<>();Set<String> groups=new HashSet<>();Set<Long> sequences=new HashSet<>();
        for(File file:io.files())try(DataInputStream in=io.body(file)){
            Ownership record=new Ownership(HistoryMetadataIO.string(in),HistoryMetadataIO.string(in),HistoryMetadataIO.string(in),in.readLong(),new Metadata(in),new Metadata(in));
            if(in.available()!=0||!file.getName().equals(record.id+".index"))throw new IOException("History index framing mismatch");
            HistoryMetadataIO.id(record.id);HistoryMetadataIO.id(record.historyId);HistoryMetadataIO.id(record.transactionId);
            if(record.sequence<=0||!sequences.add(record.sequence)||!groups.add(record.transactionId))throw new IOException("Duplicate/conflicting history sequence or ownership");
            validate(record,sources);records.add(record);
        }return records;
    }
    private static void validate(Ownership record,Sources sources)throws IOException {
        ProtectionStore.Transaction transaction=sources.groups.get(record.transactionId);HistoryIdentity.Descriptor descriptor=history(sources,record.historyId);
        if(transaction==null||!transaction.verified||!descriptor.matches(transaction.original)||!descriptor.matches(transaction.attempted)
                ||!record.original.matches(transaction.original)||!record.attempted.matches(transaction.attempted))throw new IOException("Dangling/changed/unverified history ownership");
    }
    private static HistoryIdentity.Descriptor history(Sources sources,String id)throws IOException {
        HistoryIdentity.Descriptor descriptor=sources.histories.get(id);if(descriptor==null)throw new IOException("Unknown history identity");return descriptor;
    }
    private static ProtectionStore.Snapshot requiredSnapshot(Sources sources,String id)throws IOException {
        ProtectionStore.Snapshot snapshot=sources.snapshots.get(id);if(snapshot==null)throw new IOException("Dangling source snapshot");return snapshot;
    }
    private static void nullable(DataOutputStream out,String value)throws IOException {out.writeBoolean(value!=null);if(value!=null)HistoryMetadataIO.string(out,value);}
    private static String nullable(DataInputStream in)throws IOException {
        int marker=in.readUnsignedByte();if(marker!=0&&marker!=1)throw new IOException("Invalid nullable string framing");return marker==0?null:HistoryMetadataIO.string(in);
    }
}
