# Bounded existing preview scheduling (2026-10-14)

Base: origin/main 6630609. Authorized M2 roadmap slice; root reviewed this design
before production edits. MainActivity already renders Markdown on renderIo. The
problem is its unbounded executor queue and delayed invalidation, not UI rendering.

Keep the existing 180 ms debounce, immutable main-thread String snapshot, complete
MarkdownRenderer HTML, final-field UiTheme.Palette/font inputs, and scroll restore.
Use a package-private LatestPreview helper: one active render OR posted delivery,
plus one replaceable pending request. Do not start another render while delivery
is queued; this bounds HTML retained by Handler as well as executor tasks. A
monotonic generation rejects superseded work before rendering and delivery.
Invalidation discards pending snapshots; an active renderer is not interrupted
because MarkdownRenderer has no cooperative cancellation. Shutdown closes before
WebView destruction and stops executor admission. There is no new document size
policy: existing source limits remain; the bound is request count, not renderer
allocation/peak memory. An active source, its temporary renderer allocations and
HTML, one pending source, plus the live editor may coexist.

Activity invalidates before suppressed watcher returns, on scheduling and body
rebuild, and on destruction. Acceptance checks revision, active document, palette
identity, font, mode and shutdown. Main-thread state remains on main; worker
receives only final captured values and pure renderer/theme functions. A-B-A
transitions cannot revive old requests because generations are monotonic.

Verification: extract actual production watcher/scheduler/lifecycle hooks with a
controlled worker and main queue; exercise debounce, suppressed loads, tabs/close,
A-B-A, theme/font/mode changes, stale ordering, queued delivery, shutdown and
scroll capture. Compare full HTML against unchanged renderer across all nine
palettes and font extremes; run existing renderer goldens. Capture actual matched
10k/100k/500k host baseline render/scheduler data once, then changed scheduling
measurements without repeating unchanged renderer benchmarks. Fixtures match
verification/benchmarks/HostBaseline.java exactly (hashes recorded). Physical
Android/WebView/IME/Fold remains NOT RUN; no numeric latency budget is invented.

Existing onPageFinished receives the same URL for every load and cannot identify
a stale WebView navigation. This predates the change and requires a separate
navigation/scroll protocol; do not claim host callback tests prove WebView order.
