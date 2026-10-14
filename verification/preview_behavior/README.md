# Existing preview queue and invalidation verification

Run `verification/preview_behavior/run.sh` with JDK17 on PATH. This runs the
production LatestPreview and AST-extracted MainActivity preview fields,
watcher, schedule/render/accept/invalidate/document-key/stop methods and onDestroy.
`layout()` executes the first actual rebuildBody statement; the extractor requires
that statement to be preview invalidation. Remaining Android layout operations,
statistics, syntax, storage and save/session callbacks are explicit host boundaries.
Tab changes are simulated through active-tab state plus the actual watcher and
layout invalidation, not a full Android Activity lifecycle.

Observed 2026-10-14: **402 checks PASS**, `-Xmx128m`. Checks include unchanged180ms
cadence, no snapshot during100keystrokes, one delayed snapshot, hidden-mode
invalidation, suppressed setText, stale delivery before the next debounce,
2000replacements before worker execution and while a delivery is held, A-B-A
changes, tab closure, font/palette/mode/document acceptance, scroll capture,
shutdown and executor rejection. Full HTML equals the unchanged production
renderer for all9themes ×3font sizes. A real single-thread executor tests delivery
backpressure and close. A separate memory case replaces32pending8MiB Strings
while holding32MiB headroom and a queued delivery; it deliberately does not render
those8MiB inputs. This proves bounded pending retention, not renderer peak memory.

Existing renderer goldens separately PASS:467palette/render assertions,
12nested-list assertions and14,020table-divider assertions. Full production Java
compilation against installed SDK37.0 and existing generated R.java PASS; this
is not an APK build. Initial manual compilation used the nonexistent android-37
path; after correcting to android-37.0 and including shared/editor_bridge,
compilation passed with existing deprecation/unchecked notes. Root owns clean
integration checks and existing extraction-harness compatibility stubs.

## Design and memory/cancellation contract

[Plan](PLAN.md) records scope and source findings before production changes.
Rendering was already off UI in6630609. The helper now holds one active request
(rendering OR awaiting main delivery) and one latest pending snapshot. Holding
the active slot through delivery prevents an unbounded Handler HTML backlog.
Replacing/invalidation drops pending references immediately. Queued obsolete work
checks generation before rendering; obsolete running work completes naturally,
then drops its HTML. MarkdownRenderer does not implement cooperative interruption.
`close` rejects requests/results and drops pending references; Activity closes
before destroying WebView and calls shutdownNow to discard executor-queued work.

Worker inputs are immutable String, final-field UiTheme.Palette, font, document
ID, mode and revision. Renderer theme helpers are pure and read no mutable global
palette/preferences. All Editable/View/state capture and acceptance happens on
main. No worker touches a live Editable, View, or SharedPreferences. Generation
and Activity revision cover A-B-A transitions; the acceptance predicate also
checks document, palette identity, font, mode, finishing and shutdown.

This bounds request count, not single-render allocation: active source + renderer
temporaries/output + one pending source can coexist with the editor. Existing
source policy is unchanged; no invented document-size limit or latency budget.
OutOfMemoryError and other fatal Errors remain outside recovery guarantees.

## Matched host measurements

CSV files record actual observations on Linux6.18.44 x86_64, AMD EPYC9V74,
Temurin17.0.16+8; benchmark heap `-Xms128m -Xmx1g`. Source fixtures exactly match
`verification/benchmarks/HostBaseline.java`, with byte counts and SHA-256 recorded.
Only preview work was measured; no broad benchmark suite was rerun.

`run_benchmark.sh baseline` extracts the original6630609schedule/render methods
and watcher. Baseline renderer measurement has one warmup then3samples per size.
`run_benchmark.sh changed` measures the new scheduler only; `-1`renderer sample
columns mean unchanged renderer measurement intentionally NOT REPEATED.
The eight-request burst simulates8successive settled debounces while the worker
is stalled, then drains its worker/main callbacks. It includes8main-thread source
copies in both versions; no claim is made that settled snapshots become free.

| Words | Bytes | Baseline render median ms | Worker tasks before drain, old→new | Queued HTML deliveries, old→new | Burst drain ms, old→new |
|---|---:|---:|---:|---:|---:|
|10k|57,020|20.057|8→1|8→1|90.869→102.929|
|100k|570,200|79.433|8→1|8→1|662.600→120.186|
|500k|2,851,000|364.260|8→1|8→1|2604.747→447.790|

Queue counts are deterministic. Burst timings are single host observations with
different JIT warmup (baseline renderer warmed, changed path cold), so are not a
controlled latency comparison or device responsiveness guarantee. The10k changed
burst was slower; changed capture times were also higher. Raw CSV retains every
observation without turning those mixed timings into a regression threshold.
Heap columns are post-operation used heap, without forced GC, not peaks or
retained-heap comparisons. The functional128MiB test is separate memory evidence.

## Limits and separate existing behavior

Physical Android/WebView/IME/Fold, device input latency, GC and scroll/printing
acceptance are **NOT RUN**. Existing onPageFinished uses a common URL and does not
identify stale WebView navigation completions. The queue change rejects old HTML
before load but cannot retract a previously accepted load already inside WebView;
its scroll-ready callback ordering needs a separate navigation protocol/device
test. Existing350ms printing readiness behavior is also unchanged. No renderer,
HTML/options, print navigation protocol, source limits or UI modes were redesigned.
