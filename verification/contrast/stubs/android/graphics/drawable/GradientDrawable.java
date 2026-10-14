// Recording HOST-ONLY drawable. Never compile this into Android application sources.
package android.graphics.drawable;
public class GradientDrawable {
 public int color, strokeColor, strokeWidth; public float radius;
 public void setColor(int value){color=value;}
 public void setCornerRadius(float value){radius=value;}
 public void setStroke(int width,int value){strokeWidth=width;strokeColor=value;}
}
