# Markdown Desk long-press tab freeze hotfix — 2026-10-17

## Post-audit correction — supersedes older release-ready interpretation

An additional GPT-code/Android/Reddit review found two real bugs in the first code24 long-press fix:
- `ACTION_CANCEL` wrongly closed the requested tab. Android documents cancellation as an aborted gesture. Corrected in commit `4491723a7cecd15fe03a6202cb1a806fa50e7b1e`: only `ACTION_UP` commits.
- A missing terminal event could leave a stale close pending for the next gesture. Corrected in `645d2e504f0735b2993dac5d0a61ce66671c260e` by resetting the intent on every `ACTION_DOWN`.

The `verification/split_resize` harness now extracts production long-click callbacks and checks cancel, interrupted event streams, release, and accessibility long-press. Final test fixture commit `1b3a057fb23824a8da0ff96790e8b1bbb857c976` uses production code from `645d2e5`.

**DO NOT distribute the earlier signed code24 artifact from `66ee8c5` as the audited fix.** The revised exact source has been privately signed with the canonical key as `MarkdownDesk-2.2.9-tabfix2-audited-Fold7-signed.apk`, SHA-256 `3a3cecf16e0c4ce8a585624dc3eab01d52a9b9d455693ee785747ada16c3f687`; unsigned member bytes match the package, v2/v3 signatures and signer fingerprint match original. Its full host regression and native Android long-press retest were still underway when this addendum was authored, so they must be checked for final verdict.

External references: Android MotionEvent (https://developer.android.com/reference/android/view/MotionEvent.html), ANR diagnosis (https://developer.android.com/topic/performance/anrs/diagnose-and-fix-anrs), and related but non-causal Reddit report (https://www.reddit.com/r/androiddev/comments/1gpwpb6).

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
- **PASS** exact code24 full current-phone host gate: https://github.com/pemunson90-cmd/Markdown-Desk/actions/runs/39070347566 . All 18 user-deferred portable cross-installation suites remain explicitly NOT RUN.
- **PASS** exact code24 native Android emulator: https://github.com/pemunson90-cmd/Markdown-Desk/actions/runs/39070347549 . 27 ordinary tab switches, relaunch, physical long-press inactive/active tabs, clean/dirty confirmation/cancel/close, re-open all verified. Hardware Fold7 remains NOT RUN.
- Physical Fold7 performance/real saved user session NOT RUN. 18 owner-deferred portable cross-phone state suites remain explicitly NOT RUN.

## Private APK signing
Unsigned exact `66ee8c5` proof from GitHub artifact 12115190459 was signed entirely privately with the original root Library MarkdownDesk 2.1.0 release signing archive. No key/password material committed to GitHub or distributed. Privately verified APK Signature Scheme v2/v3, original signer cert SHA-256 `a163cea419108eb595f3dcb01de16feca878368d4ffc6efc58eb9a8358b6be01`, exact original application ZIP members match unsigned artifact, and manifest reports 2.2.9. The uniquely named conversation artifact is `MarkdownDesk-2.2.9-longpress-code24-66ee8c-signed.apk`, packaged with exact SHA256SUMS and release notes as `MarkdownDesk-2.2.9-longpress-code24-66ee8c-package.zip`, SHA256 `d409c4b4864a6bc19ee53ece7d3b769122574b87aa3fcbfa00679dca1ee5acbd`. Never infer its sandbox path without container verification.

## Release hold
The code24 host/native gates PASSED. Offer original-cert **in-place upgrade** on Fold7 without uninstalling or clearing data and request owner test on original tabs. If a freeze persists, obtain Android ANR/bugreport; do not claim proof of cause solely from source patterns.
