package com.example.markdowndesk;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** Deterministic host-only M0 baseline. CSV output; all generated data lives under build/. */
public final class HostBaseline {
    private static final String LINE = "alpha beta gamma delta epsilon zeta eta theta iota kappa\n";
    private static volatile Object sink;

    private HostBaseline() {}

    private interface Operation { Object run() throws IOException; }

    private static String fixture(int words) {
        StringBuilder text = new StringBuilder((words / 10) * (LINE.length() + 1));
        for (int row = 0; row < words / 10; row++) {
            text.append(row % 100 == 0 ? LINE.replace("alpha", "needle") : LINE);
            if (row % 100 == 99) text.append('\n');
        }
        return text.toString();
    }

    private static void result(String fixture, String metric, Operation op) throws IOException {
        sink = op.run(); // One warmup, outside reported timing.
        long[] ns = new long[3];
        long[] used = new long[3];
        Runtime vm = Runtime.getRuntime();
        for (int i = 0; i < 3; i++) {
            System.gc(); // Outside timing; heap number is post-operation used heap, not peak.
            long start = System.nanoTime();
            sink = op.run();
            ns[i] = System.nanoTime() - start;
            used[i] = vm.totalMemory() - vm.freeMemory();
        }
        long[] sorted = ns.clone();
        Arrays.sort(sorted);
        long maxHeap = Math.max(used[0], Math.max(used[1], used[2]));
        System.out.printf(Locale.ROOT, "sample,%s,%s,,,,,%.3f,%.3f,%.3f,%.3f,%.1f%n",
                fixture, metric, ns[0] / 1e6, ns[1] / 1e6, ns[2] / 1e6,
                sorted[1] / 1e6, maxHeap / 1048576.0);
    }

    private static void runSize(Path dir, int words) throws IOException {
        String name = String.format(Locale.ROOT, "%dk", words / 1000);
        String text = fixture(words);
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        if (bytes.length >= 8388608) throw new IllegalStateException("Fixture exceeds app byte limit");
        Path input = dir.resolve(name + ".md");
        Path output = dir.resolve(name + ".saved.md");
        Files.write(input, bytes);
        int counted = EditorTools.wordCount(text);
        if (counted != words) throw new IllegalStateException(name + " word count " + counted);
        int matches = SearchEngine.findAll(text, "needle", new SearchEngine.Options(false, false, true)).size();
        if (matches != words / 1000) throw new IllegalStateException(name + " matches " + matches);
        System.out.printf(Locale.ROOT, "fixture,%s,,%d,%d,%d,%s,,,,,%n", name, bytes.length,
                words, EditorTools.lineCount(text), EditorTools.sha256(bytes));
        result(name, "wordCount", () -> EditorTools.wordCount(text));
        result(name, "lineCount", () -> EditorTools.lineCount(text));
        result(name, "sha256", () -> EditorTools.sha256(bytes));
        result(name, "findAll_literal_wholeWord", () -> SearchEngine.findAll(text, "needle", new SearchEngine.Options(false, false, true)));
        result(name, "render_paper", () -> MarkdownRenderer.render(text, UiTheme.palette(UiTheme.PAPER, false), 18.0f));
        result(name, "host_readAllBytes", () -> Files.readAllBytes(input));
        result(name, "host_write_bytes", () -> { Files.write(output, bytes); return Files.size(output); });
        if (!Arrays.equals(bytes, Files.readAllBytes(output))) throw new IllegalStateException("Host write mismatch");
    }

    public static void main(String[] args) throws IOException {
        if (args.length != 1) throw new IllegalArgumentException("Expected generated-data directory");
        Path dir = Path.of(args[0]);
        Files.createDirectories(dir);
        System.out.println("kind,fixture,metric,bytes,words,lines,sha256,sample1_ms,sample2_ms,sample3_ms,median_ms,max_post_gc_used_heap_MiB");
        for (int words : List.of(10000, 100000, 500000)) runSize(dir, words);
        byte[] boundary = new byte[8388608];
        byte[] pattern = LINE.getBytes(StandardCharsets.UTF_8);
        for (int i = 0; i < boundary.length; i++) boundary[i] = pattern[i % pattern.length];
        Path input = dir.resolve("exact-8MiB.md");
        Files.write(input, boundary);
        System.out.printf(Locale.ROOT, "fixture,exact-8MiB,,%d,%d,%d,%s,,,,,%n", boundary.length,
                EditorTools.wordCount(new String(boundary, StandardCharsets.UTF_8)),
                EditorTools.lineCount(new String(boundary, StandardCharsets.UTF_8)), EditorTools.sha256(boundary));
        result("exact-8MiB", "host_readAllBytes", () -> Files.readAllBytes(input));
        if (Files.readAllBytes(input).length != boundary.length) throw new IllegalStateException("Boundary read mismatch");
    }
}
