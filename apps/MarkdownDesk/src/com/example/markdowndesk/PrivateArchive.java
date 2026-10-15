package com.example.markdowndesk;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Streaming private archive framing and integrity only. No filesystem paths, live-state writes,
 * graph validation, payload parsing, promotion or verified-export receipt. Input is private data.
 * Future import must validate typed payloads/references and stage everything before adoption.
 */
public final class PrivateArchive {
    public static final long MAX_ARCHIVE_BYTES = 1L << 30, MAX_INDEX_BYTES = 16L << 20;
    public static final int MAX_ENTRIES = 65536, MAX_DOCUMENT_ENTRY_BYTES = 8388608 + 32768,
            MAX_METADATA_ENTRY_BYTES = 65536, MAX_STRING_BYTES = 4096, TRANSFER_BYTES = 65536;
    /** Conservative ID/frame/bookkeeping charge; the index budget can bind before entry count. */
    public static final int INDEX_BYTES_PER_ENTRY = 384;
    private static final byte[] MAGIC = "MDESKBACKUP\r\n\032\n".getBytes(StandardCharsets.US_ASCII);
    private static final long READER_WORKSPACE_BYTES = 128L << 10;
    private static final int VERSION = 1, ENTRY = 1, TERMINAL = 0, ENTRY_HEADER_BYTES = 58,
            TERMINAL_BYTES = 45;

    private PrivateArchive() { }

    /** Stable wire IDs; ordinal is never serialized. Raw opaque data remains provisional. */
    public enum Type {
        SNAPSHOT(1, true), PREPARATION(2, false), COMPLETION(3, false), CHECKPOINT(4, false),
        IDENTITY(5, false), SESSION_TAB(6, true), RECOVERY(7, true), PREFERENCES(8, false),
        /** Complete native HistoryIndex .index envelope; opaque bounded metadata at this layer. */
        INDEX(9, false),
        /** Inert streaming capsule, available only in explicit version3/schema2 framing. */
        OPERATION_EVIDENCE(11, false);
        private final int wireId;
        private final boolean document;
        Type(int wireId, boolean document) { this.wireId = wireId; this.document = document; }
        private static Type decode(int wireId) throws IOException {
            for (Type type : values()) if (type.wireId == wireId) return type;
            throw new IOException("Unknown archive type");
        }
    }

    /** Each invocation returns a fresh stream at byte zero with identical bounded bytes. */
    public interface StreamOpener { InputStream open() throws IOException; }

    /** Enumerated once, in wire order. Null ends enumeration; no payload arrays are required. */
    public interface ArchiveSource { SourceEntry next() throws IOException; }

    /**
     * All calls are PROVISIONAL: stage privately, never promote or write live state from callbacks.
     * begin receives a validated frame, not yet a verified payload. finish always follows a
     * successful non-null begin, including copy/hash failures. It may close/flush that staging
     * output; the codec does neither. payloadVerified says only this entry's bytes matched,
     * not that the archive is valid. Adoption requires read to return, plus future strict
     * payload/graph validation. Caller retains failed staging and controls cleanup.
     */
    public interface EntrySink {
        OutputStream begin(Entry entry) throws IOException;
        default void finish(Entry entry, OutputStream stream, boolean payloadVerified) throws IOException { }
    }

    /** Immutable descriptor and repeatable opener; the codec closes every opened source stream. */
    public static final class SourceEntry {
        public final UUID id;
        public final Type type;
        public final long length;
        private final StreamOpener opener;
        public SourceEntry(UUID id, Type type, long length, StreamOpener opener) {
            this.id = id; this.type = type; this.length = length; this.opener = opener;
        }
        private InputStream open() throws IOException {
            if (opener == null) throw new IOException("Missing entry opener");
            InputStream stream = opener.open();
            if (stream == null) throw new IOException("Missing entry stream");
            return stream;
        }
    }

