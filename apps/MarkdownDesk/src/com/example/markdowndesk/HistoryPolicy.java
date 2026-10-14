package com.example.markdowndesk;

import java.io.IOException;

/** Selectable shared policy; opt-in registration leaves legacy constructor-only stores unchanged. */
public final class HistoryPolicy {
    public final long quotaBytes;
    public final int automaticCount;
    public HistoryPolicy(long quotaBytes,int automaticCount) {
        try{HistoryStorage.validatePolicy(quotaBytes,automaticCount);}catch(IOException invalid){throw new IllegalArgumentException(invalid.getMessage(),invalid);}
        this.quotaBytes=quotaBytes;this.automaticCount=automaticCount;
    }
    /** Registers the entire sibling bundle durably; reopening cannot silently replace its policy. */
    public void configure(ProtectionStore store,HistoryCatalog catalog,HistoryIdentity identity,HistoryIndex index)throws IOException {
        if(store==null||catalog==null||identity==null||index==null||catalog.sourceStore()!=store||!index.uses(store,catalog,identity))throw new IOException("Inconsistent history bundle");
        synchronized(HistoryStorage.monitorFor(store.storageRoot())){HistoryStorage.configure(store,catalog.storageRoot(),identity.storageRoot(),index.storageRoot(),quotaBytes,automaticCount);}
    }
    public void apply(ProtectionStore store)throws IOException {
        synchronized(HistoryStorage.monitorFor(store.storageRoot())){HistoryStorage.setPolicy(store.storageRoot(),quotaBytes,automaticCount,store.durability());}
    }
    public static HistoryPolicy current(ProtectionStore store)throws IOException {
        synchronized(HistoryStorage.monitorFor(store.storageRoot())){HistoryStorage.Configuration policy=HistoryStorage.currentPolicy(store.storageRoot());return new HistoryPolicy(policy.quota,policy.automaticCount);}
    }
    public static final class Capacity {
        public final HistoryPolicy policy;
        public final long actualBytes,reservedCompletionBytes,reservedCompletionSlots,chargedBytes;
        public final boolean overQuota,mutationBlocked;
        private Capacity(HistoryStorage.Capacity capacity)throws IOException {
            policy=new HistoryPolicy(capacity.configuration.quota,capacity.configuration.automaticCount);actualBytes=capacity.actualBytes;
            reservedCompletionBytes=capacity.reservedBytes;reservedCompletionSlots=capacity.reservedSlots;chargedBytes=capacity.chargedBytes();
            overQuota=chargedBytes>policy.quotaBytes;mutationBlocked=capacity.blocked;
        }
    }
    public static Capacity inspect(ProtectionStore store)throws IOException {
        synchronized(HistoryStorage.monitorFor(store.storageRoot())){return new Capacity(HistoryStorage.capacity(store.storageRoot()));}
    }
}
