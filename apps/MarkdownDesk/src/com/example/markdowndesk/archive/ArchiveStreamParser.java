package com.example.markdowndesk.archive;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.charset.CodingErrorAction;
import java.util.Objects;

/** Bounded-memory parser for linear LLM exports and ChatGPT conversation graphs.
 * No Android dependency; suitable for host tests and Android 26+.
 * The caller persists each text segment immediately, rather than holding whole conversations.
 * It accepts a top-level conversation array or an object with a conversations array.
 */
public final class ArchiveStreamParser {
    public static final int SEGMENT_CHARS = 4096;
    public static final int MAX_NESTING = 160;
    public interface Sink {
        void beginConversation(int ordinal) throws IOException;
        void beginMessage(int ordinal) throws IOException;
        void textSegment(String text) throws IOException;
        void endMessage(MessageInfo message) throws IOException;
        void endConversation(ConversationInfo conversation) throws IOException;
    }
    public static final class ConversationInfo {
        public String id, title, createdAt, updatedAt;
        public int ordinal, messageCount;
    }
    public static final class MessageInfo {
        public String id, sender, createdAt, updatedAt;
        public int ordinal;
        public boolean textPresent, unsupportedContent;
    }
    private interface FieldHandler { void read(String key) throws IOException; }
    private interface ElementHandler { void read(int index) throws IOException; }
    private interface ChunkHandler { void accept(String chunk) throws IOException; }
    private final Reader input;
    private Sink sink;
    private int lookahead = Integer.MIN_VALUE;
    private int depth = 0;
    private int totalConversations = 0;

