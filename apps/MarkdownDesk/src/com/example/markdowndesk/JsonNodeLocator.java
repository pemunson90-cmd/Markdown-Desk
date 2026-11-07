package com.example.markdowndesk;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

/** Resolves a verified raw UTF-16 match to a JSON value's structural path.
 * This bounded, read-only lexical pass never claims an exact node for punctuation,
 * cross-token matches or malformed/overdeep content. No source bytes are changed.
 */
final class JsonNodeLocator {
    static final class Segment {
        final String key;
        final int index;
        private Segment(String key,int index){this.key=key;this.index=index;}
        static Segment field(String name){return new Segment(name,-1);}
        static Segment element(int index){return new Segment(null,index);}
    }
    static final class Target {
        final List<Segment> path;
        final boolean keyMatch;
        private Target(List<Segment> path,boolean keyMatch){
            this.path=Collections.unmodifiableList(new ArrayList<>(path));
            this.keyMatch=keyMatch;
        }
    }
    private static final int MAX_DEPTH=64,MAX_KEY=512,MAX_OBJECT_KEYS=4096;
    private final String src;
    private final int from,until;
    private int at;
    private Target target;
    private JsonNodeLocator(String src,int from,int until){this.src=src;this.from=from;this.until=until;}

    static Target locate(String source,int start,int length)throws IOException {
        if(source==null||start<0||length<=0||start>source.length()
                ||length>source.length()-start)return null;
        JsonNodeLocator parser=new JsonNodeLocator(source,start,start+length);
        parser.space();
        parser.value(new ArrayList<>(),0);
        parser.space();
        if(parser.at!=source.length())throw new IOException("Trailing data outside JSON value");
        return parser.target;
    }
    private void space(){
        while(at<src.length()){
            char c=src.charAt(at);
            if(c==' '||c=='\t'||c=='\r'||c=='\n')at++;
            else break;
        }
    }
    private boolean take(char wanted){
        if(at<src.length()&&src.charAt(at)==wanted){at++;return true;}
        return false;
    }
    private void require(char wanted)throws IOException {
        if(!take(wanted))throw new IOException("Malformed JSON token at UTF-16 offset "+at);
    }
    private void mark(int first,int end,List<Segment> path,boolean isKey){
        if(from>=first&&until<=end)target=new Target(path,isKey);
    }
    private void value(ArrayList<Segment> path,int depth)throws IOException {
        if(depth>MAX_DEPTH)throw new IOException("JSON nesting exceeds reader limit");
        space();
        if(at>=src.length())throw new IOException("Missing JSON value");
        char token=src.charAt(at);
        if(token=='{'){object(path,depth);return;}
        if(token=='['){array(path,depth);return;}
        int begin=at;
        if(token=='"')string(false);
        else if(token=='t')keyword("true");
        else if(token=='f')keyword("false");
        else if(token=='n')keyword("null");
        else if(token=='-'||(token>='0'&&token<='9'))number();
        else throw new IOException("Unexpected JSON value at UTF-16 offset "+at);
        mark(begin,at,path,false);
    }
    private void object(ArrayList<Segment> path,int depth)throws IOException {
        require('{');space();
        if(take('}'))return;
        HashSet<String> keys=new HashSet<>();
        do{
            space();
            int begin=at;
            String key=string(true);
            if(keys.size()>=MAX_OBJECT_KEYS||!keys.add(key))
                throw new IOException("Ambiguous or oversized JSON object path");
            int end=at;
            space();require(':');
            path.add(Segment.field(key));
            mark(begin,end,path,true); // A matching property name leads to its value.
            value(path,depth+1);
            path.remove(path.size()-1);
            space();
            if(take('}'))return;
            require(',');space();
            if(at>=src.length()||src.charAt(at)!='"')throw new IOException("Expected object key");
        }while(true);
    }
    private void array(ArrayList<Segment> path,int depth)throws IOException {
        require('[');space();
        if(take(']'))return;
        int index=0;
        do{
            if(index==Integer.MAX_VALUE)throw new IOException("Array index overflow");
            path.add(Segment.element(index++));
            value(path,depth+1);
            path.remove(path.size()-1);
            space();
            if(take(']'))return;
            require(',');
        }while(true);
    }
    private String string(boolean decode)throws IOException {
        require('"');
        StringBuilder out=decode?new StringBuilder():null;
        while(at<src.length()){
            char c=src.charAt(at++);
            if(c=='"')return decode?out.toString():null;
            if(c<0x20)throw new IOException("Unescaped JSON control character");
            if(c=='\\'){
                if(at>=src.length())throw new IOException("Incomplete JSON escape");
                char e=src.charAt(at++);
                switch(e){
                    case '"':case '\\':case '/':c=e;break;
                    case 'b':c='\b';break;
                    case 'f':c='\f';break;
                    case 'n':c='\n';break;
                    case 'r':c='\r';break;
                    case 't':c='\t';break;
                    case 'u':
                        if(at+4>src.length())throw new IOException("Incomplete Unicode escape");
                        int hex=0;
                        for(int i=0;i<4;i++){
                            char h=src.charAt(at++);
                            int v=h>='0'&&h<='9'?h-'0':h>='a'&&h<='f'?h-'a'+10:
                                    h>='A'&&h<='F'?h-'A'+10:-1;
                            if(v<0)throw new IOException("Malformed Unicode escape");
                            hex=(hex<<4)|v;
                        }
                        c=(char)hex;break;
                    default:throw new IOException("Invalid JSON escape");
                }
            }
            if(decode){
                if(out.length()>=MAX_KEY)throw new IOException("JSON property name too long for safe navigation");
                out.append(c);
            }
        }
        throw new IOException("Unterminated JSON string");
    }
    private void keyword(String word)throws IOException {
        if(!src.startsWith(word,at))throw new IOException("Invalid JSON literal");
        at+=word.length();
    }
    private void number()throws IOException {
        take('-');
        if(take('0')){
            if(at<src.length()&&Character.isDigit(src.charAt(at)))throw new IOException("Leading zero");
        }else{
            if(at>=src.length()||src.charAt(at)<'1'||src.charAt(at)>'9')throw new IOException("Bad JSON number");
            do{at++;}while(at<src.length()&&src.charAt(at)>='0'&&src.charAt(at)<='9');
        }
        if(take('.')){
            int start=at;
            while(at<src.length()&&src.charAt(at)>='0'&&src.charAt(at)<='9')at++;
            if(at==start)throw new IOException("Bad JSON fraction");
        }
        if(take('e')||take('E')){
            take('+'); // '-' also accepted, never both.
            if(at>0&&src.charAt(at-1)!='+')take('-');
            int start=at;
            while(at<src.length()&&src.charAt(at)>='0'&&src.charAt(at)<='9')at++;
            if(at==start)throw new IOException("Bad JSON exponent");
        }
    }
}
