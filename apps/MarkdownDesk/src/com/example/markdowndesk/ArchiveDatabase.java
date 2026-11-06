package com.example.markdowndesk;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SQLiteOpenHelper;

import com.example.markdowndesk.archive.ArchiveStreamParser;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** Database is a rebuildable local search and reading cache, never the original export. */
final class ArchiveDatabase extends SQLiteOpenHelper implements ArchiveStreamParser.Sink {
    static final String FILE="archive_catalog_v1.db";
    static final int VERSION=1;
    static final int PAGE=60;
    static final class Conversation {
        String id, archiveId, name, created, updated;
        int messages;
        Conversation(String id,String archiveId,String name,String created,String updated,int messages) {
            this.id=id;this.archiveId=archiveId;this.name=name;this.created=created;this.updated=updated;this.messages=messages;
        }
    }
    static final class Message {
        long id;
        String conversationId, messageId, role, created, text;
        int ordinal, segments;
        Message(long id,String chat,String messageId,String role,String created,String text,int ordinal,int segments){
            this.id=id;this.conversationId=chat;this.messageId=messageId;this.role=role;
            this.created=created;this.text=text;this.ordinal=ordinal;this.segments=segments;
        }
    }
    static final class Hit {
        String chatId, chatTitle, messageId, role, created, excerpt;
        int messageOrdinal;
    }
    private final String archive;
    private SQLiteDatabase ingest;
    private String stagingArchive;
    private String importingChat;
    private long importingMessage;
    private int part;
    private int segmentOffset;
    private String overlap="";
    private int batchMessages;
    private boolean cancelled;

    ArchiveDatabase(Context context,String archiveId){super(context,FILE,null,VERSION);archive=archiveId;}
    @Override public void onCreate(SQLiteDatabase db){
        db.execSQL("CREATE TABLE sources (id TEXT PRIMARY KEY, uri TEXT NOT NULL, name TEXT, indexed_at INTEGER, status TEXT)");
        db.execSQL("CREATE TABLE conversations (id TEXT PRIMARY KEY, archive_id TEXT NOT NULL, title TEXT, created TEXT, updated TEXT, message_count INTEGER NOT NULL DEFAULT 0)");
        db.execSQL("CREATE INDEX conversation_dates ON conversations(updated)");
        db.execSQL("CREATE TABLE messages (row_id INTEGER PRIMARY KEY AUTOINCREMENT, conversation_id TEXT NOT NULL, ordinal INTEGER NOT NULL, message_id TEXT, role TEXT, created TEXT, segments INTEGER NOT NULL DEFAULT 0)");
        db.execSQL("CREATE INDEX message_position ON messages(conversation_id,ordinal)");
        db.execSQL("CREATE INDEX message_identity ON messages(conversation_id,message_id)");
        db.execSQL("CREATE TABLE parts (row_id INTEGER PRIMARY KEY AUTOINCREMENT, message_row INTEGER NOT NULL, ordinal INTEGER NOT NULL, start_offset INTEGER NOT NULL, content TEXT NOT NULL)");
        db.execSQL("CREATE INDEX parts_by_message ON parts(message_row,ordinal)");
        db.execSQL("CREATE VIRTUAL TABLE search_parts USING fts4(body,tokenize=unicode61)");
    }
    @Override public void onUpgrade(SQLiteDatabase db,int oldVersion,int newVersion){
        throw new IllegalStateException("Archive database upgrade needs a non-destructive migration");
    }

