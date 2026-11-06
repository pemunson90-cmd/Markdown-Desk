# Resume Codex — continuous Markdown Desk Professional campaign

This is a **reusable session entry point**, not a one-issue task order.

## Start or resume

1. Inspect `git status` and worktree without discarding or switching existing work. Read `docs/CURRENT_SOURCE.json` and the first current section of `docs/STATUS.md`. Confirm the originating task/active owner. Fetch its named integration branch, then run `python3 verification/check_current_source.py`; stop and reconcile on failure. Do not pick `main`, the newest PR, or a candidate version number as the installed baseline. Use an isolated descendant branch for disjoint fixes.
2. Read [AGENTS.md](../AGENTS.md), the generated current section of [status](STATUS.md) (historical sections only when relevant), [M0–M7 roadmap](ROADMAP.md), [release gates](RELEASE_GATES.md), and current decisions relevant to the next task.
3. Identify the next verified, unblocked roadmap slice; check existing PRs/issues and source/test evidence before doing any further work. Do not replay completed M0.1 import.
4. Orchestrate with the strongest suitable available lead agent and adaptive effort; delegate tightly bounded tasks to the weakest capable subagents, escalating when correctness warrants it.
5. Build/test/edit/search/diff/commit **locally in Codex**. Use GitHub for source synchronization/versioning, PR/issue review, and durable documentation. Trigger external tools or GitHub Actions only when there is a concrete reason.
6. Complete a coherent change, test/review it, update `STATUS.md` and affected documentation, merge when required gates/approvals permit, then **continue to the next unblocked task without waiting for another prompt**.

If task execution is interrupted by context, runtime or limits, commit a clean checkpoint and record the exact next command/task in `STATUS.md`. If an approval/hardware/private-key gate blocks a specific task, continue independent work without bypassing that gate.

`v2.2.5-baseline` remains protected. Read the live `STATUS.md` for current merged
and draft work rather than relying on a frozen PR list in this resume recipe.
Preserve both environments' work; device/release acceptance remains separate.

Checkpoint the exact published head, saved artifact/evidence location, test outcomes, remaining next step, and observed execution state. A checkpoint is not a stopping condition. If display or delivery fails, inspect saved work before re-running it. Refresh the source record and generated status header together when the authoritative branch or manifest changes. An unconfirmed installed identity does not stop independent safe work, but it blocks release acceptance.
