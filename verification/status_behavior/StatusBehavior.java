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
        h.settle();
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
        eq("Document saved", h.saveButton.contentDescription, "clean accessible state");

        h = new StatusHarness();
        h.editor = new StatusHarness.Editor("one two\nthree");
        h.dirty = true;
        h.mode = StatusHarness.MODE_PREVIEW;
        h.wordGoal = 4;
        h.updateStatus();
        h.settle();
        eq("3 words · 13 characters · 2 lines · 1 min read · 75% of 4 · Read", h.statusView.text, "goal/read");
        eq("Note.md  •", h.titleView.text, "dirty title");
        eq("SAVE", h.saveButton.text, "dirty save");
        eq("Save document", h.saveButton.contentDescription, "dirty accessible action");
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
        h.settle();
        eq("3 words · " + unicode.length() + " characters · 2 lines · 1 min read · 100% of 2 · Split",
                h.statusView.text, "Unicode/CRLF/goal cap/split");
        eq("Note.md  ·  Read-only", h.titleView.text, "read-only title");
        eq("•••", h.saveButton.text, "saving priority");
        eq("Saving document", h.saveButton.contentDescription, "saving accessible priority");
        eq(false, h.saveButton.enabled, "saving disabled");
        eq(unicode, h.editor.text.source, "Unicode intact");
        eq(0, h.editor.selection, "Unicode cursor intact");

        h.saveInProgress = false;
        h.updateStatus();
        h.settle();
        eq("COPY", h.saveButton.text, "read-only save");
        eq("Save a copy", h.saveButton.contentDescription, "read-only accessible action");
        eq(true, h.saveButton.enabled, "read-only enabled");

        h = new StatusHarness();
        h.editor = new StatusHarness.Editor("guard");
        h.titleView = null;
        h.updateStatus();
        h.settle();
        eq(0, h.editor.text.copies, "null-title guard");

        h = new StatusHarness();
        h.editor = new StatusHarness.Editor("one two\nthree");
        h.editor.selection = 4;
        h.updateStatus();
        h.settle();
        eq(4, h.editor.selection, "selection unchanged");
        eq("one two\nthree", h.editor.text.source, "source unchanged");
        eq(1, h.editor.text.copies, "one editor snapshot per status update");
        asynchronous();
        System.out.println("PASS " + checks + " status behavior assertions");
    }

    private static void asynchronous() {
        StatusHarness h = new StatusHarness();
        h.editor = new StatusHarness.Editor("one two");
        DocumentTab first = new DocumentTab(); first.id = "first";
        DocumentTab second = new DocumentTab(); second.id = "second";
        h.tabs.add(first); h.tabs.add(second); h.activeTab = 0;
        h.updateStatus();
        eq("Calculating statistics · 7 characters · Write", h.statusView.text, "initial pending is explicit");
        eq(0, h.editor.text.copies, "no immediate editor snapshot");
        h.settle();
        eq("2 words · 7 characters · 1 lines · 1 min read · Write", h.statusView.text, "settled count");
        eq(1, h.editor.text.copies, "one debounced snapshot");
        h.mode = StatusHarness.MODE_PREVIEW;
        h.wordGoal = 4;
        h.updateStatus();
        eq("2 words · 7 characters · 1 lines · 1 min read · 50% of 4 · Read", h.statusView.text, "cached counts format current mode/goal");
        eq(0, h.main.delayed.size(), "non-text status update reuses exact result");
        eq(1, h.editor.text.copies, "no snapshot for cached refresh");
        for (int i = 0; i < 1000; i++) h.changed("one two three", false);
        eq(1, h.main.delayed.size(), "burst has one debounce callback");
        eq(0, h.worker().tasks.size(), "burst does not queue worker before debounce");
        eq(0, h.editor.text.copies, "burst makes no editor snapshots");
        eq("Calculating statistics · 13 characters · Read", h.statusView.text, "new edit hides stale counts and goal");
        eq("Note.md  •", h.titleView.text, "dirty title immediate");
        eq("SAVE", h.saveButton.text, "save immediate");
        eq(true, first.dirty, "active tab dirty immediate");
        h.main.runDelayed();
        h.worker().drain(); // Result posted, then a new edit.
        h.changed("four", false);
        h.main.runPosted();
        eq("Calculating statistics · 4 characters · Read", h.statusView.text, "queued stale completion cannot overwrite edit");
        h.settle();
        eq("1 word · 4 characters · 1 lines · 1 min read · 25% of 4 · Read", h.statusView.text, "latest edit settles");
        h.changed("old tab text", false);
        h.main.runDelayed();
        h.worker().drain();
        h.activeTab = 1; // Deliberately same revision: identity must independently reject old result.
        h.main.runPosted();
        eq("Calculating statistics · 12 characters · Read", h.statusView.text, "identity rejects old tab completion");
        h.changed("replacement\r\n", true); // Programmatic restore suppresses dirty side effects.
        h.dirty = false;
        long prior = h.revision();
        h.updateStatus();
        eq(false, second.dirty, "suppressed restore does not dirty tab");
        h.settle();
        eq("1 word · 13 characters · 2 lines · 1 min read · 25% of 4 · Read", h.statusView.text, "suppressed restore receives fresh stats");
        h.changed("same length!\n", true);
        eq(prior + 1, h.revision(), "suppressed same-length replacement increments revision");
        h.updateStatus(); h.settle();
        eq("2 words · 13 characters · 2 lines · 1 min read · 50% of 4 · Read", h.statusView.text, "same-length replacement cannot use old counts");
        Locale old = Locale.getDefault();
        try {
            h.changed("locale text", false); h.main.runDelayed();
            h.worker().drain();
            Locale.setDefault(old.equals(Locale.FRANCE) ? Locale.US : Locale.FRANCE);
            h.main.runPosted();
            eq("Calculating statistics · 11 characters · Read", h.statusView.text, "locale rejects prior completion");
            h.updateStatus(); h.settle();
            eq("2 words · 11 characters · 1 lines · 1 min read · 50% of 4 · Read", h.statusView.text, "new locale recomputes");
        } finally { Locale.setDefault(old); }
        h.changed("closing", false); h.main.runDelayed();
        h.worker().drain();
        h.onDestroy();
        h.main.runPosted(); h.main.runDelayed();
        eq(true, h.worker().isShutdown(), "lifecycle shuts down dedicated worker");
        eq(0, h.main.posted.size(), "lifecycle removes posted completion");
        eq(0, h.main.delayed.size(), "lifecycle removes debounce");
        eq("Calculating statistics · 7 characters · Read", h.statusView.text, "no result after destruction");
        eq(0, h.io.tasks.size(), "statistics do not queue save IO");
        eq(0, h.renderIo.tasks.size(), "statistics do not queue render IO");
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
        System.out.println("words,chars,snapshots_per_immediate_update,sample1_ms,sample2_ms,sample3_ms,median_ms,max_used_heap_MiB,deferred1_ms,deferred2_ms,deferred3_ms,deferred_median_ms,total_snapshots_per_edit");
        Runtime vm = Runtime.getRuntime();
        for (int words : new int[]{10000, 100000, 500000}) {
            String source = fixture(words);
            StatusHarness h = new StatusHarness();
            h.editor = new StatusHarness.Editor(source);
            h.updateStatus(); h.settle(); // warmup
            long[] time = new long[3], deferred = new long[3];
            long peakUsed = 0;
            int immediateCopies = 0, totalCopies = 0;
            for (int i = 0; i < 3; i++) {
                System.gc();
                long start = System.nanoTime();
                h.changed(source, false); // actual extracted TextWatcher -> updateStatus
                time[i] = System.nanoTime() - start;
                immediateCopies += h.editor.text.copies;
                start = System.nanoTime();
                h.settle(); // snapshot + actual worker scan + status apply; excludes intentional 250ms wait
                deferred[i] = System.nanoTime() - start;
                totalCopies += h.editor.text.copies;
                peakUsed = Math.max(peakUsed, vm.totalMemory() - vm.freeMemory());
            }
            long[] sorted = time.clone(), sortedDeferred = deferred.clone();
            Arrays.sort(sorted); Arrays.sort(sortedDeferred);
            System.out.printf(Locale.ROOT, "%d,%d,%d,%.3f,%.3f,%.3f,%.3f,%.1f,%.3f,%.3f,%.3f,%.3f,%d%n", words,
                    source.length(), immediateCopies / 3, time[0] / 1e6, time[1] / 1e6,
                    time[2] / 1e6, sorted[1] / 1e6, peakUsed / 1048576.0,
                    deferred[0] / 1e6, deferred[1] / 1e6, deferred[2] / 1e6, sortedDeferred[1] / 1e6, totalCopies / 3);
            h.onDestroy();
        }
    }

    public static void main(String[] args) {
        if (args.length == 1 && "test".equals(args[0])) test();
        else if (args.length == 1 && "bench".equals(args[0])) bench();
        else throw new IllegalArgumentException("Use test or bench");
    }
}
