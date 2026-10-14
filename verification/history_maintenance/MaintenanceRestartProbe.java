package com.example.markdowndesk;

import java.nio.file.*;
import java.util.*;

/** Fresh-JVM reader of synthetic interrupted-removal fixtures; no replay/unlink implementation. */
public final class MaintenanceRestartProbe {
    public static void main(String[] args)throws Exception {
        String mode=args[0];Path parent=Paths.get(args[1]);
        MaintenanceRecoveryCheck.Fixture f=new MaintenanceRecoveryCheck.Fixture(parent);
        if(mode.equals("seed")){
            f.prepare(true);f.publish();if(!args[2].equals("intact"))f.remove(args[2].equals("all")?null:args[2]);
            System.out.println("SEEDED");return;
        }
        Map<String,String> before=f.capture();
        HistoryControlStore.Inspection control=new HistoryControlStore(parent.toFile()).inspect();HistoryPruneIntent intent=control.committedIntent.intent;
        MaintenanceRecoveryCheck.rejects(()->HistoryCapacity.open(f.store,f.catalog,f.identity,f.index,f.disk));
        HistoryMaintenanceRecovery.RecoveryView recovery=f.open();HistoryGraph.Graph graph=f.graph().scan();
        MaintenanceRecoveryCheck.check(graph.complete&&graph.transactions.size()==1&&graph.snapshots.size()==2&&graph.records.size()==7,"fresh complete logical graph");
        MaintenanceRecoveryCheck.check(graph.reservedCompletionBytes==0&&graph.reservedCompletionSlots==0,"fresh no phantom completion");
        HistoryPruneIntent.Group anchor=intent.anchors.get(0);
        MaintenanceRecoveryCheck.check(f.store.transactions().get(0).id.equals(anchor.transactionId)&&f.index.list(anchor.historyId).size()==2&&f.catalog.all().size()==1,"fresh source/catalog/index projection");
        MaintenanceRecoveryCheck.check(Arrays.equals(f.store.snapshot(anchor.originalSnapshotId).bytes(),new byte[]{1,2,3})&&Arrays.equals(f.store.snapshot(anchor.attemptedSnapshotId).bytes(),new byte[]{4,5}),"fresh exact surviving payloads");
        MaintenanceRecoveryCheck.rejects(()->f.store.snapshot(intent.groups.get(0).originalSnapshotId));
        long bytes=0;int slots=0;for(String role:Arrays.asList("store","catalog","identity","index"))try(java.util.stream.Stream<Path> paths=Files.list(parent.resolve(role))){for(Path file:(Iterable<Path>)paths::iterator){bytes+=Files.size(file);slots++;}}
        MaintenanceRecoveryCheck.check(graph.actualBytes==bytes&&graph.occupiedEntries==slots&&recovery.graph.actualBytes==bytes,"fresh physical accounting");
        MaintenanceRecoveryCheck.check(before.equals(f.capture())&&f.disk.moves==0,"fresh recovery changed no disk bytes or names");
        System.out.println(intent.unaffectedFingerprint+" "+graph.actualBytes+" "+graph.occupiedEntries+" "+graph.transactions.get(0).id);
    }
}
