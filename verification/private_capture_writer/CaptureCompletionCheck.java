package com.example.markdowndesk;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import static com.example.markdowndesk.HistoryPinsCheck.*;

/** Actual BEGIN/LINK publication and exact process source handoff; no activation. */
public final class CaptureCompletionCheck {
    static int cases;
    interface Work { void run()throws Exception; }
    static void yes(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    static void reject(Work work,String why)throws Exception{try{work.run();throw new AssertionError("accepted "+why);}catch(IOException expected){}}
    static final class Capture {
        final PreparationPublicationCheck.Capture c=new PreparationPublicationCheck.Capture();
        final Disk disk=new Disk();final HistoryPins pins;
        final PrivateCaptureWriter writer;
        Capture()throws Exception {
            pins=HistoryPins.open(c.f.store,c.f.catalog,c.f.identity,c.f.index,c.f.disk);
            writer=c.publish(disk);locked(()->writer.writeNativeLocked());
        }
        void locked(Work work)throws Exception{synchronized(HistoryStorage.monitorFor(c.f.store.storageRoot())){try(HistoryStorage.MutationScope lease=HistoryStorage.administration(c.f.store.storageRoot())){work.run();}}}
    }
    static void normal()throws Exception {
        Capture c=new Capture();long[] observed={0};
        c.disk.hook=(event,file)->{if(event.equals("file-after")){HistoryControlStore.Inspection i=inspect(c.c.f);if(!i.pins.stages.isEmpty()){yes(i.pins.reservedBytes==126&&i.operations.reservedBytes==173&&i.controlReservedBytes==299,"complete BEGIN.stage transfers CLOSE exactly once");observed[0]++;}if(i.operations.links.containsKey(c.c.preparation)){yes(i.controlReservedBytes==126,"full LINK.stage exhausts LINK byte credit");observed[0]++;}}};
        PrivateCaptureWriter.CapturedBinding binding=c.writer.commitCapture(c.pins);
        yes(observed[0]==2,"observed both exact credit transfers");
        c.locked(()->{HistoryControlStore.Inspection i=inspect(c.c.f);yes(i.pins.active!=null&&i.pins.active.envelopeHash.equals(c.c.plan.prepare.beginHash)&&i.operations.links.get(c.c.preparation)!=null&&!i.operations.links.get(c.c.preparation).stage,"exact BEGIN and LINK committed");yes(i.controlReservedBytes==126&&i.controlReservedEntries==1,"only future CLOSE remains");yes(HistoryStorage.preparationSources(c.c.f.store.storageRoot())==null,"exact owner handed off");});
        reject(()->c.writer.commitCapture(c.pins),"one completion only");
        PreparationSources next=PreparationSources.forPreparation(c.c.f.store.storageRoot(),UUID.randomUUID(),new HistoryStorage.Budget(1<<20),PreparationSources.newProducer());
        c.locked(()->{reject(()->binding.handoffLocked(),"consumed binding cannot clear replacement owner");yes(HistoryStorage.preparationSources(c.c.f.store.storageRoot())==next,"replacement owner retained");});
        Process reopened=new ProcessBuilder(new File(System.getProperty("java.home"),"bin/java").getPath(),"-Xmx128m","-cp",System.getProperty("java.class.path"),CaptureCompletionCheck.class.getName(),"reopen",c.c.f.parent.toString(),c.c.preparation.toString(),c.c.plan.link.envelopeHash).inheritIO().start();
        yes(reopened.waitFor()==0,"fresh JVM independently rediscovers durable binding and credits");
        reject(()->c.pins.operations().activateAcknowledged(c.c.plan.prepare.operation.toString(),new ByteArrayInputStream(new byte[0])),"domain2 activation remains off");
        for(PrivateOperationRecord.Artifact a:c.c.plan.prepare.artifacts)Files.delete(c.c.f.parent.resolve("private_operations/"+c.c.preparation+"/"+a.filename()));
        Files.delete(c.c.f.parent.resolve("private_operations/"+c.c.preparation));Files.delete(c.c.f.parent.resolve("private_operations"));
        for(String name:Arrays.asList(c.c.plan.prepare.name(),c.c.plan.prepare.begin.name(),c.c.plan.link.name()))Files.delete(c.c.f.parent.resolve("history_control/"+name));
        c.locked(()->reject(()->PrivateOperationStore.inspect(c.c.f.store.storageRoot(),inspect(c.c.f),new HistoryStorage.Budget(HistoryStorage.METADATA_BUDGET)),"new retained anchors reject complete capture disappearance"));cases++;
    }
    static void failures()throws Exception {
        for(int point=0;point<3;point++){
            final int fault=point;
            Capture c=new Capture();boolean[] fired={false};c.disk.hook=(event,file)->{if(event.equals(fault==2?"dir-after":"file-after")){HistoryControlStore.Inspection i=inspect(c.c.f);HistoryControlStore.Entry link=i.operations.links.get(c.c.preparation);if(fault==0?!i.pins.stages.isEmpty():link!=null&&(fault!=2||!link.stage)){fired[0]=true;throw new IOException("injected final publication failure");}}};
            reject(()->c.writer.commitCapture(c.pins),"durability failure cannot complete "+fault);yes(fired[0],"reached target fault");c.locked(()->yes(HistoryStorage.preparationSources(c.c.f.store.storageRoot())==c.c.owner,"failed publication retains original owner"));reject(()->c.writer.commitCapture(c.pins),"failed completion cannot retry as success");cases++;
        }
        Capture changed=new Capture();boolean[] fired={false};changed.disk.hook=(event,file)->{if(event.equals("file-after")&&!fired[0]){fired[0]=true;Path nativeFile=changed.c.f.parent.resolve("store/"+changed.c.f.original.id+".snapshot");byte[] bytes=Files.readAllBytes(nativeFile);Path temp=nativeFile.resolveSibling("replacement.tmp");Files.write(temp,bytes);Files.move(temp,nativeFile,StandardCopyOption.REPLACE_EXISTING);}};
        reject(()->changed.writer.commitCapture(changed.pins),"native inode replacement during BEGIN");yes(!Files.exists(changed.c.f.parent.resolve("history_control/"+changed.c.plan.link.name())),"no LINK after failed source proof");cases++;
        Capture wrong=new Capture();Fixture foreign=new Fixture();HistoryPins foreignPins=HistoryPins.open(foreign.store,foreign.catalog,foreign.identity,foreign.index,foreign.disk);reject(()->wrong.writer.commitCapture(foreignPins),"foreign registered Pins");yes(!Files.exists(wrong.c.f.parent.resolve("history_control/"+wrong.c.plan.prepare.begin.name())),"wrong Pins cannot publish BEGIN");cases++;
    }
    static void admissionAndCallbacks()throws Exception {
        PrivateArtifactCopyCheck.Capture privateCopy=new PrivateArtifactCopyCheck.Capture(false);
        HistoryPins privatePins=HistoryPins.open(privateCopy.f.store,privateCopy.f.catalog,privateCopy.f.identity,privateCopy.f.index,privateCopy.f.disk);
        privateCopy.copyPreferences(new ByteArrayInputStream(PrivateArtifactCopyCheck.prefs(8)));
        privateCopy.writer.commitCapture(privatePins);
        privateCopy.locked(()->yes(HistoryStorage.preparationSources(privateCopy.f.store.storageRoot())==null,"private phase B closes and hands off before captured result"));cases++;
        PrivateArtifactCopyCheck.Capture missing=new PrivateArtifactCopyCheck.Capture(false);
        HistoryPins pins=HistoryPins.open(missing.f.store,missing.f.catalog,missing.f.identity,missing.f.index,missing.f.disk);
        reject(()->missing.writer.commitCapture(pins),"missing private artifact");
        yes(!Files.exists(missing.f.parent.resolve("history_control/"+missing.plan.prepare.begin.name())),"missing artifact refuses before BEGIN");cases++;
        Capture live=new Capture();PreparationSources.Reader reader=live.c.owner.open(()->new ByteArrayInputStream(new byte[0]));
        reject(()->live.writer.commitCapture(live.pins),"live owned reader blocks final claim");reader.close();
        yes(!Files.exists(live.c.f.parent.resolve("history_control/"+live.c.plan.prepare.begin.name())),"live reader refuses before BEGIN");cases++;
        for(boolean close:new boolean[]{false,true}){
            Capture c=new Capture();boolean[] fired={false};c.disk.hook=(event,file)->{if(event.equals("dir-after")&&!fired[0]&&Files.exists(c.c.f.parent.resolve("history_control/"+c.c.plan.prepare.begin.name()))){
                fired[0]=true;
                if(close)Files.write(c.c.f.parent.resolve("history_control/pin-"+c.c.plan.prepare.operation+".close"),HistoryPinRecord.close(c.c.plan.prepare.begin,HistoryPinRecord.Outcome.CANCELLED));
                else{PrivateOperationRecord.Artifact a=c.c.plan.prepare.artifact(1);Path target=c.c.f.parent.resolve("private_operations/"+c.c.preparation+"/"+a.filename());byte[] bytes=Files.readAllBytes(target);Path replacement=target.resolveSibling("replace.tmp");Files.write(replacement,bytes);Files.move(replacement,target,StandardCopyOption.REPLACE_EXISTING);}
            }};
            reject(()->c.writer.commitCapture(c.pins),"pre-LINK "+(close?"CLOSE":"same-byte artifact replacement"));yes(fired[0]&&!Files.exists(c.c.f.parent.resolve("history_control/"+c.c.plan.link.name())),"callback rejects before LINK");cases++;
        }
    }
    static void reopen(String[] args)throws Exception {
        File parent=new File(args[1]),root=new File(parent,"store");
        synchronized(HistoryStorage.monitorFor(root)){try(HistoryStorage.MutationScope lease=HistoryStorage.administration(root)){
            HistoryControlStore.Inspection c=new HistoryControlStore(parent).inspect();
            PrivateOperationStore.Ledger l=PrivateOperationStore.inspect(root,c,new HistoryStorage.Budget(HistoryStorage.METADATA_BUDGET));
            HistoryControlStore.Entry link=c.operations.links.get(UUID.fromString(args[2]));
            yes(c.pins.active!=null&&link!=null&&!link.stage&&args[3].equals(link.envelopeHash)&&l.complete&&l.reservedBytes==0&&l.reservedEntries==0,"fresh process validates exact committed files and LINK");
            yes(c.controlReservedBytes==126&&c.controlReservedEntries==1&&HistoryStorage.preparationSources(root)==null,"fresh process exact CLOSE credit without process source owner");
        }}
    }
    public static void main(String[] args)throws Exception{if(args.length!=0){reopen(args);return;}normal();failures();admissionAndCallbacks();System.out.println("Actual capture completion: "+cases+" cases PASS; activation/recovery deferred");}
}
