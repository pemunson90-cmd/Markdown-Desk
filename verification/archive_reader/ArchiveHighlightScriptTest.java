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
        a.tags="research,md-color:rose";
        String js=ArchiveHighlightScript.add(Arrays.asList(a,blank));
        check(js.contains("\"tone\":\"rose\""),"Archive tone is fixed and serialized as data");
        a.tags=null;
        check(ArchiveHighlightScript.add(Arrays.asList(a)).contains("\"tone\":\"violet\""),
                "legacy Archive annotations default to violet");
        a.tags="md-color:evil<script>";
        check(ArchiveHighlightScript.add(Arrays.asList(a)).contains("\"tone\":\"violet\""),
                "untrusted color tokens cannot select arbitrary CSS");
        check(js.contains("window.mdArchiveAddHighlights"),"annotation entry point");
        check(!js.contains("</script>"),"JS injection through annotation");
        check(js.contains("\\u003c/script\\u003e"),"dangerous tag encoded");
        check(js.contains("message-7")&&!js.contains("empty"),"only real selections sent");
        String boot=ArchiveHighlightScript.boot();
        check(!boot.contains("positions.length===1"),"unique quotes still require verified context");
        check(boot.contains("valid.length===1"),"ambiguous quote fails closed");
        check(boot.contains("md-archive-tone-")&&boot.contains("violet|rose|mint|amber|blue|slate"),
                "all tone choices come from a fixed allowlist");
        check(boot.contains("mdarchive://annotation"),"tap to original annotation");
        System.out.println("PASS: inline annotation rendering scripts are data-only and fail closed");
    }
}