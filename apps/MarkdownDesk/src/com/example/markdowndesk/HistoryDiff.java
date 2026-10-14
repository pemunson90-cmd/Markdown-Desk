package com.example.markdowndesk;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CoderResult;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Read-only, exact line diff over freshly validated snapshots. No provider or stored-byte writes. */
public final class HistoryDiff {
    public static final int MAX_PREVIEW_BYTES = 256 * 1024;
    public static final int MAX_LOGICAL_LINES = 10000;
    public static final int MAX_WORK_UNITS = 1000000;
    public static final int MAX_OUTPUT_LINES = 2000;

    public enum Kind { UNCHANGED, REMOVE, ADD }
    public enum Ending {
        LF("\n"), CRLF("\r\n"), CR("\r"), NONE("");
        /** Exact original line terminator; no newline normalization. */
        public final String bytes;
        Ending(String bytes) { this.bytes = bytes; }
    }

    public static final class DisplayLine {
        public final Kind kind;
        /** Exact decoded content, including any BOM. Does not include the line terminator. */
        public final String text;
        public final Ending ending;
        private DisplayLine(Kind kind, Line line) {
            this.kind = kind; this.text = line.text; this.ending = line.ending;
        }
        /** One visible display line: escape controls and BOM; keep valid Unicode code points intact. */
        public String display() {
            StringBuilder visible = new StringBuilder();
            visible.append(kind == Kind.ADD ? "+ " : kind == Kind.REMOVE ? "- " : "  ");
            for (int i = 0; i < text.length(); i++) {
                char c = text.charAt(i);
                if (c == '\\') visible.append("\\\\");
                else if (c == '\t') visible.append("\\t");
                else if (c < 32 || (c >= 127 && c <= 159) || c == '\ufeff' || c == '\u2028' || c == '\u2029') {
                    visible.append("\\u");
                    for (int shift = 12; shift >= 0; shift -= 4) visible.append("0123456789ABCDEF".charAt((c >>> shift) & 15));
                } else visible.append(c);
            }
            return visible.append(" [").append(ending.name()).append(']').toString();
        }
    }

    public static final class Preview {
        public final int leftByteCount, rightByteCount;
        public final String leftHash, rightHash;
        public final boolean leftBom, rightBom, complete;
        /** Empty for complete output; otherwise explicitly states the limit and available full-content paths. */
        public final String reason;
        public final List<DisplayLine> lines;
        private Preview(Input left, Input right, boolean complete, String reason, List<DisplayLine> lines) {
            this.leftByteCount = left.byteCount; this.rightByteCount = right.byteCount;
            this.leftHash = left.hash; this.rightHash = right.hash;
            this.leftBom = left.bom; this.rightBom = right.bom; this.complete = complete;
            this.reason = complete ? "" : "Incomplete preview: " + reason
                    + ". Restore to a new copy or export for full content.";
            this.lines = Collections.unmodifiableList(new ArrayList<>(lines));
        }
    }

    /** Invalid/missing/changed records or malformed UTF-8 throw, including when a preview cap is exceeded. */
    public Preview compare(ProtectionStore.Snapshot left, ProtectionStore.Snapshot right) throws IOException {
        if (left == null || right == null) throw new IOException("Missing diff snapshot");
        // Separate calls release a large validated payload before loading the other side. Only capped
        // text is retained. Snapshot.bytes() validates integrity and the handle's complete provenance.
        Input a = read(left);
        Input b = read(right);
        if (a.limit != null || b.limit != null) {
            String limit = (a.limit == null ? "" : "left " + a.limit)
                    + (a.limit != null && b.limit != null ? "; " : "")
                    + (b.limit == null ? "" : "right " + b.limit);
            return new Preview(a, b, false, limit, Collections.emptyList());
        }
        Work work = new Work();
        Output output = new Output();
        try {
            diff(a.lines, b.lines, work, output);
            return new Preview(a, b, true, "", output.lines);
        } catch (WorkLimit limit) {
            // No partial or approximate script is presented as a real diff when alignment was bounded.
            return new Preview(a, b, false, "algorithm work limit (" + MAX_WORK_UNITS + " units)", Collections.emptyList());
        } catch (OutputLimit limit) {
            return new Preview(a, b, false, "output line limit (" + MAX_OUTPUT_LINES + " lines)", output.lines);
        }
    }

