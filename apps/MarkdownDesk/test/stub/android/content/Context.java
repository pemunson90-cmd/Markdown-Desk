package android.content;
import java.io.File; import java.util.*;
public class Context {
    private final File files; private final Map<String,Map<String,String>> stores = new HashMap<>();
    public Context(File files) { this.files = files; }
    public File getFilesDir() { return files; }
    public ContentResolver getContentResolver() { return new ContentResolver(); }
    public android.content.res.AssetManager getAssets() { return new android.content.res.AssetManager(); }
    public SharedPreferences getSharedPreferences(final String name, int mode) {
        final Map<String,String> map = stores.computeIfAbsent(name, k -> new HashMap<>());
        return new SharedPreferences() {
            public String getString(String k, String def) { String v = map.get(k); return v == null ? def : v; }
            public Editor edit() { return new Editor() {
                public Editor putString(String k, String v) { map.put(k, v); return this; }
                public Editor remove(String k) { map.remove(k); return this; }
                public void apply() { }
            }; }
        };
    }
}
