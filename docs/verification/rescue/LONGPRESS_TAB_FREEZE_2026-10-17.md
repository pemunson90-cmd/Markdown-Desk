# Markdown Desk long-press tab freeze hotfix — 2026-10-17

## User-reported failure
Physical Galaxy Z Fold7, original-signed code23 / 2.2.8-tabfix1: apparent UI-thread freeze and substantial heat while long-pressing tabs to close. Splash itself dismissed normally. Earlier emulator stress checked ordinary taps only and missed the long-press code path. DO NOT uninstall or clear app data. Owner workaround: select a tab with an ordinary tap then More -> Close document, save beforehand.

## Concrete code-path finding
In installed code23, `lambda$updateTabsUi$24(int i, View view)` called `switchToTab(i,false)` on an inactive tab **within the long-click dispatch**, causing heavy editor restoration, tab-strip view replacement and only then `closeCurrentTab()`. Android fires long-click while the finger remains physically down, so merely posting the removal immediately is also unsafe.

## Isolated corrected source
Branch `rescue/md-unsigned-build-proof-20261017-longpress-freeze`. Based on code23 checkpoint `3a081c97f3e6ce8fef54019f19dae1781370903a`. App changes:
- `a24771abe9edc6f6ecb9e94487cd0e566ddb57dd`: long-press targets immutable tab ID; inactive clean tab removed without switching, dirty inactive prompts Open to save / Discard and close / Cancel; active dirty retains guarded save/discard/cancel.
- `66ee8c5995f081fa3355f7ad643d146f5073da6d`: physical touch queues close through ACTION_UP/CANCEL and posts after touch dispatch, accessibility long-press takes posted path; ordinary tap still works.
- Application identity is **versionCode 24, versionName 2.2.9-tabfix2**. The exact unsigned Android candidate was built at `66ee8c5`. Later commits in this branch only change test fixtures/scripts, not application sources.

## Verification, classified accurately
- Focused unsigned Android build / host: PASS `39069782247`, production source `66ee8c5`.
- Initial complete host CI `39069782245` failed in extracted Split test fixture, not production javac; fixture corrected in `8a2b40f`.
- Initial native Android smoke `39069782254` passed 27 tab switches, reached correct long-press **Unsaved changes · daily-journal.md** dialog, then test failed only because Android uppercase-buttons were compared case-sensitively; fixed in `5bb07fa`.
- Fresh exact regression runs: host `39070347566`, native `39070347549`. **Check final results before declaring PASS or issuing release.**
- Physical Fold7 performance/real saved user session NOT RUN. 18 owner-deferred portable cross-phone state suites remain explicitly NOT RUN.

## Private APK signing
Unsigned exact `66ee8c5` proof from GitHub artifact 12115190459 was signed entirely privately with the original root Library MarkdownDesk 2.1.0 release signing archive. No key/password material committed to GitHub or distributed. Privately verified APK Signature Scheme v2/v3, original signer cert SHA-256 `a163cea419108eb595f3dcb01de16feca878368d4ffc6efc58eb9a8358b6be01`, exact original application ZIP members match unsigned artifact, and manifest reports 2.2.9. The uniquely named conversation artifact is `MarkdownDesk-2.2.9-longpress-code24-66ee8c-signed.apk`, SHA256 `d409c4b4864a6bc19ee53ece7d3b769122574b87aa3fcbfa00679dca1ee5acbd`. Never infer its sandbox path without container verification.

## Release hold
Wait for both final host/native gates on exact code24. The source may still need a bug fix if native long-press validation fails. After checks, offer original-cert **in-place upgrade** on Fold7 without uninstalling or clearing data and request owner test on original tabs. If a freeze persists, obtain Android ANR/bugreport; do not claim proof of cause solely from source patterns.
