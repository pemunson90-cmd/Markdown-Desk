# Resume Codex — continuous Markdown Desk Professional campaign

This is a **reusable session entry point**, not a one-issue task order.

## Start or resume

1. Open `pemunson90-cmd/Markdown-Desk` in the already working Codex environment. Synchronize its branch with the latest remote changes; inspect `git status` and worktree before modifying anything.
2. Read [AGENTS.md](../AGENTS.md), [current state](STATUS.md), [M0–M7 roadmap](ROADMAP.md), [release gates](RELEASE_GATES.md), and current decisions relevant to the next task.
3. Identify the next verified, unblocked roadmap slice; check existing PRs/issues and source/test evidence before doing any further work. Do not replay completed M0.1 import.
4. Orchestrate with the strongest suitable available lead agent and adaptive effort; delegate tightly bounded tasks to the weakest capable subagents, escalating when correctness warrants it.
5. Build/test/edit/search/diff/commit **locally in Codex**. Use GitHub for source synchronization/versioning, PR/issue review, and durable documentation. Trigger external tools or GitHub Actions only when there is a concrete reason.
6. Complete a coherent change, test/review it, update `STATUS.md` and affected documentation, merge when required gates/approvals permit, then **continue to the next unblocked task without waiting for another prompt**.

If task execution is interrupted by context, runtime or limits, commit a clean checkpoint and record the exact next command/task in `STATUS.md`. If an approval/hardware/private-key gate blocks a specific task, continue independent work without bypassing that gate.

**Current note (2026-10-14):** [PR #9](https://github.com/pemunson90-cmd/Markdown-Desk/pull/9) is merged; `v2.2.5-baseline` records the exact-source baseline and issue #1 is closed. Active work is issue #2 host behavioral safety tests and issue #3 host benchmarks. Device/release acceptance remains separate. Always consult live `STATUS.md` for the latest branch/task.

## Short prompt for new Codex sessions

> Read `AGENTS.md`, `docs/STATUS.md`, and `docs/ROADMAP.md`. Resume the continuous Markdown Desk Professional M0–M7 campaign from the latest verified Git state. Orchestrate with the strongest available capable lead and adaptive effort, delegate to the cheapest capable agents, do routine work inside Codex, use GitHub/Actions only when necessary, keep docs current, and continue through successive unblocked milestones. Preserve release and safety gates. Do not stop after one issue.
