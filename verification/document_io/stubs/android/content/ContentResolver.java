package android.content;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import java.io.*;
/** Minimal provider boundary; concrete test resolvers control faults explicitly. */
public abstract class ContentResolver {
    public abstract InputStream openInputStream(Uri uri) throws FileNotFoundException;
    public abstract OutputStream openOutputStream(Uri uri, String mode) throws FileNotFoundException;
    public abstract ParcelFileDescriptor openFileDescriptor(Uri uri, String mode) throws FileNotFoundException;
    public abstract Cursor query(Uri uri, String[] projection, String selection, String[] args, String order);
}
