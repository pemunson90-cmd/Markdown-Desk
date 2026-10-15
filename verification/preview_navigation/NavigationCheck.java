package com.example.markdowndesk;
import java.util.*;

/** Safety assertions deliberately RED on the inspected baseline; no production repair here. */
public final class NavigationCheck {
    static int assertions,failures,cases;
    static boolean printOnly,baseline,scrollHazard;
    static void eq(Object expected,Object actual,String message){assertions++;if(!Objects.equals(expected,actual)){failures++;System.out.println("FAIL "+message+" expected="+summary(expected)+" actual="+summary(actual));}}
    static String summary(Object value){String s=String.valueOf(value);return s.length()>120?s.substring(0,90)+"… ("+s.length()+" chars)":s;}
    static void run(String name,Runnable task){if(scrollHazard&&!name.startsWith("native "))return;boolean print=name.startsWith("print ")||name.startsWith("successful ready print")||name.startsWith("unavailable print");if(print!=printOnly)return;if(baseline&&(name.startsWith("readiness requires")||name.startsWith("held old probe")||name.startsWith("global physical")||name.startsWith("coalesced current")||name.startsWith("probe delivery")||name.startsWith("duplicate current completion never")||name.startsWith("old same-page JS")))return;cases++;System.out.println("CASE "+name);task.run();}
    static String plain(String html){return html.replaceFirst(" data-md-preview=\"-?[0-9]+\"", "");}
    static MainActivity ready(){MainActivity a=new MainActivity();a.renderPreview();a.deliverRender();a.preview.commit(0);a.new AnonymousClass2().onPageFinished(a.preview,a.preview.loads.get(0).url);a.main.advance(160);return a;}
    public static void main(String[] args){printOnly=Arrays.asList(args).contains("--print-hazards");baseline=Arrays.asList(args).contains("--baseline");scrollHazard=Arrays.asList(args).contains("--scroll-state-hazard");
        run("native scroll from unacknowledged DOM preserves new tab state",()->{
            MainActivity a=ready();a.tabs.get(1).previewScrollY=91;a.activeTab=1;a.previewRestoreY=91;a.previewReady=false;a.editor.text="B pending";a.renderPreview();
            a.scrollEvent(7);eq(91,a.tabs.get(1).previewScrollY,"old DOM native scroll cannot overwrite pending tab scroll");eq(0,a.sessions,"unacknowledged scroll cannot schedule persisted session");
        });
        run("native capture cannot persist old DOM coordinate in pending tab",()->{
            MainActivity a=ready();a.preview.y=7;a.tabs.get(1).previewScrollY=91;a.activeTab=1;a.previewRestoreY=91;a.previewReady=false;a.editor.text="B pending";a.renderPreview();
            a.capture();eq(91,a.tabs.get(1).previewScrollY,"pending tab capture preserves its saved preview coordinate");eq("B pending",a.tabs.get(1).text,"guard does not prevent current text capture");
            a.deliverRender();a.preview.commit(1);a.new AnonymousClass2().onPageFinished(a.preview,"same-url");a.preview.y=123;a.capture();eq(123,a.tabs.get(1).previewScrollY,"acknowledged current DOM scroll captured");a.scrollEvent(150);eq(150,a.tabs.get(1).previewScrollY,"acknowledged native scroll stored");eq(1,a.sessions,"acknowledged native scroll schedules session");
        });
        run("outgoing acknowledged page preserves scroll on option changes",()->{
            for(String change:new String[]{"font","palette","mode"}){MainActivity a=ready();a.preview.y=321;
                if(change.equals("font"))a.previewFontSp=24;if(change.equals("palette"))a.palette=UiTheme.palette(2,false);if(change.equals("mode"))a.mode=MainActivity.MODE_SPLIT;
                a.renderPreview();eq(321,a.previewRestoreY,"outgoing acknowledged document scroll retained for "+change);
            }
        });
        run("current load and real renderer control",()->{
            MainActivity a=ready();eq(true,a.previewReady,"current completed load is ready");
            eq(MarkdownRenderer.render(a.editor.text,a.palette,a.previewFontSp),plain(a.preview.committedHtml),"full rendered HTML unchanged");
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
            MainActivity a=new MainActivity();a.mode=MainActivity.MODE_SPLIT;a.renderPreview();a.deliverRender();a.preview.commit(0);a.new AnonymousClass2().onPageFinished(a.preview,a.preview.loads.get(0).url);a.main.advance(300);a.syncFromEditor();a.main.advance(60);a.syncFromEditor();a.main.advance(60);
            eq(true,a.syncingFromEditor,"first same-page120ms reset cannot clear newer scroll operation");
        });
        run("preview-to-editor deferred last hop rejects changed tab",()->{
            MainActivity a=new MainActivity();a.mode=MainActivity.MODE_SPLIT;a.renderPreview();a.deliverRender();a.preview.commit(0);a.new AnonymousClass2().onPageFinished(a.preview,a.preview.loads.get(0).url);a.main.advance(300);
            a.syncFromPreview();eq(1,a.editor.posts.size(),"actual editor scroll callback queued");
            a.activeTab=1;a.editor.text="other tab";a.renderPreview();a.editor.drain();
            eq(0,a.editor.scrollCalls,"old preview offset cannot scroll new tab at deferred editor hop");
        });
        run("late finish after shutdown cannot revive readiness",()->{
            MainActivity a=ready();a.editor.text="queued";a.renderPreview();a.stop();int scrolls=a.preview.scrollCalls;
            a.new AnonymousClass2().onPageFinished(a.preview,"https://markdown.local/");
            eq(false,a.previewReady,"closed worker cannot become ready through old finish");eq(scrolls,a.preview.scrollCalls,"closed preview cannot be scrolled by late finish");
        });
        run("readiness requires complete DOM and exact JSON identity",()->{
            MainActivity a=new MainActivity();a.renderPreview();a.deliverRender();a.preview.commit(0);a.preview.domComplete=false;
            a.new AnonymousClass2().onPageFinished(a.preview,"ignored");eq(false,a.previewReady,"loading DOM rejected");a.preview.domComplete=true;
            for(String reply:new String[]{"null","true","1","\"99999\"","[1]","{}"," \"1\"","\"1\" "}){
                a.preview.forcedReply=reply;a.new AnonymousClass2().onPageFinished(a.preview,"ignored");eq(false,a.previewReady,"strict identity response "+reply);
            }
            a.preview.forcedReply=null;a.new AnonymousClass2().onPageFinished(a.preview,"ignored");eq(true,a.previewReady,"matching complete DOM accepted");
        });
        run("global physical probe remains bounded across page replacement",()->{
            MainActivity a=new MainActivity();a.preview.holdReplies=true;
            for(int i=0;i<100;i++){a.editor.text="revision "+i;a.renderPreview();a.deliverRender();a.preview.commit(i);a.new AnonymousClass2().onPageFinished(a.preview,"same-url");}
            eq(1,a.preview.replies.size(),"rapid replacements retain one physical readiness callback");eq(false,a.previewReady,"held callback cannot certify current DOM");
            a.preview.holdReplies=false;a.preview.replies();eq(true,a.previewReady,"old physical callback return triggers latest current probe");
        });
        run("held old probe cannot block or acknowledge newer page",()->{
            MainActivity a=new MainActivity();a.renderPreview();a.deliverRender();a.preview.commit(0);a.preview.holdReplies=true;
            a.new AnonymousClass2().onPageFinished(a.preview,"ignored");a.new AnonymousClass2().onPageFinished(a.preview,"ignored");eq(1,a.preview.replies.size(),"one probe while current reply pending");
            a.activeTab=1;a.editor.text="new B";a.renderPreview();a.deliverRender();a.preview.commit(1);a.preview.holdReplies=false;
            a.new AnonymousClass2().onPageFinished(a.preview,"old-callback-url");eq(false,a.previewReady,"new page waits for physical old probe to return");int scrolls=a.preview.scrollCalls;
            a.preview.replies();eq(scrolls+1,a.preview.scrollCalls,"old response triggers one current DOM acknowledgment");eq(true,a.previewReady,"old reply cannot clear newer readiness");
        });
        run("coalesced current finish retries after older DOM response",()->{
            MainActivity a=ready();a.editor.text="new DOM";a.renderPreview();a.deliverRender();a.preview.holdReplies=true;
            a.new AnonymousClass2().onPageFinished(a.preview,"old-finish");a.preview.commit(1);a.new AnonymousClass2().onPageFinished(a.preview,"current-finish");
            eq(1,a.preview.replies.size(),"one in-flight probe while current completion coalesces");a.preview.holdReplies=false;a.preview.replies();
            eq(true,a.previewReady,"coalesced current finish gets a fresh DOM probe after obsolete response");
        });
        run("probe delivery checks live document options and shutdown",()->{
            for(String change:new String[]{"tab","font","palette","mode","finishing","destroyed","revision","aba"}){
                MainActivity a=new MainActivity();a.renderPreview();a.deliverRender();a.preview.commit(0);a.preview.holdReplies=true;a.new AnonymousClass2().onPageFinished(a.preview,"ignored");
                if(change.equals("tab"))a.activeTab=1;
                if(change.equals("font"))a.previewFontSp=24;
                if(change.equals("palette"))a.palette=UiTheme.palette(2,false);
                if(change.equals("mode"))a.mode=MainActivity.MODE_EDIT;
                if(change.equals("finishing"))a.finishing=true;
                if(change.equals("destroyed"))a.preview.destroyed=true;
                if(change.equals("revision"))a.schedulePreview();
                if(change.equals("aba")){a.activeTab=1;a.renderPreview();a.activeTab=0;a.renderPreview();}
                a.preview.replies();eq(false,a.previewReady,"late probe cannot acknowledge after "+change);eq(0,a.preview.scrollCalls,"late probe cannot restore after "+change);
            }
        });
        run("duplicate current completion never adds another probe",()->{
            MainActivity a=ready();int scripts=a.preview.scripts.size();for(int i=0;i<100;i++)a.new AnonymousClass2().onPageFinished(a.preview,"same-url");eq(scripts,a.preview.scripts.size(),"acknowledged current page is idempotent");
        });
        run("old same-page JS reply cannot clear newer operation",()->{
            MainActivity a=new MainActivity();a.mode=MainActivity.MODE_SPLIT;a.renderPreview();a.deliverRender();a.preview.commit(0);a.new AnonymousClass2().onPageFinished(a.preview,"ignored");a.main.advance(300);
            a.preview.holdReplies=true;a.syncFromEditor();a.main.advance(60);a.syncFromEditor();eq(2,a.preview.replies.size(),"two scroll replies held");
            Runnable first=a.preview.replies.remove(0),second=a.preview.replies.remove(0);second.run();a.main.advance(60);first.run();a.main.advance(60);
            eq(false,a.syncingFromEditor,"older reply cannot prolong newer operation after its120ms release");
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
            eq(MarkdownRenderer.render("requested A print",a.palette,a.previewFontSp),plain(adapter.html),"print content remains captured originating document");
        });
        run("successful ready print control",()->{
            MainActivity a=ready();a.editor.text="captured print";a.requestPrint();a.deliverRender();a.preview.commit(1);a.new AnonymousClass2().onPageFinished(a.preview,a.preview.loads.get(1).url);a.main.advance(350);
            eq(1,a.printManager.adapters.size(),"ready print submits once");eq(MarkdownRenderer.render("captured print",a.palette,a.previewFontSp),plain(a.printManager.adapters.get(0).html),"ready print sees actual rendered document");
        });
        run("unavailable print service restores temporary mode",()->{
            MainActivity a=ready();a.mode=MainActivity.MODE_EDIT;a.printManager=null;a.requestPrint();a.main.advance(350);
            eq(1,a.errors.size(),"missing print service reports error");eq(MainActivity.MODE_EDIT,a.mode,"missing service restores original Write mode");
        });
        System.out.println((failures==0?"PASS":"RED")+" preview-navigation: "+cases+" cases, "+assertions+" assertions, "+failures+" failures");
        if(failures!=0)throw new AssertionError("Baseline readiness/cleanup safety assertions failed: "+failures);
    }
}
