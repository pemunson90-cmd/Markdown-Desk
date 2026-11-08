package com.example.markdowndesk;

import com.example.markdowndesk.archive.ArchiveStreamParser;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** Explicit generic JSON mode. Every node is retained as a path/type record in
 * the existing generation-safe index; scalar text is never buffered in full.
 * Positional identities distinguish duplicate object names without guessing.
 */
final class JsonDocumentIndexer {
    private final ArchiveStreamParser.Sink sink;
    private int ordinal;
    private long messageChars;
    private JsonDocumentIndexer(ArchiveStreamParser.Sink sink){this.sink=sink;}

    static String sourceIdentity(String uri,boolean generic){
        // Preserve the existing conversation UUID byte-for-byte. A generic
        // representation must never replace its index or invalidate its notes.
        String identity=generic?"json-path-index-v1\u0000"+uri:uri;
        return java.util.UUID.nameUUIDFromBytes(identity.getBytes(StandardCharsets.UTF_8)).toString();
    }
    static void index(InputStream input,String title,ArchiveStreamParser.Sink sink)throws IOException{
        JsonDocumentIndexer indexer=new JsonDocumentIndexer(sink);
        try(JsonTokenReader reader=new JsonTokenReader(ArchiveStreamParser.strictUtf8Reader(JsonUtf8Bom.strip(input)))){
            sink.beginConversation(0);
            indexer.node(reader,"$","$",0);
            if(reader.peek()!=JsonTokenReader.Token.END_DOCUMENT)throw new IOException("Unexpected data after JSON root");
            ArchiveStreamParser.ConversationInfo info=new ArchiveStreamParser.ConversationInfo();
            info.id="json-path-index-v1";info.ordinal=0;info.messageCount=indexer.ordinal;
            info.title="JSON · "+(title==null||title.isEmpty()?"Document":title);
            sink.endConversation(info);
        }
    }
    private void node(JsonTokenReader json,String path,String identity,int depth)throws IOException{
        if(Thread.currentThread().isInterrupted())throw new IOException("JSON indexing cancelled");
        if(depth>160||path.length()>32768)throw new IOException("JSON path exceeds supported identity size");
        JsonTokenReader.Token type=json.peek();
        if(type==JsonTokenReader.Token.END_DOCUMENT||type==JsonTokenReader.Token.END_OBJECT||type==JsonTokenReader.Token.END_ARRAY)
            throw new IOException("Expected a JSON value");
        if(ordinal==Integer.MAX_VALUE)throw new IOException("JSON document exceeds supported node count");
        ArchiveStreamParser.MessageInfo info=new ArchiveStreamParser.MessageInfo();
        info.ordinal=ordinal++;info.id=digest(identity+"\u0000"+path);info.sender=type.name().replace("BEGIN_","").toLowerCase(java.util.Locale.ROOT);
        sink.beginMessage(info.ordinal);messageChars=0;
        emit("JSON path: "+path+"\nType: "+info.sender+"\n\n");
        if(type==JsonTokenReader.Token.BEGIN_OBJECT||type==JsonTokenReader.Token.BEGIN_ARRAY){
            emit(type==JsonTokenReader.Token.BEGIN_OBJECT?"{object}":"[array]");
            info.textPresent=true;sink.endMessage(info);
            int child=0;
            if(type==JsonTokenReader.Token.BEGIN_OBJECT){
                json.beginObject();
                while(json.hasNext()){
                    String name=json.nextName();
                    node(json,path+"[\""+escape(name)+"\"]",identity+"/o"+child,depth+1);
                    if(child==Integer.MAX_VALUE)throw new IOException("JSON object exceeds supported node count");child++;
                }
                json.endObject();
            }else{
                json.beginArray();
                while(json.hasNext()){
                    node(json,path+"["+child+"]",identity+"/a"+child,depth+1);
                    if(child==Integer.MAX_VALUE)throw new IOException("JSON array exceeds supported node count");child++;
                }
                json.endArray();
            }
        }else{
            json.nextScalarChunks(this::emit);
            info.textPresent=true;sink.endMessage(info);
        }
    }
    private void emit(String text)throws IOException{
        if(Thread.currentThread().isInterrupted())throw new IOException("JSON indexing cancelled");
        if(text.length()>Integer.MAX_VALUE-messageChars)throw new IOException("JSON node exceeds supported text offsets");
        messageChars+=text.length();
        // Path headers can exceed one index segment; retain all header text.
        for(int start=0;start<text.length();){
            int end=Math.min(text.length(),start+ArchiveStreamParser.SEGMENT_CHARS);
            if(end<text.length()&&Character.isHighSurrogate(text.charAt(end-1)))end--;
            sink.textSegment(text.substring(start,end));start=end;
        }
    }
    private static String escape(String value){
        StringBuilder result=new StringBuilder();
        for(int i=0;i<value.length();i++){
            char c=value.charAt(i);
            if(c=='"'||c=='\\')result.append('\\').append(c);
            else if(c<32)result.append(String.format(java.util.Locale.ROOT,"\\u%04x",(int)c));
            else result.append(c);
        }
        return result.toString();
    }
    private static String digest(String value){
        try{
            byte[] digest=MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder out=new StringBuilder("json-node-");
            for(byte b:digest)out.append(Character.forDigit((b>>>4)&15,16)).append(Character.forDigit(b&15,16));
            return out.toString();
        }catch(NoSuchAlgorithmException impossible){throw new AssertionError(impossible);}
    }
}
