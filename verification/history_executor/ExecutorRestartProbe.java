package com.example.markdowndesk;
import java.io.*;
import java.nio.file.*;
import java.util.*;
/** Each invocation is a fresh JVM; the Python parent supplies actual SIGKILL. */
public final class ExecutorRestartProbe {
    static void pause(String actual,String wanted)throws IOException {
        if(actual.equals(wanted)){System.out.println("BOUNDARY "+actual);System.out.flush();while(System.in.read()!=-1){}throw new IOException("Kill parent disconnected");}
    }
    public static void main(String[] args)throws Exception {
        String mode=args[0];Path root=Path.of(args[1]);ExecutorCheck.Bundle f=new ExecutorCheck.Bundle(root);
        if(mode.equals("init")){
            f.prepare(3,true);HistoryPins pins=HistoryPins.open(f.store,f.catalog,f.identity,f.index,f.disk);
            String op=pins.begin(HistoryPinRecord.Purpose.EXPORT,HistoryPinRecord.Scope.EXACT_SUBSET,Collections.singleton(f.old.original.id),HistoryPins.ManifestIdentity.read(HistoryPinRecord.Domain.NATIVE_HISTORY_V1,0,new ByteArrayInputStream(new byte[0])));
            pins.close(op,HistoryPinRecord.Outcome.CANCELLED,null);f.preview();Files.write(root.resolveSibling("selection"),f.selection);
            for(HistoryPruneIntent.Member member:f.intent.members)System.out.println("REMOVE "+member.key());
            System.out.println("RESERVE "+f.capacity.inspect().completionReservedBytes);return;
        }
        if(mode.equals("blocked")){Map<String,String> before=f.capture();ExecutorCheck.rejects(()->f.executor().replay(),"invalid retained proof blocks fresh replay");ExecutorCheck.check(before.equals(f.capture()),"rejected fresh replay leaves every byte");System.out.println("BLOCKED");return;}
        if(mode.equals("reopen")){
            Map<String,String> before=f.capture();
            if(Files.exists(root.resolve("history_control/prune.intent.stage"))){ExecutorCheck.rejects(()->f.executor().replay(),"stage blocks fresh replay");ExecutorCheck.check(before.equals(f.capture()),"blocked fresh replay preserves all bytes");System.out.println("BLOCKED");return;}
            f.capacity=f.executor().replay();HistoryPins.open(f.store,f.catalog,f.identity,f.index,f.disk);
            ExecutorCheck.check(f.store.snapshots().size()==4&&f.store.transactions().size()==2,"fresh survivor inventory");
            ExecutorCheck.check(f.capacity.inspect().completionReservedSlots==1&&!f.capacity.inspect().mutationBlocked,"fresh surviving reservation and authority");
            System.out.println("READY "+f.capacity.inspect().completionReservedBytes);return;
        }
        f.capacity=HistoryCapacity.open(f.store,f.catalog,f.identity,f.index,f.disk);HistoryPins.open(f.store,f.catalog,f.identity,f.index,f.disk);
        f.selection.addAll(Files.readAllLines(root.resolveSibling("selection")));f.preview();String target=args[2];
        String[] member={null};boolean[] memberSync={false},retired={false};
        f.disk.hook=(event,file)->{
            Path stage=root.resolve("history_control/prune.intent.stage"),intent=root.resolve("history_control/prune.intent");
            if(event.startsWith("file-")&&Files.exists(stage))pause("intent-"+event,target);
            if(event.startsWith("move-"))pause("intent-"+event,target);
            if(event.startsWith("dir-")){
                if(memberSync[0]){pause("member-"+event+"-"+member[0],target);if(event.equals("dir-after"))memberSync[0]=false;}
                else if(retired[0]&&file.getName().equals("history_control"))pause("retire-"+event,target);
                else if(member[0]!=null){boolean absent=true;for(HistoryPruneIntent.Member value:f.intent.members)absent&=!Files.exists(f.file(value));if(absent&&!file.getName().equals("history_control"))pause("retire-data-"+event+"-"+file.getName(),target);}
                else if(Files.exists(intent)&&file.getName().equals("history_control"))pause("intent-"+event,target);
            }
        };
        HistoryMaintenanceExecutor executor=f.executor((event,key)->{
            if(event==HistoryMaintenanceExecutor.Event.BEFORE_MEMBER_UNLINK){member[0]=key.substring(key.lastIndexOf('.')+1);pause("member-before-"+member[0],target);}
            if(event==HistoryMaintenanceExecutor.Event.AFTER_MEMBER_UNLINK){pause("member-after-"+member[0],target);memberSync[0]=true;}
            if(event==HistoryMaintenanceExecutor.Event.BEFORE_INTENT_UNLINK)pause("intent-unlink-before",target);
            if(event==HistoryMaintenanceExecutor.Event.AFTER_INTENT_UNLINK){retired[0]=true;pause("intent-unlink-after",target);}
        });
        executor.execute(f.capacity,f.plan,ExecutorCheck.auth(f.plan));throw new AssertionError("Boundary not reached: "+target);
    }
}
