package android.os;
import java.util.concurrent.ConcurrentLinkedQueue;
public final class Handler {public static boolean reject;public final ConcurrentLinkedQueue<Runnable> tasks=new ConcurrentLinkedQueue<>();public Handler(Looper loop){}public boolean post(Runnable task){if(reject)return false;tasks.add(task);return true;}public void drain(){Runnable task;while((task=tasks.poll())!=null)task.run();}}
