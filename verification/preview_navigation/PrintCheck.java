package com.example.markdowndesk;

import android.app.Activity;
import android.os.*;
import android.print.*;
import android.webkit.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;

/** Exact helper and extracted action/lifecycle; Android/WebView/framework faults are modeled. */
public final class PrintCheck {
    static int checks,cases;static ExecutorService worker;static Handler delivery;
    static void ok(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
    static void same(Object a,Object b,String why){ok(a==b,why);}
    static Object field(String name)throws Exception{Field f=DocumentPrint.class.getDeclaredField(name);f.setAccessible(true);return f.get(null);}
    static void reset()throws Exception{
        // Only test isolation may discard a deliberately fail-closed owner. Production never resets it.
        Field f=DocumentPrint.class.getDeclaredField("owner");f.setAccessible(true);f.set(null,null);
        delivery.removeCallbacksAndMessages(null);WebView.ALL.clear();SystemClock.now=0;WebView.failCreate=false;WebView.onCreate=null;
    }
    static MainActivity activity(){MainActivity a=new MainActivity();a.service=new PrintManager();return a;}
    static PrintManager manager(MainActivity a){return (PrintManager)a.service;}
    static void render()throws Exception{worker.submit(()->{}).get(10,TimeUnit.SECONDS);delivery.drain();}
    static WebView view(){return WebView.ALL.get(WebView.ALL.size()-1);}
    static PrintDocumentAdapter ready(MainActivity a)throws Exception{a.requestPrint();render();view().ready();return manager(a).adapters.get(0);}
    static void run(String name,Test body)throws Exception{reset();cases++;body.run();System.out.println("PASS "+name);}
    interface Test{void run()throws Exception;}
    public static void main(String[] args)throws Exception{
        worker=(ExecutorService)field("worker");delivery=(Handler)field("main");
        try {
        run("captured source/title, isolated DOM, unchanged modes",()->{
            for(int mode=0;mode<3;mode++){
                MainActivity a=activity();a.mode=mode;a.displayName="A.md";a.editor.append("original **A**");a.requestPrint();
                a.editor.replace(0,a.editor.length(),"later B");a.displayName="B.md";render();WebView v=view();
                ok(manager(a).adapters.isEmpty(),"no adapter at render delivery");same(a,v.activity,"view belongs originating Activity");
                ok(v.html.replaceFirst(" data-md-preview=\"[^\"]+\"","").equals(MarkdownRenderer.render("original **A**",a.palette,a.previewFontSp)),"exact renderer HTML aside metadata");
                ok(v.settings.js&&!v.settings.storage&&!v.settings.file&&!v.settings.content&&v.settings.network&&!v.settings.images,"security settings");
                ok(v.base.equals("https://markdown.local/"),"baseURL");ok(v.client.shouldOverrideUrlLoading(v,"mdsrc:99"),"source link consumed");ok(v.client.shouldOverrideUrlLoading(v,"https://example.test"),"external link consumed");
                v.ready();ok(v.title.equals("A.md"),"immutable title");ok(a.mode==mode&&a.rebuilds==0,"mode unchanged");manager(a).adapters.get(0).onFinish();ok(v.destroys==1,"finish disposal");
            }
        });
        run("global admission before capture and queued destroy handoff",()->{
            CountDownLatch entered=new CountDownLatch(1),leave=new CountDownLatch(1);worker.execute(()->{entered.countDown();try{leave.await();}catch(InterruptedException e){throw new AssertionError(e);}});entered.await();
            MainActivity a=activity(),b=activity();a.requestPrint();a.destroy();b.requestPrint();ok(b.captures==0,"other Activity cannot snapshot canceled queued print");ok(WebView.ALL.isEmpty(),"no view before render");
            leave.countDown();render();ok(WebView.ALL.isEmpty(),"queued retired task skipped rendering/load");b.requestPrint();ok(b.captures==1,"admission after actual handoff");render();view().ready();manager(b).adapters.get(0).onFinish();
        });
        run("Activity handler removal cannot lose render completion",()->{
            MainActivity a=activity();a.requestPrint();worker.submit(()->{}).get();a.destroy();ok(a.main.tasks.isEmpty(),"Activity callbacks removed");
            MainActivity b=activity();b.requestPrint();ok(b.captures==0,"physical completion still reserved");delivery.drain();b.requestPrint();ok(b.captures==1,"process callback survives destroy");render();b.destroy();
        });
        run("absolute deadline checked before earlier queued completion",()->{
            MainActivity a=activity();a.requestPrint();worker.submit(()->{}).get();Runnable completion=delivery.takeFirstImmediate();SystemClock.now=30_001;completion.run();
            ok(WebView.ALL.isEmpty(),"late render cannot create view ahead of timeout message");ok(manager(a).adapters.isEmpty(),"late render cannot submit");ok(field("owner")==null,"completed expired render releases");
        });
        run("one readiness probe, strict nonce, nonextending deadline",()->{
            MainActivity a=activity();a.requestPrint();render();WebView v=view();v.finish();for(int i=0;i<40;i++)v.finish();ok(v.probes==1,"one outstanding probe");v.reply("null");ok(v.probes==2,"coalesced retry");v.reply("\"wrong\"");ok(manager(a).adapters.isEmpty(),"wrong DOM rejected");
            SystemClock.now=29_999;v.finish();SystemClock.now=30_000;v.reply("\""+v.token()+"\"");ok(manager(a).adapters.isEmpty(),"late DOM cannot submit before timer");ok(v.destroys==1,"pre-exposure timeout disposal");
        });
        run("duplicate old probe cannot retire newer physical callback",()->{
            MainActivity a=activity();a.requestPrint();render();WebView v=view();v.finish();var old=v.replies.get(0);v.finish();v.reply("null");ok(v.probes==2,"one pending retry");old.onReceiveValue("null");v.finish();ok(v.probes==2,"duplicate old callback cannot release newer probe slot");a.destroy();
        });
        run("slow capture uses original absolute timer deadline",()->{
            MainActivity a=activity();DocumentPrint.start(a,()->{SystemClock.now=10_000;return new DocumentPrint.Capture("text","A",a.palette,18,false);},a::showError);
            // The real worker can post concurrently; inspect under the stub Handler queue lock.
            synchronized(delivery){ok(delivery.tasks.stream().anyMatch(t->t.at==30_000),"capture does not extend timeout");}render();a.destroy();
        });
        run("reentrant capture cancellation keeps global reservation",()->{
            MainActivity a=activity(),b=activity();DocumentPrint.start(a,()->{DocumentPrint.cancel(a);b.requestPrint();ok(b.captures==0,"capture cancellation cannot admit another snapshot before returning");return new DocumentPrint.Capture("text","A",a.palette,18,false);},a::showError);
            render();ok(WebView.ALL.isEmpty(),"retired capture never loads");b.requestPrint();ok(b.captures==1,"capture returned and retired before next admission");render();b.destroy();
        });
        run("missing service and failed main-frame load preserve mode",()->{
            MainActivity a=activity();a.service=null;a.mode=0;a.requestPrint();render();view().ready();ok(a.mode==0&&a.rebuilds==0,"missing service no mutation");ok(view().destroys==1&&field("owner")==null,"unexposed cleanup");
            a=activity();a.requestPrint();render();WebView v=view();v.client.onReceivedError(v,new WebResourceRequest(){public boolean isForMainFrame(){return true;}public android.net.Uri getUrl(){return null;}},new WebResourceError());ok(v.destroys==1,"load failure disposal");
        });
        run("pre-exposure platform faults release only after successful disposal",()->{
            for(int fault=0;fault<4;fault++){
                MainActivity a=activity();if(fault==0)WebView.failCreate=true;
                final int f=fault;WebView.onCreate=v->{v.failLoad=f==1;v.failProbe=f==2;v.failAdapter=f==3;};
                a.requestPrint();render();if(fault==2)view().finish();else if(fault==3)view().ready();
                ok(field("owner")==null&&!a.errors.isEmpty(),"platform fault releases unexposed owner "+fault);
                if(fault!=0)ok(view().destroys==1,"unexposed view disposed once "+fault);reset();
            }
            MainActivity a=activity();a.requestPrint();render();WebView v=view();v.failDestroy=true;a.destroy();ok(field("owner")!=null&&v.destroys==1,"throwing pre-exposure disposal retains owner");reset();
        });
        run("lost probe expires; stale callback cannot affect replacement",()->{
            MainActivity a=activity();a.requestPrint();render();WebView old=view();old.finish();var stale=old.replies.get(0);SystemClock.now=30_000;delivery.drain();ok(old.destroys==1&&field("owner")==null,"lost probe finite pre-exposure cleanup");
            MainActivity b=activity();b.requestPrint();render();Object current=field("owner");stale.onReceiveValue("\""+old.token()+"\"");same(current,field("owner"),"stale disposed view cannot release new owner");ok(manager(a).adapters.isEmpty()&&manager(b).adapters.isEmpty(),"stale callback never submits");view().ready();SystemClock.now+=60_000;delivery.drain();ok(view().destroys==0,"pre-submit timer cannot destroy submitted view");manager(b).adapters.get(0).onFinish();
        });
        run("framework arguments forwarded unchanged; finish owns disposal",()->{
            MainActivity a=activity();PrintDocumentAdapter adapter=ready(a);WebView v=view();adapter.onStart();adapter.onStart();ok(v.delegate.starts==1,"one start");
            PrintAttributes old=new PrintAttributes(),next=new PrintAttributes();CancellationSignal cancel=new CancellationSignal();Bundle extras=new Bundle();var layout=FrameworkCallbacks.layout();adapter.onLayout(old,next,cancel,layout,extras);
            Object[] got=v.delegate.layoutArgs;Object[] expected={old,next,cancel,layout,extras};for(int i=0;i<got.length;i++)same(got[i],expected[i],"original layout argument "+i);
            PageRange[] pages={new PageRange()};ParcelFileDescriptor fd=new ParcelFileDescriptor();var write=FrameworkCallbacks.write();adapter.onWrite(pages,fd,cancel,write);got=v.delegate.writeArgs;expected=new Object[]{pages,fd,cancel,write};for(int i=0;i<got.length;i++)same(got[i],expected[i],"original write argument "+i);
            a.destroy();ok(v.destroys==0&&field("owner")!=null,"destroy is not finish");ok(manager(a).job.cancels==1,"one job cancellation");adapter.onLayout(old,next,cancel,layout,extras);adapter.onWrite(pages,fd,cancel,write);ok(layout.cancellations==1&&write.cancellations==1,"retired callbacks canceled");ok(v.delegate.layouts==1&&v.delegate.writes==1,"retired no delegate call");ok(fd.closes==0,"framework FD untouched");adapter.onFinish();adapter.onFinish();ok(v.delegate.finishes==1&&v.destroys==1&&field("owner")==null,"one successful finish releases");
        });
        run("recursive start/finish flags precede delegate",()->{
            MainActivity a=activity();PrintDocumentAdapter adapter=ready(a);WebView v=view();v.delegate.startHook=adapter::onStart;v.delegate.finishHook=adapter::onFinish;adapter.onStart();adapter.onFinish();ok(v.delegate.starts==1&&v.delegate.finishes==1&&v.destroys==1,"recursive lifecycle forwarded once");
        });
        run("null or throwing submission retains exposed owner",()->{
            for(boolean fail:new boolean[]{false,true}){
                MainActivity a=activity();manager(a).fail=fail;manager(a).returnNull=!fail;PrintDocumentAdapter adapter=ready(a);WebView v=view();ok(v.destroys==0&&field("owner")!=null,"submission uncertainty retains view");adapter.onFinish();ok(v.destroys==1&&field("owner")==null,"framework finish resolves uncertainty");
            }
        });
        run("reentrant destroy cancels late returned job",()->{
            MainActivity a=activity();manager(a).duringPrint=a::destroy;PrintDocumentAdapter adapter=ready(a);ok(manager(a).job.cancels==1,"late job canceled once");ok(view().destroys==0,"no premature disposal");adapter.onFinish();
        });
        run("synchronous finish and stale return cannot clear new owner",()->{
            for(int fault=0;fault<3;fault++){
                MainActivity a=activity(),b=activity();PrintManager pm=manager(a);pm.returnNull=fault==1;pm.fail=fault==2;pm.duringPrint=()->{pm.adapters.get(0).onFinish();b.requestPrint();};
                a.requestPrint();render();WebView old=view();old.ready();Object next=field("owner");ok(next!=null&&b.captures==1,"new owner admitted after finish");same(next,field("owner"),"stale return keeps new owner");render();view().ready();manager(b).adapters.get(0).onFinish();ok(old.destroys==1,"old disposed once");ok(pm.job.cancels==0,"normal synchronous finish must not cancel successful spool job");
            }
        });
        run("delegate/callback exceptions do not fabricate completion",()->{
            for(int fault=0;fault<5;fault++){
                MainActivity a=activity();PrintDocumentAdapter adapter=ready(a);WebView v=view();var cb=FrameworkCallbacks.layout();
                if(fault==0){v.delegate.failLayout=true;adapter.onLayout(null,null,null,cb,null);ok(cb.cancellations==0,"throwing delegate no fabricated callback");}
                else if(fault==1){v.delegate.failStart=true;adapter.onStart();}
                else if(fault==2){a.destroy();cb.fail=true;adapter.onLayout(null,null,null,cb,null);ok(cb.cancellations==1,"throwing callback called once");}
                else if(fault==3){v.delegate.failWrite=true;var write=FrameworkCallbacks.write();adapter.onWrite(null,null,null,write);ok(write.cancellations==0,"throwing write no fabricated callback");}
                else {a.destroy();var write=FrameworkCallbacks.write();write.fail=true;adapter.onWrite(null,null,null,write);ok(write.cancellations==1,"throwing write callback called once");}
                ok(v.destroys==0&&field("owner")!=null,"exposed failure retains");adapter.onFinish();
            }
        });
        run("throwing finish/disposal retain bounded ownership",()->{
            for(boolean disposal:new boolean[]{false,true}){
                MainActivity a=activity();PrintDocumentAdapter adapter=ready(a);WebView v=view();v.failDestroy=disposal;v.delegate.failFinish=!disposal;adapter.onFinish();adapter.onFinish();ok(field("owner")!=null,"uncertain cleanup remains owned");ok(v.delegate.finishes==1&&v.destroys==(disposal?1:0),"no retry or premature destroy");MainActivity b=activity();b.requestPrint();ok(b.captures==0,"no second capture");reset();
            }
        });
        run("strict scalar byte count and pre-copy length boundary",()->{
            DocumentPrint.Capture cap=new DocumentPrint.Capture("a".repeat(DocumentIO.MAX_DOCUMENT_BYTES),"x",UiTheme.palette(0,false),18,false);DocumentPrint.validate(cap);checks++;
            DocumentPrint.validate(new DocumentPrint.Capture("a".repeat(DocumentIO.MAX_DOCUMENT_BYTES-3),"x",cap.palette,18,true));checks++;
            DocumentPrint.validate(new DocumentPrint.Capture("\ud83d\ude00".repeat(DocumentIO.MAX_DOCUMENT_BYTES/4),"x",cap.palette,18,false));checks++;
            for(String s:new String[]{"\ud800","\udc00","\ud800x","é".repeat(DocumentIO.MAX_DOCUMENT_BYTES/2+1)}){boolean bad=false;try{DocumentPrint.validate(new DocumentPrint.Capture(s,"x",cap.palette,18,false));}catch(IllegalArgumentException e){bad=true;}ok(bad,"malformed/over-byte-cap rejected");}
            boolean bad=false;try{DocumentPrint.validate(new DocumentPrint.Capture(cap.source,"x",cap.palette,18,true));}catch(IllegalArgumentException e){bad=true;}ok(bad,"BOM counts toward cap");
            CharSequence oversized=new CharSequence(){public int length(){return DocumentIO.MAX_DOCUMENT_BYTES+1;}public char charAt(int i){throw new AssertionError();}public CharSequence subSequence(int a,int b){throw new AssertionError();}public String toString(){throw new AssertionError("copied oversized input");}};
            bad=false;try{new DocumentPrint.Capture(oversized,"x",cap.palette,18,false);}catch(IllegalArgumentException e){bad=true;}ok(bad,"reject length before snapshot");
            MainActivity a=activity();a.editor.append("\ud800");a.requestPrint();render();ok(WebView.ALL.isEmpty()&&field("owner")==null&&!a.errors.isEmpty(),"invalid capture never reaches WebView");
        });
        System.out.println("PASS print ownership: "+cases+" cases, "+checks+" checks");
        } finally {worker.shutdownNow();}
    }
}
