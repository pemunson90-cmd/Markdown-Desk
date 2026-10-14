# M0 UI accessibility and lifecycle audit — 2026-10-14

This is a bounded **static source audit**, based on `8462f79` and the current
working tree. It covers MainActivity controls, dialog semantics, target sizes,
and the boundaries of existing UI-related host fixtures. No runtime code was
changed for this audit. TalkBack, accessibility-node inspection, Android measured
bounds, keyboard navigation, screenshots, phone/Fold and process-death acceptance
are **NOT RUN**. Source locations below refer to
`apps/MarkdownDesk/src/com/example/markdowndesk/MainActivity.java`; later edits
may shift their line numbers.

The subsequent label-only repair was verified test-first on the current working
tree: 16 helper/Appearance label assertions and a Focus exit-action assertion
failed before the source change; 43 extracted helper/Appearance assertions and
117 extracted mode assertions now pass. Java 11 syntax parsing also passes for
31 source files. This updates findings 1, 2 and 6 below without closing any
Android accessibility acceptance gate. Layout sizes and divider behavior remain
unchanged.

## Concrete findings, ranked for the next bounded slices

| Rank | Source evidence | Finding and action |
| --- | --- | --- |
| 1 — addressed in source/host | `showAppearance()` size controls; `appearanceButton()` descriptive overload | The four actions now have distinct descriptions/tooltips: “Decrease source text size”, “Increase source text size”, “Decrease reading text size”, and “Increase reading text size”. Tests execute the four actual AST-extracted construction statements and assert those names independently, while retaining `A−` / `A+` text and callbacks. Android speech/context inspection remains NOT RUN. |
| 2 — addressed in source/host | `createTopButton()`, `createPillButton()` | Both helpers now route the provided description into content description as well as tooltip. Extracted helper tests cover glyph/abbreviation controls and retain text, tooltip and listener identity. This establishes explicit name routing; it does not establish exactly what a screen reader speaks. |
| 3 — P2 | `rebuildBody()` at 1216–1235 | The Split divider **already has** “Drag to resize Split panes”; its label is not missing. It is a plain 6dp-wide View with touch-only movement, no explicit focusability, accessible adjustment action, range semantics, or keyboard adjustment. Add an accessible way to increase/decrease/reset the split ratio while preserving the existing 0.28–0.72 bounds and release-time persistence. Merely changing the label does not make drag functionality operable. |
| 4 — P2 | `utilityModeParams()` at 1133; `applyResponsiveChrome()` at 1377–1383; `addModeButton()` at 1163; `updateTabsUi()` at 1487–1500 | Eye/lock are explicitly 48×40dp; mode buttons and tabs are 38dp high; New document is 44×38dp. These requested bounds fall below the usual 48×48dp Android touch-target recommendation. No TouchDelegate or other target expansion was found in MainActivity. Evaluate target expansion in the parent or 48dp minimum layout heights; verify measured overlap and compact-screen fit before calling this fixed. Padding/margins alone do not establish an expanded hit region. |
| 5 — P2 | Top bar at 307, 332, 338, 344; formatting/read tools at 1174, 1181; gallery removal at 955–967; Appearance size controls at 2987–3000 | Additional requested target heights are 44dp for top controls and size controls, 42dp for toolbar tools, and 40×40dp for gallery removal. Shared `createButton()` minima are 40dp. Apply any general target-size change coherently; it can affect toolbar overflow and Fold/cover layouts. Native AlertDialog buttons and wrap-content rows require measured bounds; this source audit does not assign them fabricated sizes. |
| 6 — addressed in source/host; device follow-up | Eye creation; `toggleFocusMode()`; `updateModeChrome()` | Eye now exposes “Enter focus mode” / “Exit focus mode” and selected state through the actual chrome refresh method. Extracted tests cover on/off in Write/Read/Split. Focus still hides the entire mode bar, including this button; existing `onBackPressed()` exits Focus. Verify accessibility focus relocation after hiding chrome on a device. |
| 7 — P2 | `styleButton()` at 1318; `updateTabsUi()` at 1467–1485; `refreshAppearance()` at 2886–2910 | Mode/tab selection and Appearance toggle/theme states are represented through colors and glyph prefixes (`●`, `○`, `✓`), without explicit selected/checkable semantics. Give selection/toggle state a consistent accessible representation. Tabs also visually shorten names; use the full document name for an accessible action label. Native CheckBox controls in Find already carry checked state and should retain it. |
| 8 — P3 | Gallery removal at 950–967; Find fields at 3575–3581; word goal at 3092–3094 | Gallery removal has visible `✕` and a contextual “Remove …” tooltip but no explicit content description. Assign the contextual name. Find, replacement and goal fields have meaningful hints; they are not unlabeled empty fields. Persistent visible labels/label associations would make filled-field context clearer, but current TalkBack hint behavior needs inspection before treating it as a reproduced failure. |

