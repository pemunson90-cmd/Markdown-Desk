# Claude archive import: reported timeout and Fold7 continuity audit

Status: **Diagnosis / plan only. No APK authorized by owner yet.**
Date: November 6, 2026. Working signed test app: 2.4.1 / versionCode 36.
Owner reports a stalled or timed-out import; previous screenshots show the static "Reading conversations and building local search..." screen on a Fold7 both open and closed. The precise kind of timeout (screen sleeping, app disappearing, explicit exception, or device stopping import) has not yet been specified. **Do not claim that the app itself enforces a deadline.**

## Code-confirmed findings (code36 importer path)
1. `ArchiveWorkspaceActivity.importArchive()` starts the parse/database ingest on an Activity-owned `Executors.newSingleThreadExecutor()` and displays a **static** progress string. There is no persisted job ID, byte/message count, cancellation control, watchdog, restart/resume, or user-visible step indicator.
2. `ArchiveWorkspaceActivity.onDestroy()` calls `io.shutdownNow()`. The ArchiveWorkspaceActivity manifest lacks explicit configuration-change handling. Android can recreate Activities on fold/unfold, rotation, or resize; that can interrupt import with no successor worker. The Activity state bundle also does not save `viewMode=import` job state.
3. `ArchiveDatabase.beginImport()` creates a staged `sources` row, and `endMessage()` commits every 160 messages. `abortImport()` rolls back only the current transaction; previously committed staged source/conversation/message/FTS records remain on disk. Earlier local SQLite testing reproduced these leftovers. The partially indexed staging data is distinct from the prior successfully READY archive.
4. The **code36 installed APK** was built as an incremental parser-Dex fix on the older code34 archive APK, not a fresh full source build of all audit fixes. The earlier code34 global search SQL did not join READY `sources`, so staged results may leak into search even though the normal conversations list filters on READY. There is a separate audited source correction but not a confirmed installed correction.
5. There is **no explicit timeout** in the examined `ArchiveWorkspaceActivity.importArchive()` or `ArchiveStreamParser` source. The user-observed timeout cannot be assigned to a specific caller without a device timestamp/error/log. It may refer to the screen locking, file-provider I/O, OS process management, or perceived lack of progress.
6. UI does not apply safe-area window insets; header/status bar overlap is visible both folded and unfolded. Existing archive reader is single-column even when unfolded.
7. Import has no source-size estimate or free-storage preflight; FTS4 indexes text segments and can use significantly more disk space than the JSON source, so low storage is a real failure path.

## Local, narrowly scoped performance evidence
Using the corrected `ArchiveStreamParser` in a standalone Java 11-target build, the host machine parsed a synthetic, formatted 30.58 MiB archive with 800 conversations and **28,000 messages** in **0.561 seconds** (`-Xmx256m`); no Android I/O, ContentResolver or database indexing was involved. The separate Python/SQLite FTS4 synthetic insert of 28,000 one-segment messages finished in ~0.87s on the host, producing a ~75.86 MiB DB. **Neither measurement predicts Fold7 latency**; they establish only that host parser throughput alone need not explain long imports. No GitHub Actions.

## Fix design before next test APK
- Put import state in an owner independent of `ArchiveWorkspaceActivity`; use a durable, restartable job for large imports, with an explicit progress/stop status and a visible notification when running long in the background. Choose a locally implementable Android job mechanism after checking platform constraints, rather than layering a timer onto the Activity. WorkManager long-running foreground support is one option, but Android 16 quotas require care.
- Provide `conversations parsed / messages indexed / bytes consumed / stage`, last progress time, import ID and approximate source size, cancellation and resume/restart. No arbitrary default time cutoff. If progress stops, show diagnostics; if failure occurs, preserve the prior READY index and recover/clean staged data.
- Support safe interruption (Fold posture, rotation, screen lock, background and process death), preserving `content://` permissions, staged checkpoints, and integrity/correct source revision.
- Ensure every search/library/reader query filters completed READY sources, never incomplete stages; clean orphaned staged messages, FTS and parts transactionally without affecting the last complete archive.
- Add available-storage/size preflight and low-storage failure handling; do not demand unlimited permissions or upload a private Claude export.
- Fix Fold7 top/bottom insets and build a truly adaptive single/two-pane reader without reintroducing the earlier JSON Explorer custom-insets crash.
- Test with real Fold7 folded/unfolded, screen off/restore, Back/cancel, import twice, intermittent provider read, 160-message staging boundary, process death, not enough storage, invalid JSON, and original index preservation; run local APK compile and signature gates before offering a new installer.

## Specific clarification still needed from owner
When they say import **timed out**, was it the phone display turning off/locking, a visible Android/Markdown Desk error, or the app being killed/returning to Home? Ask once; do not assume.

## Sources
- `apps/MarkdownDesk/src/com/example/markdowndesk/ArchiveWorkspaceActivity.java`
- `apps/MarkdownDesk/src/com/example/markdowndesk/ArchiveDatabase.java`
- `apps/MarkdownDesk/src/com/example/markdowndesk/archive/ArchiveStreamParser.java`
- Official Android large-screen configuration and continuity guidance and long-running worker guidance (see Android Developers).
