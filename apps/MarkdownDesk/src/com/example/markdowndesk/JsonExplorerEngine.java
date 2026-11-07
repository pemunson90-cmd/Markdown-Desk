package com.example.markdowndesk;

import android.util.JsonReader;
import android.util.JsonToken;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Read-only, forward-only JSON navigation. It never reads a whole export into memory.
 * Each requested page or branch reopens the SAF document and visits only the path
 * necessary to display that page. Closing a page before EOF does not validate the
 * unread suffix; malformed content encountered while navigating is reported.
 */
final class JsonExplorerEngine {
    static final int PAGE_SIZE = 100;
    static final int MAX_TRANSCRIPT_MESSAGES = 200;
    private static final int TRANSCRIPT_CHAR_LIMIT = 600_000;

    interface StreamSource { InputStream open() throws IOException; }

    static final class Step {
        final String name;
        final int index;
        private Step(String name, int index) { this.name=name; this.index=index; }
        static Step field(String name) { return new Step(name, -1); }
        static Step element(int index) { return new Step(null, index); }
    }

    static final class Entry {
        final Step step;
        final String title;
        final String detail;
        Entry(Step step, String title, String detail) {
            this.step=step; this.title=title; this.detail=detail;
        }
    }

    static final class Page {
        final ArrayList<Entry> entries = new ArrayList<>();
        String type;
        String value;
        boolean hasMore;
        boolean conversation;
        String messagesKey;
        int offset;
    }

    static String jsonPath(List<Step> path) {
        StringBuilder out = new StringBuilder("$");
        for (Step step : path) {
            if (step.name != null) {
                out.append("[\"").append(step.name.replace("\\", "\\\\").replace("\"", "\\\"")).append("\"]");
            } else out.append("[").append(step.index).append("]");
        }
        return out.toString();
    }

