package com.example.markdowndesk;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SQLiteOpenHelper;
import android.os.OperationCanceledException;
import com.example.markdowndesk.archive.ArchiveStreamParser;
import com.example.markdowndesk.workspace.archive.ArchiveTextCodec;
import com.example.markdowndesk.workspace.archive.ArchiveOverlap;
import com.example.markdowndesk.workspace.archive.ArchiveSearchOffsets;
import com.example.markdowndesk.workspace.archive.ArchiveSearchSpec;
import com.example.markdowndesk.workspace.archive.ArchiveUnicode61Phrase;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.BooleanSupplier;

/** Read-only originals. Versioned, atomically published index generations.
 * One completed generation is always available while a replacement imports.
 */
final class ArchiveDatabase extends SQLiteOpenHelper implements ArchiveStreamParser.Sink {
    static final String FILE="archive_catalog_v1.db";
    static final int VERSION=3;
    static final int PAGE=60;
    static final class Conversation {
        String id,archiveId,name,created,updated; int messages;
        final String generationId;
        Conversation(String id,String archiveId,String name,String created,String updated,int messages,String generationId){
            this.id=id;this.archiveId=archiveId;this.name=name;this.created=created;this.updated=updated;
            this.messages=messages;this.generationId=generationId;
        }
    }
    static final class Message {
        long id;String conversationId,messageId,role,created,text;int ordinal,segments;
        Message(long id,String chat,String messageId,String role,String created,String text,int ordinal,int segments){
            this.id=id;conversationId=chat;this.messageId=messageId;this.role=role;
            this.created=created;this.text=text;this.ordinal=ordinal;this.segments=segments;
        }
    }
    static final class Hit {
        String chatId,chatTitle,messageId,role,created,excerpt,generationId;
        int messageOrdinal,partOrdinal,endPartOrdinal,matchStart=-1,matchEnd=-1;
        long messageRow;
    }
    interface Progress {
        void onMessage(int count);
        void onConversation(int count);
    }
    private final String archive;
    private SQLiteDatabase ingest;
    private String stagingArchive,importingChat;
    private long importingMessage;
    private int part,segmentOffset,batchMessages,totalMessages,totalConversations;
    private String overlap="";
    private boolean cancelled;
    private Progress progress;

