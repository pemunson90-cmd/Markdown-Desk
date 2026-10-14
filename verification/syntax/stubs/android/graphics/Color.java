package android.graphics;
public final class Color {
 public static int parseColor(String value){return (int)(0xff000000L|Long.parseLong(value.substring(1),16));}
}
