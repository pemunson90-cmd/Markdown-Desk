# Markdown Desk — standing Codex engineering contract

## Mandatory testing stop rule — all agents (owner directive, 2026-11-07)

**Every agent working in this repository must read and follow this section before planning or running verification.** The lead agent must include this rule in delegated task instructions. It overrides broader or repetitive testing language elsewhere in repository guidance.

**Every test must answer a specific unresolved question whose answer could change the next engineering or delivery decision.** “Being thorough,” reassurance, and increasing test counts are not sufficient reasons to run more checks.

- Start with `python3 verification/select_checks.py --base <last-verified-commit>` and [the selection procedure](docs/TEST_SELECTION.md). An unmapped change requires an explicit verification decision; it is never an automatic pass.
- Define the smallest relevant verification set and its stopping condition for the actual change. Use maintained tests and existing evidence; do not create a new audit campaign by default.
- Once the relevant checks and applicable required gates pass, **stop testing and proceed to delivery or the next authorized task**. Do not make the owner enforce this stopping point.
- Reopen verification only for changed code or dependencies that affect the evidence, a failure, or a concrete new concern. Identify the question and run the affected checks; do not automatically restart every legacy suite.
- Reuse valid results across sessions, documentation edits, merges with unchanged application inputs, and publication of the same verified artifact. A new commit SHA alone does not invalidate test evidence; record the tested source/artifact and what changed.
- Run required release gates once at the appropriate boundary. Do not bypass a failed gate or omit a genuinely required check, but do not multiply equivalent checks or add gates without a specific risk they resolve.
- When the remaining question requires the phone, Fold layout, or a real file provider, move to that device acceptance step after the applicable local gates pass. More host testing cannot answer it.
- For documentation-only changes, review the wording, links, and diff as relevant. Do not build the app or run application regression suites.

Keep the rationale brief and reuse the existing ledger when needed. This rule must not become another checklist bureaucracy or a reason to write tests for the testing policy itself.

> **Current baseline:** owner-accepted Markdown Desk 2.5.0 / versionCode 39, published as `V2.5.0`. Read [LIVE_WORK_LEDGER.md](docs/handoffs/LIVE_WORK_LEDGER.md) for the active task and evidence, then inspect the actual branch and source. The old integration queue, STATUS chronology, and M0–M7 roadmap are historical context, not automatic task orders. Work only within the current owner-authorized scope; do not restart completed work or retire branches/PRs without authorization.
> **Release boundary:** preserve all working capabilities and user data. Cross-installation private application-state export/import remains deferred. Local save/history/recovery and ordinary exports remain required. No GitHub Actions without explicit owner authorization.

## Owner Android compatibility policy (2026-11-06)

Support the latest **stable** major Android release and the **two preceding major releases**, not older versions. As of this directive Android 17 is current (API 37), so Markdown Desk's active development minimum is **Android 15 (API 35)**, with required Android 15/16/17 compatibility checks. When a new stable major version arrives, review and advance the floor accordingly; don't retain legacy API workarounds solely for unsupported Android versions. The *minimum supported OS* and *targetSdkVersion* are separate decisions: raise minSdk to 35 now; adjust targetSdk only after evaluating behavior changes, especially Fold/window insets, storage permissions, and background imports. Preserve all previously working features and user data **on supported versions**, including in-place upgrades. Historical Android 8 / minSdk26 baselines remain provenance, not a requirement to support Android 8 in new versions.


## Owner hard rule — complete the whole assigned task before delivery (2026-11-06)

**When the owner assigns a build, upgrade, integration, or feature set, finish the ENTIRE requested scope.** Do not silently narrow it, substitute an MVP, postpone a requested feature, call a partial implementation "done", or end the assignment merely because one subsystem compiles or passes a test. "Everything" means the full authorized request; only the owner may expressly change the scope.

