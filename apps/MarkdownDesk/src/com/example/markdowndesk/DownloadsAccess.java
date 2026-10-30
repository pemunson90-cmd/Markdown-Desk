package com.example.markdowndesk;

import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.List;

/** App-internal Downloads URIs, never a bypass of the protected Android downloads provider. */
final class DownloadsAccess {
    static final String AUTHORITY = "com.example.markdowndesk.downloads";
    private DownloadsAccess() {}

    static boolean granted() {
        return Build.VERSION.SDK_INT >= 30 && Environment.isExternalStorageManager();
    }

    static File root() {
        return Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
    }

    static List<DownloadsPaths.Entry> list(String relative) throws IOException {
        if (!granted()) throw new SecurityException("Enable All files access to browse Downloads.");
        return DownloadsPaths.list(root(), relative);
    }

    static Uri uri(File file) throws IOException {
        if (!granted()) throw new SecurityException("Downloads access is not enabled.");
        String relative = DownloadsPaths.relative(root(), file);
        return new Uri.Builder().scheme("content").authority(AUTHORITY)
                .appendPath("file").appendPath(relative).build();
    }

    static File resolve(Uri uri) throws FileNotFoundException {
        if (!granted()) throw new SecurityException("Downloads access is not enabled.");
        if (uri == null || !"content".equals(uri.getScheme())
                || !AUTHORITY.equals(uri.getAuthority())) {
            throw new FileNotFoundException("Not a Markdown Desk Downloads document.");
        }
        List<String> segments = uri.getPathSegments();
        if (segments.size() != 2 || !"file".equals(segments.get(0))) {
            throw new FileNotFoundException("Invalid Downloads document URI.");
        }
        try {
            return DownloadsPaths.document(root(), segments.get(1));
        } catch (IOException unavailable) {
            FileNotFoundException result =
                    new FileNotFoundException("Downloaded file is missing or cannot be accessed.");
            result.initCause(unavailable);
            throw result;
        }
    }
}
