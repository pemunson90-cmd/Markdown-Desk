package com.example.markdowndesk;

import java.io.File;
import java.io.FileDescriptor;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

/** Real production store + diff, with independent byte reconstruction and LCS oracle. */
public final class HistoryDiffCheck {
    private static int assertions, cases;
    private static final HistoryDiff DIFF = new HistoryDiff();
    private static final ProtectionStore.Durability DURABILITY = new ProtectionStore.Durability() {
        public void syncFile(FileDescriptor fd) throws IOException { fd.sync(); }
        public void move(File a, File b) throws IOException {
            Files.move(a.toPath(), b.toPath(), StandardCopyOption.ATOMIC_MOVE);
        }
        public void syncDirectory(File d) throws IOException {
            try (FileChannel c = FileChannel.open(d.toPath(), StandardOpenOption.READ)) { c.force(true); }
        }
    };
    private static class Fixture {
        final Path root;
        final ProtectionStore store;
        Fixture() throws IOException {
            root = Files.createTempDirectory("history-diff-").resolve("store");
            store = new ProtectionStore(root.toFile(), DURABILITY, 64L * 1024 * 1024);
        }
        ProtectionStore.Snapshot put(byte[] bytes) throws IOException {
            return store.putSnapshot("synthetic", "synthetic://fixture", "fixture", 0, bytes, "original");
        }
        ProtectionStore.Snapshot put(String s) throws IOException { return put(utf8(s)); }
    }
    public static void main(String[] args) throws Exception {
        if (args.length > 0) { memory(); return; }
        exactHunks(); encodingFidelity(); repeatedLinesAndOracle(); sizeBoundary();
        logicalLineBoundary(); outputBoundary(); workLimit(); invalidUtf8(); staleSource(); changedValidMetadata();
        System.out.println("HistoryDiff: " + cases + " cases / " + assertions + " assertions PASS");
    }
    private static void exactHunks() throws Exception {
        Fixture f = new Fixture();
        HistoryDiff.Preview p = compare(f, "one\ntwo\nthree\n", "one\nnew\nthree\n");
        check(p.complete && p.reason.isEmpty(), "exact preview complete");
        check(p.lines.size() == 4, "four exact operations");
        check(p.lines.get(0).kind == HistoryDiff.Kind.UNCHANGED, "context");
        check(p.lines.get(1).kind == HistoryDiff.Kind.REMOVE && p.lines.get(1).text.equals("two"), "remove old");
        check(p.lines.get(2).kind == HistoryDiff.Kind.ADD && p.lines.get(2).text.equals("new"), "add new");
        check(p.lines.get(3).kind == HistoryDiff.Kind.UNCHANGED, "ending context");
        final HistoryDiff.Preview immutable = p;
        expectUnsupported(() -> immutable.lines.clear());
        roundtrip(p, utf8("one\ntwo\nthree\n"), utf8("one\nnew\nthree\n"));
        p = compare(f, "", "tail");
        check(p.complete && p.lines.size() == 1 && p.lines.get(0).ending == HistoryDiff.Ending.NONE, "empty to unterminated");
        roundtrip(p, new byte[0], utf8("tail"));
        p = compare(f, "", ""); check(p.complete && p.lines.isEmpty(), "empty equality");
        cases++;
    }
    private static void encodingFidelity() throws Exception {
        Fixture f = new Fixture();
        String a = "\ufeff 😀\r\n\tsecond\rthird\nlast";
        String b = " 😀\n\tsecond\rthird\nlast\n";
        byte[] left = utf8(a), right = utf8(b);
        ProtectionStore.Snapshot l = f.put(left), r = f.put(right);
        byte[] envelope = Files.readAllBytes(f.root.resolve(l.id + ".snapshot"));
        HistoryDiff.Preview p = DIFF.compare(l, r);
        check(p.complete && p.leftBom && !p.rightBom, "BOM visible in metadata");
        check(p.lines.get(0).display().contains("\\uFEFF") && p.lines.get(0).display().contains("[CRLF]"), "BOM/CRLF visible in display");
        check(p.lines.stream().anyMatch(x -> x.display().contains("[NONE]")), "unterminated end visible");
        roundtrip(p, left, right);
        check(Arrays.equals(left, l.bytes()) && Arrays.equals(right, r.bytes()), "snapshot bytes unchanged");
        check(Arrays.equals(envelope, Files.readAllBytes(f.root.resolve(l.id + ".snapshot"))), "envelope unchanged");
        p = compare(f, "\ufeff", ""); roundtrip(p, utf8("\ufeff"), new byte[0]);
        p = compare(f, "a\r\n", "a\n");
        check(p.lines.size() == 2 && p.lines.get(0).kind == HistoryDiff.Kind.REMOVE && p.lines.get(1).kind == HistoryDiff.Kind.ADD, "ending change is real edit");
        roundtrip(p, utf8("a\r\n"), utf8("a\n"));
        String boundaryUnicode = "x".repeat(4095) + "😀\u0000\u2028\u2029\\";
        p = compare(f, boundaryUnicode, boundaryUnicode);
        roundtrip(p, utf8(boundaryUnicode), utf8(boundaryUnicode));
        check(p.lines.get(0).display().contains("\\u0000") && p.lines.get(0).display().contains("\\u2028"), "controls do not split display lines");
        cases++;
    }
    private static void repeatedLinesAndOracle() throws Exception {
        Fixture f = new Fixture();
        roundtrip(compare(f, "a\na\nb\na\n", "a\nb\na\na\n"), utf8("a\na\nb\na\n"), utf8("a\nb\na\na\n"));
        Random random = new Random(1742);
        for (int test = 0; test < 60; test++) {
            List<String> a = new ArrayList<>(), b = new ArrayList<>();
            for (int i = random.nextInt(12); i > 0; i--) a.add("v" + random.nextInt(4));
            for (int i = random.nextInt(12); i > 0; i--) b.add("v" + random.nextInt(4));
            String left = joined(a), right = joined(b);
            HistoryDiff.Preview p = compare(f, left, right);
            check(p.complete, "small repeated diff complete");
            roundtrip(p, utf8(left), utf8(right));
            int common = 0;
            for (HistoryDiff.DisplayLine line : p.lines) if (line.kind == HistoryDiff.Kind.UNCHANGED) common++;
            check(common == oracle(a, b), "minimal edit script matches independent LCS oracle");
        }
        cases++;
    }
    private static void sizeBoundary() throws Exception {
        Fixture f = new Fixture();
        byte[] at = new byte[256 * 1024]; Arrays.fill(at, (byte)'g');
        ProtectionStore.Snapshot s = f.put(at);
        HistoryDiff.Preview p = DIFF.compare(s, s);
        check(p.complete && p.lines.size() == 1 && p.leftByteCount == at.length, "giant single line at cap");
        roundtrip(p, at, at);
        byte[] over = Arrays.copyOf(at, at.length + 1); over[over.length - 1] = 'x';
        p = DIFF.compare(s, f.put(over)); summary(p, "byte");
        check(p.leftByteCount == at.length && p.rightByteCount == over.length, "size summary exact counts");
        check(p.leftHash.equals(hash(at)) && p.rightHash.equals(hash(over)), "size summary exact hashes");
        cases++;
    }
    private static void logicalLineBoundary() throws Exception {
        Fixture f = new Fixture();
        String at = "\n".repeat(10000);
        HistoryDiff.Preview p = compare(f, at, at);
        summary(p, "output"); check(p.lines.size() == 2000, "10000 logical lines accepted before output cap");
        p = compare(f, at + "\n", ""); summary(p, "logical line");
        check(p.lines.isEmpty(), "10001 lines summary only");
        p = compare(f, "\r\n".repeat(10000), ""); summary(p, "output");
        check(p.lines.size() == 2000, "CRLF counts as one logical line");
        cases++;
    }
    private static void outputBoundary() throws Exception {
        Fixture f = new Fixture();
        String at = "a\n".repeat(2000);
        HistoryDiff.Preview p = compare(f, at, at);
        check(p.complete && p.lines.size() == 2000, "2000 output lines complete");
        p = compare(f, at + "a\n", at + "a\n"); summary(p, "output");
        check(p.lines.size() == 2000, "output bounded");
        cases++;
    }
    private static void workLimit() throws Exception {
        Fixture f = new Fixture();
        HistoryDiff.Preview p = compare(f, "a\n".repeat(1100), "b\n".repeat(1100));
        summary(p, "work"); check(p.lines.isEmpty(), "work cap never claims approximate operations");
        // Few giant unequal lines exercise character comparison accounting rather than cell count alone.
        p = compare(f, ("x".repeat(8000) + "a\n").repeat(25), ("x".repeat(8000) + "b\n").repeat(25));
        summary(p, "work");
        cases++;
    }
    private static void invalidUtf8() throws Exception {
        Fixture f = new Fixture(); ProtectionStore.Snapshot valid = f.put("ok");
        for (byte[] bytes : new byte[][] {
                {(byte)0xc0, (byte)0xaf}, {(byte)0xed, (byte)0xa0, (byte)0x80},
                {(byte)0xf4, (byte)0x90, (byte)0x80, (byte)0x80}, {(byte)0xe2, (byte)0x82}}) {
            ProtectionStore.Snapshot invalid = f.put(bytes);
            expectIo(() -> DIFF.compare(valid, invalid));
            expectIo(() -> DIFF.compare(invalid, valid));
        }
        byte[] large = new byte[256 * 1024 + 1]; Arrays.fill(large, (byte)'x'); large[large.length - 1] = (byte)0xff;
        ProtectionStore.Snapshot invalid = f.put(large);
        expectIo(() -> DIFF.compare(invalid, valid));
        expectIo(() -> DIFF.compare(null, valid));
        cases++;
    }
    private static void staleSource() throws Exception {
        Fixture f = new Fixture(); ProtectionStore.Snapshot a = f.put("before"), b = f.put("after");
        Path record = f.root.resolve(a.id + ".snapshot");
        byte[] bytes = Files.readAllBytes(record); bytes[bytes.length - 33] ^= 1;
        Files.write(record, bytes);
        final ProtectionStore.Snapshot changed = a;
        expectIo(() -> DIFF.compare(changed, b)); expectIo(() -> DIFF.compare(b, changed));
        Fixture g = new Fixture(); a = g.put("missing"); final ProtectionStore.Snapshot missing = a;
        Files.delete(g.root.resolve(a.id + ".snapshot"));
        expectIo(() -> DIFF.compare(missing, b));
        // Fresh validation is required even for a byte-cap summary.
        ProtectionStore.Snapshot huge = g.put("z".repeat(256 * 1024 + 1));
        expectIo(() -> DIFF.compare(huge, changed));
        Files.delete(g.root.resolve(huge.id + ".snapshot"));
        expectIo(() -> DIFF.compare(huge, b));
        cases++;
    }
    private static void changedValidMetadata() throws Exception {
        Fixture f = new Fixture();
        ProtectionStore.Snapshot stale = f.put("same bytes"), other = f.put("other");
        Path record = f.root.resolve(stale.id + ".snapshot");
        byte[] envelope = Files.readAllBytes(record);
        ByteBuffer data = ByteBuffer.wrap(envelope); data.position(13);
        for (int field = 0; field < 4; field++) skipString(data);
        data.getLong(); skipString(data); // revision, role
        int timestamp = data.position();
        data.putLong(timestamp, data.getLong(timestamp) + 1);
        byte[] checksum = MessageDigest.getInstance("SHA-256").digest(Arrays.copyOf(envelope, envelope.length - 32));
        System.arraycopy(checksum, 0, envelope, envelope.length - 32, checksum.length);
        Files.write(record, envelope);
        // A newly read handle validates the changed but internally valid record.
        ProtectionStore.Snapshot fresh = f.store.snapshot(stale.id);
        check(Arrays.equals(fresh.bytes(), utf8("same bytes")), "changed record remains internally valid");
        expectIo(() -> DIFF.compare(stale, other));
        expectIo(() -> DIFF.compare(other, stale));
        HistoryDiff.Preview p = DIFF.compare(fresh, other);
        roundtrip(p, utf8("same bytes"), utf8("other"));
        cases++;
    }
    private static void skipString(ByteBuffer buffer) {
        int length = buffer.getInt(); if (length >= 0) buffer.position(buffer.position() + length);
    }
    private static void memory() throws Exception {
        byte[] headroom = new byte[32 * 1024 * 1024];
        Arrays.fill(headroom, (byte)0x5a);
        Fixture f = new Fixture();
        ProtectionStore.Snapshot a = largeSnapshot(f, (byte)'a'), b = largeSnapshot(f, (byte)'b');
        for (int i = 0; i < 6; i++) {
            HistoryDiff.Preview p = DIFF.compare(a, b); summary(p, "byte");
            check(p.leftByteCount == ProtectionStore.MAX_DOCUMENT_BYTES && p.rightByteCount == ProtectionStore.MAX_DOCUMENT_BYTES, "large counts exact");
            check(p.leftHash.equals(a.hash) && p.rightHash.equals(b.hash), "fresh large hashes exact");
        }
        check(headroom[headroom.length - 1] == 0x5a, "32MiB headroom retained");
        System.out.println("HistoryDiff constrained heap: six paired 8MiB comparisons, 128MiB heap / 32MiB retained headroom PASS");
    }
    private static ProtectionStore.Snapshot largeSnapshot(Fixture f, byte value) throws IOException {
        byte[] bytes = new byte[ProtectionStore.MAX_DOCUMENT_BYTES]; Arrays.fill(bytes, value); return f.put(bytes);
    }
    private static HistoryDiff.Preview compare(Fixture f, String a, String b) throws IOException { return DIFF.compare(f.put(a), f.put(b)); }
    private static void roundtrip(HistoryDiff.Preview p, byte[] a, byte[] b) throws Exception {
        check(p.complete, "roundtrip requires complete preview");
        StringBuilder left = new StringBuilder(), right = new StringBuilder();
        for (HistoryDiff.DisplayLine line : p.lines) {
            if (line.kind != HistoryDiff.Kind.ADD) left.append(line.text).append(line.ending.bytes);
            if (line.kind != HistoryDiff.Kind.REMOVE) right.append(line.text).append(line.ending.bytes);
        }
        check(Arrays.equals(a, utf8(left.toString())), "left exact byte reconstruction");
        check(Arrays.equals(b, utf8(right.toString())), "right exact byte reconstruction");
        check(p.leftByteCount == a.length && p.rightByteCount == b.length, "exact preview byte counts");
        check(p.leftHash.equals(hash(a)) && p.rightHash.equals(hash(b)), "exact preview hashes");
    }
    private static void summary(HistoryDiff.Preview p, String limit) {
        check(!p.complete && p.reason.startsWith("Incomplete preview:") && p.reason.contains(limit), "explicit " + limit + " summary");
        check(p.lines.size() <= 2000, "bounded summary output");
    }
    private static int oracle(List<String> a, List<String> b) {
        int[][] dp = new int[a.size() + 1][b.size() + 1];
        for (int i = 1; i <= a.size(); i++) for (int j = 1; j <= b.size(); j++)
            dp[i][j] = a.get(i - 1).equals(b.get(j - 1)) ? dp[i - 1][j - 1] + 1 : Math.max(dp[i - 1][j], dp[i][j - 1]);
        return dp[a.size()][b.size()];
    }
    private static String joined(List<String> lines) { return lines.isEmpty() ? "" : String.join("\n", lines) + "\n"; }
    private static byte[] utf8(String s) { return s.getBytes(StandardCharsets.UTF_8); }
    private static String hash(byte[] bytes) throws Exception {
        StringBuilder s = new StringBuilder(); for (byte b : MessageDigest.getInstance("SHA-256").digest(bytes)) s.append(String.format("%02x", b & 255)); return s.toString();
    }
    private interface Action { void run() throws Exception; }
    private static void expectIo(Action action) throws Exception {
        try { action.run(); throw new AssertionError("expected IOException"); } catch (IOException expected) { assertions++; }
    }
    private static void expectUnsupported(Action action) throws Exception {
        try { action.run(); throw new AssertionError("expected immutable output"); } catch (UnsupportedOperationException expected) { assertions++; }
    }
    private static void check(boolean yes, String message) { assertions++; if (!yes) throw new AssertionError(message); }
}
