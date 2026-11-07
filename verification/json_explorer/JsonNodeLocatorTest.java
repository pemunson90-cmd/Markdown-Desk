package com.example.markdowndesk;
import java.io.IOException;
import java.util.List;
public final class JsonNodeLocatorTest {
    static void check(boolean yes,String why){if(!yes)throw new AssertionError(why);}
    static JsonNodeLocator.Target hit(String json,String needle)throws Exception {
        int at=json.indexOf(needle);check(at>=0,"fixture missing "+needle);
        return JsonNodeLocator.locate(json,at,needle.length());
    }
    static void path(JsonNodeLocator.Target t,String...components){
        check(t!=null&&t.path.size()==components.length,"target path size");
        for(int i=0;i<components.length;i++){
            JsonNodeLocator.Segment s=t.path.get(i);
            String actual=s.key!=null?s.key:"#"+s.index;
            check(components[i].equals(actual),"path "+i+" expected "+components[i]+" actual "+actual);
        }
    }
    public static void main(String[] args)throws Exception {
        String doc="{\"one\":[{\"title\":\"hello\"},{\"title\":\"hello\"}],\"a\\u002Fb\":-1.23e+4,\"flag\":true}";
        path(hit(doc,"hello"),"one","#0","title");
        path(JsonNodeLocator.locate(doc,doc.lastIndexOf("hello"),5),"one","#1","title");
        path(hit(doc,"a\\u002Fb"),"a/b");
        check(hit(doc,"a\\u002Fb").keyMatch,"property name must map to value");
        path(hit(doc,"1.23e+4"),"a/b");
        path(hit(doc,"true"),"flag");
        check(hit(doc,":")==null,"punctuation is not a node");
        check(hit(doc,"},{")==null,"cross token is not a node");
        check(JsonNodeLocator.locate(doc,0,0)==null,"empty match not a path");
        try{hit("{\"a\":1,}","1");throw new AssertionError("invalid comma accepted");}
        catch(IOException expected){}
        try{hit("{\"dup\":1,\"dup\":2}","2");throw new AssertionError("duplicate JSON property accepted");}
        catch(IOException expected){}
        try{hit("[01]","01");throw new AssertionError("leading zero accepted");}
        catch(IOException expected){}
        StringBuilder depth=new StringBuilder();for(int i=0;i<70;i++)depth.append('[');
        depth.append("1");for(int i=0;i<70;i++)depth.append(']');
        try{hit(depth.toString(),"1");throw new AssertionError("unbounded recursion");}
        catch(IOException expected){}
        path(hit("{\"emoji\":\"😀 love\"}","😀"),"emoji");
        check(hit("{\"x\":\"space here\"}","space").path.size()==1,"string value path");
        System.out.println("PASS JsonNodeLocator: verified UTF16 raw hit maps to bounded structural node");
    }
}
