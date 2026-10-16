package android.content;
import android.net.Uri;import java.util.*;
public final class Intent {
 public static final String ACTION_CREATE_DOCUMENT="android.intent.action.CREATE_DOCUMENT",CATEGORY_OPENABLE="android.intent.category.OPENABLE",EXTRA_TITLE="android.intent.extra.TITLE";
 public String action,type,title;public Class<?> component;public int flags;public Uri data;public final Map<String,String> extras=new HashMap<>();
 public Intent(){}public Intent(String action){this.action=action;}public Intent(Object context,Class<?> component){this.component=component;}
 public Intent addCategory(String category){return this;}public Intent setType(String type){this.type=type;return this;}public Intent putExtra(String key,String value){extras.put(key,value);if(EXTRA_TITLE.equals(key))title=value;return this;}public String getStringExtra(String key){return extras.get(key);}public Intent addFlags(int flags){this.flags|=flags;return this;}public Intent setFlags(int flags){this.flags=flags;return this;}public Uri getData(){return data;}public Intent setData(Uri uri){data=uri;return this;}public int getFlags(){return flags;}
}
