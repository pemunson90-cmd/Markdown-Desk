# Current project status

## M0.1 / issue #1 — imported and reproducible; baseline signed artifact independently verified; merged/tagged

As of **2026-10-14**, baseline merged at `d1288a80d4fc174c2b2a62805642218f5fa2d516`, source tag `v2.2.5-baseline`; completed
[PR #9](https://github.com/pemunson90-cmd/Markdown-Desk/pull/9):

- Exact 2.2.5 archive received; SHA-256
  `f57fe34ac489eb1f651fd1f6fe840ffddf189f4322c4bdc32b3ad285c187e6ef` verified.
- 89 files audited/imported; all runtime Java/shared bridge/resources/manifest
  and 25 splash images byte-identical. Four documented build/test scaffold edits;
  their archive originals retained. No feature/runtime/version changes.
- **Artwork publication separately cleared by owner** for all 25 bundled images.
  Private signing files, archive ZIP, generated binaries and caches excluded.
- PM correction applied: SDK37.0/build-tools36.0.0 are compatible and tested;
  36.1.0 was historical release tooling, not a hard minimum. Reused installed
  JDK17 with its bin directory on PATH; no SDK/JDK/key replacement.
- Genuinely unsigned build/audit PASS, with all signing inputs removed.
- Host suite PASS: supplied guards/syntax, 27 renderer cases, 49 SplashStore
  assertions, 15 independent functional fixtures. Original stale SplashStore
  expectation failure and missing host JSON dependency are documented/resolved
  in test scaffolding; production source unchanged.
- Two fresh committed-source builds produced byte-identical unsigned APKs:
  `bd9f99cb99acfe8b584f47c2f6edb1418c7deb70148d5f5087c3f6bb3f638d57`.
  Compiled package `com.example.markdowndesk`, 2.2.5/code18, min26/target35.
- Focus/View and other existing modes retained exactly; runtime phone/Fold and
  provider/upgrade acceptance **NOT RUN**. No connected device.
- **Independent PM signed-baseline verification completed** using the archived original 2.2.5 APK (no signing secrets): APK SHA-256 `ec5e790343b45a56f2580f3f5ec97eae3fb3096e10e012e58eb76bc89e683601`, canonical signer SHA-256 `a163cea419108eb595f3dcb01de16feca878368d4ffc6efc58eb9a8358b6be01`, v2/v3 verified, manifest 2.2.5/code18, ZIP/alignment and 25 splashes passed. Evidence is attributed to the PM's separate check in [PR #9](https://github.com/pemunson90-cmd/Markdown-Desk/pull/9#issuecomment-6164996417), **not** to the Codex host run.
- PM approved merging the **unchanged-source baseline** after final document reconciliation; physical Fold/SAF/IME/TalkBack/upgrade acceptance remains **NOT RUN** and must gate future production releases. PR #9 is merged and source-only tag `v2.2.5-baseline` is pushed; issue #1 is closed. No new signed release or hosted CI run.

See [audit/evidence](M0_1_AUDIT.md) and [reproducible commands](BUILD.md).

## Active campaign — M1 backend foundation; runtime history still pending

Protection [PR #10](https://github.com/pemunson90-cmd/Markdown-Desk/pull/10) merged
at `eea037763259e37537b24f402142e860b095b768`. Active local branch:
`codex/m1-safe-retention`. The separately reviewed issue #3/#4 slice
[PR #11](https://github.com/pemunson90-cmd/Markdown-Desk/pull/11) merged at
`c42de9d84c223feba773c849da7c7a21cac724d0` after required CI run38595113245 passed.
The owner renewed the instruction to finish the entire app continuously; do not
hand back after a successful task or PR checkpoint. Remote PM changes were
fast-forwarded through `d8d595a`; the updated AGENTS/CI policy and
[PR #10 engineering instruction](https://github.com/pemunson90-cmd/Markdown-Desk/pull/10#issuecomment-6171465179) govern this checkpoint.

Implemented locally, independently reviewed:

- Immutable private snapshot/preparation/completion records with integrity hashes,
  exact bytes, file sync, atomic same-directory commit and directory sync. Quota
  is64MiB, fails closed, counts staged records and reserves completion bytes and
  entry slots. No automatic deletion or retention/history acceptance claimed.
- Actual save coordinator protects the captured edit before reading the source,
  protects the observed original, binds conflict consent to its hash and captured
  revision, rechecks both source and stored protection, writes once and verifies.
  Failed/uncertain writes retain both versions; no automatic rollback/retry.
- Ordinary saves, pending Save As and protected-copy restoration use the
  coordinator. Identity changes only after verified success. Newer edits remain
  dirty. Workspace exposes protected versions and restore to a new copy; same
  source URI is rejected and existing tabs/records are retained.
- Legacy draft is protected independently of session parsing/full12-tab state.
  If protection fails, legacy replacement/clear fails closed. Storage repair can
  retry without a restart. Unmatched recovery is explicitly surfaced.
- Streaming envelopes/lazy disk-backed snapshot handles remove the reviewed
  memory failure. Three paired8MiB saves (~48MiB persisted) and six complete
  restores pass at128MiB host heap with32MiB retained headroom. This is a host
  regression criterion, not an Android memory/latency budget.
- Strict captured UTF8 rejects unmatched surrogate text before queuing a save;
  valid Unicode/BOM/CRLF retains exact bytes. Legacy encode API remains intact.
- Previously reviewed preflight/metadata, nested lists, wide tables, palette
  foregrounds, independent recovery loading and status snapshots remain intact.
  Tabs expose full accessible names/selection; gallery removal is named; the
  existing Split divider supports accessible/keyboard adjustment with unchanged
  drag bounds. Nine palettes and25owner-cleared assets remain unchanged.

Merged foundation evidence (production save/UI paths unchanged by the new backends):

- Full host suite PASS.42DocumentIO cases;568session/recovery assertions;
  467renderer/palette +12list +14,020divider assertions;126mode +43labels
  +4,963Appearance assertions;57status +93latest-statistics assertions;
  42Split UI +8ratio assertions;10inventory checker tests. New issue #3/#4
  scoped suites PASS. Combined required host suite PASS from clean committed
  source at `727112133d556f44c68cdb11b95afade8a4880c6`.
- Production store19cases/96assertions plus3real OS write-limit assertions;
  Android adapter double27assertions. Coordinator35cases/157assertions.
- Activity save lifecycle34cases/233assertions including actual strict codec (previously six Save As cases required the separate `--safety` invocation; the current runner now includes all34);
  recovery13cases/179assertions plus7actual UI callback/dialog assertions.
- Production SIGKILL/restart suite PASS at16 actual store/coordinator boundaries:
  snapshot/preparation/completion file-sync, move and directory-sync; provider
  truncation/partial/full write and read-back. A fresh JVM validates exact original
  and attempted bytes, transaction state and retained staging. This proves host
  process-interruption behavior, not Android app-UID or sudden-power-loss durability.
- The raw-provider diagnostic remains genuinely RED on two destructive-write
  assertions, unchanged. The aggregate safety command mixes contracts and is
  not the selected product recoverability gate. Required production recoverability
  tests must be GREEN; no universal atomic-URI guarantee is claimed.
- Historical independent CI run38581669811 passed preservation and failed the
  old mixed safety step on those primitive hazards. The PM workflow now runs
  required production host checks only when review-ready; skipped draft jobs
  are not evidence of a passing hosted check. Required independent hosted
  validation **PASS** at `3cc9542`: run38593771364/job115955100750, including
  the16restart cases. PR #10 merged only after that required check succeeded.
- Unsigned JDK17/SDK37.0/build-tools36.0.0 build PASS; package2.2.5/code18,
  min26/target35 and25asset bytes preserved. Two fresh committed-source builds at `c326b61` PASS and match SHA-256
  `059587cbf2feb5df1b3a4183a20b7ae5a993aaabbc8d38b56a9d489f06e28b23`. No credentials, replacement key or signed release produced.

New bounded repairs, independently reviewed with no blocking findings:

- Statistics updates no longer copy/scan the full text per keystroke. Immediate
  characters/title/Save remain current; exact locale/Unicode word and LF line
  counts run after a250ms debounce on a dedicated bounded worker. Identity,
  revision, locale and shutdown reject stale results. Pending counts are explicit.
- Write/Read/Split selection and nine Appearance themes/three boolean options
  expose selected/checkable/checked state. Refresh retains existing controls;
  no Save action, preference, layout or protection behavior changed.
- Matched10k/100k/500k host immediate-status medians changed from
  5.752/7.893/27.252ms to0.167/0.110/0.092ms; full exact scans remain deferred.
  These are statistics-path host measurements, not Android typing latency.
  See [measurement](benchmarks/M0_3_DEBOUNCED_STATISTICS.md).

Current backend implementation, independently reviewed:

- Immutable checkpoint names over existing validated snapshots; strict bounded
  checksummed records and fail-closed complete pin enumeration. No source rewrite.
  15cases/87assertions;6actual SIGKILL boundaries, each reopened twice.
- Exact URI/unsaved-tab identities, immutable Save As provenance and verified
  group ownership/sequence index. Missing optional records still expose actual
  completions; pending/orphan versions remain protected. 19cases/275assertions;
  two independent clean reopens and12SIGKILL boundaries, each reopened twice.
  Review fixed forged TAB provenance and false-green fault/quota fixtures.
- Read-only exact bounded diff preserving BOM/CRLF/Unicode; explicit incomplete
  summaries at byte/line/work/output limits. 10cases/522assertions plus six paired
  8MiB comparisons at128MiB heap with32MiB retained headroom.
- Streaming private archive framing/integrity codec:459assertions and constrained
  memory target,168,427,380 generated payload bytes checked, both1GiB bounds.
  Codec callbacks are provisional; typed import validation, adoption, export pins
  and provider readback receipts are not implemented. Review fixed an index-limit
  false-green fixture and a reproduced zero-read hang.

All four backends are registered in the required host runner. They are not wired
into Activity actions and do not implement pruning, combined quota, private-state
import/adoption or the full M1 user flow. Retained stages/corruption fail visibly;
no implicit cleanup or partial pin set. Calls must remain off the UI thread;
shared graph locking and operation-scoped source validation are implemented in the next reviewed slice below. Shared admission and runtime integration remain pending.

Current retention slice, independently reviewed at `f244e0e` (Task3A1):

- One canonical-private-parent monitor coordinates store/catalog/identity/index;
  mixed/aliased roots are rejected. Existing constructor quotas, native record
  formats and write admission remain unchanged.
- Read-only inventory and complete-envelope hashes bind exact metadata, edges,
  staged/corrupt inputs, byte/slot completion reservations and policy choices.
  All snapshots remain protected; there is no pruning or shared-admission API.
- A bounded operation-scoped validated-source cache avoids repeated checkpoint
  payload hashing; generation checks before and after callbacks reject mutation.
  Independent review caught and fixed a stale proof and an API33-only sink.
- Scoped tests PASS:81 functional +6 constrained-memory checks; affected native
  store/coordinator/restart/catalog/index/diff/recovery paths PASS. All34 lifecycle
  cases/233assertions PASS locally after required-runner registration. Clean
  integration host/build evidence for this slice is pending, not inherited from PR12.

Integration evidence and next executable work:

1. Merged UI source `727112133d556f44c68cdb11b95afade8a4880c6` passed required
   clean-checkout host tests and two unsigned builds, matching SHA-256
   `d07d660df8438f5f0ff16a6b5b2439c12bffe7bb7b0986b3e7e79fee968ecee9`.
   Combined backend verification **PASS** at
   `e612e3b0e875ce07fbfe1f69980458f2c7ebdaa6`: required host suite once from
   clean source; two unsigned builds match SHA-256
   `f2ba08a92d781f9e6dd8f1aaa1e3efd7f2625910ed64cafdbde6cdbfb311ab1f`.
   Independent integration review approved; deliberate hosted run38610866540 PASS. [PR #12](https://github.com/pemunson90-cmd/Markdown-Desk/pull/12) merged at `3652218c1a764dc8393280e53b1202091224f92d`.
2. Continue the [history/retention plan](superpowers/plans/2026-10-14-history-retention.md):
   finish clean integration of shared lock/validated inventory/read-only graph, then implement combined admission and reservation-bound completion staging before any destructive executor. Crash-safe deletion, verified export, strict import and
   history UI remain next tasks. [ADR-002](decisions/ADR-002-retention-journal.md)
   defines the reviewed retention protocol. Its first implementation is deliberately
   narrowed to shared monitoring, validated inventory/read-only graph and bounded
   per-operation validation cache; dynamic quota, reservation-bound staging and
   deletion follow separately. No source reconstruction or coordinator redesign.
3. Reconcile local M0 prerequisites before optional runtime enablement. Keep
   physical milestone acceptance and user-facing release gates open. Preserve
   canonical signing, migration/rollback and actual Fold/SAF/IME/TalkBack/upgrade
   requirements; no replacement key or credentials in Git/CI.

Owner-reported target: **Samsung Galaxy Z Fold7, Android16, One UI8.0**.
The owner reported installed app version “2.25”; the exact installed package
version/code remains unverified (protected source is2.2.5/code18). No ADB access
or owner-run test results supplied yet; Codex has no connected device.
See [device checklist](M0_DEVICE_ACCEPTANCE.md) and `verification/device_context.py`.
Matched synthetic fixtures remain local under ignored
`verification/build/Galaxy-Z-Fold7-baseline-fixtures.zip`. Physical Fold/SAF/IME/
TalkBack/upgrade and Android filesystem durability remain distinct NOT RUN gates.

Named history/diff/configurable retention, private backup/restore and M2–M7
features are not complete. Candidate phone/Fold/SAF/TalkBack/upgrade/process-death
and actual Android durability checks remain NOT RUN. Canonical signing inputs
will be required only for authorized release preparation, never in Git/logs/CI.

Exact2.2.5 source/provenance and owner-approved artwork are already accessible
in Git. The local Library directory contains0files; older version/SDK/private
archives were unavailable there and not published. Installed SDK remains local
tooling. Preserve private recovery/signing material in any later provenance work.
