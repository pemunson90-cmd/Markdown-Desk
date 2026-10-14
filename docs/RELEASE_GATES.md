# Build, test, and release gates

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
