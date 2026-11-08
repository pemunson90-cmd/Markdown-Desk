# Actual Workspace folder journey

Run with existing JDK17, no downloads:

    PATH=/workspace/.command-library-env/jdk-17.0.16+8/bin:$PATH bash verification/workspace_ui/run.sh

The AST extractor copies the actual Activity picker/browser methods and state,
real onPause/onDestroy bodies, and the tree-result branch of onActivityResult.
The test compiles the REAL WorkspaceRootStore, WorkspaceTreeReader and process-wide
WorkspaceDirectoryLoader with Android JSON. Only Android UI/context/grant/provider
boundaries are controlled. The provider query executes on the real broker worker;
the delivery Handler is queued rather than inline. No pretend browser/controller
or fake history implementation replaces production logic.

120 checks exercise Add-folder callback/picker flags; cancellation/temporary grant;
only returned read/write persistence; security/missing persisted grant/commit-false
refusal; remembered root immediately queried; saved-root reopen; nested/Up/Back;
exact child URI through ordinary openDocument(uri,0,true) after browser disposal;
current revoked root badge/removal; failed partial/complete empty/limited states;
ancestry cycles and depth33 refusal; unknown MIME explicit text confirmation;
stale row after pause/same-frame reload; real query ignoring cancellation across
Activity destruction/recreation; BUSY and independent safe-save/session queue;
actual cleanup before retry; destroyed picker result refusal. Review regressions
add dismissed/replaced/current confirmation ownership and nine real UiTheme
palettes: title/message/actual dialog background/button foreground and every new/
recycled saved-root row. The fixture executes actual styleDialog and history title
helper; color setters record values, not no-ops. Ordinary document
open is a recording boundary, not a second document parser. Existing DocumentIO
42 fixtures and save lifecycle40cases/323 assertions validate those unchanged paths.

Exactly one current Page and bounded32 frames are app-owned; frame holds only
reader-bound directory handle/label/exact URI. Rows capture Entry and generation,
not old Pages. Cancel/dismiss/root change/pause/destroy invalidate generation,
clear rows/page/frames and call Ticket.cancel without reclaiming broker ownership.
The dedicated static main Handler cannot be swept by Activity.main destruction.
Root registry/grant operations use existing local I/O; provider enumeration uses
only the separate process-wide broker. No pending browse queue, new per-Activity
worker, recursive search, file writes, child grants or permission release.

Retained latest-output.txt includes the UI journey, focused normal document/save/
preview checks and actual SDK37 Java compile. No broad build, APK, installation,
physical SAF/USB/removable-provider/grant-restart acceptance or Android dialogs/
predictive Back/rotation/Fold visual behavior claimed. Those phone gates remain
separate. Broker's own focused packet covers queued main delivery and low-level
slot/post/worker rejection behaviors beyond this UI journey.

The actual onPause regression installs a finite blocking provider cancellation
listener on the real loader ticket signal. An observer records local tab capture,
recovery/session scheduling and two queued writes before releasing cancellation.
The original cancel-first order fails; the local-save-first order passes. The
fixture uses its own recording CancellationSignal boundary, not a common stub edit.
