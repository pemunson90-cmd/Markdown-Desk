package com.example.markdowndesk.archive;
import java.nio.file.Files;
import java.nio.file.Path;
import java.io.IOException;
import java.util.List;

/** Optimistic per-annotation edit/delete checks under the real process lock. */
public final class ArchiveJournalConditionalCheck {
    private static ArchiveJournal.Annotation note(String id,String value){
        ArchiveJournal.Annotation a=new ArchiveJournal.Annotation();
        a.id=id;a.archiveId="archive";a.chatId="conversation";a.messageId="message";
        a.type="note";a.note=value;a.createdAt=100L;a.updatedAt=100L;
        return a;
    }
    private static void check(boolean ok,String label){if(!ok)throw new AssertionError(label);}
    private static void stale(Checked action,String label)throws Exception {
        boolean rejected=false;
        try{action.run();}catch(IOException expected){rejected=true;}
        check(rejected,label);
    }
    private interface Checked {void run()throws Exception;}
    public static void main(String[] args)throws Exception {
        Path root=Files.createTempDirectory("journal-cas-"),file=root.resolve("notes.ajn2");
        try {
            ArchiveJournal first=new ArchiveJournal(file.toFile());
            ArchiveJournal other=new ArchiveJournal(file.toFile());
            first.save(note("n","initial"));
            ArchiveJournal.Annotation snapshot=other.load().annotations.get(0).copy();
            ArchiveJournal.Annotation update=snapshot.copy();
            update.note="version 2";update.updatedAt=200;
            first.saveIfCurrent(snapshot,update);
            check("version 2".equals(other.load().annotations.get(0).note),"current edit saved");
            ArchiveJournal.Annotation older=snapshot.copy();
            older.note="stale overwrite";older.updatedAt=300;
            stale(()->other.saveIfCurrent(snapshot,older),"stale editor must not overwrite");
            stale(()->other.deleteIfCurrent(snapshot),"stale delete must not delete newer edit");
            ArchiveJournal.Annotation current=other.load().annotations.get(0).copy();
            other.deleteIfCurrent(current);
            check(other.load().annotations.isEmpty(),"confirmed delete");
            stale(()->first.saveIfCurrent(current,update),"stale editor must not resurrect deleted note");
            stale(()->first.deleteIfCurrent(current),"repeated deletion must not succeed");
            first.save(note("unrelated","keep"));
            ArchiveJournal.Annotation foreign=first.load().annotations.get(0).copy();
            ArchiveJournal.Annotation forged=foreign.copy();forged.chatId="other source";
            stale(()->first.saveIfCurrent(foreign,forged),"source identity must stay stable");
            check("keep".equals(first.load().annotations.get(0).note),"rejecting stale edit preserves original");
            System.out.println("PASS: conditional journal edits/deletes prevent lost updates and resurrection");
        } finally {
            Files.deleteIfExists(file);Files.deleteIfExists(root);
        }
    }
}