    ArchiveDatabase(Context context,String archiveId){
        super(context.getApplicationContext(),FILE,null,VERSION);
        archive=archiveId;
        setWriteAheadLoggingEnabled(true);
    }
    void setProgress(Progress listener){progress=listener;}
    @Override public void onCreate(SQLiteDatabase db){
        db.execSQL("PRAGMA auto_vacuum=INCREMENTAL");
        db.execSQL("CREATE TABLE sources (id TEXT PRIMARY KEY,uri TEXT NOT NULL,name TEXT,indexed_at INTEGER,status TEXT,search_format INTEGER NOT NULL DEFAULT 2)");
        db.execSQL("CREATE TABLE active_versions (archive_id TEXT PRIMARY KEY,generation_id TEXT NOT NULL)");
        db.execSQL("CREATE TABLE conversations (id TEXT PRIMARY KEY,archive_id TEXT NOT NULL,title TEXT,created TEXT,updated TEXT,message_count INTEGER NOT NULL DEFAULT 0)");
        db.execSQL("CREATE INDEX conversation_dates ON conversations(updated)");
        db.execSQL("CREATE INDEX conversation_source ON conversations(archive_id)");
        db.execSQL("CREATE TABLE messages (row_id INTEGER PRIMARY KEY AUTOINCREMENT,conversation_id TEXT NOT NULL,ordinal INTEGER NOT NULL,message_id TEXT,role TEXT,created TEXT,segments INTEGER NOT NULL DEFAULT 0)");
        db.execSQL("CREATE INDEX message_position ON messages(conversation_id,ordinal)");
        db.execSQL("CREATE INDEX message_identity ON messages(conversation_id,message_id)");
        // New imports store body in FTS4 only. Existing v1 rows keep parts.content.
        db.execSQL("CREATE TABLE parts (row_id INTEGER PRIMARY KEY AUTOINCREMENT,message_row INTEGER NOT NULL,ordinal INTEGER NOT NULL,start_offset INTEGER NOT NULL,content TEXT NOT NULL,overlap_len INTEGER NOT NULL DEFAULT 0)");
        db.execSQL("CREATE INDEX parts_by_message ON parts(message_row,ordinal)");
        db.execSQL("CREATE VIRTUAL TABLE search_parts USING fts4(body,tokenize=unicode61)");
    }
    @Override public void onUpgrade(SQLiteDatabase db,int oldVersion,int newVersion){
        if(oldVersion<1||oldVersion>2||newVersion!=VERSION)
            throw new IllegalStateException("Unsupported archive index migration");
        if(oldVersion==1){
            db.execSQL("ALTER TABLE parts ADD COLUMN overlap_len INTEGER NOT NULL DEFAULT 0");
            db.execSQL("CREATE INDEX IF NOT EXISTS conversation_source ON conversations(archive_id)");
            db.execSQL("CREATE TABLE active_versions (archive_id TEXT PRIMARY KEY,generation_id TEXT NOT NULL)");
            // Only ready v1 generations become visible. Incomplete import rows never do.
            db.execSQL("INSERT INTO active_versions(archive_id,generation_id) " +
                    "SELECT id,id FROM sources WHERE status='ready' AND instr(id,'#staging#')=0");
        }
        // Retain compatibility with the existing code40 beta. Search itself never rewrites index rows.
        db.execSQL("ALTER TABLE sources ADD COLUMN search_format INTEGER NOT NULL DEFAULT 2");
    }
    synchronized void beginImport(String uri,String name){
        if(ingest!=null)throw new IllegalStateException("Import already active");
        ingest=getWritableDatabase();cancelled=false;batchMessages=0;totalMessages=0;totalConversations=0;
        stagingArchive=archive+"#staging#"+java.util.UUID.randomUUID();
        ingest.beginTransaction();
        ContentValues source=new ContentValues();source.put("id",stagingArchive);
        source.put("uri",uri);source.put("name",name);source.put("status","importing");
        ingest.insertOrThrow("sources",null,source);
    }
    synchronized void completeImport(){
        if(ingest==null)throw new IllegalStateException("No active archive import");
        if(cancelled)throw new IllegalStateException("Import cancelled");
        ingest.setTransactionSuccessful();ingest.endTransaction();
        // One small publication transaction. Do NOT delete/rewrite gigabytes
        // of old generations in a rollback transaction.
        ingest.beginTransaction();
        try{
            ContentValues src=new ContentValues();src.put("status","ready");
            src.put("indexed_at",System.currentTimeMillis());
            if(ingest.update("sources",src,"id=?",new String[]{stagingArchive})!=1)
                throw new IllegalStateException("Import generation missing");
            ContentValues pointer=new ContentValues();pointer.put("archive_id",archive);
            pointer.put("generation_id",stagingArchive);
            ingest.insertWithOnConflict("active_versions",null,pointer,SQLiteDatabase.CONFLICT_REPLACE);
            ingest.setTransactionSuccessful();
        }finally{
            ingest.endTransaction();ingest=null;stagingArchive=null;
        }
    }
    synchronized void abortImport(){
        cancelled=true;
        if(ingest!=null){
            try{if(ingest.inTransaction())ingest.endTransaction();}finally{ingest=null;}
        }
        // Completed batches of this unready generation are cleaned separately.
    }
    @Override public void beginConversation(int ordinal)throws IOException{
        checkActive();importingChat=stagingArchive+"#pending#"+ordinal;
        ContentValues v=new ContentValues();v.put("id",importingChat);v.put("archive_id",stagingArchive);
        v.put("title","Importing conversation "+(ordinal+1));
        ingest.insertOrThrow("conversations",null,v);
    }
    @Override public void beginMessage(int ordinal)throws IOException{
        checkActive();part=0;segmentOffset=0;overlap="";
        ContentValues v=new ContentValues();v.put("conversation_id",importingChat);v.put("ordinal",ordinal);
        importingMessage=ingest.insertOrThrow("messages",null,v);
    }
    @Override public void textSegment(String segment)throws IOException{
        checkActive();if(segment==null||segment.isEmpty())return;
        ContentValues p=new ContentValues();p.put("message_row",importingMessage);p.put("ordinal",part++);
        p.put("start_offset",segmentOffset);p.put("content","");p.put("overlap_len",overlap.length());
        long docid=ingest.insertOrThrow("parts",null,p);
        ContentValues fts=new ContentValues();fts.put("docid",docid);fts.put("body",overlap+segment);
        ingest.insertOrThrow("search_parts",null,fts);
        String combined=overlap+segment;
        overlap=ArchiveOverlap.tail("",combined,128);
        segmentOffset+=segment.length();
    }
    @Override public void endMessage(ArchiveStreamParser.MessageInfo info)throws IOException{
        checkActive();ContentValues v=new ContentValues();v.put("message_id",info.id);
        v.put("role",info.sender);v.put("created",info.createdAt);v.put("segments",part);
        ingest.update("messages",v,"row_id=?",new String[]{Long.toString(importingMessage)});
        totalMessages++;if(progress!=null && (totalMessages&15)==0)progress.onMessage(totalMessages);
        if(++batchMessages>=160){
            ingest.setTransactionSuccessful();ingest.endTransaction();ingest.beginTransaction();batchMessages=0;
        }
    }
    @Override public void endConversation(ArchiveStreamParser.ConversationInfo info)throws IOException{
        checkActive();String canonical=stagingArchive+"/"+info.id;
        if(!canonical.equals(importingChat)){
            try(Cursor c=ingest.rawQuery("SELECT 1 FROM conversations WHERE id=?",new String[]{canonical})){
                if(c.moveToFirst())throw new IOException("Duplicate conversation identity: "+info.id);
            }
            ContentValues m=new ContentValues();m.put("conversation_id",canonical);
            ingest.update("messages",m,"conversation_id=?",new String[]{importingChat});
            ContentValues v=new ContentValues();v.put("id",canonical);v.put("title",info.title);
            v.put("created",info.createdAt);v.put("updated",info.updatedAt);
            v.put("message_count",info.messageCount);
            ingest.update("conversations",v,"id=?",new String[]{importingChat});
        }else{
            ContentValues v=new ContentValues();v.put("title",info.title);
            v.put("created",info.createdAt);v.put("updated",info.updatedAt);
            v.put("message_count",info.messageCount);
            ingest.update("conversations",v,"id=?",new String[]{canonical});
        }
        totalConversations++;if(progress!=null)progress.onConversation(totalConversations);
    }
    private void checkActive()throws IOException{
        if(ingest==null||cancelled||Thread.currentThread().isInterrupted())
            throw new IOException("Archive indexing interrupted");
    }
    private String currentGeneration(SQLiteDatabase db,String archiveId){
        try(Cursor c=db.rawQuery("SELECT generation_id FROM active_versions WHERE archive_id=?",new String[]{archiveId})){
            return c.moveToFirst()?c.getString(0):null;
        }
    }
    private static String stableChat(String internal,String gen,String archiveId){
        if(internal==null||gen==null||archiveId==null)return internal;
        return internal.startsWith(gen+"/")?archiveId+internal.substring(gen.length()):internal;
    }
    private String internalChat(SQLiteDatabase db,String publicChat){
        if(publicChat==null)return "";
        int slash=publicChat.indexOf('/');
        if(slash<0)return publicChat;
        String original=publicChat.substring(0,slash);
        String gen=currentGeneration(db,original);
        return gen==null?"#unavailable#":gen+publicChat.substring(slash);
    }
    synchronized List<Conversation> conversations(String titleFilter,int limit,int offset){
        return conversations(titleFilter,null,limit,offset);
    }
    synchronized List<Conversation> importedConversations(String sourceId,int limit,int offset){
        if(sourceId==null||sourceId.isEmpty())throw new IllegalArgumentException("Missing source identity");
        return conversations(null,sourceId,limit,offset);
    }
    private List<Conversation> conversations(String titleFilter,String sourceId,int limit,int offset){
        ArrayList<Conversation> result=new ArrayList<>();
        ArrayList<String> args=new ArrayList<>();
        String clause="";
        if(titleFilter!=null&&!titleFilter.isEmpty()){
            args.add("%"+titleFilter.replace("\\","\\\\").replace("%","\\%").replace("_","\\_")+"%");
            clause=" AND c.title LIKE ? ESCAPE '\\'";
        }
        if(sourceId!=null){
            args.add(sourceId);
            clause+=" AND av.archive_id=?";
        }
        try(Cursor c=getReadableDatabase().rawQuery(
                "SELECT c.id,av.archive_id,c.archive_id,c.title,c.created,c.updated,c.message_count " +
                "FROM conversations c JOIN active_versions av ON av.generation_id=c.archive_id " +
                "JOIN sources s ON s.id=av.generation_id WHERE s.status='ready' " +
                "AND substr(c.id,1,length(c.archive_id)+1)=c.archive_id||'/'"+clause+
                " ORDER BY COALESCE(c.updated,c.created) DESC,c.id LIMIT " +
                Math.max(1,Math.min(limit,200))+" OFFSET "+Math.max(0,offset),args.toArray(new String[0]))){
            while(c.moveToNext())result.add(new Conversation(
                    stableChat(c.getString(0),c.getString(2),c.getString(1)),c.getString(1),
                    c.getString(3),c.getString(4),c.getString(5),c.getInt(6),c.getString(2)));
        }
        return result;
    }
    synchronized List<Message> messages(String chat,int offset,int limit){
        ArrayList<Message> result=new ArrayList<>();SQLiteDatabase db=getReadableDatabase();
        String internal=internalChat(db,chat);
        try(Cursor c=db.rawQuery("SELECT row_id,conversation_id,message_id,role,created,ordinal,segments " +
                "FROM messages WHERE conversation_id=? ORDER BY ordinal LIMIT "+Math.max(1,Math.min(limit,150))+
                " OFFSET "+Math.max(0,offset),new String[]{internal})){
            while(c.moveToNext())result.add(new Message(c.getLong(0),chat,c.getString(2),c.getString(3),
                    c.getString(4),null,c.getInt(5),c.getInt(6)));
        }
        return result;
    }
    synchronized String text(long messageRow,int maxChars){
        StringBuilder out=new StringBuilder();
        try(Cursor c=getReadableDatabase().rawQuery("SELECT p.content,p.overlap_len,f.body " +
                "FROM parts p JOIN search_parts f ON f.docid=p.row_id " +
                "WHERE p.message_row=? ORDER BY p.ordinal",
                new String[]{Long.toString(messageRow)})){
            while(c.moveToNext()){
                String value=ArchiveTextCodec.reconstruct(c.getString(0),c.getInt(1),c.getString(2));
                if(out.length()+value.length()>maxChars)break;
                out.append(value);
            }
        }
        return out.toString();
    }
    synchronized String textChunk(long messageRow,int startPart,int take){
        StringBuilder out=new StringBuilder();
        try(Cursor c=getReadableDatabase().rawQuery("SELECT p.content,p.overlap_len,f.body " +
                "FROM parts p JOIN search_parts f ON f.docid=p.row_id " +
                "WHERE p.message_row=? AND p.ordinal>=? ORDER BY p.ordinal LIMIT " +
                Math.max(1,Math.min(take,64)),
                new String[]{Long.toString(messageRow),Integer.toString(startPart)})){
            while(c.moveToNext())out.append(
                ArchiveTextCodec.reconstruct(c.getString(0),c.getInt(1),c.getString(2)));
        }
        return out.toString();
    }
    /** Pin the published generation before exporting; never mix old and new
     * versions of a conversation if a reimport swaps active_versions mid-read.
     */
    synchronized String exportGeneration(Conversation selected)throws IOException {
        if(selected==null||selected.archiveId==null||selected.archiveId.isEmpty())
            throw new IOException("Missing archived conversation identity");
        // The conversation list is a generation-bound snapshot. If a new import
        // published since it was selected, never silently export newer content
        // under the previous conversation's title/metadata.
        String generation=currentGeneration(getReadableDatabase(),selected.archiveId);
        ArchiveTranscriptIntegrity.generation(selected.generationId,generation);
        return generation;
    }
    synchronized void requireExportGeneration(String archiveId,String expected)throws IOException {
        ArchiveTranscriptIntegrity.generation(expected,currentGeneration(getReadableDatabase(),archiveId));
    }
    /** Unlike preview textChunk(), export must prove every requested segment
     * exists and appears in order. An empty valid segment remains supported.
     */
    synchronized String textChunkVerified(long messageRow,int startPart,int take)throws IOException {
        if(messageRow<1||startPart<0||take<1||take>64)
            throw new IOException("Invalid transcript segment range");
        StringBuilder out=new StringBuilder();int found=0;
        try(Cursor c=getReadableDatabase().rawQuery(
                "SELECT p.ordinal,p.content,p.overlap_len,f.body " +
                "FROM parts p JOIN search_parts f ON f.docid=p.row_id " +
                "WHERE p.message_row=? AND p.ordinal>=? ORDER BY p.ordinal LIMIT " +take,
                new String[]{Long.toString(messageRow),Integer.toString(startPart)})){
            while(c.moveToNext()){
                ArchiveTranscriptIntegrity.part(startPart+found,c.getInt(0));
                out.append(ArchiveTextCodec.reconstruct(c.getString(1),c.getInt(2),c.getString(3)));
                found++;
            }
        }
        ArchiveTranscriptIntegrity.complete(take,found);
        return out.toString();
    }
    synchronized List<Hit> search(String query,String chat,int limit,int offset){
        return search(query,chat,null,null,null,limit,offset,()->false);
    }
    synchronized List<Hit> search(String query,String chat,String speaker,String fromDate,String toDate,int limit,int offset){
        return search(query,chat,speaker,fromDate,toDate,limit,offset,()->false);
    }
    synchronized List<Hit> search(String query,String chat,String speaker,String fromDate,String toDate,
            int limit,int offset,BooleanSupplier cancelled){
        ArrayList<Hit> out=new ArrayList<>();
        if(query==null||query.trim().isEmpty())return out;
        String term=query.trim();
        if(term.length()>ArchiveSearchSpec.MAX_PHRASE_UNITS)
            throw new IllegalArgumentException("Search phrase is too long");
        if(term.indexOf('\0')>=0)throw new IllegalArgumentException("Search phrase contains a NUL");
        String phrase="\""+term.replace("\"","\"\"")+"\"";
        ArchiveUnicode61Phrase matcher=ArchiveUnicode61Phrase.compile(term);
        if(matcher.isEmpty())return out;
        SQLiteDatabase db=getReadableDatabase();
        checkSearchCancelled(cancelled);
        db.beginTransactionReadOnly();
        try{
            ArrayList<String> args=new ArrayList<>();args.add(phrase);
            StringBuilder filters=new StringBuilder();
            if(chat!=null&&!chat.isEmpty()){
                filters.append(" AND m.conversation_id=?");args.add(internalChat(db,chat));
            }
            if(speaker!=null&&!speaker.trim().isEmpty()){
                filters.append(" AND m.role=? COLLATE NOCASE");args.add(speaker.trim());
            }
            if(fromDate!=null&&!fromDate.trim().isEmpty()){
                filters.append(" AND m.created>=?");args.add(fromDate.trim());
            }
            if(toDate!=null&&!toDate.trim().isEmpty()){
                filters.append(" AND m.created<?");args.add(toDate.trim()+"~");
            }
            // Order metadata once, then skip non-matches before consuming the page offset.
            // Neither large FTS bodies nor source text go into the sort.
            String candidates="WITH matched AS (SELECT p.message_row,MIN(p.row_id) AS first_doc " +
                    "FROM search_parts JOIN parts p ON p.row_id=search_parts.docid " +
                    "WHERE search_parts MATCH ? GROUP BY p.message_row) " +
                    "SELECT m.conversation_id,c.title,m.message_id,m.role,m.created,m.ordinal,m.segments, " +
                    "av.archive_id,c.archive_id,m.row_id,h.first_doc " +
                    "FROM messages m JOIN conversations c ON c.id=m.conversation_id " +
                    "JOIN active_versions av ON av.generation_id=c.archive_id " +
                    "JOIN sources s ON s.id=av.generation_id AND s.status='ready' " +
                    "LEFT JOIN matched h ON h.message_row=m.row_id " +
                    "WHERE substr(c.id,1,length(c.archive_id)+1)=c.archive_id||'/' " +
                    "AND (h.first_doc IS NOT NULL OR m.segments>1)" + filters +
                    " ORDER BY COALESCE(m.created,c.updated) DESC,m.row_id ASC";
            int skip=Math.max(0,offset),pageSize=Math.max(1,Math.min(limit,100));
            try(Cursor c=db.rawQuery(candidates,args.toArray(new String[0]))){
                while(out.size()<pageSize&&c.moveToNext()){
                    checkSearchCancelled(cancelled);
                    boolean nativeMatch=!c.isNull(10);
                    Hit h=new Hit();h.chatId=stableChat(c.getString(0),c.getString(8),c.getString(7));
                    h.chatTitle=c.getString(1);h.messageId=c.getString(2);h.role=c.getString(3);
                    h.created=c.getString(4);h.messageOrdinal=c.getInt(5);h.generationId=c.getString(8);
                    h.messageRow=c.getLong(9);
                    if(nativeMatch){
                        if(skip>0){skip--;continue;}
                        nativeSearchHit(db,h,c.getLong(10),phrase,matcher.firstClauseLength());
                    }else{
                        ArchiveUnicode61Phrase.Span span=boundaryMatch(db,h.messageRow,c.getInt(6),
                                matcher.newMatcher(),cancelled);
                        if(span==null)continue;
                        if(skip>0){skip--;continue;}
                        setSearchSpan(db,h,span.start,span.end);
                        h.excerpt=readSearchSlice(db,h.messageRow,span.start,span.start+Math.min(160,span.end-span.start))
                                +(span.end-span.start>160?"…":"");
                    }
                    out.add(h);
                }
            }
            checkSearchCancelled(cancelled);
            db.setTransactionSuccessful();
        }catch(IOException bad){
            throw new IllegalStateException("Indexed source could not be read",bad);
        }catch(SQLiteException bad){
            throw new IllegalArgumentException("Indexed search could not be completed",bad);
        }finally{
            db.endTransaction();
        }
        return out;
    }
    private static void checkSearchCancelled(BooleanSupplier cancelled){
        if(cancelled.getAsBoolean()||Thread.currentThread().isInterrupted())
            throw new OperationCanceledException();
    }
    private static ArchiveUnicode61Phrase.Span boundaryMatch(SQLiteDatabase db,long row,int segments,
            ArchiveUnicode61Phrase.Matcher matcher,BooleanSupplier cancelled)throws IOException{
        try(Cursor c=db.rawQuery("SELECT p.ordinal,p.start_offset,p.content,p.overlap_len,f.body " +
                "FROM parts p JOIN search_parts f ON f.docid=p.row_id WHERE p.message_row=? ORDER BY p.ordinal",
                new String[]{Long.toString(row)})){
            int ordinal=0,offset=0;
            while(c.moveToNext()){
                checkSearchCancelled(cancelled);
                if(c.getInt(0)!=ordinal++||c.getInt(1)!=offset)
                    throw new IOException("Indexed source parts are out of order");
                String text=ArchiveTextCodec.reconstruct(c.getString(2),c.getInt(3),c.getString(4));
                offset=Math.addExact(offset,text.length());
                ArchiveUnicode61Phrase.Span found=matcher.accept(text);
                if(found!=null)return found;
            }
            ArchiveTranscriptIntegrity.complete(segments,ordinal);
        }
        return matcher.finish();
    }
    private static void nativeSearchHit(SQLiteDatabase db,Hit hit,long doc,String phrase,int terms)throws IOException{
        try(Cursor c=db.rawQuery("SELECT p.ordinal,p.start_offset,p.content,p.overlap_len,search_parts.body," +
                "offsets(search_parts),snippet(search_parts,'[',']','…',0,16) " +
                "FROM search_parts JOIN parts p ON p.row_id=search_parts.docid " +
                "WHERE search_parts MATCH ? AND search_parts.docid=?",
                new String[]{phrase,Long.toString(doc)})){
            if(!c.moveToFirst())throw new IOException("Indexed hit changed during search");
            hit.partOrdinal=c.getInt(0);hit.endPartOrdinal=hit.partOrdinal;hit.excerpt=c.getString(6);
            String body=c.getString(4);
            String original=ArchiveTextCodec.reconstruct(c.getString(2),c.getInt(3),body);
            if(!body.endsWith(original))return;
            int origin=c.getInt(1)-(body.length()-original.length());
            ArchiveSearchOffsets.Span span=ArchiveSearchOffsets.first(body,c.getString(5),terms);
            if(span!=null&&origin>=0)
                setSearchSpan(db,hit,Math.addExact(origin,span.start),Math.addExact(origin,span.end));
        }
    }
    private static void setSearchSpan(SQLiteDatabase db,Hit hit,int start,int end)throws IOException{
        hit.matchStart=start;hit.matchEnd=end;
        hit.partOrdinal=searchPartAt(db,hit.messageRow,start);
        hit.endPartOrdinal=searchPartAt(db,hit.messageRow,end-1);
    }
    private static int searchPartAt(SQLiteDatabase db,long row,int position)throws IOException{
        try(Cursor c=db.rawQuery("SELECT ordinal FROM parts WHERE message_row=? AND start_offset<=? " +
                "ORDER BY ordinal DESC LIMIT 1",
                new String[]{Long.toString(row),Integer.toString(position)})){
            if(c.moveToFirst())return c.getInt(0);
        }
        throw new IOException("Indexed passage part is missing");
    }
    private static String readSearchSlice(SQLiteDatabase db,long row,int start,int end)throws IOException{
        int ordinal=searchPartAt(db,row,start),position=start;
        StringBuilder text=new StringBuilder(end-start);
        try(Cursor c=db.rawQuery("SELECT p.ordinal,p.start_offset,p.content,p.overlap_len,f.body " +
                "FROM parts p JOIN search_parts f ON f.docid=p.row_id WHERE p.message_row=? AND p.ordinal>=? " +
                "ORDER BY p.ordinal",new String[]{Long.toString(row),Integer.toString(ordinal)})){
            while(position<end&&c.moveToNext()){
                int base=c.getInt(1);
                String part=ArchiveTextCodec.reconstruct(c.getString(2),c.getInt(3),c.getString(4));
                if(c.getInt(0)!=ordinal++||position<base||position-base>=part.length()
                        ||(position!=start&&position!=base))
                    throw new IOException("Indexed passage has a missing source part");
                int take=Math.min(end-position,part.length()-(position-base));
                text.append(part,position-base,position-base+take);
                position+=take;
            }
        }
        if(position!=end)throw new IOException("Indexed passage is truncated");
        return text.toString();
    }
    synchronized String searchPassage(Hit hit,int start,int end)throws IOException{
        if(hit==null||hit.chatId==null||hit.generationId==null||hit.messageRow<=0
                ||start<hit.matchStart||start<0||end>hit.matchEnd||end<=start||end-start>ArchiveSearchSpec.MAX_PHRASE_UNITS)
            throw new IOException("Invalid indexed passage");
        SQLiteDatabase db=getReadableDatabase();
        db.beginTransactionReadOnly();
        try{
            String chat=internalChat(db,hit.chatId);
            try(Cursor c=db.rawQuery("SELECT c.archive_id,m.message_id,m.ordinal FROM messages m " +
                    "JOIN conversations c ON c.id=m.conversation_id " +
                    "JOIN active_versions av ON av.generation_id=c.archive_id " +
                    "JOIN sources s ON s.id=av.generation_id AND s.status='ready' " +
                    "WHERE m.row_id=? AND c.id=?",new String[]{Long.toString(hit.messageRow),chat})){
                if(!c.moveToFirst()||!hit.generationId.equals(c.getString(0))
                        ||!Objects.equals(hit.messageId,c.getString(1))||hit.messageOrdinal!=c.getInt(2))
                    throw new IOException("Indexed result changed. Run the search again.");
            }
            if(hit.partOrdinal!=searchPartAt(db,hit.messageRow,hit.matchStart)
                    ||hit.endPartOrdinal!=searchPartAt(db,hit.messageRow,hit.matchEnd-1))
                throw new IOException("Indexed passage parts changed. Run the search again.");
            String passage=readSearchSlice(db,hit.messageRow,start,end);
            db.setTransactionSuccessful();
            return passage;
        }finally{
            db.endTransaction();
        }
    }
    synchronized int findMessageOrdinal(String chat,String messageId){
        if(messageId==null||messageId.isEmpty())return -1;
        String internal=internalChat(getReadableDatabase(),chat);
        try(Cursor c=getReadableDatabase().rawQuery("SELECT ordinal FROM messages WHERE conversation_id=? " +
                "AND message_id=? LIMIT 1",new String[]{internal,messageId})){
            return c.moveToFirst()?c.getInt(0):-1;
        }
    }
    synchronized Message messageAt(String chat,int ordinal){
        List<Message> rows=messages(chat,ordinal,1);return rows.isEmpty()?null:rows.get(0);
    }
    synchronized String sourceUri(String archiveId){
        String gen=currentGeneration(getReadableDatabase(),archiveId);if(gen==null)return null;
        try(Cursor c=getReadableDatabase().rawQuery("SELECT uri FROM sources WHERE id=? AND status='ready'",new String[]{gen})){
            return c.moveToFirst()?c.getString(0):null;
        }
    }

