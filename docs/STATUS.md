# Current project status

## M0.1 / issue #1 — imported; host/build gates pass, final gates pending

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
- Baseline signed APK digest/certificate validation remains pending (APK missing).
  No release APK, baseline tag, merge, feature development or hosted CI run.

See [audit/evidence](M0_1_AUDIT.md) and [reproducible commands](BUILD.md).

## Next

1. Independent source/build review completed without critical/important implementation findings; final gatekeeper approval remains pending.
2. Obtain baseline signed APK for public-certificate/digest validation, without
   signing secrets. Record actual device/Fold/upgrade acceptance separately.
3. Keep PR #9 draft until required gates pass; then decide baseline tagging/merge.
4. Do not start issue #2 or later roadmap work in this task.

Historical Library versions/SDK/recovery archives are a secondary provenance
request, not retrieved/uploaded in this session. Exact 2.2.5 source and historical
records shipped inside its archive are now accessible in Git; protect unrelated
private recovery/signing material from any later publication.
