package com.example.markdowndesk;

import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

/* JADX INFO: loaded from: classes.dex */
public final class DocumentIO {
    public static final int MAX_DOCUMENT_BYTES = 8388608;
    private static final byte[] UTF8_BOM = {-17, -69, -65};

    public static final class Metadata {
        public boolean writable;
        public String displayName = "Document.md";
        public long size = -1;
        public long lastModified = -1;
    }

    private DocumentIO() {
    }

    public static final class ReadResult {
        public final byte[] sourceBytes;
        public final String text;
        public final boolean utf8Bom;

        ReadResult(String str, byte[] bArr, boolean z) {
            this.text = str;
            this.sourceBytes = bArr;
            this.utf8Bom = z;
        }
    }

    /* JADX WARN: Code duplicated, block: B:16:0x002f  */
    public static ReadResult read(ContentResolver contentResolver, Uri uri) throws IOException {
        boolean z = false;
        InputStream inputStreamOpenInputStream = contentResolver.openInputStream(uri);
        try {
            if (inputStreamOpenInputStream == null) {
                throw new FileNotFoundException("The document provider returned no input stream.");
            }
            byte[] bytes = readBytes(inputStreamOpenInputStream);
            if (inputStreamOpenInputStream != null) {
                inputStreamOpenInputStream.close();
            }
            if (bytes.length >= 3 && bytes[0] == UTF8_BOM[0]) {
                z = bytes[1] == UTF8_BOM[1] && bytes[2] == UTF8_BOM[2];
            }
            int i = z ? 3 : 0;
            try {
                return new ReadResult(StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes, i, bytes.length - i)).toString(), bytes, z);
            } catch (CharacterCodingException e) {
                throw new IOException("This file is not valid UTF-8 text. Markdown Desk left it unchanged.", e);
            }
        } catch (Throwable th) {
            if (inputStreamOpenInputStream != null) {
                try {
                    inputStreamOpenInputStream.close();
                } catch (Throwable th2) {
                    th.addSuppressed(th2);
                }
            }
            throw th;
        }
    }

    static byte[] readBytes(InputStream inputStream) throws IOException {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        byte[] bArr = new byte[32768];
        int i = 0;
        while (true) {
            int i2 = inputStream.read(bArr);
            if (i2 != -1) {
                if (i > MAX_DOCUMENT_BYTES - i2) {
                    throw new IOException("This document is larger than the 8 MB safety limit. Markdown Desk left it unchanged.");
                }
                byteArrayOutputStream.write(bArr, 0, i2);
                i += i2;
            } else {
                return byteArrayOutputStream.toByteArray();
            }
        }
    }

    public static byte[] encode(String str, boolean z) {
        byte[] bytes = str.getBytes(StandardCharsets.UTF_8);
        if (!z) {
            return bytes;
        }
        byte[] bArr = new byte[bytes.length + 3];
        System.arraycopy(UTF8_BOM, 0, bArr, 0, 3);
        System.arraycopy(bytes, 0, bArr, 3, bytes.length);
        return bArr;
    }

    /** Capture valid Unicode without replacement characters; the legacy helper remains compatible. */
    public static byte[] encodeStrict(String text, boolean bom) throws IOException {
        if (text == null) throw new IOException("No edited text is available.");
        if (text.length() > MAX_DOCUMENT_BYTES) throw new IOException("The edit exceeds the 8 MiB safety limit.");
        ByteBuffer encoded = StandardCharsets.UTF_8.newEncoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).encode(CharBuffer.wrap(text));
        int length = encoded.remaining();
        if (length > MAX_DOCUMENT_BYTES - (bom ? 3 : 0)) throw new IOException("The edit exceeds the 8 MiB safety limit.");
        byte[] result = new byte[length + (bom ? 3 : 0)];
        if (bom) System.arraycopy(UTF8_BOM, 0, result, 0, 3);
        encoded.get(result, bom ? 3 : 0, length);
        return result;
    }

    public static void write(ContentResolver contentResolver, Uri uri, byte[] bArr) throws IOException {
        // Count encoded bytes (including any BOM) before any destructive provider open.
        if (bArr.length > MAX_DOCUMENT_BYTES) {
            throw new IOException("This document is larger than the 8 MB safety limit. Markdown Desk left it unchanged.");
        }
        ParcelFileDescriptor parcelFileDescriptorOpenFileDescriptor;
        try {
            parcelFileDescriptorOpenFileDescriptor = contentResolver.openFileDescriptor(uri, "rwt");
        } catch (Exception e) {
            parcelFileDescriptorOpenFileDescriptor = null;
        }
        if (parcelFileDescriptorOpenFileDescriptor != null) {
            try {
                FileOutputStream fileOutputStream = new FileOutputStream(parcelFileDescriptorOpenFileDescriptor.getFileDescriptor());
                try {
                    fileOutputStream.write(bArr);
                    fileOutputStream.flush();
                    fileOutputStream.getFD().sync();
                    fileOutputStream.close();
                    if (parcelFileDescriptorOpenFileDescriptor != null) {
                        parcelFileDescriptorOpenFileDescriptor.close();
                        return;
                    }
                    return;
                } catch (Throwable th) {
                    try {
                        fileOutputStream.close();
                    } catch (Throwable th2) {
                        th.addSuppressed(th2);
                    }
                    throw th;
                }
            } catch (Throwable th3) {
                if (parcelFileDescriptorOpenFileDescriptor != null) {
                    try {
                        parcelFileDescriptorOpenFileDescriptor.close();
                    } catch (Throwable th4) {
                        th3.addSuppressed(th4);
                    }
                }
                throw th3;
            }
        }
        OutputStream outputStreamOpenOutputStream = contentResolver.openOutputStream(uri, "wt");
        try {
            if (outputStreamOpenOutputStream == null) {
                throw new FileNotFoundException("The document provider returned no output stream.");
            }
            outputStreamOpenOutputStream.write(bArr);
            outputStreamOpenOutputStream.flush();
            if (outputStreamOpenOutputStream != null) {
                outputStreamOpenOutputStream.close();
            }
        } catch (Throwable th5) {
            if (outputStreamOpenOutputStream != null) {
                try {
                    outputStreamOpenOutputStream.close();
                } catch (Throwable th6) {
                    th5.addSuppressed(th6);
                }
            }
            throw th5;
        }
    }

    /**
     * Android providers are only required to support OpenableColumns (display
     * name and size). Other columns are optional and may make a combined
     * projection fail. Preserve the user-facing name independently of them.
     * Metadata inspection is read-only; it must never rename or write a file.
     */
    public static Metadata metadata(ContentResolver contentResolver, Uri uri) {
        Metadata metadata = new Metadata();
        try (Cursor cursor = contentResolver.query(uri, new String[]{"_display_name", "_size"}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int name = cursor.getColumnIndex("_display_name");
                int size = cursor.getColumnIndex("_size");
                if (name >= 0 && !cursor.isNull(name)) {
                    String reported = cursor.getString(name);
                    if (reported != null && !reported.trim().isEmpty()) metadata.displayName = reported;
                }
                if (size >= 0 && !cursor.isNull(size)) metadata.size = cursor.getLong(size);
            }
        } catch (Exception unavailable) {
            // The URI's provider may have revoked its grant or rejected inspection.
            // We must not claim that the content has been renamed.
        }
        // Optional provider capabilities are queried separately. An unsupported
        // projection must not wipe out the filename obtained above.
        try (Cursor cursor = contentResolver.query(uri, new String[]{"last_modified", "flags"}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int changed = cursor.getColumnIndex("last_modified");
                int flags = cursor.getColumnIndex("flags");
                if (changed >= 0 && !cursor.isNull(changed)) metadata.lastModified = cursor.getLong(changed);
                if (flags >= 0 && !cursor.isNull(flags))
                    metadata.writable = (cursor.getInt(flags) & 2) != 0;
            }
        } catch (Exception unavailable) {
            // Files/MediaStore/share providers need not implement these columns.
        }
        if (!metadata.writable) {
            // Existing non-truncating, read-write capability probe; never writes.
            try (ParcelFileDescriptor descriptor = contentResolver.openFileDescriptor(uri, "rw")) {
                metadata.writable = descriptor != null;
            } catch (Exception unavailable) {
                metadata.writable = false;
            }
        }
        return metadata;
    }

}
