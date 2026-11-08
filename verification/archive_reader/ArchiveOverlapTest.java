package com.example.markdowndesk.workspace.archive;

import java.util.Random;

public final class ArchiveOverlapTest {
    static void check(boolean b,String message){ if(!b)throw new AssertionError(message); }
    static void roundtrip(String source,int size){
        String last="";
        StringBuilder reconstructed=new StringBuilder();
        for(int i=0;i<source.length();){
            int end=Math.min(i+size,source.length());
            // Streaming parser guarantees it never cuts after a high surrogate.
            if(end<source.length() && Character.isHighSurrogate(source.charAt(end-1)))end++;
            String segment=source.substring(i,end);
            String fts=last+segment;
            reconstructed.append(ArchiveTextCodec.reconstruct("",last.length(),fts));
            last=ArchiveOverlap.tail(last,segment,128);
            check(!last.isEmpty() && !Character.isLowSurrogate(last.charAt(0)),"orphan FTS overlap");
            i=end;
        }
        check(source.contentEquals(reconstructed),"round trip did not preserve the source");
    }
    public static void main(String[] args){
        String input="😀💜👩🏽»foo\n".repeat(15_000);
        roundtrip(input,4087);roundtrip(input,4096);roundtrip(input,31);
        Random random=new Random(420);
        String[] atoms={"A","🦇","💜","日","漢"," ","\n","𝄞","👩🏽"};
        StringBuilder fuzz=new StringBuilder();
        for(int i=0;i<100_000;i++)fuzz.append(atoms[random.nextInt(atoms.length)]);
        roundtrip(fuzz.toString(),4096);
        check(ArchiveOverlap.tail("","a💜",2).equals("💜"),"valid full pair");
        check(ArchiveOverlap.tail("","a💜",1).isEmpty(),"don't retain only a low surrogate");
        try{ArchiveOverlap.tail("","x",-1);throw new AssertionError("invalid size");}
        catch(IllegalArgumentException expected){}
        System.out.println("PASS: Unicode FTS overlap boundaries and full segment round trip");
    }
}