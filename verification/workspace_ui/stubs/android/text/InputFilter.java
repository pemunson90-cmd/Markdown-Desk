package android.text;
public interface InputFilter {
 public static final class LengthFilter implements InputFilter {public final int max;public LengthFilter(int max){this.max=max;}}
}
