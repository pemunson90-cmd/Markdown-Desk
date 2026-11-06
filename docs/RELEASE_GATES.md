# Build, test, and release gates

## Owner Android support window (2026-11-06)
- Current stable Android is 17 (API 37). Support Android 15–17 only (minimum **API 35**) until the next major stable version advances the rolling three-release window.
- Verify the current development manifest and build toolchain agree on minSdk; don't leave hardcoded D8/aapt minimums at API 26 or treat Android 8 compatibility as a release obligation.
- Test min supported Android 15, current Android 17, and Android 16 on Fold7 where available. An OS update's availability on a particular device is not guaranteed.
- targetSdkVersion may differ from minSdk and must be separately reviewed for behavior changes; user-data compatibility and non-destructive in-place upgrades remain mandatory on supported devices.


## Android OS compatibility acceptance
- Current stable Android plus two preceding releases only (as of Nov 2026: Android 15/API35, 16/API36, 17/API37); do not require Android 14/API34 or earlier tests or APIs. The target SDK remains separately reviewed.
- In-place update and user-data/feature preservation remain mandatory regardless of the minSdk floor.


## Owner full-scope delivery gate (2026-11-06)
- For a requested new feature or integrated upgrade, **do not generate/sign/offer a user-facing APK** until **every requested feature is implemented together**, integrated into the real app, and the applicable local bug, dependency, regression, safety and packaging checks have passed. Internal checkpoints and partial green tests are not deliverables.
- No staged test releases or versionCode churn merely to demonstrate partial progress. A separately requested bug-fix update or explicit owner milestone is the exception.
- If a genuine blocker prevents finishing, report precisely what is incomplete and why; do not represent partial work as the completed request. Keep working on all unblocked obligations. This rule overrides conflicting older milestone/release language.


## Every PR
- Source diff reviewed for behavioral scope, migration and data safety.
- Host tests pass from a clean checkout, including real functional tests for touched components.
- No credentials, signing keys, caches, API tokens, user documents, generated release binaries, private source archives, or old recovery bundles in commit.
- No accidental downgrade of app theme palette, 25 splash images, Focus/View mode, Write/Read/Split, 12-tab semantics, lock protection, search/replace, external edit protection or recovery.
- On-device checks marked NOT RUN unless actually run. No fake green test reports.

## Document safety
- Round-trip unedited Markdown/UTF-8 without mutation, including CRLF vs LF, leading/trailing whitespace, BOM policy and Unicode.
- Edit/save/reopen and verify output; simulate process death, provider throwing mid-write, revoked SAF permissions, external edits during session, out-of-storage and repeated retry.
- Verify restoration never overwrites original silently; snapshots have integrity hashes and isolated retention.

## Rendering/UI
- All nine themes: header, dialogs, Save action, tabs, focused state, selected controls, light/dark contrast; text WCAG AA >= 4.5:1 and large icon affordances >= 3:1 where applicable.
- Phone portrait/landscape, folded/unfolded, split width, keyboard shown/hidden, TalkBack, large-font and reduced-motion cases.
- Appearance dialog updates *in place* and preserves scroll after theme change.
- Focus/View mode really hides chrome and retains the current Write/Read/Split context.

## Performance
- Establish M0 benchmark on matched fixtures; report test device/model, size, timing distribution and memory. Test 10k/100k/500k words within current 8 MiB limit, plus boundary-size policy; don't claim real-time guarantees from static inspections.
- Set numeric latency budgets only after actual baseline measurements, then fail on statistically meaningful regressions.

## Release
- Package `com.example.markdowndesk`; versionCode strictly greater than installed release; min/target policy explicitly reviewed.
- Signed *only* with canonical certificate SHA-256 `a163cea419108eb595f3dcb01de16feca878368d4ffc6efc58eb9a8358b6be01`; v2/v3 verification and zipalign PASS.
- No signing credentials in CI or shared repo; obtain keys from Pat's private Library only in authorized local build session.
- Incremental install over prior signed APK with existing documents/settings unaffected; record real device success separately from package inspection.
- Deliver APK, checksum, exact source/revision reference, changelog, observed tests, open limitations and rollback plan. Do not recommend uninstall.
