package android.content;
import android.net.Uri; import android.database.Cursor; import java.io.*;
public class ContentResolver {
    public InputStream openInputStream(Uri uri) throws FileNotFoundException {
        return new java.io.ByteArrayInputStream(uri.payload == null ? new byte[0] : uri.payload);
    }
    public String getType(Uri uri) { return uri.mime; }
    public Cursor query(Uri u, String[] p, String s, String[] a, String o) { return null; }
}
