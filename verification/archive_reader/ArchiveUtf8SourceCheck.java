package com.example.markdowndesk.archive;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/** Validate original byte fidelity, not only syntactic JSON shape. */
public final class ArchiveUtf8SourceCheck {
    private static final class Collector implements ArchiveStreamParser.Sink {
        final StringBuilder content=new StringBuilder();int chats,messages;
        final java.util.List<String> titles=new java.util.ArrayList<>();
        final java.util.List<String> senders=new java.util.ArrayList<>();
        final java.util.List<String> dates=new java.util.ArrayList<>();
        public void beginConversation(int ordinal){}
        public void beginMessage(int ordinal){}
        public void textSegment(String text){content.append(text);}
        public void endMessage(ArchiveStreamParser.MessageInfo m){messages++;senders.add(m.sender);dates.add(m.createdAt);}
        public void endConversation(ArchiveStreamParser.ConversationInfo c){chats++;titles.add(c.title);}
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
    private static String node(String parent,String text) {
        return "{\"parent\":"+(parent==null?"null":"\""+parent+"\"")+",\"message\":{"+
            "\"author\":{\"role\":\"assistant\"},\"create_time\":1.25,"+
            "\"content\":{\"content_type\":\"text\",\"parts\":[\""+text+"\"]}}}";
    }
    private static void formats()throws Exception {
        Collector linear=parse("{\"title\":\"Linear\",\"messages\":[{\"role\":\"user\",\"content\":\"hello\"}]}".getBytes(StandardCharsets.UTF_8));
        eq(linear.content.toString(),"hello","root linear conversation");
        eq(linear.senders.get(0),"user","linear role");
        String graph="{\"title\":\"Branches\",\"id\":\"chat\",\"mapping\":{"+
            "\"b\":"+node("a","B😈")+",\"r\":{\"parent\":null,\"message\":null},"+
            "\"c\":"+node("a","C")+",\"a\":"+node("r","A")+"},\"current_node\":\"c\"}";
        Collector branched=parse(graph.getBytes(StandardCharsets.UTF_8));
        eq(branched.chats,2,"all branches retained");
        eq(branched.messages,4,"common ancestry replayed");
        eq(branched.content.toString(),"ACAB😈","selected branch first, parent order independent of object order");
        eq(branched.titles.get(0),"Branches [selected branch]","selected branch labeled");
        eq(branched.senders.get(0),"assistant","ChatGPT author role");
        eq(branched.dates.get(0),"1970-01-01T00:00:01.250Z","epoch timestamp normalized");
        reject("[{\"arbitrary\":true}]".getBytes(StandardCharsets.UTF_8),"arbitrary object must not import as empty chat");
        reject(graph.replace("\"parent\":\"a\"","\"parent\":\"missing\"").getBytes(StandardCharsets.UTF_8),"missing graph ancestry");
        reject(graph.replace("\"parent\":\"r\"","\"parent\":\"a\"").getBytes(StandardCharsets.UTF_8),"graph cycle");
        reject(graph.replace("\"current_node\":\"c\"","\"current_node\":\"missing\"").getBytes(StandardCharsets.UTF_8),"missing current node");
        reject("[{\"messages\":[7]}]".getBytes(StandardCharsets.UTF_8),"unsupported message not silently skipped");
        Collector claude=parse("[{\"chat_messages\":[{\"text\":\"same\",\"content\":[{\"text\":\"same\"}]}]}]".getBytes(StandardCharsets.UTF_8));
        eq(claude.content.toString(),"same","Claude redundant text does not duplicate content");
        String large="ab😈".repeat(100000);
        Collector fallback=parse(("{\"messages\":[{\"text\":\""+large+"\",\"role\":\"user\"}]}").getBytes(StandardCharsets.UTF_8));
        eq(fallback.content.toString(),large,"large linear text fallback streams without truncation");
        Collector contentFirst=parse("{\"messages\":[{\"content\":\"primary\",\"text\":\"duplicate\"}]}".getBytes(StandardCharsets.UTF_8));
        eq(contentFirst.content.toString(),"primary","content takes precedence regardless field order");
        Collector nontext=parse("{\"messages\":[{\"content\":[\"before\",{\"content_type\":\"image_asset_pointer\",\"asset_pointer\":\"private\"},{\"unknown\":true},17]}]}".getBytes(StandardCharsets.UTF_8));
        eq(nontext.content.toString(),"before\n\n[Non-text block: image_asset_pointer]\n\n[Non-text block: unknown]\n\n[Non-text block: unknown]","unsupported blocks explicitly retained in order");
        reject("{\"unrelated\":١,\"messages\":[]}".getBytes(StandardCharsets.UTF_8),"non-ASCII digit is not JSON number");
        reject(("{\"unrelated\":"+"7".repeat(1025)+",\"messages\":[]}").getBytes(StandardCharsets.UTF_8),"bounded skipped numeric token");
        reject("{\"messages\":[{\"text\":\"temporary\",BROKEN}]}".getBytes(StandardCharsets.UTF_8),"failed fallback cleanup");
        java.io.File folder=new java.io.File(System.getProperty("java.io.tmpdir"));
        String[] leftovers=folder.list((dir,name)->name.startsWith("markdown-desk-graph-")&&name.endsWith(".tmp"));
        eq(leftovers.length,0,"success and failed graph import clean private spools");
        System.out.println("PASS: linear LLM exports, ChatGPT branches/selection/Unicode/roles/dates, malformed graphs and spool cleanup");
    }
    public static void main(String[] ignored)throws Exception {
        formats();
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
