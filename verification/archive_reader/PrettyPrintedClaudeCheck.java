package com.example.markdowndesk.archive;

import java.io.StringReader;
import java.io.IOException;
import java.util.ArrayList;

/** Regression: the real owner export is formatted; the code34 parser rejected
 * valid whitespace with "Expected JSON object key (depth=2)".
 */
public final class PrettyPrintedClaudeCheck {
    private static final class Collect implements ArchiveStreamParser.Sink {
        int chats, messages;
        final ArrayList<String> text = new ArrayList<>();
        public void beginConversation(int ordinal) {}
        public void beginMessage(int ordinal) {}
        public void textSegment(String part) { text.add(part); }
        public void endMessage(ArchiveStreamParser.MessageInfo item) { messages++; }
        public void endConversation(ArchiveStreamParser.ConversationInfo item) { chats++; }
    }
    private static void check(boolean ok, String why) {
        if (!ok) throw new AssertionError(why);
    }
    public static void main(String[] args) throws Exception {
        String messages = "[\n {\n  \"uuid\": \"chat1\",\n  \"name\": \"One\",\n  \"chat_messages\": [\n" +
                "   {\n    \"uuid\": \"m1\",\n    \"sender\": \"human\",\n" +
                "    \"content\": [\n      {\"type\": \"text\", \"text\": \"first\"},\n" +
                "      {\"type\": \"text\", \"text\": \"second\"}\n    ]\n   },\n" +
                "   {\"uuid\": \"m2\", \"sender\": \"assistant\", \"content\": \"reply\"}\n  ]\n },\n" +
                " {\"uuid\": \"chat2\", \"name\": \"Two\", \"chat_messages\": []}\n]";
        for (String doc : new String[] { messages, "{\n\"metadata\": true,\n\"conversations\": " + messages + "\n}" }) {
            Collect sink = new Collect();
            int n = new ArchiveStreamParser(new StringReader(doc), sink).parse();
            check(n == 2 && sink.chats == 2, "both chats imported");
            check(sink.messages == 2, "both messages imported");
            String result = String.join("", sink.text);
            check(result.contains("first") && result.contains("second") && result.contains("reply"),
                    "all content blocks imported, not silently skipped");
        }
        System.out.println("PASS: pretty-printed Claude JSON and wrapped export");
    }
}
