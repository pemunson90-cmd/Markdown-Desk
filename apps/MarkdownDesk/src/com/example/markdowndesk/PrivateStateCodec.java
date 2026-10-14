package com.example.markdowndesk;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CoderResult;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.DigestInputStream;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

/**
 * Pure portable private-state framing, never native session parsing or activation.
 * All source authority is inert provenance. Payload sinks/output frames are provisional
 * until the method returns successfully; caller owns staging, cleanup and adoption.
 */
public final class PrivateStateCodec {
    public static final int MAX_PAYLOAD_BYTES = 8388608, MAX_OVERHEAD_BYTES = 32768,
            MAX_PREFERENCES_BYTES = 65536, MAX_STRING_BYTES = 4096, TRANSFER_BYTES = 65536;
    private static final byte[] MAGIC = "MDSTATE\n".getBytes(StandardCharsets.US_ASCII);
    private static final int VERSION = 1, FIXED_OVERHEAD = 53;
    private static final byte[] BOM = {(byte) 0xef, (byte) 0xbb, (byte) 0xbf};

    private PrivateStateCodec() { }

    public enum Kind {
        SESSION_TAB(6), RECOVERY(7), PREFERENCES(8);
        final int wireId;
        Kind(int wireId) { this.wireId = wireId; }
        static Kind decode(int value) throws IOException {
            for (Kind kind : values()) if (kind.wireId == value) return kind;
            throw new IOException("Unknown private-state kind");
        }
    }

    /** Historical observations only: never restored URI permission or clean-save authority. */
    public static final class Provenance {
        public final String sourceUri, sourceBaselineHash;
        public final long sourceBaselineModified;
        public final boolean sourceReadOnly;
        public Provenance(String sourceUri, String sourceBaselineHash, long sourceBaselineModified,
                boolean sourceReadOnly) {
            this.sourceUri = sourceUri; this.sourceBaselineHash = sourceBaselineHash;
            this.sourceBaselineModified = sourceBaselineModified; this.sourceReadOnly = sourceReadOnly;
        }
    }

    public static final class Tab {
        public final String sourceTabId, displayName, findQuery, replaceQuery;
        public final Provenance provenance;
        public final boolean utf8Bom, sourceDirty, editorLocked, findCaseSensitive, findWholeWord, findRegex;
        public final int selectionStart, selectionEnd, editorScrollY, previewScrollY, mode;
        public Tab(String sourceTabId, String displayName, Provenance provenance, boolean utf8Bom,
                boolean sourceDirty, boolean editorLocked, int selectionStart, int selectionEnd,
                int editorScrollY, int previewScrollY, int mode, String findQuery, String replaceQuery,
                boolean findCaseSensitive, boolean findWholeWord, boolean findRegex) {
            this.sourceTabId = sourceTabId; this.displayName = displayName; this.provenance = provenance;
            this.utf8Bom = utf8Bom; this.sourceDirty = sourceDirty; this.editorLocked = editorLocked;
            this.selectionStart = selectionStart; this.selectionEnd = selectionEnd;
            this.editorScrollY = editorScrollY; this.previewScrollY = previewScrollY; this.mode = mode;
            this.findQuery = findQuery; this.replaceQuery = replaceQuery;
            this.findCaseSensitive = findCaseSensitive; this.findWholeWord = findWholeWord; this.findRegex = findRegex;
        }
    }

    public static final class Recovery {
        public final String displayName;
        public final Provenance provenance;
        public final boolean utf8Bom;
        public final int selectionStart, selectionEnd;
        public final long timestamp;
        public Recovery(String displayName, Provenance provenance, boolean utf8Bom,
                int selectionStart, int selectionEnd, long timestamp) {
            this.displayName = displayName; this.provenance = provenance; this.utf8Bom = utf8Bom;
            this.selectionStart = selectionStart; this.selectionEnd = selectionEnd; this.timestamp = timestamp;
        }
    }

