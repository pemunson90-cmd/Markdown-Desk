# JSON Explorer local differential diagnosis — code31/32/33

Date: 2026-11-05 (America/New_York)

## Confirmed observations
- Owner screenshots from code31 show successful navigation to `$[0]` and rendering the Claude transcript dialog. The dialog had poor contrast and top-bar overlap, but JSON opened.
- Owner reports crashes when attempting to open `.json` after the code32 update. We do not have a stack trace or an exact screen/location of the crash.
- Code32 introduced the native chooser, modal palette, saved URI state, and custom Android 30+ insets handling.
- Code33 binary recovery skips the insets block with two equal-width DEX jump replacements. The app has **not** yet been run on the owner's Fold7 after code33.

## Local APK differential, no CI usage
- Code31, 32 and 33 have matching canonical signer SHA-256:
  `a163cea419108eb595f3dcb01de16feca878368d4ffc6efc58eb9a8358b6be01`.
- All 3 APKs pass apksigner and zipalign checks, with 25 splash files.
- Between code32 and code33, APK payload files changed: **only `classes.dex` and `AndroidManifest.xml`**.
- The `classes.dex` change comprises two 4-byte branch edits (offsets `0x568ee`, `0x56950`) plus the mandatory DEX header SHA-1/Adler-32 bytes. All other DEX payload bytes are identical.
- The code33 manifest increments versionCode 32→33, preserves `com.example.markdowndesk`, and changes versionName only.
- No Android SDK platform `android.jar`, emulator image, Android crash log or owner Claude export is present in this local sandbox. Installed build-tools alone do **not** permit a full new source build or reproduce the device crash.

## Independent parser-design risk, not root cause
`JsonExplorerEngine.scan` defaults to `PAGE_SIZE=100`, and its `summary()` traverses entire nested objects and calls `skipValue()` on conversation message arrays to reach the following item. This means a 100-row conversation list can traverse content from 100 full conversations, not just metadata. This is source-confirmed **extra work** with potentially poor behavior on very large Claude exports, not a demonstrated OOM or cause of the reported code32 crash. Do not change the parser blindly while the actual regression remains unidentified.

## Diagnostic next step
- Observe code33 on the Fold7: whether entering JSON Explorer succeeds, whether the file picker appears, and whether the crash occurs only after selecting a `.json`.
- If it still crashes, capture an Android `FATAL EXCEPTION` / `Process: com.example.markdowndesk` stack trace from a device bug report or adb crash buffer. A separate offline Python helper can extract matching stack entries without publishing personal conversations.
- Only then make a second targeted code change. Keep private signer and personal JSON out of GitHub.
- Continue using GitHub for source versioning/report storage, and **do not run GitHub Actions for routine diagnosis**.

## Status
Code33 passes static package/signature checks but **phone crash fix is unconfirmed**. The custom insets theory is a hypothesis, not a diagnosis.