    synchronized long reusableBytes(){
        try {
            long free=0,page=4096;
            try(Cursor c=getReadableDatabase().rawQuery("PRAGMA freelist_count",null)){
                if(c.moveToFirst())free=c.getLong(0);
            }
            try(Cursor c=getReadableDatabase().rawQuery("PRAGMA page_size",null)){
                if(c.moveToFirst())page=c.getLong(0);
            }
            return free>0 && page>0 && free<Long.MAX_VALUE/page ? free*page : 0;
        }catch(Exception unavailable){return 0;}
    }

    /** Garbage collect only generations not selected in active_versions, using
     * bounded transactions. Previous completed generations remain readable until
     * the new publication pointer has committed. Nothing touches user JSON.
     */
    void removeUnpublishedOrObsolete(Progress observer)throws IOException{
        SQLiteDatabase db=getWritableDatabase();ArrayList<String> retired=new ArrayList<>();
        try(Cursor c=db.rawQuery("SELECT s.id FROM sources s LEFT JOIN active_versions av " +
                "ON av.generation_id=s.id WHERE av.generation_id IS NULL",null)){
            while(c.moveToNext())retired.add(c.getString(0));
        }
        for(String gen:retired){
            if(Thread.currentThread().isInterrupted())throw new IOException("Cleanup interrupted");
            ArrayList<String> chats=new ArrayList<>();
            // Prefixes MUST be delimited: `archive%` would also match the
            // replacement `archive#staging#...` and erase the NEW ready index.
            try(Cursor c=db.rawQuery("SELECT id FROM conversations WHERE archive_id=? " +
                    "OR id LIKE ? OR id LIKE ?",
                    new String[]{gen,gen+"/%",gen+"#pending#%"})){
                while(c.moveToNext())chats.add(c.getString(0));
            }
            for(String chat:chats){
                while(true){
                    ArrayList<Long> parts=new ArrayList<>();
                    try(Cursor c=db.rawQuery("SELECT p.row_id FROM parts p JOIN messages m ON " +
                            "m.row_id=p.message_row WHERE m.conversation_id=? LIMIT 200",new String[]{chat})){
                        while(c.moveToNext())parts.add(c.getLong(0));
                    }
                    if(parts.isEmpty())break;
                    db.beginTransaction();try{
                        for(Long id:parts){String[] args={Long.toString(id)};
                            db.delete("search_parts","docid=?",args);db.delete("parts","row_id=?",args);
                        }
                        db.setTransactionSuccessful();
                    }finally{db.endTransaction();}
                    if(Thread.currentThread().isInterrupted())throw new IOException("Cleanup interrupted");
                }
                db.beginTransaction();try{
                    db.delete("messages","conversation_id=?",new String[]{chat});
                    db.delete("conversations","id=?",new String[]{chat});
                    db.setTransactionSuccessful();
                }finally{db.endTransaction();}
            }
            db.beginTransaction();try{
                db.delete("sources","id=?",new String[]{gen});db.setTransactionSuccessful();
            }finally{db.endTransaction();}
        }
        // WAL checkpoints can reduce temporary files after bounded cleanup.
        try{db.execSQL("PRAGMA wal_checkpoint(PASSIVE)");}catch(SQLiteException ignored){}
    }
}