    synchronized void beginImport(String uri,String name){
        if(ingest!=null)throw new IllegalStateException("Import already active");
        ingest=getWritableDatabase();cancelled=false;batchMessages=0;
        stagingArchive=archive+"#staging#"+java.util.UUID.randomUUID();
        ingest.beginTransaction();
        // Never remove the previous successful catalogue until the replacement
        // has completed and its full staging import is durable.
        ContentValues source=new ContentValues();source.put("id",stagingArchive);source.put("uri",uri);
        source.put("name",name);source.put("status","importing");
        ingest.insertOrThrow("sources",null,source);
    }
    synchronized void completeImport(){
        if(ingest==null)return;
        if(cancelled)throw new IllegalStateException("Import cancelled");
        ingest.setTransactionSuccessful();ingest.endTransaction();
        ingest.beginTransaction();
        try {
            // Swap the completed staging dataset into place as one transaction.
            ingest.execSQL("DELETE FROM search_parts WHERE docid IN (SELECT p.row_id FROM parts p JOIN messages m ON p.message_row=m.row_id JOIN conversations c ON m.conversation_id=c.id WHERE c.archive_id=?)",new Object[]{archive});
            ingest.execSQL("DELETE FROM parts WHERE message_row IN (SELECT m.row_id FROM messages m JOIN conversations c ON m.conversation_id=c.id WHERE c.archive_id=?)",new Object[]{archive});
            ingest.execSQL("DELETE FROM messages WHERE conversation_id IN (SELECT id FROM conversations WHERE archive_id=?)",new Object[]{archive});
            ingest.execSQL("DELETE FROM conversations WHERE archive_id=?",new Object[]{archive});
            ingest.execSQL("DELETE FROM sources WHERE id=?",new Object[]{archive});
            ArrayList<String> stagedIds=new ArrayList<>();
            try(Cursor staged=ingest.rawQuery("SELECT id FROM conversations WHERE archive_id=?",new String[]{stagingArchive})){
                while(staged.moveToNext())stagedIds.add(staged.getString(0));
            }
            for(String provisional:stagedIds){
                String canonical=archive+provisional.substring(stagingArchive.length());
                ContentValues m=new ContentValues();m.put("conversation_id",canonical);
                ingest.update("messages",m,"conversation_id=?",new String[]{provisional});
                ContentValues c=new ContentValues();c.put("id",canonical);c.put("archive_id",archive);
                ingest.update("conversations",c,"id=?",new String[]{provisional});
            }
            ContentValues src=new ContentValues();src.put("id",archive);
            src.put("status","ready");src.put("indexed_at",System.currentTimeMillis());
            ingest.update("sources",src,"id=?",new String[]{stagingArchive});
            ingest.setTransactionSuccessful();
        } finally {
            ingest.endTransaction();ingest=null;stagingArchive=null;
        }
    }
    synchronized void abortImport(){
        cancelled=true;
        if(ingest!=null){ingest.endTransaction();ingest=null;}
        // Staged partial batches remain ignored; previous READY source stays intact.
    }
    @Override public void beginConversation(int ordinal)throws IOException{
        checkActive();importingChat=stagingArchive+"#pending#"+ordinal;
        ContentValues v=new ContentValues();v.put("id",importingChat);v.put("archive_id",archive);
        v.put("title","Importing conversation "+(ordinal+1));
        ingest.insertOrThrow("conversations",null,v);
    }
    @Override public void beginMessage(int ordinal)throws IOException{
        checkActive();part=0;segmentOffset=0;overlap="";
        ContentValues v=new ContentValues();v.put("conversation_id",importingChat);v.put("ordinal",ordinal);
        importingMessage=ingest.insertOrThrow("messages",null,v);
    }
    @Override public void textSegment(String segment)throws IOException{
        checkActive();
        if(segment==null||segment.isEmpty())return;
        ContentValues row=new ContentValues();
        row.put("message_row",importingMessage);row.put("ordinal",part++);
        row.put("start_offset",segmentOffset);row.put("content",segment);
        long partId=ingest.insertOrThrow("parts",null,row);
        // 128-character overlap makes phrase matches across neighboring segments possible.
        ContentValues fts=new ContentValues();fts.put("docid",partId);fts.put("body",overlap+segment);
        ingest.insertOrThrow("search_parts",null,fts);
        String combined=overlap+segment;
        overlap=combined.length()>128?combined.substring(combined.length()-128):combined;
        segmentOffset+=segment.length();
    }
    @Override public void endMessage(ArchiveStreamParser.MessageInfo info)throws IOException{
        checkActive();
        ContentValues v=new ContentValues();v.put("message_id",info.id);v.put("role",info.sender);
        v.put("created",info.createdAt);v.put("segments",part);
        ingest.update("messages",v,"row_id=?",new String[]{Long.toString(importingMessage)});
        if(++batchMessages>=160){
            ingest.setTransactionSuccessful();ingest.endTransaction();ingest.beginTransaction();batchMessages=0;
        }
    }
    @Override public void endConversation(ArchiveStreamParser.ConversationInfo info)throws IOException{
        checkActive();
        String canonical=stagingArchive+"/"+info.id;
        // Both sides can exist when a regenerated export repeats a conversation ID.
        // Never silently combine two different conversations in this situation.
        if(!canonical.equals(importingChat)){
            try(Cursor c=ingest.rawQuery("SELECT 1 FROM conversations WHERE id=?",new String[]{canonical})){
                if(c.moveToFirst())throw new IOException("Duplicate conversation identity in export: "+info.id);
            }
            ContentValues m=new ContentValues();m.put("conversation_id",canonical);
            ingest.update("messages",m,"conversation_id=?",new String[]{importingChat});
            ContentValues v=new ContentValues();v.put("id",canonical);
            v.put("title",info.title);v.put("created",info.createdAt);
            v.put("updated",info.updatedAt);v.put("message_count",info.messageCount);
            ingest.update("conversations",v,"id=?",new String[]{importingChat});
        }else{
            ContentValues v=new ContentValues();v.put("title",info.title);v.put("created",info.createdAt);
            v.put("updated",info.updatedAt);v.put("message_count",info.messageCount);
            ingest.update("conversations",v,"id=?",new String[]{canonical});
        }
    }
    private void checkActive()throws IOException {
        if(ingest==null || cancelled||Thread.currentThread().isInterrupted())throw new IOException("Archive indexing interrupted");
    }
    synchronized List<Conversation> conversations(String titleFilter,int limit,int offset){
        ArrayList<Conversation> result=new ArrayList<>();
        boolean filtered=titleFilter!=null&&!titleFilter.isEmpty();
        String[] args=filtered?new String[]{"%"+titleFilter.replace("\\","\\\\")
                .replace("%","\\%").replace("_","\\_")+"%"}:new String[0];
        String clause=filtered?" AND c.title LIKE ? ESCAPE '\\'":"";
        try(Cursor c=getReadableDatabase().rawQuery(
                "SELECT c.id,c.archive_id,c.title,c.created,c.updated,c.message_count FROM conversations c " +
                "JOIN sources s ON c.archive_id=s.id WHERE s.status='ready'"+clause+
                " ORDER BY COALESCE(c.updated,c.created) DESC LIMIT " +
                Math.max(1,Math.min(limit,200))+" OFFSET "+Math.max(0,offset),args)){
            while(c.moveToNext())result.add(new Conversation(c.getString(0),c.getString(1),c.getString(2),c.getString(3),c.getString(4),c.getInt(5)));
        }
        return result;
    }
    synchronized List<Message> messages(String chat,int offset,int limit){
        ArrayList<Message> result=new ArrayList<>();
        try(Cursor c=getReadableDatabase().rawQuery("SELECT row_id,conversation_id,message_id,role,created,ordinal,segments FROM messages WHERE conversation_id=? ORDER BY ordinal LIMIT "+Math.max(1,Math.min(limit,150))+" OFFSET "+Math.max(0,offset),new String[]{chat})){
            while(c.moveToNext())result.add(new Message(c.getLong(0),c.getString(1),c.getString(2),c.getString(3),c.getString(4),null,c.getInt(5),c.getInt(6)));
        }
        return result;
    }
    synchronized String text(long messageRow,int maxChars){
        StringBuilder result=new StringBuilder();
        try(Cursor c=getReadableDatabase().rawQuery("SELECT content FROM parts WHERE message_row=? ORDER BY ordinal",new String[]{Long.toString(messageRow)})){
            while(c.moveToNext()){
                String segment=c.getString(0);
                if(result.length()+segment.length()>maxChars)break;
                result.append(segment);
            }
        }
        return result.toString();
    }
    synchronized String textChunk(long messageRow,int startPart,int take){
        StringBuilder result=new StringBuilder();
        try(Cursor c=getReadableDatabase().rawQuery("SELECT content FROM parts WHERE message_row=? AND ordinal>=? ORDER BY ordinal LIMIT "+Math.max(1,Math.min(take,64)),new String[]{Long.toString(messageRow),Integer.toString(startPart)})){
            while(c.moveToNext())result.append(c.getString(0));
        }
        return result.toString();
    }
    synchronized List<Hit> search(String query,String chat,int limit,int offset){
        return search(query,chat,null,null,null,limit,offset);
    }
    synchronized List<Hit> search(String query,String chat,String speaker,
                                  String fromDate,String toDate,int limit,int offset){
        ArrayList<Hit> result=new ArrayList<>();
        if(query==null||query.trim().isEmpty())return result;
        // FTS hits are indexed by segment, not by message. Grouping the snippet
        // query directly is unsupported by SQLite FTS4, and deduplicating a small
        // post-LIMIT batch silently loses messages. A separate CTE picks the
        // first matching segment per message; outer FTS retains valid snippets.
        String escaped="\"" + query.trim().replace("\"", "\"\"") + "\"";
        ArrayList<String> args=new ArrayList<>();args.add(escaped);args.add(escaped);
        StringBuilder conditions=new StringBuilder(" WHERE search_parts MATCH ?");
        if(chat!=null&&!chat.isEmpty()){
            conditions.append(" AND m.conversation_id=?");args.add(chat);
        }
        if(speaker!=null&&!speaker.trim().isEmpty()){
            conditions.append(" AND m.role=? COLLATE NOCASE");args.add(speaker.trim());
        }
        if(fromDate!=null&&!fromDate.trim().isEmpty()){
            conditions.append(" AND m.created>=?");args.add(fromDate.trim());
        }
        if(toDate!=null&&!toDate.trim().isEmpty()){
            conditions.append(" AND m.created<?");args.add(toDate.trim()+"~");
        }
        String sql="WITH matched AS (" +
                "SELECT p.message_row AS message_row, MIN(p.row_id) AS first_doc " +
                "FROM search_parts JOIN parts p ON p.row_id=search_parts.docid " +
                "WHERE search_parts MATCH ? GROUP BY p.message_row) " +
                "SELECT m.conversation_id,c.title,m.message_id,m.role,m.created,m.ordinal, " +
                "snippet(search_parts, '[', ']', '…', 0, 16) " +
                "FROM search_parts JOIN matched h ON h.first_doc=search_parts.docid " +
                "JOIN messages m ON m.row_id=h.message_row " +
                "JOIN conversations c ON c.id=m.conversation_id " +
                "JOIN sources s ON s.id=c.archive_id AND s.status='ready' " +
                conditions.toString()+
                " ORDER BY COALESCE(m.created,c.updated) DESC,m.row_id ASC " +
                "LIMIT "+Math.max(1,Math.min(limit,100))+" OFFSET "+Math.max(0,offset);
        try(Cursor c=getReadableDatabase().rawQuery(sql,args.toArray(new String[0]))){
            while(c.moveToNext()){
                Hit hit=new Hit();hit.chatId=c.getString(0);hit.chatTitle=c.getString(1);
                hit.messageId=c.getString(2);hit.role=c.getString(3);hit.created=c.getString(4);
                hit.messageOrdinal=c.getInt(5);hit.excerpt=c.getString(6);result.add(hit);
            }
        }catch(SQLiteException invalidQuery){
            throw new IllegalArgumentException("This search could not be parsed. Try a shorter phrase.",invalidQuery);
        }
        return result;
    }
    synchronized int findMessageOrdinal(String chat,String messageId){
        if(messageId==null)return 0;
        try(Cursor c=getReadableDatabase().rawQuery("SELECT ordinal FROM messages WHERE conversation_id=? AND message_id=? LIMIT 1",new String[]{chat,messageId})){
            return c.moveToFirst()?c.getInt(0):0;
        }
    }
    synchronized Message messageAt(String chat,int ordinal){
        List<Message> rows=messages(chat,ordinal,1);
        return rows.isEmpty()?null:rows.get(0);
    }
    synchronized String sourceUri(String archiveId){
        try(Cursor c=getReadableDatabase().rawQuery("SELECT uri FROM sources WHERE id=? AND status='ready'",new String[]{archiveId})){
            return c.moveToFirst()?c.getString(0):null;
        }
    }
}