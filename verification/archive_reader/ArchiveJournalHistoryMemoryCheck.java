package com.example.markdowndesk.archive;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.Arrays;

/** Independent synthetic AJN2 input to ensure history does not retain unrelated notes. */
public final class ArchiveJournalHistoryMemoryCheck {
    private static void field(DataOutputStream out,String value)throws IOException {
        if(value==null){out.writeInt(-1);return;}
        byte[] bytes=value.getBytes(StandardCharsets.UTF_8);
        out.writeInt(bytes.length);out.write(bytes);
    }
    private static byte[] record(String id,String note)throws IOException {
        ByteArrayOutputStream buffer=new ByteArrayOutputStream();
        try(DataOutputStream out=new DataOutputStream(buffer)) {
            out.writeByte(1);
            field(out,id);field(out,"arc");field(out,"chat");field(out,null);
            field(out,"message");field(out,null);field(out,"note");field(out,null);
            field(out,note);field(out,null);field(out,null);field(out,null);
            field(out,null);field(out,null);
            out.writeInt(-1);out.writeInt(-1);out.writeLong(0);out.writeLong(0);
        }
        return buffer.toByteArray();
    }
    private static ArchiveJournal.Annotation expected(){
        ArchiveJournal.Annotation a=new ArchiveJournal.Annotation();
        a.id="target";a.archiveId="arc";a.chatId="chat";a.messageId="message";
        a.type="note";return a;
    }
    public static void main(String[] ignored)throws Exception {
        Path dir=Files.createTempDirectory("history-memory-");Path path=dir.resolve("journal.ajn2");
        try {
            char[] huge=new char[180000];Arrays.fill(huge,'x');
            String filler=new String(huge);
            MessageDigest sha=MessageDigest.getInstance("SHA-256");
            try(DataOutputStream out=new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(path)))){
                for(int i=0;i<260;i++){
                    byte[] data=record(i==0?"target":"note-"+i,i==0?"original":filler);
                    out.writeInt(0x414A4E32);out.writeInt(data.length);out.write(sha.digest(data));out.write(data);
                }
            }
            ArchiveJournal.HistoryResult result=new ArchiveJournal(path.toFile()).history(expected());
            if(result.revisions.size()!=1||!"original".equals(result.revisions.get(0).annotation.note))
                throw new AssertionError("History must retain exactly the target, not other notes");
            System.out.println("PASS: 46.7MB synthetic journal history under bounded Java heap; one matching note");
        }finally{Files.deleteIfExists(path);Files.deleteIfExists(dir);}
    }
}
