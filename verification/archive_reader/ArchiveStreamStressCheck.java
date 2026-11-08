package com.example.markdowndesk.archive;

import java.io.IOException;
import java.io.Reader;
import java.util.Arrays;

/** Synthetic 700 MB-class production parser regression. No generated archive file or Android dependency. */
public final class ArchiveStreamStressCheck {
    private static final int CHATS=1400, PER_CHAT=50, TEXT_CHARS=10000;
    private static final String TEXT="The quick brown fox jumps over 12345. ".repeat(300).substring(0,TEXT_CHARS);
    private static final class Input extends Reader {
        private int chat,message,phase,offset;
        private String chunk="[";
        private boolean closed;
        private String next() {
            switch(phase++){
                case 0: return "{\"uuid\":\"chat-"+chat+"\",\"name\":\"Synthetic\",\"chat_messages\":[";
                case 1: return (message==0?"":",")+"{\"uuid\":\"m-"+chat+"-"+message+"\",\"sender\":\"human\",\"content\":\"";
                case 2: return TEXT;
                case 3:
                    message++;
                    if(message<PER_CHAT)phase=1;else phase=4;
                    return "\"}";
                case 4:
                    chat++;message=0;phase=0;
                    return "]}"+(chat<CHATS?",":"]");
                default: throw new AssertionError("Unrecognized streaming stage");
            }
        }
        @Override public int read(char[] out,int start,int length)throws IOException {
            if(closed)throw new IOException("Input closed");
            if(start<0||length<0||start>out.length-length)throw new IndexOutOfBoundsException();
            if(length==0)return 0;
            if(chunk==null)return -1;
            int copied=0;
            while(copied<length) {
                if(offset==chunk.length()) {
                    if(chat==CHATS&&chunk.endsWith("]")) {chunk=null;break;}
                    chunk=next();offset=0;
                    continue;
                }
                int take=Math.min(length-copied,chunk.length()-offset);
                chunk.getChars(offset,offset+take,out,start+copied);
                copied+=take;offset+=take;
            }
            return copied==0?-1:copied;
        }
        @Override public void close(){closed=true;}
    }
    private static final class Checks implements ArchiveStreamParser.Sink {
        int conversations,messages,segments;long chars;int charsThisMessage;
        @Override public void beginConversation(int ordinal){ if(ordinal!=conversations)throw new AssertionError("Skipped conversation"); }
        @Override public void beginMessage(int ordinal){
            if(ordinal!=messages%PER_CHAT)throw new AssertionError("Skipped message");
            charsThisMessage=0;
        }
        @Override public void textSegment(String segment){
            if(segment==null||segment.length()>ArchiveStreamParser.SEGMENT_CHARS)
                throw new AssertionError("Oversize or missing parser segment");
            chars+=segment.length();charsThisMessage+=segment.length();segments++;
        }
        @Override public void endMessage(ArchiveStreamParser.MessageInfo message){
            if(charsThisMessage!=TEXT_CHARS||!message.textPresent)throw new AssertionError("Truncated content");
            messages++;
        }
        @Override public void endConversation(ArchiveStreamParser.ConversationInfo conversation){
            if(conversation.messageCount!=PER_CHAT)throw new AssertionError("Incomplete conversation");
            conversations++;
        }
    }
    public static void main(String[] ignored)throws Exception {
        long start=System.nanoTime();Checks check=new Checks();
        int parsed=new ArchiveStreamParser(new Input(),check).parse();
        if(parsed!=CHATS||check.conversations!=CHATS||check.messages!=CHATS*PER_CHAT||
                check.chars!=(long)CHATS*PER_CHAT*TEXT_CHARS || check.segments<check.messages*2)
            throw new AssertionError("Missing archive data: "+parsed+" chats/"+check.messages+" messages/"+check.chars+" chars");
        double seconds=(System.nanoTime()-start)/1e9;
        System.out.printf(java.util.Locale.ROOT,
                "PASS production streaming Claude parser: chats=%d messages=%d segments=%d chars=%d seconds=%.2f%n",
                check.conversations,check.messages,check.segments,check.chars,seconds);
    }
}
