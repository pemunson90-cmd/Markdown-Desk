package android.net;
public class Uri {
    public String scheme = "file"; public String last; public String mime; public byte[] payload;
    public static Uri of(String last, String mime, byte[] payload) { Uri u = new Uri(); u.last = last; u.mime = mime; u.payload = payload; return u; }
    public String getScheme() { return scheme; }
    public String getLastPathSegment() { return last; }
}