    /** UUID v4 identifiers are data, never paths. Hash strings are lowercase SHA-256 hex. */
    public static final class Entry {
        public final UUID id;
        public final Type type;
        public final long length;
        public final String sha256;
        private Entry(UUID id, Type type, long length, String sha256) {
            this.id = id; this.type = type; this.length = length; this.sha256 = sha256;
        }
    }

    /** Complete stream length/hash including terminal. This is not a durable export receipt. */
    public static final class ArchiveSummary {
        public final int entryCount;
        public final long byteCount;
        public final String sha256;
        private ArchiveSummary(int entryCount, long byteCount, String sha256) {
            this.entryCount = entryCount; this.byteCount = byteCount; this.sha256 = sha256;
        }
    }

    /**
     * Two passes per entry: hash, then stream while rechecking length/hash/repeatability.
     * Does not flush or close caller output/source enumeration. Failure leaves incomplete output.
     * Caller must settle destination flush/close and perform fresh readback before reporting backup.
     */
    public static ArchiveSummary write(ArchiveSource source, OutputStream output) throws IOException {
        if (source == null || output == null) throw new IOException("Missing archive source/output");
        Tracker tracker = new Tracker();
        DataOutputStream out = new DataOutputStream(new OutputStream() {
            public void write(int value) throws IOException {
                tracker.ensure(1); output.write(value); tracker.account(value);
            }
            public void write(byte[] bytes, int offset, int length) throws IOException {
                tracker.ensure(length); output.write(bytes, offset, length); tracker.account(bytes, offset, length);
            }
        });
        out.write(MAGIC); out.writeInt(VERSION);
        byte[] buffer = new byte[TRANSFER_BYTES];
        Index index = new Index();
        SourceEntry entry;
        while ((entry = source.next()) != null) {
            validate(entry.id, entry.type, entry.length);
            index.add(entry.id);
            requireSpace(tracker.count, ENTRY_HEADER_BYTES + entry.length + TERMINAL_BYTES);
            byte[] expected;
            try (InputStream input = entry.open()) {
                expected = transfer(input, null, entry.length, buffer, true);
            }
            out.writeByte(ENTRY); out.writeByte(entry.type.wireId);
            out.writeLong(entry.id.getMostSignificantBits()); out.writeLong(entry.id.getLeastSignificantBits());
            out.writeLong(entry.length); out.write(expected);
            byte[] actual;
            try (InputStream input = entry.open()) {
                actual = transfer(input, out, entry.length, buffer, true);
            }
            if (!MessageDigest.isEqual(expected, actual)) throw new IOException("Entry source changed");
        }
        long precedingLength = tracker.count;
        byte[] precedingHash = tracker.prefix.digest();
        tracker.terminal = true;
        out.writeByte(TERMINAL); out.writeInt(index.count); out.writeLong(precedingLength); out.write(precedingHash);
        return new ArchiveSummary(index.count, tracker.count, hex(tracker.complete.digest()));
    }

    /** Never closes caller input or sink. Summary exists only after manifest and true EOF verify. */
    public static ArchiveSummary read(InputStream input, EntrySink sink) throws IOException {
        return readV1(input, sink, null);
    }

