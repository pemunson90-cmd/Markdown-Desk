package com.example.markdowndesk;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Real native stores and authority, with only durability callbacks controlled. */
public final class LocalProtectionStatusCheck {
    static int checks;
    interface Work {void run()throws Exception;}
    static void check(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
    static void refused(Work work)throws Exception {
        try{work.run();throw new AssertionError("accepted unavailable/stale status");}
        catch(IOException expected){check(expected.getCause()==null&&!expected.getMessage().contains("PRIVATE"),"sanitized failure without backend cause");}
    }
    static final class Disk implements ProtectionStore.Durability {
        Work callback;boolean failMove,failDirectory,uncheckedFailure;
        public void syncFile(FileDescriptor fd)throws IOException{fd.sync();}
        public void move(File from,File to)throws IOException{if(failMove)throw new IOException("PRIVATE move detail");Files.move(from.toPath(),to.toPath(),StandardCopyOption.ATOMIC_MOVE);}
        public void syncDirectory(File directory)throws IOException {
            if(directory.getName().equals("protected_saves")){
                if(uncheckedFailure)throw new SecurityException("PRIVATE access details");
                Work work=callback;callback=null;
                if(work!=null)try{work.run();}catch(Exception failure){throw new IOException("PRIVATE callback",failure);}
                if(failDirectory)throw new IOException("PRIVATE directory");
            }
        }
    }
    static final class Fixture {
        final Path parent=Files.createTempDirectory("local-protection-status-");
        final Disk disk=new Disk();
        final ProtectionStore store=new ProtectionStore(parent.resolve("protected_saves").toFile(),disk,ProtectionStore.DEFAULT_QUOTA_BYTES);
        final HistoryController controller=HistoryController.open(store,disk);
        Fixture()throws Exception{}
        HistoryCapacity capacity()throws IOException{synchronized(HistoryStorage.monitorFor(store.storageRoot())){return HistoryStorage.capacity(store.storageRoot());}}
        void install(HistoryCapacity capacity)throws IOException{synchronized(HistoryStorage.monitorFor(store.storageRoot())){try(HistoryStorage.MutationScope lease=HistoryStorage.administration(store.storageRoot())){HistoryStorage.capacity(store.storageRoot(),capacity);}}}
        long generation()throws IOException{synchronized(HistoryStorage.monitorFor(store.storageRoot())){return HistoryStorage.generation(store.storageRoot());}}
        ProtectionStore.Transaction pending()throws IOException {
            ProtectionStore.Snapshot before=store.putSnapshot("tab",null,"test.md",1,new byte[]{1},"original");
            ProtectionStore.Snapshot after=store.putSnapshot("tab",null,"test.md",1,new byte[]{2},"attempted");
            return store.prepare(before,after,before.hash);
        }
    }
    static Map<String,String> disk(Path parent)throws IOException {
        Map<String,String> result=new TreeMap<>();
        try(java.util.stream.Stream<Path> walk=Files.walk(parent)){
            for(Path path:(Iterable<Path>)walk.filter(Files::isRegularFile)::iterator)
                result.put(parent.relativize(path).toString(),HistoryStorage.hex(HistoryStorage.digest().digest(Files.readAllBytes(path))));
        }
        return result;
    }
    static void normal()throws Exception {
        Fixture f=new Fixture();HistoryController.LocalProtectionStatus initial=f.controller.inspectLocalProtection();
        check(initial.quotaBytes==(64L<<20)&&initial.automaticCount==20&&initial.policyGeneration==0,"preserved registered defaults");
        check(initial.usageComplete&&!initial.mutationBlocked&&!initial.overQuota&&initial.issues.isEmpty(),"healthy empty accounting");
        ProtectionStore.Transaction pending=f.pending();HistoryCapacity.Usage expected=f.capacity().inspect();
        Map<String,String> before=disk(f.parent);long generation=f.generation();
        HistoryController.LocalProtectionStatus status=f.controller.inspectLocalProtection();
        check(before.equals(disk(f.parent))&&generation==f.generation(),"inspection changes no files or history generation");
        check(status.dataActualBytes==expected.dataActualBytes&&status.completionReservedBytes==expected.completionReservedBytes&&status.completionReservedBytes>0,"actual and pending completion reservation exact");
        check(status.operationReservedBytes==expected.operationReservedBytes&&status.dataChargedBytes==status.dataActualBytes+status.completionReservedBytes+status.operationReservedBytes,"charged includes each reservation exactly once");
        check(status.controlActualBytes==expected.controlActualBytes&&status.controlReservedBytes==expected.controlReservedBytes&&status.controlActualEntries==expected.controlActualEntries&&status.controlReservedEntries==expected.controlReservedEntries,"separate control usage exact");
        try{status.issues.add(HistoryController.LocalProtectionIssue.OVER_QUOTA);throw new AssertionError("mutable issues");}catch(UnsupportedOperationException expectedMutation){checks++;}
        for(java.lang.reflect.Field field:HistoryController.LocalProtectionStatus.class.getFields())check(java.lang.reflect.Modifier.isFinal(field.getModifiers()),"immutable public field "+field.getName());
        f.capacity().setPolicy(new HistoryPolicy(128L<<20,5));
        HistoryController replacement=HistoryController.open(f.store,f.disk);
        HistoryController.LocalProtectionStatus oldController=f.controller.inspectLocalProtection(),newController=replacement.inspectLocalProtection();
        check(oldController.quotaBytes==(128L<<20)&&oldController.automaticCount==5&&oldController.policyGeneration==1&&newController.policyGeneration==1,"old controller follows currently installed replacement authority");
        check(status.quotaBytes==(64L<<20)&&status.completionReservedBytes>0,"old projection remains immutable");
        f.store.complete(pending);check(f.controller.inspectLocalProtection().completionReservedBytes==0,"completion reservation converts normally");
    }
    static void unavailableAndIncomplete()throws Exception {
        Fixture f=new Fixture();HistoryCapacity original=f.capacity();f.install(null);refused(()->f.controller.inspectLocalProtection());f.install(original);
        Fixture foreign=new Fixture();f.install(foreign.capacity());refused(()->f.controller.inspectLocalProtection());f.install(original);
        Files.writeString(f.parent.resolve("protected_saves/unknown"),"PRIVATE contents");
        HistoryController.LocalProtectionStatus status=f.controller.inspectLocalProtection();
        check(!status.usageComplete&&status.mutationBlocked,"incomplete usage never healthy");
        check(status.issues.contains(HistoryController.LocalProtectionIssue.INCOMPLETE_ACCOUNTING)&&status.issues.contains(HistoryController.LocalProtectionIssue.MUTATION_BLOCKED)&&status.issues.contains(HistoryController.LocalProtectionIssue.BACKEND_DIAGNOSTICS_PRESENT)&&status.issues.size()<=4,"bounded enum diagnostics only");
        Fixture uncertain=new Fixture();uncertain.disk.failMove=true;
        try{uncertain.capacity().setPolicy(new HistoryPolicy(128L<<20,5));throw new AssertionError("fault not injected");}catch(IOException expected){checks++;}
        uncertain.disk.failMove=false;Map<String,String> retained=disk(uncertain.parent);
        try{HistoryController.LocalProtectionStatus blocked=uncertain.controller.inspectLocalProtection();check(blocked.mutationBlocked,"uncertain policy blocked");}catch(IOException unavailable){check(unavailable.getCause()==null&&!unavailable.getMessage().contains("PRIVATE"),"uncertain inspection sanitized");}
        check(retained.equals(disk(uncertain.parent)),"inspection never promotes retained policy stage");
        Fixture full=new Fixture();
        for(int n=0;n<9;n++)try(RandomAccessFile stage=new RandomAccessFile(full.parent.resolve("protected_saves").resolve(UUID.randomUUID()+".stage").toFile(),"rw")){stage.setLength(8L<<20);}
        long fullGeneration=full.generation();HistoryController.LocalProtectionStatus over=full.controller.inspectLocalProtection();
        check(over.usageComplete&&!over.mutationBlocked&&over.overQuota&&over.issues.contains(HistoryController.LocalProtectionIssue.OVER_QUOTA),"accounted retained stages honestly over quota without pretending mutation uncertainty");
        check(over.dataActualBytes==(72L<<20)&&over.dataChargedBytes==over.dataActualBytes&&full.generation()==fullGeneration,"over quota projection does not delete or alter generation");
    }
    static void races()throws Exception {
        Fixture changed=new Fixture();changed.disk.callback=()->HistoryStorage.changed(changed.store.storageRoot());refused(()->changed.controller.inspectLocalProtection());
        Fixture replaced=new Fixture();replaced.disk.callback=()->HistoryController.open(replaced.store,replaced.disk);refused(()->replaced.controller.inspectLocalProtection());
        Fixture policy=new Fixture();policy.disk.callback=()->policy.capacity().setPolicy(new HistoryPolicy(128L<<20,5));refused(()->policy.controller.inspectLocalProtection());
        check(policy.controller.inspectLocalProtection().quotaBytes==(128L<<20),"retry after changed policy uses real authority");
        Fixture io=new Fixture();io.disk.failDirectory=true;
        HistoryController.LocalProtectionStatus incomplete=io.controller.inspectLocalProtection();
        check(!incomplete.usageComplete&&incomplete.mutationBlocked&&incomplete.issues.contains(HistoryController.LocalProtectionIssue.BACKEND_DIAGNOSTICS_PRESENT),"directory validation failure honestly incomplete and sanitized");
        Fixture unchecked=new Fixture();unchecked.disk.uncheckedFailure=true;refused(()->unchecked.controller.inspectLocalProtection());
    }
    public static void main(String[] args)throws Exception{normal();unavailableAndIncomplete();races();System.out.println("Local protection status: "+checks+" checks PASS");}
}