**No intermediate APKs, test releases, staged deliveries, or version churn for unfinished feature work.** Implement and integrate all requested features into the actual application first, using properly separated modules and the existing maintained systems. Then perform local bug checks, dependency checks, regression/performance/data-safety checks and build/sign/package **once the integrated task is complete**. Internal commits, tests, checkpoints, branches and progress updates are allowed for safe engineering; they are **not** independent deliverables or permission to stop early. No user-facing APK or installation request until the whole assignment is implemented and the required checks pass. Only an explicitly authorized bug-fix update or owner-requested delivery milestone is an exception.

**If completion is genuinely blocked**, explain the exact blocker, what remains undone, the concrete consequence, and what input/resource is needed; ask for the necessary information or additional time *without claiming asynchronous work or promising later delivery*. Continue every unblocked part of the original assignment. Never hide unfinished requirements in a release note or present known-incomplete feature work as a finished build.

Give brief progress reports at meaningful milestones and on real blockers, not an endless procession of builds. Prefer local development and testing; do not use GitHub Actions unless genuinely necessary. Protect existing user data, signing identity, and previously working features. **This owner directive takes precedence over any older wording about milestones, slices, test candidates, or "ship what works" elsewhere in this repository.**

## Non-negotiable feature continuity (owner directive)

**If Pat did not explicitly authorize removing a working capability, it stays.** Existing working features, gameplay rules, card effects, interfaces, workflows, integrations, content, data, settings, accessibility, and tools survive fixes, refactors, rewrites, ports, engine replacements and architecture changes. Do not silently delete, stub, disable, simplify, postpone or leave them behind because a new implementation is cleaner or the task named only one feature. Lack of an explicit preservation list is **not** permission to drop anything.

**Before coding**, identify the actual most recent owner-accepted/installed usable baseline and the current authoritative development/integration branch; verify commit ancestry and inspect existing successful implementations, including playable prototypes, simulators and branches that the new work replaces. Neither `main` nor the newest-looking version number alone establishes the full feature set. For replacements or substantial refactors, account for each relevant existing capability and preserve behavior or explicitly approved improvements; check meaningful parity/regressions against the old working path. Recheck on the final integration head. If preservation and the requested change conflict, retain the old working functionality and seek Pat's explicit ruling on that **specific** loss; continue independent non-destructive work. Do not substitute extra paperwork or indiscriminate test campaigns for actual continuity checks. Apply updated guidance at the next safe work boundary without disrupting active writers.


This file governs every agent session in this repository. **Read it first**, then `docs/handoffs/LIVE_WORK_LEDGER.md` and the source and guidance relevant to the authorized task. Read `docs/RELEASE_GATES.md` when preparing a new release. Do not obey historical one-shot kickoff notes over this contract. Git source and observed tests outrank AI summaries.

## Mission and task scope

Complete the owner's current assignment and stop when its acceptance conditions are met. Continue through its independent unblocked work without requiring repeated “Continue” prompts. A roadmap is not standing authorization to start unrelated expansion. Preserve `v2.2.5-baseline` as historical provenance and the accepted `V2.5.0` release as the current shipping baseline until superseded by an owner-accepted release.

If blocked by an external dependency, material product decision, unavailable private input, or necessary device acceptance, record the blocker and next executable action in the live ledger. Never bypass a failing relevant safety gate or misrepresent host tests as device acceptance.

## Agent leadership, reasoning effort and delegation
- **Orchestration:** when model/agent selection is available, assign the strongest *capable* available agent/model to overall planning, integration, risk ownership and final synthesis. The orchestrator should keep repository context and make coherent architectural decisions.
- **Adaptive reasoning:** do **not** force maximum reasoning effort for every task. Use the lowest effort that gives reliable results. Escalate effort for architecture, ambiguous or conflicting evidence, high-risk persistence/migration, concurrency, hard bugs, design tradeoffs and critical review; de-escalate for mechanical edits and known-pattern work.
- **Subagents:** delegate bounded independent tasks to the **least expensive / weakest model that is demonstrably capable** of completing them correctly. Give each a clear objective, inputs, file ownership, acceptance tests and concise return contract. Do not use a weak model merely to save tokens if it is likely to redo work; escalate promptly if it fails.
- Keep the most capable reasoning available for orchestration, risky integration and independent challenge/review. Use separate reviewer agents where valuable; reviewers must check actual diffs/test evidence, not repeat a self-report.
- Parallelize independent tasks only with clean file ownership. Avoid multiple agents rewriting the same files or producing duplicate research. Delegation is optional when coordination overhead exceeds the work. If selection/subagents are unavailable, implement directly; do not pretend to have invoked them.
- Optimize **total** wall time and token/compute spend, including rework and context reloads—not the appearance of maximizing subagent utilization.