    private static ArchiveSummary readV1(InputStream input, EntrySink sink,
            PrivateBundleInventory.Reservation prepaid) throws IOException {
        if (input == null || sink == null) throw new IOException("Missing archive input/sink");
        Tracker tracker = new Tracker();
        InputStream tracked = new InputStream() {
            public int read() throws IOException {
                int value = input.read();
                if (value >= 0) { tracker.ensure(1); tracker.account(value); }
                return value;
            }
            public int read(byte[] bytes, int offset, int length) throws IOException {
                if (length == 0) return 0;
                // Ensure progress even for a stream returning zero from every bulk read.
                int read = input.read(bytes, offset, Math.min(length, TRANSFER_BYTES));
                if (read == 0) {
                    int value = read();
                    if (value < 0) return -1;
                    bytes[offset] = (byte) value;
                    return 1;
                }
                if (read > 0) { tracker.ensure(read); tracker.account(bytes, offset, read); }
                return read;
            }
        };
        DataInputStream in = new DataInputStream(tracked);
        byte[] magic = new byte[MAGIC.length]; in.readFully(magic);
        if (!Arrays.equals(magic, MAGIC)) throw new IOException("Unknown archive magic");
        if (in.readInt() != VERSION) throw new IOException("Unknown archive version");
        byte[] buffer = new byte[TRANSFER_BYTES];
        Index index = new Index();
        while (true) {
            // Peek raw marker: terminal marker itself must not enter the preceding-stream digest.
            int marker = input.read();
            if (marker < 0) throw new EOFException("Missing terminal manifest");
            if (marker == TERMINAL) {
                long precedingLength = tracker.count;
                byte[] precedingHash = tracker.prefix.digest();
                tracker.terminal = true;
                tracker.ensure(1); tracker.account(marker);
                long declaredCount = Integer.toUnsignedLong(in.readInt());
                long declaredLength = in.readLong();
                byte[] declaredHash = new byte[32]; in.readFully(declaredHash);
                if (declaredCount != index.count || declaredLength < 0 || declaredLength != precedingLength
                        || !MessageDigest.isEqual(precedingHash, declaredHash))
                    throw new IOException("Invalid terminal manifest");
                if (input.read() != -1) throw new IOException("Trailing archive bytes");
                return new ArchiveSummary(index.count, tracker.count, hex(tracker.complete.digest()));
            }
            if (marker != ENTRY) throw new IOException("Unknown archive frame");
            tracker.ensure(1); tracker.account(marker);
            Type type = Type.decode(in.readUnsignedByte());
            if (prepaid != null) prepaid.grow(INDEX_BYTES_PER_ENTRY);
            UUID id = new UUID(in.readLong(), in.readLong());
            long length = in.readLong();
            validate(id, type, length);
            index.add(id);
            byte[] expected = new byte[32]; in.readFully(expected);
            requireSpace(tracker.count, length + TERMINAL_BYTES);
            Entry entry = new Entry(id, type, length, hex(expected));
            OutputStream output = sink.begin(entry);
            if (output == null) throw new IOException("Missing staging output");
            // try-with-resources preserves the original read/hash failure if finish also fails.
            try (SinkCompletion completion = new SinkCompletion(sink, entry, output)) {
                byte[] actual = transfer(in, output, length, buffer, false);
                if (!MessageDigest.isEqual(expected, actual)) throw new IOException("Entry hash mismatch");
                completion.verified = true;
            }
        }
    }

    /** V2/V3 provisional callbacks; coordinates never grant source authority.
     * Callbacks and their borrowed-metadata work must be synchronous/quiescent on return.
     * Rows exist before any PortableResult: a later read failure closes their owner internally.
     * Retain no borrowed row beyond readPortable completion/failure unless a successful result
     * remains open. Asynchronous work or independently retained copies require separate
     * pre-reservation and cleanup; callers must not depend on a future result for that lifetime.
     * Every successful nonnull begin has exactly one finish, including copy/hash failures.
     */
    public interface ScopedSink {
        OutputStream begin(PrivateBundleInventory.DirectoryEntry entry) throws IOException;
        default void finish(PrivateBundleInventory.DirectoryEntry entry, OutputStream output,
                            boolean payloadVerified) throws IOException { }
    }

    /** Trusted, preadmitted streaming spool. V1 framing incrementally charges its own index.
     * Caller separately precharges simultaneous spool/coordinator state to the same budget;
     * retain no borrowed entries/index. Caller owns staging, admission and closure. */
    public interface LegacySpool extends EntrySink { }

