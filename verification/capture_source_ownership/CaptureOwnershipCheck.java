package com.example.markdowndesk;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import java.util.concurrent.*;

/** Preparation source callbacks use actual parent-scoped ownership, before BEGIN exists. */
public final class CaptureOwnershipCheck {
    private static int checks;
    interface Work {void run() throws Exception;}
    static void check(boolean yes,String why){checks++;if(!yes)throw new AssertionError(why);}
    static void rejects(Work work,String why)throws Exception {try{work.run();}catch(IOException expected){checks++;return;}throw new AssertionError(why);}
    static final class Fixture {
        final File root=Files.createTempDirectory("capture-owner-").resolve("store").toFile();
        final UUID id=UUID.randomUUID();
        final HistoryStorage.Budget budget=new HistoryStorage.Budget(HistoryStorage.METADATA_BUDGET);
        final PreparationSources.Capability producer=PreparationSources.newProducer();
        final PreparationSources owner=PreparationSources.forPreparation(root,id,budget,producer);
        Fixture()throws Exception{}
        PreparationSources.Proof claim()throws IOException {synchronized(HistoryStorage.monitorFor(root)){try(HistoryStorage.MutationScope lease=HistoryStorage.administration(root)){return owner.claimLocked();}}}
    }
    public static void main(String[] args)throws Exception {
        Fixture f=new Fixture();check(f.owner==PreparationSources.forPreparation(f.root,f.id,f.budget,f.producer),"same ID keeps same owner");
        rejects(()->PreparationSources.forPreparation(f.root,f.id,new HistoryStorage.Budget(1024),f.producer),"new budget cannot replace existing ownership");
        rejects(()->f.owner.reserveMetadata(new HistoryStorage.Budget(1024),1),"workspace cannot use independent budget");
        AtomicInteger closes=new AtomicInteger();
        PreparationSources.Reader reader=f.owner.open(()->new ByteArrayInputStream(new byte[]{1,2,3}){public void close(){closes.incrementAndGet();}});
        byte[] bytes=new byte[4];check(reader.read(bytes,0,4)==3&&bytes[2]==3,"bounded read delegates");
        f.owner.freeze();rejects(f::claim,"live idle source blocks claim");rejects(()->reader.read(bytes,0,1),"freeze stops reads");rejects(()->f.owner.open(()->new ByteArrayInputStream(bytes)),"freeze stops opens");
        reader.close();reader.close();check(closes.get()==1,"successful close counted once");PreparationSources.Proof proof=f.claim();
        rejects(f::claim,"claim cannot be minted twice");rejects(proof::validateLocked,"proof requires lease");
        synchronized(HistoryStorage.monitorFor(f.root)){try(HistoryStorage.MutationScope lease=HistoryStorage.administration(f.root)){proof.validateLocked();}}
        Fixture retry=new Fixture();AtomicInteger attempts=new AtomicInteger();PreparationSources.Reader failing=retry.owner.open(()->new ByteArrayInputStream(bytes){public void close()throws IOException{if(attempts.incrementAndGet()==1)throw new IOException("retry close");}});
        retry.owner.freeze();rejects(failing::close,"throwing close retained");rejects(retry::claim,"failed close not quiescence");failing.close();retry.claim();check(attempts.get()==2,"actual close retry permits claim");
        Fixture uncertain=new Fixture();rejects(()->uncertain.owner.open(()->{throw new IOException("uncertain open");}),"factory failure reported");uncertain.owner.freeze();rejects(uncertain::claim,"factory uncertainty retains admission");rejects(()->PreparationSources.forPreparation(uncertain.root,UUID.randomUUID(),uncertain.budget,uncertain.producer),"new UUID cannot bypass unknown ownership");check(uncertain.owner==PreparationSources.forPreparation(uncertain.root,uncertain.id,uncertain.budget,uncertain.producer),"second handle cannot clear uncertainty");
        Fixture nil=new Fixture();rejects(()->nil.owner.open(()->null),"null factory result uncertain");nil.owner.freeze();rejects(nil::claim,"null does not decrement count");
        Fixture callbacks=new Fixture();AtomicInteger guarded=new AtomicInteger();PreparationSources.Reader hostile=callbacks.owner.open(()->{rejectIO(()->callbacks.owner.freeze(),guarded);return new ByteArrayInputStream(bytes){public synchronized int read(byte[] b,int o,int n){rejectIO(()->callbacks.owner.freeze(),guarded);rejectIO(HistoryOperations::rejectParticipant,guarded);return super.read(b,o,n);}public void close(){rejectIO(()->callbacks.owner.freeze(),guarded);rejectIO(HistoryOperations::rejectParticipant,guarded);}};});
        hostile.read(bytes,0,1);hostile.close();check(guarded.get()==5,"open/read/close reject reentrant terminal entry across domains");callbacks.owner.freeze();callbacks.claim();
        Fixture thread=new Fixture();PreparationSources.Reader confined=thread.owner.open(()->new ByteArrayInputStream(bytes));AtomicReference<Throwable> other=new AtomicReference<>();Thread worker=new Thread(()->{try{confined.close();}catch(Throwable error){other.set(error);}});worker.start();worker.join();check(other.get() instanceof IOException,"foreign-thread close rejected");thread.owner.freeze();rejects(thread::claim,"foreign close never releases source");confined.close();thread.claim();
        Fixture locked=new Fixture();synchronized(HistoryStorage.monitorFor(locked.root)){rejects(()->locked.owner.open(()->new ByteArrayInputStream(bytes)),"factory cannot run under monitor");rejects(locked.owner::freeze,"no freeze/signals under monitor");}
        rejects(()->PreparationSources.forPreparation(locked.root,UUID.randomUUID(),new HistoryStorage.Budget(0),locked.producer),"budget rejection before owner allocation");
        rejects(()->PreparationSources.forPreparation(f.root,f.id,f.budget,PreparationSources.newProducer()),"foreign producer capability rejected");
        Fixture empty=new Fixture();PreparationSources.Reader eof=empty.owner.open(()->new ByteArrayInputStream(new byte[0]));check(eof.read(bytes,0,4)==-1,"EOF observed");empty.owner.freeze();rejects(empty::claim,"EOF not ownership release");eof.close();empty.claim();
        Fixture readFailure=new Fixture();PreparationSources.Reader badRead=readFailure.owner.open(()->new InputStream(){public int read()throws IOException{throw new IOException("read failed");}});rejects(()->badRead.read(bytes,0,1),"read failure reported");readFailure.owner.freeze();rejects(readFailure::claim,"read failure retains idle resource");badRead.close();readFailure.claim();
        Fixture recurse=new Fixture();AtomicReference<PreparationSources.Reader> self=new AtomicReference<>();AtomicInteger recursive=new AtomicInteger();PreparationSources.Reader recursion=recurse.owner.open(()->new ByteArrayInputStream(bytes){public void close(){rejectIO(()->self.get().close(),recursive);}});self.set(recursion);recursion.close();check(recursive.get()==1,"recursive close cannot erase ownership twice");recurse.owner.freeze();recurse.claim();
        OperationQuiescenceCheck.Setup operation=new OperationQuiescenceCheck.Setup();HistoryOperations.OperationHandle prior=operation.activate();HistoryOperations.DrainRequest drain=operation.ops.freeze(prior);operation.ops.quiesce(drain).toCompletableFuture().get();HistoryOperations.OperationHandle active=operation.activate();
        Fixture cross=new Fixture();AtomicInteger crossGuards=new AtomicInteger();Runnable terminal=()->{rejectIO(()->operation.ops.freeze(active),crossGuards);rejectIO(()->operation.ops.freezeForTerminal(operation.id),crossGuards);rejectIO(()->operation.ops.quiesce(drain),crossGuards);};
        PreparationSources.Reader crossed=cross.owner.open(()->{terminal.run();return new ByteArrayInputStream(bytes){public synchronized int read(byte[] b,int o,int n){terminal.run();return super.read(b,o,n);}public void close(){terminal.run();}};});crossed.read(bytes,0,1);crossed.close();check(crossGuards.get()==9,"actual operation freeze/drain reject preparation callback participants");cross.owner.freeze();cross.claim();
        for(int mode=0;mode<3;mode++)blocked(mode);
        Fixture runtime=new Fixture();try{runtime.owner.open(()->{throw new IllegalStateException("factory runtime");});throw new AssertionError("runtime swallowed");}catch(IllegalStateException expected){checks++;}runtime.owner.freeze();rejects(runtime::claim,"runtime failure permanently uncertain");
        Fixture assembly=new Fixture();rejects(()->assembly.owner.requireIdleBindingLocked(assembly.root,assembly.id,assembly.budget),"assembly requires mutation lease");
        synchronized(HistoryStorage.monitorFor(assembly.root)){try(HistoryStorage.MutationScope lease=HistoryStorage.administration(assembly.root)){
            assembly.owner.requireIdleBindingLocked(assembly.root,assembly.id,assembly.budget);checks++;
            rejects(()->assembly.owner.requireIdleBindingLocked(new File(assembly.root.getParentFile(),"catalog"),assembly.id,assembly.budget),"assembly wrong role rejected");
            rejects(()->assembly.owner.requireIdleBindingLocked(assembly.root,UUID.randomUUID(),assembly.budget),"assembly wrong preparation rejected");
            rejects(()->assembly.owner.requireIdleBindingLocked(assembly.root,assembly.id,new HistoryStorage.Budget(8192)),"assembly foreign budget rejected");
        }}
        assembly.owner.freeze();synchronized(HistoryStorage.monitorFor(assembly.root)){try(HistoryStorage.MutationScope lease=HistoryStorage.administration(assembly.root)){rejects(()->assembly.owner.requireIdleBindingLocked(assembly.root,assembly.id,assembly.budget),"frozen source owner cannot restart assembly");}}
        Fixture invalidated=new Fixture();rejects(invalidated.owner::invalidateLocked,"publication invalidation requires mutation lease");
        synchronized(HistoryStorage.monitorFor(invalidated.root)){try(HistoryStorage.MutationScope lease=HistoryStorage.administration(invalidated.root)){invalidated.owner.invalidateLocked();}}
        rejects(invalidated::claim,"failed internal publication cannot prove closure");rejects(()->PreparationSources.forPreparation(invalidated.root,UUID.randomUUID(),invalidated.budget,invalidated.producer),"invalidated publication cannot replace owner");
        Fixture callback=new Fixture();callback.owner.freeze();
        synchronized(HistoryStorage.monitorFor(callback.root)){try(HistoryStorage.MutationScope lease=HistoryStorage.administration(callback.root)){
            callback.owner.publicationCallbackLocked(()->rejectIO(callback.owner::claimLocked,new AtomicInteger()));checks++;
        }}
        callback.claim();checks++;
        Fixture consumer=new Fixture();rejects(()->consumer.owner.consumerCallback(()->{}),"consumer work needs a live owned source");
        PreparationSources.Reader consuming=consumer.owner.open(()->new ByteArrayInputStream(bytes));
        consumer.owner.consumerCallback(()->{
            consuming.close();
            AtomicReference<Throwable> outcome=new AtomicReference<>();Thread claimant=new Thread(()->{try{consumer.owner.freeze();consumer.claim();outcome.set(new AssertionError("running consumer hid behind source close"));}catch(Throwable expected){outcome.set(expected);}});claimant.start();try{claimant.join(5000);}catch(InterruptedException interrupted){Thread.currentThread().interrupt();throw new IOException(interrupted);}
            if(claimant.isAlive()||!(outcome.get() instanceof IOException))throw new AssertionError("consumer running work not counted",outcome.get());
            synchronized(HistoryStorage.monitorFor(consumer.root)){try(HistoryStorage.MutationScope lease=HistoryStorage.administration(consumer.root)){consumer.owner.publicationCallbackLocked(()->rejectIO(consumer.owner::claimLocked,new AtomicInteger()));}}
        });checks+=2;consumer.claim();checks++;
        sharedBudget();
        System.out.println("PASS preparation source ownership: "+checks+" assertions; no producer/activation/device claim");
    }
    static void sharedBudget()throws Exception {
        for(int round=0;round<12;round++){
            HistoryStorage.Budget budget=new HistoryStorage.Budget(8192);AtomicInteger admitted=new AtomicInteger();AtomicReference<Throwable> error=new AtomicReference<>();CountDownLatch start=new CountDownLatch(1);Thread[] threads=new Thread[8];
            for(int i=0;i<threads.length;i++){threads[i]=new Thread(()->{try{start.await();while(true){try{budget.charge(1);admitted.incrementAndGet();}catch(HistoryStorage.BudgetExceeded exhausted){break;}}}catch(Throwable failure){error.set(failure);}});threads[i].setDaemon(true);threads[i].start();}
            start.countDown();for(Thread thread:threads)thread.join(5000);
            for(Thread thread:threads)check(!thread.isAlive(),"budget admission thread finished");check(error.get()==null,"budget worker succeeded");check(admitted.get()==8192,"one shared budget cannot overspend under concurrent owners: "+admitted.get());
        }
    }
    static void blocked(int mode)throws Exception {
        Fixture f=new Fixture();CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1),finished=new CountDownLatch(1);AtomicReference<Throwable> failure=new AtomicReference<>();
        Thread worker=new Thread(()->{try{
            PreparationSources.Reader r=f.owner.open(()->{if(mode==0)waitHere(entered,release);return new ByteArrayInputStream(new byte[]{9}){public synchronized int read(byte[] b,int o,int n){if(mode==1)waitHere(entered,release);return super.read(b,o,n);}public void close(){if(mode==2)waitHere(entered,release);}};});
            if(mode==1)r.read(new byte[1],0,1);r.close();
        }catch(Throwable error){failure.set(error);}finally{finished.countDown();}});
        worker.setDaemon(true);worker.start();check(entered.await(5,TimeUnit.SECONDS),"blocked delegate entered "+mode);
        f.owner.freeze();rejects(f::claim,"running source delegate blocks proof "+mode);release.countDown();check(finished.await(5,TimeUnit.SECONDS),"delegate finished "+mode);check(failure.get()==null,"delegate completed without failure "+mode);f.claim();
    }
    static void waitHere(CountDownLatch entered,CountDownLatch release){entered.countDown();try{if(!release.await(5,TimeUnit.SECONDS))throw new AssertionError("delegate timeout");}catch(InterruptedException error){Thread.currentThread().interrupt();throw new AssertionError(error);}}
    static void rejectIO(IO work,AtomicInteger count){try{work.run();throw new AssertionError("participant escaped");}catch(IOException expected){count.incrementAndGet();}}
    interface IO {void run()throws IOException;}
}
