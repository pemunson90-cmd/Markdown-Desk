package com.example.markdowndesk.workspace.archive;

import java.nio.charset.StandardCharsets;
import java.util.StringTokenizer;

/** Converts the first native FTS4 phrase occurrence to offsets in its stored body. */
public final class ArchiveSearchOffsets {
    public static final class Span {
        public final int start, end;
        Span(int start,int end){this.start=start;this.end=end;}
    }

    private ArchiveSearchOffsets(){}

    public static Span first(String body,String offsets,int terms){
        if(body==null||offsets==null||terms<=0)return null;
        StringTokenizer fields=new StringTokenizer(offsets);
        int start=-1,previousEnd=-1,nextTerm=0;
        try{
            while(fields.hasMoreTokens()){
                int column=Integer.parseInt(fields.nextToken());
                int term=Integer.parseInt(fields.nextToken());
                int begin=Integer.parseInt(fields.nextToken());
                int length=Integer.parseInt(fields.nextToken());
                if(column!=0||term<0||begin<0||length<=0||begin>Integer.MAX_VALUE-length)
                    return null;
                // A repeated term can have two entries at one offset in overlapping
                // occurrences. The next term of this occurrence starts after its predecessor.
                if(term!=nextTerm||begin<previousEnd)continue;
                if(nextTerm==0)start=begin;
                previousEnd=begin+length;
                if(++nextTerm==terms){
                    byte[] utf8=body.getBytes(StandardCharsets.UTF_8);
                    if(!boundary(utf8,start)||!boundary(utf8,previousEnd))return null;
                    int first=new String(utf8,0,start,StandardCharsets.UTF_8).length();
                    int size=new String(utf8,start,previousEnd-start,StandardCharsets.UTF_8).length();
                    return new Span(first,first+size);
                }
            }
        }catch(IllegalArgumentException|java.util.NoSuchElementException invalid){
            return null;
        }
        return null;
    }

    private static boolean boundary(byte[] bytes,int at){
        return at==bytes.length||(at>=0&&at<bytes.length&&(bytes[at]&0xc0)!=0x80);
    }
}
