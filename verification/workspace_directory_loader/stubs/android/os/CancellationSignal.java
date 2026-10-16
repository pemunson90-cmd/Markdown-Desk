package android.os;
/** Android-shaped listener seam for proving cancellation is outside broker monitor. */
public final class CancellationSignal {
 public interface OnCancelListener{void onCancel();}
 private volatile boolean cancelled;private OnCancelListener listener;
 public void cancel(){OnCancelListener callback;synchronized(this){if(cancelled)return;cancelled=true;callback=listener;}if(callback!=null)callback.onCancel();}
 public void setOnCancelListener(OnCancelListener next){boolean notify;synchronized(this){listener=next;notify=cancelled;}if(notify&&next!=null)next.onCancel();}
 public void throwIfCanceled(){if(cancelled)throw new OperationCanceledException();}
}
