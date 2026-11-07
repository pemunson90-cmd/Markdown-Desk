package com.example.markdowndesk.workspace.search;

public final class UnifiedSearchJumpCheck {
    static void ok(boolean result,String why){if(!result)throw new AssertionError(why);}
    public static void main(String[] ignored){
        String uri="content://docs/tree/root/document/novel.md";
        String hash="a".repeat(64);
        String text="Start 😀 middle phrase and another phrase";
        int exact=text.indexOf("phrase");
        ok(UnifiedSearchJump.matches(uri,uri,hash,hash,text,"phrase",exact,false),
                "exact unchanged source selection must resolve");
        ok(!UnifiedSearchJump.matches(uri,uri,hash,hash,text,"phrase",exact+19,false),
                "another matching passage cannot replace recorded offset");
        ok(UnifiedSearchJump.matches(uri,uri,hash,hash,text,"😀",6,false),
                "offsets are UTF-16 code units, including surrogate pairs");
        ok(!UnifiedSearchJump.matches(uri,uri,hash,hash,text,"😀",7,false),
                "surrogate-half offset must be rejected");
        ok(!UnifiedSearchJump.matches(uri,uri,hash,hash,text,"phrase",exact,true),
                "unsaved editor changes cannot masquerade as indexed source");
        ok(!UnifiedSearchJump.matches("content://other",uri,hash,hash,text,"phrase",exact,false),
                "document identity cannot be substituted");
        ok(!UnifiedSearchJump.matches(uri,uri,hash,"b".repeat(64),text,"phrase",exact,false),
                "changed source hash cannot navigate");
        ok(!UnifiedSearchJump.matches(uri,uri,"bad",hash,text,"phrase",exact,false),
                "invalid hash cannot authorize navigation");
        ok(!UnifiedSearchJump.matches(uri,uri,hash,hash,text,"PHRASE",exact,false),
                "changed quote may not be treated as exact");
        ok(!UnifiedSearchJump.matches(uri,uri,hash,hash,text,"phrase",-1,false),
                "negative offset rejected");
        ok(!UnifiedSearchJump.matches(uri,uri,hash,hash,text,"phrase",Integer.MAX_VALUE,false),
                "overflow offset rejected");
        ok(!UnifiedSearchJump.matches(uri,uri,hash,hash,text,"phrase",text.length()-2,false),
                "out-of-bounds suffix rejected");
        ok(!UnifiedSearchJump.matches(uri,uri,hash,hash,text,"x".repeat(129),0,false),
                "oversized untrusted search literal rejected");
        ok(!UnifiedSearchJump.matches(uri,uri,hash,hash,text,"",0,false),
                "empty search literal rejected");
        System.out.println("PASS: exact Markdown hit source, digest, changed text and UTF-16 offsets");
    }
}
