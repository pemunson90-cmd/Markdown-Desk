# M0 UI accessibility and lifecycle audit — 2026-10-14

This is a bounded **static source audit**, based on `8462f79` and the current
working tree. It covers MainActivity controls, dialog semantics, target sizes,
and the boundaries of existing UI-related host fixtures. The original audit changed no runtime code; subsequent bounded repairs are
recorded below. TalkBack, accessibility-node inspection, Android measured
bounds, keyboard navigation, screenshots, phone/Fold and process-death acceptance
are **NOT RUN**. Source locations below refer to
`apps/MarkdownDesk/src/com/example/markdowndesk/MainActivity.java`; later edits
may shift their line numbers.

The subsequent label-only repair was verified test-first on the current working
tree: 16 helper/Appearance label assertions and a Focus exit-action assertion
failed before the source change; 43 extracted helper/Appearance assertions and
117 extracted mode assertions now pass. Java 11 syntax parsing also passes for
31 source files. This updates findings 1, 2 and 6 below without closing any
Android accessibility acceptance gate. Layout sizes and drag/release behavior remain unchanged. The later Split
operation repair adds accessible/keyboard adjustments; see the evidence below.

## Concrete findings, ranked for the next bounded slices

| Rank | Source evidence | Finding and action |
| --- | --- | --- |
| 1 — addressed in source/host | `showAppearance()` size controls; `appearanceButton()` descriptive overload | The four actions now have distinct descriptions/tooltips: “Decrease source text size”, “Increase source text size”, “Decrease reading text size”, and “Increase reading text size”. Tests execute the four actual AST-extracted construction statements and assert those names independently, while retaining `A−` / `A+` text and callbacks. Android speech/context inspection remains NOT RUN. |
| 2 — addressed in source/host | `createTopButton()`, `createPillButton()` | Both helpers now route the provided description into content description as well as tooltip. Extracted helper tests cover glyph/abbreviation controls and retain text, tooltip and listener identity. This establishes explicit name routing; it does not establish exactly what a screen reader speaks. |
| 3 — addressed in source/host; device follow-up | `rebuildBody()` at 1216–1235 | The horizontal Split divider now exposes “Resize Split panes”, focusability, SeekBar float range semantics (0.28–0.72), forward/back accessibility actions and a named “Center Split panes” action. Left/Right adjust by 0.05 and Home centers at 0.5. The pure controller and extracted production callbacks are host-tested; adjustments update complementary pane weights and persist. Existing 6dp layout, touch drag clamp and release/cancel persistence remain unchanged. Android action discovery, speech and focus behavior remain NOT RUN. |
| 4 — P2 | `utilityModeParams()` at 1133; `applyResponsiveChrome()` at 1377–1383; `addModeButton()` at 1163; `updateTabsUi()` at 1487–1500 | Eye/lock are explicitly 48×40dp; mode buttons and tabs are 38dp high; New document is 44×38dp. These requested bounds fall below the usual 48×48dp Android touch-target recommendation. No TouchDelegate or other target expansion was found in MainActivity. Evaluate target expansion in the parent or 48dp minimum layout heights; verify measured overlap and compact-screen fit before calling this fixed. Padding/margins alone do not establish an expanded hit region. |
| 5 — P2 | Top bar at 307, 332, 338, 344; formatting/read tools at 1174, 1181; gallery removal at 955–967; Appearance size controls at 2987–3000 | Additional requested target heights are 44dp for top controls and size controls, 42dp for toolbar tools, and 40×40dp for gallery removal. Shared `createButton()` minima are 40dp. Apply any general target-size change coherently; it can affect toolbar overflow and Fold/cover layouts. Native AlertDialog buttons and wrap-content rows require measured bounds; this source audit does not assign them fabricated sizes. |
| 6 — addressed in source/host; device follow-up | Eye creation; `toggleFocusMode()`; `updateModeChrome()` | Eye now exposes “Enter focus mode” / “Exit focus mode” and selected state through the actual chrome refresh method. Extracted tests cover on/off in Write/Read/Split. Focus still hides the entire mode bar, including this button; existing `onBackPressed()` exits Focus. Verify accessibility focus relocation after hiding chrome on a device. |
| 7 — addressed in source/host; device follow-up | `styleButton()` at 1318; `updateTabsUi()` at 1467–1485; `refreshAppearance()` at 2886–2910 | Document tabs now expose the full untruncated “Switch to …” name and explicit selected state, including refreshed selection and all twelve tabs; visible shortened/dirty text remains unchanged. Write/Read/Split now expose selected state. All nine Appearance themes and the three boolean options expose selected/checkable/checked state with names independent of glyph prefixes (`●`, `○`, `✓`). Live refresh updates these states in the existing controls; Save and other action buttons retain their existing semantics. Native CheckBox controls in Find retain checked state. |
| 8 — gallery addressed in source/host; fields P3 | Gallery removal at 950–967; Find fields at 3575–3581; word goal at 3092–3094 | Gallery removal now has the same contextual “Remove …” content description as its tooltip, verified from actual construction statements; visible `✕` remains. Find, replacement and goal fields have meaningful hints; they are not unlabeled empty fields. Persistent visible labels/label associations would make filled-field context clearer, but current TalkBack hint behavior needs inspection before treating it as a reproduced failure. |

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
| `verification/split_resize/` | 42 assertions execute exact AST-extracted tab/rebuild/adjust methods and gallery naming statements with host widgets; eight assertions execute the unchanged pure controller. Covers full tab names, refreshed selection, twelve tabs, accessible range/actions, keyboard changes, clamps, persistence, preserved touch release/cancel and compact/Write/Read pane structure. Initial red reproduced eight UI failures and a missing controller operation. JDK17/SDK37.0 Android source type-check passes. | Actual Android accessibility nodes/events, action discovery/speech, keyboard focus relocation, measured targets, real widgets/preferences, phone/Fold acceptance remain NOT RUN. |
| `verification/mode_behavior/` | Five AST-extracted production method bodies execute with fake widgets; 126 assertions cover mode/Focus visibility, mode selection transitions, lock state, Focus action/selection, Lock/Unlock labels and keyboard branch calls. Additional extracted helpers/Appearance construction statements establish explicit action-name routing (43 assertions). Exact extracted Appearance refresh/selection methods pass 4,963 assertions across all nine themes, three-option combinations, semantic names/selection/checked state and retained simulated window/control identity. | Actual accessibility nodes/events, measured targets, divider interaction, screen-reader focus, Android keyboard effects, and real `rebuildBody()` scroll/reflow. Its rebuild dependency is a counter. |
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
boundaries described here. Full document names/selection semantics and gallery removal naming are now
implemented and host-verified as well. Mode/Appearance selection states are now implemented and host-verified;
measured targets remain open. Do not claim speech
quality or real focus behavior from the passing host assertions, and do not
replace the still-required provider safety work or close M0.

