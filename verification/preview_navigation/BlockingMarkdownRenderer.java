package com.example.markdowndesk;
import java.util.concurrent.*;
/** Controlled renderer seam only. Real HTML/Unicode parity is in PrintCheck. */
final class MarkdownRenderer {
    static CountDownLatch entered,leave;static volatile int calls,active,maxActive;static volatile boolean fail;
    static String render(String source,UiTheme.Palette palette,float font,ReaderTypography typography){return render(source,palette,font);}
    static String render(String source,UiTheme.Palette palette,float font){
        calls++;active++;maxActive=Math.max(maxActive,active);
        try {if(entered!=null){entered.countDown();if(!leave.await(10,TimeUnit.SECONDS))throw new AssertionError("renderer latch timeout");}if(fail)throw new IllegalStateException("renderer fault");return "<!doctype html><html><head></head><body>captured</body></html>";}
        catch(InterruptedException e){throw new AssertionError("renderer unexpectedly interrupted",e);}finally{active--;}
    }
}
