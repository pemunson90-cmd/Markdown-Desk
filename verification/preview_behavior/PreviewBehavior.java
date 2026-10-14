package com.example.markdowndesk;
import java.util.*;
import java.util.concurrent.*;

public final class PreviewBehavior {
    private static int checks;
    static void check(boolean ok, String message) { checks++; if (!ok) throw new AssertionError(message); }
    static void equal(Object expected, Object actual, String message) { check(Objects.equals(expected,actual),message); }
    static String expected(PreviewHarness h) { return MarkdownRenderer.render(h.editor.text.value,h.palette,h.previewFontSp); }
    static void queueDelivery(PreviewHarness h) { h.renderPreview(); h.worker().drain(); equal(1,h.main.posted.size(),"one delivery"); }

    static void debounceAndCopies() {
        PreviewHarness h = new PreviewHarness();
        for (int i=0;i<100;i++) {
            h.changed("edit " + i,false);
            equal(0,h.editor.text.copies,"no keypress snapshot");
            equal(0,h.worker().tasks.size(),"no worker before debounce");
            equal(1,h.main.delayed.size(),"single timer");
        }
        h.main.runDelayed(); equal(1,h.editor.text.copies,"one settled snapshot");
        h.worker().drain(); h.main.runPosted(); equal(expected(h),h.preview.html,"latest edit HTML");
        h.mode=PreviewHarness.MODE_EDIT; h.layout(); h.changed("hidden edit",false);
        equal(0,h.main.delayed.size(),"no hidden timer"); equal(0,h.editor.text.copies,"no hidden snapshot");
        h.mode=PreviewHarness.MODE_SPLIT; h.layout(); h.renderPreview(); h.settle();
        equal(expected(h),h.preview.html,"split render");
    }

    static void staleBeforeDebounceAndSuppression() {
        PreviewHarness h = new PreviewHarness(); queueDelivery(h);
        h.changed("new edit",false); h.main.runPosted(); equal(0,h.preview.loads,"reject before next debounce");
        h.settle(); equal(expected(h),h.preview.html,"new edit eventually accepted");
        queueDelivery(h); int loads=h.preview.loads;
        h.changed("loaded document",true); h.main.runPosted();
        equal(loads,h.preview.loads,"suppressed setText rejects old delivery");
        equal(0,h.editor.text.copies,"suppressed load does not capture");
        equal(0,h.main.delayed.size(),"suppressed load awaits explicit render");
        h.renderPreview(); h.settle(); equal(expected(h),h.preview.html,"suppressed load explicit render");
    }

    static void boundedPendingAndDelivery() {
        PreviewHarness h=new PreviewHarness(); queueDelivery(h);
        for(int i=0;i<2000;i++) { h.changed("pending " + i,false); h.main.runDelayed(); }
        equal(0,h.worker().tasks.size(),"delivery holds worker slot");
        equal(1,h.main.posted.size(),"old HTML does not multiply");
        h.main.runPosted(); equal(0,h.preview.loads,"obsolete queued delivery ignored");
        equal(1,h.worker().tasks.size(),"only latest pending launches");
        h.settle(); equal(1,h.preview.loads,"one new HTML accepted"); equal(expected(h),h.preview.html,"latest pending HTML");
        PreviewHarness before=new PreviewHarness();
        for(int i=0;i<2000;i++) { before.changed("before " + i,false); before.main.runDelayed(); }
        equal(1,before.worker().tasks.size(),"executor queue bounded before first execution");
        before.settle(); equal(1,before.preview.loads,"only latest pre-execution accepted");
    }

