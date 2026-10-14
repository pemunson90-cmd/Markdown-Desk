# M0 behavioral test checkpoint

Date: 2026-10-14. Baseline: `v2.2.5-baseline` / `d1288a8`.
The initial tests-only checkpoint is commit `d35d02f`. This report retains its
baseline findings; see the subsequent fix section and live STATUS for current
source behavior. This adds local executable fixtures and host measurement; it does not declare
M0 complete, close issue #2/#3, fix production behavior, or establish a release.

## Commands and results

With `PATH="$JAVA_HOME/bin:$PATH"`, after the existing pinned host dependency
setup, run `bash verification/run_host_checks.sh`.

| Actual test domain | Result | Boundary |
| --- | --- | --- |
| Strict baseline inventory | 6 tests pass; exact runtime files unchanged | Reject added Java/assets, substitutions, symlinks, missing/digest failures |
| DocumentIO | 39 preservation/provider-contract assertions pass | Real source with simulated resolver/cursor and real Linux FD cases |
| Session/recovery | 13 cases / 568 assertions pass | Copy, 12 tabs, all persisted fields, malformed/default/clamped state, legacy/v2 recovery, staged/open/write/sync/replace failures |
| Renderer/palettes | 368 independent assertions pass | Nine palettes × five sizes; headings, lists/tasks, table/escaped pipes, escaping/fences, Unicode/links, CRLF/LF |
| Mode/lock methods | 99 assertions pass | Five actual methods extracted by compiler AST into simulated Android controls |
| Existing baseline checks | Pass | 27 renderer cases,49 SplashStore assertions,15 independent fixtures, syntax and static Appearance/layout guards |

`bash verification/run_safety_gates.sh` is a **separate desired-safety/spec gate**,
not an expected-failure success wrapper. It must remain red until defects are
fixed. The renderer preservation runner explicitly reproduces its known nested
list failure, then the safety runner executes that desired assertion directly
and returns nonzero. Contrast `--gate` similarly returns nonzero on violations.

## Reproduced baseline failures — historical test checkpoint

- Five DocumentIO safety probes fail: null metadata descriptor fabricates
  writability; two >8MiB payloads (ASCII and Unicode/BOM-inclusive) overwrite
  before read-back rejection; failures after2bytes and0bytes leave the original
  truncated rather than intact. No assertion describes that data loss as safe.
- Nested-list fixture fails: indented child becomes a sibling. Independent
  expected HTML was authored before observing actual output.
- Numeric text-color audit fails16pairs among54samples: Save white/accent in
  eight palettes, selected accent/soft in three, Paper/Bubblegum links and
  dialog accent pairs. This audit uses existing source pair choices, not a
  screenshot or paint test; alpha/disabled/selection and every widget are not
  covered. All nine palette values remain unchanged.
- Architecture review found latest edits not durably committed before provider
  mutation and recovery loading skipped when session parsing throws. These are
  source-reviewed risks, not claimed executed MainActivity fault tests.

The complete safety gate is red. The passing preservation suite is not a claim
that the app is safe to release. Local evidence: `verification/2026-10-14/m0/`;
fixture READMEs document doubles, real host faults and reproduction commands.

## UI modes and lifecycle inventory

Write, Read and Split each have normal and Focus/View states. The extracted
mode/lock harness executes Focus on/off, tool/chrome visibility, padding, current
mode selection, keyboard branch calls, read-only lock affordances and lock
transitions. Ancillary body rebuilding/persistence/keyboard effects are simulated.
It does **not** prove real mode-switch scrolling, actual View/IME/Fold layout,
TalkBack or visual appearance. These remain a device checklist.

Document lifecycle inventory: untitled/clean → editable/dirty or locked/read-only;
open provider bytes → retained baseline hash/BOM/metadata; explicit save/Save As
→ saving captured tab → verified baseline/clean or conflict/error/dirty. Session
and recovery envelopes survive host staged-file and commit failures in tested
cases. External-modification preflight, tabs switching during async save, process
kill, Save As identity and real grants require coordinator/device tests. Existing
12-tab semantics and all source/assets remain byte-identical in this checkpoint.

## Measurements and next bounded work

See [M0.3 host baseline](benchmarks/M0_3_HOST_BASELINE.md): deterministic10k/100k/
500k word fixtures and exact8MiB host read, recorded once with1GiBJDK17heap.
500k median host word count21.510ms, search16.523ms and render363.513ms are host
algorithm results, not Android responsiveness. No UI/device budget established.

Next: test-first DocumentIO8MiB-write preflight and null-descriptor fix, then
contrast/readability and independent session/recovery loading; review the safe
save boundary in [ADR-001](decisions/ADR-001-safe-save-boundary.md). Generic SAF
cannot guarantee atomic saves; the owner delegated the product guarantee decision; ADR-001 selects mandatory recoverable copies of both versions. No M1
feature integration before prerequisites/approved sequencing. Preserve baseline
rollback and keep signed APK/device acceptance separate.

## Subsequent local M0 fixes (current branch)

- Oversized encoded writes now reject before resolver access; null descriptors
  no longer fabricate write capability. Original desired probes now3pass/2fail.
  Failed partial/zero-byte provider writes still fail original-preservation tests.
- Foreground derivation retains already-readable colors, otherwise minimally
  blends them toward black/white; all9paletteconstants and25assets remain exact.
  6,264independent helper assertions,54pair checks and72checks on AST-extracted
  actual styleButton with recorded drawable colors pass. Caption backing is
  darker;512independent sRGB/linear compositing cases cover worst-case white.
  Gallery labels/state and actual Save/dialog/icon routes are included in review.
  No screenshot/disabled-alpha/font-scale/real paint acceptance is claimed.
- Nested-list regression now passes12independent list assertions (including
  200levels), with467renderer/palette assertions. Only contiguous list lines supported;
  no fullCommonMark/multiline-list claim.
- Wide 2,000/5,000-column tables previously threw StackOverflowError. Iterative
  divider recognition now passes14,020grammar/differential assertions and all
  five input probes. Header links/blocked-image text, linked code and quoted code
  have context-specific readable foreground tests.
- Recovery read independent of session parse:10cases134assertions green after
  two prior failing cases. Actual callback bodies are simulated; no device claim.
- Actual updateStatus takes one immutable string snapshot instead of two:
 24statusassertions pass. [Matched measurement](benchmarks/M0_3_STATUS_SNAPSHOT.md)
 reports noisy samples and does not claim a reliable wall-time speedup.
- Behavior-changing source is not claimed byte-identical. Provenance verifies
  immutable pinned `v2.2.5-baseline` and current cleared assets; active source
  requires actual diff/tests/review.10checker tests include coordinated mutable
  inventory+asset tampering and dangling/directory symlink rejection.
- M0/issue#2/#3 remain open. Durable pre-write protection is designed inADR-001,
  not implemented; owner delegated the recoverability policy decision. Generic
  SAF may partially mutate a URI; restore-to-new-copy must preserve both versions.

## Integrated checkpoint verification

Independent risk review found no blocking defect in the bounded fixes. Two
fresh committed-source checkouts at `8462f79` pass the host suite and build
byte-identical genuinely unsigned APKs: SHA-256
`e19e10d8e1c85324b4cca2bbb7d142734a5d926f87aebfdb56f53662d8dd5d9e`.
JDK17, installed SDK37.0 and build-tools36.0.0 reused without dependency
downloads. Chromium overflow proxy passes108cases; no screenshot comparison
or Android device acceptance is implied. The safety gate still fails exactly
two destructive provider probes. PR10 stays draft.
