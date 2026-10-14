package com.example.markdowndesk;

import java.util.Arrays;
import java.util.Locale;

public final class StatusBehavior {
    private static int checks;

    private static void eq(Object expected, Object actual, String label) {
        checks++;
        if (!expected.equals(actual)) throw new AssertionError(label + ": expected " + expected + ", got " + actual);
    }

    private static StatusHarness status(String source, String expectedStatus, String expectedTitle,
                                        String expectedSave, boolean enabled) {
        StatusHarness h = new StatusHarness();
        h.editor = new StatusHarness.Editor(source);
        h.updateStatus();
        eq(expectedStatus, h.statusView.text, "status");
        eq(expectedTitle, h.titleView.text, "title");
        eq(expectedSave, h.saveButton.text, "save label");
        eq(enabled, h.saveButton.enabled, "save enabled");
        eq(source, h.editor.text.source, "editor text");
        eq(0, h.editor.selection, "cursor");
        return h;
    }

    private static void test() {
        StatusHarness h = status("", "0 words · 0 characters · 0 lines · 0 min read · Write",
                "Note.md", "SAVED", true);
        eq("styled", h.saveButton.background, "clean button style");

        h = new StatusHarness();
        h.editor = new StatusHarness.Editor("one two\nthree");
        h.dirty = true;
        h.mode = StatusHarness.MODE_PREVIEW;
        h.wordGoal = 4;
        h.updateStatus();
        eq("3 words · 13 characters · 2 lines · 1 min read · 75% of 4 · Read", h.statusView.text, "goal/read");
        eq("Note.md  •", h.titleView.text, "dirty title");
        eq("SAVE", h.saveButton.text, "dirty save");
        eq(true, h.saveButton.enabled, "dirty enabled");
        eq(true, h.saveButton.background != null, "dirty button background");

        String unicode = "café 🐱 naïve\r\n42";
        h = new StatusHarness();
        h.editor = new StatusHarness.Editor(unicode);
        h.readOnly = true;
        h.saveInProgress = true;
        h.mode = StatusHarness.MODE_SPLIT;
        h.wordGoal = 2;
        h.updateStatus();
        eq("3 words · " + unicode.length() + " characters · 2 lines · 1 min read · 100% of 2 · Split",
                h.statusView.text, "Unicode/CRLF/goal cap/split");
        eq("Note.md  ·  Read-only", h.titleView.text, "read-only title");
        eq("•••", h.saveButton.text, "saving priority");
        eq(false, h.saveButton.enabled, "saving disabled");
        eq(unicode, h.editor.text.source, "Unicode intact");
        eq(0, h.editor.selection, "Unicode cursor intact");

        h.saveInProgress = false;
        h.updateStatus();
        eq("COPY", h.saveButton.text, "read-only save");
        eq(true, h.saveButton.enabled, "read-only enabled");

        h = new StatusHarness();
        h.editor = new StatusHarness.Editor("guard");
        h.titleView = null;
        h.updateStatus();
        eq(0, h.editor.text.copies, "null-title guard");

        h = new StatusHarness();
        h.editor = new StatusHarness.Editor("one two\nthree");
        h.editor.selection = 4;
        h.updateStatus();
        eq(4, h.editor.selection, "selection unchanged");
        eq("one two\nthree", h.editor.text.source, "source unchanged");
        eq(1, h.editor.text.copies, "one editor snapshot per status update");
        System.out.println("PASS " + checks + " status behavior assertions");
    }

    private static String fixture(int words) {
        String line = "alpha beta gamma delta epsilon zeta eta theta iota kappa\n";
        StringBuilder out = new StringBuilder(words / 10 * (line.length() + 1));
        for (int row = 0; row < words / 10; row++) {
            out.append(row % 100 == 0 ? line.replace("alpha", "needle") : line);
            if (row % 100 == 99) out.append('\n');
        }
        return out.toString();
    }

    private static void bench() {
        System.out.println("words,chars,snapshots_per_update,sample1_ms,sample2_ms,sample3_ms,median_ms,max_used_heap_MiB");
        Runtime vm = Runtime.getRuntime();
        for (int words : new int[]{10000, 100000, 500000}) {
            String source = fixture(words);
            StatusHarness h = new StatusHarness();
            h.editor = new StatusHarness.Editor(source);
            h.updateStatus(); // warmup
            long[] time = new long[3];
            long peakUsed = 0;
            int priorCopies = h.editor.text.copies;
            for (int i = 0; i < 3; i++) {
                System.gc();
                long start = System.nanoTime();
                h.updateStatus();
                time[i] = System.nanoTime() - start;
                peakUsed = Math.max(peakUsed, vm.totalMemory() - vm.freeMemory());
            }
            int copies = (h.editor.text.copies - priorCopies) / 3;
            if (h.editor.text.copies - priorCopies != 3 * copies) throw new AssertionError("Inconsistent snapshot count");
            long[] sorted = time.clone();
            Arrays.sort(sorted);
            System.out.printf(Locale.ROOT, "%d,%d,%d,%.3f,%.3f,%.3f,%.3f,%.1f%n", words,
                    source.length(), copies, time[0] / 1e6, time[1] / 1e6,
                    time[2] / 1e6, sorted[1] / 1e6, peakUsed / 1048576.0);
        }
    }

    public static void main(String[] args) {
        if (args.length == 1 && "test".equals(args[0])) test();
        else if (args.length == 1 && "bench".equals(args[0])) bench();
        else throw new IllegalArgumentException("Use test or bench");
    }
}