    /** One strict decoder for original source bytes: replacement characters would
     * silently change quoted passages, exported text, search and note anchors.
     */
    public static Reader strictUtf8Reader(InputStream bytes) {
        return new InputStreamReader(Objects.requireNonNull(bytes),
                StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT));
    }

    public ArchiveStreamParser(Reader input, Sink sink) {
        this.input = new BufferedReader(Objects.requireNonNull(input), 32 * 1024);
        this.sink = Objects.requireNonNull(sink);
    }

    public int parse() throws IOException {
        if (peek() == '\ufeff') consume();
        skipWhitespace();
        if (peek() == '[') conversations();
        else if (peek() == '{') conversation(true);
        else throw error("Expected a conversation export; open arbitrary JSON in Inspector");
        skipWhitespace();
        if (peek() != -1) throw error("Unexpected data after JSON root");
        if (totalConversations == 0) throw error("No supported conversations found; open this JSON in Inspector");
        return totalConversations;
    }

    private void conversations() throws IOException {
        array(index -> {
            if (peek() != '{') throw error("Conversation entry is not an object");
            conversation(false);
        });
    }

    private void conversation(boolean wrapperAllowed) throws IOException {
        ConversationInfo info = new ConversationInfo();
        final boolean[] messages = {false}, wrapper = {false};
        final String[] current = {null};
        final Graph[] graph = {null};
        try {
            object(key -> {
                switch (key) {
                    case "uuid": case "id": if(info.id == null) info.id = scalar(512); else skipValue(); break;
                    case "name": case "title": if(info.title == null) info.title = scalar(8192); else skipValue(); break;
                    case "created_at": info.createdAt = scalar(256); break;
                    case "create_time": info.createdAt = epochDate(scalar(256)); break;
                    case "updated_at": info.updatedAt = scalar(256); break;
                    case "update_time": info.updatedAt = epochDate(scalar(256)); break;
                    case "current_node": current[0] = scalar(512); break;
                    case "conversations":
                        if (!wrapperAllowed || wrapper[0] || messages[0] || graph[0] != null || peek() != '[')
                            throw error("Ambiguous conversation wrapper");
                        wrapper[0] = true; conversations(); break;
                    case "mapping":
                        if (messages[0] || wrapper[0] || graph[0] != null || peek() != '{')
                            throw error("Ambiguous conversation mapping");
                        graph[0] = new Graph();
                        readGraph(graph[0]); break;
                    case "chat_messages": case "messages":
                        if (messages[0] || wrapper[0] || graph[0] != null || peek() != '[')
                            throw error("Ambiguous or invalid message array");
                        messages[0] = true;
                        info.ordinal = totalConversations++;
                        sink.beginConversation(info.ordinal);
                        array(messageIndex -> {
                            if (peek() != '{') throw error("Message entry is not an object");
                            message(info.messageCount++);
                        });
                        break;
                    default: skipValue();
                }
            });
            if (info.id == null || info.id.isEmpty()) info.id = "missing-id-" + (messages[0] ? info.ordinal : totalConversations);
            if (info.title == null || info.title.isEmpty()) info.title = "Untitled conversation " + (info.ordinal + 1);
            if (graph[0] != null) graph[0].emit(info, current[0]);
            else if (messages[0]) sink.endConversation(info);
            else if (!wrapper[0]) throw error("No supported message structure; open this JSON in Inspector");
        } finally {
            if (graph[0] != null) graph[0].close();
        }
    }

    /** Disk-backed text, bounded graph metadata. Each leaf is a complete branch,
     * with the selected branch first. Nothing is silently discarded on import. */
    private final class Graph implements java.io.Closeable {
        final java.io.File file;
        final java.io.RandomAccessFile text;
        final java.util.LinkedHashMap<String, Node> nodes = new java.util.LinkedHashMap<>();
        Graph() throws IOException {
            file = java.io.File.createTempFile("markdown-desk-graph-", ".tmp");
            try { text = new java.io.RandomAccessFile(file, "rw"); }
            catch(IOException e) { file.delete(); throw e; }
        }
        void emit(ConversationInfo original, String current) throws IOException {
            if (nodes.isEmpty()) throw error("Empty conversation mapping");
            java.util.HashSet<String> parents = new java.util.HashSet<>();
            for (Node n : nodes.values()) if (n.parent != null) {
                if (!nodes.containsKey(n.parent)) throw error("Missing conversation parent: " + n.parent);
                parents.add(n.parent);
            }
            if (current != null && !nodes.containsKey(current)) throw error("Missing selected conversation branch");
            java.util.ArrayList<String> ends = new java.util.ArrayList<>();
            if (current != null) ends.add(current);
            for (String id : nodes.keySet()) if (!parents.contains(id) && !id.equals(current)) ends.add(id);
            if (ends.isEmpty()) throw error("Conversation mapping has no branch endpoint");
            java.util.HashSet<String> covered = new java.util.HashSet<>();
            long emitted = 0;
            for (String end : ends) {
                java.util.ArrayList<Node> path = new java.util.ArrayList<>();
                java.util.HashSet<String> seen = new java.util.HashSet<>();
                for (Node n = nodes.get(end); n != null; n = nodes.get(n.parent)) {
                    if (!seen.add(n.id)) throw error("Cycle in conversation mapping");
                    path.add(n); covered.add(n.id);
                }
                if ((emitted += path.size()) > 1_000_000L) throw error("Conversation branch expansion exceeds safety limit");
                ConversationInfo info = new ConversationInfo();
                info.ordinal = totalConversations++;
                info.id = original.id + "/branch/" + end;
                info.title = original.title + (end.equals(current) ? " [selected branch]" : " [branch " + end + "]");
                info.createdAt = original.createdAt; info.updatedAt = original.updatedAt;
                sink.beginConversation(info.ordinal);
                for (int i = path.size()-1; i >= 0; i--) {
                    if (Thread.currentThread().isInterrupted()) throw error("Import cancelled");
                    Node n = path.get(i);
                    if (n.message == null) continue;
                    n.message.ordinal = info.messageCount++;
                    sink.beginMessage(n.message.ordinal);
                    text.seek(n.start);
                    while (text.getFilePointer() < n.end) {
                        if (Thread.currentThread().isInterrupted()) throw error("Import cancelled");
                        int length = text.readInt();
                        byte[] bytes = new byte[length]; text.readFully(bytes);
                        sink.textSegment(new String(bytes, StandardCharsets.UTF_8));
                    }
                    sink.endMessage(n.message);
                }
                sink.endConversation(info);
            }
            if (covered.size() != nodes.size()) throw error("Disconnected cycle in conversation mapping");
        }
        public void close() throws IOException { try {text.close();} finally {file.delete();} }
    }
    private static final class Node {
        String id, parent; MessageInfo message; long start, end;
    }
    private void readGraph(Graph graph) throws IOException {
        object(id -> {
            if (graph.nodes.size() >= 100_000) throw error("Conversation mapping exceeds 100000-node safety limit");
            if (id.length() > 512) throw error("Conversation node identity exceeds safety limit");
            if (graph.nodes.containsKey(id)) throw error("Duplicate conversation node");
            if (peek() != '{') throw error("Invalid conversation node");
            Node node = new Node(); node.id = id;
            graph.nodes.put(id, node);
            object(key -> {
                if (key.equals("parent")) node.parent = scalar(512);
                else if (key.equals("message") && peek() != 'n') {
                    if (node.message != null || peek() != '{') throw error("Invalid graph message");
                    Sink destination = sink;
                    node.start = graph.text.getFilePointer();
                    sink = new Sink() {
                        public void beginConversation(int ordinal) {}
                        public void beginMessage(int ordinal) {}
                        public void textSegment(String chunk) throws IOException {
                            if (Thread.currentThread().isInterrupted()) throw error("Import cancelled");
                            long free = graph.file.getUsableSpace();
                            if (free > 0 && free < 64L * 1024 * 1024)
                                throw error("Insufficient temporary storage for conversation branches");
                            byte[] bytes = chunk.getBytes(StandardCharsets.UTF_8);
                            graph.text.writeInt(bytes.length); graph.text.write(bytes);
                        }
                        public void endMessage(MessageInfo info) { node.message = info; }
                        public void endConversation(ConversationInfo info) {}
                    };
                    try { message(0); } finally { sink = destination; }
                    node.end = graph.text.getFilePointer();
                    if (node.message.id.equals("missing-message-id-0")) node.message.id = id;
                } else skipValue();
            });
        });
    }

    private static String epochDate(String value) {
        if (value == null) return null;
        try { return java.time.Instant.ofEpochMilli(new java.math.BigDecimal(value)
                .multiply(java.math.BigDecimal.valueOf(1000)).setScale(0, java.math.RoundingMode.DOWN).longValueExact()).toString(); }
        catch (RuntimeException invalid) { return value; }
    }

    private void message(int ordinal) throws IOException {
        MessageInfo info = new MessageInfo(); info.ordinal = ordinal;
        sink.beginMessage(ordinal);
        object(key -> {
            switch (key) {
                case "uuid": case "id": if(info.id == null) info.id = scalar(512); else skipValue(); break;
                case "sender": case "role": info.sender = scalar(256); break;
                case "created_at": info.createdAt = scalar(256); break;
                    case "create_time": info.createdAt = epochDate(scalar(256)); break;
                case "updated_at": info.updatedAt = scalar(256); break;
                    case "update_time": info.updatedAt = epochDate(scalar(256)); break;
                case "author":
                    if (peek() == '{') object(field -> {
                        if (field.equals("role")) info.sender = scalar(256); else skipValue();
                    }); else skipValue();
                    break;
                case "content": case "parts": content(info, 0); break;
                default: skipValue();
            }
        });
        if (info.id == null || info.id.isEmpty()) info.id = "missing-message-id-" + ordinal;
        if (info.sender == null || info.sender.isEmpty()) info.sender = "unknown";
        sink.endMessage(info);
    }

    private void content(MessageInfo info, int nesting) throws IOException {
        if (nesting > 32) throw error("Content nesting limit exceeded");
        skipWhitespace();
        int c = peek();
        if (c == '"') {
            if (info.textPresent) sink.textSegment("\n\n");
            info.textPresent = true;
            chunks(sink::textSegment);
        } else if (c == '[') {
            array(i -> content(info, nesting + 1));
        } else if (c == '{') {
            final boolean[] found = {false};
            final String[] type = {null};
            object(key -> {
                if (key.equals("type") && peek() == '"') type[0] = scalar(128);
                else if (key.equals("text") || key.equals("content") || key.equals("parts")) {
                    found[0] = true;
                    content(info, nesting + 1);
                } else skipValue();
            });
            if (!found[0]) {
                info.unsupportedContent = true;
                if (type[0] != null && !type[0].isEmpty()) {
                    if (info.textPresent) sink.textSegment("\n\n");
                    sink.textSegment("[Non-text block: " + safeLabel(type[0]) + "]");
                    info.textPresent = true;
                }
            }
        } else if (c == 'n') { literal("null"); }
        else { info.unsupportedContent = true; skipValue(); }
    }

    private static String safeLabel(String text) {
        StringBuilder result = new StringBuilder();
        for (int i=0;i<text.length() && result.length()<60;i++) {
            char c=text.charAt(i);
            result.append(Character.isLetterOrDigit(c) || c=='_' || c=='-' ? c : ' ');
        }
        return result.toString();
    }

    private void object(FieldHandler handle) throws IOException {
        enter('{');
        if (accept('}')) { leave(); return; }
        for (;;) {
            skipWhitespace();
            if (peek() != '"') throw error("Expected JSON object key");
            String key = string(8192);
            require(':');
            skipWhitespace();
            handle.read(key);
            if (accept('}')) break;
            require(',');
        }
        leave();
    }
    private void array(ElementHandler handle) throws IOException {
        enter('[');
        if (accept(']')) { leave(); return; }
        int index=0;
        for (;;) {
            skipWhitespace();
            handle.read(index++);
            if (accept(']')) break;
            require(',');
        }
        leave();
    }

    private void enter(char token) throws IOException {
        if (++depth > MAX_NESTING) throw error("JSON nesting limit exceeded");
        require(token);
    }
    private void leave() { depth--; }
    private void skipValue() throws IOException {
        skipWhitespace();
        int c=peek();
        if (c=='{') object(k -> skipValue());
        else if (c=='[') array(i -> skipValue());
        else if (c=='"') chunks(chunk -> {});
        else if (c=='n') literal("null");
        else if (c=='t') literal("true");
        else if (c=='f') literal("false");
        else if (c=='-' || Character.isDigit(c)) number();
        else throw error("Invalid JSON value");
    }
    private String scalar(int max) throws IOException {
        skipWhitespace();
        int c=peek();
        if(c=='"') return string(max);
        if(c=='n') { literal("null"); return null; }
        if(c=='t') { literal("true"); return "true"; }
        if(c=='f') { literal("false"); return "false"; }
        if(c=='-'||Character.isDigit(c)) return number();
        skipValue(); return null;
    }
    private String number() throws IOException {
        StringBuilder s=new StringBuilder();
        int c=peek();if(c=='-'){s.append((char)consume()); c=peek();}
        if(!Character.isDigit(c)) throw error("Invalid number");
        if(c=='0') s.append((char)consume());
        else while(Character.isDigit(peek())) s.append((char)consume());
        if(peek()=='.') {s.append((char)consume());if(!Character.isDigit(peek()))throw error("Invalid fraction");while(Character.isDigit(peek()))s.append((char)consume());}
        if(peek()=='e'||peek()=='E') {s.append((char)consume());if(peek()=='+'||peek()=='-')s.append((char)consume());if(!Character.isDigit(peek()))throw error("Invalid exponent");while(Character.isDigit(peek()))s.append((char)consume());}
        return s.toString();
    }
    private String string(int max) throws IOException {
        skipWhitespace();
        StringBuilder s=new StringBuilder();
        readString(chunk -> { if(s.length()+chunk.length()>max) throw error("Metadata field exceeds limit");s.append(chunk); });
        return s.toString();
    }
    private void chunks(ChunkHandler out) throws IOException { skipWhitespace(); readString(out); }
    private void readString(ChunkHandler out) throws IOException {
        require('"');
        StringBuilder buf = new StringBuilder(SEGMENT_CHARS);
        boolean awaitingLowSurrogate=false;
        while(true) {
            int n=consume();
            if(n<0)throw error("Unterminated string");
            if(n=='"')break;
            char ch;
            if(n=='\\') {
                int escaped=consume();
                switch(escaped) {
                    case '"':ch='"';break;case '\\':ch='\\';break;case '/':ch='/';break;
                    case 'b':ch='\b';break;case 'f':ch='\f';break;case 'n':ch='\n';break;
                    case 'r':ch='\r';break;case 't':ch='\t';break;
                    case 'u':
                        int code=0;
                        for(int i=0;i<4;i++){
                            int d=Character.digit(consume(),16);
                            if(d<0)throw error("Invalid Unicode escape");
                            code=(code<<4)|d;
                        }
                        ch=(char)code;break;
                    default:throw error("Invalid string escape");
                }
            } else {
                if(n<0x20) throw error("Control character inside string");
                ch=(char)n;
            }
            if(awaitingLowSurrogate && !Character.isLowSurrogate(ch))
                throw error("Unpaired high surrogate in JSON string");
            if(!awaitingLowSurrogate && Character.isLowSurrogate(ch))
                throw error("Unpaired low surrogate in JSON string");
            awaitingLowSurrogate=Character.isHighSurrogate(ch);
            buf.append(ch);
            if(buf.length()>=SEGMENT_CHARS && !Character.isHighSurrogate(buf.charAt(buf.length()-1))){
                out.accept(buf.toString());buf.setLength(0);
            }
        }
        if(awaitingLowSurrogate)throw error("Unpaired high surrogate in JSON string");
        if(buf.length()>0)out.accept(buf.toString());
    }
    private void literal(String expected)throws IOException {
        for(int i=0;i<expected.length();i++)if(consume()!=expected.charAt(i))throw error("Invalid literal");
    }
    private int peek()throws IOException {
        if(lookahead==Integer.MIN_VALUE)lookahead=input.read();
        return lookahead;
    }
    private int consume()throws IOException {int c=peek();lookahead=Integer.MIN_VALUE;return c;}
    private void skipWhitespace()throws IOException {while(peek()==' '||peek()=='\n'||peek()=='\r'||peek()=='\t')consume();}
    private boolean accept(char c)throws IOException {skipWhitespace();if(peek()==c){consume();return true;}return false;}
    private void require(char c)throws IOException {if(!accept(c))throw error("Expected '"+c+"'");}
    private IOException error(String reason){return new IOException(reason+" (depth="+depth+")");}
}