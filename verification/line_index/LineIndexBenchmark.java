package com.example.markdowndesk;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Locale;

/** Timings cover the exact extracted callback; source String mutation is outside timing. */
public final class LineIndexBenchmark {
    private static final int PAIRS = 20;
    private static volatile int sink;
    private static String fixture(int words) {
        String line = "alpha beta gamma delta epsilon zeta eta theta iota kappa\n";
        StringBuilder text = new StringBuilder();
        for (int row = 0; row < words / 10; row++) {
            text.append(row % 100 == 0 ? line.replace("alpha", "needle") : line);
            if (row % 100 == 99) text.append('\n');
        }
        return text.toString();
    }
    private static void batch(EditorCallbackSurface editor, CharSequence original, CharSequence inserted, int position) {
        for (int i = 0; i < PAIRS; i++) {
            editor.onTextChanged(inserted, position, 0, 1);
            editor.onTextChanged(original, position, 1, 0);
        }
    }
    private static void measure(String kind, int words, String original, int position, String label) {
        String inserted = original.substring(0, position) + "x" + original.substring(position);
        EditorCallbackSurface editor = kind.equals("baseline") ? new BaselineLineHarness() : new LineIndexHarness();
        editor.onTextChanged(original, 0, 11, original.length());
        for (int i = 0; i < 3; i++) batch(editor, original, inserted, position);
        double[] samples = new double[5];
        long maxUsedHeap = 0;
        for (int sample = 0; sample < samples.length; sample++) {
            System.gc();
            long start = System.nanoTime();
            batch(editor, original, inserted, position);
            samples[sample] = (System.nanoTime() - start) / (PAIRS * 2.0 * 1e6);
            Runtime vm = Runtime.getRuntime();
            maxUsedHeap = Math.max(maxUsedHeap, vm.totalMemory() - vm.freeMemory());
        }
        LineIndexCheck.CountedText counted = new LineIndexCheck.CountedText(inserted);
        editor.onTextChanged(counted, position, 0, 1);
        int insertReads = counted.reads;
        counted = new LineIndexCheck.CountedText(original);
        editor.onTextChanged(counted, position, 1, 0);
        sink = kind.equals("baseline") ? ((BaselineLineHarness) editor).line(original.length())
                : ((LineIndexHarness) editor).line(original.length());
        double[] sorted = samples.clone();
        Arrays.sort(sorted);
        System.out.printf(Locale.ROOT, "%s,%d,%d,%s,%d,%d,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f,%.3f%n", kind,
                words, original.length(), label, insertReads, counted.reads,
                samples[0], samples[1], samples[2], samples[3], samples[4], sorted[2], maxUsedHeap / 1048576.0);
    }
    public static void main(String[] args) throws Exception {
        System.out.println("path,words,chars,position,insert_char_reads,delete_char_reads,sample1_ms_per_edit,sample2_ms_per_edit,sample3_ms_per_edit,sample4_ms_per_edit,sample5_ms_per_edit,median_ms_per_edit,max_post_operation_used_heap_MiB");
        for (int words : new int[]{10000, 100000, 500000}) {
            String text = fixture(words);
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) hex.append(String.format("%02x", b & 255));
            System.err.println("fixture words=" + words + " chars=" + text.length() + " sha256=" + hex);
            for (String kind : new String[]{"baseline", "incremental"}) {
                measure(kind, words, text, 0, "beginning");
                measure(kind, words, text, text.length() / 2, "middle");
                measure(kind, words, text, text.length(), "end");
            }
        }
    }
}
