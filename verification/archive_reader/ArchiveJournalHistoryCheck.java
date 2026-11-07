package com.example.markdowndesk.archive;

import java.io.*;
import java.nio.file.*;

public final class ArchiveJournalHistoryCheck {
    private static void require(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    private static ArchiveJournal.Annotation state(String text){
        ArchiveJournal.Annotation a=new ArchiveJournal.Annotation();
        a.id="history-1";a.archiveId="archive1";a.chatId="archive1/chat1";a.messageId="message1";
        a.type="note";a.note=text;a.createdAt=100;a.updatedAt=100;return a;
    }
    public static void main(String[] ignored)throws Exception {
        Path directory=Files.createTempDirectory("md-history-");File file=directory.resolve("notes.ajn2").toFile();
        try {
            ArchiveJournal journal=new ArchiveJournal(file);
            ArchiveJournal.Annotation first=state("Original");journal.save(first);
            ArchiveJournal.Annotation second=first.copy();second.note="Revised";second.updatedAt=200;
            journal.saveIfCurrent(first,second);
            ArchiveJournal.HistoryResult before=journal.history(first);
            require(before.revisions.size()==2,"two distinct saved versions");
            require("Original".equals(before.revisions.get(0).annotation.note),"initial bytes preserved");
            require("Revised".equals(before.revisions.get(1).annotation.note),"edited bytes preserved");
            require(!before.revisions.get(1).deleted,"edit is not deletion");
            journal.deleteIfCurrent(second);
            ArchiveJournal.HistoryResult after=journal.history(second);
            require(after.revisions.size()==3&&after.revisions.get(2).deleted,"tombstone must be visible");
            require(journal.load().annotations.isEmpty(),"history query cannot resurrect deleted note");
            ArchiveJournal.Annotation foreign=state("foreign");foreign.archiveId="archive2";
            try{journal.history(foreign);throw new AssertionError("ID source collision disclosed history");}
            catch(IOException expected){}
            journal.save(foreign);
            try{journal.history(first);throw new AssertionError("mixed source history incorrectly accepted");}
            catch(IOException expected){}
            // A damaged tail may not be described as a complete history.
            try(FileOutputStream output=new FileOutputStream(file,true)){output.write(new byte[]{0x41,0x4a});}
            try{journal.history(first);throw new AssertionError("truncated history silently accepted");}
            catch(IOException expected){}
            File large=directory.resolve("large.ajn2").toFile();
            try {
                ArchiveJournal bulky=new ArchiveJournal(large);
                ArchiveJournal.Annotation original=state("large-source");
                for(int i=0;i<4;i++){
                    ArchiveJournal.Annotation revision=original.copy();
                    revision.note="X".repeat(600000)+i;
                    revision.updatedAt=1000+i;
                    bulky.save(revision);
                }
                try {
                    bulky.history(original);
                    throw new AssertionError("Unbounded large revision history was displayed");
                }catch(IOException oversized){
                    require(oversized.getMessage().contains("too large to display safely"),
                            "Oversized history must fail with a clear resource error");
                }
                ArchiveJournal.ReadResult latest=bulky.load();
                require(latest.annotations.size()==1,"History resource refusal must not remove notes");
                require(latest.annotations.get(0).note.endsWith("3"),
                        "Latest note remains readable after history resource refusal");
            } finally {Files.deleteIfExists(large.toPath());}
            System.out.println("PASS immutable annotation history, revisions, tombstones, source isolation, corrupt tail and bounded memory");
        } finally {Files.deleteIfExists(file.toPath());Files.deleteIfExists(directory);}
    }
}
