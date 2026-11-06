# Unified Workspaces — active integration queue

**Checkpoint:** 2026-11-06, batch 00A (PR #37 read-only reconciliation plan; no implementation or merge).
**Authority:** Owner-approved full Unified Workspaces contract + existing features + relevant open bugs. Cross-installation private app-state export/import remains EXCLUDED by prior owner decision. No separate future roadmap features included.
**Execution:** One bounded batch per chat turn. Before any edit: read this file, check GitHub remote head and working tree, reconcile completed work with evidence, then perform the *next unblocked batch only*. After work: commit updated queue and evidence with exact refs. Do not repeat a completed batch or claim background progress. No routine GitHub Actions, no partial user-facing APKs, no destructive branch cleanup without provenance.

## Source checkpoint (read-only verified)

- `main` = `07660de8a7b2128bf00cb19f31711ab1d5531c54`, manifest 2.2.5 / versionCode 18: **NOT current accepted stable by policy**.
- This integration branch was forked from `feature/unified-research-parity-20261106` @ `6f9f8b6cc3003305f7a9c1697291a4f49b84126e`, manifest versionCode 38 / targetSdk 37 / minSdk 35. This is **development source, not release-accepted**.
- `feature/unified-workspaces-20261106`, `feature/unified-markdown-annotations-20261106`, and `work/full-unified-parity-20261106` retain unique work. Do not merge blindly; source overlap and missing dependencies are known.
- `fix/json-recovery-local-20261105` has code33 source and a reported signed binary patch; device acceptance and exact source/binary equivalence are **not established by the branch report**. Prior owner evidence explicitly confirms 2.2.12/code27 download; later accepted installed version needs evidence review. Do not infer acceptance from version number or a candidate signing audit.
- Draft PR #37 (audit efficiency) remains separate at `a3bc7866190fe72c0918679ef5735ddd5482a7ba`. **Batch 00A inspected its exact 21-file diff and verified none of those changes is included in this integration branch; adopt selectively, not blindly.** GitHub branch search reported 45 existing branches before this integration branch; exhaustive classification still pending.
- `docs/STATUS.md` is historical/rolling and may be stale; this queue owns the *current* integration checkpoint. Full contract exists on `work/full-unified-parity-20261106` at `docs/design/UNIFIED_WORKSPACES_COMPLETE_IMPLEMENTATION.md` and on the annotations line at `docs/development/UNIFIED_WORKSPACES_COMPLETE_IMPLEMENTATION.md`.

## Batch ledger (single queue, no parallel competing handoffs)

| Batch | State | Bounded objective | Exit evidence |
|---|---|---|---|
| 00 | **DONE** | Read-only repo baseline assessment; identify missing branches, stable-policy mismatch, owner scope; establish this integration branch/queue | `c299bf240ac66c9d4b84743646d8c30c4f3e6b92`; no app code touched |
| 00A | **DONE — PLANNING ONLY** | Inspect PR #37, current heads, prior queue, and classify all 21 changed files; prioritize adaptation without creating a competing status system | PR `#37` at `a3bc7866190fe72c0918679ef5735ddd5482a7ba`; this checkpoint; no code, workflow or merge run |
| 01 | **NEXT** | Verify latest actually accepted signed/installed stable APK and corresponding source, owner acceptance, and any binary-patch discrepancies; enumerate unique commits presently on `main`; prepare a *non-destructive* stable-main alignment proposal. Do not alter `main` until proof is sufficient | Exact SHA, version, certificate and acceptance evidence; clear blocker if not provable |
| 02 | PENDING | Inventory every branch and open PR/issue; classify unique source/tests/docs as preserve, integrate, historical tag/archive, or duplicate. Keep old refs until verified | 100% branch ledger and evidence refs |
| 03 | PENDING — **PR #37 FIRST** | On implementation authorization, adapt PR #37's verified source guard, manual-only workflow, tests, AST helper and deduplicated suites **to this queue and current integration head** before broader feature integration. Then reconcile four unified branches plus code37 and recover missing annotation classes/methods. Review conflicts semantically. | Adapted focused checks + full required host gate later on final head; no competing status system; compiling full app; no feature loss |
| 04 | PENDING | Complete all eight contract areas: shared source/session identity, continuous virtual reading, reader parity, notes/highlights/backlinks, full search/exact jumps, interop incl complete Markdown/PDF, Fold lifecycle, native integration | Requirement-to-code and behavior-test matrix |
| 05 | PENDING | Safety: exact selection anchors, process-wide journal synchronization, safe sidecar publication/recovery, transaction integrity, 700 MB archive behavior, source immutability, no silent truncation | Behavior/fault tests for all known failure modes |
| 06 | PENDING | Full native build with real Android 17/API37 SDK; JDK with actual `--release 11` support; execute complete required host suite including **reconciled PR #37 tests** (no test-only source/target workaround), Android 15/16/17, screen/insets/Fold, accessibility, process death, 700 MB runs, full old-editor regression | Clean build, full host suite and test artifacts tied to exact final integrated commit; no skipped hosted run counted green |
| 07 | PENDING | Independent integration QA, **recheck PR #37 guards against exact final source**, one canonical signed APK, in-place Fold7 acceptance; only then advance `main` to accepted release, tag, classify/retire old branches | Final owner-accepted source and stable branch alignment; no stale status/manifest; no duplicate workflow triggers |


## PR #37 — priority reconciliation disposition (batch 00A; **planning only**)

### Evidence and exact refs

- PR: https://github.com/pemunson90-cmd/Markdown-Desk/pull/37 (draft/open, not merged); head `fix/audit-efficiency-20261106` @ `a3bc7866190fe72c0918679ef5735ddd5482a7ba`; base `feature/unified-workspaces-20261106` @ `9daf5bedb5a6c51fc1ab5d2a1291a068b5f2efc4`. Its content was authored against older code38 source anchor `6b95d5876caf9d3d1f81bc9455bef4c90a0fdc67`.
- Read-only current heads: `main` `07660de8a7b2128bf00cb19f31711ab1d5531c54`; integration `c299bf240ac66c9d4b84743646d8c30c4f3e6b92` before this edit; Research Parity `6f9f8b6cc3003305f7a9c1697291a4f49b84126e`; Unified Workspaces `9daf5bedb5a6c51fc1ab5d2a1291a068b5f2efc4`; Markdown Annotations `6a7fd23e1370717a05d732b250974fc9ec815e05`; Full Parity `1d810b951d481e967521fa9705a5397b3d74be85`; code33 recovery `30a08de8171e710cf31afb876a8c8321787bef9e`.
- GitHub compare: PR head and integration **diverged**, PR 1 commit ahead of common base, integration 27 commits ahead; 21 changed paths in PR head versus this branch. **No PR #37 patch is presently integrated here.** Existing layout guards, extractors, build workflows and host scripts exist, but retain their pre-repair behavior; that is not equivalent to having the repair.
- PR report `docs/verification/AUDIT_EFFICIENCY_20261106.md` asserts 10 focused regression tests, shared extractor byte-identical fixtures, 450 preview/57 status tests with a JDK21 `-source 11 -target 11` wrapper. These are **PR-local claims**, not final integration evidence. Full host suite stopped on unavailable Java 11 release symbols, and current-source signed-device/Android SDK gates were NOT RUN. GitHub workflow run `42294112193` is **completed/skipped**, not passed; no successful hosted acceptance is established.
- Owner policy unchanged: this queue is the **only live integration handoff**, `main` must hold latest truly owner-accepted stable source only, private cross-install state export/import excluded, no unnecessary Actions, no intermediate APK.

### Disposition of **every** PR #37 changed path

| PR #37 path(s) | Current/compatibility finding | Planned reconciliation (only after implementation is authorized) |
|---|---|---|
| `.github/workflows/json-explorer-unsigned.yml` | **Needed, not present:** integration copy still runs on rapid matching pushes; code32-named artifact stale. | Remove push trigger, retain manual dispatch, permissions, concurrency, compile and focused checks; label unsigned artifact with exact SHA; check against newest workflow and no accidental secondary triggers. Do not dispatch now. |
| `docs/CI_AND_WORKFLOW_POLICY.md` | **Needed policy adaptation:** manual-acceptance explanation absent. | Amend existing policy for selective hosted validation; leave review-ready host PR gate and full-release gates intact; no separate policy. |
| `docs/CODEX_START.md` | **Needed but PR text is stale:** currently reads historical `STATUS.md` and old M0–M7 continuing mission; PR redirects to `CURRENT_SOURCE.json` and auto-continues the roadmap. | Point session resume to **this** authoritative queue first, inspect dirty work/current remote, run adapted source guard, execute only the authorized next batch; preserve historical STATUS/roadmap as references, not competing task queues. Never permit unprompted resumption while frozen. |
| `docs/CURRENT_SOURCE.json` | **Stale/incompatible as-is:** hardcodes old `feature/unified-workspaces` anchor and would create a second mutable current-status authority. | **Do not copy verbatim or maintain a second status registry.** When implementing, encode minimal machine-readable source identity once inside this existing queue (delimited block), or derive it from the queue and Git; make the guard read that record only. Keep acceptance and worker state explicitly unknown until evidenced. |
| `docs/STATUS.md` | **Historically useful but PR dynamic header conflicts with single handoff.** Its 71,521 prior bytes must not be lost. | Preserve historical text; do not import the PR's generated 'current' header. At most add a static pointer to this queue if necessary; task/checkpoint authority stays here. |
| `docs/verification/AUDIT_EFFICIENCY_20261106.md` | **Useful provenance only; old source/env results.** | Retain file verbatim or with immutable PR commit reference in historical verification, never elevate old PASS/skipped markers to final-gate evidence. |
| `verification/check_current_source.py` | **Needed, must adapt:** PR correctly guards Git ancestry, manifest/version, ref freshness, evidence and release-installed identity, but reads separate JSON and generated STATUS header, targets old branch. | Refactor guard to consume this queue's single canonical source metadata; check clean/dirty context, fetched origin integration tip versus checked-out HEAD, source manifest and evidence, and fail closed on stale refs. Keep it read-only. Verified installed/source APK and release identity must be evidenced, not assumed from ancestry. |
| `verification/check_layout.py`, `verification/check_live_appearance.py` | **Needed:** old check allowlist stops at code30 despite current code38 development manifest. | Retain all legacy identities and coverage; admit *only* exact current manifest identity after adapted queue-based guard succeeds, rejecting arbitrary version bumps. Run against final source. |
| `verification/preview_behavior/ExtractPreview.java`, `verification/status_behavior/ExtractStatus.java` | **Useful maintenance repair, not already adopted:** duplicate AST parse/slice logic in current versions; app code unaffected by PR. | Reuse shared exact-source parser only where it preserves member selection/completeness and generated harness bytes on the actual final Activity. No broad refactor. |
| `verification/source_extraction/ParsedJavaSource.java`, `ParsedJavaSourceCheck.java`, `README.md`, `run.sh` | **Useful new shared test helper; absent here.** | Adapt helper and self-tests as verification-only code; compare generated current and historical test harnesses byte-for-byte, verify source/Unicode/CRLF behavior. Do not alter production code to accommodate tests. |
| `verification/preview_behavior/run.sh`, `run_benchmark.sh`, `verification/status_behavior/run.sh` | **Needed if helper adopted:** current runners do not compile new parser. | Add shared parser to all affected javac inputs, including historical benchmark extraction; prove behavior equivalence and Java compiler compatibility. |
| `verification/run_host_checks.sh`, `run_current_phone_checks.sh` | **Needed, current host aggregate duplicates Downloads browser suite.** | Register adapted source guard, PR regression tests and parser tests exactly once per relevant aggregate; remove only the confirmed duplicate Downloads invocation. Preserve all other safety/owner-deferred-test labels; do not expand excluded portable private-backup gates. |
| `verification/test_audit_efficiency.py` | **Needed tests, but PR fixtures are tied to separate CURRENT_SOURCE/STATUS.** | Adapt 10 focused tests to this queue's canonical state, source-guard failure paths and current workflow; add regression for missing/stale queue identity and skipped-hosted-not-green classification. |
 
### Implementation priority, prerequisites and exit checks

1. **Planning now only:** Batch 00A records dispositions; no PR merge/cherry-pick, no source mutation, no CI run. PR #37 remains draft until selectively reconciled.
2. **Batch 01 still next:** verify owner-accepted stable baseline and the 11 unique `main` commits without overwriting any source; Batch 02 inventories all branch histories and stale handoffs.
3. **First implementation work after explicit authorization (Batch 03):** bring over/adapt the PR #37 protections before product-code conflict resolution. Keep single queue authority; reject original hardcoded branch/anchor. Preserve app code/features and PR historical evidence.
4. **Revalidation:** 10 focused PR regression checks (adapted), source ancestry/manifest/dirtiness and installed-release gate tests, old+new layout/appearance gates, extractor source-byte equality, preview 450 and status 57 assertions (or updated equivalent with explained count), two host aggregate entry points and duplicate-workflow audit. Use actual `javac --release 11` toolchain; no `-source/-target` workaround counted as parity. Verify no unintended hosted push jobs; skipped CI ≠ passing.
5. **Only at final integrated source:** full required host suite, complete API37 native build, relevant Android 15/16/17 and Fold7/product-safety acceptance, no regressions, canonical signed update. Record exact commit and observed test outcomes. Nothing from PR #37 alone fulfills this gate.


## Known, not deferred

1. `MainActivity` on annotations branch references missing `MarkdownReadingNotes` and `WorkspaceDeepLinks.isDocumentLink`; research branch drops Markdown annotation integration.
2. Research branch has instance-level `ArchiveJournal` synchronization; annotations branch adds cross-instance process lock.
3. Search currently lacks full scope/filters and exact Markdown line jump; source selection uses first quote occurrence in DOM text.
4. Unified transcript PDF/export parity, annotation back-navigation, sidecar recovery/cleanup, and end-to-end tab/reader parity unverified.
5. Research manifest targets API37; build default `android-37` path conflicts with docs naming installed `android-37.0`; no full current-source API37 build proven.
6. Existing `main` has 11 commits absent from code33 history, per compare; preserve them before any stable-main realignment.
7. Preserve original package `com.example.markdowndesk`, canonical signing certificate SHA-256 `a163cea419108eb595f3dcb01de16feca878368d4ffc6efc58eb9a8358b6be01`, all 25 splash assets, and in-place data/history.
8. Explicitly excluded: cross-installation private backup export/import; preserve its inert foundations and existing local recovery.

## Per-turn handoff contract

- Start: read **this file first** + inspect current GitHub ref/remote state. Work on **one batch**, avoiding long chains.
- Finish: update **this file once**, marking verified steps DONE, adding exact commit/PR/test outcomes, stating the next actionable item and outstanding blockers. Do not reset or duplicate other queues.
- Do not delete/force-push `main` or old branches. Don't call unverified host checks green, don't publish an intermediate APK, and don't use Actions unless locally unavailable independent validation requires it.
- User-facing response: 1–5 lines with **what changed, evidence link, blockers, next batch**. No work continues in the background.
