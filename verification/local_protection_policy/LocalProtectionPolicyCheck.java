package com.example.markdowndesk;

import java.io.*;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import com.example.markdowndesk.HistoryController.*;

/** Real installed authority/control publications, not a modeled settings store. */
public final class LocalProtectionPolicyCheck {
    static int checks;
    interface Work {void run()throws Exception;}
    static void check(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
    static void refuses(Work work)throws Exception {
        try{work.run();throw new AssertionError("unsafe policy update accepted");}
        catch(IOException failure){check(failure.getCause()==null&&!failure.getMessage().contains("PRIVATE"),"fixed sanitized refusal without backend cause");}
    }
    static LocalProtectionStatus status(LocalProtectionStatusCheck.Fixture f)throws IOException{return f.controller.inspectLocalProtection();}
    static LocalProtectionStatus update(LocalProtectionStatusCheck.Fixture f,LocalProtectionStatus expected,long quota,int count,boolean enabled)throws IOException{return f.controller.updateLocalProtectionPolicy(expected,quota,count,enabled);}
    static Map<String,String> disk(LocalProtectionStatusCheck.Fixture f)throws IOException{return LocalProtectionStatusCheck.disk(f.parent);}
    static void copyFiles(Path from,Path to)throws IOException {
        try(java.util.stream.Stream<Path> paths=Files.walk(from)){
            for(Path p:(Iterable<Path>)paths::iterator){Path target=to.resolve(from.relativize(p));if(Files.isDirectory(p))Files.createDirectories(target);else Files.copy(p,target,StandardCopyOption.REPLACE_EXISTING);}
        }
    }
    static void defaultsAndChanges()throws Exception {
        LocalProtectionStatusCheck.Fixture f=new LocalProtectionStatusCheck.Fixture();LocalProtectionStatus before=status(f);
        check(!before.automaticEnabled&&before.quotaBytes==(64L<<20)&&before.automaticCount==20,"legacy registration default disabled");
        Map<String,String> files=disk(f);long generation=f.generation();
        LocalProtectionStatus same=update(f,before,64L<<20,20,false);
        check(generation==f.generation()&&files.equals(disk(f))&&same.policyGeneration==before.policyGeneration,"fresh triple no-op does not append or change generation");
        LocalProtectionStatus enabled=update(f,same,128L<<20,5,true);
        check(enabled.automaticEnabled&&enabled.policyGeneration==1&&enabled.quotaBytes==(128L<<20)&&enabled.automaticCount==5,"explicit enabled triple publishes once");
        check(!before.automaticEnabled&&before.quotaBytes==(64L<<20),"prior scalar projection immutable");
        HistoryController reopened=HistoryController.open(f.store,f.disk);
        check(reopened.inspectLocalProtection().automaticEnabled,"reopen reads durable enabled flag");
        LocalProtectionStatus disabled=reopened.updateLocalProtectionPolicy(enabled,128L<<20,5,false);
        check(!disabled.automaticEnabled&&disabled.policyGeneration==2,"same-policy installed replacement accepts evidence and explicit disable");
        for(long quota:new long[]{64L<<20,128L<<20,256L<<20,512L<<20,1024L<<20})disabled=update(f,disabled,quota,20,false);
        for(int count:new int[]{0,5,10,20,50})disabled=update(f,disabled,1024L<<20,count,false);
        check(disabled.automaticCount==50&&!disabled.automaticEnabled,"all existing codec values accepted without enabling cleanup");
        LocalProtectionStatus valid=disabled;files=disk(f);
        refuses(()->update(f,valid,65L<<20,20,false));refuses(()->update(f,valid,64L<<20,6,false));refuses(()->update(f,valid,64L<<20,0,true));refuses(()->update(f,null,64L<<20,20,false));
        check(files.equals(disk(f)),"invalid values or missing expected never append");
    }
    static void expectedEvidence()throws Exception {
        LocalProtectionStatusCheck.Fixture f=new LocalProtectionStatusCheck.Fixture();LocalProtectionStatus stale=status(f);
        LocalProtectionStatus current=update(f,stale,128L<<20,20,false);current=update(f,current,64L<<20,20,false);
        Map<String,String> before=disk(f);refuses(()->update(f,stale,256L<<20,20,false));check(before.equals(disk(f)),"scalar ABA stale generation rejected before append");
        Constructor<LocalProtectionStatus> displayConstructor=LocalProtectionStatus.class.getDeclaredConstructor(HistoryCapacity.EffectivePolicy.class,HistoryCapacity.Usage.class);displayConstructor.setAccessible(true);
        LocalProtectionStatus display=displayConstructor.newInstance(f.capacity().policy(),f.capacity().inspect());
        check(display.quotaBytes==current.quotaBytes&&!display.automaticEnabled,"old display constructor compatible");refuses(()->update(f,display,256L<<20,20,false));
        LocalProtectionStatusCheck.Fixture original=new LocalProtectionStatusCheck.Fixture();LocalProtectionStatus token=status(original);
        Path cloned=Files.createTempDirectory("policy-cloned-bundle-");copyFiles(original.parent,cloned);
        LocalProtectionStatusCheck.Disk cloneDisk=new LocalProtectionStatusCheck.Disk();ProtectionStore cloneStore=new ProtectionStore(cloned.resolve("protected_saves").toFile(),cloneDisk,ProtectionStore.DEFAULT_QUOTA_BYTES);
        HistoryController clone=HistoryController.open(cloneStore,cloneDisk);LocalProtectionStatus cloneStatus=clone.inspectLocalProtection();
        check(cloneStatus.policyGeneration==token.policyGeneration,"cloned bundle exact generation");
        refuses(()->clone.updateLocalProtectionPolicy(token,128L<<20,20,false));refuses(()->update(original,cloneStatus,128L<<20,20,false));
        LocalProtectionStatusCheck.Fixture replacement=new LocalProtectionStatusCheck.Fixture();copyFiles(replacement.parent,original.parent);
        HistoryController replacementController=HistoryController.open(original.store,original.disk);LocalProtectionStatus replaced=replacementController.inspectLocalProtection();
        check(replaced.policyGeneration==token.policyGeneration,"new bundle at same root has equal generation");before=disk(original);
        refuses(()->replacementController.updateLocalProtectionPolicy(token,128L<<20,20,false));check(before.equals(disk(original)),"same-root equal-generation different bundle refuses");
        // Test the private hash check independently of bundle/generation via a synthetic display boundary.
        LocalProtectionStatus forged=replacementController.inspectLocalProtection();Field hash=LocalProtectionStatus.class.getDeclaredField("policyControlHash");hash.setAccessible(true);hash.set(forged,"0".repeat(64));
        refuses(()->replacementController.updateLocalProtectionPolicy(forged,128L<<20,20,false));
    }
    static void lowerAndFailure()throws Exception {
        LocalProtectionStatusCheck.Fixture f=new LocalProtectionStatusCheck.Fixture();update(f,status(f),128L<<20,20,false);ProtectionStore.Transaction pending=f.pending();
        for(int i=0;i<9;i++)try(RandomAccessFile file=new RandomAccessFile(f.parent.resolve("protected_saves").resolve(UUID.randomUUID()+".stage").toFile(),"rw")){file.setLength(8L<<20);}
        Map<String,String> before=disk(f);LocalProtectionStatus lowered=update(f,status(f),64L<<20,20,false);
        check(lowered.overQuota&&lowered.usageComplete&&lowered.dataActualBytes>(64L<<20),"lower-than-used accepted without pretending free space");
        Map<String,String> after=disk(f);check(before.entrySet().stream().allMatch(e->Objects.equals(e.getValue(),after.get(e.getKey()))),"quota change preserves every existing file");
        try{f.store.putSnapshot("tab",null,"test.md",2,new byte[]{3},"attempted");throw new AssertionError("over quota growth");}catch(IOException expected){checks++;}
        long charged=lowered.dataChargedBytes;f.store.complete(pending);check(status(f).dataChargedBytes==charged,"existing exact native completion still converts reservation");
        LocalProtectionStatusCheck.Fixture incomplete=new LocalProtectionStatusCheck.Fixture();LocalProtectionStatus expected=status(incomplete);Files.writeString(incomplete.parent.resolve("protected_saves/unknown"),"PRIVATE");before=disk(incomplete);
        refuses(()->update(incomplete,expected,64L<<20,20,false));check(before.equals(disk(incomplete)),"even no-op refuses incomplete accounting without writes");
        LocalProtectionStatusCheck.Fixture uncertain=new LocalProtectionStatusCheck.Fixture();LocalProtectionStatus old=status(uncertain);uncertain.disk.failMove=true;
        refuses(()->update(uncertain,old,128L<<20,20,true));uncertain.disk.failMove=false;before=disk(uncertain);
        refuses(()->update(uncertain,old,64L<<20,20,false));check(before.equals(disk(uncertain)),"uncertain append never repaired retried or rolled back by bridge");
    }
    static void callbackCoherence()throws Exception {
        LocalProtectionStatusCheck.Fixture changed=new LocalProtectionStatusCheck.Fixture();LocalProtectionStatus expected=status(changed);
        changed.disk.callback=()->changed.capacity().setPolicy(new HistoryPolicy(128L<<20,5),false);
        refuses(()->update(changed,expected,256L<<20,20,false));check(status(changed).policyGeneration==1&&status(changed).quotaBytes==(128L<<20),"callback policy change prevents requested second append");
        LocalProtectionStatusCheck.Fixture replaced=new LocalProtectionStatusCheck.Fixture();LocalProtectionStatus token=status(replaced);replaced.disk.callback=()->HistoryController.open(replaced.store,replaced.disk);
        refuses(()->update(replaced,token,128L<<20,20,false));check(status(replaced).policyGeneration==0,"callback authority replacement invalidates sample before append");
        LocalProtectionStatusCheck.Fixture post=new LocalProtectionStatusCheck.Fixture();LocalProtectionStatus prior=status(post);
        LocalProtectionStatusCheck.Work[] afterAppend=new LocalProtectionStatusCheck.Work[1];
        afterAppend[0]=()->{if(post.capacity().policy().generation==0)post.disk.callback=afterAppend[0];else HistoryStorage.changed(post.store.storageRoot());};
        post.disk.callback=afterAppend[0];refuses(()->update(post,prior,128L<<20,5,true));
        LocalProtectionStatus persisted=status(post);check(persisted.policyGeneration==1&&persisted.automaticEnabled&&persisted.quotaBytes==(128L<<20),"post-append sample failure reports unconfirmed despite one durable requested generation");
        Map<String,String> once=disk(post);refuses(()->update(post,prior,128L<<20,5,true));check(once.equals(disk(post)),"unconfirmed stale intent never retried or rolled back");
        LocalProtectionStatusCheck.Fixture fatal=new LocalProtectionStatusCheck.Fixture();LocalProtectionStatus valid=status(fatal);AssertionError original=new AssertionError("fatal");fatal.disk.callback=()->{throw original;};
        try{update(fatal,valid,128L<<20,20,false);throw new AssertionError("fatal swallowed");}catch(AssertionError actual){check(actual==original,"fatal Error identity propagates");}
    }
    public static void main(String[] args)throws Exception{defaultsAndChanges();expectedEvidence();lowerAndFailure();callbackCoherence();System.out.println("Local protection policy: "+checks+" checks PASS");}
}
