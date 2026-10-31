# Moonshot recovery checkpoint — October 31, 2026

## Verified durable state

At the owner's `Hello?` interruption, a fresh GitHub read confirmed branch `moonshot/professional-upgrade-20261031` still pointed to `6d6be39ce15b3facf2ac92bd6685777ee4e6530f`. That commit adds only the bootstrap workflow. The application remains the code27 source from `877facadce54247f38aa29d8b7c1cb5f055f813b` (PR #30). No new feature source, signed upgrade, merge, or device acceptance was published by the moonshot work.

The temporary `/mnt/data/markdown-desk-moonshot/repository` working checkout is not present in the current execution environment. Old conversation APK attachments remain. The local feature changes below had not been pushed; their implementation commands and test output are recorded in the conversation and need reconstruction and fresh verification. Do not mistake this checkpoint for the recovered feature source.

## Earlier execution evidence — not current reproduced results

The preceding run implemented a navigation candidate, versionCode28 / `2.3.0-navigation1`:
- `NavigationChoices.java`: immutable original-row identities, literal multi-term case-insensitive/canonical-Unicode filtering, duplicate-title disambiguation, stale-query and repeated-selection rejection.
- `NavigationPicker.java`: native AlertDialog adapter and recycled themed rows, custom title/search/status controls, clear/cancel, keyboard selection; final revision uses Android's built-in list measurement rather than a fixed-height custom list.
- MainActivity callers: searchable command palette (22 commands), outline and Recents; existing URI/open routes and source-navigation revision guard retained; picker dismissed on stop/configuration change.
- Tests: 24 chooser assertions, 29 actual Activity caller/dispatcher assertions through an AST-extracted harness, and 34 outline cases / 77 assertions passed. These are host/model-boundary evidence, not native UI evidence.
- The complete application compiled and packaged unsigned with Android SDK37.0/build-tools36.0.0 and local JDK21 targeting Java11. No code28 signed APK, emulator test, or Fold7 acceptance was completed.

## Test repairs and remaining failures

Both `verification/check_layout.py` and `check_live_appearance.py` rejected installed code27 because explicit version allowlists stopped at26. Earlier local changes added exact27 and candidate28 pairs, with four identity tests including rejection of mismatched/unknown pairs.

`PrivateFrameCopyCheck.guardsAndBudget` failed in fixture setup because its budget arithmetic omitted the newer scalar commitment charge. The earlier test-only repair included `PrivateStateCodec.scalarCommitmentCharge` and exercised exact-copy-budget admission versus one byte short. Production protection code was unchanged. Focused output:30 preflight scenarios and17 copy cases passed.

The exploratory full host run then stopped at `PrivateCapturePlanCheck.identitiesAndEvidence`, line136, with `HistoryStorage.BudgetExceeded`. The full suite was NOT green. Source was edited during that exploratory run, so it is not a frozen final-candidate acceptance result.

Existing owner scope defers18 portable cross-installation families. `verification/run_current_phone_checks.sh` is the applicable established current-phone host gate, as documented in RELEASE_GATES.md; it must be run on reconstructed frozen source, adding the missing current Downloads/navigation/identity checks without removing existing required checks. Do not relabel deferred or failed tests as passed.

## Resume in order

1. Recover the exact base via the authorized GitHub connection. Bootstrap run41170226192 previously produced artifact13031101033, containing source.bundle, SDK build inputs and the baseline failure log. Recheck its current availability before use. Credentials and original signing inputs are not missing merely because local Git transport fails.
2. Reconstruct the recorded source/test changes from the conversation, preserving the later native AlertDialog-list revision and correct `showReaderPlaces()` command route. The planned local source was not uploaded; do not claim otherwise.
3. Save a durable source patch/commit before any further long verification. Work only on the isolated moonshot branch, not stale main or another worker's branch.
4. Run the current-phone suite on frozen source; perform native chooser/lifecycle/IME/layout and unchanged Downloads/Recents journeys. Review and repair before signing. Preserve original package/certificate and data; never recommend uninstall.
5. Continue the existing M0–M7 product roadmap, with portable backup deferred. The navigation candidate is a first batch, not the completed moonshot. Recover the launch prompt and plan as recorded in the conversation; reconcile them with this checkpoint.

Owner evidence to retain: Pat successfully installed code27 and reports that old Recent Files reopen. The mechanism is unverified. Native Browse Files stays primary; Quick Downloads remains optional. No new independent reviewer or coding agent was launched in this run.
