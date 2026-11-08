package com.example.markdowndesk;

import com.example.markdowndesk.workspace.search.UnifiedSearchJump;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Bounded read-only verification of raw JSON search hits and optional strict node identity. */
final class JsonRawSearchHit {
    static final int MAX_BYTES=8*1024*1024;
    static final class Hit {
        final String quote,snippet;final int line,begin,end;
        final JsonNodeLocator.Target node;
        final byte[] snapshot; // only retained by an explicit on-demand jump
        Hit(String quote,String snippet,int line,int begin,int end,JsonNodeLocator.Target node,byte[] snapshot) {
            this.quote=quote;this.snippet=snippet;this.line=line;this.begin=begin;this.end=end;
            this.node=node;this.snapshot=snapshot;
        }
    }
    static final int MAX_HITS=500;
    static final class Occurrence {
        final int offset,line;
        final String snippet;
        Occurrence(int offset,int line,String snippet){
            this.offset=offset;this.line=line;this.snippet=snippet;
        }
    }
    static final class SearchResult {
        final String sha256;
        final List<Occurrence> hits;
        final boolean limited;
        SearchResult(String sha256,List<Occurrence> hits,boolean limited){
            this.sha256=sha256;
            this.hits=Collections.unmodifiableList(new ArrayList<>(hits));
            this.limited=limited;
        }
    }
    private static final class Snapshot {
        final byte[] bytes;final String sha256,text;
        Snapshot(byte[] bytes,String sha256,String text){
            this.bytes=bytes;this.sha256=sha256;this.text=text;
        }
    }
    /** Exact UTF-16 literal scan of one read-only, bounded, strictly decoded
     * source. Large files and excess result sets return an explicit limitation;
     * never pretend an 8MiB scan searched a 700MB archive.
     */
    static SearchResult search(InputStream stream,String literal)throws IOException {
        if(literal==null||literal.isEmpty()||literal.length()>128)
            throw new IOException("Enter a search phrase between 1 and 128 UTF-16 units");
        Snapshot source=read(stream);
        String text=source.text;
        ArrayList<Occurrence> matches=new ArrayList<>();
        int position=text.indexOf(literal),cursor=0,line=1;
        while(position>=0&&matches.size()<MAX_HITS){
            for(int i=cursor;i<position;i++){
                char ch=text.charAt(i);
                if(ch=='\r'){
                    line++;if(i+1<position&&text.charAt(i+1)=='\n')i++;
                }else if(ch=='\n')line++;
            }
            cursor=position;
            int start=Math.max(0,position-110);
            int end=Math.min(text.length(),position+literal.length()+110);
            if(start>0&&Character.isLowSurrogate(text.charAt(start)))start--;
            if(end<text.length()&&end>0&&Character.isHighSurrogate(text.charAt(end-1)))end++;
            matches.add(new Occurrence(position,line,text.substring(start,end)));
            position=text.indexOf(literal,position+1);
        }
        return new SearchResult(source.sha256,matches,position>=0);
    }
    private JsonRawSearchHit(){}

    static Hit verify(InputStream stream,String requestedUri,String actualUri,
            String sourceSha,String literal,int offset)throws IOException {
        return verify(stream,requestedUri,actualUri,sourceSha,literal,offset,false);
    }
    /** An explicit node jump needs one immutable source snapshot for verification and reading. */
    static Hit verifyWithSnapshot(InputStream stream,String requestedUri,String actualUri,
            String sourceSha,String literal,int offset)throws IOException {
        return verify(stream,requestedUri,actualUri,sourceSha,literal,offset,true);
    }
    private static Snapshot read(InputStream stream)throws IOException {
        if(stream==null)throw new IOException("JSON source permission unavailable");
        ByteArrayOutputStream received=new ByteArrayOutputStream(16384);
        byte[] buffer=new byte[8192];int count;
        while((count=stream.read(buffer))!=-1){
            if(Thread.currentThread().isInterrupted())throw new IOException("JSON search cancelled");
            if(count==0)throw new IOException("JSON source reader stalled");
            if(count>MAX_BYTES-received.size())throw new IOException("JSON source exceeds verified search limit");
            received.write(buffer,0,count);
        }
        byte[] source=received.toByteArray();
        String actualSha;
        try {
            byte[] digest=MessageDigest.getInstance("SHA-256").digest(source);
            StringBuilder hex=new StringBuilder(64);
            for(byte b:digest){hex.append(Character.forDigit((b>>4)&15,16));hex.append(Character.forDigit(b&15,16));}
            actualSha=hex.toString();
        }catch(NoSuchAlgorithmException impossible){throw new IOException("SHA-256 unavailable",impossible);}
        int bom=source.length>=3&&(source[0]&255)==0xef&&(source[1]&255)==0xbb&&
                (source[2]&255)==0xbf?3:0;
        String text;
        try {
            text=StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(source,bom,source.length-bom)).toString();
        }catch(java.nio.charset.CharacterCodingException damaged){
            throw new IOException("JSON source contains malformed UTF-8",damaged);
        }

        return new Snapshot(source,actualSha,text);
    }
    private static Hit verify(InputStream stream,String requestedUri,String actualUri,
            String sourceSha,String literal,int offset,boolean keepSnapshot)throws IOException {
        Snapshot parsed=read(stream);
        String text=parsed.text;
        if(!UnifiedSearchJump.matches(requestedUri,actualUri,sourceSha,parsed.sha256,
                text,literal,offset,false))
            throw new IOException("JSON passage moved or file changed; search again");
        int line=1;
        for(int i=0;i<offset;i++){
            char c=text.charAt(i);
            if(c=='\r'){line++;if(i+1<offset&&text.charAt(i+1)=='\n')i++;}
            else if(c=='\n')line++;
        }
        int start=Math.max(0,offset-160),end=Math.min(text.length(),offset+literal.length()+160);
        if(start>0&&Character.isLowSurrogate(text.charAt(start)))start--;
        if(end<text.length()&&end>0&&Character.isHighSurrogate(text.charAt(end-1)))end++;
        // A verified raw hit is always useful; a structural jump is offered only
        // when a strict bounded lexical pass establishes an unambiguous path.
        JsonNodeLocator.Target node=null;
        try{node=JsonNodeLocator.locate(text,offset,literal.length());}
        catch(IOException malformedOrAmbiguous){/* Keep the verified raw passage only. */}
        return new Hit(literal,text.substring(start,end),line,offset-start,
                offset-start+literal.length(),node,keepSnapshot?parsed.bytes:null);
    }
}