    static void transitions() {
        PreviewHarness h=new PreviewHarness(); queueDelivery(h);
        h.activeTab=1; h.changed("B",true); h.layout(); h.renderPreview();
        h.activeTab=0; h.changed("A again",true); h.layout(); h.renderPreview();
        h.main.runPosted(); equal(0,h.preview.loads,"A B A rejects original A");
        h.settle(); equal(expected(h),h.preview.html,"latest A loads");
        queueDelivery(h); int loads=h.preview.loads;
        h.tabs.remove(0); h.main.runPosted(); equal(loads,h.preview.loads,"tab close document mismatch");
        h.renderPreview(); h.settle(); equal(loads+1,h.preview.loads,"remaining tab loads");
        queueDelivery(h); loads=h.preview.loads;
        UiTheme.Palette original=h.palette;
        h.palette=UiTheme.palette(8,true); h.schedulePreview();
        h.palette=original; h.schedulePreview();
        h.main.runPosted(); equal(loads,h.preview.loads,"theme A B A invalidates");
        h.settle(); equal(expected(h),h.preview.html,"restored theme HTML");
        queueDelivery(h); loads=h.preview.loads;
        h.previewFontSp=30; h.renderPreview(); h.previewFontSp=18; h.renderPreview();
        h.main.runPosted(); equal(loads,h.preview.loads,"font A B A invalidates");
        h.settle(); equal(expected(h),h.preview.html,"restored font HTML");
        queueDelivery(h); loads=h.preview.loads;
        h.mode=PreviewHarness.MODE_EDIT; h.layout(); h.mode=PreviewHarness.MODE_PREVIEW; h.layout();
        h.main.runPosted(); equal(loads,h.preview.loads,"layout A B A invalidates");
        h.renderPreview(); h.settle(); equal(loads+1,h.preview.loads,"restored layout HTML");
    }

    static void acceptanceGuards() {
        for(int which=0;which<6;which++) {
            PreviewHarness h=new PreviewHarness(); queueDelivery(h);
            switch(which) {
                case 0: h.activeTab=1; break;
                case 1: h.palette=UiTheme.palette(1,true); break;
                case 2: h.previewFontSp=19; break;
                case 3: h.mode=PreviewHarness.MODE_SPLIT; break;
                case 4: h.finishing=true; break;
                case 5: h.tabs.clear(); break;
            }
            h.main.runPosted(); equal(0,h.preview.loads,"acceptance guard " + which);
        }
    }

    static void scrollAndShutdown() {
        PreviewHarness h=new PreviewHarness(); h.previewReady=true; h.preview.y=317;
        h.renderPreview(); equal(317,h.previewRestoreY,"existing ready scroll captured");
        h.preview.y=0; h.renderPreview(); equal(317,h.previewRestoreY,"in-flight render retains restore Y");
        h.settle(); equal(expected(h),h.preview.html,"scroll render HTML");
        h.previewRestoreY=901; h.previewReady=false; h.renderPreview();
        equal(901,h.previewRestoreY,"tab restore Y retained");
        h.onDestroy(); h.schedulePreview(); h.renderPreview();
        equal(0,h.main.delayed.size(),"no timer after destruction"); equal(0,h.worker().tasks.size(),"queued work cancelled on destruction");
        check(h.preview.destroyed,"WebView destroyed");
        PreviewHarness posted=new PreviewHarness(); queueDelivery(posted);
        Runnable alreadyPosted=posted.main.posted.peek(); posted.onDestroy(); alreadyPosted.run();
        equal(0,posted.preview.loads,"even removed callback cannot load after shutdown");
    }

    static void fullHtmlParity() {
        String source="\ufeff# Title\r\n\r\n**Bold** *italic* ~~strike~~ `code` & < >\n\n"
                +"> quote [link](https://example.com)\n\n- parent\n  - child\n- [x] task\n\n"
                +"| a | b |\n| --- | --- |\n| ü | 👨‍👩‍👧 |\n\n```java\n<code>\n```\n"
                +"![image](https://example.com/a.png)\\* escaped";
        for(int theme=0;theme<9;theme++) for(float font:new float[]{12,18,32}) {
            PreviewHarness h=new PreviewHarness(); h.changed(source,true);
            h.palette=UiTheme.palette(theme,false); h.previewFontSp=font; h.renderPreview();
            h.settle(); equal(expected(h),h.preview.html,"full HTML theme/font " + theme + "/" + font);
        }
    }

    static LatestPreview.Request request(String source,long revision) {
        return new LatestPreview.Request("A",revision,source,UiTheme.palette(0,false),18,1);
    }

