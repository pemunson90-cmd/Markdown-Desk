# Choose checks for the change

Run locally before verification:

```bash
python3 verification/select_checks.py --base <last-verified-commit>
```

The base is the last relevant tested source, not automatically the parent commit. The planner includes committed changes since that base, staged/unstaged edits, deletions, and untracked files. With no base it examines edits against HEAD. A clean HEAD therefore produces an empty plan; that is **not** evidence that HEAD was tested.

The planner prints each selected command, the question it answers, and any unmapped paths. `--json` includes triggering paths. `--paths README.md` is a planning example, never an execution mode. Documentation alone requires wording/link/diff review, not app tests.

After reviewing the plan, `--run` executes the selected host checks serially and stops at the first failure. It never installs dependencies, packages an APK, signs, or invokes Actions. Results and elapsed times go to ignored `verification/build/selected-results.json`. Source-only Android compilation is reported separately when application inputs change; run it once before delivering changed application code. A selected-host pass is not a release or device pass.

## What this first selector can establish

`verification/check_catalog.json` inventories the existing current-phone host runners and source-level checks, with questions and explicit input/fixture ownership. Java identifier references propagate shared-source impact conservatively to consumers. Family ownership includes existing fixtures and new files under that family.

This is a **conservative candidate set**, not a proof of the mathematically smallest suite or a complete Android dependency graph. Reflection, resource wiring, new source files, dynamic dependencies, and changes without explicit ownership can require manual mapping. Unmapped changes exit 2 and block automatic execution. Never silence them by labeling production files as documentation. MainActivity coupling and directory-wide fixture ownership can select more checks than a narrowly isolated utility; narrow ownership only after inspecting the actual runner and dependencies.

The standalone inline renderer/baseline/splash blocks in the full host script are not independently runnable catalogue entries yet. Changes that lack coverage remain unmapped rather than silently skipped. Choose the applicable existing check manually or extract a maintained runner before claiming coverage. Native compilation alone is not a behavioral replacement.

The selector does not cache passes. Reuse prior evidence only when its relevant source, fixture, dependency, and environment inputs remain valid. Record that reasoning in the existing live ledger; do not rerun simply because the commit SHA changed.

## Maintain and retire tests

```bash
python3 verification/select_checks.py --audit
```

This validates runner existence, stale input paths, duplicate commands, and missing questions, and lists static/tooling checks for review. It does **not** run them or delete anything. When adding or changing a test, update its family inputs and question. The catalogue is deliberately local JSON/Python; there is no new service or dependency.

A test earns continued use by exposing a distinct failure that matters to the product. Retire it when the risk is obsolete, or another identified test establishes the same behavior more directly. Record the old check, reason, and replacement (or why none is needed) in the existing maintenance report/commit. Age, speed, green history, assertion count, and similar names alone do not justify deletion. Distinct failure boundaries in save/recovery tests are not duplicates merely because they exercise the same class.

## Changes made in this audit

| Check | Decision | Reason / retained evidence |
| --- | --- | --- |
| `test_current_source_guard.py` | Tooling changes only | Tests the guard with synthetic Git repositories. The actual source guard still runs in the full app gate. |
| `test_pr37_integration.py` | Tooling/workflow changes only | Workflow and fixture-registration assertions do not need to rerun for ordinary app changes. |
| `test_source_only_gate.py` | Source-compile script changes only | Static checks of script text are distinct from actually compiling the app. |
| `test_signed_phone_gate.py` | Packaging/gate script changes only | Check gate structure when its inputs change, not on every app regression run. |
| PR37 historical version-allowlist test | Retired | Requiring obsolete version strings to remain forever is not current release identity protection. Actual manifest/source guards remain. |
| PR37 STATUS first-line assertion | Retired | A new current-status banner broke an irrelevant file-position constraint. Single source-record uniqueness checks remain. |
| 18 private-state portable families | Still deferred, not newly retired | Existing owner exclusion; retain separately runnable historical/full suites. |

All application behavior suites remain intact. No blanket deletion of static integration checks: some currently supply unique wiring evidence and need an identified replacement before retirement. Full current-phone release gates remain available; this selector replaces indiscriminate routine invocation, not required acceptance.

**Stopping condition:** selected relevant checks pass, applicable required gates have valid evidence, and no concrete unresolved question remains for this stage. Proceed to delivery or the next authorized task. Device-only questions go to the device.
