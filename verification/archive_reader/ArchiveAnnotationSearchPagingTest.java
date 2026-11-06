package com.example.markdowndesk.workspace.archive;

import com.example.markdowndesk.archive.ArchiveJournal;
import java.io.File;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.List;

public final class ArchiveAnnotationSearchPagingTest {
    static void assertOk(boolean pass,String message){if(!pass)throw new AssertionError(message);}
    public static void main(String[] ignored)throws Exception{
        File dir=Files.createTempDirectory("md-annotation-search-test").toFile();
        try {
            File path=new File(dir,"notes.journal");ArchiveJournal storage=new ArchiveJournal(path);
            for(int i=0;i<263;i++){
                ArchiveJournal.Annotation a=new ArchiveJournal.Annotation();
                a.id="note-"+i;a.archiveId="arc";a.chatId="chat-"+(i%13);
                a.type=i%2==0?"note":"external_link";
                a.label="Something to find "+i;a.quote="sample content";a.note="Find this later";
                a.createdAt=1793980000000L+i;a.updatedAt=a.createdAt;
                storage.save(a);
            }
            HashSet<String> ids=new HashSet<>();int all=0;
            for(int offset=0;;offset+=75){
                List<ArchiveJournal.Annotation> slice=ArchiveAnnotationSearch.page(path,"find",null,75,offset);
                if(slice.isEmpty())break;
                for(ArchiveJournal.Annotation a:slice){assertOk(ids.add(a.id),"duplicate from offset pagination");all++;}
            }
            assertOk(all==263,"must not silently cap saved note search to first 200");
            assertOk(ArchiveAnnotationSearch.page(path,"find","chat-2",100,0).size()==21,"chat filter must remain exact");
            assertOk(ArchiveAnnotationSearch.page(path,"nonexistent",null,100,0).isEmpty(),"no false results");
            assertOk(ArchiveAnnotationSearch.page(path,"find",null,100,10000).isEmpty(),"end of results");
            System.out.println("PASS: paged search of 263 durable annotations, scoped queries, no silent cap");
        } finally {Files.deleteIfExists(new File(dir,"notes.journal").toPath());Files.deleteIfExists(dir.toPath());}
    }
}