    /** Source framing result only. A legacy spool remains provisional and must be replayed
     * against sourceSummary (all descriptors/payloads) before typed validation/adoption.
     * Scoped rows are borrowed: keep this result open through consumers and quiescence.
     * Close releases logical accounting; escaped immutable rows remain readable, not revoked.
     * Retained copies require separate reservation and a coordinator lifetime audit. */
    public static final class PortableResult implements AutoCloseable {
        public final ArchiveSummary sourceSummary;
        public final LegacySpool provisionalLegacySpool;
        public final PrivateBundleInventory inventory;
        private PortableResult(ArchiveSummary summary, LegacySpool spool, PrivateBundleInventory inventory) {
            sourceSummary=summary; provisionalLegacySpool=spool; this.inventory=inventory;
        }
        public void close() { if (inventory!=null) inventory.close(); }
    }

    /** Explicit portable dispatch. Never closes caller input, including prefix exhaustion/errors. */
    public static PortableResult readPortable(InputStream input, ScopedSink sink, LegacySpool spool,
            PrivateBundleInventory.MetadataBudget budget) throws IOException {
        if(input==null||budget==null) throw new IOException("Missing portable input/budget");
        // Own wrapper/transfer workspace before allocation or any caller input read.
        try(PrivateBundleInventory.Reservation workspace=budget.reserve(READER_WORKSPACE_BYTES)) {
            byte[] header=new byte[MAGIC.length+4];
            new DataInputStream(progress(input)).readFully(header);
            for(int i=0;i<MAGIC.length;i++) if(header[i]!=MAGIC[i]) throw new IOException("Unknown archive magic");
            int version=new DataInputStream(new java.io.ByteArrayInputStream(header,MAGIC.length,4)).readInt();
            InputStream replay=new InputStream() {
                int at;
                public int read() throws IOException {return at<header.length?header[at++]&255:input.read();}
                public int read(byte[] b,int off,int len)throws IOException {
                    if(len==0)return 0;
                    if(at<header.length){int n=Math.min(len,header.length-at);System.arraycopy(header,at,b,off,n);at+=n;return n;}
                    return input.read(b,off,len);
                }
                // Deliberately non-owning. Unlike SequenceInputStream, exhaustion closes nothing.
            };
            if(version==1) {
                if(spool==null)throw new IOException("Missing trusted legacy spool");
                return readV1Budgeted(replay,spool,workspace);
            }
            if(version!=2&&version!=3)throw new IOException("Unknown archive version");
            if(sink==null)throw new IOException("Missing scoped sink");
            return readScoped(replay,sink,budget,version);
        }
    }

    private static PortableResult readV1Budgeted(InputStream input,LegacySpool spool,
            PrivateBundleInventory.Reservation workspace)throws IOException {
        return new PortableResult(readV1(input,spool,workspace),spool,null);
    }

