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

- Branch: `codex/m0-safety-regressions`, draft [PR #10](https://github.com/pemunson90-cmd/Markdown-Desk/pull/10).
- Issue #2: host provider/UTF8/BOM/size and session/recovery fixtures and independent renderer/mode-method fixtures integrated:39provider,
  568session/recovery,467renderer/palette,117mode/lock plus43label assertions pass; ten
  inventory audit tests pass. Actual Android boundaries are simulated.
- Issue #3: deterministic10k/100k/500k-word and8MiB host benchmarks recorded;
  full UI/device/Fold timing remains NOT RUN.
- Runtime changes now deliberately fix encoded-byte write preflight/null-descriptor
  metadata, readable derived foregrounds and hint backing, contiguous nested
  lists, independent session/recovery loading and duplicate status snapshots.
  Nine palette definitions,25artworkfiles, storage formats and modes/locks remain
  preserved. Test doubles do not establish real Android/phone/Fold acceptance.
- Provider safety probes now3pass/2fail; destructive partial/zero-byte provider
  writes remain unresolved. Nesting, wide-table stack-overflow and sampled/context contrast fixes pass locally.
  The desired-safety gate remains RED, so M0 is not complete.
- Recovery loader10cases134assertions and status28assertions pass after RED-first
  fixes; status snapshot count2→1. No reliable timing speedup or device claim.
- Chromium host layout proxy passes108cases (9themes×3sizes×4viewports);
  this is browser overflow coverage, not screenshots or Android/Fold acceptance.
- Independent review and two fresh committed-source builds PASS at `081b610`.
  Unsigned APK SHA-256: `51ef128c7dfc7903a10553578ccc48abb1e02cc315e31e86dd963eb3faa673c7`.
  JDK17/SDK37.0/build-tools36.0.0 reused; no dependency downloads in clean rebuild.
  Independent CI run38564454893 failed in status-test tooling: a hard-coded
  local Java path; desired safety step was skipped. Scripts now use PATH and
  locally pass. Rerun38564818234 passes behavioral preservation and fails
  the safety step on the same two destructive provider assertions; no overall
  green claim. See GitHub Actions run evidence.
- Save As lifecycle repair:10baseline cases/80assertions and16desired cases/
  127assertions pass after6independent failures. Destination stays pending until
  verified success; missing/busy origin results abort; failed writes preserve
  URI/name/hash/time/readOnly/BOM; newer edits remain dirty; closed origins clear
  stale continuations. Real Android/provider boundaries are simulated.
- Label repairs:source/reading size actions distinguished; helpers expose
  descriptions; Focus Enter/Exit/selected state tracks existing behavior; Save
  action/state descriptions follow Saving/Copy/Save/Saved precedence.
  Static accessibility audit records remaining divider/target/device gaps.
- Safe-save preparation:20model-contract tests pass, explicitly not production
  persistence/durability proof; no existing provider assertion weakened.
- Current next action: prepare real storage durability adapter/fault cases and remaining
  accessibility/UI seams under
  ADR-001 before claiming data-safety gates. Owner delegated policy: preserve
  both original and attempted edit durably, fail closed if protection fails,
  restore only to a new copy. This is recoverability, not unchanged provider URI.
  M1 history integration and later milestones still require prerequisites.
- M0 is not complete and no M1–M7 implementation/release is claimed. Continue
  independent eligible work while hardware-dependent checks remain NOT RUN.


Historical Library versions/SDK/recovery archives are a secondary provenance
request, not retrieved/uploaded in this session. Exact 2.2.5 source and historical
records shipped inside its archive are now accessible in Git; protect unrelated
private recovery/signing material from any later publication.
