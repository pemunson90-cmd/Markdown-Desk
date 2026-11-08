package com.example.markdowndesk;

import com.example.markdowndesk.archive.ArchiveStreamParser;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public final class JsonDocumentIndexCheck {
    private static final class Collect implements ArchiveStreamParser.Sink {
        final List<String> ids=new ArrayList<>(),texts=new ArrayList<>(),types=new ArrayList<>();
        final StringBuilder current=new StringBuilder();int nodes;
        public void beginConversation(int n){}
        public void beginMessage(int n){eq(n,ids.size(),"ordinal");current.setLength(0);}
        public void textSegment(String value){
            if(value.length()>4096)throw new AssertionError("unbounded segment");current.append(value);
        }
        public void endMessage(ArchiveStreamParser.MessageInfo info){ids.add(info.id);texts.add(current.toString());types.add(info.sender);}
        public void endConversation(ArchiveStreamParser.ConversationInfo info){nodes=info.messageCount;}
    }
    private static void eq(Object actual,Object expected,String why){if(!expected.equals(actual))throw new AssertionError(why+": "+actual);}
    private static Collect parse(String source)throws Exception{
        Collect result=new Collect();JsonDocumentIndexer.index(new ByteArrayInputStream(source.getBytes(StandardCharsets.UTF_8)),"fixture",result);return result;
    }
    private static void reject(String source)throws Exception{
        try{parse(source);throw new AssertionError("accepted malformed JSON");}catch(IOException expected){}
    }
    public static void main(String[] args)throws Exception{
        Collect all=parse("{\"a\":[\"hello😈\",true,null,1.25],\"a\":{},\"quote\\\"\\n\":[]}");
        eq(all.nodes,8,"all containers and scalars retained");
        eq(new HashSet<>(all.ids).size(),8,"duplicate field names have distinct source positions");
        eq(all.types,Arrays.asList("object","array","string","boolean","null","number","object","array"),"node types");
        if(!all.texts.get(2).endsWith("hello😈")||!all.texts.get(2).contains("$[\"a\"][0]"))throw new AssertionError("value/path lost");
        if(!all.texts.get(7).contains("quote\\\"\\u000a"))throw new AssertionError("path escaping");
        eq(parse("\"root\"").texts.get(0),"JSON path: $\nType: string\n\nroot","scalar root");
        if(parse("{\"old\":\"same\"}").ids.get(1).equals(parse("{\"new\":\"same\"}").ids.get(1)))throw new AssertionError("renamed field must not inherit notes by position");
        String large="abc😈".repeat(100000);
        if(!parse("[\""+large+"\"]").texts.get(1).endsWith(large))throw new AssertionError("large value truncated");
        eq(JsonDocumentIndexer.sourceIdentity("content://one",false),UUID.nameUUIDFromBytes("content://one".getBytes(StandardCharsets.UTF_8)).toString(),"legacy identity preserved");
        if(JsonDocumentIndexer.sourceIdentity("content://one",true).equals(JsonDocumentIndexer.sourceIdentity("content://one",false)))throw new AssertionError("index modes collide");
        reject("[1,]");reject("{\"a\":1} trailing");reject("[\"\\ud800\"]");
        Thread.currentThread().interrupt();try{reject("[1]");}finally{Thread.interrupted();}
        // Generated 700MB scalar; a counting sink verifies bounded memory and full traversal.
        final long length=700_000_000L;final long[] chars={0};
        InputStream huge=new InputStream(){long at;
            public int read(){if(at==0){at++;return '"';}if(at<=length){at++;return 'x';}if(at==length+1){at++;return '"';}return -1;}
            public int read(byte[] b,int off,int len){if(len==0)return 0;int count=0;while(count<len){int c=read();if(c<0)break;b[off+count++]=(byte)c;}return count==0?-1:count;}
        };
        JsonDocumentIndexer.index(huge,"huge",new ArchiveStreamParser.Sink(){
            public void beginConversation(int n){} public void beginMessage(int n){}
            public void textSegment(String text){if(text.length()>4096)throw new AssertionError("unbounded chunk");chars[0]+=text.length();}
            public void endMessage(ArchiveStreamParser.MessageInfo m){}public void endConversation(ArchiveStreamParser.ConversationInfo c){eq(c.messageCount,1,"huge scalar count");}
        });
        eq(chars[0],length+"JSON path: $\nType: string\n\n".length(),"complete 700MB scalar indexed");
        System.out.println("PASS generic JSON indexing: paths/types/all values, duplicate-key identity, Unicode, mode isolation, malformed/cancel, 700MB scalar");
    }
}
