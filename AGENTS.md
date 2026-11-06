# Markdown Desk — standing Codex engineering contract

## Owner Android compatibility policy (2026-11-06)

Support the latest **stable** major Android release and the **two preceding major releases**, not older versions. As of this directive Android 17 is current (API 37), so Markdown Desk's active development minimum is **Android 15 (API 35)**, with required Android 15/16/17 compatibility checks. When a new stable major version arrives, review and advance the floor accordingly; don't retain legacy API workarounds solely for unsupported Android versions. The *minimum supported OS* and *targetSdkVersion* are separate decisions: raise minSdk to 35 now; adjust targetSdk only after evaluating behavior changes, especially Fold/window insets, storage permissions, and background imports. Preserve all previously working features and user data **on supported versions**, including in-place upgrades. Historical Android 8 / minSdk26 baselines remain provenance, not a requirement to support Android 8 in new versions.


## Android OS support window — owner directive (2026-11-06)

**Support only the current stable Android major version and the two immediately preceding major versions.** As of 2026-11-06, Android 17 (API 37) is stable, so the supported range is **Android 15–17 (minimum API 35)**. No requirement to maintain, run compatibility checks for, or design new workarounds specifically for Android 14/API 34 or earlier. Raise the minimum OS floor as the stable Android major-version window advances, with deliberate integration/testing rather than silently during an active build. This applies to manifests, build scripts, dependency choices, and device-test matrices. The *target SDK* is a distinct decision and may change only with relevant behavior checks.

**This is an OS-support policy only.** Preserve existing user data, in-place app upgrades, documents, notes, annotations, saved states, current functional features, and the canonical signing identity. Dropping old Android OS compatibility never authorizes destructive data migration or loss of working features.

## Owner hard rule — complete the whole assigned task before delivery (2026-11-06)

**When the owner assigns a build, upgrade, integration, or feature set, finish the ENTIRE requested scope.** Do not silently narrow it, substitute an MVP, postpone a requested feature, call a partial implementation "done", or end the assignment merely because one subsystem compiles or passes a test. "Everything" means the full authorized request; only the owner may expressly change the scope.

**No intermediate APKs, test releases, staged deliveries, or version churn for unfinished feature work.** Implement and integrate all requested features into the actual application first, using properly separated modules and the existing maintained systems. Then perform local bug checks, dependency checks, regression/performance/data-safety checks and build/sign/package **once the integrated task is complete**. Internal commits, tests, checkpoints, branches and progress updates are allowed for safe engineering; they are **not** independent deliverables or permission to stop early. No user-facing APK or installation request until the whole assignment is implemented and the required checks pass. Only an explicitly authorized bug-fix update or owner-requested delivery milestone is an exception.

**If completion is genuinely blocked**, explain the exact blocker, what remains undone, the concrete consequence, and what input/resource is needed; ask for the necessary information or additional time *without claiming asynchronous work or promising later delivery*. Continue every unblocked part of the original assignment. Never hide unfinished requirements in a release note or present known-incomplete feature work as a finished build.

Give brief progress reports at meaningful milestones and on real blockers, not an endless procession of builds. Prefer local development and testing; do not use GitHub Actions unless genuinely necessary. Protect existing user data, signing identity, and previously working features. **This owner directive takes precedence over any older wording about milestones, slices, test candidates, or "ship what works" elsewhere in this repository.**

## Non-negotiable feature continuity (owner directive)

**If Pat did not explicitly authorize removing a working capability, it stays.** Existing working features, gameplay rules, card effects, interfaces, workflows, integrations, content, data, settings, accessibility, and tools survive fixes, refactors, rewrites, ports, engine replacements and architecture changes. Do not silently delete, stub, disable, simplify, postpone or leave them behind because a new implementation is cleaner or the task named only one feature. Lack of an explicit preservation list is **not** permission to drop anything.

**Before coding**, identify the actual most recent owner-accepted/installed usable baseline and the current authoritative development/integration branch; verify commit ancestry and inspect existing successful implementations, including playable prototypes, simulators and branches that the new work replaces. Neither `main` nor the newest-looking version number alone establishes the full feature set. For replacements or substantial refactors, account for each relevant existing capability and preserve behavior or explicitly approved improvements; check meaningful parity/regressions against the old working path. Recheck on the final integration head. If preservation and the requested change conflict, retain the old working functionality and seek Pat's explicit ruling on that **specific** loss; continue independent non-destructive work. Do not substitute extra paperwork or indiscriminate test campaigns for actual continuity checks. Apply updated guidance at the next safe work boundary without disrupting active writers.