    private static final class Line {
        final String text;
        final Ending ending;
        Line(String text, Ending ending) { this.text = text; this.ending = ending; }
    }
    private static final class Input {
        final int byteCount;
        final String hash, limit;
        final boolean bom;
        final List<Line> lines;
        Input(int byteCount, String hash, boolean bom, String limit, List<Line> lines) {
            this.byteCount = byteCount; this.hash = hash; this.bom = bom; this.limit = limit; this.lines = lines;
        }
    }
    private static Input read(ProtectionStore.Snapshot snapshot) throws IOException {
        byte[] bytes = snapshot.bytes();
        boolean bom = bytes.length >= 3 && bytes[0] == (byte)0xef && bytes[1] == (byte)0xbb && bytes[2] == (byte)0xbf;
        // Validate even over-cap bytes with a fixed-size character scratch buffer, never an 8MiB String.
        validateUtf8(bytes);
        if (bytes.length > MAX_PREVIEW_BYTES)
            return new Input(bytes.length, snapshot.hash, bom, "byte limit (" + MAX_PREVIEW_BYTES + " bytes)", Collections.emptyList());
        String text = StandardCharsets.UTF_8.decode(ByteBuffer.wrap(bytes)).toString();
        List<Line> lines = new ArrayList<>();
        int start = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c != '\n' && c != '\r') continue;
            int end = i;
            Ending ending = c == '\n' ? Ending.LF : Ending.CR;
            if (c == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n') { ending = Ending.CRLF; i++; }
            if (lines.size() == MAX_LOGICAL_LINES) return lineLimit(bytes.length, snapshot.hash, bom);
            lines.add(new Line(text.substring(start, end), ending)); start = i + 1;
        }
        // A trailing newline completes the preceding line; it does not add a phantom empty line.
        if (start < text.length()) {
            if (lines.size() == MAX_LOGICAL_LINES) return lineLimit(bytes.length, snapshot.hash, bom);
            lines.add(new Line(text.substring(start), Ending.NONE));
        }
        return new Input(bytes.length, snapshot.hash, bom, null, lines);
    }
    private static Input lineLimit(int bytes, String hash, boolean bom) {
        return new Input(bytes, hash, bom, "logical line limit (" + MAX_LOGICAL_LINES + " lines)", Collections.emptyList());
    }
    private static void validateUtf8(byte[] bytes) throws IOException {
        CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        ByteBuffer input = ByteBuffer.wrap(bytes);
        CharBuffer scratch = CharBuffer.allocate(4096);
        try {
            CoderResult result;
            do {
                scratch.clear(); result = decoder.decode(input, scratch, true);
                if (result.isError()) result.throwException();
            } while (result.isOverflow());
            scratch.clear(); result = decoder.flush(scratch);
            if (result.isError()) result.throwException();
        } catch (CharacterCodingException invalid) { throw new IOException("Malformed UTF-8 diff snapshot", invalid); }
    }

    private static final class WorkLimit extends Exception { private static final long serialVersionUID = 1L; }
    private static final class OutputLimit extends Exception { private static final long serialVersionUID = 1L; }
    private static final class Work {
        int remaining = MAX_WORK_UNITS;
        void spend(int units) throws WorkLimit {
            if (units > remaining) throw new WorkLimit();
            remaining -= units;
        }
        boolean same(Line a, Line b) throws WorkLimit {
            // Charge a cell/control unit plus an upper bound for String.equals character work.
            spend(1 + Math.max(a.text.length(), b.text.length()));
            return a.ending == b.ending && a.text.equals(b.text);
        }
    }
    private static final class Output {
        final List<DisplayLine> lines = new ArrayList<>();
        void add(Kind kind, Line line) throws OutputLimit {
            if (lines.size() == MAX_OUTPUT_LINES) throw new OutputLimit();
            lines.add(new DisplayLine(kind, line));
        }
    }
    /** Exact LCS with common-edge trimming. The bounded grid never exceeds ~4MiB. */
    private static void diff(List<Line> a, List<Line> b, Work work, Output out) throws WorkLimit, OutputLimit {
        int prefix = 0, n = a.size(), m = b.size();
        while (prefix < n && prefix < m && work.same(a.get(prefix), b.get(prefix))) prefix++;
        int endA = n, endB = m;
        while (endA > prefix && endB > prefix && work.same(a.get(endA - 1), b.get(endB - 1))) { endA--; endB--; }
        int rows = endA - prefix, columns = endB - prefix;
        // At least one unit is spent per grid cell. Reject before allocating a grid that cannot finish.
        if ((long)rows * columns > work.remaining) throw new WorkLimit();
        int[] lcs = rows == 0 || columns == 0 ? null : new int[(rows + 1) * (columns + 1)];
        int stride = columns + 1;
        for (int i = rows - 1; i >= 0 && columns > 0; i--) {
            for (int j = columns - 1; j >= 0; j--) {
                int cell = i * stride + j;
                lcs[cell] = work.same(a.get(prefix + i), b.get(prefix + j))
                        ? 1 + lcs[cell + stride + 1] : Math.max(lcs[cell + stride], lcs[cell + 1]);
            }
        }
        for (int i = 0; i < prefix; i++) { work.spend(1); out.add(Kind.UNCHANGED, a.get(i)); }
        int i = 0, j = 0;
        while (i < rows || j < columns) {
            work.spend(1);
            if (i < rows && j < columns && work.same(a.get(prefix + i), b.get(prefix + j))) {
                out.add(Kind.UNCHANGED, a.get(prefix + i)); i++; j++;
            } else if (i < rows && (j == columns || lcs[(i + 1) * stride + j] >= lcs[i * stride + j + 1])) {
                out.add(Kind.REMOVE, a.get(prefix + i++));
            } else out.add(Kind.ADD, b.get(prefix + j++));
        }
        for (int k = endA; k < n; k++) { work.spend(1); out.add(Kind.UNCHANGED, a.get(k)); }
    }
}
