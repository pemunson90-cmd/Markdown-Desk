# Post-release maintenance — 2026-11-07

Baseline: owner-accepted `V2.5.0`, code39; source guidance head `3775af3323ae97c133ef1969ca7e504356f9f833`. Scope: bounded bug/dependency/stale-doc audit and local test selection, not another release.

## Completed

- Added a local change-based planner over 106 existing/maintenance check commands, explicit source/fixture ownership, risk questions, and static/tooling classifications. It refuses unknown ownership, detects stale paths/duplicate commands, selects documentation-only changes without application tests, and can run selected host checks serially with first-failure stop and elapsed-time reporting. No dependencies, daemon, hosted CI or APK generation.
- Moved four verification-tool self-test suites out of unconditional app gates into changed-tool selection. Actual production behavior suites and actual source guard remain. Retired two obsolete PR37 assertions; reasons are in [TEST_SELECTION.md](../TEST_SELECTION.md).
- Removed stale 2.2.5/M0 automatic continuation instructions from agent entry points. Updated build/resume instructions and the source identity record to accepted code39/main. Historical source-contract remains in its existing single queue location; the live ledger remains execution authority.
- Aligned standalone build-tools default with the verified 36.1.0 source-only pipeline; no toolchain upgrade or application code change.

## Confirmed findings, still open

See [SEARCH_FINDINGS.md](SEARCH_FINDINGS.md): indexed long phrases can miss across 128-unit segment boundaries; token-normalized FTS results can lack literal reader highlighting. The small SQLite reproduction passed in the sense of reproducing the defects, **not** a product correctness pass. Both need a source-span/index repair that also handles existing databases. No partial runtime fix was made.

See [DEPENDENCY_AUDIT.md](DEPENDENCY_AUDIT.md): the first-candidate script is tied to the completed 2.5.0 flow and must be adapted before another release; optional browser probes are not pinned repository dependencies. No vulnerability scan or dependency advisory review was performed. No broad upgrades are justified by this inspection.

## Verification actually executed

- Selector behavior: 8 focused cases passed (docs, direct and transitive input impact, unknown source, new fixture, stale input, duplicate command, empty plan).
- Updated PR37 tooling assertions: 6 passed.
- Existing signed-phone-gate structure assertions affected by build default: 3 passed.
- Source record guard `--require-installed`: passed against preserved ancestry, manifest and acceptance metadata; not new device testing.
- Catalogue audit: valid runner/input ownership and no duplicate commands.
- CLI documentation-only plan: zero app tests; real maintenance plan: three tooling suites, no unmapped files.
- Shell syntax and diff whitespace checks passed. No app regressions, source compilation, stress run, signing, APK build, or Actions were run in this maintenance task.

## Next useful work

Repair exact indexed search using the bounded acceptance cases in SEARCH_FINDINGS.md, including existing indexes. Do not restart the full host/stress campaign to investigate those known semantics. The test selector is a conservative suite-level starting point, not an exact coverage oracle; remaining static wiring checks are review candidates, not proven duplicates.
