package com.example.markdowndesk.workspace.archive;

public final class ArchiveTextCodecTest {
    static void eq(String expected, String actual, String message) {
        if (!expected.equals(actual)) throw new AssertionError(message + " expected=" + expected + " actual=" + actual);
    }
    static void rejected(Runnable body, String message) {
        try { body.run(); throw new AssertionError(message); }
        catch (IllegalArgumentException expected) {}
    }
    public static void main(String[] args) {
        eq("abc", ArchiveTextCodec.reconstruct("abc",0,"ignored"), "pre-v2 text");
        eq("xyz", ArchiveTextCodec.reconstruct("",0,"xyz"), "new part no overlap");
        eq("second", ArchiveTextCodec.reconstruct("",2,"💜second"), "single supplementary code point");
        eq("still here", ArchiveTextCodec.reconstruct("",4,"👩🏽still here"), "combined emoji with skin tone modifier");
        eq("C", ArchiveTextCodec.reconstruct("",6,"A💜B💜C"), "mixed ASCII and emoji");
        eq("☃", ArchiveTextCodec.reconstruct("",4,"a💜b☃"), "BMP after surrogate");
        rejected(()->ArchiveTextCodec.reconstruct("",2,"a"), "corrupt overlap should throw");
        rejected(()->ArchiveTextCodec.reconstruct("",1,"💜source"), "split surrogate should throw");
        rejected(()->ArchiveTextCodec.reconstruct("",-1,"hello"), "negative overlap should throw");
        rejected(()->ArchiveTextCodec.reconstruct("",0,null), "missing FTS body should throw");
        eq("",ArchiveTextCodec.reconstruct("",3,"abc"),"empty final segment is safe");
        System.out.println("PASS: lossless UTF-16-indexed archive segment recovery");
    }
}