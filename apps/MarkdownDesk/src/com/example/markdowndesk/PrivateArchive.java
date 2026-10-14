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
    private static final int VERSION = 1, ENTRY = 1, TERMINAL = 0, ENTRY_HEADER_BYTES = 58,
            TERMINAL_BYTES = 45;

    private PrivateArchive() { }

    /** Stable wire IDs; ordinal is never serialized. Raw opaque data remains provisional. */
    public enum Type {
        SNAPSHOT(1, true), PREPARATION(2, false), COMPLETION(3, false), CHECKPOINT(4, false),
        IDENTITY(5, false), SESSION_TAB(6, true), RECOVERY(7, true), PREFERENCES(8, false),
        /** Complete native HistoryIndex .index envelope; opaque bounded metadata at this layer. */
        INDEX(9, false);
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

    private static void validate(UUID id, Type type, long length) throws IOException {
        if (id == null || id.version() != 4 || id.variant() != 2) throw new IOException("Invalid generated entry ID");
        if (type == null) throw new IOException("Missing entry type");
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
