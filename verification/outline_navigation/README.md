# Outline/source navigation ownership

Run with the installed JDK17 on PATH:

```sh
bash verification/outline_navigation/run.sh
bash verification/outline_navigation/run.sh --baseline
```

The current runner passes **32 cases / 74 assertions**. It
extracts 53 exact production members plus the actual first editor TextWatcher,
including outline callbacks, source-link routing, search callers, shared editor
scroll, source/route generations, pending intent, DOM readiness and synchronization.
It compiles real `LatestPreview`, `MarkdownRenderer`, `MarkdownStructure`,
`SearchEngine` and `UiTheme`. No production body is rewritten and no context
predicate is replaced with an unconditional success.

The implementation retains one replaceable pending Read intent and zero polling
callbacks. Text/restoration, mode transitions, tab/view identity and shutdown retire
stale intent. A current intent survives ordinary render/font/palette changes and
applies only against the acknowledged current DOM. Explicit editor navigation owns
its final posted effect and retires overlapping preview synchronization again at
application. Search callers retain their selected match range; a missing editor
layout does not initiate a source-zero preview scroll.

`--baseline` intentionally exits1: **21 cases / 46 assertions / 15 failures**.
It uses immutable production source
`05e74ca2906695ebe6e7ae4e2d68b6b536a24a47` with the exact audit extractor, template and check
snapshots from `ba86c1763e385b2401681e57f7bb40afb77813a6`, retained in `baseline/`.
This historical diagnostic must not be registered as a required passing gate.
Its controls passed **8 cases / 27 assertions**. Raw output remains in
[baseline-2026-10-15.txt](baseline-2026-10-15.txt).

Historical failures, now covered by the current passing runner:

- Same-source Read→Write→Read revives an old outline row; Write→Split→Write
  revives an old deferred editor effect. Actual `setMode` leaves source unchanged.
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
position of successive ready Read jumps. Actual find-next/match-list callbacks
preserve the selected match range with `select=false`; ordinary same-source
render/font/palette changes preserve the final explicit editor effect. Successive ready jumps are not labeled
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
Full `restoreActiveTab` and Android layout are not executed here. Tab restoration
assigns modeled document state and invokes the actual watcher with suppression;
ordinary edits invoke the actual watcher without suppression. Actual `setMode`
runs with keyboard/preferences/chrome modeled. The watcher keeps its real source
revision/invalidation ordering; unrelated syntax/statistics/storage are boundaries.

Additional checks deliver sync responses, scheduled sync and already dequeued
editor callbacks between the explicit request and its final effect; those four
probes were RED against pre-fix source. Current controls include suppressed
identical-text restoration, same-text A→B→A, missing layout, current search range,
ordinary rendering/appearance survival and subsequent legitimate Split sync.
A lost readiness response leaves one pending intent with no polling or extra
readiness submission; no eventual-readiness guarantee is claimed.

The actual `mdsrc:` parser is exercised at invocation; URI callbacks contain only
a source offset, so this audit does not prove which DOM originated a late link
callback. External navigation is outside the fixture and throws if accidentally
invoked. No global bound for all existing scroll JS work is claimed. No Android,
Fold, IME, SAF, signed release, full suite or benchmark was run.

[Approved bounded plan](../../docs/superpowers/plans/2026-10-15-outline-navigation.md)
records the authorized scope and source evidence. Independent final source review
is separate from these worker checks. Generated classes remain under ignored `verification/build/outline-navigation`.
