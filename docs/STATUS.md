# Current project status

## Current integration checkpoint — shared authority and syntax, 2026-10-14

Main is `2ae2930`: PR #15 merged after required hosted run38635569038 passed on
`57124d7`. Its combined completion/editor/control/planning source passed the full
clean host suite and two unsigned builds (SHA-256
`2957c32061cb072fda8bb3bcbf640ba2768113f2cfef2cbb663c5fc77f16f216`).

Current local branch `codex/capacity-syntax-integration` reconciles existing draft
[PR #14](https://github.com/pemunson90-cmd/Markdown-Desk/pull/14). Both environments'
published histories are preserved; the alternate quota authority is replaced as
one coherent unit after independent production probes exposed policy uncertainty,
reentrancy, inode-durability and fresh-process fallback defects. No alternate
protocol is enabled in the app, and no existing native records are rewritten.
Superseded fixtures remain retrievable from their original commits; current
executable coverage lives in `verification/history_capacity`/`history_planner`.

Reviewed authority `1fa6d80` (test follow-up `7e474cb`) binds four existing sibling
roots, acknowledged generation/hash policy and a durable parent enrollment marker.
The marker, registration, policy and future maintenance share ONE1MiB/256-entry
control allowance. Every writer uses a typed role/dependency check and common
mutation lease before callbacks. Missing/uncertain authority blocks writes;
complete accounting charges optional damaged metadata without pretending it is
safe to prune. Only freshly validated zero-growth completion may proceed above
quota, including legitimate second handles. No completion proceeds under uncertain
policy. Controlled startup is read-only until explicit reconciliation.

Scoped worker evidence: codec14cases/209assertions; policy49, admission61,
integration42checks;10real SIGKILL boundaries and7additional fresh-process cases,
each reopened twice. Independent review approved exact source and reproduced
missing-root, second-handle, wrong-bundle and wrong-role repairs. Fresh combined
host/unsigned gate is PENDING, not covered by earlier branch evidence.

Reviewed syntax fix `4c2fda6` preserves equal-priority search/foreign style
precedence, with baseline full fallback whenever a foreign CharacterStyle exists.
Worker39,137assertions +90ms scheduler and SDK37 compilation passed; independent
review56focused checks passed. The global span query costs O(spans) work/storage;
physical IME latency is NOT RUN. Historical pre-fix timings remain labeled.
Reviewed pressure mode `8baa6eb`: existing81+5 and new60+9checks pass; floor/selected
proof encoding remains compatible. Profile previews grant no execution/admission;
newest same-history anchors, labels, pins, stages and unresolved data stay protected.

Next: complete one coherent clean host/build gate, publish the reconciled PR #14
without force-pushing, then required hosted validation. An inert bounded intent
codec is in independent development; no deletion, durable live pins, maintenance
projection/replay, runtime history, export/import or M1 completion is claimed.
Continue those next slices with explicit authority and complete framing bounds.
Physical Fold7/Android16/OneUI8, SAF/IME/TalkBack, power-loss, canonical signing and
upgrade gates remain NOT RUN. Raw-provider hazard diagnostics are unchanged RED.

## Parallel-environment checkpoint — historical, before reconciliation

## Current checkpoint — quota/syntax/planner integration

- Verified main: `6afd3a1`, PR #13 merged after hosted run38630847829 PASS.
- Active `codex/m1-capacity-syntax`, [draft PR #14](https://github.com/pemunson90-cmd/Markdown-Desk/pull/14).
  Committed syntax `c2cd90c`, shared quota `1171338`, pure planner `6d55d9a`.
- Targeted checks PASS: syntax39,081 + production watcher/scheduler; shared quota
  18cases/91checks +17actual host SIGKILL boundaries; planner427regular +19
  constrained-memory assertions. Reviews found and repaired initial-registration
  parent-sync, fresh-stage retry, graph-root binding and planner batching/ordering
  gaps. Host Editable relocation is a model, not Android IME proof.
- First combined gate at `6d55d9a` failed in the statistics-only host adapter:
  the extracted watcher gained syntax coordination dependencies. The adapter
  boundary was updated without changing product behavior; targeted status
  checks PASS. Rerun at `7bc928dc4db28cefd80bc1437204a6633417d667` **PASS**:
  full required host suite once from clean committed source, two audited unsigned
  builds with matching SHA-256 `b1176663dbe9cb44e54548d551cf1bb904625702368e07f37d88d76af1c34557`.
  All25assets and package2.2.5/code18/min26/target35 preserved. Hosted review-ready
  validation is next; no hardware/signing/upgrade acceptance is implied.
  Earlier `90f258e` full evidence remains attributed to that exact revision.
- Shared capacity is opt-in only; legacy constructor paths remain unchanged.
  Pure planner performs no deletion. No runtime quota/history control acceptance,
  private-backup adoption or M1 completion is claimed.
- Exact next action: complete the current combined gate, record its actual result,
  update PR #14 and run hosted validation when ready. Then implement explicit
  maintenance registration/accounting from [ADR-003](decisions/ADR-003-retention-maintenance.md),
  durable pins and journaled projection/replay before wiring runtime pruning.
  Explicit v2 maintenance enrollment/accounting is implemented locally and under
  independent review:12cases/101checks +11host SIGKILL boundaries reported by
  the implementer. Root integration is pending; it is outside `7bc928d` proof.
- Four initial merged remote branches and merged retention branch were cleaned
  safely; current draft branch/main and all committed history are preserved.
  Main remains unprotected; connector administration is unavailable (403).
- No connected device or signing inputs: Android/SAF/IME/Fold/TalkBack/canonical
  signed upgrade remain NOT RUN. Use the single candidate matrix already prepared.


## Independent-environment snapshot — historical evidence, 2026-10-14

- Fresh independent checkout verified remote main at `3652218` (PR #12 merged),
  and preserved draft PR #13 at `0918e76` on `codex/m1-safe-retention`.
  The earlier audited `c42de9d` remains its ancestor. One local worktree;
  no pre-existing Markdown Desk checkout or uncommitted work was available
  in this attached environment. Other agents/environments may hold unpublished
  work; this observation does not establish their status or why an agent stopped.
- This separately observed snapshot predates the combined reconciliation below. That snapshot resumed PR #13. Existing graph tests rerun with JDK17:
  `bash verification/history_retention/run.sh` PASS, 81 functional and
  6 constrained-memory checks. Historical full-suite/build evidence below is
  attributed to its original commits, not this fresh run.
- Completion reservation/staging safety (`809d41b`) and incremental logical-line
  indexing (`90f258e`) are implemented and independently reviewed. Shared quota/policy admission,
  safe planner/journal/executor and runtime controls remain incomplete.
- Fresh integration at `90f258ee05ddaadf58739327f4d4399ed0153cc4`:
  `python3 verification/check_clean_rebuild.py` **PASS**, required full host
  suite once from clean committed source and two audited unsigned builds with
  matching SHA-256 `e45934a0a12a5f1787fdd66ffd31b64bb6595b2dce32885c507980f9767bba28`.
  Package 2.2.5/code18, min26/target35 and all25assets preserved; no signing.
- New completion coverage: 7cases/86assertions and six actual retry SIGKILL
  boundaries, each inspected/retried in fresh JVMs; immutable v1 bytes retained.
  One exact transaction-bound stage spends its byte/slot reservation once.
  Partial/changed/foreign/duplicate stages fail closed; no automatic removal,
  reduced-quota exemption, dynamic quota or shared admission claimed yet.
- New line indexing: 46,564assertions/3,000seeded edits and matched benchmark
  of exact extracted callbacks on existing 10k/100k/500k-word fixtures.
  Valid insertion reads inserted chars only; deletion reads none. Remaining
  offset shifts cost O(following lines); full resets scan once.
  See `verification/line_index/README.md`; Android typing/IME latency NOT RUN.
- Exact next work: finish opt-in persisted four-root policy/shared admission
  and tested reduced-quota completion, then pure safe planner and journaled
  executor from ADR-002. Independent affected-line syntax implementation is
  underway with conservative full fallback for multiline marker documents.
  Neither ongoing slice is included in the `90f258e` integration evidence.
- GitHub branch API reports main `protected=false`; rulesets list is empty.
  Administration mutation is unavailable through the installed GitHub connector;
  the shell GitHub API call is denied by the current proxy. No protection claim.
  Retain review-ready hosted required checks and do not merge without them.
- Four fully merged historical remote branches were removed after verifying
  their tips are reachable from main: baseline audit, M0 safety, M0 performance
  and M1 foundation. Their commits remain in main. Active retention branch
  and all work here are preserved; no worktree was removed.
- Targeted tracked-content scan: 282 files, zero private-key/GitHub-token/AWS-key
  pattern matches; no tracked key/archive/APK names. This is a scoped scan,
  not proof that every historical artifact is public-safe.
- Installed platform-tools reports zero connected devices. Physical Android,
  SAF, IME, Fold, TalkBack and canonical signed upgrade remain **NOT RUN**.

The independent `90f258e` build above does not cover the latest combined source.
Reconciliation retains the more thoroughly reviewed `b511fee` completion path
(inode checks, read-only stage retry and same-target recursion guard), plus all
independent completion fixtures and line-index work. Root review reproduced a
bounded-heap regressions in the new line index; the reviewed correction above
requires fresh combined validation before the follow-up becomes review-ready. No work or
historical evidence is discarded; no force-push is used.

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
`codex/capacity-syntax-integration`, preparing the reconciled [draft PR #14](https://github.com/pemunson90-cmd/Markdown-Desk/pull/14). PR #15 is merged; current evidence is recorded above. Draft skipped CI is not passing hosted evidence. The separately reviewed issue #3/#4 slice
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
- Production store31cases/272assertions plus3real OS snapshot write-limit assertions and11zero-prefix/11partial-prefix completion assertions;
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
- Scoped tests PASS:94 functional +6 constrained-memory checks (including completion staging); affected native
  store/coordinator/restart/catalog/index/diff/recovery paths PASS. All34 lifecycle
  cases/233assertions PASS locally after required-runner registration. Clean committed-source host suite PASS at `904d7bfb9dcf8a9721930aae745c79df4eef2814`; two genuinely unsigned builds PASS and match SHA-256 `4caf117b90159dbbbb9ba1585551769d0a95c77d42f44cd29366195edecc6856`. No signatures/credentials or device acceptance.

Additional local framing prerequisite: explicit INDEX wire ID9 preserves IDs1–8
and framing version;68 assertions pass using a byte-identical real production
ownership envelope plus category/size/integrity faults. This does not implement
full history export/import. Completion-stage tests observed RED on the old anonymous-stage implementation,
then GREEN against the reviewed prerequisite `b511fee` (integrated `a53127e`).
One deterministic stage conserves its exact completion byte/slot reservation;
full matching stages may retry without rewriting, partial/changed stages cannot.
Current constructor quotas and v1 envelope bytes remain unchanged; no reduced-
quota exemption. Review reproduced and fixed fresh-stage inode substitution before
move; same-target recursion and generation invalidation are checked. Original16
restart boundaries plus3full-stage retry SIGKILL boundaries (each reopened twice)
are now required. Independent pure planner review approved `6f02af3` (integrated
`11fdf74`):81 functional +5 memory checks, bounded exact proofs with no partial
plans or deletion. Current count remains a protected floor; future pressure mode
requires its own tests. Read-only control codec `786745c` (integrated `85d2bc2`)
passes14cases/209assertions at64MiB; it publishes/adopts no policy. Combined clean committed-source verification PASS at `a18e148912eb2b2c62876886a922999758fbba40`: full required host suite once (including all19restart boundaries), two audited unsigned builds matching SHA-256 `52996f927fc40f73a5b70cb7e09011ddd6aa477be81fe65e1b949b41537f99d0`. A subsequent runner-only log-transport fix passed all3/11/11physical assertions with redirected output; production bytecode is unchanged. This historical local evidence predates the independent PR13 merge and current reconciliation above.

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
   finish combined source/build and review-ready hosted validation for the reconciled PR14, then implement journaled maintenance before any destructive executor. Next local branch `codex/shared-history-capacity` is implementing that opt-in backend; partial policy tests are not all-writer acceptance. Inert M2 range-API preparation is separately reviewed on `codex/m2-syntax-range`; it has no runtime caller or physical acceptance. Crash-safe deletion, verified export, strict import and
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
