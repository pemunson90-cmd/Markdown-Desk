package android.os;
import java.util.*;
public final class Handler {
 public static final List<Handler> ALL=new ArrayList<>();
 public static final class Task {public final Runnable r;public final long at;Task(Runnable r,long at){this.r=r;this.at=at;}}
 public final List<Task> tasks=new ArrayList<>(); public Handler(Looper l){ALL.add(this);} public Handler(){this(Looper.getMainLooper());}
 public synchronized boolean post(Runnable r){return postDelayed(r,0);} public synchronized boolean postDelayed(Runnable r,long delay){tasks.add(new Task(r,SystemClock.now+delay));return true;}
 public synchronized void removeCallbacks(Runnable r){tasks.removeIf(t->t.r==r);} public synchronized void removeCallbacksAndMessages(Object token){tasks.clear();}
 public void drain(){while(true){Task next; synchronized(this){next=tasks.stream().filter(t->t.at<=SystemClock.now).min(Comparator.comparingLong(t->t.at)).orElse(null);if(next==null)return;tasks.remove(next);}next.r.run();}}
 public synchronized Runnable takeFirstImmediate(){for(Task t:tasks)if(t.at<=SystemClock.now){tasks.remove(t);return t.r;}throw new AssertionError("no immediate callback");}
}