## Codex environment first; GitHub as durable remote
- **Do routine work inside the existing Codex workspace:** inspect files with local search, edit files locally, use local shell and git, build, run tests, diff, benchmark, debug, examine manifests and verify artifacts. Reuse installed JDK17 / Android SDK37 / build-tools36.1.0 when compatible; do not repeatedly reprovision or download already available tools.
- Use **local Git** for commits, branches, diffs, history and code review preparation. Use **GitHub remote** for pushing/synchronizing, PRs, code review, issue tracking, tags, durable documentation, and authorized releases. Prefer `git`/local filesystem to remote GitHub API calls for everyday reading/writing/searching.
- **No GitHub Actions without explicit owner authorization.** Use local verification and reuse valid evidence. A historical workflow or branch-protection note is not authorization to execute hosted jobs.
- Avoid unnecessary agent calls, repeated broad repository scans, duplicate benchmark runs, verbose logs in context, and needless PR/issue fragmentation. Capture short reproducible commands and evidence once, then reuse it until inputs change.
- Offline-first builds/tests where practical. Never send signing material or personal content to hosted CI/PRs/issues.
- **CI and safety classification:** follow [docs/CI_AND_WORKFLOW_POLICY.md](docs/CI_AND_WORKFLOW_POLICY.md). Use local tests; historical hosted-validation recipes do not override the no-Actions rule. Keep generic-provider destructive-write probes visibly RED as raw hazard evidence, while actual protected transaction/recovery behavior must pass required production-path tests. Neither test class may silently replace or excuse the other.

## Continuous implementation loop
1. Resolve the **current** checked-out branch/PR/issue and current status; consult `docs/handoffs/LIVE_WORK_LEDGER.md` before planning. Inspect modified files, not the full project from scratch every turn.
2. Pick the next unblocked part of the owner-authorized task. Define its smallest relevant verification set, stopping condition, and file ownership.
3. Implement with explicit non-destructive migration and backward-compatibility requirements. Add behavioral tests before or with code; prefer meaningful functional tests over static source-string assertions.
4. Run the *smallest relevant* local tests early; run broader host/build/regression suites at integration gates. Benchmark on matched fixtures when performance is affected. State exactly what ran, failed or was unavailable.
5. Have an independent critical review where it improves correctness; fix defects, re-test and inspect final diff. Merge only when applicable code-review and safety gates pass.
6. Commit in coherent units; use one PR per meaningful integrated change. Merge only within the authorized scope. Continue the assigned work; stop when it is complete.
7. Maintain a reproducible checkpoint before long context compaction, task transition or interruption. If unable to proceed anywhere, give a concise factual blocker report and the next exact action.

A completed import of unchanged source is not a new app release. Host builds are not device/Fold acceptance. Do not hold safe, already verified source/version-control housekeeping hostage to unrelated future physical-device tests; keep those gates explicit for user-facing releases.

## Documents are a living control plane — prevent staleness
- `AGENTS.md`: durable global engineering behavior, tool routing and safety rules; never copy the whole roadmap here.
- `docs/ROADMAP.md`: authorized features, dependencies, sequencing and completion gates. It is the full multi-milestone campaign scope.
- `docs/handoffs/LIVE_WORK_LEDGER.md`: single current execution checkpoint: baseline, branch, task, executed evidence, blockers, and next action. `docs/STATUS.md` retains historical chronology and redirects here.
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

## Resume checkpoint

Use the live ledger and current owner request. The Professional campaign and PR #9 import are completed historical context, not instructions to resume M0. Shipping an unchanged verified artifact or editing documentation does not require rebuilding or rerunning application tests.