The lock button is **not missing its action label**: it starts with “Lock editing”
at 375, then `applyEditorLockState()` changes it to “Unlock editing” / “Lock
editing” at 1409/1413. `updateModeChrome()` disables it in Read mode or for a
read-only document. Existing host fixtures cover enable/alpha and lock effects,
and now assert those existing label strings while preserving the transitions.
A read-only/disabled explanation can be evaluated
with actual accessibility-node feedback rather than inferred from tint alone.

## Existing coverage and its lifecycle boundary

| Current evidence | What it establishes | What remains untested |
| --- | --- | --- |
| `verification/mode_behavior/` | Five AST-extracted production method bodies execute with fake widgets; checks cover mode/Focus visibility, lock state, Focus action/selection, Lock/Unlock labels and keyboard branch calls. Additional extracted helpers/Appearance construction statements establish explicit action-name routing. | Actual accessibility nodes/events, measured targets, divider interaction, screen-reader focus, Android keyboard effects, and real `rebuildBody()` scroll/reflow. Its rebuild dependency is a counter. |
| `verification/contrast/` and renderer goldens | Numeric foreground/background checks and actual extracted styling/renderer behavior within their documented host boundaries. | Android paint, disabled-state presentation, accessibility announcements, target sizes, font-scale clipping and screenshots. Contrast alone does not establish accessible controls. |
| `verification/check_layout.py` and `check_live_appearance.py` | Static source guards for retained scroll container/dialog refresh paths and configuration-call structure. | Showing a dialog, focus continuity, measured layout, preserving actual scroll during a theme change, or surviving activity recreation. Source-string guards are not Android UI execution. |
| `verification/reader_layout.cjs` | Chromium reader overflow/layout proxy. | Native chrome/dialogs, Android WebView differences, TalkBack, IME, orientation/Fold and screenshots. |
| Session/recovery fixtures and `verification/recovery_loader/` | Store persistence plus extracted loader/merge outputs and callback forwarding. | Actual UI restore callback, recovery dialog visibility, all-12-tabs recovery offer, process death, Activity/Handler timing, selection/scroll/lock restoration in real widgets. |

Lifecycle source evidence is narrower than device acceptance:

- `onCreate()` builds the interface, shows the splash and starts restoration
  (169–188). The manifest handles `screenSize`, `smallestScreenSize`, `uiMode`,
  `orientation` and `keyboardHidden` through configuration callbacks.
- `onConfigurationChanged()` captures the active tab, rebuilds the body and
  refreshes responsive chrome (199–204). It does not prove focus, dialog scroll,
  caret, WebView position or IME continuity across a real configuration change.
- `onPause()` captures the active tab, requests dirty recovery and session writes
  (207–213). Those asynchronous persistence requests are not a completed-write
  guarantee before process termination.
- `onDestroy()` removes Handler callbacks, destroys the preview and shuts down
  executors (217–224). The host fixtures do not exercise already-running worker
  callbacks racing destruction or accessibility focus recovery.
- `onNewIntent()` forwards to intent handling (192–195); Back exits Focus before
  ordinary unsaved-document handling (235–245). Neither path has end-to-end UI
  coverage in the extracted mode harness.

## Recommended next implementation and test seam

The semantic-label slice above is implemented and verified within the host
boundaries described here. Remaining bounded label work includes full document
names/selection semantics and the gallery removal action. Do not claim speech
quality or real focus behavior from the passing host assertions, and do not
replace the still-required provider safety work or close M0.

Give the divider an explicit adjustment operation with a separately tested
ratio/persistence seam, followed by target-size changes with Android measured
bounds. Device acceptance must inspect the accessibility tree and TalkBack
navigation with Focus on/off, Write/Read/Split, locked/read-only source, all nine
themes, populated dialogs, larger system fonts, keyboard shown/hidden and folded/
unfolded layouts. Record each condition actually run. These checks remain
**NOT RUN**, and no UI/accessibility release gate is closed by this audit.
