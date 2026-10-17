# Markdown Desk 2.2.9-tabfix2 — long-press tab freeze checkpoint (2026-10-17)

## Owner report / semantics
Code22 then code23 installed on Galaxy Z Fold7. After closing/switching tabs, touch interactions become unresponsive and phone heats; splash auto-dismisses normally. Owner narrowed repro to long-pressing an open tab to close it. No physical Fold7 stack/ANR trace was captured. Do NOT advise clearing app data or uninstalling.

## Confirmed pre-fix failure
On `2.2.8-tabfix1`, long-pressing **project-readme.md** while **daily-journal.md** is selected invokes a tab switch before opening the Unsaved changes dialog. Cancel does not restore the previous tab. Disposable native Android RED regression https://github.com/pemunson90-cmd/Markdown-Desk/actions/runs/39070161305 exactly logged `BUG: long-press Cancel changed the active document`. The earlier ordinary tap-switch test had missed this path. This proves unexpected navigation; it does NOT alone prove the thermal freeze mechanism.

## Remediation
- Source commit `b4061016ba2dabb998e8b196e60ec8246b305a88`: long-press captures immutable tab ID. Nonactive clean tab closes without switching/reloading; nonactive dirty tab prompts Save/Discard/Cancel, with guarded identity and save-confirmation flows. Active tab retains existing safe Save/Discard/Cancel semantics.
- Source commit `10c6d68f0a7fe31cb5455bcbc996c266f8764005`: physical touch long-press is completed on ACTION_UP before any tab-strip rebuild/removal; non-touch accessibility actions still dispatch. This is the exact application source of signed code24.
- Manifest code24 / versionName `2.2.9-tabfix2`; package unchanged `com.example.markdowndesk`.
- Fixture-only adjustments in `verification/split_resize/UiHarness.template` and native script accept Android uppercase dialog buttons.
- No merge or reset of original production or previous rescue branches.

## Verification
- RED old path: https://github.com/pemunson90-cmd/Markdown-Desk/actions/runs/39070161305
- GREEN corrected native Android test on code24 app source: https://github.com/pemunson90-cmd/Markdown-Desk/actions/runs/39070165383 — 27 normal switches, cold relaunch, inactive dirty long press Cancel/Discard, clean close and post-close UI responsiveness PASS.
- Exact unsigned code24 build PASS https://github.com/pemunson90-cmd/Markdown-Desk/actions/runs/39069893111
- Full current-phone host regression: https://github.com/pemunson90-cmd/Markdown-Desk/actions/runs/39070481125 — inspect final status before release; was still IN PROGRESS at checkpoint.
- The eighteen portable cross-install tests remain owner-deferred. Real Fold7 thermal/input acceptance remains NOT RUN.

## Private signing
Unsigned application artifact from exact `10c6d68` via GitHub Actions artifact `12115105262`; privately signed with original archive identity and verified v2/v3 APK schemes. Release certificate SHA256 `a163cea419108eb595f3dcb01de16feca878368d4ffc6efc58eb9a8358b6be01`, matching installed code22/23. Every unsigned APK ZIP member, including classes.dex and manifest, was verified unchanged in signed APK. APK SHA256 `5eada8610ee4ab020161d4d04a83699fb280660b346ec317ae55adb8ba2627ca`.
Temporary private signer was cleaned; no credentials or keystore committed to GitHub.
Private conversation artifacts at time of checkpoint (container paths require reverification in later sessions):
`MarkdownDesk-2.2.9-tabfix2-Fold7-signed.apk`,
`MarkdownDesk-2.2.9-tabfix2-package.zip`,
`MarkdownDesk-2.2.9-tabfix2-RELEASE-NOTES.md`,
`MarkdownDesk-2.2.9-tabfix2-SHA256SUMS.txt`.

If physical Fold7 still freezes after code24, prioritize capturing native ANR/input dispatch traces rather than guessing at more CPU optimizations.
