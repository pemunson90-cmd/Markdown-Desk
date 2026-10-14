package com.example.markdowndesk;

import android.content.Context;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.json.JSONException;
import org.json.JSONObject;

/* JADX INFO: loaded from: classes.dex */
public final class RecoveryStore {
    private static final String CONTENT = "recovery-draft.md";
    private static final String META = "recovery-meta.json";

    public static final class Draft {
        public String baselineHash;
        public long baselineModified;
        public String displayName;
        public boolean readOnly;
        public int selectionEnd;
        public int selectionStart;
        public String text;
        public long timestamp;
        public String uri;
        public boolean utf8Bom;
    }

    private RecoveryStore() {
    }

    public static synchronized void write(Context context, Draft draft) throws IOException {
        // One envelope commits content and metadata together. Keep legacy pairs readable.
        JSONObject value = new JSONObject();
        try {
            value.put("version", 2);
            value.put("text", draft.text);
            value.put("displayName", draft.displayName);
            value.put("uri", draft.uri == null ? JSONObject.NULL : draft.uri);
            value.put("utf8Bom", draft.utf8Bom);
            value.put("baselineHash", draft.baselineHash == null ? JSONObject.NULL : draft.baselineHash);
            value.put("baselineModified", draft.baselineModified);
            value.put("readOnly", draft.readOnly);
            value.put("selectionStart", draft.selectionStart);
            value.put("selectionEnd", draft.selectionEnd);
            value.put("timestamp", draft.timestamp);
        } catch (JSONException e) { throw new IOException(e); }
        File target = new File(context.getFilesDir(), "recovery-v2.json");
        File staged = new File(context.getFilesDir(), "recovery-v2.json.tmp");
        try (FileOutputStream output = new FileOutputStream(staged)) {
            output.write(value.toString().getBytes(StandardCharsets.UTF_8));
            output.getFD().sync();
        }
        AtomicCommit.replace(staged, target);
    }

    public static synchronized Draft read(Context context) throws IOException {
        File file = new File(context.getFilesDir(), CONTENT);
        File file2 = new File(context.getFilesDir(), META);
        File envelope = new File(context.getFilesDir(), "recovery-v2.json");
        if (!envelope.isFile() && (!file.isFile() || !file2.isFile())) {
            return null;
        }
        try {
            Draft draft = new Draft();
            JSONObject jSONObject = new JSONObject(readUtf8(envelope.isFile() ? envelope : file2));
            draft.text = envelope.isFile() ? jSONObject.getString("text") : readUtf8(file);
            draft.displayName = jSONObject.optString("displayName", "Recovered draft.md");
            draft.uri = jSONObject.isNull("uri") ? null : jSONObject.optString("uri", null);
            draft.utf8Bom = jSONObject.optBoolean("utf8Bom", false);
            draft.baselineHash = jSONObject.isNull("baselineHash") ? null : jSONObject.optString("baselineHash", null);
            draft.baselineModified = jSONObject.optLong("baselineModified", -1L);
            draft.readOnly = jSONObject.optBoolean("readOnly", false);
            draft.selectionStart = jSONObject.optInt("selectionStart", 0);
            draft.selectionEnd = jSONObject.optInt("selectionEnd", draft.selectionStart);
            draft.timestamp = jSONObject.optLong("timestamp", (envelope.isFile() ? envelope : file).lastModified());
            return draft;
        } catch (JSONException e) {
            throw new IOException("Recovery metadata is malformed.", e);
        }
    }

    public static synchronized void clear(Context context) {
        new File(context.getFilesDir(), CONTENT).delete();
        new File(context.getFilesDir(), META).delete();
        new File(context.getFilesDir(), "recovery-draft.md.tmp").delete();
        new File(context.getFilesDir(), "recovery-meta.json.tmp").delete();
        new File(context.getFilesDir(), "recovery-v2.json.tmp").delete();
        new File(context.getFilesDir(), "recovery-v2.json").delete();
    }

    private static String readUtf8(File file) throws IOException {
        FileInputStream fileInputStream = new FileInputStream(file);
        try {
            int length = (int) file.length();
            byte[] bArr = new byte[length];
            int i = 0;
            while (i < length) {
                int i2 = fileInputStream.read(bArr, i, length - i);
                if (i2 < 0) {
                    break;
                }
                i += i2;
            }
            String str = new String(bArr, 0, i, StandardCharsets.UTF_8);
            fileInputStream.close();
            return str;
        } catch (Throwable th) {
            try {
                fileInputStream.close();
            } catch (Throwable th2) {
                th.addSuppressed(th2);
            }
            throw th;
        }
    }

}
