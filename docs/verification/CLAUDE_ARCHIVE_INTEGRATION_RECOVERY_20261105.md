# Claude Archive integration recovery and local QA
**Status:** Under active development; **NOT a release-ready APK**.
**Date:** 2026-11-05 (America/New_York).
**Review/source base:** `feature/claude-archive-integrated-20261105` @ `887583d08f20bfbfef95a38591f6a3651499439e`.
**Audit branch:** `fix/archive-integration-audit-20261105` (inherits all integrated code; no merge or replacement of working code33).
**User constraint:** No GitHub Actions for routine work. None initiated by this recovery/audit.

## Recovery finding
The earlier UI session DID write 14 source commits, including ArchiveStreamParser, ArchiveJournal, ArchiveAnchors, ArchiveMarkdownIndexes, ArchiveDatabase, ArchiveSidecarPublisher, ArchiveWorkspaceActivity, feature wiring/manifest code34, and host core tests. They survived on the branch. No verified full-source compile, signed code34 APK, or Fold7 device acceptance was found. PR #36 remains the older code32 JSON-explorer development branch, not this integrated archive branch.

## Local test results (no hosted CI)
**PASS:** `javac --release 11` + `ArchiveCoreTest` on recovered Library-stage Java classes:
- >305-message conversation; >120,000-character single message; nested text/image blocks; Unicode.
- Invalid/unterminated/trailing JSON rejected.
- Append-only annotation journal update/delete/corruption detection.
- Per-chat Markdown index; master links-only projection; manual prose around managed region retained.

**PASS:** Python 3's SQLite FTS4 with a 103-message ready dataset, 260 matching segments in one message, and a fourth hidden/incomplete source:
- Stable distinct-message result pages of 60+43;
- incomplete source absent;
- snippets contain highlighted matches.

**PASS:** Extra Java regression: two IDs sharing 120 initial characters generate different stable sanitized Markdown sidecar filenames.

**NOT RUN:** Full Android compilation, release-manifest byte check against built APK, on-device API26/35/Android16 execution, Fold7 folded/unfolded UI, real Claude export, crash/logcat, full existing Markdown editor regression suite. The tool-provisioned container has JDK21 and the owner's Android 36.1 **build-tools archive**, but lacks an Android platform `android.jar` and SDK/emulator image. No full APK can truthfully be called tested/signed from the audited branch in that environment.

## Bugs reproduced/fixed in audit branch
1. **Partial imports leaked into global search:** SQL search omitted `sources.status='ready'` guard. Reproduced with FTS4 staging fixture. Fixed with a ready-source join.
2. **Search lost hits:** 100+ matching segments from one message exhausted raw SQL LIMIT before deduplication, causing apparently missing results. Fixed with FTS4-compatible `matched` CTE selecting first matching segment per *message*, then ranked/chronological ordering and LIMIT/OFFSET on distinct messages. Tested locally.
3. **Search results jumped to a nearby window:** SQL now returns matched part ordinal; UI opens the exact message and segment, with WebView find highlight.
4. **Bad/outdated links jumped to message zero:** missing IDs now return -1; link navigation fails explicitly and preserves source identity.
5. **Bookmarks silently marked first message on displayed page:** UI now requires exact message selection when multiple messages are visible.
6. **Focus mode wasn't actually chrome-free:** hides archive header/navigation while reading and Android Back exits Focus first; status bar visibility restored outside reader.
7. **Cross-message selections could be misanchored:** selection now verifies both endpoints are inside the same message body, excludes speaker/date chrome, and refuses empty/unlocated text.
8. **Useful search filters were present only in database API:** added speaker and inclusive YYYY-MM-DD date controls in reader search.
9. **Sidecar name collision:** previously truncated IDs were used for filenames; now file names include a digest of the entire stable ID and have a corresponding test.

All source edits committed with `[skip ci]`; no build or signed artifact was created from these edits.

## Important remaining feature gaps relative to approved owner request
- Reader presently uses **12-message navigation windows** and 20-part long-message stepping; it provides access to later content but not yet one seamless, lossless continuous scroll. A future owner build must not be represented as meeting no-cutoff reading until this is corrected.
- Search covers indexed message text with exact phrase, speaker and date; **annotation text, saved links, project/tag collections, saved searches and robust query navigation** are not yet comprehensively indexed/visible.
- Selected passage overlays are stored but **in-reader visual highlight display, inline note panel and two-way cross-chat picker/backlinks** are incomplete.
- True Fold7 two-pane notebook/reader layout, reading timeline, keyboard/TalkBack matrix, and complete source-change reconciliation are incomplete.
- Archive importing indexes in batches but does not yet provide complete visible progress/cancellation/restart/recovery controls; interrupted provisional records require cleanup.
- SAF Markdown index staging preserves old copies but lacks complete provider-failure recovery and convergence tests; large existing index files and user-made edits require explicit conflict handling.
- External imports and private canonical key/material must remain offline. Original code33 working JSON Explorer and all Markdown editing behaviors must be regression-tested before a candidate APK.

## Next eligible implementation sequence
1. Reconcile exact audit branch and finish search/anchor/sidecar safety gates with reproducible local tests, without touching working editor modules.
2. Complete truly continuous untruncated reader, full linked annotation UI, stable precise selections and backlinks; implement practical Fold layout.
3. Full source Android build with an authorized compatible `android.jar` SDK/platform; run host safety/renderer/UI tests and ideally device/emulator acceptance. GitHub Actions only if genuinely unavoidable and justified.
4. Privately sign with canonical certificate `a163cea419108eb595f3dcb01de16feca878368d4ffc6efc58eb9a8358b6be01`, increasing versionCode above code33; verify alignment/signature and in-place data continuity on Fold7.
5. Present **one** owner-installable candidate only once the above gates pass. Keep public GitHub free of signing secrets and private Claude exports.

## Continuity rule
The new audit branch **extends**, rather than replaces, all prior integrated work. Do not merge into stale `main` or discard current M0–M7 features. The older signed code33 recovery remains the installed/verified JSON fallback until a newer owner-accepted APK exists.
