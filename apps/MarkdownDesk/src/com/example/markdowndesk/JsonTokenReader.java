package com.example.markdowndesk;

import java.io.*;
import java.util.ArrayDeque;

/** Strict streaming tokens with bounded string windows; skipped values never allocate their text. */
final class JsonTokenReader implements Closeable {
    enum Token { BEGIN_OBJECT, END_OBJECT, BEGIN_ARRAY, END_ARRAY, STRING, NUMBER, BOOLEAN, NULL, END_DOCUMENT }
    static final class Window {
        final String text; final int start, total;
        Window(String text,int start,int total){this.text=text;this.start=start;this.total=total;}
    }
    private static final class Scope { final char end; boolean consumed, ready; Scope(char end){this.end=end;} }
    private final Reader input;
    private final ArrayDeque<Scope> scopes=new ArrayDeque<>();
    private int look=-2;
    interface ChunkConsumer { void accept(String text)throws IOException; }
    private ChunkConsumer chunks;
    private StringBuilder chunk;
    void nextScalarChunks(ChunkConsumer consumer)throws IOException{
        if(chunks!=null)throw new IOException("Nested scalar streaming");
        chunks=consumer;chunk=new StringBuilder(4098);
        try{
            Token type=peek();
            if(type==Token.STRING||type==Token.NUMBER)nextWindow(0,0);
            else if(type==Token.BOOLEAN)consumer.accept(String.valueOf(nextBoolean()));
            else if(type==Token.NULL){nextNull();consumer.accept("null");}
            else throw bad();
            if(chunk.length()>0)consumer.accept(chunk.toString());
        }finally{chunks=null;chunk=null;}
    }
    private void stream(int c,int low)throws IOException{
        if(chunks==null)return;chunk.append((char)c);if(low>=0)chunk.append((char)low);
        if(chunk.length()>=4096){chunks.accept(chunk.toString());chunk.setLength(0);}
    }
    JsonTokenReader(Reader input){this.input=new BufferedReader(input,32768);}
    void setLenient(boolean ignored){}
    private int peekChar()throws IOException{if(look==-2)look=input.read();return look;}
    private int take()throws IOException{int c=peekChar();look=-2;return c;}
    private void ws()throws IOException{while(peekChar()==32||peekChar()==9||peekChar()==10||peekChar()==13)take();}
    private IOException bad(){return new IOException("Malformed JSON or unsupported nesting/identity size");}
    private void require(char c)throws IOException{ws();if(take()!=c)throw bad();}
    private void consumed(){if(!scopes.isEmpty()){scopes.peek().consumed=true;scopes.peek().ready=false;}}
    Token peek()throws IOException{
        ws();switch(peekChar()){
            case '{':return Token.BEGIN_OBJECT;case '}':return Token.END_OBJECT;
            case '[':return Token.BEGIN_ARRAY;case ']':return Token.END_ARRAY;
            case '"':return Token.STRING;case 't':case 'f':return Token.BOOLEAN;case 'n':return Token.NULL;
            case -1:return Token.END_DOCUMENT;default:if(peekChar()=='-'||digit(peekChar()))return Token.NUMBER;throw bad();
        }
    }
    private void begin(char c,char end)throws IOException{require(c);consumed();if(scopes.size()>=160)throw bad();scopes.push(new Scope(end));}
    void beginObject()throws IOException{begin('{','}');}
    void beginArray()throws IOException{begin('[',']');}
    private void end(char c)throws IOException{if(scopes.isEmpty()||scopes.peek().end!=c)throw bad();require(c);scopes.pop();}
    void endObject()throws IOException{end('}');} void endArray()throws IOException{end(']');}
    boolean hasNext()throws IOException{
        if(scopes.isEmpty())throw bad();Scope s=scopes.peek();ws();
        if(s.ready)return true;if(peekChar()==s.end)return false;
        if(s.consumed){require(',');ws();if(peekChar()==s.end)throw bad();}
        s.ready=true;return true;
    }
    String nextName()throws IOException{
        if(scopes.isEmpty()||scopes.peek().end!='}'||!hasNext())throw bad();
        Window w=stringWindow(0,8192);if(w.total>8192)throw bad();require(':');return w.text;
    }
    String nextString()throws IOException{return nextWindow(0,24001).text;}
    Window nextWindow(int start,int size)throws IOException{
        if(start<0||size<0)throw bad();Window w;
        if(peek()==Token.STRING)w=stringWindow(start,size);
        else if(peek()==Token.NUMBER)w=numberWindow(start,size);
        else throw bad();consumed();return w;
    }
    private int unicode()throws IOException{int result=0;for(int i=0;i<4;i++){int c=take();int d=Character.digit(c,16);if(d<0||c>127)throw bad();result=result*16+d;}return result;}
    private int decoded()throws IOException{
        int c=take();if(c<0||c<32)throw bad();if(c!='\\')return c;
        c=take();switch(c){case '"':case '\\':case '/':return c;case 'b':return 8;case 'f':return 12;case 'n':return 10;case 'r':return 13;case 't':return 9;case 'u':return unicode();default:throw bad();}
    }
    private Window stringWindow(int start,int size)throws IOException{
        require('"');StringBuilder b=new StringBuilder(Math.min(size,65536));int pos=0,actual=start;
        while(peekChar()!='"'){
            if((pos&4095)==0&&Thread.currentThread().isInterrupted())throw new IOException("Navigation cancelled");
            int c=decoded();if(Character.isLowSurrogate((char)c))throw bad();
            int low=-1;if(Character.isHighSurrogate((char)c)){low=decoded();if(!Character.isLowSurrogate((char)low))throw bad();}
            int count=low<0?1:2;stream(c,low);
            if(pos<start&&pos+count>start)actual=pos+count;
            if(pos>=start&&pos<(long)start+size){b.append((char)c);if(low>=0)b.append((char)low);}
            if(pos>Integer.MAX_VALUE-count)throw new IOException("JSON scalar exceeds supported offset range");pos+=count;
        }
        take();return new Window(b.toString(),actual,pos);
    }
    private static boolean digit(int c){return c>='0'&&c<='9';}
    private Window numberWindow(int start,int size)throws IOException{
        StringBuilder b=new StringBuilder();int pos=0;int state=0;
        while(true){int c=peekChar();if(c<0||c==','||c==']'||c=='}'||c==32||c==9||c==10||c==13)break;
            if((pos&4095)==0&&Thread.currentThread().isInterrupted())throw new IOException("Navigation cancelled");
            switch(state){
                case 0: if(c=='-'){state=1;break;}
                case 1: if(c=='0')state=2;else if(c>='1'&&c<='9')state=3;else throw bad();break;
                case 2:case 3:if(c=='.')state=4;else if(c=='e'||c=='E')state=6;else if(state==3&&digit(c)){}else throw bad();break;
                case 4:if(digit(c))state=5;else throw bad();break;
                case 5:if(c=='e'||c=='E')state=6;else if(!digit(c))throw bad();break;
                case 6:if(c=='+'||c=='-')state=7;else if(digit(c))state=8;else throw bad();break;
                case 7:if(digit(c))state=8;else throw bad();break;
                case 8:if(!digit(c))throw bad();break;
            }
            stream(c,-1);if(pos>=start&&pos<(long)start+size)b.append((char)c);take();if(pos==Integer.MAX_VALUE)throw bad();pos++;
        }
        if(state!=2&&state!=3&&state!=5&&state!=8)throw bad();return new Window(b.toString(),start,pos);
    }
    private void literal(String s)throws IOException{ws();for(int i=0;i<s.length();i++)if(take()!=s.charAt(i))throw bad();consumed();}
    boolean nextBoolean()throws IOException{ws();boolean b=peekChar()=='t';literal(b?"true":"false");return b;}
    void nextNull()throws IOException{literal("null");}
    void skipValue()throws IOException{
        switch(peek()){
            case BEGIN_OBJECT:beginObject();while(hasNext()){nextName();skipValue();}endObject();break;
            case BEGIN_ARRAY:beginArray();while(hasNext())skipValue();endArray();break;
            case STRING:case NUMBER:nextWindow(0,0);break;
            case BOOLEAN:nextBoolean();break;case NULL:nextNull();break;default:throw bad();
        }
    }
    public void close()throws IOException{input.close();}
}