    /** No text/payload arrays and no native DocumentTab/provider/preferences objects. */
    public static final class Record {
        public final Kind kind;
        public final Tab tab;
        public final Recovery recovery;
        public final Map<String, Object> preferences;
        public final int payloadBytes, utf16Units;
        private Record(Kind kind, Tab tab, Recovery recovery, Map<String, Object> preferences,
                int payloadBytes, int utf16Units) {
            this.kind = kind; this.tab = tab; this.recovery = recovery; this.preferences = preferences;
            this.payloadBytes = payloadBytes; this.utf16Units = utf16Units;
        }
    }

    /** Streams one caller-owned immutable input. Does not flush/close either stream. */
    public static void writeTab(Tab tab, int payloadBytes, InputStream payload, OutputStream frame)
            throws IOException {
        if (tab == null) throw new IOException("Missing tab metadata");
        byte[] metadata = tabMetadata(tab);
        write(Kind.SESSION_TAB, metadata, payloadBytes, payload, frame, tab.utf8Bom,
                tab.selectionStart, tab.selectionEnd);
    }

    public static void writeRecovery(Recovery recovery, int payloadBytes, InputStream payload,
            OutputStream frame) throws IOException {
        if (recovery == null) throw new IOException("Missing recovery metadata");
        byte[] metadata = recoveryMetadata(recovery);
        write(Kind.RECOVERY, metadata, payloadBytes, payload, frame, recovery.utf8Bom,
                recovery.selectionStart, recovery.selectionEnd);
    }

    /** Omitted keys remain omitted. Unsupported keys/types/ranges fail, never silently filter. */
    public static void writePreferences(Map<String, Object> preferences, OutputStream frame)
            throws IOException {
        byte[] metadata = preferencesMetadata(preferences);
        write(Kind.PREFERENCES, metadata, 0, new ByteArrayInputStream(new byte[0]), frame, false, 0, 0);
    }

    /**
     * Streams untrusted bytes to a provisional sink. Return requires the complete checksum,
     * strict UTF8/cursor validation and true EOF. Sink bytes on any failure are NOT approved.
     * Does not flush/close either stream. A future importer must remap IDs and restore dirty
     * drafts with null URI/unknown baseline, never reuse source provenance as authority.
     */
    public static Record read(InputStream frame, OutputStream provisionalPayload) throws IOException {
        if (frame == null || provisionalPayload == null) throw new IOException("Missing private-state stream");
        MessageDigest digest = sha256();
        DigestInputStream tracked = new DigestInputStream(new ProgressInput(frame), digest);
        DataInputStream in = new DataInputStream(tracked);
        byte[] magic = new byte[MAGIC.length]; in.readFully(magic);
        if (!Arrays.equals(magic, MAGIC)) throw new IOException("Unknown private-state magic");
        if (in.readInt() != VERSION) throw new IOException("Unknown private-state version");
        Kind kind = Kind.decode(in.readUnsignedByte());
        int metadataBytes = in.readInt(), payloadBytes = in.readInt();
        bounds(kind, metadataBytes, payloadBytes);
        byte[] metadata = new byte[metadataBytes]; in.readFully(metadata);
        DataInputStream fields = new DataInputStream(new ByteArrayInputStream(metadata));
        Tab tab = null; Recovery recovery = null; Map<String, Object> preferences = null;
        boolean bom = false; int start = 0, end = 0;
        if (kind == Kind.SESSION_TAB) {
            Common common = readCommon(fields);
            tab = new Tab(readString(fields, false), common.name, common.provenance, common.bom,
                    readBoolean(fields), readBoolean(fields), common.start, common.end,
                    fields.readInt(), fields.readInt(), fields.readInt(), readString(fields, false),
                    readString(fields, false), readBoolean(fields), readBoolean(fields), readBoolean(fields));
            validateTab(tab);
            bom = tab.utf8Bom; start = tab.selectionStart; end = tab.selectionEnd;
        } else if (kind == Kind.RECOVERY) {
            Common common = readCommon(fields);
            recovery = new Recovery(common.name, common.provenance, common.bom,
                    common.start, common.end, fields.readLong());
            validateRecovery(recovery);
            bom = recovery.utf8Bom; start = recovery.selectionStart; end = recovery.selectionEnd;
        } else {
            preferences = readPreferences(fields);
        }
        if (fields.read() != -1) throw new IOException("Trailing private-state metadata");
        int units = transfer(in, provisionalPayload, payloadBytes, bom);
        cursor(start, end, units);
        tracked.on(false);
        byte[] expected = new byte[32]; in.readFully(expected);
        if (!MessageDigest.isEqual(expected, digest.digest())) throw new IOException("Private-state checksum mismatch");
        if (in.read() != -1) throw new IOException("Trailing private-state bytes");
        return new Record(kind, tab, recovery, preferences, payloadBytes, units);
    }

