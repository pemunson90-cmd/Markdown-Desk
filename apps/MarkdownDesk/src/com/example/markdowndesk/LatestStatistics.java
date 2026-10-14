package com.example.markdowndesk;

import java.util.Locale;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Consumer;

/** One active calculation OR queued delivery, and one replaceable snapshot. */
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
        submit();
    }

    /** Reject an in-flight/posted result immediately, before a later debounced snapshot. */
    synchronized void invalidate() {
        generation++;
        pending = null;
    }

    private void submit() {
        // Executor implementations may block/reenter. Never hold the state monitor here.
        try {
            worker.execute(this::calculate);
        } catch (RejectedExecutionException rejected) {
            reject();
        }
    }

    private void calculate() {
        final Request request;
        synchronized (this) {
            request = pending;
            pending = null;
            if (closed || request == null) {
                running = false;
                return;
            }
        }
        final Result result;
        try {
            result = new Result(request);
        } catch (RuntimeException failed) {
            finish();
            return;
        }
        final long ticket = request.generation;
        final boolean stale;
        synchronized (this) {
            stale = closed || ticket != generation;
        }
        if (stale) {
            finish();
            return;
        }
        try {
            // Capture only small counts/keys and the ticket, not the full source Request.
            completion.execute(() -> deliver(result, ticket));
        } catch (RejectedExecutionException rejected) {
            reject();
        }
    }

    private void deliver(Result result, long ticket) {
        try {
            synchronized (this) {
                if (!closed && ticket == generation) listener.accept(result);
            }
        } finally {
            // Backpressure includes main delivery, so completed results cannot accumulate.
            finish();
        }
    }

    private void finish() {
        synchronized (this) {
            running = false;
            if (closed || pending == null) return;
            running = true;
        }
        submit();
    }

    private synchronized void reject() {
        close();
        running = false;
    }

    @Override public synchronized void close() {
        closed = true;
        generation++;
        pending = null;
    }
}
