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

**Current note (2026-10-14):** PRs #9–#13 and #15 are merged; `v2.2.5-baseline` remains protected. Draft PR #14 is being reconciled with independently reviewed shared authority, pressure planning and foreign-style syntax repairs. Consult live `STATUS.md` and inspect remote heads before continuing; preserve both environments' work. Device/release acceptance remains separate.