    private static void write(Kind kind, byte[] metadata, int payloadBytes, InputStream payload,
            OutputStream frame, boolean bom, int start, int end) throws IOException {
        if (payload == null || frame == null) throw new IOException("Missing private-state stream");
        bounds(kind, metadata.length, payloadBytes);
        if (bom && payloadBytes < 3) throw new IOException("Missing flagged UTF8 BOM");
        MessageDigest digest = sha256();
        DigestOutputStream tracked = new DigestOutputStream(frame, digest);
        DataOutputStream out = new DataOutputStream(tracked);
        out.write(MAGIC); out.writeInt(VERSION); out.writeByte(kind.wireId);
        out.writeInt(metadata.length); out.writeInt(payloadBytes); out.write(metadata);
        int units = transfer(new ProgressInput(payload), out, payloadBytes, bom);
        cursor(start, end, units);
        if (payload.read() != -1) throw new IOException("Trailing private-state source payload");
        tracked.on(false); out.write(digest.digest());
    }

    private static void bounds(Kind kind, int metadata, int payload) throws IOException {
        int maxOverhead = kind == Kind.PREFERENCES ? MAX_PREFERENCES_BYTES : MAX_OVERHEAD_BYTES;
        if (metadata < 0 || metadata > maxOverhead - FIXED_OVERHEAD || payload < 0
                || payload > MAX_PAYLOAD_BYTES || (kind == Kind.PREFERENCES && payload != 0))
            throw new IOException("Private-state framing exceeds bound");
    }

    private static byte[] tabMetadata(Tab tab) throws IOException {
        validateTab(tab);
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(buffer);
        writeCommon(out, tab.displayName, tab.provenance, tab.utf8Bom, tab.selectionStart, tab.selectionEnd);
        writeString(out, tab.sourceTabId, false); out.writeBoolean(tab.sourceDirty); out.writeBoolean(tab.editorLocked);
        out.writeInt(tab.editorScrollY); out.writeInt(tab.previewScrollY); out.writeInt(tab.mode);
        writeString(out, tab.findQuery, false); writeString(out, tab.replaceQuery, false);
        out.writeBoolean(tab.findCaseSensitive); out.writeBoolean(tab.findWholeWord); out.writeBoolean(tab.findRegex);
        return buffer.toByteArray();
    }

    private static byte[] recoveryMetadata(Recovery recovery) throws IOException {
        validateRecovery(recovery);
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(buffer);
        writeCommon(out, recovery.displayName, recovery.provenance, recovery.utf8Bom,
                recovery.selectionStart, recovery.selectionEnd);
        out.writeLong(recovery.timestamp);
        return buffer.toByteArray();
    }

    private static void validateTab(Tab tab) throws IOException {
        validateCommon(tab.provenance, tab.selectionStart, tab.selectionEnd);
        if (tab.sourceTabId == null || tab.sourceTabId.isEmpty() || tab.editorScrollY < 0
                || tab.previewScrollY < 0 || tab.mode < 0 || tab.mode > 2)
            throw new IOException("Invalid tab metadata");
    }

    private static void validateRecovery(Recovery recovery) throws IOException {
        validateCommon(recovery.provenance, recovery.selectionStart, recovery.selectionEnd);
        if (recovery.timestamp < 0) throw new IOException("Invalid recovery timestamp");
    }

