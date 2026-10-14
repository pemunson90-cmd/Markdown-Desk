package com.example.markdowndesk;
import java.nio.file.*;
import java.io.*;
import java.util.*;
import static com.example.markdowndesk.HistoryPinRecord.*;
/** Closed pin audit plus synthetic interrupted maintenance, without a production executor. */
public final class PinMaintenanceProbe {
    public static void main(String[] args)throws Exception {
        Path parent=Paths.get(args[1]);Files.createDirectories(parent);MaintenanceRecoveryCheck.Fixture f=new MaintenanceRecoveryCheck.Fixture(parent);
        if(args[0].equals("init")){
            f.prepare(false);HistoryPins pins=HistoryPins.open(f.store,f.catalog,f.identity,f.index,f.disk);
            String id=pins.begin(Purpose.EXPORT,Scope.EXACT_SUBSET,Collections.singleton(f.old.original.id),PinLifecycleCheck.manifest());pins.close(id,Outcome.CANCELLED,null);
            f.before=f.graph().scan();f.plan=new HistoryPruner(f.graph()).planAutomaticPressure(1,1).plan;
            HistoryCapacity.EffectivePolicy policy=f.capacity.policy();f.intent=HistoryPruneIntent.create(f.plan,f.before,new HistoryPruneIntent.PolicyBinding(policy.bundleId,policy.generation,policy.controlRecordHash,policy.quotaBytes,policy.automaticCount),IntentCodecCheck.auth(f.plan),HistoryMaintenanceRecovery.unaffectedFingerprint(f.before,f.plan.members));
            f.publish();f.remove(null);System.out.println("INIT");return;
        }
        try{f.open();throw new AssertionError("unacknowledged closed audit bypassed");}catch(IOException expected){}
        HistoryPins pins=HistoryPins.open(f.store,f.catalog,f.identity,f.index,f.disk);
        if(HistoryStorage.capacity(f.store.storageRoot())!=null)throw new AssertionError("audit reconciliation enabled writes");
        try{pins.begin(Purpose.EXPORT,Scope.ALL_BUNDLE,Collections.emptySet(),PinLifecycleCheck.manifest());throw new AssertionError("maintenance allowed new pin");}catch(IOException expected){}
        if(f.open().graph.snapshots.size()!=2)throw new AssertionError("closed audit prevented survivor recovery");
        System.out.println("CLOSED-MAINTENANCE");
    }
}
