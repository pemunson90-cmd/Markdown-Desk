package com.example.markdowndesk.workspace.archive;

import com.example.markdowndesk.archive.ArchiveJournal;
import java.util.Arrays;

public final class ArchiveHighlightBrowserFixture {
    static ArchiveJournal.Annotation note(String id,String quote,String prefix,String suffix){
        ArchiveJournal.Annotation a=new ArchiveJournal.Annotation();
        a.id=id;a.messageId="msg-1";a.quote=quote;a.prefix=prefix;a.suffix=suffix;
        return a;
    }
    public static void main(String[] args){
        ArchiveJournal.Annotation across=note("note-1","💜 chosen words","Alpha ",".");
        ArchiveJournal.Annotation ambiguous=note("note-2","Repeated","","");
        ArchiveJournal.Annotation bookmark=note("bookmark-1","","","");
        System.out.print("<!doctype html><html><body>"
            +"<section class='archive-msg' data-message-id='msg-1'><div class='archive-meta'>Assistant</div>"
            +"<div class='archive-content'><p>Alpha <strong>💜 chosen</strong> words.</p>"
            +"<p>Repeated and Repeated</p></div></section>"
            +ArchiveHighlightScript.boot()
            +"<script>"+ArchiveHighlightScript.add(Arrays.asList(across,ambiguous,bookmark))+"</script>"
            +"</body></html>");
    }
}