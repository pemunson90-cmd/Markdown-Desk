package com.example.markdowndesk;

import java.util.Locale;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

/** One running scan and one replaceable snapshot, independent of document/save I/O. */
final class LatestStatistics implements AutoCloseable {
    static final class Result {
        final String documentId;
        final long revision;
        final Locale locale;
        final int words, lines;

        Result(Request request) {
            documentId = request.documentId;
            revision = request.revision;
            locale = request.locale;
            words = EditorTools.wordCount(request.text, locale);
            lines = EditorTools.lineCount(request.text);
        }
    }

    private static final class Request {
        final String documentId, text;
        final long revision, generation;
        final Locale locale;

        Request(String documentId, long revision, String text, Locale locale, long generation) {
            this.documentId = documentId;
            this.revision = revision;
            this.text = text;
            this.locale = locale;
            this.generation = generation;
        }
    }

    private final Executor worker, completion;
    private final Consumer<Result> listener;
    private Request pending;
    private boolean running, closed;
    private long generation;

    LatestStatistics(Executor worker, Executor completion, Consumer<Result> listener) {
        this.worker = worker;
        this.completion = completion;
        this.listener = listener;
    }

    void request(String documentId, long revision, String text, Locale locale) {
        synchronized (this) {
            if (closed) return;
            pending = new Request(documentId, revision, text, locale, ++generation);
            if (running) return;
            running = true;
        }
        worker.execute(this::drain);
    }

    /** Reject an in-flight/posted result immediately, before a later debounced snapshot. */
    synchronized void invalidate() {
        generation++;
        pending = null;
    }

    private void drain() {
        while (true) {
            final Request request;
            synchronized (this) {
                request = pending;
                pending = null;
                if (closed || request == null) {
                    running = false;
                    return;
                }
            }
            final Result result = new Result(request);
            synchronized (this) {
                if (closed || request.generation != generation) continue;
            }
            completion.execute(() -> {
                synchronized (LatestStatistics.this) {
                    if (closed || request.generation != generation) return;
                }
                listener.accept(result);
            });
        }
    }

    @Override public synchronized void close() {
        closed = true;
        generation++;
        pending = null;
    }
}
