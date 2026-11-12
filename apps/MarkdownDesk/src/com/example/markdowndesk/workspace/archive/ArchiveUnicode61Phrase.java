package com.example.markdowndesk.workspace.archive;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;

/**
 * Streams the same default unicode61 tokens as FTS4 across original message
 * segments. Offsets are UTF-16 positions in the concatenated source message.
 */
public final class ArchiveUnicode61Phrase {
    public static final class Span {
        public final int start;
        public final int end;

        private Span(int start, int end) {
            this.start = start;
            this.end = end;
        }
    }

    private static final class Term {
        final String text;
        final boolean prefix;
        final boolean anchored;

        Term(String text, boolean prefix, boolean anchored) {
            this.text = text;
            this.prefix = prefix;
            this.anchored = anchored;
        }

        boolean matches(StringBuilder token, boolean overflow, boolean firstToken) {
            if (anchored && !firstToken) return false;
            if (prefix ? token.length() < text.length() : overflow || token.length() != text.length()) {
                return false;
            }
            for (int index = 0; index < text.length(); index++) {
                if (text.charAt(index) != token.charAt(index)) return false;
            }
            return true;
        }
    }

    private final List<List<Term>> clauses = new ArrayList<>();
    private boolean empty;
    private int maxTokenUnits;

    private ArchiveUnicode61Phrase() {}

    public static ArchiveUnicode61Phrase compile(String query) {
        ArchiveUnicode61Phrase phrase = new ArchiveUnicode61Phrase();
        // The SQL path doubles input quotes inside its outer quotes. FTS4 reads
        // the resulting adjacent quoted clauses as an implicit AND.
        for (String clause : (query == null ? "" : query.trim()).split("\"", -1)) {
            List<Term> terms = readTerms(clause);
            phrase.clauses.add(terms);
            if (terms.isEmpty()) phrase.empty = true;
            for (Term term : terms) {
                phrase.maxTokenUnits = Math.max(phrase.maxTokenUnits, term.text.length());
            }
        }
        return phrase;
    }

    public boolean isEmpty() {
        return empty;
    }

    public int firstClauseLength() {
        return empty ? 0 : clauses.get(0).size();
    }

    public Matcher newMatcher() {
        return new Matcher();
    }

    private static List<Term> readTerms(String input) {
        List<Term> terms = new ArrayList<>();
        int start = 0;
        while (start < input.length()) {
            int codePoint = input.codePointAt(start);
            if (!ArchiveUnicode61Tables.isAlnum(sqliteCodePoint(codePoint))) {
                start += Character.charCount(codePoint);
                continue;
            }
            int end = start;
            StringBuilder token = new StringBuilder();
            do {
                codePoint = input.codePointAt(end);
                int folded = ArchiveUnicode61Tables.fold(sqliteCodePoint(codePoint));
                if (folded != 0) token.appendCodePoint(folded);
                end += Character.charCount(codePoint);
            } while (end < input.length() && continuesToken(sqliteCodePoint(input.codePointAt(end))));
            terms.add(new Term(token.toString(), end < input.length() && input.charAt(end) == '*',
                    start > 0 && input.charAt(start - 1) == '^'));
            start = end;
        }
        return terms;
    }

    private static boolean continuesToken(int codePoint) {
        return ArchiveUnicode61Tables.isAlnum(codePoint) || ArchiveUnicode61Tables.isDiacritic(codePoint);
    }

    private static int sqliteCodePoint(int codePoint) {
        if ((codePoint >= 0xD800 && codePoint <= 0xDFFF) || codePoint == 0xFFFE || codePoint == 0xFFFF) {
            return 0xFFFD;
        }
        return codePoint;
    }

    /** One active start per possible phrase progress, including overlapping starts. */
    private static final class ClauseMatcher {
        private final List<Term> terms;
        private final int[] starts;
        private final BitSet active;
        private Span hit;

        ClauseMatcher(List<Term> terms) {
            this.terms = terms;
            starts = new int[terms.size()];
            active = new BitSet(terms.size());
        }

        void accept(StringBuilder token, boolean overflow, boolean firstToken, int start, int end) {
            if (hit != null) return;
            // Descending progress lets this token advance each previous state once.
            for (int matched = active.length() - 1; matched >= 1; matched = active.previousSetBit(matched - 1)) {
                active.clear(matched);
                if (!terms.get(matched).matches(token, overflow, firstToken)) continue;
                int next = matched + 1;
                if (next == terms.size()) {
                    hit = new Span(starts[matched], end);
                    return;
                }
                starts[next] = starts[matched];
                active.set(next);
            }
            if (!terms.get(0).matches(token, overflow, firstToken)) return;
            if (terms.size() == 1) {
                hit = new Span(start, end);
            } else {
                starts[1] = start;
                active.set(1);
            }
        }
    }

    public final class Matcher {
        private final List<ClauseMatcher> matchers = new ArrayList<>();
        private final StringBuilder token = new StringBuilder();
        private int position;
        private int tokenStart = -1;
        private int tokenEnd;
        private boolean firstToken = true;
        private boolean overflow;
        private char highSurrogate;
        private Span result;

        private Matcher() {
            if (empty) return;
            for (List<Term> clause : clauses) matchers.add(new ClauseMatcher(clause));
        }

        public Span accept(String segment) {
            if (empty || result != null || segment.isEmpty()) return result;
            int index = 0;
            if (highSurrogate != 0) {
                char low = segment.charAt(0);
                if (Character.isLowSurrogate(low)) {
                    acceptCodePoint(Character.toCodePoint(highSurrogate, low), position - 1, position + 1);
                    position++;
                    index++;
                } else {
                    acceptCodePoint(0xFFFD, position - 1, position);
                }
                highSurrogate = 0;
            }
            while (index < segment.length() && result == null) {
                int codePoint = segment.codePointAt(index);
                int units = Character.charCount(codePoint);
                if (index + 1 == segment.length() && Character.isHighSurrogate((char) codePoint)) {
                    highSurrogate = (char) codePoint;
                } else {
                    acceptCodePoint(sqliteCodePoint(codePoint), position, position + units);
                }
                position += units;
                index += units;
            }
            return result;
        }

        public Span finish() {
            if (empty || result != null) return result;
            if (highSurrogate != 0) {
                acceptCodePoint(0xFFFD, position - 1, position);
                highSurrogate = 0;
            }
            if (tokenStart >= 0 && result == null) emitToken();
            return result;
        }

        private void acceptCodePoint(int codePoint, int start, int end) {
            if (tokenStart < 0 ? ArchiveUnicode61Tables.isAlnum(codePoint) : continuesToken(codePoint)) {
                if (tokenStart < 0) tokenStart = start;
                tokenEnd = end;
                int folded = ArchiveUnicode61Tables.fold(codePoint);
                // Keep the longest possible query-token prefix plus at most one
                // surrogate unit. A megabyte token still needs bounded memory.
                if (folded != 0 && !overflow) {
                    if (token.length() < maxTokenUnits) token.appendCodePoint(folded);
                    else overflow = true;
                }
            } else if (tokenStart >= 0) {
                emitToken();
            }
        }

        private void emitToken() {
            Span earliest = null;
            boolean complete = true;
            for (ClauseMatcher matcher : matchers) {
                matcher.accept(token, overflow, firstToken, tokenStart, tokenEnd);
                if (matcher.hit == null) {
                    complete = false;
                } else if (earliest == null || matcher.hit.start < earliest.start) {
                    earliest = matcher.hit;
                }
            }
            if (complete) result = earliest;
            tokenStart = -1;
            token.setLength(0);
            overflow = false;
            firstToken = false;
        }
    }
}
