package com.example.markdowndesk.workspace.archive;

public final class ArchiveSearchSpecTest {
    static void invalid(String term,String speaker,String from,String to){
        try{new ArchiveSearchSpec(term,speaker,from,to);throw new AssertionError("Invalid query accepted");}
        catch(IllegalArgumentException expected){}
    }
    public static void main(String[] args){
        ArchiveSearchSpec all=new ArchiveSearchSpec(" \\? ","","","");
        if(!all.term.equals("\\?")||all.filtersMessagesOnly())throw new AssertionError("Plain phrase");
        ArchiveSearchSpec filtered=new ArchiveSearchSpec("conversation","assistant","2026-01-01","2026-11-06");
        if(!filtered.filtersMessagesOnly()||!filtered.fromDate.equals("2026-01-01"))throw new AssertionError("Filtered message query");
        invalid("","","","");invalid("a","","2026-02-30","");
        invalid("a","","2026-12-12","2026-11-13");
        invalid("a","","2026/01/01","");
        invalid("a".repeat(1100),"","","");
        System.out.println("PASS: source-local filtered archive search validation");
    }
}