# Outline/source navigation — bounded audit and proposed repair

Status: audit only. Production remains byte-identical to root checkpoint
`05e74ca2906695ebe6e7ae4e2d68b6b536a24a47`. Root owns authorization and independent
review before a fix. Scope is the existing M0/M2 navigation correctness work;
printing, storage/history, runtime backup and release/version changes are excluded.

## Source and evidence

- `MainActivity.scrollPreviewToSourceOffset` (3956) checks only `previewReady`.
  Its anonymous View callback recursively retries every180ms with only an offset;
  successful delivery emits `window.mdScrollToSource(offset)` without DOM identity.
- `showOutline` (3906) captures headings from real `MarkdownStructure`, then each
  row reads live mode and dispatches an old offset without source-context checks.
- `jumpToSourceOffset` (3877) intentionally changes Read→Write. Its
  `scrollEditorToOffset` (3884) posts an unowned final editor effect, unlike the
  guarded preview-sync last hop introduced by Task1.
- Existing `beginPreviewScroll` / `releasePreviewScroll` and
  `lambda$scheduleEditorSyncFromPreview$75` reject stale operation results, but
  explicit source jumps never advance that operation or retire pending sync work.
- The actual editor watcher (441) calls `invalidatePreview` before its suppressed
  load return. `restoreActiveTab` (1731) calls editor.setText for each restoration;
  mode changes rebuild/invalidate. `previewRevision` also advances on ordinary
  `renderPreview`, so it is not a stable text-revision identifier for a jump waiting
  through debounce→render without an edit.

`verification/outline_navigation/run.sh`: **17 cases /32 assertions /13 RED**.
`--controls`: **6 cases /17 assertions PASS**. The fixture extracts37 exact source
members and uses real preview worker, renderer and heading parser. Failures cover
stale tab/revision/A→B→A, hidden/shutdown retry retention,40 independent retry chains,
stale dialog offsets, delayed old-DOM commands, stale final editor posts and an
older Split sync response overriding a newer outline choice. No production change
or Android/browser ordering guarantee is inferred from the controlled schedules.

## Proposed minimal implementation

1. Add a main-owned source-navigation revision advanced unconditionally at the
   start of the actual editor TextWatcher, including suppressed tab/recovery loads.
   Do not reuse renderer revision or statistics internals. No snapshot/hash/whole
   text comparison is added per keystroke. Capture a small immutable source context
   (document ID, source revision, editor/preview object identities and intended
   mode) when an outline opens or an explicit jump is requested. A→B→A restoration
   must invalidate old contexts even if the final tab ID/text match.
2. Replace recursive180ms polling with one replaceable pending Read-outline intent
   containing only context and offset. Try it immediately against the real current
   acknowledged page, otherwise consume it at `acknowledgePreviewPage` after initial
   restoration. No polling timer or new render request/snapshot is required. A
   valid request must survive ordinary debounce→render generation changes when its
   source context/route is unchanged. Content/tab/mode/view/shutdown changes retire
   it. Palette/font changes may retain the source offset, but application still
   requires the exact current page/options predicate. A lost readiness callback
   may leave at most this one pending intent until context retirement; no repeated
   work or unconditional liveness promise.
3. Validate an outline row's captured context before dispatch. A stale row must
   neither change mode/selection nor queue a jump. Keep the actual heading parser,
   row styling, dismissal, Read behavior and Write/Split source selection. The
   legitimate source-link Read→Write transition happens before capturing the final
   editor-effect context; do not reject that intended mode change as stale.
4. At explicit source navigation, retire pending editor↔preview sync runnables,
   pending preview-owned editor effect and the shared scroll operation before
   submitting the new effect. A new explicitly owned editor runnable is replaceable
   and rechecks context/operation/editor identity at its final scroll, including an
   already dequeued runnable. Use the existing page/operation-scoped suppression
   release where a current Split page exists; do not leave flags stuck in Write.
   Preserve subsequent legitimate editor→preview synchronization. The actual old
   held-response fixture must stay RED until this shared retirement is implemented.
5. Guard Read-outline JavaScript inside the DOM with the exact current page nonce
   and complete state, as existing sync does. Keep renderer HTML, baseURL, WebView
   settings, `mdsrc:` parsing and external link policy unchanged. This bounds pending
   outline intent/retry work, not every preexisting WebView JS call. Ordered already
   submitted ready jumps may both execute; do not invent a guarantee that a later
   tap retracts an earlier browser command.

Before source edits, independent review must challenge the new source-revision
lifecycle, debounce→render handoff, same-ID restoration, existing source-link
Read→Write semantics, scheduled versus already-running sync retirement, and the
last editor post. Extend exact extraction with the actual watcher before validating
that new revision hook. Add controls for same-document delayed rendering, pending
appearance changes, legitimate Split synchronization after explicit navigation,
source edits/suppressed loads, late shutdown delivery and latest pending offset.
No raw `mdsrc:` callback origin-proof is claimed: its current URI carries only an
offset; validating which DOM emitted an already delivered link event is a distinct
protocol question beyond this audit's deferred-effect findings.

## Verification and limits

Keep the committed raw baseline as an intentional RED diagnostic. Once authorized,
make current focused checks GREEN, run affected preview/outline/source fixtures
and whole-Java SDK37 compile, then obtain exact-source independent approval. Root
owns central runner registration and combined gates; no unchanged broad suites or
benchmarks here. Physical Android/WebView timing, modal reachability, scroll/IME,
Fold/Focus/Read/Write/Split and release acceptance remain NOT RUN candidate gates.
