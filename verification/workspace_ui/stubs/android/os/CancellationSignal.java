package android.os;
/** Host cancellation boundary with a provider listener that may block. */
public final class CancellationSignal {
 public interface OnCancelListener {void onCancel();}
 private volatile boolean cancelled;private OnCancelListener listener;
 public void cancel(){OnCancelListener current;synchronized(this){if(cancelled)return;cancelled=true;current=listener;}if(current!=null)current.onCancel();}
 public void setOnCancelListener(OnCancelListener value){boolean already;synchronized(this){listener=value;already=cancelled;}if(already&&value!=null)value.onCancel();}
 public void throwIfCanceled(){if(cancelled)throw new OperationCanceledException();}
}
