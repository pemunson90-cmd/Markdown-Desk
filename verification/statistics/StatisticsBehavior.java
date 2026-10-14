package com.example.markdowndesk;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/** Deterministic scheduling tests against the actual production helper. */
public final class StatisticsBehavior {
    static int checks;
    static void eq(Object expected, Object actual, String label) {
        checks++;
        if (!Objects.equals(expected, actual)) throw new AssertionError(label + ": expected " + expected + ", got " + actual);
    }
    static final class Queue implements Executor {
        final ArrayDeque<Runnable> tasks = new ArrayDeque<>();
        public void execute(Runnable task) { tasks.add(task); }
        void drain() { while (!tasks.isEmpty()) tasks.remove().run(); }
    }
    public static void main(String[] args) throws Exception {
        Queue worker = new Queue(), ui = new Queue();
        List<LatestStatistics.Result> delivered = new ArrayList<>();
        LatestStatistics stats = new LatestStatistics(worker, ui, delivered::add);
        for (int i = 0; i < 1000; i++) stats.request("tab", i, "one two " + i, Locale.US);
        eq(1, worker.tasks.size(), "bounded worker queue for a burst");
        worker.drain();
        eq(1, ui.tasks.size(), "one calculation for newest queued request");
        ui.drain();
        eq(1, delivered.size(), "one newest delivery");
        eq(999L, delivered.get(0).revision, "latest revision");
        eq(3, delivered.get(0).words, "latest words");
        eq(1, delivered.get(0).lines, "latest lines");
        stats.request("old-tab", 1000, "old", Locale.US);
        worker.drain();
        stats.request("new-tab", 1000, "new text\r\n", Locale.US);
        ui.drain();
        eq(1, delivered.size(), "superseded posted result rejected");
        worker.drain(); ui.drain();
        eq("new-tab", delivered.get(1).documentId, "document identity retained");
        eq(2, delivered.get(1).lines, "LF lines preserved");
        stats.request("tab", 1001, "stale", Locale.US);
        worker.drain(); stats.invalidate(); ui.drain();
        eq(2, delivered.size(), "edit invalidation before next debounce rejects result");
        stats.request("tab", 1002, "queued", Locale.US);
        stats.close(); worker.drain(); ui.drain();
        stats.request("tab", 1003, "closed", Locale.US);
        eq(0, worker.tasks.size(), "closed helper rejects requests");
        eq(2, delivered.size(), "closed helper drops pending results");

        for (Locale locale : new Locale[]{Locale.US, Locale.FRANCE, Locale.JAPAN, Locale.forLanguageTag("th-TH")}) {
            Locale prior = Locale.getDefault();
            try {
                Locale.setDefault(locale);
                for (String text : new String[]{"", "\n", "one\rtwo\r\nthree\n", "café 🐱 naïve 42", "中文 日本語", "ภาษาไทย", "𐐀 𝟙", "can't re-enter foo_bar", "e\u0301 \uD800"}) {
                    Queue w = new Queue(), u = new Queue();
                    List<LatestStatistics.Result> out = new ArrayList<>();
                    LatestStatistics counter = new LatestStatistics(w, u, out::add);
                    counter.request("fixture", 1, text, locale); w.drain(); u.drain();
                    eq(EditorTools.wordCount(text), out.get(0).words, "exact word semantics " + locale);
                    eq(EditorTools.lineCount(text), out.get(0).lines, "exact LF semantics " + locale);
                    counter.close();
                }
            } finally { Locale.setDefault(prior); }
        }
        // Request while the worker is running, rather than merely before execution.
        Queue u = new Queue(); List<LatestStatistics.Result> out = new ArrayList<>();
        LatestStatistics[] live = new LatestStatistics[1];
        Executor reentrant = task -> {
            live[0].request("replacement", 2, "one two", Locale.US);
            task.run();
        };
        live[0] = new LatestStatistics(reentrant, u, out::add);
        live[0].request("first", 1, "first", Locale.US); u.drain();
        eq(1, out.size(), "replacement during worker submission delivered once");
        eq("replacement", out.get(0).documentId, "replacement during running state");
        live[0].close();
        concurrent();
        System.out.println("PASS " + checks + " statistics assertions");
    }
    private static void concurrent() throws Exception {
        ExecutorService pool = Executors.newSingleThreadExecutor();
        CountDownLatch postedFirst = new CountDownLatch(1), release = new CountDownLatch(1);
        Queue ui = new Queue();
        List<LatestStatistics.Result> delivered = new ArrayList<>();
        AtomicInteger submissions = new AtomicInteger(), posts = new AtomicInteger();
        AtomicReference<Throwable> error = new AtomicReference<>();
        Executor worker = task -> {
            submissions.incrementAndGet();
            pool.execute(() -> { try { task.run(); } catch (Throwable failure) { error.set(failure); } });
        };
        Executor completion = task -> {
            synchronized (ui) { ui.execute(task); }
            if (posts.incrementAndGet() == 1) {
                postedFirst.countDown();
                try {
                    if (!release.await(5, TimeUnit.SECONDS)) throw new AssertionError("worker gate timeout");
                } catch (InterruptedException failure) { throw new AssertionError(failure); }
            }
        };
        LatestStatistics stats = new LatestStatistics(worker, completion, delivered::add);
        try {
            stats.request("first", 0, "old", Locale.US);
            eq(true, postedFirst.await(5, TimeUnit.SECONDS), "worker reached completion gate");
            for (int i = 1; i <= 1000; i++) stats.request("latest", i, "new two", Locale.US);
            eq(1, submissions.get(), "requests during running scan never enqueue another worker task");
            release.countDown();
            pool.submit(() -> {}).get(5, TimeUnit.SECONDS); // drain barrier, no sleeps
            if (error.get() != null) throw new AssertionError("worker failed", error.get());
            eq(1, posts.get(), "main delivery backpressure prevents another calculation");
            synchronized (ui) { ui.drain(); }
            pool.submit(() -> {}).get(5, TimeUnit.SECONDS); // latest starts after consuming old delivery
            synchronized (ui) { ui.drain(); }
            eq(2, posts.get(), "one running scan and one latest replacement scanned");
            eq(1, delivered.size(), "posted first result suppressed after replacement");
            eq(1000L, delivered.get(0).revision, "concurrent latest revision");
            eq(2, delivered.get(0).words, "concurrent latest words");
        } finally {
            release.countDown(); stats.close(); pool.shutdownNow();
            eq(true, pool.awaitTermination(5, TimeUnit.SECONDS), "test worker terminated");
        }
    }

}
