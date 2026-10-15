# Outline/source jump audit — production unchanged

Baseline source: `05e74ca2906695ebe6e7ae4e2d68b6b536a24a47`.
Run with the installed JDK17 on PATH:

```sh
bash verification/outline_navigation/run.sh
bash verification/outline_navigation/run.sh --controls
```

The audit runner intentionally exits1: **17 cases / 32 assertions / 13 failures**.
The separate controls pass: **6 cases / 17 assertions**. This RED audit must not
be registered as a required passing host gate. Raw output is preserved in
[baseline-2026-10-15.txt](baseline-2026-10-15.txt).

The javac AST extractor copies37 exact production members, including the actual
`showOutline` row callback, `handlePreviewUri`, `jumpToSourceOffset`,
`scrollPreviewToSourceOffset`, final editor scroll, preview scheduling/acceptance,
full DOM identity predicates and scroll synchronization/retirement. It compiles
actual `LatestPreview`, `MarkdownRenderer`, `MarkdownStructure` and `UiTheme`.
No production body is rewritten. Outline offsets come from the real heading
parser, and readiness comes from the actual render/probe/acknowledgment path.

Observed failures:

- A pending offset applies to a newly acknowledged other tab, edited same tab,
  or A→B→A document context.
- Hidden Write mode retains an indefinite retry chain. A previously dequeued
  callback posts another retry after actual `stopPreview` and modeled destruction.
- Forty actual outline row selections while rendering waits retain40 independent
  retry callbacks, retain40 after another180ms, and eventually apply40 offsets.
- An old outline dialog row applies its old heading offset to another document.
- An unguarded submitted script can act on a replacement DOM in the modeled
  delayed-JavaScript schedule.
- The ordinary source jump's final editor post scrolls a changed tab/revision.
- An older actual preview→editor result overrides a newer Split outline jump:
  the intended viewport114 becomes0 because explicit source navigation does not
  retire the older shared scroll operation.

Passing controls preserve current Read outline navigation, current Write/Split
selection and viewport, one delayed same-document jump, actual `mdsrc:` Read→Write
routing, existing nonce-guarded sync rejecting a replacement DOM, and the final
position of successive ready Read jumps. Successive ready jumps are not labeled
incorrect merely because both execute: ordered user actions finishing at the
latest choice are legitimate.

The host models Android widget methods, DOM commit and selected callback delivery;
it does not run a browser JS engine. Its tiny script model recognizes only emitted
source-scroll calls and the existing DOM identity guard; a control executes that
actual guarded production script. Separate View/Activity queues expose scheduling
ownership, not a claim about WebView internal Handler implementation. The shutdown
case explicitly delivers an already dequeued callback, so no Android destroy/remove
queue guarantee is assumed. The stale dialog case simulates an already open dialog
followed by a context transition; ordinary modal touch reachability is not claimed.
Full `restoreActiveTab`, `setMode`, layout and TextWatcher are not executed here:
context transitions assign their source-observed state and run actual render/
invalidation methods. A future source-generation implementation must extend the
fixture to extract the actual watcher before claiming that lifecycle coverage.

The actual `mdsrc:` parser is exercised at invocation; URI callbacks contain only
a source offset, so this audit does not prove which DOM originated a late link
callback. External navigation is outside the fixture and throws if accidentally
invoked. No global bound for all existing scroll JS work is claimed. No Android,
Fold, IME, SAF, signed release, full suite or benchmark was run.

[Proposed bounded plan](../../docs/superpowers/plans/2026-10-15-outline-navigation.md)
requires orchestrator/independent review before production changes. Generated
classes remain under ignored `verification/build/outline-navigation`.
