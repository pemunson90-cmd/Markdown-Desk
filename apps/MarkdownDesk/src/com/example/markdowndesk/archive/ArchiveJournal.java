package com.example.markdowndesk.archive;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

/** Append-only, fsynced annotation journal. Does not modify original exports.
 * A truncated final record is reported and ignored; a corrupt committed record fails closed.
 */
public final class ArchiveJournal {
    private static final int MAGIC = 0x414A4E32; // AJN2 (unreleased schema)
    private static final int MAX_RECORD = 8*1024*1024;
    // Bound retained historical strings to approximately 4 MiB of UTF-16 storage.
    private static final long MAX_HISTORY_UNITS = 2L*1024*1024;
    // Multiple Activity instances append to the same journal. Per-instance synchronized
    // methods do not serialize read/append or crash-tail checks across instances.
    private static final Object PROCESS_LOCK = new Object();
    private final File file;
    public ArchiveJournal(File file) { this.file=Objects.requireNonNull(file); }
    public static final class Annotation {
        public String id, archiveId, chatId, chatTitle, messageId, messageDate, type,
                quote, note, label, target, tags, prefix, suffix;
        public int selectionStart=-1, selectionEnd=-1;
        public long createdAt, updatedAt;
        public boolean isLink() {
            return "source_link".equals(type)||"cross_link".equals(type)||"external_link".equals(type);
        }
        public Annotation copy() {
            Annotation x=new Annotation(); x.id=id; x.archiveId=archiveId; x.chatId=chatId;
            x.chatTitle=chatTitle; x.messageId=messageId; x.messageDate=messageDate;
            x.type=type; x.quote=quote; x.note=note; x.label=label;
            x.target=target; x.tags=tags; x.prefix=prefix; x.suffix=suffix;
            x.selectionStart=selectionStart; x.selectionEnd=selectionEnd;
            x.createdAt=createdAt; x.updatedAt=updatedAt;
            return x;
        }
    }
    public static final class ReadResult {
        public final ArrayList<Annotation> annotations = new ArrayList<>();
        public boolean truncatedTail;
    }
    public static final class Revision {
        public final Annotation annotation;
        public final boolean deleted;
        private Revision(Annotation state,boolean isDeleted) {
            annotation=state.copy();deleted=isDeleted;
        }
    }
    public static final class HistoryResult {
        public final ArrayList<Revision> revisions=new ArrayList<>();
    }
    /** Immutable, source-bound revision history. A suspicious ID reuse or an
     * interrupted record never becomes another document's history.
     */
    public HistoryResult history(Annotation expected)throws IOException {
        if(expected==null||expected.id==null||expected.id.isEmpty()
                ||expected.archiveId==null||expected.chatId==null||expected.messageId==null)
            throw new IOException("Missing annotation history identity");
        synchronized(PROCESS_LOCK){
            HistoryResult history=new HistoryResult();
            ReadResult state=loadLocked(expected,history);
            if(state.truncatedTail)
                throw new IOException("Annotation journal has an interrupted record; history is incomplete");
            return history;
        }
    }

