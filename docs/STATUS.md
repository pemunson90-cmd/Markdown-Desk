# Current project status

## M0.1 / issue #1 — imported and reproducible; baseline signed artifact independently verified; merge pending

As of **2026-10-14**, branch `codex/issue-1-baseline-audit`, draft
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
- PM approved merging the **unchanged-source baseline** after final document reconciliation; physical Fold/SAF/IME/TalkBack/upgrade acceptance remains **NOT RUN** and must gate future production releases. No new signed release, tag, merge, feature development or hosted CI run has occurred yet.

See [audit/evidence](M0_1_AUDIT.md) and [reproducible commands](BUILD.md).

## Next — continuous campaign

1. Complete this documentation reconciliation, confirm current PR state and implement the PM-approved **baseline source-import merge** of PR #9; tag `v2.2.5-baseline` without issuing a new APK release. The source import does not require retroactive Fold/device acceptance.
2. Immediately start [issue #2](https://github.com/pemunson90-cmd/Markdown-Desk/issues/2) behavioral regression tests and then move through the next eligible tasks in the [M0–M7 roadmap](ROADMAP.md). **Do not stop after a single issue** unless a real external gate blocks all independent work.
3. Keep runtime device/SAF/Fold/upgrade checks explicitly NOT RUN until performed. Do not bypass private signing or device acceptance for user-facing releases.
4. Apply the standing orchestration/local-first workflow in [AGENTS.md](../AGENTS.md), updating this status with actual results and the next runnable task at every coherent checkpoint.

Historical Library versions/SDK/recovery archives are a secondary provenance
request, not retrieved/uploaded in this session. Exact 2.2.5 source and historical
records shipped inside its archive are now accessible in Git; protect unrelated
private recovery/signing material from any later publication.
