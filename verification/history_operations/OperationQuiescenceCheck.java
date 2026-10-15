package com.example.markdowndesk;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static com.example.markdowndesk.HistoryPinsCheck.*;
import static com.example.markdowndesk.OperationTerminalCheck.*;

public final class OperationQuiescenceCheck {
    static final class Setup {
        final Fixture f=new Fixture();final HistoryPins pins=pins(f);final String id=begin(f,pins);
        final HistoryOperations ops=HistoryOperations.forBundle(f.store,f.catalog,f.identity,f.index);
        Setup()throws Exception{}
        HistoryOperations.OperationHandle activate()throws IOException{return ops.activateAcknowledged(id,input());}
    }
    static void readersAndCallbacks()throws Exception {
        Setup s=new Setup();HistoryOperations.OperationHandle handle=s.activate();
        check(s.ops==HistoryOperations.forBundle(s.f.store,s.f.catalog,s.f.identity,s.f.index),"two handles share coordinator");
        AtomicInteger closed=new AtomicInteger();
        HistoryOperations.TrackedInput in=s.ops.openReader(handle,()->new ByteArrayInputStream(MANIFEST){public void close(){closed.incrementAndGet();}});
        check(in.read()==9&&in.available()==2,"tracked reader delegates");in.mark(10);check(in.skip(1)==1,"tracked skip");in.reset();check(in.read(new byte[1])==1,"tracked bulk read");
        List<Runnable> queue=new ArrayList<>();AtomicInteger delivered=new AtomicInteger();
        HistoryOperations.CallbackTicket ticket=s.ops.post(handle,queue::add,delivered::incrementAndGet);
        HistoryOperations.DrainRequest request=s.ops.freeze(handle);CompletableFuture<HistoryOperations.Quiescence> proof=s.ops.quiesce(request).toCompletableFuture();
        check(!proof.isDone(),"open resource prevents proof");rejects(()->in.read(),"read after freeze");rejects(()->s.ops.openReader(handle,()->input()),"admission frozen");
        queue.get(0).run();check(delivered.get()==0,"queued callback suppressed");s.ops.cancelPending(ticket);
        AtomicBoolean inlineRejected=new AtomicBoolean();AtomicBoolean outsideLocks=new AtomicBoolean();
        proof.thenAccept(q->{try{outsideLocks.set(!Thread.holdsLock(gateLock(s.ops))&&!Thread.holdsLock(HistoryStorage.monitorFor(s.f.store.storageRoot())));s.pins.close(q,HistoryPinRecord.Outcome.CANCELLED,null);}catch(IOException expected){inlineRejected.set(true);}});
        in.close();check(closed.get()==1&&proof.isDone(),"actual closure produces proof");
        check(inlineRejected.get()&&outsideLocks.get(),"inline terminal rejected outside both locks");
        s.pins.close(proof.get(),HistoryPinRecord.Outcome.CANCELLED,null);
        rejects(()->s.activate(),"closed operation cannot reactivate");
    }
    static void countedOpeningAndWrite()throws Exception {
        Setup s=new Setup();HistoryOperations.OperationHandle handle=s.activate();CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1),closed=new CountDownLatch(1);
        ExecutorService owner=Executors.newSingleThreadExecutor(task->{Thread thread=new Thread(task);thread.setDaemon(true);return thread;});
        Future<?> future=owner.submit(()->{try{HistoryOperations.TrackedOutput out=s.ops.openWriter(handle,()->{entered.countDown();await(release);return new ByteArrayOutputStream(){public void close(){closed.countDown();}};});out.close();}catch(IOException failure){throw new UncheckedIOException(failure);}});
        check(entered.await(5,TimeUnit.SECONDS),"factory entered");CompletableFuture<HistoryOperations.Quiescence> q=s.ops.quiesce(s.ops.freeze(handle)).toCompletableFuture();
        check(!q.isDone(),"opening ticket prevents proof");release.countDown();future.get(5,TimeUnit.SECONDS);check(closed.getCount()==0&&q.isDone(),"late factory return owned until close");owner.shutdown();
        Setup bad=new Setup();HistoryOperations.OperationHandle b=bad.activate();rejects(()->bad.ops.openReader(b,()->{throw new IOException("uncertain opener");}),"factory error");
        check(!bad.ops.quiesce(bad.ops.freeze(b)).toCompletableFuture().isDone(),"uncertain factory ownership retains registration");
        Setup write=new Setup();HistoryOperations.OperationHandle w=write.activate();ByteArrayOutputStream bytes=new ByteArrayOutputStream();HistoryOperations.TrackedOutput out=write.ops.openWriter(w,()->bytes);
        out.write(1);out.write(new byte[]{2,3});out.write(new byte[]{4,5},1,1);out.flush();
        check(Arrays.equals(bytes.toByteArray(),new byte[]{1,2,3,5}),"all write methods tracked");
        CompletableFuture<HistoryOperations.Quiescence> proof=write.ops.quiesce(write.ops.freeze(w)).toCompletableFuture();out.close();check(proof.isDone(),"writer closure drains");
    }
    static void callbackAndOuterLock()throws Exception {
        Setup s=new Setup();HistoryOperations.OperationHandle h=s.activate();List<Runnable> queue=new ArrayList<>();
        HistoryOperations.CallbackTicket t=s.ops.post(h,queue::add,()->{});HistoryOperations.DrainRequest r=s.ops.freeze(h);CompletableFuture<HistoryOperations.Quiescence> q=s.ops.quiesce(r).toCompletableFuture();
        // Freeze revokes pending delivery; no actual callback may remain after proof.
        queue.get(0).run();check(q.isDone(),"revoked queued callback needs no scheduler cooperation");
        synchronized(HistoryStorage.monitorFor(s.f.store.storageRoot())){rejects(()->s.ops.cancelPending(t),"outer-lock cancellation rejects");rejects(()->s.ops.quiesce(r),"outer-lock future entry rejects");}
        Setup running=new Setup();HistoryOperations.OperationHandle run=running.activate();CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);ExecutorService worker=Executors.newSingleThreadExecutor(task->{Thread thread=new Thread(task);thread.setDaemon(true);return thread;});
        running.ops.post(run,worker::execute,()->{entered.countDown();await(release);});check(entered.await(5,TimeUnit.SECONDS),"callback running");
        CompletableFuture<HistoryOperations.Quiescence> pending=running.ops.quiesce(running.ops.freeze(run)).toCompletableFuture();check(!pending.isDone(),"running callback drains");release.countDown();check(pending.get(5,TimeUnit.SECONDS)!=null,"callback proof completes");worker.shutdown();
        Setup throwsAfterQueue=new Setup();HistoryOperations.OperationHandle e=throwsAfterQueue.activate();List<Runnable> captured=new ArrayList<>();AtomicInteger calls=new AtomicInteger();
        try{throwsAfterQueue.ops.post(e,task->{captured.add(task);throw new RejectedExecutionException();},calls::incrementAndGet);throw new AssertionError("scheduler rejection");}catch(RejectedExecutionException expected){checks++;}
        captured.get(0).run();check(calls.get()==0,"scheduler exception cannot deliver unregistered callback");
    }
    static void epochs()throws Exception {
        Setup s=new Setup();HistoryOperations.OperationHandle first=s.activate();HistoryOperations.Quiescence old=s.ops.quiesce(s.ops.freeze(first)).toCompletableFuture().get();
        HistoryOperations.OperationHandle second=s.activate();rejects(()->s.ops.openReader(first,()->input()),"old epoch handle");rejects(()->s.pins.close(old,HistoryPinRecord.Outcome.CANCELLED,null),"old epoch proof");
        HistoryOperations.Quiescence current=s.ops.quiesce(s.ops.freeze(second)).toCompletableFuture().get();
        InputStream hostile=new ByteArrayInputStream(MANIFEST){public synchronized int read(byte[] b,int o,int n){try{s.activate();throw new AssertionError("activation during terminal manifest");}catch(IOException expected){checks++;}return super.read(b,o,n);}};
        s.pins.close(current,HistoryPinRecord.Outcome.RELEASED,hostile);
        Setup cold=new Setup();HistoryOperations.Quiescence q=cold.ops.quiesce(cold.ops.freezeForTerminal(cold.id)).toCompletableFuture().get();cold.pins.close(q,HistoryPinRecord.Outcome.CANCELLED,null);
        check(inspect(cold.f).pins.active==null,"cold cancellation needs no manifest or activation");
    }
    static void inFlightAndCloseFailures()throws Exception {
        for(boolean writer:new boolean[]{false,true}){
            Setup s=new Setup();HistoryOperations.OperationHandle h=s.activate();CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);ExecutorService worker=Executors.newSingleThreadExecutor(task->{Thread thread=new Thread(task);thread.setDaemon(true);return thread;});AtomicBoolean actualClosed=new AtomicBoolean();
            Future<?> task=worker.submit(()->{try{
                if(writer){HistoryOperations.TrackedOutput out=s.ops.openWriter(h,()->new OutputStream(){public void write(int b){entered.countDown();await(release);}public void close(){actualClosed.set(true);}});out.write(1);out.close();}
                else{HistoryOperations.TrackedInput in=s.ops.openReader(h,()->new InputStream(){public int read(){entered.countDown();await(release);return 4;}public void close(){actualClosed.set(true);}});in.read();in.close();}
            }catch(IOException failure){throw new UncheckedIOException(failure);}});
            check(entered.await(5,TimeUnit.SECONDS),"in-flight delegate entered");CompletableFuture<HistoryOperations.Quiescence> q=s.ops.quiesce(s.ops.freeze(h)).toCompletableFuture();check(!q.isDone(),"in-flight work retains ownership");
            release.countDown();q.get(5,TimeUnit.SECONDS);check(actualClosed.get(),"proof follows actual resource closure");task.get(5,TimeUnit.SECONDS);worker.shutdown();
        }
        Setup failed=new Setup();HistoryOperations.OperationHandle h=failed.activate();AtomicInteger attempts=new AtomicInteger();HistoryOperations.TrackedInput input=failed.ops.openReader(h,()->new ByteArrayInputStream(MANIFEST){public void close()throws IOException{if(attempts.incrementAndGet()==1)throw new IOException("not closed");}});
        CompletableFuture<HistoryOperations.Quiescence> q=failed.ops.quiesce(failed.ops.freeze(h)).toCompletableFuture();rejects(input::close,"uncertain close failure");check(!q.isDone(),"close exception retains registration");input.close();check(q.isDone(),"successful owner retry releases resource");
        // A failed wrapper must clear only its own participant depth after signaling.
        failed.pins.close(q.get(),HistoryPinRecord.Outcome.CANCELLED,null);
    }
    static void metadataEpilogueAndAuditDrain()throws Exception {
        Setup s=new Setup();HistoryOperations.OperationHandle h=s.activate();CountDownLatch opened=new CountDownLatch(1),closeNow=new CountDownLatch(1),signaling=new CountDownLatch(1),finishSignal=new CountDownLatch(1);AtomicBoolean resourceClosed=new AtomicBoolean();ExecutorService worker=Executors.newSingleThreadExecutor(task->{Thread thread=new Thread(task);thread.setDaemon(true);return thread;});
        Future<?> task=worker.submit(()->{try{HistoryOperations.TrackedInput in=s.ops.openReader(h,()->new ByteArrayInputStream(MANIFEST){public void close(){resourceClosed.set(true);}});opened.countDown();await(closeNow);in.close();}catch(IOException failure){throw new UncheckedIOException(failure);}});
        check(opened.await(5,TimeUnit.SECONDS),"reader registered before freeze");CompletableFuture<HistoryOperations.Quiescence> q=s.ops.quiesce(s.ops.freeze(h)).toCompletableFuture();
        q.thenAccept(proof->{signaling.countDown();await(finishSignal);});closeNow.countDown();check(signaling.await(5,TimeUnit.SECONDS),"metadata listener holds wrapper epilogue");
        check(resourceClosed.get(),"no delegate work remains during proof signaling");s.pins.close(q.get(),HistoryPinRecord.Outcome.CANCELLED,null);finishSignal.countDown();task.get(5,TimeUnit.SECONDS);worker.shutdown();
        Setup audit=new Setup();HistoryOperations.OperationHandle active=audit.activate();HistoryOperations.TrackedInput reader=audit.ops.openReader(active,()->input());
        java.nio.file.Path close=audit.f.parent.resolve("history_control/pin-"+audit.id+".close");
        java.nio.file.Files.write(close,HistoryPinRecord.close(inspect(audit.f).pins.active,HistoryPinRecord.Outcome.CANCELLED));
        rejects(audit.pins::reconcile,"raw receipt cannot release registered source");CompletableFuture<HistoryOperations.AuditQuiescence> pending=audit.ops.prepareClosedAudits().toCompletableFuture();check(!pending.isDone(),"audit discovery freezes and actually drains");
        rejects(()->audit.activate(),"raw CLOSED seals consumer admission");reader.close();check(pending.isDone(),"audit proof after owner close");
        java.nio.file.Files.delete(audit.f.parent.resolve("store/"+audit.f.original.id+".snapshot"));audit.pins.reconcile();check(!HistoryPins.pruneBlocked(audit.f.store.storageRoot(),inspect(audit.f)),"committed audit never rereads retired source");
        synchronized(HistoryStorage.monitorFor(audit.f.store.storageRoot())){try(HistoryStorage.MutationScope lease=HistoryStorage.administration(audit.f.store.storageRoot())){check(audit.ops.tryClosedAuditsNowLocked()!=null,"closed-only fast path under caller lease has no future signaling");}}
    }
    static Object gateLock(HistoryOperations operations){try{java.lang.reflect.Field lock=HistoryOperations.class.getDeclaredField("gateLock");lock.setAccessible(true);return lock.get(operations);}catch(ReflectiveOperationException failure){throw new AssertionError(failure);}}
    static void await(CountDownLatch latch){try{if(!latch.await(5,TimeUnit.SECONDS))throw new AssertionError("latch timeout");}catch(InterruptedException failure){throw new AssertionError(failure);}}
    public static void main(String[] args)throws Exception{readersAndCallbacks();countedOpeningAndWrite();callbackAndOuterLock();epochs();inFlightAndCloseFailures();metadataEpilogueAndAuditDrain();System.out.println("Operation quiescence: "+checks+" checks PASS");}
}
