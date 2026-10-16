# Markdown Desk rescue continuation — 2026-10-16

## Scope and source
- User-approved continuation without Codex usage, on isolated branch `rescue/md-unsigned-build-proof-20261016`. Do not overwrite, reset, or merge the original draft PR #24 branch `codex/operation-data-capture` without separate integration review. Original production baseline is 2.2.5/versionCode 18, package `com.example.markdowndesk`.
- Rescue branch began at original development commit `1a17223a74acc4f465edc1209c59db2674affbf1`.
- All app behavior added in rescue is committed by `6f360ae1eecf63a31416c3f30be34b29d60e981a`. The MainActivity source blob is `343905d2c3d1bffb45d2f71a45177001b8a10b80`, verified unchanged through the later test harness iterations.
- Newly app-wired: explicit Favorites (documents/saved folders, permission-aware open, shortcut-only removal), saved source-cursor reading places/named bookmarks with bounded verified relocation, and read-only protected history storage status. Reader bookmarks intentionally require positioning in Write/Split; Read mode must not silently claim an unverified cursor.
- Sources for previously integrated folders, search, DOCX export, history/recovery and editor behavior were preserved.

## Verified source/runtime evidence
- Broad *owner-scoped current-phone* host regression PASS plus successful unsigned Android SDK37 package: [run 38860335030](https://github.com/pemunson90-cmd/Markdown-Desk/actions/runs/38860335030), source `6f360ae1eecf63a31416c3f30be34b29d60e981a`. This suite explicitly marks 18 deferred cross-installation private-state test families NOT RUN. Original unmodified `verification/run_host_checks.sh` remains available, and its portable frame copy budget failure was recorded separately; it was not converted to a pass.
- Latest focused current-phone/source integration PASS with package/checksum: [run 38868883698](https://github.com/pemunson90-cmd/Markdown-Desk/actions/runs/38868883698), source `7d13b7c324d23d29682af933190d4262d34d7a47`. This run includes **real extracted Activity** tests: Favorites UI 31 checks, Reading Places UI 31 checks, folder browser UI 120, folder search UI 104, reader storage 160 and SaveLifecycle 40 cases/323 assertions; no failures.
- A prior unsigned artifact was retrieved from GitHub and independently inspected. These are unsigned development artifacts, never a user-installable signed release. Host Android UI simulation is not physical Fold7/SAF provider/IME/TalkBack/WebView acceptance.

## Release and safety holds
- User-approved only cross-installation private-state export/import deferral. M0–M7 retained current-phone obligations still require reconciliation: safe workspace file operations, remaining reader/editor features, opt-in retention settings/scheduling, performance benchmarks, final host/device/migration/rollback/upgrade and physical Fold7 acceptance. This new code does **not** establish that these are complete.
- Keep automatic retention and destructive executors unexposed until their integrated, consent-bound production journey passes. Read-only usage and bookmark controls are not permission to clean data.
- Canonical release certificate SHA-256: `a163cea419108eb595f3dcb01de16feca878368d4ffc6efc58eb9a8358b6be01`. Private `MarkdownDesk-release.p12` and signing instructions exist in the user's Library archive `MarkdownDesk-2.1.0-Full-Build-Bundle.zip`. Do not publish keys/passwords to GitHub, Actions, logs, or source. Sign only in a trusted private final-release session, after applicable gates and a monotonically increased version code.
- No canonical signed APK, in-place upgrade test, or physical Fold7 acceptance has occurred in this rescue.

## Exact next work
1. Confirm latest public branch heads and work ownership; preserve all current rescue/PR24 history.
2. Reconcile remaining M2–M7 **current-phone** product obligations against the working app source; prioritize independently testable user journeys (including safe workspace operations and native acceptance), not deferred portable export.
3. Execute focused host tests during implementation, then repeat the complete owner-scoped current-phone gate against the integrated candidate; preserve separate NOT RUN deferred-suite classifications and all genuine RED safety diagnostics.
4. Independent source/code safety review, canonical private signing, installed-package upgrade/readback, and actual Fold7/TalkBack/SAF acceptance are release holds. No debug/unsigned artifact is an approved deliverable.
