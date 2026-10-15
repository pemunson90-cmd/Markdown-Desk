package android.print;
import android.os.*;
public abstract class PrintDocumentAdapter {
 public void onStart(){} public void onFinish(){}
 public abstract void onLayout(PrintAttributes old,PrintAttributes next,CancellationSignal signal,LayoutResultCallback callback,Bundle extras);
 public abstract void onWrite(PageRange[] pages,ParcelFileDescriptor fd,CancellationSignal signal,WriteResultCallback callback);
 // Match the SDK restriction: application code cannot construct/subclass these callbacks.
 public static class LayoutResultCallback {public int cancellations;public boolean fail;LayoutResultCallback(){}public void onLayoutCancelled(){cancellations++;if(fail)throw new IllegalStateException("callback");}}
 public static class WriteResultCallback {public int cancellations;public boolean fail;WriteResultCallback(){}public void onWriteCancelled(){cancellations++;if(fail)throw new IllegalStateException("callback");}}
}

