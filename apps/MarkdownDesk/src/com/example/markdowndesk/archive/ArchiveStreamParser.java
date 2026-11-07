package com.example.markdowndesk.archive;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.charset.CodingErrorAction;
import java.util.Objects;

/** Bounded-memory, one-pass parser for Claude conversation exports.
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
    private final Sink sink;
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
        else if (peek() == '{') object(key -> {
            if (key.equals("conversations") && peek() == '[') conversations();
            else skipValue();
        });
        else throw error("Expected Claude export array or object");
        skipWhitespace();
        if (peek() != -1) throw error("Unexpected data after JSON root");
        if (totalConversations == 0) throw error("No conversations found in export");
        return totalConversations;
    }

    private void conversations() throws IOException {
        array(index -> {
            if (peek() != '{') { skipValue(); return; }
            ConversationInfo info = new ConversationInfo();
            info.ordinal = totalConversations++;
            sink.beginConversation(info.ordinal);
            object(key -> {
                switch (key) {
                    case "uuid": case "id": if(info.id == null) info.id = scalar(512); else skipValue(); break;
                    case "name": case "title": if(info.title == null) info.title = scalar(8192); else skipValue(); break;
                    case "created_at": info.createdAt = scalar(256); break;
                    case "updated_at": info.updatedAt = scalar(256); break;
                    case "chat_messages": case "messages":
                        if (peek() == '[') array(messageIndex -> {
                            if (peek() == '{') message(info.messageCount++);
                            else skipValue();
                        });
                        else skipValue();
                        break;
                    default: skipValue();
                }
            });
            if (info.id == null || info.id.isEmpty()) info.id = "missing-id-" + info.ordinal;
            if (info.title == null || info.title.isEmpty()) info.title = "Untitled conversation " + (info.ordinal + 1);
            sink.endConversation(info);
        });
    }

    private void message(int ordinal) throws IOException {
        MessageInfo info = new MessageInfo(); info.ordinal = ordinal;
        sink.beginMessage(ordinal);
        object(key -> {
            switch (key) {
                case "uuid": case "id": if(info.id == null) info.id = scalar(512); else skipValue(); break;
                case "sender": case "role": info.sender = scalar(256); break;
                case "created_at": info.createdAt = scalar(256); break;
                case "updated_at": info.updatedAt = scalar(256); break;
                case "content": content(info, 0); break;
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
                else if (key.equals("text") || key.equals("content")) {
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