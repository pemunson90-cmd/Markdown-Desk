package com.example.markdowndesk;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import com.example.markdowndesk.PrivateArchive.*;
import static com.example.markdowndesk.PrivateArchive.*;

/** Real codec behavior; only streams are fault-injected. All fixtures are synthetic. */
public final class ArchiveCheck {
    private static final byte[] MAGIC = "MDESKBACKUP\r\n\032\n".getBytes(StandardCharsets.US_ASCII);
    private static final int PREFIX = MAGIC.length + 4, RECORD = 58, TRAILER = 45;
    private static int assertions;
    private interface Action { void run() throws Exception; }
    private static void check(boolean value, String detail) {
        assertions++; if (!value) throw new AssertionError(detail);
    }
    private static void rejects(Action action, String detail) throws Exception {
        assertions++;
        try { action.run(); } catch (IOException expected) { return; }
        throw new AssertionError("Accepted " + detail);
    }
    private static UUID id(int number) { return new UUID(0x123456789abc4000L, 0x8000000000000000L + number); }
    private static ArchiveSource source(SourceEntry... entries) {
        return new ArchiveSource() { int index; public SourceEntry next() { return index == entries.length ? null : entries[index++]; } };
    }
    private static SourceEntry entry(UUID id, Type type, byte[] payload) {
        return new SourceEntry(id, type, payload.length, () -> new ByteArrayInputStream(payload));
    }
    private static byte[] encode(SourceEntry... entries) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PrivateArchive.write(source(entries), out); return out.toByteArray();
    }
    private static ArchiveSummary decode(byte[] data) throws Exception {
        return PrivateArchive.read(new ByteArrayInputStream(data), entry -> OutputStream.nullOutputStream());
    }
    private static byte[] digest(byte[] bytes) throws Exception { return MessageDigest.getInstance("SHA-256").digest(bytes); }
    private static String hex(byte[] bytes) {
        StringBuilder out = new StringBuilder(); for (byte b : bytes) out.append(String.format(Locale.ROOT, "%02x", b & 255)); return out.toString();
    }
    /** Build hostile records independently, so reject tests do not rely on writer validation. */
    private static byte[] raw(int version, int type, UUID id, long length, byte[] payload, int count) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream(); DataOutputStream d = new DataOutputStream(out);
        d.write(MAGIC); d.writeInt(version);
        if (type != 0) { d.writeByte(1); d.writeByte(type); d.writeLong(id.getMostSignificantBits()); d.writeLong(id.getLeastSignificantBits()); d.writeLong(length); d.write(digest(payload)); d.write(payload); }
        byte[] prefix = out.toByteArray(); d.writeByte(0); d.writeInt(count); d.writeLong(prefix.length); d.write(digest(prefix)); return out.toByteArray();
    }
    private static void renewTrailer(byte[] bytes) throws Exception {
        int start = bytes.length - TRAILER;
        System.arraycopy(digest(Arrays.copyOf(bytes, start)), 0, bytes, start + 13, 32);
    }
    private static void roundTripAndOwnership() throws Exception {
        byte[] payload = "\ufeffsynthetic\r\n \tΩ🙂\n".getBytes(StandardCharsets.UTF_8);
        List<SourceEntry> entries = new ArrayList<>(); int[] opens = {0}, closes = {0};
        for (Type type : Type.values()) entries.add(new SourceEntry(id(entries.size() + 1), type, payload.length, () -> {
            opens[0]++;
            return new ByteArrayInputStream(payload) { public void close() { closes[0]++; } };
        }));
        ByteArrayOutputStream out = new ByteArrayOutputStream() { public void close() { throw new AssertionError("closed caller output"); } };
        ArchiveSummary written = PrivateArchive.write(source(entries.toArray(new SourceEntry[0])), out);
        byte[] bytes = out.toByteArray(); List<Entry> delivered = new ArrayList<>(); List<byte[]> received = new ArrayList<>();
        InputStream in = new ByteArrayInputStream(bytes) { public void close() { throw new AssertionError("closed caller input"); }
            public int read(byte[] b, int o, int l) { return super.read(b, o, Math.min(3, l)); } };
        ArchiveSummary read = PrivateArchive.read(in, entry -> {
            delivered.add(entry);
            ByteArrayOutputStream sink = new ByteArrayOutputStream() {
                public void close() { throw new AssertionError("closed caller sink"); }
                public void flush() { throw new AssertionError("flushed caller sink"); }
                public synchronized void write(byte[] b, int o, int l) { super.write(b,o,l); received.add(Arrays.copyOfRange(b,o,o+l)); }
            }; return sink;
        });
        check(written.entryCount == Type.values().length && read.entryCount == written.entryCount, "all typed categories roundtrip");
        check(written.byteCount == bytes.length && read.byteCount == bytes.length, "full archive length");
        check(written.sha256.equals(hex(digest(bytes))) && written.sha256.equals(read.sha256), "independent complete stream hash");
        check(opens[0] == entries.size() * 2 && opens[0] == closes[0], "owned streams close on both passes");
        for (int i = 0; i < delivered.size(); i++) {
            Entry e = delivered.get(i); check(e.id.equals(id(i+1)) && e.type == Type.values()[i] && e.length == payload.length && e.sha256.equals(hex(digest(payload))), "typed descriptor fidelity");
        }
        ByteArrayOutputStream combined = new ByteArrayOutputStream(); for (byte[] fragment : received) combined.write(fragment);
        ByteArrayOutputStream expected = new ByteArrayOutputStream(); for (Type ignored : Type.values()) expected.write(payload);
        check(Arrays.equals(combined.toByteArray(), expected.toByteArray()), "exact encoding/payload bytes");
    }
    private static void corruptInputs() throws Exception {
        byte[] empty = encode(); check(decode(empty).entryCount == 0 && empty.length == PREFIX + TRAILER, "valid zero-record terminal");
        rejects(() -> decode(Arrays.copyOf(empty, PREFIX)), "missing trailer");
        byte[] one = encode(entry(id(1), Type.SNAPSHOT, new byte[]{1,2,3,4,5}));
        for (int end = 0; end < one.length; end++) { final int n = end; rejects(() -> decode(Arrays.copyOf(one,n)), "truncated at byte " + n); }
        byte[] bad = one.clone(); bad[0] ^= 1; rejects(() -> decode(bad), "unknown magic");
        rejects(() -> decode(raw(2, 1, id(1), 0, new byte[0], 1)), "unknown version");
        rejects(() -> decode(raw(1, 255, id(1), 0, new byte[0], 1)), "unknown type");
        byte[] marker = one.clone(); marker[PREFIX] = 2; rejects(() -> decode(marker), "unknown frame marker");
        rejects(() -> decode(raw(1, 1, new UUID(0,0), 0, new byte[0], 1)), "nil/non-generated UUID");
        rejects(() -> decode(raw(1, 1, new UUID(0x123456789abc1000L,0x8000000000000001L),0,new byte[0],1)), "non-v4 UUID");
        rejects(() -> decode(raw(1, 1, new UUID(0x123456789abc4000L,1L),0,new byte[0],1)), "bad UUID variant");
        rejects(() -> decode(raw(1, 1, id(1), -1L, new byte[0], 1)), "unsigned64 overflow");
        rejects(() -> decode(raw(1, 1, id(1), Long.MAX_VALUE, new byte[0], 1)), "huge bounded length");
        rejects(() -> decode(raw(1, 1, id(1), MAX_DOCUMENT_ENTRY_BYTES+1L, new byte[0], 1)), "snapshot cap");
        rejects(() -> decode(raw(1, 2, id(1), MAX_METADATA_ENTRY_BYTES+1L, new byte[0], 1)), "metadata cap");
        rejects(() -> decode(raw(1, 1, id(1), 6, new byte[]{1,2,3,4,5},1)), "inconsistent payload length");
        byte[] validTypeChange=one.clone();validTypeChange[PREFIX+1]=7;
        try{decode(validTypeChange);throw new AssertionError("accepted changed frame metadata");}
        catch(IOException expected){check(expected.getMessage().contains("terminal manifest"),"frame metadata participates in terminal digest");}
        renewTrailer(validTypeChange);
        check(decode(validTypeChange).entryCount==1,"same valid frame with renewed digest accepted");
        byte[] payloadHash = one.clone(); payloadHash[PREFIX+26] ^= 1; renewTrailer(payloadHash); rejects(() -> decode(payloadHash), "payload hash with correct manifest");
        byte[] payload = one.clone(); payload[PREFIX+RECORD] ^= 1; renewTrailer(payload); rejects(() -> decode(payload), "changed payload with correct manifest");
        int[] fields = {one.length-TRAILER+1, one.length-TRAILER+5, one.length-1};
        for (int field : fields) { byte[] changed=one.clone(); changed[field]^=1; rejects(() -> decode(changed), "terminal field corruption"); }
        byte[] countOverflow = one.clone(); ByteBuffer.wrap(countOverflow,one.length-TRAILER+1,4).putInt(-1); rejects(() -> decode(countOverflow), "unsigned count overflow");
        byte[] lengthOverflow = one.clone(); ByteBuffer.wrap(lengthOverflow,one.length-TRAILER+5,8).putLong(-1); rejects(() -> decode(lengthOverflow), "unsigned terminal length overflow");
        rejects(() -> decode(Arrays.copyOf(one, one.length+1)), "trailing byte");
        byte[] concatenated=new byte[one.length*2]; System.arraycopy(one,0,concatenated,0,one.length); System.arraycopy(one,0,concatenated,one.length,one.length);
        rejects(() -> decode(concatenated), "concatenated archives");
        byte[] two = encode(entry(id(1),Type.SNAPSHOT,new byte[0]), entry(id(2),Type.SNAPSHOT,new byte[0]));
        System.arraycopy(two,PREFIX+2,two,PREFIX+RECORD+2,16); renewTrailer(two); rejects(() -> decode(two), "duplicate generated IDs with valid manifest");
        int[] callbacks = {0};
        rejects(() -> PrivateArchive.read(new ByteArrayInputStream(Arrays.copyOf(one,one.length-1)), e -> { callbacks[0]++; return OutputStream.nullOutputStream(); }), "provisional callback before bad trailer");
        check(callbacks[0]==1,"staged entry exists but archive cannot be promoted on corrupt terminal");
        byte[] metadataBoundary=new byte[MAX_METADATA_ENTRY_BYTES];
        check(decode(encode(entry(id(4),Type.PREFERENCES,metadataBoundary))).entryCount==1,"metadata boundary accepted");
        for(Type documentType:new Type[]{Type.SESSION_TAB,Type.RECOVERY}) {
            check(decode(encode(entry(id(5),documentType,new byte[MAX_METADATA_ENTRY_BYTES+1]))).entryCount==1,"document-bearing category exceeds metadata cap");
            rejects(()->decode(raw(1,documentType==Type.SESSION_TAB?6:7,id(6),MAX_DOCUMENT_ENTRY_BYTES+1L,new byte[0],1)),"document-bearing category cap");
        }
        byte[] opaque = {(byte)0xff,0,1}; check(decode(encode(entry(id(8),Type.PREFERENCES,opaque))).entryCount==1,"strict payload/graph semantics intentionally not codec scope");
    }
    private static void writeFailures() throws Exception {
        byte[] bytes={1,2,3};
        rejects(() -> encode(entry(id(1),Type.SNAPSHOT,bytes),entry(id(1),Type.SNAPSHOT,bytes)),"writer duplicate");
        rejects(() -> encode(new SourceEntry(id(1),Type.PREFERENCES,-1,()->new ByteArrayInputStream(bytes))),"negative source length");
        rejects(() -> encode(new SourceEntry(id(1),Type.SNAPSHOT,4,()->new ByteArrayInputStream(bytes))),"short source");
        rejects(() -> encode(new SourceEntry(id(1),Type.SNAPSHOT,2,()->new ByteArrayInputStream(bytes))),"long source");
        int[] passes={0}, closes={0};
        SourceEntry changing=new SourceEntry(id(1),Type.SNAPSHOT,3,()-> new ByteArrayInputStream(passes[0]++ == 0 ? bytes : new byte[]{4,5,6}) { public void close(){ closes[0]++; } });
        rejects(() -> encode(changing),"nonrepeatable source"); check(closes[0]==2,"changing source streams closed");
        int[] opened={0};
        rejects(() -> encode(new SourceEntry(id(1),Type.SNAPSHOT,0,()->{ opened[0]++; throw new IOException("synthetic open failure"); })),"source open failure");
        check(opened[0]==1,"source opening fails closed");
        rejects(() -> encode(new SourceEntry(id(1),Type.SNAPSHOT,3,()->new ByteArrayInputStream(bytes){ public void close() throws IOException { throw new IOException("synthetic close failure"); }})),"first source close failure");
        int[] closePass={0};
        rejects(() -> encode(new SourceEntry(id(1),Type.SNAPSHOT,3,()->{ int pass=++closePass[0]; return new ByteArrayInputStream(bytes) { public void close() throws IOException { if(pass==2) throw new IOException("second close"); } }; })),"second source close failure");
        byte[] valid=encode(entry(id(1),Type.SNAPSHOT,bytes));
        for(int limit=0;limit<valid.length;limit++) {
            final int n=limit; ByteArrayOutputStream partial=new ByteArrayOutputStream();
            rejects(() -> PrivateArchive.write(source(entry(id(1),Type.SNAPSHOT,bytes)),new OutputStream(){
                public void write(int b) throws IOException { if(partial.size()==n)throw new IOException("output at "+n);partial.write(b); }
            }),"output failure at "+n);
            rejects(() -> decode(partial.toByteArray()),"failed output cannot verify at "+n);
        }
        int[] sourceClose={0};
        rejects(() -> PrivateArchive.write(source(new SourceEntry(id(1),Type.SNAPSHOT,3,()->new ByteArrayInputStream(bytes){public void close(){sourceClose[0]++;}})),new OutputStream(){int writes;public void write(int b)throws IOException{if(++writes> PREFIX+RECORD)throw new IOException("payload output failure");}}),"source closed on payload output failure");
        check(sourceClose[0]==2,"both source streams closed after output failure");
        rejects(() -> PrivateArchive.write(() -> {throw new IOException("source enumeration");},OutputStream.nullOutputStream()),"source enumeration failure");
        rejects(() -> PrivateArchive.read(new ByteArrayInputStream(valid),e -> {throw new IOException("sink open");}),"sink open failure");
        rejects(() -> PrivateArchive.read(new ByteArrayInputStream(valid),e -> new OutputStream(){ public void write(int b)throws IOException {throw new IOException("sink write");}}),"sink write failure");
        rejects(() -> PrivateArchive.read(new InputStream(){ final ByteArrayInputStream input=new ByteArrayInputStream(valid); public int read() throws IOException { int b=input.read(); if(b<0) throw new IOException("EOF read failure"); return b; }}, e->OutputStream.nullOutputStream()),"final EOF read failure");
    }
    private static void stagingResourceContracts() throws Exception {
        byte[] valid=encode(entry(id(1),Type.SNAPSHOT,new byte[]{3,4,5}));
        int[] finish={0}, close={0}; boolean[] verified={false};
        EntrySink staging=new EntrySink(){
            public OutputStream begin(Entry e){return new ByteArrayOutputStream(){public void close(){close[0]++;}};}
            public void finish(Entry e,OutputStream out,boolean payloadVerified)throws IOException{
                finish[0]++;verified[0]=payloadVerified;out.close();
            }
        };
        check(PrivateArchive.read(new ByteArrayInputStream(valid),staging).entryCount==1,"caller closes each stage via finish");
        check(finish[0]==1 && close[0]==1 && verified[0],"verified entry finish once");
        byte[] damaged=valid.clone();damaged[PREFIX+RECORD]^=1;
        rejects(()->PrivateArchive.read(new ByteArrayInputStream(damaged),staging),"hash failure still finishes staging");
        check(finish[0]==2 && close[0]==2 && !verified[0],"failed entry finish once with false");
        EntrySink failingFinish=new EntrySink(){
            public OutputStream begin(Entry e){return OutputStream.nullOutputStream();}
            public void finish(Entry e,OutputStream out,boolean payloadVerified)throws IOException{throw new IOException("synthetic stage close failure");}
        };
        rejects(()->PrivateArchive.read(new ByteArrayInputStream(valid),failingFinish),"stage finish failure cannot return summary");
        try{PrivateArchive.read(new ByteArrayInputStream(damaged),failingFinish);throw new AssertionError("missing failure");}
        catch(IOException expected){check(expected.getMessage().contains("hash mismatch") && expected.getSuppressed().length==1,"preserve hash error plus finish failure");}
        rejects(()->PrivateArchive.read(new ByteArrayInputStream(valid),e->null),"null sink stream");
        rejects(()->PrivateArchive.write(source(new SourceEntry(id(1),Type.SNAPSHOT,0,()->null)),OutputStream.nullOutputStream()),"null source stream");
        rejects(()->PrivateArchive.write(null,OutputStream.nullOutputStream()),"null archive source");
        rejects(()->PrivateArchive.read(null,e->OutputStream.nullOutputStream()),"null archive input");
        int[] opened={0};
        rejects(()->PrivateArchive.write(source(new SourceEntry(id(1),Type.PREFERENCES,MAX_METADATA_ENTRY_BYTES+1L,()->{opened[0]++;return new ByteArrayInputStream(new byte[0]);})),OutputStream.nullOutputStream()),"invalid length before opener");
        check(opened[0]==0,"oversized source never opened");
        int[] closed={0};
        rejects(()->encode(new SourceEntry(id(1),Type.SNAPSHOT,3,()->new InputStream(){
            public int read()throws IOException{throw new IOException("synthetic source read failure");}
            public void close(){closed[0]++;}
        })),"source read failure");
        check(closed[0]==1,"owned stream closes on source read failure");
    }
    private static void zeroBulkProgress() throws Exception {
        byte[] valid=encode(entry(id(1),Type.SNAPSHOT,new byte[]{1,2,3}));
        InputStream input=new InputStream(){final ByteArrayInputStream bytes=new ByteArrayInputStream(valid);
            public int read(){return bytes.read();}
            public int read(byte[] b,int o,int n){return 0;}
        };
        check(PrivateArchive.read(input,e->OutputStream.nullOutputStream()).entryCount==1,"zero bulk read header makes progress");
        SourceEntry zeros=new SourceEntry(id(1),Type.SNAPSHOT,3,()->new InputStream(){final ByteArrayInputStream bytes=new ByteArrayInputStream(new byte[]{1,2,3});
            public int read(){return bytes.read();}
            public int read(byte[] b,int o,int n){return 0;}
        });
        check(decode(encode(zeros)).entryCount==1,"zero bulk source read makes progress");
    }
    private static void budgets() throws Exception {
        // Every ID/frame costs a conservative 384-byte slot, independently of payload sizes.
        int slots=(int)(MAX_INDEX_BYTES/INDEX_BYTES_PER_ENTRY);
        ArchiveSource many=new ArchiveSource(){int n;public SourceEntry next(){return n==slots+1?null:entry(id(++n),Type.SNAPSHOT,new byte[0]);}};
        rejects(() -> PrivateArchive.write(many,OutputStream.nullOutputStream()),"metadata index budget overflow");
        ByteArrayOutputStream out=new ByteArrayOutputStream(); DataOutputStream d=new DataOutputStream(out); d.write(MAGIC);d.writeInt(1);
        for(int n=1;n<=slots+1;n++){d.writeByte(1);d.writeByte(1);d.writeLong(id(n).getMostSignificantBits());d.writeLong(id(n).getLeastSignificantBits());d.writeLong(0);d.write(digest(new byte[0]));}
        byte[] preceding=out.toByteArray();d.writeByte(0);d.writeInt(slots+1);d.writeLong(preceding.length);d.write(digest(preceding));
        try{decode(out.toByteArray());throw new AssertionError("accepted reader index overflow");}
        catch(IOException expected){check(expected.getMessage().contains("index limit"),"reader rejects actual index budget, not missing terminal");}
        // Keep exactly the allowed frames and build their independent valid terminal.
        byte[] boundary=Arrays.copyOf(preceding,PREFIX+slots*RECORD);
        ByteArrayOutputStream allowed=new ByteArrayOutputStream();DataOutputStream terminal=new DataOutputStream(allowed);terminal.write(boundary);
        terminal.writeByte(0);terminal.writeInt(slots);terminal.writeLong(boundary.length);terminal.write(digest(boundary));
        check(decode(allowed.toByteArray()).entryCount==slots,"index boundary accepted");
    }
    public static void main(String[] args) throws Exception {
        if(args.length>0 && args[0].equals("zero")){zeroBulkProgress();return;}
        roundTripAndOwnership(); corruptInputs(); writeFailures(); stagingResourceContracts(); budgets(); zeroBulkProgress();
        System.out.println("PrivateArchive PASS: "+assertions+" assertions");
    }
}
