package com.example.markdowndesk;

import android.app.Activity;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import android.print.PageRange;
import android.print.PrintAttributes;
import android.print.PrintDocumentAdapter;
import android.print.PrintJob;
import android.print.PrintManager;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Main-thread admission/lifecycle; at most one process-wide print, including retired work. */
final class DocumentPrint {
    private static final long DEADLINE_MS = 30_000L;
    // These deliberately outlive an Activity's executor and removeCallbacksAndMessages calls.
    private static final ExecutorService worker = Executors.newSingleThreadExecutor();
    private static final Handler main = new Handler(Looper.getMainLooper());
    private static Operation owner;

    static final class Capture {
        final String source, title;
        final UiTheme.Palette palette;
        final float fontSp;
        final boolean bom;

        Capture(CharSequence source, String title, UiTheme.Palette palette, float fontSp, boolean bom) {
            if (source == null || source.length() > DocumentIO.MAX_DOCUMENT_BYTES)
                throw new IllegalArgumentException("Printing supports documents up to 8 MiB.");
            this.source = source.toString();
            this.title = title;
            this.palette = palette;
            this.fontSp = fontSp;
            this.bom = bom;
        }
    }

    /** Admission precedes capture, so repeated actions cannot copy/queue more documents. Main only. */
    static void start(Activity activity, Supplier<Capture> capture, Consumer<String> errors) {
        if (activity.isFinishing() || activity.isDestroyed()) return;
        if (owner != null) {
            errors.accept("A print operation is still active.");
            return;
        }
        Operation operation = new Operation(activity, errors);
        owner = operation;
        try {
            operation.capture = capture.get();
            operation.capturing = false;
            if (!operation.live()) { operation.releaseIfSafe(); return; }
            if (operation.capture == null) throw new IllegalArgumentException("Unable to capture document for printing.");
            main.postDelayed(operation.timeout, Math.max(0L, operation.deadline - SystemClock.elapsedRealtime()));
            operation.renderOutstanding = true;
            try {
                worker.execute(operation::render);
            } catch (RuntimeException rejected) {
                operation.renderOutstanding = false;
                operation.cancel("Unable to start printing.");
            }
        } catch (RuntimeException error) {
            operation.capturing = false;
            operation.cancel(error instanceof IllegalArgumentException ? error.getMessage() : "Unable to capture document for printing.");
        }
    }

    static void cancel(Activity activity) {
        Operation operation = owner;
        if (operation != null && operation.activity == activity) operation.cancel(null);
    }

