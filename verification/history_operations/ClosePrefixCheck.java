package com.example.markdowndesk;
import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import static com.example.markdowndesk.HistoryPinsCheck.*;
import static com.example.markdowndesk.OperationTerminalCheck.*;
import static com.example.markdowndesk.HistoryPinRecord.*;

public final class ClosePrefixCheck {
    static HistoryOperations ops(Fixture f)throws IOException{return HistoryOperations.forBundle(f.store,f.catalog,f.identity,f.index);}
    static HistoryOperations.Quiescence quiescence(Fixture f,String id)throws Exception{HistoryOperations o=ops(f);return o.quiesce(o.freezeForTerminal(id)).toCompletableFuture().get();}
    static void prefixes()throws Exception {
        for(Outcome outcome:Outcome.values())for(int n=0;n<=126;n++){
            Fixture f=new Fixture();HistoryPins pins=pins(f);String id=begin(f,pins);HistoryPinRecord b=inspect(f).pins.active;
            byte[] full=HistoryPinRecord.close(b,outcome),prefix=Arrays.copyOf(full,n);Path stage=f.parent.resolve("history_control/pin-"+id+".close.stage");Files.write(stage,prefix);
            Object inode=Files.readAttributes(stage,BasicFileAttributes.class).fileKey();HistoryControlStore.Inspection before=inspect(f);
            check(before.pins.reservedBytes==126-n&&before.pins.reservedEntries==0,"prefix credit "+n);
            rejects(()->pins.reconcile(),"ordinary reconcile cannot release stage "+n);
            HistoryOperations.Quiescence q=quiescence(f,id);HistoryPins.RecoveryChoice choice=n<=93?HistoryPins.RecoveryChoice.CANCEL_UNBOUND:HistoryPins.RecoveryChoice.RECOVER_BOUND;
            if(n>=94&&outcome==Outcome.RELEASED){HistoryOperations.Quiescence missingManifest=q;rejects(()->pins.recoverClose(missingManifest,choice,null),"bound RELEASED requires original manifest");check(Arrays.equals(prefix,Files.readAllBytes(stage)),"missing manifest preserves prefix");q=quiescence(f,id);}
            pins.recoverClose(q,choice,n>=94&&outcome==Outcome.RELEASED?input():null);
            Path close=f.parent.resolve("history_control/pin-"+id+".close");Outcome finalOutcome=n<=93?Outcome.CANCELLED:outcome;
            check(Arrays.equals(HistoryPinRecord.close(b,finalOutcome),Files.readAllBytes(close)),"exact canonical suffix/outcome "+n);
            check(Objects.equals(inode,Files.readAttributes(close,BasicFileAttributes.class).fileKey()),"same inode append/promote "+n);
            HistoryControlStore.Inspection after=inspect(f);check(after.actualBytes==before.actualBytes+126-n&&after.actualEntries==before.actualEntries&&after.pins.reservedBytes==0,"reservation conservation "+n);
            pins.reconcile();check(!HistoryPins.pruneBlocked(f.store.storageRoot(),after),"durable closure acknowledged");
        }
    }
    static void failures()throws Exception {
        for(String mutation:Arrays.asList("source","stage","policy","root","unknown")){
            Fixture f=new Fixture();HistoryPins pins=pins(f);String id=begin(f,pins);byte[] bytes=HistoryPinRecord.close(inspect(f).pins.active,Outcome.CANCELLED);Path stage=f.parent.resolve("history_control/pin-"+id+".close.stage");Files.write(stage,Arrays.copyOf(bytes,70));
            HistoryOperations.Quiescence q=quiescence(f,id);boolean[] once={false};f.disk.hook=(event,file)->{if(once[0]||!event.equals("file-after"))return;once[0]=true;
                Path target=mutation.equals("source")?f.parent.resolve("store/"+f.original.id+".snapshot"):mutation.equals("stage")?stage:mutation.equals("policy")?f.parent.resolve("history_control/"+HistoryControlStore.REGISTRATION_NAME):null;
                if(target!=null){byte[] saved=Files.readAllBytes(target);Path other=target.resolveSibling("replace");Files.write(other,saved);Files.move(other,target,StandardCopyOption.REPLACE_EXISTING);}
                else if(mutation.equals("root")){Files.move(f.parent.resolve("index"),f.parent.resolve("index-old"));Files.createDirectory(f.parent.resolve("index"));}
                else Files.write(f.parent.resolve("history_control/foreign"),new byte[]{1});
            };
            rejects(()->pins.recoverClose(q,HistoryPins.RecoveryChoice.CANCEL_UNBOUND,null),"callback invalidation "+mutation);f.disk.hook=null;
            check(Files.size(stage)==70&&!Files.exists(stage.resolveSibling("pin-"+id+".close")),"invalid proof never appends/promotes");
        }
        for(int size:new int[]{0,1,60,125}){
            Fixture f=new Fixture();HistoryPins pins=pins(f);HistoryPinRecord b=record(f);Path stage=f.parent.resolve("history_control/"+b.name()+".stage");byte[] prefix=Arrays.copyOf(HistoryPinRecord.encode(b),size);Files.write(stage,prefix);
            rejects(()->ops(f).freezeForTerminal(b.operation.toString()),"partial BEGIN no invented provenance");check(Arrays.equals(prefix,Files.readAllBytes(stage)),"partial BEGIN retained");
        }
    }
    public static void main(String[] args)throws Exception{prefixes();failures();System.out.println("Close prefix: "+checks+" checks PASS");}
}
