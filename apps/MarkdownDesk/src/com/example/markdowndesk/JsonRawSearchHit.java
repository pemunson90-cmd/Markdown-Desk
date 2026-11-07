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

/** Bounded read-only verification of raw JSON search hits and optional strict node identity. */
final class JsonRawSearchHit {
    static final int MAX_BYTES=8*1024*1024;
    static final class Hit {
        final String quote,snippet;final int line,begin,end;
        final JsonNodeLocator.Target node;
        Hit(String quote,String snippet,int line,int begin,int end,JsonNodeLocator.Target node) {
            this.quote=quote;this.snippet=snippet;this.line=line;this.begin=begin;this.end=end;
            this.node=node;
        }
    }
    private JsonRawSearchHit(){}

    static Hit verify(InputStream stream,String requestedUri,String actualUri,
            String sourceSha,String literal,int offset)throws IOException {
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
        if(!UnifiedSearchJump.matches(requestedUri,actualUri,sourceSha,actualSha,
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
                offset-start+literal.length(),node);
    }
}