    private static PortableResult readScoped(InputStream input,ScopedSink sink,
            PrivateBundleInventory.MetadataBudget budget,int version)throws IOException {
        Tracker tracker=new Tracker(); DataInputStream in=new DataInputStream(trackedInput(input,tracker));
        byte[] header=new byte[MAGIC.length+4];in.readFully(header); // dispatch already validated these bytes
        if(in.readUnsignedByte()!=ENTRY||in.readUnsignedByte()!=10)throw new IOException("Missing first inventory");
        UUID inventoryId=new UUID(in.readLong(),in.readLong());PrivateBundleInventory.requireGenerated(inventoryId);
        long length=in.readLong();if(length<144||length>MAX_INDEX_BYTES)throw new IOException("Inventory frame limit");
        byte[] hash=new byte[32];in.readFully(hash);
        PrivateBundleInventory inventory=version==3?PrivateBundleInventory.readSchema2(slice(in,length),budget)
                :PrivateBundleInventory.read(slice(in,length),budget);
        boolean returned=false;
        try {
            if(inventory.serializedLength!=length||!inventory.sha256.equals(hex(hash)))throw new IOException("Inventory outer hash/length mismatch");
            rejectInventoryCollision(inventoryId,inventory);
            byte[] buffer=new byte[TRANSFER_BYTES];
            for(PrivateBundleInventory.DirectoryEntry row:inventory.entries()) {
                if(in.readUnsignedByte()!=ENTRY)throw new IOException("Missing ordinary frame");
                Type type=Type.decode(in.readUnsignedByte());UUID id=new UUID(in.readLong(),in.readLong());
                long n=in.readLong();byte[] expected=new byte[32];in.readFully(expected);
                if(!row.id.equals(id)||row.type!=type.wireId||row.length!=n||!row.sha256.equals(hex(expected)))
                    throw new IOException("Inventory frame membership mismatch");
                OutputStream output=sink.begin(row);if(output==null)throw new IOException("Missing scoped staging output");
                try(ScopedCompletion completion=new ScopedCompletion(sink,row,output)) {
                    if(!MessageDigest.isEqual(expected,transfer(in,output,n,buffer,false)))throw new IOException("Entry hash mismatch");
                    completion.verified=true;
                }
            }
            long preceding=tracker.count;byte[] prefix=tracker.prefix.digest();
            tracker.terminal=true;
            if(in.readUnsignedByte()!=TERMINAL||Integer.toUnsignedLong(in.readInt())!=inventory.entries().size()+1L
                    ||in.readLong()!=preceding)throw new IOException("Invalid scoped terminal");
            byte[] terminalHash=new byte[32];in.readFully(terminalHash);
            if(!MessageDigest.isEqual(prefix,terminalHash)||tracker.count!=inventory.archiveBytes||input.read()!=-1)
                throw new IOException("Invalid scoped terminal/hash/EOF");
            PortableResult result=new PortableResult(new ArchiveSummary(inventory.entries().size()+1,tracker.count,
                    hex(tracker.complete.digest())),null,inventory);returned=true;return result;
        } finally {if(!returned)inventory.close();}
    }

    /** Explicit v2 output. Frozen inventory and each source are re-opened and checked for change.
     * Owns opened source streams only; caller settles outer flush/close and fresh readback. */
    public static ArchiveSummary writeV2(UUID inventoryId,StreamOpener frozenInventory,ArchiveSource source,
            OutputStream output,PrivateBundleInventory.MetadataBudget budget)throws IOException {
        return writeScoped(inventoryId,frozenInventory,source,output,budget,2);
    }

    /** Explicit v3 output; framing and hashes do not establish semantic backup completion. */
    public static ArchiveSummary writeV3(UUID inventoryId,StreamOpener frozenInventory,ArchiveSource source,
            OutputStream output,PrivateBundleInventory.MetadataBudget budget)throws IOException {
        return writeScoped(inventoryId,frozenInventory,source,output,budget,3);
    }

