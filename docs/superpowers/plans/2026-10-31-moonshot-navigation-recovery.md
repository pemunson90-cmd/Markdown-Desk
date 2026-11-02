# Moonshot navigation recovery

Goal: reconstruct the interrupted navigation slice on code27, not restart the app.
Authority: owner approved recovery, commit and verification in the current conversation; remote recovery checkpoint 1c8fe53. The original unpushed bytes are unavailable here, so this is a fresh implementation of the documented behavior, not byte-identical restoration.

1. Reproduce installed-version fixture failure; add tests for immutable filtered identities, Unicode/literal multi-term search, duplicate labels, stale snapshots, cancellation and repeated selection.
2. Implement NavigationChoices and native NavigationPicker, then wire existing command/outline/Recents entry points without changing document storage, URI permissions or source-navigation guards.
3. Repair exact version pairs in both existing layout gates. Repair the documented private-frame test budget only after reproducing its failure; do not modify protection code.
4. Save source and tests remotely on the Moonshot branch before long acceptance runs. Direct git network is unavailable; a branch-confined checked patch and Actions source commit may bridge the authorized GitHub connector. Preserve concurrent remote changes, no force push.
5. Run current-phone host gate and full host suite separately, compile/build exact candidate, and run native tests when an actual Android runner is available. Sign locally with original certificate only after relevant checks, retaining physical device acceptance as pending.

Shared boundaries: one writer owns MainActivity. NavigationChoices returns original row IDs, never filtered indices. Picker retires a selection before caller dispatch. Outline retains SourceNavigation identity/revision checks; Recents keeps the captured URI/open route.
Review focus: duplicate recent names; query change between display/click; reentrant second picker; stale document/mode; cancel/stop/configuration; keyboard/empty results; native dialog measurement and all nine theme text contrast.

Scope: versionCode28 / 2.3.0-navigation1 is a first Moonshot batch, not completed M0–M7. Portable private-state features remain deferred. Native Browse files and optional Quick Downloads remain unchanged. Pat reports old Recents work in code27; causal mechanism unverified.
