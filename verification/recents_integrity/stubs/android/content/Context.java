package android.content;
public class Context {
    private final SharedPreferences prefs;
    public Context(SharedPreferences preferences) { prefs = preferences; }
    public SharedPreferences getSharedPreferences(String name, int mode) {
        if (!"markdown_desk".equals(name) || mode != 0) throw new AssertionError("Unexpected preferences");
        return prefs;
    }
}
