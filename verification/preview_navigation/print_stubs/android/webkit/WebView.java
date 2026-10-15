package android.webkit;
import android.app.Activity;import android.os.*;import android.print.*;import java.util.*;
public class WebView {
 public static final List<WebView> ALL=new ArrayList<>();public static boolean failCreate;public static java.util.function.Consumer<WebView> onCreate;
 public final Activity activity;public final WebSettings settings=new WebSettings();public WebViewClient client;public String html,base,title;public int destroys,probes;public boolean failDestroy,failLoad,failProbe,failAdapter,holdReplies=true;public final List<ValueCallback<String>> replies=new ArrayList<>();public final Delegate delegate=new Delegate();
 public WebView(Activity activity){if(failCreate)throw new IllegalStateException("create");this.activity=activity;ALL.add(this);if(onCreate!=null)onCreate.accept(this);}public WebSettings getSettings(){return settings;}public void setWebViewClient(WebViewClient c){client=c;}
 public void loadDataWithBaseURL(String base,String html,String mime,String encoding,String history){if(failLoad)throw new IllegalStateException("load");if(!mime.equals("text/html")||!encoding.equals("UTF-8")||history!=null)throw new AssertionError("load contract");this.base=base;this.html=html;}
 public void evaluateJavascript(String script,ValueCallback<String> reply){if(failProbe)throw new IllegalStateException("probe");probes++;replies.add(reply);}
 public String token(){var m=java.util.regex.Pattern.compile("data-md-preview=\"([^\"]+)\"").matcher(html);if(!m.find())throw new AssertionError("missing nonce");return m.group(1);}
 public void finish(){client.onPageFinished(this,base);}public void reply(String value){replies.remove(0).onReceiveValue(value);}public void ready(){finish();reply("\""+token()+"\"");}
 public PrintDocumentAdapter createPrintDocumentAdapter(String title){if(failAdapter)throw new IllegalStateException("adapter");this.title=title;return delegate;}public void destroy(){destroys++;if(failDestroy)throw new IllegalStateException("destroy");}
 public static class Delegate extends PrintDocumentAdapter {
  public int starts,finishes,layouts,writes;public boolean failStart,failFinish,failLayout,failWrite;public Runnable startHook,finishHook;public Object[] layoutArgs,writeArgs;
  public void onStart(){starts++;if(startHook!=null)startHook.run();if(failStart)throw new IllegalStateException("start");}
  public void onFinish(){finishes++;if(finishHook!=null)finishHook.run();if(failFinish)throw new IllegalStateException("finish");}
  public void onLayout(PrintAttributes old,PrintAttributes next,CancellationSignal signal,LayoutResultCallback cb,Bundle extras){layouts++;layoutArgs=new Object[]{old,next,signal,cb,extras};if(failLayout)throw new IllegalStateException("layout");}
  public void onWrite(PageRange[] pages,ParcelFileDescriptor fd,CancellationSignal signal,WriteResultCallback cb){writes++;writeArgs=new Object[]{pages,fd,signal,cb};if(failWrite)throw new IllegalStateException("write");}
 }
}

