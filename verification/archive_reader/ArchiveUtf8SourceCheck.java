package com.example.markdowndesk.archive;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/** Validate original byte fidelity, not only syntactic JSON shape. */
public final class ArchiveUtf8SourceCheck {
    private static final class Collector implements ArchiveStreamParser.Sink {
        final StringBuilder content=new StringBuilder();int chats,messages;
        public void beginConversation(int ordinal){}
        public void beginMessage(int ordinal){}
        public void textSegment(String text){content.append(text);}
        public void endMessage(ArchiveStreamParser.MessageInfo m){messages++;}
        public void endConversation(ArchiveStreamParser.ConversationInfo c){chats++;}
    }
    private static Collector parse(byte[] bytes)throws Exception {
        Collector sink=new Collector();
        new ArchiveStreamParser(ArchiveStreamParser.strictUtf8Reader(
            new ByteArrayInputStream(bytes)),sink).parse();
        return sink;
    }
    private static byte[] json(String value){return ("[{\"uuid\":\"chat\",\"chat_messages\":[{\"uuid\":\"m\",\"content\":\""+value+"\"}]}]")
            .getBytes(StandardCharsets.UTF_8);}
    private static void reject(byte[] bytes,String why)throws Exception {
        try{parse(bytes);throw new AssertionError("Accepted invalid imported source: "+why);}
        catch(IOException expected){}
    }
    private static void eq(Object value,Object expected,String why){
        if(!expected.equals(value))throw new AssertionError(why+": "+value);
    }
    public static void main(String[] ignored)throws Exception {
        Collector ascii=parse(json("ascii"));eq(ascii.content.toString(),"ascii","ASCII content");
        byte[] emoji=json("a😀z");byte[] bom=new byte[emoji.length+3];
        bom[0]=(byte)0xef;bom[1]=(byte)0xbb;bom[2]=(byte)0xbf;
        System.arraycopy(emoji,0,bom,3,emoji.length);
        Collector valid=parse(bom);eq(valid.content.toString(),"a😀z","BOM and surrogate pair");
        eq(valid.chats,1,"one conversation");eq(valid.messages,1,"one message");
        eq(parse(json("\\uD83D\\uDE00")).content.toString(),"😀","escaped supplementary codepoint");
        reject(json("\\uD800"),"isolated high surrogate JSON escape");
        reject(json("\\uDC00"),"isolated low surrogate JSON escape");
        reject(json("\\uD800x"),"high surrogate followed by normal text");
        byte[] corrupt=json("before after");
        int offset=new String(corrupt,StandardCharsets.ISO_8859_1).indexOf("before");
        corrupt[offset]=(byte)0xc0;corrupt[offset+1]=(byte)0xaf;
        reject(corrupt,"overlong or malformed UTF-8");
        byte[] trailing=json("complete");trailing=Arrays.copyOf(trailing,trailing.length+1);
        trailing[trailing.length-1]=(byte)0xe2;
        reject(trailing,"incomplete UTF-8 sequence after parsed root");
        System.out.println("PASS: strict archive UTF-8 decoder, BOM, supplementary escapes and fail-closed corruption");
    }
}