    public void save(Annotation a) throws IOException {
        synchronized (PROCESS_LOCK) { saveLocked(a); }
    }
    private void saveLocked(Annotation a) throws IOException {
        if(a==null || a.id==null || a.id.isEmpty() || a.chatId==null || a.chatId.isEmpty())
            throw new IOException("Incomplete annotation identity");
        if(!validType(a.type))throw new IOException("Unknown annotation type");
        writeRecord((byte)1,a);
    }
    public void delete(String id) throws IOException {
        synchronized (PROCESS_LOCK) { deleteLocked(id); }
    }
    private void deleteLocked(String id) throws IOException {
        if(id==null||id.isEmpty())throw new IOException("Empty annotation id");
        Annotation tombstone=new Annotation();tombstone.id=id;
        writeRecord((byte)2,tombstone);
    }
    /** An editor opened an immutable snapshot. Refuse to resurrect a deleted
     * note or overwrite another Activity's newer journal record.
     */
    public void saveIfCurrent(Annotation expected, Annotation replacement) throws IOException {
        synchronized(PROCESS_LOCK) {
            requireCurrentLocked(expected);
            if(replacement==null || !Objects.equals(expected.id,replacement.id) ||
                    !Objects.equals(expected.archiveId,replacement.archiveId) ||
                    !Objects.equals(expected.chatId,replacement.chatId) ||
                    !Objects.equals(expected.messageId,replacement.messageId))
                throw new IOException("Annotation edit changes source identity");
            saveLocked(replacement);
        }
    }
    public void deleteIfCurrent(Annotation expected) throws IOException {
        synchronized(PROCESS_LOCK) {
            requireCurrentLocked(expected);
            deleteLocked(expected.id);
        }
    }
    private void requireCurrentLocked(Annotation expected) throws IOException {
        if(expected==null || expected.id==null || expected.id.isEmpty())
            throw new IOException("Missing annotation snapshot");
        ReadResult state=loadLocked();
        if(state.truncatedTail)throw new IOException("Interrupted journal; preserve evidence before editing");
        for(Annotation current:state.annotations) {
            if(Objects.equals(current.id,expected.id)) {
                if(sameRecord(current,expected))return;
                throw new IOException("Annotation changed elsewhere; reopen its latest version");
            }
        }
        throw new IOException("Annotation was deleted or is no longer available");
    }
    private static boolean sameRecord(Annotation a,Annotation b) {
        return Objects.equals(a.id,b.id) &&
                Objects.equals(a.archiveId,b.archiveId) &&
                Objects.equals(a.chatId,b.chatId) &&
                Objects.equals(a.chatTitle,b.chatTitle) &&
                Objects.equals(a.messageId,b.messageId) &&
                Objects.equals(a.messageDate,b.messageDate) &&
                Objects.equals(a.type,b.type) &&
                Objects.equals(a.quote,b.quote) &&
                Objects.equals(a.note,b.note) &&
                Objects.equals(a.label,b.label) &&
                Objects.equals(a.target,b.target) &&
                Objects.equals(a.tags,b.tags) &&
                Objects.equals(a.prefix,b.prefix) &&
                Objects.equals(a.suffix,b.suffix) &&
                a.selectionStart==b.selectionStart &&
                a.selectionEnd==b.selectionEnd &&
                a.createdAt==b.createdAt && a.updatedAt==b.updatedAt;
    }

