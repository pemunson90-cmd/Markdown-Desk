import com.example.markdowndesk.workspace.archive.ArchiveSearchOffsets;
import com.example.markdowndesk.workspace.archive.ArchiveUnicode61Phrase;
import com.example.markdowndesk.workspace.archive.ArchiveUnicode61Phrase.Span;

/** UTF-16 source spans across the exact segment breaks missed by FTS4 rows. */
public final class ArchiveUnicode61PhraseCheck {
    public static void main(String[] args) {
        StringBuilder first = new StringBuilder("prefix");
        StringBuilder query = new StringBuilder();
        for (int i = 0; i < 40; i++) {
            first.append(" word").append(i);
            if (i >= 10) query.append("word").append(i).append(' ');
        }
        String longPhrase = query.append("ending").toString();
        if (longPhrase.length() != 216) throw new AssertionError("Wrong maintenance fixture");
        found(longPhrase, longPhrase, first.toString(), " ending suffix");
        found("foo bar", "foo-bar", "before fo", "o-", "bar after");
        found("cafe", "café", "😀 c", "afé.");
        found("cafe", "cafe\u0301", "😀 cafe", "\u0301.");
        found("foo bar", "foo" + "-".repeat(8192) + "bar",
                "prefix foo", "-".repeat(4096), "-".repeat(4096), "bar");
        found("foo foo bar", "foo foo bar", "foo foo ", "foo b", "ar");
        found("foo bar*", "foo bartender", "foo bar", "tender");
        found("^foo bar", "foo bar", "  fo", "o b", "ar");
        found("foo\"bar", "bar", "bar before ", "foo after");
        found("𐐨", "𐐀", "prefix \uD801", "\uDC00 suffix");
        found("c*", "c" + "x".repeat(5000), "c" + "x".repeat(2500), "x".repeat(2500));
        found("a b", "a\uFFFFb", "a\uFFFF", "b");
        absent("foo ^bar", "foo ", "bar");
        absent("^foo", "prefix ", "foo");
        absent("foo\"\"bar", "foo ", "bar");
        absent("foo bar", "foo ", "other ", "bar");
        absent("c", "c", "x".repeat(5000));
        absent("o", "ộ"); // unicode61's default preserves multi-diacritic letters.
        absent("a", "ab");
        absent("a b", "a", new String(Character.toChars(0x1FFFF)) + "b");

        // Values emitted by SQLite SELECT offsets(t) for the given body/query.
        nativeSpan("😀 foo-bar then foo-bar", "foo bar",
                "0 0 5 3 0 1 9 3 0 0 18 3 0 1 22 3", 3, 10);
        nativeSpan("a a a", "a a", "0 0 0 1 0 0 2 1 0 1 2 1 0 1 4 1", 0, 3);
        nativeSpan("😀 café cafe", "cafe", "0 0 5 5 0 0 11 4", 3, 7);
        nativeSpan("bar before foo after", "foo\"bar", "0 1 0 3 0 0 11 3", 11, 14);
        System.out.println("PASS: streaming Unicode61 phrase and source-span fixtures");
    }

    private static void found(String query, String expected, String... segments) {
        Span span = find(query, segments);
        String original = String.join("", segments);
        if (span == null || span.start != original.indexOf(expected)
                || !original.substring(span.start, span.end).equals(expected)) {
            throw new AssertionError("Wrong source span for " + query);
        }
    }

    private static void absent(String query, String... segments) {
        if (find(query, segments) != null) throw new AssertionError("Unexpected match for " + query);
    }

    private static void nativeSpan(String body, String query, String offsets, int start, int end) {
        int terms = ArchiveUnicode61Phrase.compile(query).firstClauseLength();
        ArchiveSearchOffsets.Span span = ArchiveSearchOffsets.first(body, offsets, terms);
        if (span == null || span.start != start || span.end != end) {
            throw new AssertionError("Wrong native source span for " + query);
        }
    }

    private static Span find(String query, String... segments) {
        ArchiveUnicode61Phrase.Matcher matcher = ArchiveUnicode61Phrase.compile(query).newMatcher();
        for (String segment : segments) {
            Span span = matcher.accept(segment);
            if (span != null) return span;
        }
        return matcher.finish();
    }
}
