package com.example.markdowndesk;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.DocumentsContract;
import android.provider.OpenableColumns;
import java.io.File;
import java.io.FileNotFoundException;
import java.util.Locale;

/**
 * Non-exported, same-app bridge: DocumentIO continues using ContentResolver for
 * both SAF documents and files in the shared Downloads directory.
 * The Android DownloadsProvider's privileged /all_downloads URI is NEVER reused.
 */
public final class DownloadsFileProvider extends ContentProvider {
    @Override public boolean onCreate() { return true; }

    @Override public String getType(Uri uri) {
        try {
            File file = DownloadsAccess.resolve(uri);
            String lower = file.getName().toLowerCase(Locale.ROOT);
            return lower.endsWith(".txt") || lower.endsWith(".text") ? "text/plain" : "text/markdown";
        } catch (FileNotFoundException unavailable) {
            return null;
        }
    }

    @Override public Cursor query(Uri uri, String[] projection, String selection,
                                  String[] selectionArgs, String sortOrder) {
        File file;
        try {
            file = DownloadsAccess.resolve(uri);
        } catch (FileNotFoundException unavailable) {
            return null;
        }
        String[] columns = projection == null
                ? new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE,
                DocumentsContract.Document.COLUMN_LAST_MODIFIED, DocumentsContract.Document.COLUMN_FLAGS}
                : projection;
        MatrixCursor cursor = new MatrixCursor(columns, 1);
        Object[] values = new Object[columns.length];
        for (int i = 0; i < columns.length; i++) {
            String col = columns[i];
            if (OpenableColumns.DISPLAY_NAME.equals(col)) values[i] = file.getName();
            else if (OpenableColumns.SIZE.equals(col)) values[i] = file.length();
            else if (DocumentsContract.Document.COLUMN_LAST_MODIFIED.equals(col)) values[i] = file.lastModified();
            else if (DocumentsContract.Document.COLUMN_FLAGS.equals(col)) {
                values[i] = file.canWrite() ? DocumentsContract.Document.FLAG_SUPPORTS_WRITE : 0;
            }
        }
        cursor.addRow(values);
        return cursor;
    }

    @Override public ParcelFileDescriptor openFile(Uri uri, String mode)
            throws FileNotFoundException {
        File file = DownloadsAccess.resolve(uri);
        int flags;
        switch (mode) {
            case "r": flags = ParcelFileDescriptor.MODE_READ_ONLY; break;
            case "rw": flags = ParcelFileDescriptor.MODE_READ_WRITE; break;
            case "rwt": flags = ParcelFileDescriptor.MODE_READ_WRITE
                                | ParcelFileDescriptor.MODE_TRUNCATE; break;
            case "wt": flags = ParcelFileDescriptor.MODE_WRITE_ONLY
                               | ParcelFileDescriptor.MODE_TRUNCATE; break;
            default: throw new FileNotFoundException("Unsupported Downloads access mode.");
        }
        // Deliberately no MODE_CREATE: a moved/deleted recent must not be recreated.
        return ParcelFileDescriptor.open(file, flags);
    }

    @Override public Uri insert(Uri uri, ContentValues values) {
        throw new UnsupportedOperationException("Create through explicit file operations, not provider insertion.");
    }
    @Override public int delete(Uri uri, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException("Downloads records cannot be deleted through this provider.");
    }
    @Override public int update(Uri uri, ContentValues values, String selection,
                                String[] selectionArgs) {
        throw new UnsupportedOperationException("Downloads metadata cannot be changed through this provider.");
    }
}
