package com.example.markdowndesk;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/** Read-only proof/accounting for four private sibling roots and their optional policy ledger. No admission or deletion API. */
public final class HistoryGraph {
    public static final class Ownership {
        public final String id, historyId, transactionId, originalSnapshotId, attemptedSnapshotId;
        public final long sequence;
        private Ownership(HistoryIndex.Ownership record){id=record.id;historyId=record.historyId;transactionId=record.transactionId;
            originalSnapshotId=record.original.id;attemptedSnapshotId=record.attempted.id;sequence=record.sequence;}
    }
    public static final class Graph {
        public final List<ProtectionStore.StoredRecord> records;
        public final List<ProtectionStore.Snapshot> snapshots;
        public final List<ProtectionStore.Transaction> transactions;
        public final List<HistoryCatalog.Checkpoint> checkpoints;
        public final List<HistoryIdentity.Descriptor> identities;
        public final List<Ownership> ownerships;
        public final Set<String> pinnedSnapshotIds, orphanSnapshotIds, unresolvedTransactionIds, unindexedTransactionIds;
        /** All sources are protected in this foundation, including successfully indexed sources. */
        public final Set<String> protectedSnapshotIds;
        public final List<String> diagnostics;
        public final long actualBytes, reservedCompletionBytes, reservedCompletionSlots, chargedBytes;
        public final boolean complete, pruneBlocked, usageComplete;
        public final String fingerprint;
        public final HistoryPolicy policy;
        private Graph(Builder builder,String fingerprint,HistoryPolicy policy){
            records=freeze(builder.records);snapshots=freeze(builder.snapshots);transactions=freeze(builder.transactions);
            checkpoints=freeze(builder.checkpoints);identities=freeze(builder.identities);ownerships=freeze(builder.ownerships);
            pinnedSnapshotIds=freezeSet(builder.pins);orphanSnapshotIds=freezeSet(builder.orphans);unresolvedTransactionIds=freezeSet(builder.unresolved);
            unindexedTransactionIds=freezeSet(builder.unindexed);protectedSnapshotIds=freezeSet(builder.protectedSources);
            diagnostics=freeze(builder.diagnostics);actualBytes=builder.actual;reservedCompletionBytes=builder.reserved;
            reservedCompletionSlots=builder.slots;chargedBytes=builder.charged;usageComplete=builder.usageComplete;
            complete=diagnostics.isEmpty()&&usageComplete;pruneBlocked=!complete;this.fingerprint=fingerprint;this.policy=policy;
        }
    }
    private static final class Builder {
        final List<ProtectionStore.StoredRecord> records=new ArrayList<>();
        final List<ProtectionStore.Snapshot> snapshots=new ArrayList<>();
        final List<ProtectionStore.Transaction> transactions=new ArrayList<>();
        final List<HistoryCatalog.Checkpoint> checkpoints=new ArrayList<>();
        final List<HistoryIdentity.Descriptor> identities=new ArrayList<>();
        final List<Ownership> ownerships=new ArrayList<>();
        final List<String> diagnostics=new ArrayList<>();
        final Set<String> pins=new TreeSet<>(),orphans=new TreeSet<>(),unresolved=new TreeSet<>(),unindexed=new TreeSet<>(),protectedSources=new TreeSet<>();
        long actual,reserved,slots,charged;boolean usageComplete=true;
    }
    private final ProtectionStore store;
    private final HistoryCatalog catalog;
    private final HistoryIdentity identity;
    private final HistoryIndex index;
    private final HistoryPolicy policy;
    private final long metadataBudget;
    private final Object monitor;
    public HistoryGraph(ProtectionStore store,HistoryCatalog catalog,HistoryIdentity identity,HistoryIndex index,HistoryPolicy policy)throws IOException {
        this(store,catalog,identity,index,policy,HistoryStorage.METADATA_BUDGET);
    }
    HistoryGraph(ProtectionStore store,HistoryCatalog catalog,HistoryIdentity identity,HistoryIndex index,HistoryPolicy policy,long metadataBudget)throws IOException {
        if(store==null||catalog==null||identity==null||index==null||catalog.sourceStore()!=store||!index.uses(store,catalog,identity))throw new IOException("Inconsistent history role bundle");
        HistoryStorage.siblings(store.storageRoot(),catalog.storageRoot(),identity.storageRoot(),index.storageRoot());
        new HistoryStorage.Budget(metadataBudget);
        this.store=store;this.catalog=catalog;this.identity=identity;this.index=index;this.policy=policy;this.metadataBudget=metadataBudget;
        monitor=HistoryStorage.monitorFor(store.storageRoot());
    }
    public Graph scan()throws IOException {
        synchronized(monitor){
            HistoryStorage.siblings(store.storageRoot(),catalog.storageRoot(),identity.storageRoot(),index.storageRoot());
            HistoryStorage.Budget budget=new HistoryStorage.Budget(metadataBudget);
            return store.withValidatedSources(budget,sources->{
                Builder builder=new Builder();ProtectionStore.Inventory inventory=sources.inventory;
                builder.records.addAll(inventory.records);builder.snapshots.addAll(inventory.snapshots);builder.transactions.addAll(inventory.transactions);
                builder.diagnostics.addAll(inventory.diagnostics);builder.actual=inventory.actualBytes;builder.reserved=inventory.reservedCompletionBytes;
                builder.slots=inventory.reservedCompletionSlots;builder.usageComplete=inventory.usageComplete;
                HistoryPolicy effectivePolicy=policy;
                try {
                    HistoryStorage.Configuration configured=HistoryStorage.configuredPolicy(store.storageRoot());
                    if(configured!=null){
                        effectivePolicy=new HistoryPolicy(configured.quota,configured.automaticCount);
                        String[] actualRoots={store.storageRoot().getName(),catalog.storageRoot().getName(),identity.storageRoot().getName(),index.storageRoot().getName()};
                        if(!Arrays.equals(actualRoots,configured.names))throw new IOException("Graph differs from registered history bundle");
                    }
                    for(ProtectionStore.StoredRecord record:HistoryStorage.policyRecords(store.storageRoot())){
                        budget.charge(1024L+record.filename.length()*2L);builder.records.add(record);builder.actual=Math.addExact(builder.actual,record.serializedBytes);
                    }
                    if(HistoryStorage.policyBlocked(store.storageRoot()))builder.diagnostics.add("policy: pending publication or recovery");
                }catch(IOException|ArithmeticException invalid){builder.diagnostics.add("policy: invalid capacity proof");builder.usageComplete=false;}
                boolean metadataValid=true;
                for(String role:Arrays.asList("catalog","identity","index")){
                    File root="catalog".equals(role)?catalog.storageRoot():"identity".equals(role)?identity.storageRoot():index.storageRoot();
                    HistoryStorage.FilesView view=HistoryStorage.inspect(root,role,HistoryMetadataIO.MAX_RECORD,HistoryMetadataIO.MAX_ENTRIES,budget);
                    builder.records.addAll(view.records);builder.diagnostics.addAll(view.diagnostics);builder.usageComplete&=view.usageComplete;
                    try{builder.actual=Math.addExact(builder.actual,view.bytes);
                        // Covers decoded strings, maps, reference lists, immutable copies and fingerprint buffers.
                        budget.charge(Math.addExact(Math.multiplyExact(view.bytes,8),view.records.size()*2048L));
                    }catch(IOException|ArithmeticException exceeded){builder.diagnostics.add(role+": metadata budget or accounting limit");metadataValid=false;}
                    metadataValid&=view.diagnostics.isEmpty()&&view.usageComplete;
                }
                if(metadataValid&&inventory.complete){
                    try{builder.checkpoints.addAll(catalog.allAgainstValidatedSnapshots(sources));
                        builder.identities.addAll(identity.descriptors());
                        for(HistoryIndex.Ownership record:index.ownershipsAgainst(sources))builder.ownerships.add(new Ownership(record));
                    }catch(IOException invalid){builder.diagnostics.add("graph: invalid metadata links");}
                }else builder.diagnostics.add("graph: incomplete metadata proof");
                Set<String> grouped=new HashSet<>(),indexed=new HashSet<>();
                for(Ownership ownership:builder.ownerships)indexed.add(ownership.transactionId);
                for(ProtectionStore.Transaction transaction:builder.transactions){
                    grouped.add(transaction.original.id);grouped.add(transaction.attempted.id);
                    if(!transaction.verified)builder.unresolved.add(transaction.id);else if(!indexed.contains(transaction.id))builder.unindexed.add(transaction.id);
                }
                for(ProtectionStore.Snapshot snapshot:builder.snapshots){builder.protectedSources.add(snapshot.id);if(!grouped.contains(snapshot.id))builder.orphans.add(snapshot.id);}
                for(HistoryCatalog.Checkpoint checkpoint:builder.checkpoints)builder.pins.add(checkpoint.snapshotId);
                try{builder.charged=Math.addExact(builder.actual,builder.reserved);}catch(ArithmeticException overflow){builder.diagnostics.add("graph: accounting overflow");builder.usageComplete=false;builder.charged=Long.MAX_VALUE;}
                builder.records.sort(Comparator.comparing((ProtectionStore.StoredRecord record)->record.rootRole).thenComparing(record->record.filename));
                Collections.sort(builder.diagnostics);
                return new Graph(builder,fingerprint(builder,effectivePolicy),effectivePolicy);
            });
        }
    }
    private String fingerprint(Builder builder,HistoryPolicy effectivePolicy)throws IOException {
        MessageDigest digest=HistoryStorage.digest();
        try(DataOutputStream out=new DataOutputStream(new java.security.DigestOutputStream(new OutputStream(){@Override public void write(int value){} @Override public void write(byte[] bytes,int offset,int length){}},digest))){
            text(out,"read-only-history-graph-v1");
            for(File root:Arrays.asList(store.storageRoot(),catalog.storageRoot(),identity.storageRoot(),index.storageRoot()))text(out,root.getName());
            out.writeLong(store.constructorQuotaBytes());out.writeBoolean(effectivePolicy!=null);
            if(effectivePolicy!=null){out.writeLong(effectivePolicy.quotaBytes);out.writeInt(effectivePolicy.automaticCount);}
            out.writeLong(builder.actual);out.writeLong(builder.reserved);out.writeLong(builder.slots);out.writeBoolean(builder.usageComplete);
            out.writeInt(builder.records.size());
            for(ProtectionStore.StoredRecord record:builder.records){text(out,record.rootRole);text(out,record.filename);text(out,record.id);text(out,record.kind);out.writeLong(record.serializedBytes);text(out,record.envelopeHash);}
            // The exact envelope hashes bind all source fields, checkpoint labels, FORK edges and persisted ownership sequences.
            for(ProtectionStore.Transaction transaction:builder.transactions){text(out,transaction.id);text(out,transaction.original.id);text(out,transaction.attempted.id);out.writeBoolean(transaction.verified);}
            for(String diagnostic:builder.diagnostics)text(out,diagnostic);
        }
        return HistoryStorage.hex(digest.digest());
    }
    private static void text(DataOutputStream out,String value)throws IOException {if(value==null){out.writeInt(-1);return;}byte[] bytes=value.getBytes(StandardCharsets.UTF_8);out.writeInt(bytes.length);out.write(bytes);}
    private static <T> List<T> freeze(Collection<T> values){return Collections.unmodifiableList(new ArrayList<>(values));}
    private static <T> Set<T> freezeSet(Collection<T> values){return Collections.unmodifiableSet(new LinkedHashSet<>(values));}
}
