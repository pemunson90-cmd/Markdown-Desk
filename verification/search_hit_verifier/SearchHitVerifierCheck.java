package com.example.markdowndesk;
import android.os.*;import java.lang.reflect.*;import java.security.*;import java.util.*;import java.util.concurrent.*;import java.util.concurrent.atomic.*;
public final class SearchHitVerifierCheck {
 static int checks;static void check(boolean yes,String why){checks++;if(!yes)throw new AssertionError(why);}
 static Object field(Object target,String name)throws Exception{Field f=(target==null?SearchHitVerifier.class:target.getClass()).getDeclaredField(name);f.setAccessible(true);return f.get(target);}
 static ExecutorService pool()throws Exception{return(ExecutorService)field(null,"worker");}static Handler main()throws Exception{return(Handler)field(null,"main");}
 static void done()throws Exception{long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);while(true){try{pool().submit(()->{}).get(5,TimeUnit.SECONDS);return;}catch(RejectedExecutionException full){if(System.nanoTime()>end)throw full;Thread.sleep(1);}}}
 static void await(CountDownLatch latch)throws Exception{if(!latch.await(5,TimeUnit.SECONDS))throw new AssertionError("deadline");}
 static String hex(byte[]bytes){StringBuilder s=new StringBuilder();for(byte b:bytes)s.append(String.format(Locale.ROOT,"%02x",b&255));return s.toString();}
 static final class Editable implements CharSequence {String text;int copies;Editable(String s){text=s;}public int length(){return text.length();}public char charAt(int i){return text.charAt(i);}public CharSequence subSequence(int a,int b){return text.subSequence(a,b);}public String toString(){copies++;return text;}}
 static SearchHitVerifier.Ticket start(CharSequence text,boolean bom,AtomicReference<SearchHitVerifier.Outcome> result){return SearchHitVerifier.tryVerify(()->new SearchHitVerifier.Capture(text,bom),result::set);}
 static void oracle(String text,boolean bom)throws Exception {AtomicReference<SearchHitVerifier.Outcome> result=new AtomicReference<>();SearchHitVerifier.Ticket t=start(new Editable(text),bom,result);done();check(field(t,"capture")==null&&result.get()==null&&main().tasks.size()==1,"source capture gone before queued receipt");main().drain();byte[] expected=DocumentIO.encodeStrict(text,bom);check(result.get().problem==null&&result.get().result.byteLength==expected.length&&result.get().result.sha256.equals(hex(MessageDigest.getInstance("SHA-256").digest(expected))),"actual DocumentIO.encodeStrict hash/length oracle");check(field(null,"owner")==null,"oracle retirement");}
 static void invalid(CharSequence text,boolean bom)throws Exception {try{start(text,bom,new AtomicReference<>());throw new AssertionError("accepted");}catch(IllegalArgumentException expected){checks++;}check(field(null,"owner")==null,"invalid capture unused owner released");}
 static void normal()throws Exception {
  for(String text:List.of("","\ufeff","a\r\nb\rc\n","😀 é שלום 中文","x".repeat(8191)+"😀tail"))for(boolean bom:new boolean[]{false,true})oracle(text,bom);
  Editable snapshot=new Editable("first");AtomicReference<SearchHitVerifier.Outcome> result=new AtomicReference<>();SearchHitVerifier.Ticket t=start(snapshot,false,result);snapshot.text="later";done();main().drain();check(result.get().result.sha256.equals(hex(MessageDigest.getInstance("SHA-256").digest(DocumentIO.encodeStrict("first",false))))&&snapshot.copies==1,"immutable snapshot once");
  t=start("queued",false,result);done();AtomicInteger suppliers=new AtomicInteger();check(SearchHitVerifier.tryVerify(()->{suppliers.incrementAndGet();return null;},x->{})==null&&suppliers.get()==0,"BUSY lazy supplier");t.cancel();check(field(t,"callback")==null&&field(null,"owner")==t,"queued cancellation holds physical receipt");main().drain();check(field(null,"owner")==null,"discard retires");
  t=SearchHitVerifier.tryVerify(()->new SearchHitVerifier.Capture("x",false),x->{check(SearchHitVerifier.tryVerify(()->null,y->{})==null,"consumer reentrant BUSY");throw new IllegalStateException("consumer");});done();try{main().drain();throw new AssertionError();}catch(IllegalStateException expected){checks++;}check(field(null,"owner")==null&&field(t,"callback")==null,"consumer finally releases");
  Handler.reject=true;t=start("x",false,result);done();Handler.reject=false;check(field(null,"owner")==null&&field(t,"callback")==null&&main().tasks.isEmpty(),"rejected delivery no phantom owner");
  invalid("\ud800",false);invalid("\udc00",false);Editable oversized=new Editable("x".repeat(DocumentIO.MAX_DOCUMENT_BYTES+1));invalid(oversized,false);check(oversized.copies==0,"UTF16 oversized before copy");
  Editable byteOver=new Editable("é".repeat(DocumentIO.MAX_DOCUMENT_BYTES/2+1));invalid(byteOver,false);check(byteOver.copies==0,"UTF8 oversized before copy");
  Editable bomOver=new Editable("x".repeat(DocumentIO.MAX_DOCUMENT_BYTES-2));invalid(bomOver,true);check(bomOver.copies==0,"BOM included before copy");
  oracle("x".repeat(DocumentIO.MAX_DOCUMENT_BYTES),false);oracle("x".repeat(DocumentIO.MAX_DOCUMENT_BYTES-3),true);
  for(String returned:new String[]{"\ud800","é".repeat(DocumentIO.MAX_DOCUMENT_BYTES/2+1)}){
   CharSequence misleading=new CharSequence(){public int length(){return 1;}public char charAt(int i){return 'x';}public CharSequence subSequence(int a,int b){return "x";}public String toString(){return returned;}};
   result.set(null);start(misleading,false,result);done();main().drain();check(result.get()!=null&&result.get().problem==SearchHitVerifier.Problem.HASH_FAILED&&field(null,"owner")==null,"worker independently checks immutable snapshot Unicode/byte cap");
  }
  AtomicReference<Throwable> wrongThread=new AtomicReference<>();Thread thread=new Thread(()->{try{start("x",false,new AtomicReference<>());}catch(Throwable error){wrongThread.set(error);}});thread.start();thread.join(5000);check(!thread.isAlive()&&wrongThread.get() instanceof IllegalStateException&&field(null,"owner")==null,"off-main admission rejected before capture");
  for(boolean atCopy:new boolean[]{false,true}){
   AssertionError original=new AssertionError("finite capture");CharSequence source=new CharSequence(){public int length(){return 1;}public char charAt(int i){if(!atCopy)throw original;return 'x';}public CharSequence subSequence(int a,int b){throw original;}public String toString(){throw original;}};
   try{start(source,false,result);throw new AssertionError();}catch(AssertionError expected){check(expected==original,"capture Error original preserved");}check(field(null,"owner")==null,"capture Error admission released");
  }
 }
 public static final class FaultDigest extends MessageDigestSpi {
  static String mode;static CountDownLatch entered,release;static final AssertionError fatal=new AssertionError("finite hash fatal");final MessageDigest delegate;
  public FaultDigest(){try{delegate=MessageDigest.getInstance("SHA-256","SUN");}catch(Exception e){throw new AssertionError(e);}}
  private void seam(){if(mode.equals("fatal"))throw fatal;if(mode.equals("runtime"))throw new ProviderException("hash failure");if(mode.equals("blocked")){entered.countDown();try{await(release);}catch(Exception e){throw new AssertionError(e);}}}
  protected void engineUpdate(byte b){seam();delegate.update(b);}protected void engineUpdate(byte[]b,int o,int n){seam();delegate.update(b,o,n);}protected byte[]engineDigest(){if(mode.equals("terminal")){entered.countDown();try{await(release);}catch(Exception e){throw new AssertionError(e);}}return delegate.digest();}protected void engineReset(){delegate.reset();}
 }
 static void isolated(String mode)throws Exception {
  if(mode.equals("reject")){pool().shutdownNow();try{start("x",false,new AtomicReference<>());throw new AssertionError();}catch(RejectedExecutionException expected){checks++;}check(field(null,"owner")==null,"rejected worker proven unused");return;}
  if(mode.equals("supplier")){AssertionError original=new AssertionError("supplier");try{SearchHitVerifier.tryVerify(()->{throw original;},x->{});throw new AssertionError();}catch(AssertionError expected){check(expected==original,"supplier Error original");}check(field(null,"owner")==null,"supplier Error unused release");return;}
  Provider provider=new Provider("VerifierFault","1.0","finite verification boundary"){{put("MessageDigest.SHA-256",FaultDigest.class.getName());}};Security.insertProviderAt(provider,1);FaultDigest.mode=mode;FaultDigest.entered=new CountDownLatch(1);FaultDigest.release=new CountDownLatch(1);
  CountDownLatch fatalSeen=new CountDownLatch(1);AtomicReference<Throwable> fatal=new AtomicReference<>();Thread.setDefaultUncaughtExceptionHandler((thread,e)->{fatal.set(e);fatalSeen.countDown();});
  AtomicReference<SearchHitVerifier.Outcome> result=new AtomicReference<>();SearchHitVerifier.Ticket t=start("x".repeat(100000),false,result);
  if(mode.equals("fatal")){await(fatalSeen);done();check(fatal.get()==FaultDigest.fatal&&result.get()==null&&field(null,"owner")==null&&field(t,"capture")==null&&field(t,"callback")==null,"worker fatal no source/UI/owner retained");}
  else if(mode.equals("runtime")){done();main().drain();check(result.get()!=null&&result.get().problem==SearchHitVerifier.Problem.HASH_FAILED&&field(null,"owner")==null,"bounded ordinary hash failure receipt");}
  else {await(FaultDigest.entered);try{check(field(t,"capture")==null,"worker consumed snapshot");t.cancel();check(field(t,"callback")==null&&SearchHitVerifier.tryVerify(()->{throw new AssertionError("BUSY recapture");},x->{})==null,"cancel held hash retains physical slot");}finally{FaultDigest.release.countDown();}done();main().drain();check(result.get()==null&&field(null,"owner")==null&&main().tasks.isEmpty(),"terminal cancellation no normal receipt/source retention");}
 }
 public static void main(String[]args)throws Exception{Looper.getMainLooper();try{if(args.length==0)normal();else isolated(args[0]);System.out.println("PASS SearchHitVerifier "+checks+" checks "+Arrays.toString(args));}finally{pool().shutdownNow();check(pool().awaitTermination(5,TimeUnit.SECONDS),"worker terminates");}}
}
