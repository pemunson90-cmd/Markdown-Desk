package com.example.markdowndesk;
import android.content.*;import android.net.Uri;import android.os.*;import android.database.Cursor;
import java.io.*;import java.lang.reflect.*;import java.util.*;import java.util.concurrent.*;import java.util.concurrent.atomic.*;
final class WorkspaceSearchLoaderCheck {
 static int checks;static final Uri TREE=Uri.parse("content://p/tree/root");
 static void check(boolean yes,String why){checks++;if(!yes)throw new AssertionError(why);}
 static Object field(Object target,String name)throws Exception{Field f=(target==null?WorkspaceSearchLoader.class:target.getClass()).getDeclaredField(name);f.setAccessible(true);return f.get(target);}
 static ExecutorService pool()throws Exception{return(ExecutorService)field(null,"worker");}static Handler main()throws Exception{return(Handler)field(null,"main");}
 static void done()throws Exception{long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);while(true){try{pool().submit(()->{}).get(5,TimeUnit.SECONDS);return;}catch(RejectedExecutionException full){if(System.nanoTime()>deadline)throw full;Thread.sleep(1);}}}static void await(CountDownLatch latch)throws Exception{if(!latch.await(5,TimeUnit.SECONDS))throw new AssertionError("deadline");}
 static final class App extends Context {final WorkspaceSearchCheck.Provider provider;int resolutions;App(WorkspaceSearchCheck.Provider p){provider=p;}public ContentResolver getContentResolver(){resolutions++;return provider;}}
 static class Blocking extends WorkspaceSearchCheck.Provider {
  final String phase;final CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);CancellationSignal observed;
  Blocking(String phase){this.phase=phase;file("root","f","needle");}
  void block(){entered.countDown();try{await(release);}catch(Exception e){throw new AssertionError(e);}}
  public Cursor query(Uri u,String[]p,String s,String[]a,String o,CancellationSignal c){observed=c;if(phase.equals("query"))block();return super.query(u,p,s,a,o,c);}
  public InputStream openInputStream(Uri u)throws FileNotFoundException{if(phase.equals("open"))block();InputStream raw=super.openInputStream(u);return new FilterInputStream(raw){public int read(byte[]b,int o,int n)throws IOException{if(phase.equals("read"))block();return super.read(b,o,n);}};}
 }
 static WorkspaceSearchLoader.Ticket start(App app,AtomicReference<WorkspaceSearchLoader.Delivery> delivery){return WorkspaceSearchLoader.trySearch(app,TREE,"needle",WorkspaceLiteralSearch.Limits.defaults(),delivery::set);}
 static void normal()throws Exception {
  WorkspaceSearchCheck.Provider p=new WorkspaceSearchCheck.Provider();p.file("root","f","needle");App app=new App(p);AtomicReference<WorkspaceSearchLoader.Delivery> result=new AtomicReference<>();
  Context activity=new Context(){public Context getApplicationContext(){return app;}public ContentResolver getContentResolver(){throw new AssertionError("Activity resolver retained");}};
  WorkspaceSearchLoader.Ticket t=WorkspaceSearchLoader.trySearch(activity,TREE,"needle",WorkspaceLiteralSearch.Limits.defaults(),result::set);done();
  check(result.get()==null&&field(null,"owner")==t&&main().tasks.size()==1,"one queued receipt still owns slot");int resolutions=app.resolutions;check(start(app,result)==null&&app.resolutions==resolutions,"BUSY before resolver/Attempt preparation");
  main().drain();check(result.get()!=null&&result.get().result.hits.size()==1&&result.get().problem==null&&app.resolutions==1,"real backend application resolver result");check(field(null,"owner")==null&&field(t,"callback")==null&&field(t,"attempt")==null,"closed delivery releases UI and attempt");
  for(String phase:List.of("query","open","read")){
   Blocking blocked=new Blocking(phase);App blockedApp=new App(blocked);result.set(null);t=start(blockedApp,result);await(blocked.entered);
   check(field(t,"attempt")!=null,"Attempt retained before blocking I/O "+phase);final WorkspaceSearchLoader.Ticket pending=t;
   blocked.observed.setOnCancelListener(()->{try{check(field(pending,"callback")==null&&!Thread.holdsLock(field(null,"guard")),"callback dropped before cancel outside guard");}catch(Exception e){throw new AssertionError(e);}});
   t.cancel();check(start(new App(new WorkspaceSearchCheck.Provider()),new AtomicReference<>())==null,"cancelled physical slot BUSY "+phase);
   ExecutorService safety=Executors.newSingleThreadExecutor();try{check(safety.submit(()->true).get(1,TimeUnit.SECONDS),"safety executor progresses");}finally{safety.shutdownNow();}
   WorkspaceSearchCheck.Provider browse=new WorkspaceSearchCheck.Provider();WorkspaceTreeReader reader=new WorkspaceTreeReader(browse,TREE);AtomicReference<WorkspaceTreeReader.Page> page=new AtomicReference<>();
   WorkspaceDirectoryLoader.tryLoad(reader,reader.root(),r->mainUnchecked().post(r),page::set);directoryDone();main().drain();check(page.get()!=null,"actual directory broker independent "+phase);
   blocked.release.countDown();done();main().drain();check(result.get()==null&&field(null,"owner")==null,"ignored cancellation retires only after physical return "+phase);
  }
  WorkspaceSearchCheck.Provider ordinary=new WorkspaceSearchCheck.Provider();ordinary.file("root","f","needle");ordinary.failEOF=true;result.set(null);start(new App(ordinary),result);done();main().drain();check(result.get().result.state==WorkspaceLiteralSearch.State.FAILED&&field(null,"owner")==null,"ordinary all-closed failure releases");
  t=start(app,result);done();t.cancel();check(field(t,"callback")==null&&field(null,"owner")==t,"queued cancellation holds owner");main().drain();check(field(null,"owner")==null,"discard consumes queued receipt");
  t=WorkspaceSearchLoader.trySearch(app,TREE,"needle",WorkspaceLiteralSearch.Limits.defaults(),x->{check(start(app,new AtomicReference<>())==null,"reentrant delivery BUSY");throw new IllegalStateException("consumer");});done();try{main().drain();throw new AssertionError();}catch(IllegalStateException expected){checks++;}check(field(null,"owner")==null&&field(t,"callback")==null,"consumer throw finally release");
  Handler.reject=true;t=start(app,result);done();Handler.reject=false;check(field(null,"owner")==null&&field(t,"callback")==null&&main().tasks.isEmpty(),"Handler rejection no phantom receipt");
  try{WorkspaceSearchLoader.trySearch(app,TREE,"",WorkspaceLiteralSearch.Limits.defaults(),x->{});throw new AssertionError();}catch(IllegalArgumentException expected){checks++;}check(field(null,"owner")==null,"pre-I/O validation releases");
 }
 static Handler mainUnchecked(){try{return main();}catch(Exception e){throw new AssertionError(e);}}
 static void directoryDone()throws Exception{Field f=WorkspaceDirectoryLoader.class.getDeclaredField("worker");f.setAccessible(true);ExecutorService executor=(ExecutorService)f.get(null);long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);while(true){try{executor.submit(()->{}).get(5,TimeUnit.SECONDS);return;}catch(RejectedExecutionException full){if(System.nanoTime()>deadline)throw full;Thread.sleep(1);}}}
 static void isolated(String mode)throws Exception {
  if(mode.equals("reject")){pool().shutdownNow();App app=new App(new WorkspaceSearchCheck.Provider());try{start(app,new AtomicReference<>());throw new AssertionError();}catch(RejectedExecutionException expected){checks++;}check(field(null,"owner")==null,"worker refusal unused release");return;}
  WorkspaceSearchCheck.Provider p=new WorkspaceSearchCheck.Provider();p.file("root","f","needle");boolean fatal=mode.startsWith("fatal");boolean closed=mode.equals("fatal-closed");
  if(mode.contains("cursor")){p.cursorCloseFail=true;}else if(!closed)p.closeFail=true;
  AssertionError original=new AssertionError("finite body fatal");if(fatal){if(mode.contains("cursor"))p.cursorBodyFatal=original;else p.fileBodyFatal=original;}if(mode.equals("fatal-cursor-close-error")){p.cursorCloseFail=false;p.cursorCloseFatal=new AssertionError("finite cursor close fatal");}
  CountDownLatch fatalSeen=new CountDownLatch(1);AtomicReference<Throwable> error=new AtomicReference<>();Thread.setDefaultUncaughtExceptionHandler((thread,e)->{error.set(e);fatalSeen.countDown();});
  AtomicReference<WorkspaceSearchLoader.Delivery> result=new AtomicReference<>();WorkspaceSearchLoader.Ticket t=mode.equals("cursor-consumer-throws")?WorkspaceSearchLoader.trySearch(new App(p),TREE,"needle",WorkspaceLiteralSearch.Limits.defaults(),x->{throw new IllegalStateException("uncertain consumer");}):start(new App(p),result);if(fatal)await(fatalSeen);done();
  if(mode.contains("cancel"))t.cancel();
  try{main().drain();if(mode.equals("cursor-consumer-throws"))throw new AssertionError("consumer not invoked");}catch(IllegalStateException expected){if(!mode.equals("cursor-consumer-throws"))throw expected;checks++;}
  check(field(t,"callback")==null,"no UI callback in retired/permanent owner");
  if(fatal){check(error.get()==original&&result.get()==null,"original fatal Error propagated no fake receipt");}
  else check(result.get()==null||result.get().result.reasons.contains(WorkspaceLiteralSearch.Reason.CLOSE_UNCERTAIN),"actual uncertain reason");
  check((field(null,"owner")==null)==closed,"CLOSED versus UNCERTAIN physical admission");
  if(!closed){check(field(t,"attempt")!=null,"minted uncertain Attempt retained");check(start(new App(new WorkspaceSearchCheck.Provider()),new AtomicReference<>())==null,"new screen cannot reset uncertainty");}
 }
 public static void main(String[]args)throws Exception{Looper.getMainLooper();try{if(args.length==0)normal();else{if(args[0].contains("reject-delivery"))Handler.reject=true;isolated(args[0]);}System.out.println("PASS search loader "+checks+" checks "+Arrays.toString(args));}finally{pool().shutdownNow();pool().awaitTermination(5,TimeUnit.SECONDS);Field f=WorkspaceDirectoryLoader.class.getDeclaredField("worker");f.setAccessible(true);((ExecutorService)f.get(null)).shutdownNow();}}
}
