package com.example.markdowndesk;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
/** Real pure streaming Inspector engine tests under a small heap. */
public final class JsonStreamingEngineCheck {
    static void eq(Object a,Object b){if(!a.equals(b))throw new AssertionError(a+" != "+b);}
    static JsonExplorerEngine.Page scan(String s,List<JsonExplorerEngine.Step> p,int offset)throws Exception{
        return JsonExplorerEngine.scan(()->new ByteArrayInputStream(s.getBytes(StandardCharsets.UTF_8)),p,offset);
    }
    static void bad(String json)throws Exception{
        try(JsonTokenReader r=new JsonTokenReader(new StringReader(json))){r.skipValue();if(r.peek()!=JsonTokenReader.Token.END_DOCUMENT)throw new IOException();throw new AssertionError("Accepted malformed JSON: "+json);}
        catch(IOException expected){}
    }
    public static void main(String[] args)throws Exception{
        eq(scan("{\"a\":[null,true,3,\"x\"],\"b\":{\"z\":2}}",Collections.emptyList(),0).entries.size(),2);
        List<JsonExplorerEngine.Step> path=Arrays.asList(JsonExplorerEngine.Step.field("a"),JsonExplorerEngine.Step.element(3));
        eq(scan("{\"a\":[null,true,3,\"x\"]}",path,0).value,"x");
        String value="x".repeat(65535)+"😈"+"z".repeat(80000);
        String json="{\"a\":\""+value+"\"}";path=Arrays.asList(JsonExplorerEngine.Step.field("a"));
        StringBuilder joined=new StringBuilder();
        for(int page=0;;page+=100){JsonExplorerEngine.Page p=scan(json,path,page);joined.append(p.value);if(!p.hasMore)break;}
        eq(joined.toString(),value);
        eq(scan(json,path,100).valueStart,65537);
        JsonExplorerEngine.Page legacy=JsonExplorerEngine.scanWindow(()->new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)),path,0,0,66064);
        eq(legacy.value.substring(65000,66000),value.substring(65000,66000));
        eq(scan("{\"n\":-1.2e+3}",Arrays.asList(JsonExplorerEngine.Step.field("n")),0).value,"-1.2e+3");
        for(String s:new String[]{"[1,]","{\"a\":1,}","[01]","[.1]","[1e]","[1.]","[truex]","[\"\\uD800\"]","[\"\\uDC00\"]"})bad(s);
        // Generate a 70MB scalar without allocating it, then read a late window
        // under -Xmx32m. The previous Android nextString path cannot pass this.
        final int total=Integer.getInteger("json.scalar.stress",70_000_000);
        JsonExplorerEngine.StreamSource large=()->new InputStream(){int pos;public int read(){if(pos++==0)return '"';if(pos<=total+1)return 'x';if(pos==total+2)return '"';return -1;}public int read(byte[] b,int o,int n){int i=0;for(;i<n;i++){int c=read();if(c<0)return i==0?-1:i;b[o+i]=(byte)c;}return i;}};
        JsonExplorerEngine.Page late=JsonExplorerEngine.scan(large,Collections.emptyList(),100000);
        eq(late.value.length(),65536);eq(late.valueStart,65536000);eq(late.valueTotal,total);
        // A giant scalar preceding the selected field is skipped at bounded memory.
        try(JsonTokenReader r=new JsonTokenReader(new StringReader("{\"long\":\""+"q".repeat(200000)+"\",\"last\":1}"))){r.beginObject();eq(r.nextName(),"long");r.skipValue();eq(r.nextName(),"last");eq(r.nextString(),"1");r.endObject();}
        try(JsonTokenReader r=new JsonTokenReader(new StringReader("\""+value+"\""))){
            StringBuilder all=new StringBuilder();r.nextScalarChunks(part->{
                if(Character.isLowSurrogate(part.charAt(0))||Character.isHighSurrogate(part.charAt(part.length()-1)))throw new AssertionError("Split Unicode stream");
                all.append(part);
            });eq(all.toString(),value);
        }
        System.out.println("PASS streaming JSON Inspector: paths, strict grammar, 70MB scalar window under32MB, Unicode page boundaries, bounded skips");
    }
}
