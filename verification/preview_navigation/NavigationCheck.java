package com.example.markdowndesk;
import java.util.*;

/** Safety assertions deliberately RED on the inspected baseline; no production repair here. */
public final class NavigationCheck {
    static int assertions,failures,cases;
    static boolean printOnly;
    static void eq(Object expected,Object actual,String message){assertions++;if(!Objects.equals(expected,actual)){failures++;System.out.println("FAIL "+message+" expected="+summary(expected)+" actual="+summary(actual));}}
    static String summary(Object value){String s=String.valueOf(value);return s.length()>120?s.substring(0,90)+"… ("+s.length()+" chars)":s;}
    static void run(String name,Runnable task){boolean print=name.startsWith("print ")||name.startsWith("successful ready print")||name.startsWith("unavailable print");if(print!=printOnly)return;cases++;System.out.println("CASE "+name);task.run();}
    static MainActivity ready(){MainActivity a=new MainActivity();a.renderPreview();a.deliverRender();a.preview.commit(0);a.new AnonymousClass2().onPageFinished(a.preview,a.preview.loads.get(0).url);a.main.advance(160);return a;}
    public static void main(String[] args){printOnly=args.length>0&&args[0].equals("--print-hazards");
        run("current load and real renderer control",()->{
            MainActivity a=ready();eq(true,a.previewReady,"current completed load is ready");
            eq(MarkdownRenderer.render(a.editor.text,a.palette,a.previewFontSp),a.preview.committedHtml,"full rendered HTML unchanged");
            eq("https://markdown.local/",a.preview.loads.get(0).url,"source common URL observed");
        });
        run("old finish while latest render waits",()->{
            MainActivity a=ready();a.activeTab=1;a.editor.text="new B document";a.previewRestoreY=91;a.previewReady=false;a.renderPreview();
            eq(false,a.previewReady,"request initially not ready");int scrolls=a.preview.scrollCalls;
            // Simulate prior navigation callback while latest worker remains unexecuted.
            a.new AnonymousClass2().onPageFinished(a.preview,a.preview.loads.get(0).url);
            eq(false,a.previewReady,"old completion cannot mark pending B ready");
            eq(scrolls,a.preview.scrollCalls,"old completion cannot apply B restore position");
        });
        run("same URL callbacks have no accepted-load identity",()->{
            MainActivity a=ready();a.editor.text="new A revision";a.renderPreview();a.deliverRender();
            eq(2,a.preview.loads.size(),"second real render accepted");eq(a.preview.loads.get(0).url,a.preview.loads.get(1).url,"both accepted URLs identical");
            a.new AnonymousClass2().onPageFinished(a.preview,a.preview.loads.get(0).url);
            eq(false,a.previewReady,"old same-URL completion cannot certify uncommitted revision");
        });
        run("old reset cannot end newer scroll suppression",()->{
            MainActivity a=new MainActivity();a.renderPreview();a.deliverRender();a.preview.commit(0);a.new AnonymousClass2().onPageFinished(a.preview,"https://markdown.local/");a.main.advance(80);
            a.editor.text="second revision";a.renderPreview();a.deliverRender();a.preview.commit(1);a.new AnonymousClass2().onPageFinished(a.preview,"https://markdown.local/");
            eq(true,a.syncingFromEditor,"new callback starts suppression");a.main.advance(80);
            eq(true,a.syncingFromEditor,"prior 160ms reset cannot clear newer navigation suppression");
        });
        run("duplicate current finish preserves user scroll",()->{
            MainActivity a=ready();a.preview.y=777;int scrolls=a.preview.scrollCalls;
            a.new AnonymousClass2().onPageFinished(a.preview,a.preview.loads.get(0).url);
            eq(777,a.preview.y,"duplicate current finish cannot reset user scroll");eq(scrolls,a.preview.scrollCalls,"current page acknowledged only once");
        });
        run("same-page older scroll reset cannot release newer operation",()->{
            MainActivity a=ready();a.mode=MainActivity.MODE_SPLIT;a.syncFromEditor();a.main.advance(60);a.syncFromEditor();a.main.advance(60);
            eq(true,a.syncingFromEditor,"first same-page120ms reset cannot clear newer scroll operation");
        });
        run("late finish after shutdown cannot revive readiness",()->{
            MainActivity a=ready();a.editor.text="queued";a.renderPreview();a.stop();int scrolls=a.preview.scrollCalls;
            a.new AnonymousClass2().onPageFinished(a.preview,"https://markdown.local/");
            eq(false,a.previewReady,"closed worker cannot become ready through old finish");eq(scrolls,a.preview.scrollCalls,"closed preview cannot be scrolled by late finish");
        });
        run("print waits for current render",()->{
            MainActivity a=ready();a.editor.text="new print revision";String old=a.preview.committedHtml;a.requestPrint();
            eq(false,a.previewReady,"print requests current render");eq(1,a.worker().tasks.size(),"actual LatestPreview work held");a.main.advance(350);
            eq(0,a.printManager.adapters.size(),"no print adapter before captured render completes");
            if(!a.printManager.adapters.isEmpty())eq(old,a.printManager.adapters.get(0).html,"observed premature adapter uses old committed DOM in host model");
        });
        run("print waits for DOM commit after render delivery",()->{
            MainActivity a=ready();a.editor.text="fresh rendered but not committed";a.requestPrint();a.deliverRender();
            eq(2,a.preview.loads.size(),"new print HTML submitted");eq(false,a.previewReady,"DOM completion still pending");a.main.advance(350);
            eq(0,a.printManager.adapters.size(),"render delivery alone cannot authorize printing old DOM");
        });
        run("print cannot retarget to later active tab",()->{
            MainActivity a=ready();a.editor.text="requested A print";a.requestPrint();a.deliverRender();a.preview.commit(1);a.new AnonymousClass2().onPageFinished(a.preview,a.preview.loads.get(1).url);
            a.activeTab=1;a.displayName="B.md";a.editor.text="later B document";a.previewReady=false;a.renderPreview();a.deliverRender();a.preview.commit(2);a.new AnonymousClass2().onPageFinished(a.preview,a.preview.loads.get(2).url);a.main.advance(350);
            eq(1,a.printManager.adapters.size(),"one requested print submitted");
            MainActivity.PrintAdapter adapter=a.printManager.adapters.get(0);
            eq("A.md",adapter.title,"print title remains captured originating document");
            eq(MarkdownRenderer.render("requested A print",a.palette,a.previewFontSp),adapter.html,"print content remains captured originating document");
        });
        run("successful ready print control",()->{
            MainActivity a=ready();a.editor.text="captured print";a.requestPrint();a.deliverRender();a.preview.commit(1);a.new AnonymousClass2().onPageFinished(a.preview,a.preview.loads.get(1).url);a.main.advance(350);
            eq(1,a.printManager.adapters.size(),"ready print submits once");eq(MarkdownRenderer.render("captured print",a.palette,a.previewFontSp),a.printManager.adapters.get(0).html,"ready print sees actual rendered document");
        });
        run("unavailable print service restores temporary mode",()->{
            MainActivity a=ready();a.mode=MainActivity.MODE_EDIT;a.printManager=null;a.requestPrint();a.main.advance(350);
            eq(1,a.errors.size(),"missing print service reports error");eq(MainActivity.MODE_EDIT,a.mode,"missing service restores original Write mode");
        });
        System.out.println((failures==0?"PASS":"RED")+" preview-navigation: "+cases+" cases, "+assertions+" assertions, "+failures+" failures");
        if(failures!=0)throw new AssertionError("Baseline readiness/cleanup safety assertions failed: "+failures);
    }
}
