# Source of truth / import protocol

## Verified baseline
- Package: `com.example.markdowndesk`.
- Version: **2.2.5**, versionCode **18**, minSdk **26**, targetSdk **35**.
- Release certificate SHA-256: `a163cea419108eb595f3dcb01de16feca878368d4ffc6efc58eb9a8358b6be01`.
- Signed 2.2.5 APK SHA-256 recorded by release: `ec5e790343b45a56f2580f3f5ec97eae3fb3096e10e012e58eb76bc89e683601`.
- Source archive: `MarkdownDesk-2.2.5-source.zip`; supplied in the ChatGPT conversation/Library. Import exact source, preserve filenames and asset bytes, and compare SHA256SUMS as applicable. This repo initially has no verified application source; docs in Git are not a substitute for source import.
- Main code path in archive: `MarkdownDesk-2.2.5/apps/MarkdownDesk/`. Shared editor bridge: `MarkdownDesk-2.2.5/shared/editor_bridge/`. Tests/tools: `MarkdownDesk-2.2.5/verification/`. Preserve relative path relationships.
- Run `verification/run_host_checks.sh`; it currently covers syntax/layout and selected renderer behavior, and must be extended with actual behavioral tests.
- Build path: `apps/MarkdownDesk/build.sh`, Android platform 35+ (actual release used platform 36), build-tools 36.1.0 (a Library copy exists), JDK 17+, Java 11 target.
- 2.2.5 was compiled, aligned and signed in chat with the canonical identity; **physical Fold acceptance for 2.2.5 has not been established**.

## Library usage
Pat's Library contains historical versions, development notes, Android toolchains and recovery/signing material. Treat older builds as provenance, never automatic authority over 2.2.5. Inspect one source by version and file digest before taking code. Read privacy/signer recovery instructions separately if needed. The private signing material must stay outside Git and outside cloud runners.

## Public-repository privacy guard
This repo is **public**. Review assets, embedded strings, history, and files before pushing. Release keystores, passwords, recovery bundles, original private notes, personal documents and potentially private artwork are never committed by default. If the 25 splash assets cannot be publicly distributed, use approved public equivalents or make the repo private first—do not silently omit required runtime assets or pretend a source-only build reproduces the release.

## Import acceptance
1. Inspect archive integrity and enumerate tracked source/resources/assets/tests.
2. Import clean files, excluding `build/`, private key material, temporary caches and historical screenshots unless deliberately tracked.
3. Compare imported source to archive byte-for-byte; record source and baseline APK digest.
4. Run host checks and clean unsigned Android build. Inspect Android manifest package/version and payload.
5. Verify the signed 2.2.5 baseline certificate fingerprint and preserve installed-user-data migration behavior.
6. Establish reproducible test fixtures with independent expected outputs.
7. Create baseline tag `v2.2.5-baseline` only when the imported source has passed gates; do not tag placeholder README.
