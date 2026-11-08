# Build, test, and release gates

## Owner Android support window (2026-11-06)
- Current stable Android is 17 (API 37). Support Android 15–17 only (minimum **API 35**) until the next major stable version advances the rolling three-release window.
- Verify the current development manifest and build toolchain agree on minSdk; don't leave hardcoded D8/aapt minimums at API 26 or treat Android 8 compatibility as a release obligation.
- Test min supported Android 15, current Android 17, and Android 16 on Fold7 where available. An OS update's availability on a particular device is not guaranteed.
- targetSdkVersion may differ from minSdk and must be separately reviewed for behavior changes; user-data compatibility and non-destructive in-place upgrades remain mandatory on supported devices.


## First signed phone candidate — gated command (NOT YET AUTHORIZED)

After the eight live queue rows **T01–T08** have each been independently updated to
`PRE-APK PASS` with exact, executable local test evidence, run on an authorized
machine with the complete current Git checkout, Android SDK37 and canonical private
signing environment:

```bash
bash verification/first_signed_phone_candidate.sh
```

This refuses a dirty or nonintegration checkout, checks that no required feature
or pre-APK test is open, requires a manifest `versionCode` above 38, verifies the
original signing certificate **before packaging**, reruns the applicable host suite
and the full AAPT2/Java/D8 **source-only** gate, and only then creates the **first**
signed phone-test APK. It verifies the resulting APK certificate again. No credential
is committed to GitHub. The command neither starts GitHub Actions nor promotes
`main` nor publishes a Release. Folding, real provider, accessibility, upgrade and
data-preservation acceptance must happen **after** installing this first candidate.
Only an accepted phone build may then be merged/promoted and published manually.

**As of this checkpoint the feature queue is NOT approved; do not execute this command.**

## Owner reaffirmation: local tests, complete plan, one APK (2026-11-07)
- **No GitHub Actions during ordinary Markdown Desk development or release preparation.** Use the available chat/local JDK, Android SDK and direct production-path tests. A required external exception must be explicitly owner-approved, not inferred from an existing workflow.
- **Do not generate a Markdown Desk APK, including an intermediate unsigned candidate, until the complete approved Unified Workspaces feature scope is implemented and the applicable local host/dependency/safety tests are green.** SDK/compiler smoke fixtures are not app builds and do not satisfy this gate.
- Final build/packaging, verification, original-certificate signing and the first phone-testable release occur only after the complete-scope readiness matrix is closed. Fold7 installation and real-device acceptance are separately observed afterward. No release claim based on static assertions alone.

## Owner full-scope delivery gate (2026-11-06)
- For a requested new feature or integrated upgrade, **do not generate/sign/offer a user-facing APK** until **every requested feature is implemented together**, integrated into the real app, and the applicable local bug, dependency, regression, safety and packaging checks have passed. Internal checkpoints and partial green tests are not deliverables.
- No staged test releases or versionCode churn merely to demonstrate partial progress. A separately requested bug-fix update or explicit owner milestone is the exception.
- If a genuine blocker prevents finishing, report precisely what is incomplete and why; do not represent partial work as the completed request. Keep working on all unblocked obligations. This rule overrides conflicting older milestone/release language.


## Current-phone acceptance scope

The [2026-10-15 owner decision](https://github.com/pemunson90-cmd/Markdown-Desk/pull/24#issuecomment-6204593523)
defers cross-installation private-state export/import. Its unfinished capture/funding/
adoption stack is not a current-phone release gate and must not be enabled as a
partial backup feature. Local save/history/draft recovery, all other roadmap features,
non-destructive in-place upgrade, original certificate and Fold/device gates below
remain required. Explain in the app that copying `.md`/`.txt` transfers current text,
not private history, and clearing/uninstalling app storage can lose private history.
Future Export App Data / Import App Data must pass full validation/staging/provenance
gates before claiming portable private-state recovery.

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
