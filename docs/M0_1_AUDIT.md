# M0.1 baseline import audit — 2026-10-14

Completed issue [#1](https://github.com/pemunson90-cmd/Markdown-Desk/issues/1), merged
[PR #9](https://github.com/pemunson90-cmd/Markdown-Desk/pull/9).
Base: `60f3acbdfca399e99f5f1754a4d66b3fddd1f4b4`.
Implementation verified by two clean builds:
`ec9937df8a5a4f77ad427c3a02a2606a72885dce`.
Branch: `codex/issue-1-baseline-audit`.

## Provenance and publication

- Exact attachment: `MarkdownDesk-2.2.5-source.zip`, SHA-256
  `f57fe34ac489eb1f651fd1f6fe840ffddf189f4322c4bdc32b3ad285c187e6ef`:
  **matches owner/PM expected digest**.
- ZIP CRC/integrity check: PASS. No absolute/traversal paths, symlinks or duplicate
  import paths; 89 regular files. All 88 entries in supplied SHA256SUMS passed;
  SHA256SUMS itself is separately hashed in the complete inventory.
- `apps/`, `shared/`, `verification/` retain relative layout. Four archive root
  files moved byte-for-byte to `docs/provenance/2.2.5/`, keeping project README.
  [inventory.json](provenance/2.2.5/inventory.json) records every mapping/size/hash.
- **Artwork publication gate: CLEARED separately by owner on 2026-10-14.**
  Owner explicitly selected “All 25 are cleared for public publication.” All 25
  original WebP files are included without substitutions or conversions.
- Examined archive names and text source/resources/build/test material for
  private keys, credential values, personal documents, recovery/caches and
  embedded endpoints. No signing file or credential value found. The source
  contains signing variable names and public certificate pins, not private keys.
  The manifest requests no INTERNET or broad-storage permissions. This is a
  bounded archive review, not a guarantee about all historical Library material.
- ZIP itself, host dependency JAR, generated HTML/classes/APKs, caches and test
  temporary files are excluded from Git. No original signer was accessed or
  generated. No older versions/recovery archives were retrieved or uploaded.

## Exact source comparison / permitted scaffolding differences

85 of 89 imported files are byte-identical, including all 18 application Java
files, three shared Java bridge files, manifest/resources and all 25 splashes.
Four build/test files differ; all four archive originals remain byte-identical
under `docs/provenance/2.2.5/original-scaffolding/`:

1. `apps/MarkdownDesk/build.sh`: explicit unsigned path; no signing credentials
   or signer needed; asset completeness preflight; deterministic ZIP metadata.
2. `verification/compile_unsigned.sh`: delegates to real manifest-aware unsigned
   path rather than enforcing SDK35 and packaging stale 2.2.4/code17 identity.
3. `verification/run_host_checks.sh`: isolated ignored outputs, live-Appearance,
   pinned JSON dependency, existing SplashStore tests and independent fixtures.
4. `apps/MarkdownDesk/test/SplashStoreTest.java`: correct stale expected count
   20 to 24 after disabling one of 25, plus exclusion assertion. **No app fix.**

New verification scripts/checks and build/provenance docs are separate scaffolding.
The inventory audit proves baseline bytes, including retained original scaffolds;
it does not claim the four active scaffolds are unchanged.

## Actually executed

- Original supplied host runner on raw extracted archive: PASS layout guards,
  syntax parse of 31 Java files, renderer output (nine themes × three sizes).
  Separate supplied live-Appearance guards/contrast check: PASS.
- Test-first unsigned check against original `build.sh`: failed for missing
  canonical keystore, as expected. New unsigned path then compiled and passed.
- Initial audit assertion failed due to aapt2 reporting `minSdkVersion` rather
  than legacy `sdkVersion`; compiled XML confirmed 26 and test parsing corrected.
- First expanded host run failed because standalone JVM lacks `org.json`;
  pinned host-only Android JSON dependency resolved this harness dependency.
- Unchanged SplashStore fixture: **47 passed, 1 failed** (stale expected20).
  [Original failure log](verification/2026-10-14/original-splash-failure.txt).
  Corrected test-only fixture: **49 passed, 0 failed**.
- Current host suite: PASS inventory, layout/Appearance static guards, syntax,
  27 renderer cases, 49 SplashStore assertions and 15 independent functional
  search/replace, statistics/hash and app-private atomic-file assertions.
- Unsigned Android resource link, Java11 type compilation, D8, packaging and
  alignment: PASS using existing JDK17 / platform37.0 / build-tools36.0.0.
  Compilation reports existing deprecation/unchecked API notes; no compile errors.
- Compiled manifest: package `com.example.markdowndesk`, version **2.2.5/code18**,
  minSdk **26**, targetSdk **35**, compileSdk **37**. No permission/version changes.
- APK audit: signature verification fails as expected for genuinely unsigned APK;
  no signature entries/signing block; nonempty DEX; all 25 splash bytes match.
- Two fresh `git archive` builds, distinct paths, pre-provisioned inputs only:
  **identical APK SHA-256**
  `bd9f99cb99acfe8b584f47c2f6edb1418c7deb70148d5f5087c3f6bb3f638d57`.
  APK size: **5,083,736 bytes**. No dependency downloads in either clean run;
  network isolation was not independently enforced. Git status was clean after
  source commit and clean rebuild; normal build outputs remain ignored.
- `adb devices -l`: no attached device. No JDK/SDK reinstallation or key generation.

[Commands/tool pins](BUILD.md). Current execution logs and compiled manifest are
under [verification/2026-10-14](verification/2026-10-14/). Archive's HOST_RESULTS,
SDK35_RESULTS and RELEASE225_RESULTS files are historical evidence, not these runs.

## Existing behavior disposition

All runtime source is preserved byte-for-byte; **no runtime behavior deliberately
changed**. Evidence is scoped as follows:

| Behavior | Evidence in this session | Runtime gate |
| --- | --- | --- |
| Native editing; Write/Read/Split; Focus/View hides chrome | Exact Java preserved and Android compilation | NOT RUN |
| Split divider, tabs/12-tab semantics, scrolling, orientation/Fold, eye/lock controls | Exact source; existing layout guards only | NOT RUN |
| Markdown rendering, nine themes, font sizes and Appearance | 27 real renderer cases; static redraw/contrast guards | UI/visual acceptance NOT RUN |
| 25 splash options, toggles/import/remove/restore/random | Exact APK assets; 49 host assertions using stubs | Actual decoding/UI NOT RUN |
| Outline, command palette, typewriter, lock | Exact source | NOT RUN |
| Literal/regex find/replace, word/line statistics | 15 combined independent host fixtures | UI/IME cases NOT RUN |
| SAF open/save, external edits, read-back, document fidelity | Exact source; only app-private atomic-file semantics tested | Provider/data safety NOT RUN |
| Session/recovery, HTML/share/PDF | Exact source | Process-death/export/device NOT RUN |

## Remaining gates / missing inputs

- **Signed baseline APK independently verified by PM outside the Codex run**, using the archived original 2.2.5 APK and Android inspection tools; no signing credentials used. APK SHA-256 `ec5e790343b45a56f2580f3f5ec97eae3fb3096e10e012e58eb76bc89e683601` matches the pinned reference; v2 and v3 signatures verify with the single canonical signer certificate SHA-256 `a163cea419108eb595f3dcb01de16feca878368d4ffc6efc58eb9a8358b6be01`. Package/version/min/target, 25 splash images and zipalign passed. See [PM evidence on PR #9](https://github.com/pemunson90-cmd/Markdown-Desk/pull/9#issuecomment-6164996417). This is separately attributed evidence, **not a claim that Codex executed these APK checks**.
- Emulator, actual phone/Fold acceptance and signed in-place upgrade checks NOT RUN.
  No connected device and no canonical signer used. No latency/memory benchmarks.
- Screenshot/browser layout proxy and GitHub-hosted CI NOT RUN.
- Independent read-only code review completed with no critical/important implementation defects. **PM approved the M0.1 unchanged-source baseline import for merge**, subject to reconciling live documents, then creating the `v2.2.5-baseline` provenance tag. Physical Fold, provider and upgrade acceptance remain unrun and are release gates for later behavior-changing APKs, not blockers to this Git import. No shipping APK is authorized by this review.

## Independent review and deferred minor

Read-only reviewer checked `ec9937df8a5a4f77ad427c3a02a2606a72885dce` against
base, independently compared the ZIP and runtime files, and confirmed no extra
runtime files and no signing-path defect. Its important documentation finding
(obsolete absent-source/tooling status) is resolved by this final status update.

Deferred minor: `check_baseline_inventory.py` checks every listed file's digest
but does not reject newly added runtime paths. This exact import has none, as
independently checked. Future runtime additions require source diff review and
must not rely solely on this inventory checker for baseline parity.

## Source-baseline completion

After PM approval and final documentation reconciliation, PR #9 was merged at
`d1288a80d4fc174c2b2a62805642218f5fa2d516`, issue #1 closed, and annotated
`v2.2.5-baseline` pushed. This tag records exact source provenance, not a new
APK release. The campaign continues through M0 tests/benchmarks and later
milestones; this historical audit does not replace current `STATUS.md`.
