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

## Active continuous campaign — M0 safety and measurement

- Branch: `codex/m0-safety-regressions`, based on merged/tagged source baseline.
- Issue #2: host provider/UTF8/BOM/size and session/recovery fixtures and independent renderer/mode-method fixtures integrated:39provider,
  568session/recovery,368renderer/palette,99mode/lock assertions pass; six
  inventory audit tests pass. Actual Android boundaries are simulated.
- Issue #3: deterministic10k/100k/500k-word and8MiB host benchmarks recorded;
  full UI/device/Fold timing remains NOT RUN.
- Runtime source remains unchanged in this slice; test doubles cannot establish
  real SAF, UI/Focus/keyboard, phone/Fold or process-death acceptance.
- Desired-safety/spec gate is RED:5provider probes, nested-list rendering and
  16text-contrast pairs fail. See M0_2_REPORT.md; do not bypass these defects.
- Current next action: publish reviewed tests-only checkpoint, then execute
  test-first8MiBwrite preflight/nullmetadata fix and bounded contrast fixes.
  ADR-001 safe-save design prepared; owner atomic-versus-recoverability
  product guarantee decision is pending while independent M0 work continues.
- M0 is not complete and no M1–M7 implementation/release is claimed. Continue
  independent eligible work while hardware-dependent checks remain NOT RUN.


Historical Library versions/SDK/recovery archives are a secondary provenance
request, not retrieved/uploaded in this session. Exact 2.2.5 source and historical
records shipped inside its archive are now accessible in Git; protect unrelated
private recovery/signing material from any later publication.
