# Local Claude JSON import fix — code36
Date: 2026-11-06. **No GitHub Actions invoked.**

## Owner-reported failure
On the Fold7, the 2.4.0 archive test app rejected a real Claude export with `Expected JSON object key (depth=2)`; it reported that its previous index was preserved. The owner confirmed the install ZIP worked.

## Cause: reproduced
`ArchiveStreamParser.object()` and `array()` called `peek()` or dispatch callbacks immediately after delimiters, without consuming legal JSON whitespace. With pretty-printed JSON, whitespace after commas or opening brackets triggered `Expected JSON object key (depth=2)` or silently skipped message arrays when whitespace followed a colon. The old parser was run against a synthetic formatted Claude conversation and reproduced the exact error. No private export was requested or copied.

## Corrected paths
1. Before reading each object key, call `skipWhitespace()`.
2. After consuming object key colon, call `skipWhitespace()` before value dispatch.
3. Before dispatching each array item, call `skipWhitespace()`.

Source branch: `fix/claude-pretty-json-import-20261106` based on the existing audited archive branch. Added `verification/archive_reader/PrettyPrintedClaudeCheck.java` for formatted array and object-wrapped Claude exports.

## Local tests
- Reproduced original exact error with baseline Java parser.
- Patched parser: pretty-printed multi-conversation and multi-message test passes, including wrapped `{"conversations": [...]}` and preserving all text blocks.
- Existing `ArchiveCoreTest` passes: >305 messages, >120k-char message, malformed JSON rejection, annotation journal and Markdown sidecar projections.
- Recompiled replacement parser with Java 11 bytecode target + Android SDK36/D8 minAPI26.
- APK archive DEX rebuilt using the original archive DEX with exactly 19 original parser-related definitions removed from its DEX class table, then merged with recompiled parser using D8. Rebuilt DEX has 122 verified classes and passes `dexdump` verification.
- Signed test APK produced **locally** from prior working code34 APK, replacing only `classes2.dex` and the manifest's version fields, so the original editor `classes.dex` and 35 other package payloads are byte-identical.
- Manifest versionCode **36**, versionName `2.4.1-archive-fix1`, same `com.example.markdowndesk` package and minSdk26.
- Canonical signing certificate SHA256 `a163cea419108eb595f3dcb01de16feca878368d4ffc6efc58eb9a8358b6be01`; APK v2/v3 verification and zipalign passed. APK SHA256 `96ac0f1313a39c1dd6d909d83919aea2d905c9a063ab09f7b371dbc60ce4fa9b`.
- Original signing key/password remained in private local runtime and never entered this public repo.

## Honest limits
This is a targeted **code36 test update**, based on the earlier installed code34 archive-test APK, not a compiled deliverable of every additional audit-branch change. Real-device import of the owner's particular JSON remains to be observed. No emulator/native UI acceptance was run. If import still fails, capture error and source structural features without uploading private messages.

## Delivery
APK and ZIP fallback saved under private Library `Markdown Desk / Builds and outputs`. Install in place; do not uninstall or clear data. Existing code35 stable-return fallback is an earlier version and cannot overwrite code36 under ordinary Android upgrade rules.
