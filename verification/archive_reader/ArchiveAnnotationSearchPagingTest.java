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
                a.type=i%2==0?"note":"external_link";a.tags=i%3==0?"priority, work":"other";
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
            int notes=0, priority=0, filtered=0;HashSet<String> kinds=new HashSet<>();
            for(int offset=0;;offset+=35){
                List<ArchiveJournal.Annotation> part=ArchiveAnnotationSearch.pageFiltered(
                        path,"find",null,35,offset,"note",null,"archive");
                if(part.isEmpty())break;
                for(ArchiveJournal.Annotation a:part){
                    assertOk(kinds.add(a.id),"filtered paging duplicated a note");
                    assertOk("note".equals(a.type),"filtered page leaked another kind");
                    notes++;
                }
            }
            assertOk(notes==132,"kind filter silently truncated or skipped an annotation");
            for(int offset=0;;offset+=25){
                List<ArchiveJournal.Annotation> part=ArchiveAnnotationSearch.pageFiltered(
                        path,"find",null,25,offset,null,"PRIORITY","archive");
                if(part.isEmpty())break;
                for(ArchiveJournal.Annotation a:part){
                    assertOk(a.tags.startsWith("priority"),"tag must be exact and case-insensitive");
                    priority++;
                }
            }
            assertOk(priority==88,"exact tag-filtered pages must preserve all matching notes");
            assertOk(ArchiveAnnotationSearch.pageFiltered(path,"find",null,100,0,
                    null,"prio","archive").isEmpty(),"tag filter must not use substring matching");
            assertOk(ArchiveAnnotationSearch.pageFiltered(path,"find",null,100,0,
                    null,null,"markdown").isEmpty(),"archive notes may not leak into Markdown scope");
            assertOk(ArchiveAnnotationSearch.pageFiltered(path,"find",null,20,20,
                    "note",null,"archive").size()==20,"offset applies to filtered results");
            try {
                ArchiveAnnotationSearch.pageFiltered(path,"find",null,10,0,null,null,"invalid");
                throw new AssertionError("invalid note source scope was accepted");
            }catch(IllegalArgumentException expected){}
            try {
                ArchiveAnnotationSearch.pageFiltered(path,"find",null,10,0,null,"priority,other","archive");
                throw new AssertionError("ambiguous multi-tag query was accepted");
            }catch(IllegalArgumentException expected){}
            assertOk(ArchiveAnnotationSearch.page(path,"find",null,100,10000).isEmpty(),"end of results");
            ArchiveJournal.Annotation jsonNote=new ArchiveJournal.Annotation();
            jsonNote.id="json-note";jsonNote.archiveId="json-document";
            jsonNote.chatId="content://example.documents/document/chats.json";
            jsonNote.messageId="$[\\\"data\\\"]";
            jsonNote.type="note";jsonNote.label="find exact JSON source";
            jsonNote.quote="source quote";jsonNote.tags="search,md-color:rose";
            storage.save(jsonNote);
            assertOk(ArchiveAnnotationSearch.pageFiltered(path,"find exact JSON",null,10,0,
                    null,null,"json").size()==1,"JSON origin must include a saved JSON note");
            assertOk(ArchiveAnnotationSearch.pageFiltered(path,"find exact JSON",null,10,0,
                    null,null,"archive").isEmpty(),"raw JSON notes must not masquerade as Archive");
            assertOk(ArchiveAnnotationSearch.pageFiltered(path,"find exact JSON",null,10,0,
                    null,null,"markdown").isEmpty(),"raw JSON notes must not masquerade as Markdown");
            ArchiveJournal.Annotation unowned=new ArchiveJournal.Annotation();
            unowned.id="unnamed-source";unowned.chatId="chat-orphan";unowned.messageId="message";
            unowned.type="note";unowned.label="find an unnamed source";
            storage.save(unowned);
            assertOk(ArchiveAnnotationSearch.pageFiltered(path,"find","chat-orphan",10,0,
                    null,null,"archive").isEmpty(),
                    "missing archive identity must not masquerade as an archive result");
            assertOk(ArchiveAnnotationSearch.pageFiltered(path,"find","chat-orphan",10,0,
                    null,null,"markdown").isEmpty(),
                    "unknown source must not masquerade as Markdown");
            assertOk(ArchiveAnnotationSearch.page(path,"find","chat-orphan",10,0).size()==1,
                    "unnamed legacy notes must remain retrievable without a source filter");
            System.out.println("PASS: paged search of 263 durable annotations, scoped queries, no silent cap");
        } finally {Files.deleteIfExists(new File(dir,"notes.journal").toPath());Files.deleteIfExists(dir.toPath());}
    }
}