    static void helperLifecycle() {
        PreviewHarness.QueueExecutor worker=new PreviewHarness.QueueExecutor(), main=new PreviewHarness.QueueExecutor();
        List<LatestPreview.Result> delivered=new ArrayList<>();
        LatestPreview latest=new LatestPreview(worker,main,delivered::add);
        latest.request(request("A",1)); worker.drain(); latest.invalidate();
        latest.request(request("B",2)); latest.request(request("A",3));
        equal(1,main.tasks.size(),"helper one callback during replace"); equal(0,worker.tasks.size(),"helper delivery backpressure");
        main.drain(); worker.drain(); main.drain(); equal(1,delivered.size(),"helper A B A single result");
        equal(3L,delivered.get(0).request.revision,"helper newest revision");
        latest.request(request("queued",4)); latest.close(); worker.drain(); main.drain();
        latest.request(request("after close",5)); equal(1,delivered.size(),"helper shutdown rejects queued and future");
        // A rejected executor must not retain/admit another snapshot.
        PreviewHarness.QueueExecutor rejected=new PreviewHarness.QueueExecutor(); rejected.shutdown();
        LatestPreview rejectedWork=new LatestPreview(rejected,main,delivered::add);
        rejectedWork.request(request("rejected",6)); rejectedWork.request(request("retry",7));
        equal(0,rejected.tasks.size(),"worker rejection closes helper");
        PreviewHarness.QueueExecutor completedWorker=new PreviewHarness.QueueExecutor();
        LatestPreview rejectedMain=new LatestPreview(completedWorker,rejected,delivered::add);
        rejectedMain.request(request("rejected completion",8)); completedWorker.drain();
        rejectedMain.request(request("retry",9)); equal(0,completedWorker.tasks.size(),"completion rejection closes helper");
    }

    static void realWorkerConcurrency() throws Exception {
        ExecutorService worker=java.util.concurrent.Executors.newSingleThreadExecutor();
        BlockingQueue<Runnable> completions=new LinkedBlockingQueue<>();
        List<LatestPreview.Result> accepted=new ArrayList<>();
        LatestPreview latest=new LatestPreview(worker,completions::add,accepted::add);
        try {
            latest.request(request("initial",0));
            Runnable old=completions.poll(10,TimeUnit.SECONDS); check(old!=null,"real worker completion");
            for(int i=1;i<=1000;i++) latest.request(request("latest " + i,i));
            equal(0,completions.size(),"no additional real completion before consume");
            old.run(); Runnable fresh=completions.poll(10,TimeUnit.SECONDS); check(fresh!=null,"real latest completion");
            fresh.run(); equal(1,accepted.size(),"real only latest accepted"); equal(1000L,accepted.get(0).request.revision,"real latest revision");
            latest.request(request("closing",1001)); latest.close();
            worker.shutdown(); check(worker.awaitTermination(10,TimeUnit.SECONDS),"real worker terminates");
            Runnable r; while((r=completions.poll())!=null) r.run(); equal(1,accepted.size(),"close during worker execution rejects");
        } finally { latest.close(); worker.shutdownNow(); }
    }

    static void boundedLargePendingMemory() {
        byte[] headroom=new byte[32*1024*1024]; headroom[0]=7;
        PreviewHarness.QueueExecutor worker=new PreviewHarness.QueueExecutor(), main=new PreviewHarness.QueueExecutor();
        LatestPreview latest=new LatestPreview(worker,main,result -> { throw new AssertionError("stale delivery"); });
        latest.request(request("small active HTML",0)); worker.drain();
        for(int i=1;i<=32;i++) {
            char[] chars=new char[8*1024*1024]; Arrays.fill(chars,(char)('a'+i%26));
            latest.request(request(new String(chars),i));
        }
        equal(1,main.tasks.size(),"large pending snapshots do not retain many deliveries");
        equal(0,worker.tasks.size(),"large pending snapshots stay out of executor queue");
        latest.close(); main.drain(); equal(7,(int)headroom[0],"32 MiB headroom retained");
    }

    public static void main(String[] args) throws Exception {
        debounceAndCopies(); staleBeforeDebounceAndSuppression(); boundedPendingAndDelivery();
        transitions(); acceptanceGuards(); scrollAndShutdown(); fullHtmlParity(); helperLifecycle(); realWorkerConcurrency(); boundedLargePendingMemory();
        System.out.println("Preview production watcher/scheduler + helper: " + checks + " checks PASS");
    }
}