    public ReadResult load() throws IOException {
        synchronized (PROCESS_LOCK) { return loadLocked(); }
    }
    private ReadResult loadLocked() throws IOException {return loadLocked(null,null);}
    private ReadResult loadLocked(Annotation requested,HistoryResult history) throws IOException {
        ReadResult result=new ReadResult();
        if(!file.exists())return result;
        LinkedHashMap<String,Annotation> live=new LinkedHashMap<>();
        long retainedHistoryUnits=0;
        try(DataInputStream input=new DataInputStream(new BufferedInputStream(new FileInputStream(file)))) {
            for(;;) {
                int first=input.read();
                if(first<0)break;
                int magic;
                try {magic=(first<<24) | (input.readUnsignedByte()<<16)
                        | (input.readUnsignedByte()<<8) | input.readUnsignedByte();}
                catch(EOFException tail){result.truncatedTail=true;break;}
                if(magic!=MAGIC)throw new IOException("Annotation journal header corrupted");
                int count;
                try{count=input.readInt();}catch(EOFException tail){result.truncatedTail=true;break;}
                if(count<1||count>MAX_RECORD)throw new IOException("Invalid journal record length");
                byte[] hash=new byte[32],payload=new byte[count];
                if(!readExact(input,hash)||!readExact(input,payload)){
                    result.truncatedTail=true; break;
                }
                if(!MessageDigest.isEqual(hash,digest(payload)))
                    throw new IOException("Annotation journal integrity mismatch");
                try(DataInputStream record=new DataInputStream(new ByteArrayInputStream(payload))){
                    byte op=record.readByte();
                    Annotation row=decode(record);
                    if(record.available()!=0)throw new IOException("Unexpected journal trailing data");
                    if(op==1) {
                        if(row.id==null || row.chatId==null || !validType(row.type))
                            throw new IOException("Corrupt annotation record");
                        live.put(row.id,row);
                    } else if(op==2) live.remove(row.id);
                    else throw new IOException("Unknown journal operation");
                    if(history!=null && Objects.equals(row.id,requested.id)) {
                        if(op==1 && (!Objects.equals(row.archiveId,requested.archiveId)
                                ||!Objects.equals(row.chatId,requested.chatId)
                                ||!Objects.equals(row.messageId,requested.messageId)))
                            throw new IOException("Annotation history has conflicting source identity");
                        if(history.revisions.size()>=512)
                            throw new IOException("Annotation history exceeds 512 revisions; no partial view shown");
                        long next=historyUnits(row);
                        if(next>MAX_HISTORY_UNITS-retainedHistoryUnits)
                            throw new IOException("Annotation history is too large to display safely; journal preserved");
                        retainedHistoryUnits+=next;
                        history.revisions.add(new Revision(row,op==2));
                    }
                }
            }
        }
        result.annotations.addAll(live.values());return result;
    }
    private static long historyUnits(Annotation row) {
        long count=0;
        for(String part:new String[]{row.id,row.archiveId,row.chatId,row.chatTitle,
                row.messageId,row.messageDate,row.type,row.quote,row.note,
                row.label,row.target,row.tags,row.prefix,row.suffix})
            if(part!=null)count+=part.length();
        return count;
    }
    private static boolean readExact(DataInputStream in,byte[] dest)throws IOException {
        try{in.readFully(dest);return true;}catch(EOFException incomplete){return false;}
    }
    private void writeRecord(byte op, Annotation a) throws IOException {
        // Appending after a partial crash record would put valid entries behind an
        // unreadable tail; fail closed until a verified repair/backup is performed.
        if(file.exists() && load().truncatedTail)
            throw new IOException("Annotation journal has an interrupted last record; repair before saving");
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        try(DataOutputStream out=new DataOutputStream(bytes)){
            out.writeByte(op);field(out,a.id);field(out,a.archiveId);field(out,a.chatId);
            field(out,a.chatTitle);field(out,a.messageId);field(out,a.messageDate);
            field(out,a.type);field(out,a.quote);field(out,a.note);field(out,a.label);
            field(out,a.target);field(out,a.tags);field(out,a.prefix);field(out,a.suffix);
            out.writeInt(a.selectionStart);out.writeInt(a.selectionEnd);
            out.writeLong(a.createdAt);out.writeLong(a.updatedAt);
        }
        byte[] payload=bytes.toByteArray();if(payload.length>MAX_RECORD)throw new IOException("Annotation exceeds 8 MiB");
        File parent=file.getParentFile();
        if(parent!=null&&!parent.exists()&&!parent.mkdirs())throw new IOException("Cannot create annotation folder");
        try(FileOutputStream stream=new FileOutputStream(file,true);
            DataOutputStream out=new DataOutputStream(stream)){
            out.writeInt(MAGIC);out.writeInt(payload.length);out.write(digest(payload));
            out.write(payload);out.flush();stream.getFD().sync();
        }
    }
    private static byte[] digest(byte[] payload)throws IOException {
        try{return MessageDigest.getInstance("SHA-256").digest(payload);}
        catch(NoSuchAlgorithmException unavailable){throw new IOException("SHA256 unavailable",unavailable);}
    }
    private static void field(DataOutputStream out,String value)throws IOException {
        if(value==null){out.writeInt(-1);return;}
        byte[] bytes=value.getBytes(StandardCharsets.UTF_8);
        if(bytes.length>4*1024*1024)throw new IOException("Annotation field exceeds limit");
        out.writeInt(bytes.length);out.write(bytes);
    }
    private static String field(DataInputStream in)throws IOException {
        int length=in.readInt();
        if(length==-1)return null;
        if(length<0||length>4*1024*1024)throw new IOException("Corrupt annotation field length");
        byte[] bytes=new byte[length];in.readFully(bytes);
        return new String(bytes,StandardCharsets.UTF_8);
    }
    private static Annotation decode(DataInputStream in)throws IOException {
        Annotation a=new Annotation();a.id=field(in);a.archiveId=field(in);
        a.chatId=field(in);a.chatTitle=field(in);a.messageId=field(in);
        a.messageDate=field(in);a.type=field(in);a.quote=field(in);
        a.note=field(in);a.label=field(in);a.target=field(in);
        a.tags=field(in);a.prefix=field(in);a.suffix=field(in);
        a.selectionStart=in.readInt();a.selectionEnd=in.readInt();
        a.createdAt=in.readLong();a.updatedAt=in.readLong();return a;
    }
    public static boolean validType(String type) {
        return "note".equals(type)||"highlight".equals(type)||"bookmark".equals(type)
                ||"source_link".equals(type)||"cross_link".equals(type)||"external_link".equals(type);
    }
}
