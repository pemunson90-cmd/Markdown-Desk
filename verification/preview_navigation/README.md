# Preview navigation and printing audit (baseline intentionally RED)

Run from this worktree with JDK17:

```sh
PATH=/workspace/.command-library-env/jdk-17.0.16+8/bin:$PATH bash verification/preview_navigation/run.sh
```

Baseline source `116b180c80d7395a7c23297979f0cdc90ae9c7e2`:
**10 cases, 27 assertions, 11 failed safety assertions**, exit1. The output is
retained in [baseline-116b180.txt](baseline-116b180.txt). This is an investigation,
not a repaired product or a passing required gate. The runner is deliberately
not registered centrally. No production code changes are included.

The javac AST extractor copies15 exact production members without rewriting
bodies, including the entire `AnonymousClass2` WebViewClient, preview scheduling/
render/accept/invalidation/stop, print action/timer callback and editor-to-preview
synchronization. It compiles actual `LatestPreview`, `MarkdownRenderer` and
`UiTheme`. Android classes are local boundaries. No remote artifacts, WebView
uploads, real documents, browser engine, full suite or benchmark are used.

| Case | Observed baseline |
|---|---|
| Current completed load | Ready, full HTML equals actual renderer; control passes |
| Old finish while new tab's render waits | Sets ready and applies current restoration position;2 failures |
| Old finish after another load submission with same URL | Certifies readiness while new DOM not committed;1 failure |
| Two ordered page finishes80ms apart | First160ms reset clears second page's suppression early;1 failure |
| Late finish after stopPreview | Revives readiness and scrolls;2 failures |
| Print with current worker held | Creates adapter at350ms using old host DOM;1 failure |
| Print with renderer delivered, DOM not committed | Creates adapter before readiness;1 failure |
| Print A, then load/commit tab B before350ms | Uses B's title and HTML;2 failures |
| Ready current print | One adapter, expected captured HTML; control passes |
| Missing PrintManager from Write mode | Reports error but leaves Read mode;1 failure |

The host explicitly separates `loadDataWithBaseURL` submission from DOM commit.
It invokes page callbacks in selected orders and records the committed DOM at
adapter creation. **This does not prove Android delivers stale page callbacks in
those orders, nor that Android's print adapter snapshots the DOM when created.**
What is directly established is that the exact callback unconditionally changes
readiness/scroll and that the exact print timer invokes the current WebView
adapter without waiting for the current render or DOM. Current-page controls
ensure failures are not caused by an always-broken renderer or harness.

The overlapping-reset case uses two normally ordered page completions80ms apart;
it does not require an old callback to arrive after a newer one. Timer values
come from source; the held worker and DOM are simulated scheduling, not latency
measurements. `stopPreview` is actual; full Android `onDestroy`/WebView destruction
is not exercised. `rebuildBody` is a boundary retaining only its source-observed
invalidation/render order; no claim of full modes/layout coverage is made.
The link handler is stubbed and this fixture makes no link/security test claim.

[Proposed implementation plan](../../docs/superpowers/plans/2026-10-15-preview-navigation.md)
records the source findings, DOM-identity acknowledgment and the dedicated
single-operation print WebView alternative, resource bounds, error cleanup and
review questions. Root technical review precedes production implementation.
Original renderer HTML/style/source mapping and WebView security/link policy
must be preserved. Physical Android WebView, PDF output, print service,
Fold/IME/SAF and signed release gates are **NOT RUN**.

Generated classes stay under ignored `verification/build/preview-navigation`.
