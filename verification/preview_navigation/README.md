# Preview DOM identity and scroll ownership

Run with JDK17 on PATH:

```sh
bash verification/preview_navigation/run.sh
bash verification/preview_navigation/run.sh --baseline
bash verification/preview_navigation/run.sh --print-hazards
```

Current navigation checks: **18 cases / 66 assertions PASS**. The exact-source
baseline selector reads committed main116b180 through `git show`; its **11 cases /
28 assertions / 13 failures** remain reproducible (intentional exit1). Baseline
includes duplicate completion resetting user scroll, same-page scroll reset and
the last deferred editor-scroll hop. Original investigation output10/27/11 is
also retained as historical evidence, before splitting navigation from printing.

`--print-hazards` deliberately runs a separate RED diagnostic: **5 cases /
14 assertions / 5 failures**. Fixed350ms printing still can create an adapter
before rendering/DOM readiness, use a later tab's title/content, or leave Write
mode changed when PrintManager is missing. Printing production code is unchanged;
this diagnostic must not be registered as a required passing navigation gate.

The javac AST extractor copies35 exact production members (including the nested
WebViewClient), the actual render/accept/invalidate/stop methods, navigation
state/acknowledgment, both scroll directions and the final editor-scroll method.
It compiles actual LatestPreview, MarkdownRenderer and UiTheme. Android classes
are local boundaries; only synthetic source strings are used. Current-page/full
renderer and ready-print controls remain in place. The existing preview suite
also passed438 checks with only the root navigation attribute removed for full
HTML comparison across9themes ×3font sizes.

## Protocol and ownership

Each accepted render gets a root `data-md-preview` attribute from its immutable
revision. Renderer source/output API, styles, source mapping, base URL and link/
security policy remain unchanged. A page finish merely requests a DOM probe:
readiness requires complete DOM and the exact JSON-encoded identity. Acceptance
rechecks page object, view, revision, tab, mode, palette/font, shutdown and Activity
state. Initial scroll restoration is idempotent. One physical readiness probe across ALL pages plus one replaceable current
intent prevent callback accumulation during rapid replacement. The old callback
must actually return before the latest page can probe; invalidation never releases
that slot. A lost callback therefore stalls readiness fail-closed until it returns
or the Activity instance is replaced. This is readiness-probe backpressure only:
existing scroll JavaScript calls are not claimed globally bounded. Coalesced
current finishes retry after an old DOM response.

Invalidation clears readiness immediately and cancels owned timers/deferred editor
scroll. Scroll operations have separate tokens, so same-page old JS responses and
120/160ms resets cannot clear newer suppression. DOM-side sync scripts check the
page identity; preview-to-editor applies its guard again at the final editor post.
Native scroll/session persistence and captureActiveTab also require a current
acknowledged page, preserving saved coordinates while another DOM is pending.
At invalidation only, an outgoing acknowledged page with matching view/document/
revision may supply its last viewport even after font/theme/mode values changed;
this preserves appearance-change scroll before retiring the old options. Native
pending-DOM persistence was observed RED (2cases/4assertions/3failures), and the
first strict outgoing-options capture attempt was RED on font/palette/mode before
this correction. A100-revision held-response fixture proves one readiness probe.
Normal source jump/outline editor scrolling is unchanged. The separate outline
preview polling helper remains an existing unguarded retry path, outside this
slice; it has not been relabeled fixed.

The page metadata holds no source or HTML. Wrapping does allocate a full-size
StringBuilder plus a result String while the original renderer result is still
live. WebView may copy/retain more internally. This is a material linear main-thread
cost after render acceptance, not zero-overhead metadata or a phone memory bound.

## Matched wrapper measurement

`run_benchmark.sh` measures only the actual extracted Activity wrapper, using the
unchanged10k/100k/500k M0 fixture construction. Source hashes match prior CSVs.
Observed host: Linux6.18.44 x86_64, Temurin17.0.16+8. Each input renders once to obtain HTML (renderer speed is not remeasured), then
three wrapper warmups and three samples run at `-Xmx128m`. Reflection overhead is
included; raw CSV retains variance. Reference columns are the old no-wrap reference
assignment measured in nanoseconds, not renderer timings or a meaningful speedup
ratio. Inputs/outputs remain checked byte-equivalent apart from the trusted marker.

| Words | Source bytes | HTML chars | Added chars (19-digit token) | Wrapper samples ms |
|---|---:|---:|---:|---|
|10k|57,020|63,093|38|0.011 / 0.012 / 0.011|
|100k|570,200|605,965|38|0.311 / 0.312 / 0.329|
|500k|2,851,000|3,019,089|38|3.907 / 0.391 / 4.764|

[Raw observations](wrapping-2026-10-15.csv). No GC/peak-memory attribution,
physical latency threshold, benchmark acceptance budget or renderer improvement
is claimed. Production tokens normally have fewer digits than the measured19.

## Limits

The host separates load submission from DOM commit and deliberately delivers
callbacks/results in selected orders. It models DOM-probe return values and
records generated sync JavaScript; it does not execute a browser JavaScript
engine. The print adapter records host DOM at creation; Android may lay it out
later. No Android callback URL/order or real spooler snapshot guarantee is inferred.
`rebuildBody` preserves only its source-observed render/invalidate order, and link
handling is a boundary here; full layout/link/security/device acceptance is not
claimed. Production settings and handler are reviewed unchanged.

[Plan](../../docs/superpowers/plans/2026-10-15-preview-navigation.md) records the
reviewed Task1 scope and separately reviewed Task2 proposal. Whole production Java compilation passed against installed Android SDK37 with
JDK17 `--release 11` after normal merge of main d0b02d7 (history UI); existing
generated R.java was reused because resources are unchanged. Compilation was repeated after the final ownership corrections. This is compile evidence, not an
APK or Android runtime test. Physical Android
WebView, PDF/service behavior, Fold/IME/SAF and signing/release gates remain NOT RUN.
Affected save-lifecycle40cases/323assertions and history UI84checks pass after
truthfully modeling acknowledged-tab preview ownership at their existing boundary.
The navigation fixture executes the actual full predicate; these adapters do not
claim browser acknowledgment coverage. No remote artifacts, uploads, full suite
or broad benchmark repeat were used.
Generated classes stay under ignored `verification/build/preview-navigation*`.
