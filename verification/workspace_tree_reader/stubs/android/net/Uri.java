package android.net;
public final class Uri {
 private final String value;private Uri(String v){value=v;}
 public static Uri parse(String v){return new Uri(v);}public String getScheme(){int i=value.indexOf(':');return i<0?null:value.substring(0,i);}
 public String getAuthority(){return value.startsWith("content://")?value.substring(10).split("/",2)[0]:null;}
 public String getLastPathSegment(){int i=value.lastIndexOf('/');return i<0?value:value.substring(i+1);}
 public boolean equals(Object o){return o instanceof Uri&&value.equals(((Uri)o).value);}
 public int hashCode(){return value.hashCode();}
 public String toString(){return value;}
}
