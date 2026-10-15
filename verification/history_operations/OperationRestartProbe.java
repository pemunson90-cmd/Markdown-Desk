package com.example.markdowndesk;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import static com.example.markdowndesk.HistoryPinRecord.*;

/** Synthetic retained stages; all completion goes through production recovery in fresh JVMs. */
public final class OperationRestartProbe {
    static void boundary(String actual,String wanted)throws IOException {
        if(actual.equals(wanted)){System.out.println("BOUNDARY "+actual);System.out.flush();while(System.in.read()!=-1){}throw new IOException("Kill parent disconnected");}
    }
    public static void main(String[] args)throws Exception {
        String mode=args[0];Path parent=Paths.get(args[1]);Files.createDirectories(parent);HistoryPinsCheck.Disk disk=new HistoryPinsCheck.Disk();
        ProtectionStore store=new ProtectionStore(parent.resolve("store").toFile(),disk,64L*1024*1024);
        HistoryCatalog catalog=new HistoryCatalog(parent.resolve("catalog").toFile(),store,disk);HistoryIdentity identity=new HistoryIdentity(parent.resolve("identity").toFile(),disk);HistoryIndex index=new HistoryIndex(store,catalog,identity,parent.resolve("index").toFile(),disk);
        if(mode.equals("init")){
            ProtectionStore.Snapshot source=store.putSnapshot("tab","uri","title",1,new byte[]{1,2,3},"original"),attempt=store.putSnapshot("tab","uri","title",1,new byte[]{4,5},"attempted");store.prepare(source,attempt,source.hash);
            HistoryCapacity.register(store,catalog,identity,index,new HistoryPolicy(64L*1024*1024,20),disk);HistoryPins pins=HistoryPins.open(store,catalog,identity,index,disk);
            String id=pins.begin(Purpose.EXPORT,Scope.EXACT_SUBSET,Collections.singleton(source.id),HistoryPins.ManifestIdentity.read(Domain.NATIVE_HISTORY_V1,1,OperationTerminalCheck.input()));
            HistoryPinRecord begin=new HistoryControlStore(parent.toFile()).inspect().pins.active;byte[] full=HistoryPinRecord.close(begin,Outcome.valueOf(args[3]));Files.write(parent.resolve("history_control/pin-"+id+".close.stage"),Arrays.copyOf(full,Integer.parseInt(args[2])));
            Files.write(parent.resolve("original-manifest"),OperationTerminalCheck.MANIFEST);System.out.println("INIT "+id);return;
        }
        HistoryControlStore.Inspection control=new HistoryControlStore(parent.toFile()).inspect();
        if(control.pins.stages.isEmpty()){
            HistoryPins.open(store,catalog,identity,index,disk);if(HistoryPins.pruneBlocked(store.storageRoot(),new HistoryControlStore(parent.toFile()).inspect()))throw new AssertionError("closed audit still blocked");
            if(HistoryStorage.capacity(store.storageRoot())!=null)throw new AssertionError("audit enabled ordinary authority");System.out.println("CLOSED");return;
        }
        HistoryPins pins=HistoryPins.forRecovery(store,catalog,identity,index,disk);HistoryOperations ops=HistoryOperations.forBundle(store,catalog,identity,index);
        HistoryOperations.Quiescence q=ops.quiesce(ops.freezeForTerminal(control.pins.active.operation.toString())).toCompletableFuture().getNow(null);
        if(q==null)throw new AssertionError("fresh process accepted participants before discovery");
        HistoryControlStore.Entry stage=control.pins.stages.get(0);HistoryPins.RecoveryChoice choice=stage.length<=93?HistoryPins.RecoveryChoice.CANCEL_UNBOUND:HistoryPins.RecoveryChoice.RECOVER_BOUND;
        String target=args.length>2?args[2]:"none";boolean[] moved={false};disk.hook=(event,file)->{
            if(event.equals("move-after"))moved[0]=true;
            if(event.startsWith("move-"))boundary(event,target);
            if(event.startsWith("file-"))boundary("read-only-"+event,target);
            if(moved[0]&&event.startsWith("dir-"))boundary("close-"+event,target);
        };
        InputStream manifest=Files.exists(parent.resolve("original-manifest"))?Files.newInputStream(parent.resolve("original-manifest")):null;
        try{
            pins.recoverClose(q,choice,manifest,event->boundary(event.name(),target));
            if(mode.equals("limit"))throw new AssertionError("OS file limit did not interrupt suffix");
            if(HistoryStorage.capacity(store.storageRoot())!=null)throw new AssertionError("recovery enabled ordinary capacity");System.out.println("CLOSED");
        }catch(IOException failure){if(mode.equals("limit")||mode.equals("blocked")){System.out.println("BLOCKED");return;}throw failure;}
        finally{if(manifest!=null)manifest.close();}
    }
}