    private static ArchiveSummary writeScoped(UUID inventoryId,StreamOpener frozenInventory,ArchiveSource source,
            OutputStream output,PrivateBundleInventory.MetadataBudget budget,int version)throws IOException {
        PrivateBundleInventory.requireGenerated(inventoryId);
        if(frozenInventory==null||source==null||output==null||budget==null)throw new IOException("Missing scoped source/output/budget");
        try(PrivateBundleInventory.Reservation workspace=budget.reserve(READER_WORKSPACE_BYTES)) {
            PrivateBundleInventory parsed=null;
            try(InputStream input=openInventory(frozenInventory)){parsed=version==3?PrivateBundleInventory.readSchema2(input,budget):PrivateBundleInventory.read(input,budget);}
            catch(IOException|RuntimeException|Error failure) {
                if(parsed!=null)parsed.close();
                throw failure;
            }
            final PrivateBundleInventory inventory=parsed;
            try(PrivateBundleInventory retained=inventory) {
                rejectInventoryCollision(inventoryId,inventory);
                Tracker tracker=new Tracker();DataOutputStream out=new DataOutputStream(trackedOutput(output,tracker));
                byte[] buffer=new byte[TRANSFER_BYTES];out.write(MAGIC);out.writeInt(version);
                writeFrame(out,10,inventoryId,inventory.serializedLength,inventory.sha256);
                try(InputStream input=openInventory(frozenInventory)) {
                    if(!inventory.sha256.equals(hex(transfer(input,out,inventory.serializedLength,buffer,true))))
                        throw new IOException("Frozen inventory changed");
                }
                for(PrivateBundleInventory.DirectoryEntry row:inventory.entries()) {
                    SourceEntry entry=source.next();
                    if(entry==null||!row.id.equals(entry.id)||entry.type==null||row.type!=entry.type.wireId||row.length!=entry.length)
                        throw new IOException("Source inventory membership mismatch");
                    try(InputStream input=entry.open()) {
                        if(!row.sha256.equals(hex(transfer(input,null,entry.length,buffer,true))))throw new IOException("Source inventory hash mismatch");
                    }
                    writeFrame(out,row.type,row.id,row.length,row.sha256);
                    try(InputStream input=entry.open()) {
                        if(!row.sha256.equals(hex(transfer(input,out,entry.length,buffer,true))))throw new IOException("Entry source changed");
                    }
                }
                if(source.next()!=null)throw new IOException("Extra source entry");
                long preceding=tracker.count;byte[] prefix=tracker.prefix.digest();tracker.terminal=true;
                out.writeByte(TERMINAL);out.writeInt(inventory.entries().size()+1);out.writeLong(preceding);out.write(prefix);
                if(tracker.count!=inventory.archiveBytes)throw new IOException("Archive total mismatch");
                return new ArchiveSummary(inventory.entries().size()+1,tracker.count,hex(tracker.complete.digest()));
            }
        }
    }
    private static InputStream openInventory(StreamOpener opener)throws IOException {
        InputStream input=opener.open();if(input==null)throw new IOException("Missing inventory stream");return input;
    }
    private static void rejectInventoryCollision(UUID id,PrivateBundleInventory inventory)throws IOException {
        for(PrivateBundleInventory.DirectoryEntry row:inventory.entries())if(id.equals(row.id))throw new IOException("Inventory entry ID collision");
    }
    private static void writeFrame(DataOutputStream out,int type,UUID id,long length,String hash)throws IOException {
        out.writeByte(ENTRY);out.writeByte(type);out.writeLong(id.getMostSignificantBits());out.writeLong(id.getLeastSignificantBits());
        out.writeLong(length);PrivateBundleInventory.putHash(out,hash);
    }
    private static InputStream slice(InputStream input,long length) {
        return new InputStream(){long remaining=length;
            public int read()throws IOException {if(remaining==0)return -1;int b=input.read();if(b>=0)remaining--;return b;}
            public int read(byte[] b,int off,int len)throws IOException {if(len==0)return 0;if(remaining==0)return -1;
                int n=input.read(b,off,(int)Math.min(len,remaining));if(n>0)remaining-=n;return n;}
        };
    }
    private static InputStream progress(InputStream input) {
        return new InputStream(){public int read()throws IOException{return input.read();}
            public int read(byte[] b,int off,int len)throws IOException {if(len==0)return 0;int n=input.read(b,off,len);
                if(n==0){int v=read();if(v<0)return -1;b[off]=(byte)v;return 1;}return n;}
        };
    }
    private static InputStream trackedInput(InputStream input,Tracker tracker) {
        InputStream moving=progress(input);
        return new InputStream(){public int read()throws IOException{int b=moving.read();if(b>=0){tracker.ensure(1);tracker.account(b);}return b;}
            public int read(byte[] b,int off,int len)throws IOException {int n=moving.read(b,off,Math.min(len,TRANSFER_BYTES));if(n>0){tracker.ensure(n);tracker.account(b,off,n);}return n;}
        };
    }
    private static OutputStream trackedOutput(OutputStream output,Tracker tracker) {
        return new OutputStream(){public void write(int b)throws IOException{tracker.ensure(1);output.write(b);tracker.account(b);}
            public void write(byte[] b,int off,int n)throws IOException{tracker.ensure(n);output.write(b,off,n);tracker.account(b,off,n);}
        };
    }
    private static final class ScopedCompletion implements AutoCloseable {
        final ScopedSink sink;final PrivateBundleInventory.DirectoryEntry entry;final OutputStream output;boolean verified;
        ScopedCompletion(ScopedSink sink,PrivateBundleInventory.DirectoryEntry entry,OutputStream output){this.sink=sink;this.entry=entry;this.output=output;}
        public void close()throws IOException{sink.finish(entry,output,verified);}
    }

