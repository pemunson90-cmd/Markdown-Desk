package com.example.markdowndesk;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
/** Matched M0 fixture construction; only preview work is measured. */
public final class PreviewBenchmark {
    private static final String LINE = "alpha beta gamma delta epsilon zeta eta theta iota kappa\n";
    static String fixture(int words) {
        StringBuilder text = new StringBuilder();
        for (int row = 0; row < words / 10; row++) {
            text.append(row % 100 == 0 ? LINE.replace("alpha", "needle") : LINE);
            if (row % 100 == 99) text.append('\n');
        }
        return text.toString();
    }
    public static void main(String[] args) throws Exception {
        System.out.println("phase,words,bytes,sha256,render1_ms,render2_ms,render3_ms,burst_requests,queued_worker_tasks,queued_main_deliveries,burst_capture_ms,drain_ms,post_operation_used_MiB");
        for (int words : new int[]{10000,100000,500000}) {
            String text = fixture(words); byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
            StringBuilder hash = new StringBuilder();
            for (byte b : MessageDigest.getInstance("SHA-256").digest(bytes)) hash.append(String.format("%02x", b & 255));
            double[] times = {-1,-1,-1};
            if (args[0].equals("baseline")) {
                MarkdownRenderer.render(text, UiTheme.palette(0, false), 18);
                for (int i = 0; i < times.length; i++) {
                    long start = System.nanoTime();
                    String html = MarkdownRenderer.render(text, UiTheme.palette(0, false), 18);
                    times[i] = (System.nanoTime() - start) / 1e6;
                    if (html.length() < text.length()) throw new AssertionError();
                }
            }
            PreviewHarness h = new PreviewHarness(); h.editor.text = new PreviewHarness.Text(text);
            long start = System.nanoTime();
            for (int i = 0; i < 8; i++) { h.schedulePreview(); h.main.runDelayed(); }
            double capture = (System.nanoTime() - start) / 1e6;
            int queue = h.worker().tasks.size(); start = System.nanoTime(); h.worker().drain();
            int posted = h.main.posted.size(); h.settle(); double drain = (System.nanoTime() - start) / 1e6;
            Runtime vm = Runtime.getRuntime();
            System.out.printf(Locale.ROOT,"%s,%d,%d,%s,%.3f,%.3f,%.3f,8,%d,%d,%.3f,%.3f,%.1f%n",
                    args[0],words,bytes.length,hash,times[0],times[1],times[2],queue,posted,capture,drain,(vm.totalMemory()-vm.freeMemory())/1048576.0);
        }
    }
}
