package android.content;
import java.util.*;
public interface SharedPreferences {
    interface Editor { Editor putString(String k, String v); Editor remove(String k); void apply(); }
    String getString(String k, String def);
    Editor edit();
}