    private static void validate(UUID id, Type type, long length) throws IOException {
        if (id == null || id.version() != 4 || id.variant() != 2) throw new IOException("Invalid generated entry ID");
        if (type == null || type == Type.OPERATION_EVIDENCE) throw new IOException("Unsupported v1 entry type");
        long cap = type.document ? MAX_DOCUMENT_ENTRY_BYTES : MAX_METADATA_ENTRY_BYTES;
        // Wire lengths are unsigned64. The sign-bit range is necessarily beyond every cap.
        if (length < 0 || length > cap) throw new IOException("Entry length limit exceeded");
    }

    private static void requireSpace(long count, long bytes) throws IOException {
        if (bytes < 0 || count > MAX_ARCHIVE_BYTES - bytes) throw new IOException("Archive byte limit exceeded");
    }

    private static byte[] transfer(InputStream input, OutputStream output, long length,
                                   byte[] buffer, boolean requireEof) throws IOException {
        MessageDigest hash = digest();
        long remaining = length;
        while (remaining > 0) {
            int read = input.read(buffer, 0, (int) Math.min(buffer.length, remaining));
            if (read == 0) {
                // Some non-seekable streams temporarily return zero from bulk read; ensure progress.
                int value = input.read();
                if (value < 0) throw new EOFException("Truncated entry payload");
                buffer[0] = (byte) value; read = 1;
            }
            if (read < 0) throw new EOFException("Truncated entry payload");
            hash.update(buffer, 0, read);
            if (output != null) output.write(buffer, 0, read);
            remaining -= read;
        }
        if (requireEof && input.read() != -1) throw new IOException("Entry source exceeds declared length");
        return hash.digest();
    }

    private static final class SinkCompletion implements AutoCloseable {
        private final EntrySink sink;
        private final Entry entry;
        private final OutputStream output;
        private boolean verified;
        SinkCompletion(EntrySink sink, Entry entry, OutputStream output) {
            this.sink = sink; this.entry = entry; this.output = output;
        }
        public void close() throws IOException { sink.finish(entry, output, verified); }
    }

    private static final class Index {
        private final Set<UUID> ids = new HashSet<>();
        private int count;
        private long charged;
        void add(UUID id) throws IOException {
            if (count >= MAX_ENTRIES) throw new IOException("Archive entry limit exceeded");
            if (charged > MAX_INDEX_BYTES - INDEX_BYTES_PER_ENTRY) throw new IOException("Archive index limit exceeded");
            if (!ids.add(id)) throw new IOException("Duplicate archive entry ID");
            count++; charged += INDEX_BYTES_PER_ENTRY;
        }
    }

    private static final class Tracker {
        private final MessageDigest prefix = digest(), complete = digest();
        private long count;
        private boolean terminal;
        void ensure(int bytes) throws IOException { requireSpace(count, bytes); }
        void account(int value) {
            if (!terminal) prefix.update((byte) value);
            complete.update((byte) value); count++;
        }
        void account(byte[] bytes, int offset, int length) {
            if (!terminal) prefix.update(bytes, offset, length);
            complete.update(bytes, offset, length); count += length;
        }
    }

    private static MessageDigest digest() {
        try { return MessageDigest.getInstance("SHA-256"); }
        catch (NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
    }

    private static String hex(byte[] bytes) {
        char[] out = new char[bytes.length * 2];
        final String digits = "0123456789abcdef";
        for (int i = 0; i < bytes.length; i++) {
            out[i * 2] = digits.charAt((bytes[i] & 255) >>> 4);
            out[i * 2 + 1] = digits.charAt(bytes[i] & 15);
        }
        return new String(out);
    }
}
