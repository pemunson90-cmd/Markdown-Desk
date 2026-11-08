package com.example.markdowndesk.workspace.archive;

public final class ArchiveSearchMatchCheck {
    private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    private static void span(String body,String offsets,String quote,int start){
        ArchiveSearchMatch.Span s=ArchiveSearchMatch.first(body,offsets);
        check(s!=null,"missing span");check(quote.equals(s.text),"actual source quote");
        check(s.start==start&&s.end==start+quote.length(),"UTF16 source coordinates");
    }
    public static void main(String[] args){
        span("foo-bar café","0 0 0 3 0 1 4 3","foo-bar",0);
        span("😀 café foo","0 0 5 5 0 1 11 3","café foo",3);
        span("a a a","0 0 0 1 0 0 2 1 0 1 2 1 0 1 4 1","a a",0);
        span("x foo-bar foo bar","0 0 2 3 0 1 6 3 0 0 10 3 0 1 14 3","foo-bar",2);
        check(ArchiveSearchMatch.first("café","0 0 0 4")==null,"reject mid UTF8 end");
        check(ArchiveSearchMatch.first("x","0 0 0 99")==null,"reject out of body");
        check(ArchiveSearchMatch.first("x","bad")==null,"reject malformed offsets");
        String original="😀"+"a".repeat(1023);
        String tail=ArchiveOverlap.tail("",original,ArchiveSearchMatch.OVERLAP_UNITS);
        check(!Character.isLowSurrogate(tail.charAt(0)),"overlap begins on character boundary");
        new ArchiveSearchSpec("a".repeat(ArchiveSearchMatch.OVERLAP_UNITS),"","","");
        try{new ArchiveSearchSpec("a".repeat(ArchiveSearchMatch.OVERLAP_UNITS+1),"","","");
            throw new AssertionError("over-limit query accepted");}catch(IllegalArgumentException expected){}
        System.out.println("PASS: FTS source spans, UTF8/UTF16 conversion and shared query bound");
    }
}