    private static void validateCommon(Provenance provenance, int start, int end) throws IOException {
        if (provenance == null || provenance.sourceBaselineModified < -1 || start < 0 || end < 0)
            throw new IOException("Invalid document metadata");
        String hash = provenance.sourceBaselineHash;
        if (hash != null) {
            if (hash.length() != 64) throw new IOException("Invalid source baseline hash");
            for (int i = 0; i < hash.length(); i++) {
                char c = hash.charAt(i);
                if (!((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f')))
                    throw new IOException("Invalid source baseline hash");
            }
        }
    }

    private static void writeCommon(DataOutputStream out, String name, Provenance provenance,
            boolean bom, int start, int end) throws IOException {
        writeString(out, name, false); writeString(out, provenance.sourceUri, true);
        writeString(out, provenance.sourceBaselineHash, true); out.writeLong(provenance.sourceBaselineModified);
        out.writeBoolean(provenance.sourceReadOnly); out.writeBoolean(bom); out.writeInt(start); out.writeInt(end);
    }

    private static final class Common {
        final String name; final Provenance provenance; final boolean bom; final int start, end;
        Common(String name, Provenance provenance, boolean bom, int start, int end) {
            this.name = name; this.provenance = provenance; this.bom = bom; this.start = start; this.end = end;
        }
    }

    private static Common readCommon(DataInputStream in) throws IOException {
        String name = readString(in, false), uri = readString(in, true), hash = readString(in, true);
        Provenance provenance = new Provenance(uri, hash, in.readLong(), readBoolean(in));
        return new Common(name, provenance, readBoolean(in), in.readInt(), in.readInt());
    }

    private static byte[] preferencesMetadata(Map<String, Object> values) throws IOException {
        if (values == null || values.size() > 8) throw new IOException("Invalid preference count");
        TreeMap<String, Object> sorted = new TreeMap<>();
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            preferenceType(entry.getKey(), entry.getValue()); sorted.put(entry.getKey(), entry.getValue());
        }
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(buffer); out.writeInt(sorted.size());
        for (Map.Entry<String, Object> entry : sorted.entrySet()) {
            writeString(out, entry.getKey(), false);
            int type = preferenceType(entry.getKey(), entry.getValue()); out.writeByte(type);
            if (type == 1) out.writeBoolean((Boolean) entry.getValue());
            else if (type == 2) out.writeInt((Integer) entry.getValue());
            else out.writeInt(Float.floatToIntBits((Float) entry.getValue()));
        }
        return buffer.toByteArray();
    }

    private static Map<String, Object> readPreferences(DataInputStream in) throws IOException {
        int count = in.readInt();
        if (count < 0 || count > 8) throw new IOException("Invalid preference count");
        TreeMap<String, Object> values = new TreeMap<>(); String prior = null;
        for (int i = 0; i < count; i++) {
            String key = readString(in, false);
            if (prior != null && key.compareTo(prior) <= 0) throw new IOException("Noncanonical preference order");
            int type = in.readUnsignedByte(); Object value;
            if (type == 1) value = Boolean.valueOf(readBoolean(in));
            else if (type == 2) value = Integer.valueOf(in.readInt());
            else if (type == 3) value = Float.valueOf(Float.intBitsToFloat(in.readInt()));
            else throw new IOException("Unknown preference type");
            if (preferenceType(key, value) != type) throw new IOException("Wrong preference type");
            values.put(key, value); prior = key;
        }
        return Collections.unmodifiableMap(values);
    }

    private static int preferenceType(String key, Object value) throws IOException {
        if (key == null || value == null) throw new IOException("Missing preference key/value");
        if (key.equals("line_numbers") || key.equals("typewriter") || key.equals("launch_splash")) {
            if (value.getClass() == Boolean.class) return 1;
        } else if (key.equals("ui_theme") || key.equals("word_goal")) {
            if (value.getClass() == Integer.class) {
                int number = (Integer) value;
                if (number >= 0 && (!key.equals("ui_theme") || number <= 8)) return 2;
            }
        } else if (key.equals("font_size") || key.equals("preview_font_size") || key.equals("split_ratio")) {
            if (value.getClass() == Float.class) {
                float number = (Float) value;
                float min = key.equals("split_ratio") ? 0.28f : 12f;
                float max = key.equals("split_ratio") ? 0.72f : 32f;
                if (Float.isFinite(number) && number >= min && number <= max) return 3;
            }
        }
        throw new IOException("Unsupported preference key/type/range");
    }

    private static void writeString(DataOutputStream out, String text, boolean nullable) throws IOException {
        if (text == null) {
            if (!nullable) throw new IOException("Missing metadata string");
            out.writeInt(-1); return;
        }
        if (text.length() > MAX_STRING_BYTES) throw new IOException("Metadata string exceeds bound");
        ByteBuffer bytes = StandardCharsets.UTF_8.newEncoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).encode(CharBuffer.wrap(text));
        if (bytes.remaining() > MAX_STRING_BYTES) throw new IOException("Metadata string exceeds bound");
        out.writeInt(bytes.remaining());
        out.write(bytes.array(), bytes.arrayOffset() + bytes.position(), bytes.remaining());
    }

    private static String readString(DataInputStream in, boolean nullable) throws IOException {
        int length = in.readInt();
        if (nullable && length == -1) return null;
        if (length < 0 || length > MAX_STRING_BYTES) throw new IOException("Invalid metadata string length");
        byte[] bytes = new byte[length]; in.readFully(bytes);
        return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
    }

    private static boolean readBoolean(DataInputStream in) throws IOException {
        int value = in.readUnsignedByte();
        if (value != 0 && value != 1) throw new IOException("Noncanonical boolean");
        return value == 1;
    }

    private static void cursor(int start, int end, int units) throws IOException {
        if (start < 0 || end < 0 || start > units || end > units) throw new IOException("Cursor exceeds decoded text");
    }

    private static int transfer(InputStream input, OutputStream output, int length, boolean bom) throws IOException {
        Utf8Counter counter = new Utf8Counter(bom);
        byte[] buffer = new byte[TRANSFER_BYTES]; int left = length;
        while (left > 0) {
            int count = input.read(buffer, 0, Math.min(buffer.length, left));
            if (count < 0) throw new IOException("Truncated private-state payload");
            counter.accept(buffer, count); output.write(buffer, 0, count); left -= count;
        }
        return counter.finish();
    }

    private static final class Utf8Counter {
        final CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT);
        final ByteBuffer bytes = ByteBuffer.allocate(TRANSFER_BYTES + 4);
        final CharBuffer chars = CharBuffer.allocate(TRANSFER_BYTES / 2);
        int bomLeft, units;
        Utf8Counter(boolean bom) { bomLeft = bom ? 3 : 0; }
        void accept(byte[] input, int length) throws IOException {
            int at = 0;
            while (bomLeft > 0 && at < length) {
                if (input[at++] != BOM[3 - bomLeft]) throw new IOException("Missing flagged UTF8 BOM");
                bomLeft--;
            }
            bytes.put(input, at, length - at); bytes.flip(); decode(false); bytes.compact();
        }
        void decode(boolean end) throws IOException {
            while (true) {
                chars.clear(); CoderResult result = decoder.decode(bytes, chars, end); units += chars.position();
                if (result.isError()) result.throwException();
                if (!result.isOverflow()) return;
            }
        }
        int finish() throws IOException {
            if (bomLeft != 0) throw new IOException("Missing flagged UTF8 BOM");
            bytes.flip(); decode(true);
            while (true) {
                chars.clear(); CoderResult result = decoder.flush(chars); units += chars.position();
                if (result.isError()) result.throwException();
                if (!result.isOverflow()) return units;
            }
        }
    }

    private static final class ProgressInput extends InputStream {
        final InputStream source;
        ProgressInput(InputStream source) { this.source = source; }
        @Override public int read() throws IOException { return source.read(); }
        @Override public int read(byte[] bytes, int offset, int length) throws IOException {
            if (length == 0) return 0;
            int count = source.read(bytes, offset, Math.min(length, TRANSFER_BYTES));
            if (count == 0) {
                int value = read(); if (value < 0) return -1;
                bytes[offset] = (byte) value; return 1;
            }
            if (count < -1 || count > Math.min(length, TRANSFER_BYTES)) throw new IOException("Invalid source read count");
            return count;
        }
    }

    private static MessageDigest sha256() {
        try { return MessageDigest.getInstance("SHA-256"); }
        catch (NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
    }
}