The divider now has explicit adjustment operations and a tested ratio/persistence
seam. Evaluate target-size changes using Android measured bounds before changing
requested sizes or claiming expanded hit regions. Device acceptance must inspect the accessibility tree and TalkBack
navigation with Focus on/off, Write/Read/Split, locked/read-only source, all nine
themes, populated dialogs, larger system fonts, keyboard shown/hidden and folded/
unfolded layouts. Record each condition actually run. These checks remain
**NOT RUN**, and no UI/accessibility release gate is closed by this audit.

## Mode and Appearance selection repair — 2026-10-14

The test-first extension reproduced missing Write selection and Paper theme
selection before the source change. `bash verification/mode_behavior/run.sh`
now passes 126 mode, 43 label and 4,963 Appearance assertions. Appearance node
checks execute the actual accessibility delegate callback against a recording
node double; the delegate reads current selection rather than capturing stale
state. Mode selection is scoped to the three mode controls; Appearance selection
is scoped to theme/boolean choices. Existing visible glyphs, callbacks,
preferences, dialog construction, scrolling container, layout sizes, Focus and
Save behavior are unchanged.

JDK17/SDK37.0/build-tools36.0.0 unsigned build passes. Java 11 syntax parsing
passes for 35 files; existing layout and live-Appearance static guards pass.
The host scroll check establishes only that refresh does not mutate the simulated
window's scroll field; actual Android dialog scroll/focus continuity is **NOT
RUN**. Android node inspection, TalkBack speech/events, measured targets,
large-font behavior, keyboard focus, phone/Fold and process-death acceptance
remain **NOT RUN**. This repair closes no physical or release gate.
