# Baseline import implementation plan

**Goal:** Resume issue #1 from the verified exact 2.2.5 archive, with no app behavior changes.
**Architecture:** Preserve apps/shared/verification paths and all application bytes. Restrict changes to unsigned packaging, host-runner scaffolding and provenance documentation.
**Tech stack:** JDK 17, Java 11 target, SDK platform 37.0, build-tools 36.0.0, Python 3 and Bash; no Gradle.
**Spec:** Issue #1, docs/CODEX_START.md, and PM correction on PR #9.

- [x] Verify archive digest, CRC, safe paths and all SHA256SUMS; inventory exact bytes.
- [x] Import locally; map root release documents into docs/provenance/2.2.5 without replacing project README. Owner explicitly cleared all 25 artwork files; include exact bytes.
- [x] Test real unsigned build with all four signing variables removed, then add --unsigned to apps/MarkdownDesk/build.sh. Assert manifest identity, absence of signatures, alignment and byte-identical assets.
- [x] Replace stale verification/compile_unsigned.sh with a wrapper over the actual unsigned path; preserve original bytes in provenance.
- [x] Run supplied regression guards, renderer and SplashStore host tests with JDK 17 on PATH. Isolate generated outputs.
- [x] Build twice from fresh copied inputs offline after setup; compare package digests and inspect package identity/payload. Any failure must be reported separately from historical claims.
- [x] Compare source/resources/shared bytes to inventory, update status/decisions/audit and draft PR. Publish cleared artwork; keep draft, no tag/merge/device claim until remaining gates pass.

Review focus: missing signing credentials, signed-by-default path, stale version overrides, missing private artwork, absent device/upgrade evidence. No runtime source fixes or features are authorized in this slice.
