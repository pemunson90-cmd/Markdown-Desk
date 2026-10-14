package android.net;
/** Host identifier only; does not implement Android URI/grant semantics. */
public final class Uri {
    private final String value;
    private Uri(String value) { this.value = value; }
    public static Uri parse(String value) { return new Uri(value); }
    @Override public String toString() { return value; }
}
