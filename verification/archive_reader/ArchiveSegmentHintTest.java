package com.example.markdowndesk.archive;
import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;
public final class ArchiveSegmentHintTest {
    static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    public static void main(String[] args)throws Exception {
        Path path=Files.createTempFile("archive-segment-", ".journal");
        try {
            ArchiveJournal journal=new ArchiveJournal(path.toFile());
            ArchiveJournal.Annotation a=new ArchiveJournal.Annotation();
            a.id="id";a.archiveId="source";a.chatId="chat";a.messageId="message";a.type="note";
            a.quote="selected";a.sourcePart=120;a.createdAt=1;a.updatedAt=2;journal.save(a);
            ArchiveJournal.Annotation stored=journal.load().annotations.get(0);
            check(stored.sourcePart==120&&stored.copy().sourcePart==120,"hint survives round trip/copy");
            ArchiveJournal.Annotation stale=stored.copy();stale.sourcePart=0;
            try{journal.saveIfCurrent(stale,stored);throw new AssertionError("changed hint accepted as same revision");}
            catch(IOException expected){}
            // Reconstruct the original AJN2 payload by removing only the new
            // optional int and regenerating its checksum, retaining all fields.
            byte[] current=Files.readAllBytes(path);
            byte[] oldPayload=Arrays.copyOfRange(current,40,current.length-4);
            try(DataOutputStream out=new DataOutputStream(Files.newOutputStream(path))){
                out.writeInt(0x414a4e32);out.writeInt(oldPayload.length);
                out.write(MessageDigest.getInstance("SHA-256").digest(oldPayload));out.write(oldPayload);
            }
            ArchiveJournal.ReadResult old=journal.load();
            check(!old.truncatedTail&&old.annotations.size()==1,"old AJN2 record preserved");
            check(old.annotations.get(0).sourcePart==-1,"legacy unknown hint defaults safely");
            ArchiveJournal.Annotation edit=old.annotations.get(0).copy();edit.sourcePart=90;edit.updatedAt=3;
            journal.saveIfCurrent(old.annotations.get(0),edit);
            check(journal.history(edit).revisions.size()==2,"old and new revision history preserved");
            check(journal.load().annotations.get(0).sourcePart==90,"mixed old/new journal readable");
            System.out.println("PASS: source segment hint round trip/CAS and original AJN2 mixed history compatibility");
        }finally{Files.deleteIfExists(path);}
    }
}