This file governs every Codex session in this repository. **Read it first**, then `docs/STATUS.md`, `docs/ROADMAP.md`, `docs/RELEASE_GATES.md`, and relevant current decisions/source. Do not obey historical one-shot kickoff notes over this contract. Git source and observed tests outrank AI summaries.

## Mission and ongoing authorization
Build the complete **Markdown Desk Professional** M0–M7 roadmap as one sustained engineering campaign, not one prompt per issue. The protected shipping baseline is 2.2.5 / versionCode 18, package `com.example.markdowndesk`, historical minSdk26/targetSdk35 (provenance only, superseded for current active development by the Android 15+ floor). The exact baseline source was imported on PR #9; never recreate it from memory. The current progress and next executable action live in `docs/STATUS.md`.

Continue to the next eligible, approved roadmap task immediately after each bounded change, PR, or milestone. A successful test or closed issue is a checkpoint, **not** the end of the assignment. Within the available run, pursue as much verified work as feasible. When context/runtime/usage ends, leave committed, recoverable work and precise next steps; instructions cannot make an ended session keep running.

Stop and request input only for a genuine external dependency, a new material product decision, irreversible/destructive action, unavailable private input, or required owner/device acceptance. When blocked on one item, record it and execute independent unblocked work. Never bypass failing safety/release gates to keep moving.

## Agent leadership, reasoning effort and delegation
- **Orchestration:** when model/agent selection is available, assign the strongest *capable* available agent/model to overall planning, integration, risk ownership and final synthesis. The orchestrator should keep repository context and make coherent architectural decisions.
- **Adaptive reasoning:** do **not** force maximum reasoning effort for every task. Use the lowest effort that gives reliable results. Escalate effort for architecture, ambiguous or conflicting evidence, high-risk persistence/migration, concurrency, hard bugs, design tradeoffs and critical review; de-escalate for mechanical edits and known-pattern work.
- **Subagents:** delegate bounded independent tasks to the **least expensive / weakest model that is demonstrably capable** of completing them correctly. Give each a clear objective, inputs, file ownership, acceptance tests and concise return contract. Do not use a weak model merely to save tokens if it is likely to redo work; escalate promptly if it fails.
- Keep the most capable reasoning available for orchestration, risky integration and independent challenge/review. Use separate reviewer agents where valuable; reviewers must check actual diffs/test evidence, not repeat a self-report.
- Parallelize independent tasks only with clean file ownership. Avoid multiple agents rewriting the same files or producing duplicate research. Delegation is optional when coordination overhead exceeds the work. If selection/subagents are unavailable, implement directly; do not pretend to have invoked them.
- Optimize **total** wall time and token/compute spend, including rework and context reloads—not the appearance of maximizing subagent utilization.

## Codex environment first; GitHub as durable remote
- **Do routine work inside the existing Codex workspace:** inspect files with local search, edit files locally, use local shell and git, build, run tests, diff, benchmark, debug, examine manifests and verify artifacts. Reuse installed JDK17 / Android SDK37 / build-tools36.0.0 when compatible; do not repeatedly reprovision or download already available tools.
- Use **local Git** for commits, branches, diffs, history and code review preparation. Use **GitHub remote** for pushing/synchronizing, PRs, code review, issue tracking, tags, durable documentation, and authorized releases. Prefer `git`/local filesystem to remote GitHub API calls for everyday reading/writing/searching.
- **GitHub Actions and external tools are exceptions, not the default workbench.** Invoke them when required by branch protections, portability/independent-runner verification, hardware/test infrastructure unavailable locally, or a specific integration/release gate. Do not trigger Actions for every trivial edit, duplicate local tests in CI without value, or use web/API round trips to inspect files already in the checkout.
- Avoid unnecessary agent calls, repeated broad repository scans, duplicate benchmark runs, verbose logs in context, and needless PR/issue fragmentation. Capture short reproducible commands and evidence once, then reuse it until inputs change.
- Offline-first builds/tests where practical. Never send signing material or personal content to hosted CI/PRs/issues.
- **CI and safety classification:** follow [docs/CI_AND_WORKFLOW_POLICY.md](docs/CI_AND_WORKFLOW_POLICY.md). Draft PR work uses local tests; review-ready PRs trigger hosted validation. Keep generic-provider destructive-write probes visibly RED as raw hazard evidence, while actual protected transaction/recovery behavior must pass required production-path tests. Neither test class may silently replace or excuse the other.

