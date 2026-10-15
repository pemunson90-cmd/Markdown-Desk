# History action integration checks

This focused host harness extracts **17 actual MainActivity methods unchanged**
using javac AST source positions. It includes the new history methods, actual
capture/switch/restore-tab methods, and actual pause/destroy bodies. It also
extracts the production strict encoder. Missing or overloaded extraction targets
fail rather than silently using a copied implementation.

```sh
PATH=/workspace/.command-library-env/jdk-17.0.16+8/bin:$PATH verification/history_ui_integration/run.sh
PATH=/workspace/.command-library-env/jdk-17.0.16+8/bin:$PATH verification/history_ui_integration/run_mutations.py
```

The normal runner executes real HistoryController, ProtectionStore, shared
capacity, identity, catalog, index and diff implementations over temporary native
files with real sync and atomic moves. It reuses only the existing controller
fixture's native disk/setup helpers; it does not execute that suite or replace any
history backend. All native fixture directories are removed after each test.

Observed on 2026-10-14, JDK 17 with `--release 11` and `-Xmx128m`:
**84 assertions PASS**, including a second invocation through the literal
`checkout with spaces` path after quoting the discovered stub source array.
The tested MainActivity Git blob is
`73be90c5076916c8221b29ab7bcdae0d36ef7d15`.
The three optional copied-source sensitivity checks also pass. Scope:

- Controlled I/O and main delivery order: tab A→B→A, changed exact URI, closed tab,
  finishing/destroyed Activity, newer history request and late older diff result.
  Queued I/O may finish after shutdown, but cannot display a new history dialog.
- Actual onPause invokes the modeled recovery/session boundaries and permits a
  still-live history result. Pause is not claimed to cancel a request. Actual
  onDestroy removes pending main callbacks and shuts down the modeled executor;
  the Android `isDestroyed` transition is modeled after that method returns.
- Cancelled/stale naming confirmations create no native snapshot. A confirmed
  naming job retains exact captured BOM/CRLF/Unicode bytes after newer edits and
  a tab switch, preserving dirty text, 12 tabs, locks, read-only and View state.
  Invalid blank labels are reported without a new snapshot.
- A real 203-version list exercises first/middle/last pages (100/100/3 versions),
  previous/next buttons and full count. Five actual checkpoint names render only
  three names plus the exact remaining count. Returned version/name lists reject
  mutation. All four actual native status classes have explicit visible wording,
  including an orphan original snapshot.
- Actual adapter bindings set row colors for newly created and recycled views;
  custom titles and action messages use the actual palette text for all nine
  production palettes. API 26 avoids the accessibility-heading call; API 28 uses
  it. These assertions inspect modeled property assignments, not Android paint.
- Selecting an actual row revalidates membership before the modeled copy-picker
  boundary. Foreign versions and stale callbacks cannot launch the picker.
- Actual row selection invokes real comparison, exposes exact line-ending markers,
  byte/BOM information and selectable text. A real over-cap snapshot produces the
  visible incomplete title, reason and full byte count.

The optional mutation runner edits temporary copies only. It removes the request
check, removes the adapter text color, and restores the misleading orphan label;
all three must fail a specific behavioral assertion. These are **new-API test
sensitivity probes**, not evidence of failures in an old shipping implementation.
The two UI findings were fixed before this harness was written. The separate
controller owned-byte race has its own genuine observed RED/GREEN evidence.

Android dialogs, views, queues, picker dispatch, Activity lifetime queries and
editor boundaries are doubles. Button clicks invoke the actual listener and then
dismiss the original dialog, so replacement dialogs survive normal Android button
ordering. `styleDialog` is a boundary no-op: this harness verifies the new explicit
history title/list/message assignments, not existing button/window styling.
The controller is supplied already initialized. Mandatory startup failures,
provider-save/optional-index behavior, source-preserving restore and rejection of
the original restore destination are covered by the separate actual-method
save_lifecycle and recovery_loader suites, not duplicated here.

NOT RUN here: Android dialog rendering/window tokens, TalkBack traversal, IME,
physical pause/process death, Fold, SAF provider behavior, app-UID durability,
whole-application build, performance benchmark, signing or release acceptance.
No Focus/layout or editor mutation is introduced by these fixtures; broader mode
compatibility remains an integration/device gate. No production source is edited.
