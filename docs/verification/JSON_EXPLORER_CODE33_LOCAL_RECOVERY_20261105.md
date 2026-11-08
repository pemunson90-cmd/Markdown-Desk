# JSON Explorer opening crash — local recovery (2026-11-05)

## Symptom and evidence
After upgrading from code31 to code32, the owner reports that opening any `.json` crashes Markdown Desk. The preceding owner screenshots prove code31 reached `$[0]` and the Claude transcript dialog. Code32 changed the JSON explorer's window-insets behavior, file-source choice and transcript appearance. **There is not yet an Android logcat stack trace, and the precise exception has not been proven.**

## Provisional root-cause hypothesis
The JSON explorer's new `buildUi()` block calls `Window.setDecorFitsSystemWindows(false)`, installs a custom `OnApplyWindowInsetsListener`, changes root padding within that listener, and calls `requestApplyInsets()` immediately after `setContentView()`. The new insets lifecycle is a plausible Android 16/One UI regression; this is a *hypothesis*, not a reproduced stack trace.

## Scoped recovery
- Based on the exact 2.3.4 code32 APK, source branch `feature/json-claude-export-explorer-20261105`.
- The local binary candidate bypasses **only** the newly added custom window-insets block and `requestApplyInsets()` inside `JsonExplorerActivity.buildUi()`. Android's default decor fitting is used.
- All other app compiled classes, resources, JSON browsing, transient alternate file picker, and dark transcript dialog remain unchanged.
- Manifest version bumped to `2.3.5-json-recovery` / code33. Same package `com.example.markdowndesk` and canonical signing certificate.
- Source-equivalent recovery changes are committed on isolated branch `fix/json-recovery-local-20261105`. The tested APK is a narrowly patched code32 artifact (not a fresh full-source compilation), because the authoring sandbox does not have the complete modern Android SDK or full latest repository checkout. This provenance must not be misrepresented.

## Local validations (no GitHub Actions)
- Binary DEX change was precisely two equal-length conditional-to-unconditional branch rewrites in `buildUi()` (DEX file offsets `0x568ee` and `0x56950`), plus regenerated DEX signature/checksum; Android `dexdump -c` passed.
- Binary manifest versionCode and equal-length versionName were updated, preserving all binary XML chunk offsets.
- ZIP entries were compared against original code32 APK; **only `classes.dex` and `AndroidManifest.xml` differ** (excluding regenerated signing metadata). All 25 splash assets preserved.
- `zipalign -c -p 4` passed; `apksigner verify` passed v2/v3, cert SHA-256 `a163cea419108eb595f3dcb01de16feca878368d4ffc6efc58eb9a8358b6be01`.
- `aapt2 dump badging` confirms package `com.example.markdowndesk`, code33 and versionName.
- SHA-256 of signed recovery APK: `7e9da48264e7e3df22fdf96f3c8f585587ad43c37f4af8476e97616487a7bcd2`.
- Private signer from the owner's Library never entered GitHub, hosted actions, source commits, or this report.

## NOT proven; next gates
- **No Android emulator/device run or stack trace; do not mark crash fixed.**
- Install recovery code33 **over** code32; do not uninstall or clear app data.
- Test Files > Explore JSON > both picker routes > open real Claude export, browse array/object, show transcript, save a *new* Markdown file.
- Confirm old Markdown tabs, folders, favorites, themes and user documents retained.
- The earlier status-bar overlap may recur because this recovery prioritizes opening JSON over custom insets; address safely after crash cause is confirmed.
- If code33 still crashes, capture Android logcat of the actual crash and trace the exception. Do not keep guessing through blind rebuilds.
- Rebuild from full canonical source via owner's existing Android SDK when available; locally run focused UI tests before promoting an accepted release.

## GitHub usage
This branch is for organization/versioning only. **No GitHub Actions were invoked** for the diagnosis, patch or signature validation. Commits include `[skip ci]`; preserve the existing draft PR #36 and do not merge this recovery into the release line without device acceptance.
