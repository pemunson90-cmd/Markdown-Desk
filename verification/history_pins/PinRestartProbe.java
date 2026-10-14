package com.example.markdowndesk;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import static com.example.markdowndesk.HistoryPinRecord.*;

/** Child killed externally at a real production durability callback. */
public final class PinRestartProbe {
    public static void main(String[] args)throws Exception {
        String mode=args[0];Path parent=Paths.get(args[1]);Files.createDirectories(parent);HistoryPinsCheck.Disk disk=new HistoryPinsCheck.Disk();
        ProtectionStore store=new ProtectionStore(parent.resolve("store").toFile(),disk,64L*1024*1024);
        HistoryCatalog catalog=new HistoryCatalog(parent.resolve("catalog").toFile(),store,disk);
        HistoryIdentity identity=new HistoryIdentity(parent.resolve("identity").toFile(),disk);
        HistoryIndex index=new HistoryIndex(store,catalog,identity,parent.resolve("index").toFile(),disk);
        if(mode.equals("init")){
            ProtectionStore.Snapshot original=store.putSnapshot("tab","uri","title",1,new byte[]{1,2},"original");
            ProtectionStore.Snapshot attempted=store.putSnapshot("tab","uri","title",1,new byte[]{3},"attempted");store.prepare(original,attempted,original.hash);
            HistoryCapacity.register(store,catalog,identity,index,new HistoryPolicy(64L*1024*1024,20),disk);
            Files.writeString(parent.resolve("source"),original.id);System.out.println("INIT");return;
        }
        HistoryControlStore controls=new HistoryControlStore(parent.toFile());
        if(mode.equals("reopen")){
            String expected=args[2];HistoryControlStore.Inspection before=controls.inspect();
            if(!HistoryPins.pruneBlocked(store.storageRoot(),before))throw new AssertionError("raw pin evidence bypassed explicit acknowledgement");
            if(expected.equals("blocked")){
                try{HistoryPins.open(store,catalog,identity,index,disk);throw new AssertionError("partial/unknown stage promoted");}catch(IOException blocked){System.out.println("BLOCKED");return;}
            }
            HistoryPins.open(store,catalog,identity,index,disk);HistoryControlStore.Inspection after=controls.inspect();
            if(HistoryStorage.capacity(store.storageRoot())!=null)throw new AssertionError("pin reconciliation enabled ordinary authority");
            try{store.putSnapshot("tab",null,null,1,new byte[]{5},"attempted");throw new AssertionError("pin open enabled writer");}catch(IOException expectedFailure){}
            boolean active=expected.equals("active");if(HistoryPins.pruneBlocked(store.storageRoot(),after)!=active||!after.pins.stages.isEmpty())throw new AssertionError("bad reconciled pin state");
            System.out.println(active?"ACTIVE":"CLOSED");return;
        }
        HistoryCapacity.open(store,catalog,identity,index,disk);HistoryPins pins=HistoryPins.open(store,catalog,identity,index,disk);
        boolean close=mode.equals("crash-close");String id=null;
        if(close)id=pins.begin(Purpose.RESTORE,Scope.EXACT_SUBSET,Collections.singleton(Files.readString(parent.resolve("source"))),PinLifecycleCheck.manifest());
        String boundary=args[2];disk.hook=(event,file)->{
            if(!event.equals(boundary.startsWith("partial-")||boundary.equals("unknown")?"file-before":boundary))return;
            if(boundary.startsWith("partial-")||boundary.equals("unknown")){
                try(DirectoryStream<Path> stream=Files.newDirectoryStream(parent.resolve("history_control"),"*.stage")){
                    Path stage=stream.iterator().next();if(boundary.equals("unknown"))Files.write(stage,new byte[]{7,8,9});
                    else try(RandomAccessFile truncated=new RandomAccessFile(stage.toFile(),"rw")){truncated.setLength(Integer.parseInt(boundary.substring(8)));}
                }
            }
            System.out.println("BOUNDARY "+boundary);System.out.flush();
            try{Thread.sleep(60000);}catch(InterruptedException interrupted){throw new IOException(interrupted);}throw new IOException("parent failed to kill child");
        };
        if(close)pins.close(id,Outcome.CANCELLED,null);
        else pins.begin(Purpose.RESTORE,Scope.EXACT_SUBSET,Collections.singleton(Files.readString(parent.resolve("source"))),PinLifecycleCheck.manifest());
        throw new AssertionError("boundary not reached");
    }
}
