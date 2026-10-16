package android.widget;
public final class Toast {
 public static final int LENGTH_SHORT=0;
 public static String latest;
 public static Toast makeText(Object owner,String text,int duration){latest=text;return new Toast();}
 public void show(){}
}
