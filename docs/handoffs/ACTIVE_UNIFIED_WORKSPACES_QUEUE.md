# Unified Workspaces — active integration queue

**Checkpoint:** 2026-11-06, batch 00 (source audit / durable handoff setup).
**Authority:** Owner-approved full Unified Workspaces contract + existing features + relevant open bugs. Cross-installation private app-state export/import remains EXCLUDED by prior owner decision. No separate future roadmap features included.
**Execution:** One bounded batch per chat turn. Before any edit: read this file, check GitHub remote head and working tree, reconcile completed work with evidence, then perform the *next unblocked batch only*. After work: commit updated queue and evidence with exact refs. Do not repeat a completed batch or claim background progress. No routine GitHub Actions, no partial user-facing APKs, no destructive branch cleanup without provenance.

## Source checkpoint (read-only verified)

- `main` = `07660de8a7b2128bf00cb19f31711ab1d5531c54`, manifest 2.2.5 / versionCode 18: **NOT current accepted stable by policy**.
- This integration branch was forked from `feature/unified-research-parity-20261106` @ `6f9f8b6cc3003305f7a9c1697291a4f49b84126e`, manifest versionCode 38 / targetSdk 37 / minSdk 35. This is **development source, not release-accepted**.
- `feature/unified-workspaces-20261106`, `feature/unified-markdown-annotations-20261106`, and `work/full-unified-parity-20261106` retain unique work. Do not merge blindly; source overlap and missing dependencies are known.
- `fix/json-recovery-local-20261105` has code33 source and a reported signed binary patch; device acceptance and exact source/binary equivalence are **not established by the branch report**. Prior owner evidence explicitly confirms 2.2.12/code27 download; later accepted installed version needs evidence review. Do not infer acceptance from version number or a candidate signing audit.
- Draft PR #37 (audit efficiency) remains separate. GitHub branch search reported 45 existing branches before this integration branch; exhaustive classification still pending.
- `docs/STATUS.md` is historical/rolling and may be stale; this queue owns the *current* integration checkpoint. Full contract exists on `work/full-unified-parity-20261106` at `docs/design/UNIFIED_WORKSPACES_COMPLETE_IMPLEMENTATION.md` and on the annotations line at `docs/development/UNIFIED_WORKSPACES_COMPLETE_IMPLEMENTATION.md`.

## Batch ledger (single queue, no parallel competing handoffs)

| Batch | State | Bounded objective | Exit evidence |
|---|---|---|---|
| 00 | **DONE** | Read-only repo baseline assessment; identify missing branches, stable-policy mismatch, owner scope; establish this integration branch/queue | This commit; no app code touched |
| 01 | **NEXT** | Verify latest actually accepted signed/installed stable APK and corresponding source, owner acceptance, and any binary-patch discrepancies; enumerate unique commits presently on `main`; prepare a *non-destructive* stable-main alignment proposal. Do not alter `main` until proof is sufficient | Exact SHA, version, certificate and acceptance evidence; clear blocker if not provable |
| 02 | PENDING | Inventory every branch and open PR/issue; classify unique source/tests/docs as preserve, integrate, historical tag/archive, or duplicate. Keep old refs until verified | 100% branch ledger and evidence refs |
| 03 | PENDING | Reconcile four unified branches plus code37 and PR #37 on this integration line; recover missing annotation classes/methods. Review conflicts semantically | Full source graph + compiling baseline; no functionality lost |
| 04 | PENDING | Complete all eight contract areas: shared source/session identity, continuous virtual reading, reader parity, notes/highlights/backlinks, full search/exact jumps, interop incl complete Markdown/PDF, Fold lifecycle, native integration | Requirement-to-code and behavior-test matrix |
| 05 | PENDING | Safety: exact selection anchors, process-wide journal synchronization, safe sidecar publication/recovery, transaction integrity, 700 MB archive behavior, source immutability, no silent truncation | Behavior/fault tests for all known failure modes |
| 06 | PENDING | Full native build with real Android 17/API37 SDK; Android 15/16/17, screen/insets/Fold, accessibility, process death, 700 MB runs, full old-editor regression | Clean build and test artifacts tied to exact commit |
| 07 | PENDING | Independent integration QA, one canonical signed APK, in-place Fold7 acceptance; only then advance `main` to accepted release, tag, classify/retire old branches | Final owner-accepted source and stable branch alignment |

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
