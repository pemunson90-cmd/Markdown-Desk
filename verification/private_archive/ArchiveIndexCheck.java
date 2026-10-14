package com.example.markdowndesk;

import java.io.*;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import com.example.markdowndesk.PrivateArchive.*;

/** INDEX framing only; the native fixture uses production persistence, never imported adoption. */
public final class ArchiveIndexCheck {
    private static final byte[] MAGIC = "MDESKBACKUP\r\n\032\n".getBytes(StandardCharsets.US_ASCII);
    private static final int PREFIX = MAGIC.length + 4;
    private static int assertions;
    private interface Action { void run() throws Exception; }
    private static void check(boolean condition, String message) {
        assertions++; if (!condition) throw new AssertionError(message);
    }
    private static void rejects(Action action, String message) throws Exception {
        assertions++;
        try { action.run(); } catch (IOException expected) { return; }
        throw new AssertionError("Accepted " + message);
    }
    private static ArchiveSource source(SourceEntry entry) {
        return new ArchiveSource() { boolean consumed;
            public SourceEntry next() { if (consumed) return null; consumed = true; return entry; }
        };
    }
    private static byte[] encode(Type type, UUID id, byte[] payload) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PrivateArchive.write(source(new SourceEntry(id, type, payload.length,
                () -> new ByteArrayInputStream(payload))), out);
        return out.toByteArray();
    }
    /** Independent framing fixture: accepts no writer assumptions or private wire-ID access. */
    private static byte[] raw(int type, long length, byte[] payload) throws Exception {
        UUID id = UUID.fromString("12345678-9abc-4000-8000-000000000001");
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(); DataOutputStream out = new DataOutputStream(bytes);
        out.write(MAGIC); out.writeInt(1); out.writeByte(1); out.writeByte(type);
        out.writeLong(id.getMostSignificantBits()); out.writeLong(id.getLeastSignificantBits());
        out.writeLong(length); out.write(hash(payload)); out.write(payload);
        byte[] preceding = bytes.toByteArray();
        out.writeByte(0); out.writeInt(1); out.writeLong(preceding.length); out.write(hash(preceding));
        return bytes.toByteArray();
    }
    private static byte[] hash(byte[] bytes) throws Exception { return MessageDigest.getInstance("SHA-256").digest(bytes); }
    private static void stableWireIds(Type index) throws Exception {
        Type[] types = {Type.SNAPSHOT, Type.PREPARATION, Type.COMPLETION, Type.CHECKPOINT,
                Type.IDENTITY, Type.SESSION_TAB, Type.RECOVERY, Type.PREFERENCES, index};
        UUID id = UUID.randomUUID();
        for (int n = 0; n < types.length; n++) {
            final Type expected = types[n]; byte[] bytes = encode(expected, id, new byte[0]);
            check(Arrays.equals(Arrays.copyOf(bytes, MAGIC.length), MAGIC), "unchanged archive magic");
            check(new DataInputStream(new ByteArrayInputStream(bytes, MAGIC.length, 4)).readInt() == 1,
                    "unchanged archive version");
            check((bytes[PREFIX + 1] & 255) == n + 1, "explicit stable wire ID for " + expected);
            check(PrivateArchive.read(new ByteArrayInputStream(raw(n + 1, 0, new byte[0])), e -> {
                check(e.type == expected, "independent wire category decoded"); return OutputStream.nullOutputStream();
            }).entryCount == 1, "independent frame accepted");
        }
    }
    private static void metadataBounds(Type index) throws Exception {
        byte[] boundary = new byte[PrivateArchive.MAX_METADATA_ENTRY_BYTES]; Arrays.fill(boundary, (byte) 0xa5);
        ByteArrayOutputStream received = new ByteArrayOutputStream();
        check(PrivateArchive.read(new ByteArrayInputStream(encode(index, UUID.randomUUID(), boundary)),
                e -> received).entryCount == 1 && Arrays.equals(boundary, received.toByteArray()),
                "INDEX exact metadata cap accepted byte for byte");
        int[] opens = {0}, callbacks = {0};
        rejects(() -> PrivateArchive.write(source(new SourceEntry(UUID.randomUUID(), index, boundary.length + 1L,
                () -> { opens[0]++; return new ByteArrayInputStream(new byte[0]); })), OutputStream.nullOutputStream()),
                "oversized INDEX source");
        check(opens[0] == 0, "oversized INDEX rejected before source opener");
        for (long length : new long[]{boundary.length + 1L, -1L, Long.MAX_VALUE}) {
            rejects(() -> PrivateArchive.read(new ByteArrayInputStream(raw(9, length, new byte[0])), e -> {
                callbacks[0]++; return OutputStream.nullOutputStream();
            }), "INDEX invalid length " + length);
        }
        for (int type : new int[]{10, 255}) {
            rejects(() -> PrivateArchive.read(new ByteArrayInputStream(raw(type, 0, new byte[0])), e -> {
                callbacks[0]++; return OutputStream.nullOutputStream();
            }), "unknown category " + type);
        }
        check(callbacks[0] == 0, "invalid INDEX/category declaration rejected before staging");
        check(PrivateArchive.read(new ByteArrayInputStream(raw(9, 3, new byte[]{(byte) 0xff, 0, 1})),
                e -> OutputStream.nullOutputStream()).entryCount == 1, "INDEX payload semantics remain outside framing");
    }
    private static final class Disk implements ProtectionStore.Durability {
        public void syncFile(FileDescriptor descriptor) throws IOException { descriptor.sync(); }
        public void move(File from, File to) throws IOException {
            Files.move(from.toPath(), to.toPath(), StandardCopyOption.ATOMIC_MOVE);
        }
        public void syncDirectory(File directory) throws IOException {
            try (FileChannel channel = FileChannel.open(directory.toPath(), StandardOpenOption.READ)) { channel.force(true); }
        }
    }
    private static void nativeOwnershipEnvelope(Type category) throws Exception {
        Path parent = Files.createTempDirectory("archive-native-index-");
        try {
            Disk disk = new Disk(); Path root = parent.resolve("index"); String uri = "content://synthetic/history";
            ProtectionStore store = new ProtectionStore(parent.resolve("protection").toFile(), disk, ProtectionStore.DEFAULT_QUOTA_BYTES);
            HistoryCatalog catalog = new HistoryCatalog(parent.resolve("catalog").toFile(), store, disk);
            HistoryIdentity identity = new HistoryIdentity(parent.resolve("identity").toFile(), disk);
            HistoryIndex index = new HistoryIndex(store, catalog, identity, root.toFile(), disk);
            String historyId = identity.resolve(uri, "synthetic-tab");
            ProtectionStore.Snapshot original = store.putSnapshot("synthetic-tab", uri, "Ω", 1,
                    "\ufefforiginal\r\n".getBytes(StandardCharsets.UTF_8), "original");
            ProtectionStore.Snapshot attempted = store.putSnapshot("synthetic-tab", uri, "Ω", 1,
                    "attempted\r\n雪".getBytes(StandardCharsets.UTF_8), "attempted");
            ProtectionStore.Transaction transaction = store.prepare(original, attempted, original.hash);
            store.complete(transaction); index.recordVerified(historyId, transaction.id);
            File[] files = root.toFile().listFiles(); check(files != null && files.length == 1, "one native ownership envelope");
            Path file = files[0].toPath(); check(file.getFileName().toString().endsWith(".index"), "native INDEX suffix");
            byte[] envelope = Files.readAllBytes(file); UUID entryId = UUID.fromString(files[0].getName().replace(".index", ""));
            ByteArrayOutputStream archive = new ByteArrayOutputStream();
            ArchiveSummary written = PrivateArchive.write(source(new SourceEntry(entryId, category, envelope.length,
                    () -> Files.newInputStream(file))), archive);
            ByteArrayOutputStream received = new ByteArrayOutputStream();
            ArchiveSummary read = PrivateArchive.read(new ByteArrayInputStream(archive.toByteArray()), e -> {
                check(e.id.equals(entryId) && e.type == category && e.length == envelope.length, "native envelope descriptor");
                return received;
            });
            check(Arrays.equals(envelope, received.toByteArray()), "complete native INDEX envelope preserved byte for byte");
            check(written.entryCount == 1 && read.entryCount == 1 && written.byteCount == read.byteCount
                    && written.sha256.equals(read.sha256), "native envelope archive summaries match");
            check(Arrays.equals(envelope, Files.readAllBytes(file)), "codec never changes original ownership envelope");
            List<HistoryIndex.Version> versions = new HistoryIndex(store, catalog, identity, root.toFile(), disk).list(historyId);
            check(versions.size() == 2 && versions.stream()
                    .allMatch(v -> v.status == HistoryIndex.Status.INDEXED_VERIFIED && v.sequence == 1),
                    "original native ownership remains valid after framing roundtrip");
        } finally {
            try (java.util.stream.Stream<Path> files = Files.walk(parent)) {
                for (Path file : (Iterable<Path>) files.sorted(Comparator.reverseOrder())::iterator) Files.delete(file);
            }
        }
    }
    private static void indexFaults(Type index) throws Exception {
        byte[] valid = encode(index, UUID.randomUUID(), new byte[]{1, 2, 3});
        int[] finished = {0}; boolean[] verified = {true};
        EntrySink sink = new EntrySink() {
            public OutputStream begin(Entry e) { return OutputStream.nullOutputStream(); }
            public void finish(Entry e, OutputStream out, boolean payloadVerified) {
                finished[0]++; verified[0] = payloadVerified;
            }
        };
        byte[] damaged = valid.clone(); damaged[PREFIX + 58] ^= 1;
        rejects(() -> PrivateArchive.read(new ByteArrayInputStream(damaged), sink), "damaged INDEX payload");
        check(finished[0] == 1 && !verified[0], "INDEX corruption finishes provisional stage without verification");
        rejects(() -> PrivateArchive.read(new ByteArrayInputStream(Arrays.copyOf(valid, PREFIX + 58 + 2)), sink),
                "truncated INDEX payload");
        check(finished[0] == 2 && !verified[0], "INDEX truncation finishes failed stage once");
        rejects(() -> PrivateArchive.read(new ByteArrayInputStream(Arrays.copyOf(valid, valid.length - 1)), sink),
                "truncated INDEX terminal after payload verification");
        check(finished[0] == 3 && verified[0], "verified INDEX payload cannot establish whole archive success");
    }
    public static void main(String[] args) throws Exception {
        Type index = Type.valueOf("INDEX");
        stableWireIds(index); metadataBounds(index); indexFaults(index); nativeOwnershipEnvelope(index);
        System.out.println("PrivateArchive INDEX PASS: " + assertions + " assertions (framing only)");
    }
}
