package com.example.markdowndesk;

import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Consumer;

/** One active render OR queued delivery, and one replaceable immutable snapshot. */
final class LatestPreview implements AutoCloseable {
    static final class Request {
        final String documentId, source;
        final long revision;
        final UiTheme.Palette palette;
        final float fontSp;
        final int mode;
        final ReaderTypography typography;

        Request(String documentId, long revision, String source, UiTheme.Palette palette,
                float fontSp, int mode) {
            this(documentId, revision, source, palette, fontSp, mode, ReaderTypography.defaults());
        }

        Request(String documentId, long revision, String source, UiTheme.Palette palette,
                float fontSp, int mode, ReaderTypography typography) {
            this.typography=typography==null?ReaderTypography.defaults():typography;
            this.documentId = documentId;
            this.revision = revision;
            this.source = source;
            this.palette = palette;
            this.fontSp = fontSp;
            this.mode = mode;
        }
    }

    static final class Result {
        final Request request;
        final String html;
        Result(Request request, String html) { this.request = request; this.html = html; }
    }

    private final Executor worker, completion;
    private final Consumer<Result> listener;
    private Request pending;
    private long generation;
    private boolean running, closed;

    LatestPreview(Executor worker, Executor completion, Consumer<Result> listener) {
        this.worker = worker;
        this.completion = completion;
        this.listener = listener;
    }

    synchronized void request(Request request) {
        if (closed) return;
        generation++;
        pending = request;
        start();
    }

    /** Logical cancellation; MarkdownRenderer does not support interruption. */
    synchronized void invalidate() {
        generation++;
        pending = null;
    }

    // Called with the monitor held; admission cannot race close/executor shutdown.
    private void start() {
        if (closed || running || pending == null) return;
        Request request = pending;
        pending = null;
        long ticket = generation;
        running = true;
        try {
            worker.execute(() -> render(request, ticket));
        } catch (RejectedExecutionException rejected) {
            close();
            running = false;
        }
    }

    private void render(Request request, long ticket) {
        String html = null;
        synchronized (this) {
            if (closed || ticket != generation) {
                running = false;
                start();
                return;
            }
        }
        try {
            html = MarkdownRenderer.render(request.source, request.palette, request.fontSp, request.typography);
        } catch (RuntimeException failed) {
            // A malformed input must not permanently block the latest pending render.
        }
        final String rendered = html;
        synchronized (this) {
            if (closed || ticket != generation) {
                running = false;
                start();
                return;
            }
            try {
                completion.execute(() -> deliver(request, ticket, rendered));
            } catch (RejectedExecutionException rejected) {
                close();
                running = false;
            }
        }
    }

    private synchronized void deliver(Request request, long ticket, String html) {
        try {
            if (!closed && ticket == generation && html != null) listener.accept(new Result(request, html));
        } finally {
            // Keep the active slot until main consumes this delivery: HTML cannot queue up.
            running = false;
            start();
        }
    }

    @Override public synchronized void close() {
        closed = true;
        generation++;
        pending = null;
    }
}
