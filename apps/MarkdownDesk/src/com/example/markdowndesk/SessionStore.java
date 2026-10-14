package com.example.markdowndesk;

import android.content.Context;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/* JADX INFO: loaded from: classes.dex */
public final class SessionStore {
    private static final String FILE = "open-tabs.json";

    public static final class State {
        public int activeIndex;
        public final List<DocumentTab> tabs = new ArrayList();
        public long timestamp;
    }

    private SessionStore() {
    }

    public static synchronized void write(Context context, List<DocumentTab> list, int i, long j) throws IOException {
        JSONObject jSONObject = new JSONObject();
        JSONArray jSONArray = new JSONArray();
        try {
            jSONObject.put("version", 1);
            jSONObject.put("activeIndex", i);
            jSONObject.put("timestamp", j);
            for (DocumentTab documentTab : list) {
                JSONObject jSONObject2 = new JSONObject();
                jSONObject2.put("id", documentTab.id);
                jSONObject2.put("uri", documentTab.uri == null ? JSONObject.NULL : documentTab.uri);
                jSONObject2.put("displayName", documentTab.displayName);
                jSONObject2.put("text", documentTab.text);
                jSONObject2.put("baselineHash", documentTab.baselineHash == null ? JSONObject.NULL : documentTab.baselineHash);
                jSONObject2.put("baselineModified", documentTab.baselineModified);
                jSONObject2.put("utf8Bom", documentTab.utf8Bom);
                jSONObject2.put("readOnly", documentTab.readOnly);
                jSONObject2.put("editorLocked", documentTab.editorLocked);
                jSONObject2.put("dirty", documentTab.dirty);
                jSONObject2.put("selectionStart", documentTab.selectionStart);
                jSONObject2.put("selectionEnd", documentTab.selectionEnd);
                jSONObject2.put("editorScrollY", documentTab.editorScrollY);
                jSONObject2.put("previewScrollY", documentTab.previewScrollY);
                jSONObject2.put("mode", documentTab.mode);
                jSONObject2.put("findQuery", documentTab.findQuery);
                jSONObject2.put("replaceQuery", documentTab.replaceQuery);
                jSONObject2.put("findCaseSensitive", documentTab.findCaseSensitive);
                jSONObject2.put("findWholeWord", documentTab.findWholeWord);
                jSONObject2.put("findRegex", documentTab.findRegex);
                jSONArray.put(jSONObject2);
            }
            jSONObject.put("tabs", jSONArray);
            File file = new File(context.getFilesDir(), FILE);
            File file2 = new File(context.getFilesDir(), "open-tabs.json.tmp");
            FileOutputStream fileOutputStream = new FileOutputStream(file2);
            try {
                fileOutputStream.write(jSONObject.toString().getBytes(StandardCharsets.UTF_8));
                fileOutputStream.getFD().sync();
                fileOutputStream.close();
                replace(file2, file);
            } catch (Throwable th) {
                try {
                    fileOutputStream.close();
                } catch (Throwable th2) {
                    th.addSuppressed(th2);
                }
                throw th;
            }
        } catch (JSONException e) {
            throw new IOException(e);
        }
    }

    public static synchronized State read(Context context) throws IOException {
        File file = new File(context.getFilesDir(), FILE);
        if (!file.isFile()) {
            return null;
        }
        try {
            JSONObject jSONObject = new JSONObject(readUtf8(file));
            JSONArray jSONArrayOptJSONArray = jSONObject.optJSONArray("tabs");
            if (jSONArrayOptJSONArray != null && jSONArrayOptJSONArray.length() != 0) {
                State state = new State();
                state.activeIndex = jSONObject.optInt("activeIndex", 0);
                state.timestamp = jSONObject.optLong("timestamp", file.lastModified());
                for (int i = 0; i < jSONArrayOptJSONArray.length(); i++) {
                    JSONObject jSONObjectOptJSONObject = jSONArrayOptJSONArray.optJSONObject(i);
                    if (jSONObjectOptJSONObject != null) {
                        DocumentTab documentTab = new DocumentTab();
                        documentTab.id = jSONObjectOptJSONObject.optString("id", documentTab.id);
                        documentTab.uri = jSONObjectOptJSONObject.isNull("uri") ? null : jSONObjectOptJSONObject.optString("uri", null);
                        documentTab.displayName = jSONObjectOptJSONObject.optString("displayName", "Document.md");
                        documentTab.text = jSONObjectOptJSONObject.optString("text", "");
                        documentTab.baselineHash = jSONObjectOptJSONObject.isNull("baselineHash") ? null : jSONObjectOptJSONObject.optString("baselineHash", null);
                        documentTab.baselineModified = jSONObjectOptJSONObject.optLong("baselineModified", -1L);
                        documentTab.utf8Bom = jSONObjectOptJSONObject.optBoolean("utf8Bom", false);
                        documentTab.readOnly = jSONObjectOptJSONObject.optBoolean("readOnly", false);
                        documentTab.editorLocked = jSONObjectOptJSONObject.optBoolean("editorLocked", false);
                        documentTab.dirty = jSONObjectOptJSONObject.optBoolean("dirty", false);
                        documentTab.selectionStart = jSONObjectOptJSONObject.optInt("selectionStart", 0);
                        documentTab.selectionEnd = jSONObjectOptJSONObject.optInt("selectionEnd", documentTab.selectionStart);
                        documentTab.editorScrollY = Math.max(0, jSONObjectOptJSONObject.optInt("editorScrollY", 0));
                        documentTab.previewScrollY = Math.max(0, jSONObjectOptJSONObject.optInt("previewScrollY", 0));
                        documentTab.mode = Math.max(0, Math.min(2, jSONObjectOptJSONObject.optInt("mode", 0)));
                        documentTab.findQuery = jSONObjectOptJSONObject.optString("findQuery", "");
                        documentTab.replaceQuery = jSONObjectOptJSONObject.optString("replaceQuery", "");
                        documentTab.findCaseSensitive = jSONObjectOptJSONObject.optBoolean("findCaseSensitive", false);
                        documentTab.findWholeWord = jSONObjectOptJSONObject.optBoolean("findWholeWord", false);
                        documentTab.findRegex = jSONObjectOptJSONObject.optBoolean("findRegex", false);
                        state.tabs.add(documentTab);
                    }
                }
                if (state.tabs.isEmpty()) {
                    return null;
                }
                state.activeIndex = Math.max(0, Math.min(state.activeIndex, state.tabs.size() - 1));
                return state;
            }
            return null;
        } catch (JSONException e) {
            throw new IOException("Open-tab session metadata is malformed.", e);
        }
    }

    public static synchronized void clear(Context context) {
        new File(context.getFilesDir(), FILE).delete();
        new File(context.getFilesDir(), "open-tabs.json.tmp").delete();
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

    private static void replace(File staged, File destination) throws IOException {
        AtomicCommit.replace(staged, destination);
    }
}
