package com.example.markdowndesk;
import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;
import android.os.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
public final class WorkspaceDirectoryLoaderCheck {
 static int checks;
 static void check(boolean b,String why){checks++;if(!b)throw new AssertionError(why);}
 interface Action{void run()throws Exception;}
 static void rejects(Action action,Class<? extends Throwable> kind)throws Exception{try{action.run();throw new AssertionError("accepted: "+kind);}catch(Throwable e){if(!kind.isInstance(e))throw e;checks++;}}
 static Object field(Object target,String name)throws Exception{Field f=(target==null?WorkspaceDirectoryLoader.class:target.getClass()).getDeclaredField(name);f.setAccessible(true);return f.get(target);}
 static ExecutorService worker()throws Exception{return (ExecutorService)field(null,"worker");}
 static void workerDone()throws Exception{
  long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);
  while(true)try{worker().submit(()->{}).get(5,TimeUnit.SECONDS);return;}
  catch(RejectedExecutionException full){if(System.nanoTime()>deadline)throw full;Thread.sleep(1);}
 }
 static void await(CountDownLatch latch)throws InterruptedException{if(!latch.await(5,TimeUnit.SECONDS))throw new AssertionError("bounded fixture latch expired");}
 static final class MainQueue implements Executor {
  final ArrayDeque<Runnable> tasks=new ArrayDeque<>();boolean reject;
  public synchronized void execute(Runnable task){if(reject)throw new RejectedExecutionException("fixture");tasks.add(task);check(tasks.size()==1,"at most one queued delivery");}
  void drain(){Runnable task;synchronized(this){task=tasks.poll();}if(task!=null)task.run();}
  synchronized int size(){return tasks.size();}
 }
 static final class Rows implements Cursor {
  int closes,count,at=-1;boolean closeFail;java.lang.Error bodyFatal,closeFatal;CountDownLatch closing,releaseClose;
  public boolean moveToNext(){if(bodyFatal!=null)throw bodyFatal;return ++at<count;}
  public int getColumnIndex(String name){return Arrays.asList("document_id","_display_name","mime_type","_size","last_modified","flags").indexOf(name);}
  public boolean isNull(int column){return column>=3;}public String getString(int column){return column==0?"id"+at:column==1?"file"+at:"text/plain";}public long getLong(int column){return 0;}
  public void close(){closes++;if(closeFatal!=null)throw closeFatal;if(closeFail)throw new IllegalStateException("cursor close");if(closing!=null){closing.countDown();try{await(releaseClose);}catch(InterruptedException e){throw new AssertionError(e);}}}
 }
 static final class Provider extends ContentResolver {
  final Rows rows=new Rows();final CountDownLatch entered=new CountDownLatch(1);CountDownLatch release;CancellationSignal signal;int calls;
  public Cursor query(Uri uri,String[] p,String selection,String[] args,String order,CancellationSignal signal){this.signal=signal;calls++;entered.countDown();if(release!=null)try{await(release);}catch(InterruptedException e){throw new AssertionError(e);}return rows;}
 }
 static WorkspaceTreeReader reader(Provider p){return new WorkspaceTreeReader(p,Uri.parse("content://provider/tree/root"));}
 static WorkspaceDirectoryLoader.Ticket start(WorkspaceTreeReader r,MainQueue q,AtomicInteger callbacks){return WorkspaceDirectoryLoader.tryLoad(r,r.root(),q,p->{check(p.state==WorkspaceTreeReader.State.COMPLETE,"real reader Page delivered");callbacks.incrementAndGet();});}
 static void released()throws Exception{check(field(null,"owner")==null,"physical owner released");}
 static void blockedCancellation()throws Exception {
  Provider p=new Provider();p.release=new CountDownLatch(1);WorkspaceTreeReader r=reader(p);MainQueue q=new MainQueue();AtomicInteger callbacks=new AtomicInteger();
  WorkspaceDirectoryLoader.Ticket ticket=start(r,q,callbacks);await(p.entered);
  try{
   ticket.cancel();rejects(()->p.signal.throwIfCanceled(),OperationCanceledException.class);
   check(field(ticket,"callback")==null&&field(ticket,"deliveryExecutor")==null,"disposed callback/executor cleared before physical return");
   for(int i=0;i<40;i++)check(start(reader(new Provider()),q,callbacks)==null,"new Activity style attempts BUSY while retired provider blocked");
   ExecutorService safety=Executors.newSingleThreadExecutor();try{check(safety.submit(()->"saved").get(1,TimeUnit.SECONDS).equals("saved"),"independent safety work not blocked");}finally{safety.shutdownNow();}
   check(p.calls==1&&q.size()==0,"one physical query/no delivery yet");
  }finally{p.release.countDown();}
  workerDone();check(p.rows.closes==1&&callbacks.get()==0&&q.size()==0,"cancelled result closed and discarded");released();
 }
 static void queuedCancellation()throws Exception {
  MainQueue q=new MainQueue();AtomicInteger callbacks=new AtomicInteger();Provider p=new Provider();WorkspaceTreeReader r=reader(p);
  WorkspaceDirectoryLoader.Ticket ticket=start(r,q,callbacks);workerDone();check(q.size()==1&&field(ticket,"page")!=null,"one bounded Page queued");
  check(start(reader(new Provider()),q,callbacks)==null,"worker handoff does not free physical slot");ticket.cancel();
  check(field(ticket,"callback")==null&&field(ticket,"deliveryExecutor")==null,"queued cancellation drops Activity closure");
  check(start(reader(new Provider()),q,callbacks)==null,"cancelled queued delivery still BUSY");q.drain();released();check(callbacks.get()==0&&field(ticket,"page")==null,"queued obsolete Page consumed/dropped");
 }
 static void cursorCloseLifetime()throws Exception {
  Provider p=new Provider();p.rows.closing=new CountDownLatch(1);p.rows.releaseClose=new CountDownLatch(1);MainQueue q=new MainQueue();AtomicInteger calls=new AtomicInteger();WorkspaceTreeReader r=reader(p);
  WorkspaceDirectoryLoader.Ticket ticket=start(r,q,calls);await(p.rows.closing);
  try{ticket.cancel();check(start(reader(new Provider()),q,calls)==null,"query return alone cannot release blocked physical cursor.close");}finally{p.rows.releaseClose.countDown();}
  workerDone();released();check(q.size()==0&&calls.get()==0,"closed cancelled cursor not delivered");
 }
 static void prequeryCancellation()throws Exception {
  CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);worker().execute(()->{entered.countDown();try{await(release);}catch(InterruptedException e){throw new AssertionError(e);}});await(entered);
  Provider p=new Provider();MainQueue q=new MainQueue();WorkspaceTreeReader r=reader(p);AtomicInteger calls=new AtomicInteger();WorkspaceDirectoryLoader.Ticket ticket=start(r,q,calls);
  try{ticket.cancel();check(start(reader(new Provider()),q,calls)==null,"unconsumed cancelled worker task still owns slot");}finally{release.countDown();}
  workerDone();released();check(p.calls==0&&q.size()==0,"cancelled queued task never queries");
 }
 static void cancellationListenerAndDefaults()throws Exception {
  Provider blocked=new Provider();blocked.release=new CountDownLatch(1);WorkspaceTreeReader tree=reader(blocked);MainQueue queue=new MainQueue();AtomicInteger callbacks=new AtomicInteger();
  WorkspaceDirectoryLoader.Ticket ticket=start(tree,queue,callbacks);await(blocked.entered);
  blocked.signal.setOnCancelListener(()->{
   try{check(field(ticket,"callback")==null&&field(ticket,"deliveryExecutor")==null,"UI closure dropped before provider cancellation");blocked.release.countDown();workerDone();released();}
   catch(Exception failure){throw new AssertionError("provider listener must not run under broker monitor",failure);}
  });
  try{ticket.cancel();}finally{blocked.release.countDown();}
  check(callbacks.get()==0,"blocking cancellation listener never resurrects callback");
  Provider large=new Provider();large.rows.count=101;WorkspaceTreeReader r=reader(large);
  WorkspaceDirectoryLoader.Ticket pending=WorkspaceDirectoryLoader.tryLoad(r,r.root(),queue,page->{check(page.state==WorkspaceTreeReader.State.LIMITED&&page.entries.size()==100&&page.scannedRows==100&&page.retainedTextUnits<=65536,"actual defaults bound broker Page");callbacks.incrementAndGet();});
  workerDone();AtomicReference<Throwable> background=new AtomicReference<>();Thread thread=new Thread(()->{try{pending.cancel();}catch(Throwable failure){background.set(failure);}});thread.start();thread.join(2000);check(background.get() instanceof IllegalStateException,"main-thread cancel enforced");queue.drain();released();check(callbacks.get()==1&&large.rows.closes==1,"bounded Page delivered and cursor closed");
 }
 static void callbacksAndRejections()throws Exception {
  Provider p=new Provider();WorkspaceTreeReader r=reader(p);MainQueue q=new MainQueue();AtomicInteger calls=new AtomicInteger();
  WorkspaceDirectoryLoader.Ticket normal=start(r,q,calls);workerDone();q.drain();released();check(calls.get()==1&&field(normal,"reader")==null&&field(normal,"page")==null&&field(normal,"callback")==null,"normal references cleared");normal.cancel();released();
  WorkspaceDirectoryLoader.tryLoad(r,r.root(),q,page->{check(WorkspaceDirectoryLoader.tryLoad(r,r.root(),q,x->{})==null,"callback keeps owner until finally");throw new IllegalStateException("consumer fixture");});workerDone();rejects(q::drain,IllegalStateException.class);released();
  q.reject=true;check(start(r,q,calls)!=null,"completion rejection starts real query");workerDone();released();check(calls.get()==1&&q.size()==0,"rejected completion discarded without consumer");q.reject=false;
  start(r,q,calls);workerDone();q.drain();released();check(calls.get()==2,"retry after rejected completion");
  WorkspaceTreeReader foreign=reader(new Provider());WorkspaceDirectoryLoader.tryLoad(r,foreign.root(),q,page->{throw new AssertionError("invalid handle delivered");});workerDone();released();check(q.size()==0,"programmer invalid handle retires without fabricated Page");
  rejects(()->WorkspaceDirectoryLoader.tryLoad(null,r.root(),q,x->{}),IllegalArgumentException.class);
  AtomicReference<Throwable> background=new AtomicReference<>();Thread thread=new Thread(()->{try{start(r,q,calls);}catch(Throwable failure){background.set(failure);}});thread.start();thread.join(2000);check(background.get() instanceof IllegalStateException,"main-thread admission enforced");
 }
 static void poisoned(String mode)throws Exception {
  Provider p=new Provider();p.rows.closeFail=!mode.equals("fatal-closed");MainQueue q=new MainQueue();AtomicInteger callbacks=new AtomicInteger();WorkspaceTreeReader r=reader(p);
  boolean fatal=mode.startsWith("fatal");java.lang.Error original=new AssertionError("original provider body Error");CountDownLatch uncaught=new CountDownLatch(1);AtomicReference<Throwable> thrown=new AtomicReference<>();
  if(fatal){p.rows.bodyFatal=original;if(mode.equals("fatal-close-error")){p.rows.closeFail=false;p.rows.closeFatal=new AssertionError("provider close Error");}Thread.setDefaultUncaughtExceptionHandler((thread,error)->{thrown.set(error);uncaught.countDown();});}
  if(mode.equals("poison-reject"))q.reject=true;
  if(mode.equals("poison-cancel")){p.release=new CountDownLatch(1);}
  WorkspaceDirectoryLoader.Ticket ticket=WorkspaceDirectoryLoader.tryLoad(r,r.root(),q,page->{check(page.state==WorkspaceTreeReader.State.FAILED&&page.closeDisposition()==WorkspaceTreeReader.CloseDisposition.UNCERTAIN,"uncertain failure notified once");callbacks.incrementAndGet();});
  if(mode.equals("poison-cancel")){await(p.entered);ticket.cancel();p.release.countDown();}
  workerDone();if(fatal){await(uncaught);check(thrown.get()==original,"fatal body Error rethrown unchanged");}
  if(mode.equals("fatal-closed")){released();check(field(ticket,"listing")==null,"successful close releases fatal receipt");return;}
  check(start(reader(new Provider()),q,callbacks)==null,"uncertain cursor retains process physical owner");
  if(!fatal&&!mode.equals("poison-reject")&&!mode.equals("poison-cancel")){q.drain();check(callbacks.get()==1,"one normal failed Page delivery");}
  ticket.cancel();q.drain();check(start(reader(new Provider()),q,callbacks)==null,"cancel/delivery/rejection cannot reset poisoned owner");
  check(field(ticket,"reader")==null&&field(ticket,"directory")==null&&field(ticket,"page")==null&&field(ticket,"callback")==null&&field(ticket,"deliveryExecutor")==null&&field(ticket,"listing")!=null,"poison retains receipt only, drops UI/query/Page graph");
  Object receipt=field(ticket,"listing");check(field(receipt,"reader")==null&&field(receipt,"directory")==null&&field(receipt,"limits")==null&&field(receipt,"cancellation")==null&&field(receipt,"cursor")!=null,"receipt retains actual cursor not request/Page graph");
  check(p.calls==1&&p.rows.closes==1,"no retry query or close");
 }
 public static void main(String[] args)throws Exception {
  Looper.getMainLooper();ExecutorService pool=worker();
  try{
   if(args.length>0&&args[0].equals("worker-reject")){pool.shutdown();Provider p=new Provider();WorkspaceTreeReader r=reader(p);MainQueue q=new MainQueue();rejects(()->start(r,q,new AtomicInteger()),RejectedExecutionException.class);released();check(p.calls==0&&q.size()==0,"worker refusal has no query/delivery");}
   else if(args.length>0){poisoned(args[0]);}
   else{blockedCancellation();queuedCancellation();cursorCloseLifetime();prequeryCancellation();cancellationListenerAndDefaults();callbacksAndRejections();}
   System.out.println("PASS workspace directory loader: "+checks+" assertions "+Arrays.toString(args));
  }finally{pool.shutdownNow();check(pool.awaitTermination(5,TimeUnit.SECONDS),"test worker terminated");}
 }
}
