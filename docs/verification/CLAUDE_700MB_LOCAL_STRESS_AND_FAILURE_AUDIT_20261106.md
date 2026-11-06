# 700 MB Claude export: offline import stress and failure audit
**Date:** 2026-11-06. **Status:** Reproduced defects and local stress results, not a corrected Android release.
**Owner situation:** `conversations.json` is almost 700 MB; installed code36 appears to time out while indexing. Fold7 screen shows only a static import status and header overlap in both postures.
**Source examined:** `ArchiveWorkspaceActivity`, `ArchiveDatabase`, and corrected `ArchiveStreamParser` on this branch. **No GitHub Actions, no APK built, no private Claude data used.**

## Tests actually run locally
All data synthetic. Linux Java 21 targeting Java 11 bytecode (parser), Java heap capped at 128 MiB. Python 3.13 SQLite FTS4 uses same table schema, three text parts per message, 128-character overlap and commit every 160 messages. Host results cannot be extrapolated as Android CPU or storage performance.

| Test | Result |
| --- | --- |
| Stream-parse 705,204,882-byte valid pretty JSON | **PASS**, 7.49 s, 1,400 conversations / 70,000 messages / 210,000 text segments / 696,570,000 content characters |
| Bounded parser heap, `-Xmx128m` | **PASS**; no full-archive deserialization |
| First SQLite import of 70k messages, 210k segments with FTS4 | **PASS**, 13.97 s, database **1,813 MB**, peak process RSS **138 MB** |
| Reimport and transactional replacement over a ready index | **PASS**, 24.8 s; database reaches **3,628 MB** |
| Third reimport and transactional replacement | **PASS**, 31.6 s; database reaches **3,703 MB** |
| Peak rollback-journal footprint during third replacement | **5,457 MB** combined database + journal (3,703 MB DB, 1,754 MB journal), excluding the ~705 MB source JSON |
| SQLite free-page inspection after replacement | About **48%** reusable free pages inside DB; disk file is not automatically shrunk by deletion |
| Interrupt after 160-message commit, then abort current transaction | **PASS (defect reproduced)**: old READY archive remains but **160 staging messages and 160 indexed parts** persist |
| Search incomplete staging with code36-style query | **DEFECT**: partial messages can appear without a `sources.status='ready'` join; the safer query returns zero |
| Simulate disk full with `PRAGMA max_page_count` | **PASS**: SQLite raises database/disk full and old committed data remains readable after rollback |

## Findings with severity

### Critical: temporary storage budget not checked
The actual SQLite database footprint depends on content and tokenizer. With our repetitive synthetic content, a 700 MB export generated a 1.81 GB index. Replacing it temporarily occupied **5.46 GB of DB and rollback journal** plus its source file (about **6.16 GB combined**). More varied real text can change the ratio; Android may choose different journaling and device storage behavior. The current app checks neither disk capacity nor expected index growth. Aborted staging data can further consume space. Do **not** claim that 6.16 GB is a guaranteed minimum/maximum on the Fold7. Treat it as a measured warning.

### High: import not owned across Activity destruction
`ArchiveWorkspaceActivity.importArchive()` uses an Activity-scoped single-thread executor, and `onDestroy()` shuts down that executor. Fold/unfold, rotation, resize or process recreation can interrupt import without a durable job or UI resume path. The activity has no import job restore. This is source-confirmed, not a runtime reproduction on Fold7.

### High: incompletely committed staging persists
`ArchiveDatabase.endMessage()` commits every 160 messages, but `abortImport()` only rolls back the active transaction; previous batches persist. The prior READY archive survives, but orphaned stages need safe cleanup. Code36 search does not fully isolate READY sources; tested safer source join on local SQLite.

### High: no user-visible progress/cancellation/diagnostics
Screen shows a static message during potentially gigabytes of disk I/O. No byte/message count, import step, last advancement timestamp, storage use, cancel or recover UI. **The source has no explicit timeout setting**; the owner's observed timeout may have been display sleep, lifecycle interruption, provider I/O, storage pressure, or process termination. No Android logcat yet identifies which.

### High: unstable import identity if provider URI changes
Archive ID is derived from the `content://` URI string, not archive content. The same 700 MB file selected through different providers or changed URIs can be imported as another archive rather than an update, creating duplicate indexes. Needs source identity/fingerprint and explicit dedup/relink behavior.

### Medium: phone layout / broader feature parity
Screenshots confirm top-bar overlap both folded and unfolded. Current Archive Research activity uses a single-column reader, 12-message paging and manual long-message continuation. Full read-only Markdown reader parity and inline annotations remain incomplete and are separate from importer readiness.

## Required gates before next user-facing APK
1. Introduce an import owner independent of Activity; preserve/cancel/resume intelligently across Fold posture, configuration and process lifecycle. Keep original archive read-only.
2. Show persistent, truthful progress (conversations/messages/bytes/stage), last advancement, storage usage, cancel/retry and useful failure errors. If a provider doesn't expose size, show unknown rather than invent an ETA.
3. Perform source-size/free-space preflight with conservative headroom and fail safely; consider a storage-efficient index design (FTS-only or targeted fields, fewer duplicated text copies, incremental durable staging) rather than requiring multi-GB temporary journal space.
4. Implement verified cleanup of obsolete staging records and safe transactional publish. Never delete the previous READY archive, personal annotations, or source JSON on failure. Re-import should reuse source identity and avoid duplicates when possible.
5. Require `sources.status='ready'` in all normal reader/search queries and test interruption at every 160-message commit boundary.
6. Fix Fold7 safe-area insets without repeating the code32 custom-insets crash. Test folded/unfolded, screen-off, app background, mid-import orientation switch, disk-full, provider timeout, and process death on actual Android.
7. Re-run local 700 MB parser+DB stress after changes; only then compile and sign one private canonical APK. No GitHub Actions by default.

## Evidence provenance
Exact local scripts and synthetic data were run in the model working container under `/mnt/data/archive_700mb_test`. The source dataset contains no owner files. Results are from logs `sqlite_700mb.log`, `reimport700.log`, `reimport700_third.log`, `Archive700MBTest` and `failed_import_checks.py`. The 700 MB synthetic dataset and multi-GB SQLite database remain local temporary test fixtures and are not committed or uploaded.
