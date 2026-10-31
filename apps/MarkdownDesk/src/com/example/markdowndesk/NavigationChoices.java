package com.example.markdowndesk;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** A picker session: display filtering never changes the identity of an action. */
final class NavigationChoices {
    static final class Row {
        final int id;
        final String title;
        final String detail;
        Row(int id, String title, String detail) {
            this.id = id;
            this.title = title == null ? "" : title;
            this.detail = detail == null ? "" : detail;
        }
    }
    static final class Snapshot {
        final List<Row> rows;
        private final NavigationChoices owner;
        private final long revision;
        private Snapshot(NavigationChoices owner, long revision, List<Row> rows) {
            this.owner = owner;
            this.revision = revision;
            this.rows = Collections.unmodifiableList(rows);
        }
    }
    private final List<Row> all;
    private final List<String> searchable;
    private long revision;
    private boolean closed;

    NavigationChoices(List<Row> rows) {
        all = new ArrayList<>(rows);
        searchable = new ArrayList<>(rows.size());
        Set<Integer> identities = new HashSet<>();
        for (Row row : all) {
            if (row == null || !identities.add(row.id)) throw new IllegalArgumentException("Duplicate or null navigation row");
            searchable.add(normalize(row.title + "\n" + row.detail));
        }
    }
    private static String normalize(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFC).toLowerCase(Locale.ROOT);
    }
    // Android's ICU regex engine does not accept Java's (?U) pattern flag.
    // Split by Unicode code points instead, including non-breaking spaces.
    private static String[] splitTerms(String query) {
        ArrayList<String> terms = new ArrayList<>();
        int start = 0;
        for (int index = 0; index < query.length();) {
            int codePoint = query.codePointAt(index);
            int next = index + Character.charCount(codePoint);
            if (Character.isWhitespace(codePoint) || Character.isSpaceChar(codePoint)) {
                if (start < index) terms.add(query.substring(start, index));
                start = next;
            }
            index = next;
        }
        if (start < query.length()) terms.add(query.substring(start));
        return terms.toArray(new String[0]);
    }
    Snapshot filter(String query) {
        long next = ++revision;
        List<Row> matches = new ArrayList<>();
        if (!closed) {
            String normalized = normalize(query == null ? "" : query);
            String[] terms = splitTerms(normalized);
            for (int i = 0; i < all.size(); i++) {
                boolean include = true;
                for (String term : terms) if (!searchable.get(i).contains(term)) { include = false; break; }
                if (include) matches.add(all.get(i));
            }
        }
        return new Snapshot(this, next, matches);
    }
    Row choose(Snapshot displayed, int position) {
        if (closed || displayed == null || displayed.owner != this || displayed.revision != revision
                || position < 0 || position >= displayed.rows.size()) return null;
        Row row = displayed.rows.get(position);
        cancel();
        return row;
    }
    void cancel() {
        closed = true;
        ++revision;
        all.clear();
        searchable.clear();
    }
}
