package com.example.markdowndesk.archive;

import java.io.*;
import java.nio.file.*;
import java.util.*;

public final class ArchiveCoreTest {
    private static void check(boolean ok,String message) {if(!ok)throw new AssertionError(message);}
    private static String js(String s){
        return "\""+s.replace("\\","\\\\").replace("\"","\\\"").replace("\n","\\n").replace("\r","\\r")+"\"";
    }
    private static final class Collect implements ArchiveStreamParser.Sink {
        final ArrayList<ArchiveStreamParser.ConversationInfo> conversations=new ArrayList<>();
        final ArrayList<ArchiveStreamParser.MessageInfo> messages=new ArrayList<>();
        final ArrayList<String> contents=new ArrayList<>();
        StringBuilder current;
        public void beginConversation(int i){}
        public void beginMessage(int i){current=new StringBuilder();}
        public void textSegment(String s){current.append(s);}
        public void endMessage(ArchiveStreamParser.MessageInfo m){messages.add(m);contents.add(current.toString());}
        public void endConversation(ArchiveStreamParser.ConversationInfo c){conversations.add(c);}
    }
    private static void testImporter()throws Exception{
        StringBuilder json=new StringBuilder("{\"irrelevant\":{\"huge\": [1,2,{\"nested\":true}]},\"conversations\":[{");
        json.append("\"chat_messages\":[");
        String longText="Very long source text 😈\\test\n".repeat(5000);
        for(int i=0;i<305;i++){
            if(i>0)json.append(',');
            json.append("{\"content\":[{\"type\":\"text\",\"text\":")
                .append(js(i==304?longText:"message "+i))
                .append("},{\"type\":\"image\",\"reference\":\"file-123\"}],")
                .append("\"uuid\":\"msg").append(i).append("\",\"sender\":\"human\",\"created_at\":\"2026-11-05T20:00:00Z\"}");
        }
        json.append("],\"name\":\"a title\",\"uuid\":\"chat-1\",\"created_at\":\"2026-10-01\"},")
            .append("{\"name\":\"same title\",\"uuid\":\"chat-2\",\"chat_messages\":[")
            .append("{\"content\":{\"content\":[{\"text\":\"alpha\"},{\"text\":\"beta\"}]},\"sender\":\"assistant\"}")
            .append("]}],\"metadata\":\"ignored\"}");
        Collect got=new Collect();
        int count=new ArchiveStreamParser(new StringReader(json.toString()),got).parse();
        check(count==2,"conversation count");check(got.messages.size()==306,"messages complete");
        check(got.conversations.get(0).messageCount==305,"long conversation count");
        check("chat-1".equals(got.conversations.get(0).id),"IDs after message array");
        check("msg304".equals(got.messages.get(304).id),"last message ID");
        check(got.contents.get(304).contains(longText),"no truncation of >24K message");
        check(got.contents.get(304).contains("[Non-text block: image]"),"nontext explicitly shown");
        check(got.contents.get(305).contains("alpha\n\nbeta"),"nested text order");
        check(got.conversations.get(1).title.equals("same title"),"second title");
        check(got.messages.get(305).sender.equals("assistant"),"sender");
        check(got.contents.get(304).length()>120_000,"large text full length");
        System.out.println("PASS: 305+ message transcript, >120k-char single message, nested blocks and unknown metadata");
    }
    private static void invalid(String s)throws Exception{
        try {new ArchiveStreamParser(new StringReader(s),new Collect()).parse();throw new AssertionError("accepted malformed JSON: "+s);}
        catch(IOException expected){}
    }
    private static void testMalformed()throws Exception{
        invalid("[{\"chat_messages\":[{\"content\":\"illegal\\x\"}]}]");
        invalid("[{\"chat_messages\":[{\"content\":\"hello\"}],\"uuid\":\"a\"}]garbage");
        invalid("[\n {\"uuid\": \"x\", \"name\": \"bad\" ");
        invalid("[{\"uuid\":\"x\",\"name\":\"abc\",\"messages\":[]},]");
        invalid("{\"else\": 1}");
        System.out.println("PASS: malformed and empty archive rejected");
    }
    private static ArchiveJournal.Annotation row(String id,String kind){
        ArchiveJournal.Annotation a=new ArchiveJournal.Annotation();
        a.id=id;a.archiveId="a1";a.chatId="chat-1";a.chatTitle="Narrative designs";
        a.messageId="msg1";a.messageDate="2026-10-01";a.label="Character *decision*";
        a.quote="the line [here]";a.note="revisit this";a.tags="story,novel";
        a.type=kind;a.createdAt=1791234567000L;a.updatedAt=1791234567000L;
        return a;
    }
    private static void testJournalAndIndexes()throws Exception{
        Path folder=Files.createTempDirectory("archive-test-");
        try{
            File target=folder.resolve("annotations.journal").toFile();
            ArchiveJournal journal=new ArchiveJournal(target);
            ArchiveJournal.Annotation note=row("n1","note");
            ArchiveJournal.Annotation link=row("l1","source_link");
            link.target="https://example.org/path?q=stuff";
            journal.save(note);journal.save(link);
            ArchiveJournal.ReadResult read=journal.load();
            check(read.annotations.size()==2,"journal roundtrip");
            String md=ArchiveMarkdownIndexes.perChat("Narrative designs","a1","chat-1",read.annotations);
            String master=ArchiveMarkdownIndexes.master(read.annotations);
            check(md.contains("## Notes, Highlights and Bookmarks"),"notebook section");
            check(md.contains("archive-record-id: n1"),"note retained in per-chat");
            check(master.contains("?link=l1")&&!master.contains("?link=n1"),"master excludes note");
            check(master.contains("Narrative designs")&&master.contains("2026-10-01"),"dates/title");
            check(master.contains("https://example.org/path?q=stuff"),"safe external URL");
            String edited="# My comments\n\n"+ArchiveMarkdownIndexes.BEGIN+"\nold\n"+ArchiveMarkdownIndexes.END+"\n\nmanual tail\n";
            String merged=ArchiveMarkdownIndexes.replaceGeneratedRegion(edited,md);
            check(merged.startsWith("# My comments")&&merged.endsWith("manual tail\n"),"manual sections preserved");
            check(merged.contains("archive-record-id: n1")&&!merged.contains("\nold\n"),"managed section replaced");
            journal.delete("n1");
            check(journal.load().annotations.size()==1,"tombstone preserved link");
            ArchiveJournal.Annotation updated=link.copy();updated.note="updated";journal.save(updated);
            check(journal.load().annotations.get(0).note.equals("updated"),"latest edit wins");
            String sanitized=ArchiveMarkdownIndexes.fileName("../../: Weir*d ","id/unsafe");
            check(!sanitized.contains("/")&&!sanitized.contains("\\"),"filename escape");
            try(FileOutputStream out=new FileOutputStream(target,true)){out.write(new byte[]{0x41,0x4A});}
            check(journal.load().truncatedTail,"truncated crash record detected");
            try(FileOutputStream out=new FileOutputStream(target,false)){out.write("corrupt".getBytes());}
            try{journal.load();throw new AssertionError("accepted corrupt journal");}catch(IOException expected){}
        }finally{try(java.util.stream.Stream<Path> paths=Files.walk(folder)){
            paths.sorted(Comparator.reverseOrder()).forEach(p->{try{Files.deleteIfExists(p);}catch(IOException e){throw new RuntimeException(e);}});
        }}
        System.out.println("PASS: fsynced journal, edits/tombstones, master links only, Markdown sidecars and corruption detection");
    }
    public static void main(String[] args)throws Exception {
        testImporter();testMalformed();testJournalAndIndexes();
        System.out.println("ARCHIVE CORE HOST ACCEPTANCE PASS");
    }
}