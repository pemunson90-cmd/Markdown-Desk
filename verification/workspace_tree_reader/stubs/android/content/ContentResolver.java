package android.content;import android.net.Uri;import android.database.Cursor;import android.os.CancellationSignal;
public abstract class ContentResolver {public abstract Cursor query(Uri u,String[] p,String s,String[] a,String o,CancellationSignal c);}