    private static JsonReader reader(StreamSource source) throws IOException {
        InputStream stream = source.open();
        if (stream == null) throw new IOException("The document provider did not supply a readable stream.");
        // SAF providers may return fewer than three bytes per read, even when
        // a UTF-8 BOM is present. Preserve all non-BOM input exactly.
        InputStream input=JsonUtf8Bom.strip(stream);
        JsonReader json = new JsonReader(new InputStreamReader(input,
                StandardCharsets.UTF_8.newDecoder()
                        .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                        .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)));
        json.setLenient(false);
        return json;
    }

    private static void seek(JsonReader json, List<Step> path) throws IOException {
        for (Step step : path) {
            if (step.name != null) {
                if (json.peek() != JsonToken.BEGIN_OBJECT)
                    throw new IOException("The selected JSON field is no longer an object.");
                json.beginObject();
                boolean found = false;
                while (json.hasNext()) {
                    String name=json.nextName();
                    if (name.equals(step.name)) { found=true; break; }
                    json.skipValue();
                    if (Thread.currentThread().isInterrupted()) throw new IOException("Navigation cancelled.");
                }
                if (!found) throw new IOException("JSON field missing: " + step.name);
            } else {
                if (json.peek() != JsonToken.BEGIN_ARRAY)
                    throw new IOException("The selected JSON item is no longer an array.");
                json.beginArray();
                for (int i=0; i<step.index; i++) {
                    if (!json.hasNext()) throw new IOException("JSON array item no longer exists.");
                    json.skipValue();
                    if (Thread.currentThread().isInterrupted()) throw new IOException("Navigation cancelled.");
                }
                if (!json.hasNext()) throw new IOException("JSON array item no longer exists.");
            }
        }
    }

    static Page scan(StreamSource source, List<Step> path, int offset) throws IOException {
        Page page = new Page();
        page.offset=offset;
        try (JsonReader json = reader(source)) {
            seek(json, path);
            JsonToken token=json.peek();
            if (token == JsonToken.BEGIN_ARRAY) {
                page.type="Array";
                json.beginArray();
                int index=0;
                while (index<offset && json.hasNext()) {
                    json.skipValue(); index++;
                    if (Thread.currentThread().isInterrupted()) throw new IOException("Navigation cancelled.");
                }
                while (page.entries.size()<PAGE_SIZE && json.hasNext()) {
                    page.entries.add(new Entry(Step.element(index), "#" + (index+1), summary(json)));
                    index++;
                    if (Thread.currentThread().isInterrupted()) throw new IOException("Navigation cancelled.");
                }
                page.hasMore=json.hasNext();
            } else if (token == JsonToken.BEGIN_OBJECT) {
                page.type="Object";
                json.beginObject();
                int index=0;
                while (index<offset && json.hasNext()) {
                    json.nextName(); json.skipValue(); index++;
                    if (Thread.currentThread().isInterrupted()) throw new IOException("Navigation cancelled.");
                }
                while (page.entries.size()<PAGE_SIZE && json.hasNext()) {
                    String key=json.nextName();
                    JsonToken valueType=json.peek();
                    if (("chat_messages".equals(key) || "messages".equals(key))
                            && valueType==JsonToken.BEGIN_ARRAY) {
                        page.conversation=true;
                        page.messagesKey=key;
                    }
                    page.entries.add(new Entry(Step.field(key), key, summary(json)));
                    if (Thread.currentThread().isInterrupted()) throw new IOException("Navigation cancelled.");
                }
                page.hasMore=json.hasNext();
            } else {
                page.type="Value";
                page.value=scalar(json);
            }
        } catch (IllegalStateException | StackOverflowError badJson) {
            throw new IOException("This JSON is malformed or too deeply nested.", badJson);
        }
        return page;
    }

    private static String scalar(JsonReader json) throws IOException {
        JsonToken token=json.peek();
        if (token==JsonToken.STRING || token==JsonToken.NUMBER) return json.nextString();
        if (token==JsonToken.BOOLEAN) return String.valueOf(json.nextBoolean());
        if (token==JsonToken.NULL) { json.nextNull(); return "null"; }
        throw new IOException("Expected a JSON value.");
    }

    private static String shortText(String value, int limit) {
        if (value == null) return "";
        String clean=value.replace('\n',' ').replace('\r',' ').trim();
        return clean.length()>limit ? clean.substring(0,limit)+"…" : clean;
    }

    private static String summary(JsonReader json) throws IOException {
        JsonToken type=json.peek();
        if (type==JsonToken.BEGIN_ARRAY) { json.skipValue(); return "Array · tap to explore"; }
        if (type!=JsonToken.BEGIN_OBJECT) {
            return shortText(scalar(json), 200);
        }
        String name="", timestamp="", sender="", marker="";
        json.beginObject();
        while (json.hasNext()) {
            String key=json.nextName();
            JsonToken value=json.peek();
            boolean simple=value==JsonToken.STRING || value==JsonToken.NUMBER;
            if (simple && ("name".equals(key)||"title".equals(key))) {
                String candidate=shortText(json.nextString(),110);
                if (name.isEmpty()) name=candidate;
            } else if (simple && ("created_at".equals(key)||"updated_at".equals(key))) {
                if (timestamp.isEmpty()) timestamp=shortText(json.nextString(),48); else json.skipValue();
            } else if (simple && ("sender".equals(key)||"role".equals(key))) {
                if (sender.isEmpty()) sender=shortText(json.nextString(),25); else json.skipValue();
            } else if (simple && "type".equals(key)) {
                marker=shortText(json.nextString(),40);
            } else if (simple && "uuid".equals(key)) {
                if (name.isEmpty()) marker="ID " + shortText(json.nextString(),18); else json.skipValue();
            } else json.skipValue();
            if (Thread.currentThread().isInterrupted()) throw new IOException("Navigation cancelled.");
        }
        json.endObject();
        StringBuilder out=new StringBuilder("Object");
        if (!name.isEmpty()) out.append(" · ").append(name);
        if (!sender.isEmpty()) out.append(" · ").append(sender);
        if (!timestamp.isEmpty()) out.append(" · ").append(timestamp);
        if (!marker.isEmpty()) out.append(" · ").append(marker);
        return out.toString();
    }

    static String transcript(StreamSource source, List<Step> conversationPath, String messagesKey)
            throws IOException {
        StringBuilder result = new StringBuilder("# Claude conversation\n\n");
        int count=0;
        boolean more=false;
        try (JsonReader json=reader(source)) {
            seek(json, conversationPath);
            if (json.peek()!=JsonToken.BEGIN_OBJECT) throw new IOException("Select a conversation object.");
            json.beginObject();
            while (json.hasNext()) {
                String name=json.nextName();
                if (("name".equals(name)||"title".equals(name))
                        && json.peek()==JsonToken.STRING && count==0) {
                    String title=json.nextString();
                    result.replace(0, "# Claude conversation".length(), "# " + shortText(title, 300));
                } else if (messagesKey.equals(name) && json.peek()==JsonToken.BEGIN_ARRAY) {
                    json.beginArray();
                    while (json.hasNext() && count<MAX_TRANSCRIPT_MESSAGES
                            && result.length()<TRANSCRIPT_CHAR_LIMIT) {
                        result.append(readMessage(json, count+1));
                        count++;
                        if (Thread.currentThread().isInterrupted()) throw new IOException("Navigation cancelled.");
                    }
                    more=json.hasNext();
                    break; // Only this array is needed. Never require parsing the whole archive.
                } else json.skipValue();
            }
        } catch (IllegalStateException | StackOverflowError badJson) {
            throw new IOException("Unable to read this conversation safely.", badJson);
        }
        if (count==0) throw new IOException("No readable messages were found in this conversation.");
        if (more) result.append("\n_Only the first ").append(count)
                .append(" messages are shown. Browse the messages array for later messages._\n");
        return result.toString();
    }

    private static String readMessage(JsonReader json, int number) throws IOException {
        if (json.peek()!=JsonToken.BEGIN_OBJECT) { json.skipValue(); return ""; }
        String sender="Message "+number, text="", when="";
        json.beginObject();
        while (json.hasNext()) {
            String key=json.nextName();
            JsonToken value=json.peek();
            if (("sender".equals(key)||"role".equals(key)) && value==JsonToken.STRING) {
                sender=shortText(json.nextString(),60);
            } else if ("created_at".equals(key) && value==JsonToken.STRING) {
                when=shortText(json.nextString(),70);
            } else if ("content".equals(key)) {
                text=readContent(json);
            } else json.skipValue();
        }
        json.endObject();
        if (text.isEmpty()) text="_[Non-text or empty message]_";
        if (text.length()>24000) text=text.substring(0,24000)+"\n_[Message truncated in preview]_";
        return "## " + sender + (when.isEmpty()?"":" · "+when) + "\n\n" + text + "\n\n";
    }

    private static String readContent(JsonReader json) throws IOException {
        JsonToken value=json.peek();
        if (value==JsonToken.STRING) return json.nextString();
        if (value==JsonToken.BEGIN_OBJECT) {
            json.beginObject();
            StringBuilder out=new StringBuilder();
            while (json.hasNext()) {
                String name=json.nextName();
                if (("text".equals(name)||"content".equals(name))
                        && out.length()<24000) {
                    String part=readContent(json);
                    if (!part.isEmpty()) out.append(part);
                } else json.skipValue();
            }
            json.endObject();
            return out.toString();
        }
        if (value==JsonToken.BEGIN_ARRAY) {
            json.beginArray();
            StringBuilder out=new StringBuilder();
            while (json.hasNext()) {
                if (out.length()>24000) { json.skipValue(); continue; }
                String part=readContent(json);
                if (!part.isEmpty()) {
                    if (out.length()>0) out.append("\n\n");
                    out.append(part);
                }
            }
            json.endArray();
            return out.toString();
        }
        json.skipValue();
        return "";
    }

    private JsonExplorerEngine() {}
}
