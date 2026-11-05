package android.content;
public final class Context {private final SharedPreferences preferences;public Context(SharedPreferences p){preferences=p;}public SharedPreferences getSharedPreferences(String name,int mode){if(!name.equals("workspace_favorites")||mode!=0)throw new AssertionError("unrelated preferences access");return preferences;}}