    /** Strict UTF-8 byte count without an encoded document-sized array. Includes stored BOM policy. */
    static void validate(Capture capture) {
        long bytes = capture.bom ? 3 : 0;
        String text = capture.source;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c < 0x80) bytes++;
            else if (c < 0x800) bytes += 2;
            else if (Character.isHighSurrogate(c)) {
                if (++i >= text.length() || !Character.isLowSurrogate(text.charAt(i)))
                    throw new IllegalArgumentException("The document contains invalid Unicode text.");
                bytes += 4;
            } else if (Character.isLowSurrogate(c)) {
                throw new IllegalArgumentException("The document contains invalid Unicode text.");
            } else bytes += 3;
            if (bytes > DocumentIO.MAX_DOCUMENT_BYTES)
                throw new IllegalArgumentException("Printing supports documents up to 8 MiB.");
        }
    }

    private static final class Operation extends PrintDocumentAdapter {
        final long deadline = SystemClock.elapsedRealtime() + DEADLINE_MS;
        final String token = UUID.randomUUID().toString();
        final Runnable timeout = () -> cancel("Printing timed out before the document was ready.");
        Activity activity;
        Consumer<String> errors;
        Capture capture;
        WebView view;
        PrintDocumentAdapter delegate;
        PrintJob job;
        // Only this flag and final fields cross into worker execution; all other transitions are main-owned.
        volatile boolean retired;
        boolean capturing = true;
        boolean renderOutstanding, exposed, started, finishEntered, finishSucceeded;
        long probeSequence;
        boolean destroyEntered, released, cancellationWanted, jobCancelSent, errorReported, probeRunning, probePending;

        Operation(Activity activity, Consumer<String> errors) {
            this.activity = activity;
            this.errors = errors;
        }

        void render() {
            String html = null, error = null;
            try {
                if (!retired && SystemClock.elapsedRealtime() < deadline) {
                    Capture input = capture;
                    validate(input);
                    if (!retired && SystemClock.elapsedRealtime() < deadline)
                        html = MarkdownRenderer.render(input.source, input.palette, input.fontSp);
                }
            } catch (RuntimeException failed) {
                error = failed instanceof IllegalArgumentException ? failed.getMessage() : "Unable to render the document for printing.";
            } finally {
                final String result = retired ? null : html;
                final String failure = error;
                // This handoff, not logical cancellation, consumes the physical reservation.
                main.post(() -> renderComplete(result, failure));
            }
        }

        boolean live() {
            if (released || retired || owner != this) return false;
            if (activity.isFinishing() || activity.isDestroyed()) { cancel(null); return false; }
            if (!exposed && SystemClock.elapsedRealtime() >= deadline) {
                cancel("Printing timed out before the document was ready.");
                return false;
            }
            return true;
        }

        void renderComplete(String html, String error) {
            renderOutstanding = false;
            String title = capture == null ? null : capture.title;
            capture = null;
            if (!live()) { releaseIfSafe(); return; }
            if (error != null || html == null) { cancel(error == null ? "Unable to render the document for printing." : error); return; }
            try {
                view = new WebView(activity);
                WebSettings settings = view.getSettings();
                settings.setJavaScriptEnabled(true);
                settings.setDomStorageEnabled(false);
                settings.setAllowContentAccess(false);
                settings.setAllowFileAccess(false);
                settings.setBlockNetworkLoads(true);
                settings.setLoadsImagesAutomatically(false);
                view.setWebViewClient(new WebViewClient() {
                    @Override public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest request) { return true; }
                    @Override public boolean shouldOverrideUrlLoading(WebView v, String url) { return true; }
                    @Override public void onPageFinished(WebView v, String url) { if (v == view) probe(title); }
                    @Override public void onReceivedError(WebView v, WebResourceRequest request, WebResourceError error) {
                        if (v == view && request.isForMainFrame() && !exposed) cancel("Unable to load the document for printing.");
                    }
                    @Override public void onReceivedHttpError(WebView v, WebResourceRequest request, WebResourceResponse error) {
                        if (v == view && request.isForMainFrame() && !exposed) cancel("Unable to load the document for printing.");
                    }
                });
                final String prefix = "<!doctype html><html>";
                if (!html.startsWith(prefix)) throw new IllegalArgumentException("Unexpected print HTML framing");
                // Same trusted metadata contract as preview; this incurs a full HTML builder/result copy.
                String page = new StringBuilder(html.length() + token.length() + 32)
                        .append("<!doctype html><html data-md-preview=\"").append(token).append("\">")
                        .append(html, prefix.length(), html.length()).toString();
                view.loadDataWithBaseURL("https://markdown.local/", page, "text/html", "UTF-8", null);
            } catch (RuntimeException failed) {
                cancel("Unable to load the document for printing.");
            }
        }

        void probe(String title) {
            if (!live() || exposed) return;
            if (probeRunning) { probePending = true; return; }
            probeRunning = true;
            long sequence = ++probeSequence;
            WebView expected = view;
            try {
                expected.evaluateJavascript("document.readyState === 'complete' ? document.documentElement.getAttribute('data-md-preview') : null", value -> {
                    if (released || view != expected || !probeRunning || sequence != probeSequence) return;
                    probeRunning = false;
                    boolean retry = probePending;
                    probePending = false;
                    if (!live() || exposed) return;
                    if (("\"" + token + "\"").equals(value)) submit(title);
                    else if (retry) probe(title);
                });
            } catch (RuntimeException failed) { cancel("Unable to check print readiness."); }
        }

        void submit(String title) {
            if (!live() || exposed) return;
            try {
                PrintManager manager = (PrintManager) activity.getSystemService("print");
                if (manager == null) { cancel("Printing is not available on this device."); return; }
                delegate = view.createPrintDocumentAdapter(title);
                if (delegate == null) { cancel("Printing is not available on this device."); return; }
                if (!live()) return;
                // PrintManager may synchronously call any lifecycle method before returning a job.
                exposed = true;
                main.removeCallbacks(timeout);
                PrintJob returned = manager.print("Markdown Desk · " + title, this, null);
                if (!released) job = returned;
                if (cancellationWanted) cancelJob(returned);
                else if (!retired && returned == null) cancel("Unable to start printing.");
            } catch (RuntimeException failed) { cancel("Unable to start printing."); }
        }

        void cancel(String error) {
            cancellationWanted = true;
            retired = true;
            main.removeCallbacks(timeout);
            probePending = false;
            if (error != null && !errorReported && activity != null && !activity.isFinishing() && !activity.isDestroyed()) {
                errorReported = true;
                try { errors.accept(error); } catch (RuntimeException ignored) { /* Reporting cannot release exposed ownership. */ }
            }
            cancelJob(job);
            releaseIfSafe();
        }

        void cancelJob(PrintJob candidate) {
            if (candidate == null || jobCancelSent) return;
            jobCancelSent = true;
            try { candidate.cancel(); } catch (RuntimeException ignored) { /* Cancellation is never a disposal proof. */ }
        }

        void releaseIfSafe() {
            if (released || !retired || capturing || renderOutstanding || (exposed && !finishSucceeded)) return;
            if (view != null) {
                if (destroyEntered) return;
                destroyEntered = true;
                try { view.destroy(); } catch (RuntimeException uncertain) { return; }
            }
            released = true;
            view = null;
            delegate = null;
            activity = null;
            errors = null;
            capture = null;
            job = null;
            if (owner == this) owner = null;
        }

        @Override public void onStart() {
            if (started || retired || delegate == null) return;
            started = true;
            try { delegate.onStart(); } catch (RuntimeException failed) { cancel("Unable to start printing."); }
        }

        @Override public void onLayout(PrintAttributes oldAttributes, PrintAttributes newAttributes,
                CancellationSignal cancellationSignal, LayoutResultCallback callback, Bundle extras) {
            try {
                if (retired || delegate == null) callback.onLayoutCancelled();
                else delegate.onLayout(oldAttributes, newAttributes, cancellationSignal, callback, extras);
            } catch (RuntimeException failed) { cancel("Unable to lay out the printed document."); }
        }

        @Override public void onWrite(PageRange[] pages, ParcelFileDescriptor destination,
                CancellationSignal cancellationSignal, WriteResultCallback callback) {
            try {
                if (retired || delegate == null) callback.onWriteCancelled();
                else delegate.onWrite(pages, destination, cancellationSignal, callback);
            } catch (RuntimeException failed) { cancel("Unable to write the printed document."); }
        }

        @Override public void onFinish() {
            if (finishEntered || delegate == null) return;
            finishEntered = true;
            retired = true;
            main.removeCallbacks(timeout);
            try {
                delegate.onFinish();
                finishSucceeded = true;
                releaseIfSafe();
            } catch (RuntimeException uncertain) { cancel("Unable to finish printing."); }
        }
    }
}
