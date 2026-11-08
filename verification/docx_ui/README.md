# DOCX Activity UI fixture

Run with installed Java17: `bash verification/docx_ui/run.sh`.

The AST extractor inserts exact production DOCX fields, state/dialog/capture/result
helpers, lifecycle methods, existing real theme helpers, and the actual overflow17,
command-palette16, Workspace and request1006 branches. The backend coordinator and
Android widgets are controlled boundaries. The Capture constructor and its URI
validation are extracted unchanged from the actual DocumentDocxExport helper, so
Editable admission-before-copy is tested through the real validation path.

72 Activity UI checks plus 23 actual picker-proxy/manifest checks: BUSY no capture, Editable pre-copy limits, captured title/source/sourceURI
plus all12 original tabs, all12 current URIs forwarded for coordinator union, exact
one-use picker state, picker-induced pause preserved, publishing/generation/offer
pause cancellation and native save-before-cancel ordering, destroy/recreated/null/
duplicate/malformed results, stale READY/failure/DONE/dialog actions, all seven
warning/failure kinds, all nine actual title/message/button/background palettes,
verified-only success, and no export mutation of native editing state.

Publication worker/stage/provider binary readback and actual captured/current URI
union are covered by the separate reviewed publication runner. This UI fixture
records publish admission; it does not claim to emulate provider writes or prove
heap disposal. No Office/APK/device SAF acceptance is established by host tests.

The non-exported standard DocxPickerActivity uses a waiting-capable translucent
theme. Each launch carries a new UUID and filename only. Main accepts results only
with its exact canonical UUID; the proxy takes that UUID from its own original
Intent, never provider extras. Tests run the actual class for default canceled
results on create/recreate, launch-once, success/cancel/failure, wrong/duplicate
callbacks, provider-forged nonce/extras, filename bounds and private manifest policy.
The old-A/new-B success/cancellation regression and Main SecurityException launch
regression both failed before the correction. Original handoff is preserved at
`.superpowers/campaign-resume/docx-ui-pre-proxy-report.md`.
