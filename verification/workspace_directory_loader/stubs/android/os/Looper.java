package android.os;
public final class Looper {
 private static final Looper MAIN=new Looper(Thread.currentThread());private final Thread thread;
 private Looper(Thread thread){this.thread=thread;}
 public static Looper getMainLooper(){return MAIN;}
 public Thread getThread(){return thread;}
}
