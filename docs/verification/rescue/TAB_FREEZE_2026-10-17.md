# Markdown Desk 2.2.8 tab-switch freeze rescue — 2026-10-17

## Owner report
The original canonical-signed 2.2.7-ux/code22 installs. After closing or switching among existing tabs, touch interactions sometimes stop responding and the Fold7 heats up; force-stop/clear cache did not permanently recover. A later cold launch automatically dismissed its splash after five seconds, then the editor worked until further tab switching. Therefore the splash is not established as the cause; a device ANR/CPU trace has NOT been captured. Do not uninstall or clear app data.

## Isolated fix
Development branch: `rescue/md-unsigned-build-proof-20261017-tab-freeze`; original 2.2.7 source on separate rescue branch untouched.
- `05ae8b88ed0f1f4d02360bbbcd8b2bc742d786d4`: avoid global applyColors() during tab restoration; that unnecessary call rebuilt Files navigation, caused extra provider reads and tab chrome repaint.
- `c0fa26ddf9b0cc5e88288bc2888d18b21cc29d00`: explicitly updateTabsUi() within restoreActiveTab() so file-open, recovery and session-relaunch paths preserve visible tab controls; retain document syntax highlighting.
- All intervening changes repair extracted source test fixtures and add a genuine Android emulator tab switching test.
- `7998dbd080a9fe73e697c3752297035248e17fbf`: code23 manifest/versionName 2.2.8-tabfix1; `c87294675eff24e0ab8e26a6c6a1f8d7128bba38`: version check recognizes code23. No additional application code beyond the manifest differs from tested `c0fa26d`.

## Evidence and release hold
- Full current-phone host suite **PASS** on same app source: https://github.com/pemunson90-cmd/Markdown-Desk/actions/runs/39067515308 .
- Focused host build and source tests **PASS** for code23: https://github.com/pemunson90-cmd/Markdown-Desk/actions/runs/39068341580 .
- Native emulator 27 selected tab switches plus force-stop/relaunch **PASS**, code23: https://github.com/pemunson90-cmd/Markdown-Desk/actions/runs/39068274655 .
- Exact code23 final full host suite **PASSED**: https://github.com/pemunson90-cmd/Markdown-Desk/actions/runs/39068341581 . All 18 expressly deferred portable cross-install test families remain NOT RUN.
- Cross-phone portable-state families remain explicitly owner-deferred. Real Fold7 and original saved-tab workload, thermal behavior, IME/SAF acceptance remain NOT RUN and need owner feedback.

## Private signing artifacts
An unsigned proof for exact c87294 source was downloaded from private workflow artifact `12113139903`. It was privately signed with original keystore from root Library archive `MarkdownDesk-2.1.0-Full-Build-Bundle.zip`, with APK Signature Schemes v2+v3 verified and certificate SHA-256 `a163cea419108eb595f3dcb01de16feca878368d4ffc6efc58eb9a8358b6be01`, matching previously signed 2.2.7. Signing material/passwords NOT put in repository or GitHub CI and temporary extracted key cleaned.

Prepared private conversation artifacts:
- `MarkdownDesk-2.2.8-tabfix1-Fold7-signed.apk`
- `MarkdownDesk-2.2.8-tabfix1-package.zip`
- `MarkdownDesk-2.2.8-tabfix1-RELEASE-NOTES.md`
- `MarkdownDesk-2.2.8-tabfix1-SHA256SUMS.txt`
APK SHA256: `71dfc5608ad357078e21f909753ce35b7274544017a60f046a9c51505d616075`.

These are conversation/container artifacts, not committed binaries; exact sandbox path must be reverified in each turn before linking. Future replacement must use same original release signing identity and versionCode greater than 23. Do not recommend uninstall/clear storage. If code23 fails on the physical phone, capture a bug report/ANR trace; do not assume the root cause is completely fixed.