## Continuous implementation loop
1. Resolve the **current** checked-out branch/PR/issue and current status; consult `docs/STATUS.md` before planning. Inspect modified files, not the full project from scratch every turn.
2. Pick the next unblocked, high-value slice in `docs/ROADMAP.md` (safety and tests before feature expansion). Plan bounded implementation with test criteria and ownership.
3. Implement with explicit non-destructive migration and backward-compatibility requirements. Add behavioral tests before or with code; prefer meaningful functional tests over static source-string assertions.
4. Run the *smallest relevant* local tests early; run broader host/build/regression suites at integration gates. Benchmark on matched fixtures when performance is affected. State exactly what ran, failed or was unavailable.
5. Have an independent critical review where it improves correctness; fix defects, re-test and inspect final diff. Merge only when applicable code-review and safety gates pass.
6. Commit in coherent units; use one PR per meaningful integrated slice, not one per tiny edit. When gatekeeping permits, merge, reconcile documents and continue to the next task **without asking for a new prompt**.
7. Maintain a reproducible checkpoint before long context compaction, task transition or interruption. If unable to proceed anywhere, give a concise factual blocker report and the next exact action.

A completed import of unchanged source is not a new app release. Host builds are not device/Fold acceptance. Do not hold safe, already verified source/version-control housekeeping hostage to unrelated future physical-device tests; keep those gates explicit for user-facing releases.

## Documents are a living control plane — prevent staleness
- `AGENTS.md`: durable global engineering behavior, tool routing and safety rules; never copy the whole roadmap here.
- `docs/ROADMAP.md`: authorized features, dependencies, sequencing and completion gates. It is the full multi-milestone campaign scope.
- `docs/STATUS.md`: **single current-state checkpoint**: current verified baseline, active branch/PR, achieved test evidence, blockers, next runnable task, and true not-run gates. Refresh whenever task state changes or before pausing.
- `docs/DECISIONS.md`: append-only significant decisions, including revised decisions with links; retain chronology. Use scoped ADRs for material architecture.
- `README.md`: brief durable project introduction and pointers to authoritative live docs; do **not** freeze stale "start issue #1" instructions or claim implementation/release status based on plans.
- `docs/CODEX_START.md`: reusable short resume recipe, never an obsolete one-task mandate.
- Audit any directly affected README, CHECKPOINT, TODO, plan, handoff and build guide while changing behavior; update current docs once per coherent change, not after every keystroke. Clearly mark superseded historical evidence rather than silently altering archival records. Prefer one authoritative live status over contradictory copies.
- Before merging/releasing, run a **documentation consistency check**: versions, feature state, test status, paths, commands, active PR/issue, and next steps must not conflict. Do not call tests "passed" that were not run.

## Product invariants and safety
- User-owned Markdown/plaintext is canonical, fully local/offline, opened through scoped SAF. No mandatory cloud account, telemetry or broad storage permissions.
- **Existing Focus/View already hides substantial UI.** Preserve it, Write/Read/Split, fold/unfold, draggable divider, 12-tab semantics, locks, editing/navigation, recovery and external-change protections. Do not duplicate existing features.
- Preserve all nine themes and 25 owner-approved splash assets. Maintain contrast, accessibility and responsive layouts.
- Never silently overwrite user files, discard unsaved drafts or perform unapproved destructive migration. Preserve byte fidelity of unedited documents, test error paths, and keep protected 2.2.5 recovery reference.
- Canonical release signing key, passwords and private recovery archives must **never** enter Git, logs, issues, PRs, or GitHub Actions. Never create a replacement identity. Signed updates require original signer, increasing versionCode, verified signature and separate owner/device gates.
- Document all behavior changed/preserved/deferred in code review; do not conflate compile, host, emulator, phone and Fold results. `docs/RELEASE_GATES.md` controls production readiness.
- Reuse existing non-Gradle `build.sh` (`javac`, `aapt2`, D8, `zipalign`, `apksigner`) unless measured benefits and an approved ADR warrant a migration. The canonical signed path requires four private environment variables named in build scripts; never print their values.

## Current campaign checkpoint

PR #9 merged the verified source baseline; `v2.2.5-baseline` is a source-only provenance tag, not a new APK release. Issue #1 is complete. Resume active M0 behavioral safety tests and benchmarks from `docs/STATUS.md`, then continue eligible M1–M7 slices. Physical Fold/SAF/upgrade acceptance remains a user-facing release gate, not a reason to halt independent local tests/design work.
