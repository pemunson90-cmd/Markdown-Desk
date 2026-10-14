package com.example.markdowndesk;

import java.nio.file.*;

/** Registered native graphs and retained intent files; no executor is invoked. */
public final class MaintenanceRecoveryCheck {
    static int checks;
    static void check(boolean value,String reason){checks++;if(!value)throw new AssertionError(reason);}
    static void discovery()throws Exception {
        IntentCodecCheck.Fixture fixture=new IntentCodecCheck.Fixture();
        fixture.group("content://maintenance",true,true);fixture.group("content://maintenance",true,true);
        HistoryCapacity capacity=HistoryCapacity.register(fixture.store,fixture.catalog,fixture.identity,fixture.index,
                new HistoryPolicy(64L*1024*1024,20),fixture.disk);
        HistoryGraph.Graph graph=fixture.graph(20).scan();
        HistoryPruner.DeletePlan plan=fixture.pruner(20).planAutomaticPressure(1,1).plan;
        check(plan!=null,"bounded registered native preview");
        HistoryCapacity.EffectivePolicy effective=capacity.policy();
        HistoryPruneIntent intent=HistoryPruneIntent.create(plan,graph,
                new HistoryPruneIntent.PolicyBinding(effective.bundleId,effective.generation,effective.controlRecordHash,effective.quotaBytes,effective.automaticCount),
                new HistoryPruneIntent.Authorization(plan.proofFingerprint,plan.selectedCheckpointIds),IntentCodecCheck.HASH);
        Path path=fixture.parent.resolve("history_control").resolve(HistoryPruneIntent.INTENT_NAME);
        byte[] bytes=HistoryPruneIntent.encode(intent);Files.write(path,bytes);
        HistoryControlStore.Inspection control=new HistoryControlStore(fixture.parent.toFile()).inspect();
        check(control.chainComplete,"committed valid intent must preserve independently validated policy chain");
        check(control.state==HistoryControlStore.State.BLOCKED,"active intent enabled ordinary mutation authority");
        check(control.usageComplete&&control.actualEntries==3,"intent omitted from enrollment/shared control allowance");
        IntentCodecCheck.rejects(capacity::reconcile);
        check(java.util.Arrays.equals(bytes,Files.readAllBytes(path)),"ordinary policy reconcile changed retained intent");
    }
    public static void main(String[] args)throws Exception {
        discovery();System.out.println("Maintenance recovery discovery: "+checks+" checks PASS");
    }
}
