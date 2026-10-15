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
    static void finalBoundaries()throws Exception {
        for(HistoryPinTerminalRecovery.Event event:HistoryPinTerminalRecovery.Event.values())for(boolean replaceStage:new boolean[]{false,true}){
            Fixture f=new Fixture();HistoryPins pins=pins(f);String id=begin(f,pins);Path stage=f.parent.resolve("history_control/pin-"+id+".close.stage");byte[] expected=HistoryPinRecord.close(inspect(f).pins.active,Outcome.CANCELLED);Files.write(stage,Arrays.copyOf(expected,60));HistoryOperations.Quiescence q=quiescence(f,id);
            rejects(()->pins.recoverClose(q,HistoryPins.RecoveryChoice.CANCEL_UNBOUND,null,at->{if(at!=event)return;Path target=replaceStage?stage:f.parent.resolve("store/"+f.original.id+".snapshot");byte[] bytes=Files.readAllBytes(target);Path replacement=target.resolveSibling("replacement");Files.write(replacement,bytes);Files.move(replacement,target,StandardCopyOption.REPLACE_EXISTING);}),"final append/force identity barrier "+event);
            check(!Files.exists(stage.resolveSibling("pin-"+id+".close")),"replacement never promoted");
            check(Files.size(stage)==(event==HistoryPinTerminalRecovery.Event.BEFORE_APPEND?60:126),"only authorized suffix may have reached stage");check(HistoryPins.pruneBlocked(f.store.storageRoot(),inspect(f)),"failed final boundary never acknowledged");
        }
        Fixture finalSync=new Fixture();HistoryPins pins=pins(finalSync);String id=begin(finalSync,pins);Path stage=finalSync.parent.resolve("history_control/pin-"+id+".close.stage");Files.write(stage,Arrays.copyOf(HistoryPinRecord.close(inspect(finalSync).pins.active,Outcome.CANCELLED),125));HistoryOperations.Quiescence q=quiescence(finalSync,id);
        finalSync.disk.hook=(event,file)->{if(event.equals("dir-after")&&!Files.exists(stage)){Path source=finalSync.parent.resolve("store/"+finalSync.original.id+".snapshot");byte[] bytes=Files.readAllBytes(source);bytes[bytes.length-1]^=1;Files.write(source,bytes);}};
        rejects(()->pins.recoverClose(q,HistoryPins.RecoveryChoice.RECOVER_BOUND,null),"source corruption after final directory sync");finalSync.disk.hook=null;check(HistoryPins.pruneBlocked(finalSync.store.storageRoot(),inspect(finalSync)),"post-rename failure retains protection");
        // Once committed, historical audit reconciliation does not claim source integrity.
        pins.reconcile();check(!HistoryPins.pruneBlocked(finalSync.store.storageRoot(),inspect(finalSync)),"committed historical receipt reconciliation is distinct from new closure");
    }
    static void invalidAndCredit()throws Exception {
        for(String kind:Arrays.asList("oversized","nonprefix","nonregular","conflict","wrong-choice")){
            Fixture f=new Fixture();HistoryPins pins=pins(f);String id=begin(f,pins);Path stage=f.parent.resolve("history_control/pin-"+id+".close.stage");byte[] full=HistoryPinRecord.close(inspect(f).pins.active,Outcome.RELEASED),bytes=Arrays.copyOf(full,kind.equals("oversized")?127:94);
            if(kind.equals("nonprefix"))bytes[0]^=1;if(kind.equals("nonregular"))Files.createDirectory(stage);else Files.write(stage,bytes);
            if(kind.equals("conflict"))Files.write(f.parent.resolve("history_control/pin-"+id+".close"),full);
            Object inode=Files.readAttributes(stage,BasicFileAttributes.class).fileKey();
            rejects(()->pins.recoverClose(quiescence(f,id),HistoryPins.RecoveryChoice.CANCEL_UNBOUND,input()),"invalid retained evidence "+kind);
            check(Objects.equals(inode,Files.readAttributes(stage,BasicFileAttributes.class).fileKey()),"invalid inode retained");if(!kind.equals("nonregular"))check(Arrays.equals(bytes,Files.readAllBytes(stage)),"invalid bytes retained");
        }
        for(boolean byteBound:new boolean[]{false,true}){
            Fixture f=new Fixture();HistoryPins pins=pins(f);String id=begin(f,pins);HistoryControlStore.Inspection initial=inspect(f);
            if(byteBound){int pairs=70,total=(int)((HistoryControlStore.MAX_BYTES-initial.actualBytes-126-pairs*330L)/58);HistoryPinRecord sample=initial.pins.active;
                for(int i=0;i<pairs;i++){int count=Math.min(256,total-(pairs-i-1));total-=count;List<Source> sources=new ArrayList<>();for(int j=0;j<count;j++)sources.add(new Source(new UUID(0,j).toString(),45,sample.sources.get(0).hash));
                    HistoryPinRecord audit=HistoryPinRecord.begin(UUID.randomUUID(),initial.registration,initial.latest,Purpose.EXPORT,Scope.EXACT_SUBSET,sample.manifest,sources);Files.write(f.parent.resolve("history_control/"+audit.name()),HistoryPinRecord.encode(audit));Files.write(f.parent.resolve("history_control/pin-"+audit.operation+".close"),HistoryPinRecord.close(audit,Outcome.RELEASED));}
                HistoryControlStore.Inspection full=inspect(f);byte[] policy=HistoryControlStore.encodePolicy(full.registration.bundleId,full.latest.generation+1,full.latest.envelopeHash,128L*1024*1024,5);check(full.actualBytes+policy.length<=HistoryControlStore.MAX_BYTES&&full.actualBytes+126+policy.length>HistoryControlStore.MAX_BYTES,"policy would steal exact terminal byte credit");
            }else while(inspect(f).actualEntries<255){HistoryControlStore.Inspection c=inspect(f);Files.write(f.parent.resolve("history_control/"+HistoryControlStore.policyFilename(c.latest.generation+1)),HistoryControlStore.encodePolicy(c.registration.bundleId,c.latest.generation+1,c.latest.envelopeHash,64L*1024*1024,20));}
            rejects(()->f.capacity.setPolicy(new HistoryPolicy(128L*1024*1024,5)),"control writer cannot spend terminal credit");HistoryPinRecord active=inspect(f).pins.active;Path stage=f.parent.resolve("history_control/pin-"+id+".close.stage");Files.write(stage,Arrays.copyOf(HistoryPinRecord.close(active,Outcome.CANCELLED),80));HistoryControlStore.Inspection before=inspect(f);
            pins.recoverClose(quiescence(f,id),HistoryPins.RecoveryChoice.CANCEL_UNBOUND,null);HistoryControlStore.Inspection after=inspect(f);
            check(after.actualBytes==before.actualBytes+46&&after.actualEntries==before.actualEntries,"full allowance conversion creates no entry or extra bytes");check(after.actualBytes<=HistoryControlStore.MAX_BYTES&&after.actualEntries<=256,"same shared allowance retained");
            check(!HistoryPins.pruneBlocked(f.store.storageRoot(),after),"historical closed sources need no dereference during recovery");
        }
    }
    public static void main(String[] args)throws Exception{prefixes();failures();finalBoundaries();invalidAndCredit();System.out.println("Close prefix: "+checks+" checks PASS");}
}
