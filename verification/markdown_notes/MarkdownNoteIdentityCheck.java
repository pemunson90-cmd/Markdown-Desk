package com.example.markdowndesk.workspace.annotations;

import com.example.markdowndesk.archive.ArchiveJournal;

public final class MarkdownNoteIdentityCheck {
    static void check(boolean b,String s){if(!b)throw new AssertionError(s);}
    static void reject(Runnable r){try{r.run();throw new AssertionError("unsafe source accepted");}
        catch(IllegalArgumentException expected){}}
    public static void main(String[] args) {
        String uri="content://com.android.providers.media.documents/document/primary%3Anotes.md";
        ArchiveJournal.Annotation a=MarkdownNoteIdentity.create(uri,"Notes","highlight",
                "quoted phrase","before "," after",7,20,"Label","My note","tag");
        check(MarkdownNoteIdentity.owns(a,uri),"document owner identity");
        check(!MarkdownNoteIdentity.owns(a,uri+"-different"),"do not rebind to another document");
        check(!MarkdownNoteIdentity.owns(a,"content://other/document/1"),"no cross-source bleed");
        check(a.selectionEnd-a.selectionStart==a.quote.length(),"exact quote length");
        check(a.createdAt>0&&a.updatedAt==a.createdAt&&ArchiveJournal.validType(a.type),"journal schema");
        check(com.example.markdowndesk.archive.ArchiveMarkdownIndexes.deepLink(a).startsWith("markdowndesk://document/v1/"),"Markdown note links must route to document not Archive");
        reject(()->MarkdownNoteIdentity.create("file:///secret","n","note","word","","",0,4,"","",""));
        reject(()->MarkdownNoteIdentity.create("content://provider/path","n","note","word","","",0,8,"","",""));
        reject(()->MarkdownNoteIdentity.create(uri,"n","bad","word","","",0,4,"","",""));
        reject(()->MarkdownNoteIdentity.create(uri,"n","note","","","",0,1,"","",""));
        reject(()->MarkdownNoteIdentity.create("content://u@provider/p","n","note","word","","",0,4,"","",""));
        System.out.println("PASS: shared journal Markdown identity, bounds, ownership and rejected sources");
    }
}
