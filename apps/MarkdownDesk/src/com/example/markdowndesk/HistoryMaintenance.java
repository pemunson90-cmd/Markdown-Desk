package com.example.markdowndesk;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Explicit, bounded maintenance registration and read-only inventory. No payload or deletion API. */
public final class HistoryMaintenance {
    public static final long MAX_BYTES=HistoryStorage.MAINTENANCE_BYTES, RESERVED_PRUNE_BYTES=HistoryStorage.PRUNE_RESERVE_BYTES;
    public static final int MAX_ENTRIES=HistoryStorage.MAINTENANCE_ENTRIES, RESERVED_PRUNE_SLOTS=HistoryStorage.PRUNE_RESERVE_SLOTS;
    private HistoryMaintenance(){}
    public static void enroll(ProtectionStore store,File root)throws IOException {
        synchronized(HistoryStorage.monitorFor(store.storageRoot())){HistoryStorage.enrollMaintenance(store,root);}
    }
    public static final class Inventory {
        public final boolean enrolled,usageComplete,pruneBlocked;
        public final long actualBytes,occupiedEntries,reservedPruneBytes,reservedPruneSlots,availableOperationBytes,availableOperationSlots;
        public final List<ProtectionStore.StoredRecord> records;
        public final List<String> diagnostics;
        private Inventory(HistoryStorage.Maintenance view){
            enrolled=view.enrolled;usageComplete=view.usageComplete;pruneBlocked=!enrolled||view.blocked();actualBytes=view.bytes;occupiedEntries=view.entries;
            reservedPruneBytes=enrolled?RESERVED_PRUNE_BYTES:0;reservedPruneSlots=enrolled?RESERVED_PRUNE_SLOTS:0;
            availableOperationBytes=enrolled&&usageComplete?Math.max(0,MAX_BYTES-actualBytes-reservedPruneBytes):0;
            availableOperationSlots=enrolled&&usageComplete?Math.max(0,MAX_ENTRIES-occupiedEntries-reservedPruneSlots):0;
            records=Collections.unmodifiableList(new ArrayList<>(view.records));diagnostics=Collections.unmodifiableList(new ArrayList<>(view.diagnostics));
        }
    }
    public static Inventory inspect(ProtectionStore store)throws IOException {
        synchronized(HistoryStorage.monitorFor(store.storageRoot())){return new Inventory(HistoryStorage.maintenance(store.storageRoot()));}
    }
}
