# Markdown Desk — Current-phone local host verification (2026-11-07)

## Verdict

**PASS for all executed applicable host regressions** on the complete Unified Workspaces source snapshot, with **three Git-history-dependent checks NOT RUN** because the owner-supplied GitHub ZIP has no `.git` directory. The separately owner-deferred portable private-state test families remain **NOT RUN by design**. This is NOT Android/Fold7/on-device acceptance and NOT approval to create an APK.

## Exact inputs and environment

- Source input: owner-uploaded `Markdown-Desk-integration-unified-workspaces-20261106.zip`, SHA-256 `27ddce51a4219e1494bcec4aeb6279c7dc84264ab1bec5874c8076b9a870eaab` (extracted safely and compared with integration branch source blobs).
- Development branch: `integration/unified-workspaces-20261106`; passing harness/source blobs match remote head `89710800dffc5039ee6a7a80c1b5e856e9dfcea0`.
- Source evidence blobs: `MainActivity.java` `bb75467110a4605657b70e3b9fcc59cd5f35dcca`; `verification/preview_navigation/run_print.sh` `7b9f662bc96eeeced9970e7989b79f7693510b78`; `verification/save_lifecycle/MainActivity.template` `e3691aaf95715622a1d52839ecf6b265ad702b98`.
- Host JSON dependency: user-uploaded `android-json-0.0.20131108.vaadin1.jar`, **18,279 bytes**, exact pinned SHA-256 `dfb7bae2f404cfe0b72b4d23944698cb716b7665171812a0a4d0f5926c0fac79`. Stored in `verification/build/deps/` for host tests only; it is NOT an APK dependency.
- Linux container: OpenJDK 21 supporting `javac --release 11`, Python 3.13.5, Node 22.16.0. The separate API36/API37 real application Java+D8 compile checks were recorded earlier and did NOT assemble an APK.
- Consolidated local test log: SHA-256 `d75866afd6bc66d4a10d7f1a62e1fce9c25186f73190a12b1940e124ed5a767a`, **42,145 bytes / 801 lines**, ends with `RUN_EXIT=0`.

## How the local suite was executed

The current-phone host script was executed once without interruption after fixing isolated test harnesses. The runner was copied to an **untracked, temporary archive-only script** with precisely three Git-history-related commands replaced by explicit `NOT RUN` notices. An uncommitted `current_release_pair()` archive-mode shim performed manifest/queue/evidence checks for the static layout and appearance tests, but **did not simulate ancestry, fetched remote freshness, or owner-installed acceptance**. The production source guard in GitHub remains unchanged.

The historical syntax baseline was recovered from the owner's earlier `MarkdownDesk-2.2.4-source-candidate(1).zip`; its exact Git blob SHA is `31a7a7d875d8b9e50757d7597b3b5632af63505a`, equal to the repository's `v2.2.5-baseline` version. An untracked local syntax-runner copy used that file instead of `git show`. All production test sources remained the actual current source.

The consolidated run used an ordinary unprivileged `oai` account because root can bypass POSIX mode-bit denials and would falsely fail the maintenance `unreadable()` regression. The same real maintenance test run as that user passed **65 cases / 578 checks**. No production behavior was changed to satisfy root-specific testing.

**Result:** all executed checks PASS, process exit **0**. Highlights from actual logged output:

- Nine themes' contrast, renderer goldens (**467 assertions**), table-divider (**14,020**), footnote (**131/246**), autolink (**147**) and reader typography/positions (**80/160**) PASS.
- Preview (**450**), preview navigation (**66**), outline navigation (**74**), status behavior (**57**), DOCX core (**184**), DOCX publication and UI, line index (**46,564**), and syntax adapter (**39,137**) PASS.
- Annotation journal concurrency, conditional CAS edits, paged note search (**263** test records), identity-specific document links, exact search jumps, sidecar ownership, and transcript completeness PASS.
- Document protection (**31 cases / 272 assertions**), completion reservations (**7 / 86**), save lifecycle (**40 / 323**), save coordinator (**35 / 157**), 16 save restart boundaries, multiple history catalog/index/diff/restart suites, and history UI/protected-save interfaces PASS.
- History pinning (**909** discovery checks, **332** lifecycle checks, **20** restart boundaries), operation data (**3,434**), maintenance (**65 / 578**), journaled executor (**747**), operation recovery (**48** cases / **96** fresh reopens), executor interrupted operations (**44** / **88** reopens), and automatic retention (**261** checks + **38** kill boundaries / **76** reopens) PASS.

## Source and harness fixes found during local execution

- Production compiler collision: three decompiler-generated `lambda$buildInterface$8/9/10` names repaired at `745c6f1d75e268a910ed444e5cbff477b3f2bcef`; complete Java source and D8 passed against Android API37 and API36 without assembling APK.
- Missing `WorkspaceDeepLinks` in the isolated print-runner compilation fixed at `51492ba72a43ab4f148a693e5869c7ef83e2cc0e`; print ownership 134 checks and controlled renderer 18 checks PASS.
- `save_lifecycle` harness now models unrelated JSON/reading-notes picker boundaries, without modifying real save behavior, at `89710800dffc5039ee6a7a80c1b5e856e9dfcea0`; its **40 / 323** regression passed.
- Prior isolated renderer, preview/outline, sidecar and source-extraction harness fixes are already in the branch and were covered by this consolidated run.

## Still NOT RUN / NOT VERIFIED

1. **Git ancestry, fetched origin integration tip and historical baseline archive**: `verification/check_current_source.py`, `verification/test_baseline_inventory.py`, and `verification/check_baseline_inventory.py --baseline-ref v2.2.5-baseline` require real `.git` history, not a ZIP. No fake green claims.
2. Owner-deferred cross-install private-state backup/import/export families (18 categories), explicitly excluded from the current-phone gate; left untouched.
3. Native Android SAF/provider, persisted grant/revocation, WebView, Fold7/insets/rotation/accessibility, actual PDF pagination/output, 700 MB-class Android JSON import/reimport/cancel/restart and memory behavior, and physical in-place upgrade.
4. Entire approved Unified Workspaces feature/QA matrix must still be reconciled before authorizing the **single** full-release phone-testable APK. No intermediate signed/unsigned APK, no GitHub Actions, no canonical signing key use, and no `main` promotion occurred.

**Authority:** [Unified Workspaces active queue](../handoffs/ACTIVE_UNIFIED_WORKSPACES_QUEUE.md); this is supporting execution evidence, not a second status system.
