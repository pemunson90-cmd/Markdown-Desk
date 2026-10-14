package com.example.markdowndesk;

import java.io.*;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.util.*;

/** Child JVM: the driver kills the blocked process at actual durability calls. */
public final class CapacityRestartProbe {
    static final class Disk implements ProtectionStore.Durability {
        String boundary="";boolean armed;
        void barrier(String event)throws IOException {
            if(armed&&boundary.equals(event)){System.out.println("BARRIER "+event);System.out.flush();System.in.read();throw new IOException("barrier released without kill");}
        }
        public void syncFile(FileDescriptor fd)throws IOException {fd.sync();}
        public void move(File source,File destination)throws IOException {
            String name=destination.getName();
            if(name.equals(HistoryControlStore.ENROLLMENT_NAME))barrier("enrollment-stage-sync");
            if(name.equals(HistoryControlStore.REGISTRATION_NAME))barrier("registration-stage-sync");
            if(name.startsWith("policy-"))barrier("policy-stage-sync");
            Files.move(source.toPath(),destination.toPath(),StandardCopyOption.ATOMIC_MOVE);
            if(name.equals(HistoryControlStore.ENROLLMENT_NAME))barrier("enrollment-move");
            if(name.equals(HistoryControlStore.REGISTRATION_NAME))barrier("registration-move");
            if(name.startsWith("policy-"))barrier("policy-move");
        }
        public void syncDirectory(File directory)throws IOException {
            if(!directory.getName().equals(HistoryControlStore.ROOT_NAME)&&Files.isDirectory(directory.toPath().resolve(HistoryControlStore.ROOT_NAME)))barrier("registration-root-created");
            try(FileChannel channel=FileChannel.open(directory.toPath(),StandardOpenOption.READ)){channel.force(true);}
            if(directory.getName().equals(HistoryControlStore.ROOT_NAME)&&Files.exists(directory.toPath().resolve("policy-0000000000000001.control")))barrier("policy-directory-sync");
        }
    }
    static final class Bundle {
        final Path parent;final Disk disk;final ProtectionStore store;final HistoryCatalog catalog;final HistoryIdentity identity;final HistoryIndex index;
        Bundle(Path parent,Disk disk)throws IOException {
            this.parent=parent;this.disk=disk;store=new ProtectionStore(parent.resolve("store").toFile(),disk,ProtectionStore.DEFAULT_QUOTA_BYTES);
            catalog=new HistoryCatalog(parent.resolve("catalog").toFile(),store,disk);identity=new HistoryIdentity(parent.resolve("identity").toFile(),disk);
            index=new HistoryIndex(store,catalog,identity,parent.resolve("index").toFile(),disk);
        }
        HistoryCapacity register()throws IOException{return HistoryCapacity.register(store,catalog,identity,index,new HistoryPolicy(64L*1024*1024,20),disk);}
        HistoryCapacity open()throws IOException{return HistoryCapacity.open(store,catalog,identity,index,disk);}
        void write()throws IOException {store.putSnapshot("document","uri","title",1,new byte[]{4,5},"attempted");}
    }
    interface Action {void run()throws Exception;}
    static void blocked(Action action,String reason)throws Exception {try{action.run();throw new AssertionError(reason);}catch(IOException expected){}}
    public static void main(String[] args)throws Exception {
        String mode=args[0];Path parent=Paths.get(args[1]);Disk disk=new Disk();Bundle bundle=new Bundle(parent,disk);
        if(mode.equals("interrupt")){
            bundle.write();String boundary=args[2];if(boundary.startsWith("policy-"))bundle.register();
            disk.boundary=boundary;disk.armed=true;
            if(boundary.startsWith("policy-"))bundle.open().setPolicy(new HistoryPolicy(128L*1024*1024,5));else bundle.register();
            throw new AssertionError("boundary not reached "+boundary);
        }
        if(mode.equals("seed")){bundle.write();bundle.register();System.out.println("SEEDED");return;}
        if(mode.equals("limited-policy")){
            HistoryCapacity capacity=bundle.open();long size=Long.parseLong(args[2]);
            blocked(()->capacity.setPolicy(new HistoryPolicy(128L*1024*1024,5)),"write limit did not fail");
            Path stage=parent.resolve("history_control/policy-0000000000000001.control.stage");
            if(Files.size(stage)!=size)throw new AssertionError("unexpected stage prefix size");
            disk.boundary="policy-prefix-"+size;disk.armed=true;disk.barrier(disk.boundary);return;
        }
        if(mode.equals("fill-control")){
            HistoryControlStore.Inspection control=new HistoryControlStore(parent.toFile()).inspect();String previous=control.latest.envelopeHash;
            for(int generation=1;generation<=254;generation++){
                byte[] bytes=HistoryControlStore.encodePolicy(control.registration.bundleId,generation,previous,64L*1024*1024,20);
                Files.write(parent.resolve("history_control").resolve(HistoryControlStore.policyFilename(generation)),bytes);previous=HistoryControlStore.decode(bytes).envelopeHash;
            }
            System.out.println("CONTROL FILLED");return;
        }
        if(mode.equals("legacy")){bundle.write();System.out.println("LEGACY PASS");return;}
        blocked(bundle::write,"fresh process legacy mutation bypass");
        if(mode.equals("blocked")){blocked(bundle::open,"missing/malformed authority opened");System.out.println("BLOCKED PASS");return;}
        HistoryCapacity capacity=bundle.open();long expected=Long.parseLong(args[2]);
        if(capacity.policy().generation!=expected||capacity.reconcile().generation!=expected)throw new AssertionError("generation changed on reconcile");
        long quota=expected==1?128L*1024*1024:64L*1024*1024;int count=expected==1?5:20;
        if(capacity.policy().quotaBytes!=quota||capacity.policy().automaticCount!=count)throw new AssertionError("reconcile adopted wrong policy");
        if(expected==254){
            if(capacity.inspect().controlActualEntries!=256)throw new AssertionError("enrollment allowance omitted");
            blocked(()->capacity.setPolicy(new HistoryPolicy(128L*1024*1024,5)),"full valid control admitted policy");
            if(capacity.policy().generation!=254)throw new AssertionError("rejected policy changed generation");
        }
        blocked(()->new ProtectionStore(parent.resolve("store").toFile(),disk,1),"conflicting bootstrap hint");
        blocked(()->new ProtectionStore(parent.resolve("unlisted").toFile(),disk,ProtectionStore.DEFAULT_QUOTA_BYTES),"unlisted constructor created root");
        bundle.write();System.out.println("RECONCILED PASS "+expected);
    }
}
