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

## Active campaign — production save protection and M0 integration

Branch: `codex/m0-safety-regressions`, draft [PR #10](https://github.com/pemunson90-cmd/Markdown-Desk/pull/10).
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

Current observed host evidence:

- Full host suite PASS.42DocumentIO cases;568session/recovery assertions;
  467renderer/palette +12list +14,020divider assertions;117mode +43labels;
  28status;42Split UI +8ratio assertions;10inventory checker tests.
- Production store19cases/96assertions plus3real OS write-limit assertions;
  Android adapter double27assertions. Coordinator35cases/157assertions.
- Activity save lifecycle34cases/233assertions including actual strict codec;
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
  are not evidence of a passing hosted check. Updated hosted validation pending.
- Unsigned JDK17/SDK37.0/build-tools36.0.0 build PASS; package2.2.5/code18,
  min26/target35 and25asset bytes preserved. Two fresh committed-source builds at `c326b61` PASS and match SHA-256
  `059587cbf2feb5df1b3a4183a20b7ae5a993aaabbc8d38b56a9d489f06e28b23`. No credentials, replacement key or signed release produced.

Next executable work:

1. Restart/adapter/recovery code review is complete with no blocking findings.
   Integrated required host suite PASS including the16 new restart cases.
   Update the existing PR and invoke one deliberate review-ready hosted check.
   Matched host protection timing/space is recorded in
   [measurement](benchmarks/M0_PROTECTED_SAVE_HOST.md); no device budget claimed.
2. Parallel local issue #3 statistics performance and issue #4 selected-control
   accessibility work is active in isolated worktrees based on `d8d595a`.
   Review bounded changes independently before integrating; save/recovery code
   remains in the reviewed protection boundary. Continue eligible local work
   rather than waiting for unrelated physical acceptance.
3. Prepare the next history/retention/backup slice against the actual protection
   store. Physical M0/M1 acceptance is still open; no completed milestone or
   user-facing release is claimed from local host results.

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
