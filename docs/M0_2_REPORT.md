# M0 behavioral test checkpoint

Date: 2026-10-14. Baseline: `v2.2.5-baseline` / `d1288a8`.
This adds local executable fixtures and host measurement; it does not declare
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

## Reproduced baseline failures — unresolved

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
cannot guarantee atomic saves; the product guarantee decision is pending. No M1
feature integration before prerequisites/approved sequencing. Preserve baseline
rollback and keep signed APK/device acceptance separate.
