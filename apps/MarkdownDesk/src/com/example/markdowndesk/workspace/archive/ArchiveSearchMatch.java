package com.example.markdowndesk.workspace.archive;

import java.nio.charset.StandardCharsets;

/** Decode SQLite FTS4 offsets against its original source body, not the query.
 * Byte offsets from unicode61 must not be confused with Java/DOM UTF-16 offsets.
 */
public final class ArchiveSearchMatch {
    // Queries accept at most 1024 UTF-16 units. This covers literal phrases of
    // that size across boundaries. Token matches spread over longer punctuation
    // gaps remain a documented beta limitation; memory is never unbounded.
    public static final int OVERLAP_UNITS=1024;
    private ArchiveSearchMatch() {}
    public static final class Span {
        public final int start,end;
        public final String text;
        Span(String body,int start,int end){this.start=start;this.end=end;text=body.substring(start,end);}
    }
    public static Span first(String body,String offsets){
        if(body==null||offsets==null||offsets.trim().isEmpty())return null;
        String[] fields=offsets.trim().split("\\s+");
        if(fields.length%4!=0)return null;
        try{
            int count=fields.length/4,maxTerm=-1;
            int[] term=new int[count],begin=new int[count],end=new int[count];
            for(int i=0;i<count;i++){
                if(Integer.parseInt(fields[i*4])!=0)return null;
                term[i]=Integer.parseInt(fields[i*4+1]);
                begin[i]=Integer.parseInt(fields[i*4+2]);
                int length=Integer.parseInt(fields[i*4+3]);
                if(term[i]<0||begin[i]<0||length<=0||begin[i]>Integer.MAX_VALUE-length)return null;
                end[i]=begin[i]+length;maxTerm=Math.max(maxTerm,term[i]);
            }
            int start=-1,last=-1,next=0;
            for(int i=0;i<count;i++){
                if(term[i]!=next||begin[i]<last)continue;
                if(next==0)start=begin[i];
                last=end[i];next++;
                if(next>maxTerm){
                    byte[] bytes=body.getBytes(StandardCharsets.UTF_8);
                    if(start<0||last>bytes.length||!boundary(bytes,start)||!boundary(bytes,last))return null;
                    int first=new String(bytes,0,start,StandardCharsets.UTF_8).length();
                    int finish=first+new String(bytes,start,last-start,StandardCharsets.UTF_8).length();
                    return new Span(body,first,finish);
                }
            }
        }catch(NumberFormatException|IndexOutOfBoundsException invalid){return null;}
        return null;
    }
    private static boolean boundary(byte[] bytes,int at){
        return at==bytes.length||(at>=0&&at<bytes.length&&(bytes[at]&0xc0)!=0x80);
    }
}
