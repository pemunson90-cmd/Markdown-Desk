package com.example.markdowndesk.workspace.annotations;
import com.example.markdowndesk.archive.ArchiveJournal;
import java.util.Arrays;
public final class MarkdownNoteScriptCheck {
    static void check(boolean b,String why){if(!b)throw new AssertionError(why);}
    public static void main(String[] args) {
        String capture=MarkdownNoteScript.capture(),boot=MarkdownNoteScript.boot();
        check(capture.contains("all.slice(start,end)!==quote"),"capture refuses unverifiable source");
        check(capture.contains("root.contains(r.commonAncestorContainer)"),"selection is inside reader");
        check(boot.contains("valid.length!==1")&&boot.contains("while((p=text.indexOf(q,p))>=0)")&&
                !boot.contains("matches(row.start)"),"every matching passage must be checked, even at old offset");
        check(capture.contains("startContainer.nodeType!==3")&&
                capture.contains("closest(\u0027script,style,noscript,template\u0027)"),"capture ignores script text");
        check(boot.contains("new Highlight(...ranges)")&&boot.contains("CSS.highlights.set"),"non-destructive highlight API");
        ArchiveJournal.Annotation a=new ArchiveJournal.Annotation();
        a.id="id-1";a.quote="</script><svg onload=evil()>💜";
        a.prefix="x\"";a.suffix="\n";
        a.selectionStart=42;
        String js=MarkdownNoteScript.decorate(Arrays.asList(a));
        check(!js.contains("</script>")&&!js.contains("<svg"),"serialized data cannot close script");
        check(js.contains("\\u003c/script\\u003e"),"HTML delimiters escaped");
        check(!MarkdownNoteScript.jump("a\";evil()").contains("a\";evil()"),"jump ID serialized");
        System.out.println("PASS: exact selection, ambiguity rejection, non-destructive marks and JS encoding");
    }
}
