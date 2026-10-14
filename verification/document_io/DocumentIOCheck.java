package com.example.markdowndesk;

import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;

/** Runs production DocumentIO unchanged, against explicitly simulated providers. */
public final class DocumentIOCheck {
    private static final Uri URI = Uri.parse("content://host-fixture/document/1");
    private static int passed, failed;
    private interface Check { void run() throws Exception; }
    private static void test(String name, Check check) {
        try { check.run(); passed++; System.out.println("PASS " + name); }
        catch (Throwable failure) { failed++; System.out.println("FAIL " + name + ": " + failure); }
    }
    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
    private static void bytes(byte[] expected, byte[] actual) {
        require(Arrays.equals(expected, actual), "byte mismatch: expected length=" + expected.length + ", actual=" + actual.length);
    }
    private static void throwsType(Class<? extends Throwable> type, Check check) throws Exception {
        try { check.run(); } catch (Throwable failure) {
            require(type.isInstance(failure), "expected " + type.getSimpleName() + ", got " + failure);
            return;
        }
        throw new AssertionError("expected " + type.getSimpleName());
    }
    private static byte[] utf8(String value) { return value.getBytes(StandardCharsets.UTF_8); }
    private static byte[] bom(byte[] value) {
        byte[] result = new byte[value.length + 3];
        result[0] = (byte) 0xef; result[1] = (byte) 0xbb; result[2] = (byte) 0xbf;
        System.arraycopy(value, 0, result, 3, value.length); return result;
    }
    private static final class Row implements Cursor {
        final Map<String, Object> values;
        final List<String> columns;
        boolean closed, hasRow = true;
        Row(Map<String, Object> values) { this.values = values; columns = new ArrayList<>(values.keySet()); }
        public boolean moveToFirst() { return hasRow; }
        public int getColumnIndex(String name) { return columns.indexOf(name); }
        private Object get(int index) { return values.get(columns.get(index)); }
        public boolean isNull(int index) { return get(index) == null; }
        public String getString(int index) { return (String) get(index); }
        public long getLong(int index) { return ((Number) get(index)).longValue(); }
        public int getInt(int index) { return ((Number) get(index)).intValue(); }
        public void close() { closed = true; }
    }
    private static final class Provider extends ContentResolver {
        byte[] stored;
        boolean nullInput, nullOutput, revoked, descriptorThrows, inputClosed, outputClosed;
        boolean inputFails, flushFails, outputCloseFails, queryFails, corruptWrite;
        int failAfter = -1, inputOpens, outputOpens, descriptorOpens;
        String outputMode, descriptorMode;
        Row row;
        File file;
        ParcelFileDescriptor lastDescriptor;
        Provider(byte[] initial) { stored = initial.clone(); }
        private void grant() { if (revoked) throw new SecurityException("simulated revoked grant"); }
        public InputStream openInputStream(Uri uri) throws FileNotFoundException {
            grant(); inputOpens++;
            if (nullInput) return null;
            try {
                byte[] data = file == null ? stored : Files.readAllBytes(file.toPath());
                return new FilterInputStream(new ByteArrayInputStream(data)) {
                    public int read(byte[] buffer, int offset, int length) throws IOException {
                        if (inputFails) throw new IOException("simulated input failure");
                        return super.read(buffer, offset, Math.min(length, 7));
                    }
                    public void close() { inputClosed = true; }
                };
            } catch (IOException e) { throw new FileNotFoundException(e.toString()); }
        }
        public OutputStream openOutputStream(Uri uri, String mode) {
            grant(); outputOpens++; outputMode = mode;
            if (nullOutput) return null;
            stored = new byte[0]; // Model a destructive provider's documented "wt" open.
            return new OutputStream() {
                final ByteArrayOutputStream sink = new ByteArrayOutputStream();
                public void write(int value) throws IOException {
                    if (failAfter >= 0 && sink.size() >= failAfter) throw new IOException("simulated out of storage after " + failAfter + " bytes");
                    sink.write(value); stored = sink.toByteArray();
                }
                public void write(byte[] data, int off, int len) throws IOException {
                    int accepted = failAfter < 0 ? len : Math.min(len, Math.max(0, failAfter - sink.size()));
                    sink.write(data, off, accepted); stored = sink.toByteArray();
                    if (accepted != len) throw new IOException("simulated out of storage after " + failAfter + " bytes");
                }
                public void flush() throws IOException { if (flushFails) throw new IOException("simulated flush failure"); }
                public void close() throws IOException {
                    outputClosed = true;
                    if (corruptWrite) stored = utf8("provider altered saved text");
                    if (outputCloseFails) throw new IOException("simulated close failure");
                }
            };
        }
        public ParcelFileDescriptor openFileDescriptor(Uri uri, String mode) throws FileNotFoundException {
            grant(); descriptorOpens++; descriptorMode = mode;
            if (descriptorThrows) throw new FileNotFoundException("simulated descriptor unsupported");
            if (file == null) return null;
            try { return lastDescriptor = new ParcelFileDescriptor(file, "rwt".equals(mode)); }
            catch (IOException e) { throw new FileNotFoundException(e.toString()); }
        }
        public Cursor query(Uri uri, String[] projection, String selection, String[] args, String order) {
            grant(); if (queryFails) throw new IllegalStateException("simulated query failure"); return row;
        }
    }
    private static void roundTrip(String text, boolean withBom) throws Exception {
        byte[] expected = withBom ? bom(utf8(text)) : utf8(text);
        Provider provider = new Provider(expected);
        DocumentIO.ReadResult result = DocumentIO.read(provider, URI);
        require(text.equals(result.text), "decoded text changed");
        require(result.utf8Bom == withBom, "BOM flag changed");
        bytes(expected, result.sourceBytes);
        bytes(expected, DocumentIO.encode(result.text, result.utf8Bom));
        bytes(expected, provider.stored);
        require(provider.inputClosed, "input stream not closed");
        require(provider.outputOpens == 0 && provider.descriptorOpens == 0, "read attempted write");
    }
    private static void preservation() {
        test("Unicode including supplementary characters", () -> roundTrip("# café\n日本語 Ελληνικά 🧭 e\u0301\n", false));
        test("CRLF leading trailing whitespace and final newline", () -> roundTrip(" \t# Title\r\n\r\nbody  \r\n", false));
        test("mixed CRLF LF and bare CR", () -> roundTrip("one\r\ntwo\nthree\rfour", false));
        test("UTF8 BOM fidelity", () -> roundTrip("☕\r\n", true));
        test("empty document", () -> roundTrip("", false));
        test("BOM only document", () -> roundTrip("", true));
        test("embedded BOM remains content", () -> roundTrip("a\ufeffb", false));
        test("encode independent UTF8 golden bytes", () -> bytes(new byte[]{0x41, (byte)0xc3, (byte)0xa9, (byte)0xf0, (byte)0x9f, (byte)0x98, (byte)0x80, 13, 10}, DocumentIO.encode("Aé😀\r\n", false)));
        test("strict capture preserves independent UTF8 BOM bytes", () -> bytes(bom(new byte[]{0x41, (byte)0xc3, (byte)0xa9, (byte)0xf0, (byte)0x9f, (byte)0x98, (byte)0x80, 13, 10}), DocumentIO.encodeStrict("Aé😀\r\n", true)));
        test("strict capture rejects unmatched high surrogate", () -> throwsType(IOException.class, () -> DocumentIO.encodeStrict("draft\ud800", false)));
        test("strict capture rejects unmatched low surrogate", () -> throwsType(IOException.class, () -> DocumentIO.encodeStrict("draft\udc00", true)));
        byte[][] malformed = {{(byte)0xc3, 0x28}, {(byte)0xe2, (byte)0x82}, {(byte)0xc0, (byte)0xaf}, {(byte)0xed, (byte)0xa0, (byte)0x80}, {(byte)0xff}, {(byte)0xef, (byte)0xbb}};
        for (int i = 0; i < malformed.length; i++) {
            final byte[] invalid = malformed[i];
            test("invalid UTF8 fixture " + i, () -> {
                Provider p = new Provider(invalid);
                throwsType(IOException.class, () -> DocumentIO.read(p, URI));
                bytes(invalid, p.stored); require(p.inputClosed, "invalid input not closed");
                require(p.outputOpens == 0, "invalid input attempted write");
            });
        }
        test("null input rejected", () -> { Provider p = new Provider(utf8("original")); p.nullInput = true; throwsType(FileNotFoundException.class, () -> DocumentIO.read(p, URI)); bytes(utf8("original"), p.stored); });
        test("read provider IOException closes input", () -> {
            Provider p = new Provider(utf8("original")); p.inputFails = true;
            throwsType(IOException.class, () -> DocumentIO.read(p, URI));
            bytes(utf8("original"), p.stored); require(p.inputClosed, "failing input not closed");
        });
    }
    private static void remaining() {
        test("8 MiB exact boundary read", () -> {
            byte[] original = new byte[8 * 1024 * 1024]; Arrays.fill(original, (byte)'x');
            Provider p = new Provider(original);
            DocumentIO.ReadResult r = DocumentIO.read(p, URI);
            bytes(original, r.sourceBytes); require(r.text.length() == original.length, "boundary text length changed");
        });
        test("8 MiB plus one rejected unchanged", () -> {
            byte[] original = new byte[8 * 1024 * 1024 + 1]; Arrays.fill(original, (byte)'x');
            Provider p = new Provider(original);
            throwsType(IOException.class, () -> DocumentIO.read(p, URI));
            bytes(original, p.stored); require(p.inputClosed, "oversized input not closed");
        });
        test("BOM counts toward byte boundary", () -> {
            byte[] payload = new byte[8 * 1024 * 1024 - 3]; Arrays.fill(payload, (byte)'a');
            DocumentIO.ReadResult r = DocumentIO.read(new Provider(bom(payload)), URI);
            require(r.utf8Bom && r.text.length() == payload.length, "BOM byte boundary wrong");
            throwsType(IOException.class, () -> DocumentIO.read(new Provider(bom(new byte[payload.length + 1])), URI));
        });
        test("8 MiB exact boundary write and reopen", () -> {
            byte[] payload = new byte[8 * 1024 * 1024]; Arrays.fill(payload, (byte)'a');
            Provider p = new Provider(utf8("original")); DocumentIO.write(p, URI, payload);
            bytes(payload, DocumentIO.read(p, URI).sourceBytes);
        });
        test("8 MiB UTF8 BOM-inclusive write boundary", () -> {
            String text = "é".repeat(4 * 1024 * 1024 - 2) + "a";
            byte[] encoded = DocumentIO.encode(text, true);
            require(encoded.length == 8 * 1024 * 1024, "independent UTF8 boundary arithmetic wrong");
            Provider p = new Provider(utf8("original")); DocumentIO.write(p, URI, encoded);
            DocumentIO.ReadResult r = DocumentIO.read(p, URI);
            require(text.equals(r.text) && r.utf8Bom, "UTF8 BOM write boundary changed"); bytes(bom(utf8(text)), r.sourceBytes);
        });
        test("fallback edit save reopen with BOM CRLF", () -> {
            Provider p = new Provider(bom(utf8("# Before\r\n")));
            DocumentIO.ReadResult before = DocumentIO.read(p, URI);
            byte[] edited = DocumentIO.encode(before.text.replace("Before", "Après 🧭"), before.utf8Bom);
            byte[] expected = bom(utf8("# Après 🧭\r\n"));
            DocumentIO.write(p, URI, edited);
            bytes(expected, p.stored); bytes(expected, DocumentIO.read(p, URI).sourceBytes);
            require(p.outputClosed && "wt".equals(p.outputMode) && "rwt".equals(p.descriptorMode), "fallback contract wrong");
        });
        test("fallback truncates shorter successful save", () -> {
            Provider p = new Provider(utf8("original much longer"));
            DocumentIO.write(p, URI, utf8("new")); bytes(utf8("new"), DocumentIO.read(p, URI).sourceBytes);
        });
        test("unsupported descriptor falls back", () -> {
            Provider p = new Provider(utf8("old")); p.descriptorThrows = true;
            DocumentIO.write(p, URI, utf8("saved")); bytes(utf8("saved"), p.stored);
            require(p.outputOpens == 1, "fallback not attempted");
        });
        test("null output rejected", () -> {
            Provider p = new Provider(utf8("original")); p.nullOutput = true;
            throwsType(FileNotFoundException.class, () -> DocumentIO.write(p, URI, utf8("edited")));
            bytes(utf8("original"), p.stored);
        });
        test("revoked read rejects and preserves", () -> {
            Provider p = new Provider(utf8("original")); p.revoked = true;
            throwsType(SecurityException.class, () -> DocumentIO.read(p, URI)); bytes(utf8("original"), p.stored);
        });
        test("revoked write rejects and preserves", () -> {
            Provider p = new Provider(utf8("original")); p.revoked = true;
            throwsType(SecurityException.class, () -> DocumentIO.write(p, URI, utf8("edited"))); bytes(utf8("original"), p.stored);
        });
        test("mid-write error propagates closes stream", () -> {
            Provider p = new Provider(utf8("original")); p.failAfter = 2;
            throwsType(IOException.class, () -> DocumentIO.write(p, URI, utf8("replacement")));
            require(p.outputClosed, "partial write stream not closed");
            // Original preservation has its own intentionally failing safety assertion.
        });
        test("flush error propagates closes stream", () -> {
            Provider p = new Provider(utf8("original")); p.flushFails = true;
            throwsType(IOException.class, () -> DocumentIO.write(p, URI, utf8("edited"))); require(p.outputClosed, "flush error stream not closed");
        });
        test("close error propagates", () -> {
            Provider p = new Provider(utf8("original")); p.outputCloseFails = true;
            throwsType(IOException.class, () -> DocumentIO.write(p, URI, utf8("edited")));
        });
        test("provider corruption visible in reopened bytes", () -> {
            Provider p = new Provider(utf8("original")); p.corruptWrite = true;
            byte[] expected = utf8("edited"); DocumentIO.write(p, URI, expected);
            require(!Arrays.equals(expected, DocumentIO.read(p, URI).sourceBytes), "provider corruption hidden on read-back");
            bytes(utf8("provider altered saved text"), DocumentIO.read(p, URI).sourceBytes);
            // DocumentIO.write does not verify. MainActivity read-back UI/recovery handling is not executed here.
        });
        test("read-back revoked after successful write rejects", () -> {
            Provider p = new Provider(utf8("original")); DocumentIO.write(p, URI, utf8("edited")); p.revoked = true;
            throwsType(SecurityException.class, () -> DocumentIO.read(p, URI));
        });
        test("real host descriptor save truncates syncs reopens", () -> {
            File path = File.createTempFile("document-io-", ".md");
            try {
                Files.write(path.toPath(), utf8("old contents longer than replacement"));
                Provider p = new Provider(new byte[0]); p.file = path;
                DocumentIO.write(p, URI, bom(utf8("new 🧭\r\n")));
                bytes(bom(utf8("new 🧭\r\n")), Files.readAllBytes(path.toPath()));
                bytes(bom(utf8("new 🧭\r\n")), DocumentIO.read(p, URI).sourceBytes);
                require(p.lastDescriptor.closed && p.outputOpens == 0, "descriptor path leaked or fell back");
            } finally { Files.deleteIfExists(path.toPath()); }
        });
        test("metadata provider values cursor closed", () -> {
            Provider p = new Provider(new byte[0]);
            p.row = new Row(Map.of("_display_name", "Café.md", "_size", 19L, "last_modified", 123L, "flags", 2));
            DocumentIO.Metadata m = DocumentIO.metadata(p, URI);
            require("Café.md".equals(m.displayName) && m.size == 19L && m.lastModified == 123L && m.writable, "metadata changed");
            require(p.row.closed && p.descriptorOpens == 0, "metadata cursor leaked or redundant descriptor");
        });
        test("metadata absent null columns defaults with denied descriptor", () -> {
            Provider p = new Provider(new byte[0]); p.descriptorThrows = true;
            Map<String,Object> values = new HashMap<>(); values.put("_display_name", null); values.put("_size", null); values.put("flags", null);
            p.row = new Row(values);
            DocumentIO.Metadata m = DocumentIO.metadata(p, URI);
            require("Document.md".equals(m.displayName) && m.size == -1 && m.lastModified == -1 && !m.writable, "metadata defaults changed"); require(p.row.closed, "cursor leaked");
        });
        test("metadata flags without write bit require descriptor", () -> {
            Provider p = new Provider(new byte[0]); p.descriptorThrows = true;
            p.row = new Row(Map.of("flags", 4));
            require(!DocumentIO.metadata(p, URI).writable, "nonwrite flag treated as write support");
            require(p.row.closed && p.descriptorOpens == 1, "nonwrite flag skipped descriptor probe");
        });
        test("metadata query failure denied descriptor read only", () -> {
            Provider p = new Provider(new byte[0]); p.queryFails = true; p.descriptorThrows = true;
            require(!DocumentIO.metadata(p, URI).writable, "denied descriptor marked writable");
        });
        test("metadata revoked permissions read only", () -> {
            Provider p = new Provider(new byte[0]); p.revoked = true;
            require(!DocumentIO.metadata(p, URI).writable, "revoked grant marked writable");
        });
        test("metadata real rw descriptor nontruncating closed", () -> {
            File path = File.createTempFile("document-metadata-", ".md");
            try {
                Files.write(path.toPath(), utf8("original")); Provider p = new Provider(new byte[0]); p.file = path;
                require(DocumentIO.metadata(p, URI).writable, "real descriptor not writable");
                require(p.lastDescriptor.closed && "rw".equals(p.descriptorMode), "metadata descriptor leaked or wrong mode");
                bytes(utf8("original"), Files.readAllBytes(path.toPath()));
            } finally { Files.deleteIfExists(path.toPath()); }
        });
    }
    private static void requireOversizedWriteRejected(byte[] oversized) throws Exception {
        byte[] original = utf8("original document"); Provider p = new Provider(original);
        boolean rejected = false;
        try { DocumentIO.write(p, URI, oversized); } catch (IOException expected) { rejected = true; }
        require(rejected && p.descriptorOpens == 0 && p.outputOpens == 0 && Arrays.equals(original, p.stored),
            "oversized write must reject before provider call: rejected=" + rejected + ", descriptor opens=" + p.descriptorOpens + ", output opens=" + p.outputOpens + ", retained length=" + p.stored.length);
    }
    private static void safety() {
        test("SAFETY oversized write must reject before provider access", () -> {
            byte[] oversized = new byte[8 * 1024 * 1024 + 1]; Arrays.fill(oversized, (byte)'a');
            requireOversizedWriteRejected(oversized);
        });
        test("SAFETY UTF8 BOM oversized write must reject before provider access", () -> {
            byte[] oversized = DocumentIO.encode("é".repeat(4 * 1024 * 1024 - 1), true);
            require(oversized.length == 8 * 1024 * 1024 + 1, "independent BOM boundary arithmetic wrong");
            requireOversizedWriteRejected(oversized);
        });
        test("SAFETY null descriptor must not mark metadata writable", () -> {
            Provider p = new Provider(utf8("original"));
            require(!DocumentIO.metadata(p, URI).writable, "null rw descriptor reported writable=true");
        });
        test("SAFETY failed truncated output must preserve original", () -> {
            byte[] original = utf8("original document"); Provider p = new Provider(original); p.failAfter = 2;
            throwsType(IOException.class, () -> DocumentIO.write(p, URI, utf8("replacement")));
            require(Arrays.equals(original, p.stored), "original destroyed: retained bytes=" + new String(p.stored, StandardCharsets.UTF_8));
        });
        test("SAFETY rejected write immediately after truncate must preserve original", () -> {
            byte[] original = utf8("original document"); Provider p = new Provider(original); p.failAfter = 0;
            throwsType(IOException.class, () -> DocumentIO.write(p, URI, utf8("replacement")));
            require(Arrays.equals(original, p.stored), "original destroyed: retained length=" + p.stored.length);
        });
    }
    public static void main(String[] args) {
        if (args.length == 0) { preservation(); remaining(); }
        else if (args.length == 1 && "--safety".equals(args[0])) safety();
        else throw new IllegalArgumentException("usage: DocumentIOCheck [--safety]");
        System.out.println("DocumentIO host fixtures: " + passed + " passed, " + failed + " failed");
        if (failed != 0) System.exit(1);
    }
}
