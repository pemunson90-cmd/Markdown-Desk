package com.example.markdowndesk.workspace.archive;

import com.example.markdowndesk.archive.ArchiveJournal;
import java.util.Arrays;

public final class ArchiveHighlightScriptTest {
    static void check(boolean v,String message){if(!v)throw new AssertionError(message);}
    public static void main(String[] args){
        ArchiveJournal.Annotation a=new ArchiveJournal.Annotation();
        a.id="id\"</script>💜";a.messageId="message-7";a.quote="The 💜 phrase\nacross lines";
        a.prefix="a\"<script>";a.suffix="\r\n";
        ArchiveJournal.Annotation blank=new ArchiveJournal.Annotation();blank.id="empty";blank.messageId="m";blank.quote="";
        String js=ArchiveHighlightScript.add(Arrays.asList(a,blank));
        check(js.contains("window.mdArchiveAddHighlights"),"annotation entry point");
        check(!js.contains("</script>"),"JS injection through annotation");
        check(js.contains("\\u003c/script\\u003e"),"dangerous tag encoded");
        check(js.contains("message-7")&&js.contains("empty"),"bookmarks remain visible as note badges");
        String boot=ArchiveHighlightScript.boot();
        check(boot.contains("positions.length===1"),"unique quote requirement");
        check(boot.contains("function find")&&boot.contains("return positions.length===1"),"ambiguous quote fails closed");
        check(boot.contains("mdarchive://annotation")&&boot.contains("annotationlist"),"tap to original annotation or message notes");
        System.out.println("PASS: inline annotation rendering scripts are data-only and fail closed");
    }
}