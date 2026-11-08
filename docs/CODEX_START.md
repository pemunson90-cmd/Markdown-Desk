# Resume Codex — continuous Markdown Desk Professional campaign

> **Current Unified Workspaces reconciliation (2026-11-06):** Before taking work from this historical Professional resume recipe, read [the single authoritative integration queue](handoffs/ACTIVE_UNIFIED_WORKSPACES_QUEUE.md) and verify its recorded current head. The Unified Workspaces release boundary—not the entire later M0–M7 roadmap—controls active work; cross-installation private-state export/import remains excluded. Execute **one explicitly requested bounded batch per user turn**, save evidence in that existing queue, and stop. Do not autonomously resume, treat the oldest 'current' STATUS section as a live handoff, duplicate status systems, or merge/retire historic PRs before reconciliation. PR #37 source/workflow efficiency repairs are first implementation priority once authorized. All legacy details below remain historical reference and cannot override this notice.

> **Source verification before editing:** Fetch the named integration branch (without resetting/switching a dirty worktree), then run `python3 verification/check_current_source.py` from a clean or safely preserved descendant checkout. The guard reads the single `docs/handoffs/ACTIVE_UNIFIED_WORKSPACES_QUEUE.md` source-contract, validates manifest/anchor/fetched-tip/evidence, and warns on dirty work. Stop and reconcile if it fails. Use `--require-clean` for final clean-source tests, and `--require-installed` only for release acceptance; neither a test candidate nor stale `main` establishes device acceptance. Run `python3 -m unittest discover -s verification -p 'test_current_source_guard.py'` for focused regression verification.

This is a **reusable session entry point**, not a one-issue task order.

## Start or resume

1. Open `pemunson90-cmd/Markdown-Desk` in the already working Codex environment. Synchronize its branch with the latest remote changes; inspect `git status` and worktree before modifying anything.
2. Read [AGENTS.md](../AGENTS.md), [current state](STATUS.md), [M0–M7 roadmap](ROADMAP.md), [release gates](RELEASE_GATES.md), and current decisions relevant to the next task.
3. Identify the next verified, unblocked roadmap slice; check existing PRs/issues and source/test evidence before doing any further work. Do not replay completed M0.1 import.
4. Orchestrate with the strongest suitable available lead agent and adaptive effort; delegate tightly bounded tasks to the weakest capable subagents, escalating when correctness warrants it.
5. Build/test/edit/search/diff/commit **locally in Codex**. Use GitHub for source synchronization/versioning, PR/issue review, and durable documentation. Trigger external tools or GitHub Actions only when there is a concrete reason.
6. Complete a coherent change, test/review it, update `STATUS.md` and affected documentation, merge when required gates/approvals permit, then **continue to the next unblocked task without waiting for another prompt**.

If task execution is interrupted by context, runtime or limits, commit a clean checkpoint and record the exact next command/task in `STATUS.md`. If an approval/hardware/private-key gate blocks a specific task, continue independent work without bypassing that gate.

`v2.2.5-baseline` remains protected. Read the live `STATUS.md` for current merged
and draft work rather than relying on a frozen PR list in this resume recipe.
Preserve both environments' work; device/release acceptance remains separate.
