package com.example.markdowndesk.workspace.archive;
import com.example.markdowndesk.archive.ArchiveJournal;
import java.io.File;
import java.nio.file.Files;
import java.util.List;
public final class ArchiveWorkspaceFeaturesTest {
    static void check(boolean pass,String name){if(!pass)throw new AssertionError(name);}
    public static void main(String[] args)throws Exception {
        File temp=Files.createTempFile("markdown-research-", ".journal").toFile();
        try {
            ArchiveJournal journal=new ArchiveJournal(temp);
            ArchiveJournal.Annotation a=new ArchiveJournal.Annotation();
            a.id="anno-1";a.archiveId="source id";a.chatId="chat/one";a.messageId="msg?2";
            a.chatTitle="Research on the radio tower";a.type="note";
            a.note="Continuity: character exits first";a.quote="the door was open";a.tags="continuity,NSTL";
            a.createdAt=1;a.updatedAt=2;
            journal.save(a);
            ArchiveJournal.Annotation link=a.copy();link.id="saved-link";link.type="external_link";
            link.note="Research citation";link.tags="reference";link.target="https://example.org/source";
            journal.save(link);
            check(ArchiveAnnotationSearch.find(temp,"CONTINUITY",null,50).size()==1,"notes case-insensitive");
            check(ArchiveAnnotationSearch.find(temp,"source", "chat/one",50).size()==1,"link search");
            check(ArchiveAnnotationSearch.find(temp,"the door", "other-chat",50).isEmpty(),"conversation scoping");
            check(ArchiveAnnotationSearch.find(temp,"the door", "chat/one",50).size()==2,"quote search");
            String uri=ArchiveCitations.deepLink("source id","chat/one","msg?2");
            check(uri.equals("markdowndesk://archive/v1/source%20id/chat%2Fone/msg%3F2"),"stable URI encoding");
            String citation=ArchiveCitations.citation("Research","2026-11-06","source id","chat/one","msg?2","original text");
            check(citation.contains("original text")&&citation.contains("2026-11-06")&&citation.contains(uri),"quote provenance");
            journal.delete("anno-1");
            check(ArchiveAnnotationSearch.find(temp,"continuity",null,50).isEmpty(),"deleted notes excluded");
            System.out.println("PASS: annotation search, scope, link search, citations, tombstones");
        }finally{Files.deleteIfExists(temp.toPath());}
    }
}