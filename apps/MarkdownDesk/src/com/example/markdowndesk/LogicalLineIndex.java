package com.example.markdowndesk;

import java.util.Arrays;

/** LF-only logical editor lines. Offsets and callback lengths are UTF-16 units. */
final class LogicalLineIndex {
    private int[] starts = new int[16];
    private int size = 1;
    private int length;

    int size() { return size; }
    int start(int line) { return starts[line]; }

    int lineForOffset(int offset) {
        return Math.max(0, upperBound(offset) - 1);
    }

    void changed(CharSequence text, int start, int before, int count) {
        int newLength = text.length();
        if (start < 0 || before < 0 || count < 0 || start > length
                || before > length - start || start > newLength || count > newLength - start
                || (long) length - before + count != newLength) {
            rebuild(text);
            return;
        }
        // Large changes use exact allocation instead of holding a paste-offset buffer
        // alongside the old and new line arrays. Small typing stays incremental.
        if ((start == 0 && before == length) || count > 65536) {
            rebuild(text);
            return;
        }
        int first = upperBound(start);
        int after = upperBound(start + before);
        int[] inserted = null;
        int insertedSize = 0;
        for (int i = start, end = start + count; i < end; i++) {
            if (text.charAt(i) == '\n') {
                if (inserted == null) inserted = new int[4];
                else if (insertedSize == inserted.length) inserted = Arrays.copyOf(inserted, inserted.length * 2);
                inserted[insertedSize++] = i + 1;
            }
        }
        int newSize = size - (after - first) + insertedSize;
        ensureCapacity(newSize);
        System.arraycopy(starts, after, starts, first + insertedSize, size - after);
        int delta = count - before;
        if (delta != 0) {
            for (int i = first + insertedSize; i < newSize; i++) starts[i] += delta;
        }
        if (insertedSize != 0) System.arraycopy(inserted, 0, starts, first, insertedSize);
        size = newSize;
        length = newLength;
    }

    private int upperBound(int offset) {
        int lo = 0, hi = size;
        while (lo < hi) {
            int mid = (lo + hi) >>> 1;
            if (starts[mid] <= offset) lo = mid + 1; else hi = mid;
        }
        return lo;
    }

    private void rebuild(CharSequence text) {
        // Full document replacement uses the baseline's exact two-pass allocation.
        // Geometric growth here doubled an 8MiB LF-only document's line array.
        int lines = 1;
        for (int i = 0; i < text.length(); i++) if (text.charAt(i) == '\n') lines++;
        starts = new int[lines];
        size = 1;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '\n') starts[size++] = i + 1;
        }
        length = text.length();
    }

    private void ensureCapacity(int required) {
        if (required > starts.length) {
            // Retain amortized small-edit growth without doubling large line arrays.
            long capacity = Math.max((long) required,
                    (long) starts.length + Math.min(starts.length, 65536));
            if (capacity > Integer.MAX_VALUE) capacity = required;
            starts = Arrays.copyOf(starts, (int) capacity);
        }
    }
}
