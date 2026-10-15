package com.example.markdowndesk;
import android.os.*;import android.print.*;import android.webkit.*;import java.lang.reflect.*;import java.util.concurrent.*;
public final class PrintRenderCheck {
    static int checks;static void ok(boolean b,String why){checks++;if(!b)throw new AssertionError(why);}
    static Object field(String n)throws Exception{Field f=DocumentPrint.class.getDeclaredField(n);f.setAccessible(true);return f.get(null);}
    static MainActivity activity(){MainActivity a=new MainActivity();a.service=new PrintManager();return a;}
    public static void main(String[] args)throws Exception{
        ExecutorService worker=(ExecutorService)field("worker");Handler main=(Handler)field("main");
        try{
            for(boolean destroy:new boolean[]{false,true}){
                MarkdownRenderer.entered=new CountDownLatch(1);MarkdownRenderer.leave=new CountDownLatch(1);
                MainActivity a=activity(),b=activity();a.requestPrint();ok(MarkdownRenderer.entered.await(5,TimeUnit.SECONDS),"real helper entered controlled renderer");int calls=MarkdownRenderer.calls;
                if(destroy)a.destroy();else {SystemClock.now+=30_001;main.drain();}
                for(int i=0;i<40;i++)b.requestPrint();ok(b.captures==0,"no capture while retired renderer running");ok(MarkdownRenderer.calls==calls&&MarkdownRenderer.active==1,"one noninterrupted renderer");ok(field("owner")!=null,"physical slot retained");
                MarkdownRenderer.leave.countDown();worker.submit(()->{}).get(5,TimeUnit.SECONDS);b.requestPrint();ok(b.captures==0,"completion callback still holds slot");main.drain();ok(WebView.ALL.isEmpty(),"canceled result dropped");ok(field("owner")==null,"worker completion retires slot");
            }
            MarkdownRenderer.entered=null;MarkdownRenderer.leave=null;MarkdownRenderer.fail=true;
            MainActivity a=activity();a.requestPrint();worker.submit(()->{}).get(5,TimeUnit.SECONDS);main.drain();ok(field("owner")==null&&!a.errors.isEmpty(),"renderer RuntimeException releases after handoff");
            MarkdownRenderer.fail=false;a=activity();a.requestPrint();worker.submit(()->{}).get(5,TimeUnit.SECONDS);main.drain();WebView v=WebView.ALL.get(0);v.ready();((PrintManager)a.service).adapters.get(0).onFinish();ok(field("owner")==null,"next operation works");ok(MarkdownRenderer.maxActive==1,"one print render active");
            worker.shutdown();a=activity();a.requestPrint();ok(field("owner")==null&&!a.errors.isEmpty(),"executor rejection releases unadmitted reservation");
            System.out.println("PASS print controlled renderer: "+checks+" checks");
        }finally{worker.shutdownNow();}
    }
}
