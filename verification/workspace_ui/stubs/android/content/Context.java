package android.content;
public class Context {
 private final SharedPreferences roots, favorites;private final ContentResolver resolver;
 public Context(SharedPreferences p,ContentResolver r){this(p,new MemoryPrefs(),r);}
 public Context(SharedPreferences p,SharedPreferences f,ContentResolver r){roots=p;favorites=f;resolver=r;}
 public SharedPreferences getSharedPreferences(String name,int mode){return "workspace_favorites".equals(name)?favorites:roots;}
 public ContentResolver getContentResolver(){return resolver;}
 private static final class MemoryPrefs implements SharedPreferences {
  String value;public String getString(String k,String d){return value==null?d:value;}
  public Editor edit(){return new Editor(){String next;public Editor putString(String k,String v){next=v;return this;}
   public boolean commit(){value=next;return true;}};}
 }
}
