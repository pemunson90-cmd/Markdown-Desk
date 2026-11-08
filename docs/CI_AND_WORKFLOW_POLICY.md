# CI and execution policy — Markdown Desk Professional

This document defines **when to run tests** and **what their results establish**, not permission to waive any release or document-safety requirement. See [AGENTS.md](../AGENTS.md), [release gates](RELEASE_GATES.md), [ADR-001](decisions/ADR-001-safe-save-boundary.md), and [live status](STATUS.md).

## Owner local-first verification rule (2026-11-07)

Develop in the environment actually available: ordinary ChatGPT with the connected GitHub repository is an approved source-editing and commit workflow; Codex/WSL is **not a prerequisite**. For executable checks, prefer a clean full-source checkout in a local runtime, or isolated exact Git-blob-verified production sources with honest scope labels when network access separates the shell from the connected GitHub tool. Run the smallest meaningful functional regressions while editing and the full applicable local host/native gates once the entire product integration is ready. Do not equate import scans, synthetic toolchain smokes, or static checks with a full app compile.

**GitHub Actions are NOT the default verification runner.** For this Unified Workspaces implementation, do **not** dispatch, require, or intentionally trigger hosted GitHub Actions unless the owner explicitly authorizes a concrete exception or a hard external repository protection makes them unavoidable. Use local tests instead, preserving their exact source SHA, command and observed results. A workflow's existence does not authorize running it.

**Draft PRs:** keep the unfinished integration as draft, avoid marking ready for review (which could automatically trigger the existing hosted workflow), and do not confuse skipped hosted jobs with passing tests. Review-ready/hosted CI remains a historical repository capability, not an implementation-stage requirement for this owner-directed release. No routine background or repeated hosted builds.

## Separate the two safety claims

### A. Required application-level recoverability (must be GREEN before merge/release)

Application behavior must **durably protect exact original and attempted edit bytes before opening a destructive provider writer**, fail closed when protection cannot commit, retain both versions through partial/zero-byte provider failures, and expose explicit restore to a **new copy**. The source revision/identity, Save As behavior, and recoveries must be correct.

The current production-path host fixtures in `bash verification/run_host_checks.sh` include the real `ProtectionStore`, Android adapter double, save coordinator, constrained-memory probe, save lifecycle, and recovery/mode tests. These are the **required local host checks**, with genuine assertions and a nonzero failure exit. They are evidence within their stated host/simulation boundaries, not Android device acceptance. Add further **production-path** tests as implementation grows. A green host run alone never establishes power-loss, SAF-provider, Fold, TalkBack, or upgrade readiness.

### B. Low-level generic-provider atomicity hazard (diagnostic, expected to remain RED)

`bash verification/document_io/run.sh --safety` reproduces two destructive-provider scenarios where a raw `rwt`/`wt` write leaves a provider document truncated. Android Storage Access Framework does not guarantee universal atomic replacement. ADR-001 deliberately promises recoverability of **protected versions**, not immutability of the original provider URI.

These original strong probes must **remain runnable, unchanged and visibly reported as failing** until a genuinely stronger primitive is available. Never turn their assertions into expected-pass wrappers, mark them skipped while claiming coverage, remove the fixtures, or claim that their RED result means protected transactions failed. A low-level primitive test cannot be a universal required-GREEN merge gate when the product's explicitly selected contract instead establishes protected recoverability.

The historical `bash verification/run_safety_gates.sh` aggregates the primitive and protected checks and may therefore remain RED; do not use its single exit status to represent application-level recoverability. Keep it available as a **manual diagnostic** with its full raw failure output. Any actual failing production-recoverability test remains blocking, even if the primitive failure also occurs.

## CI pipeline definition

The workflow at `.github/workflows/host-regression.yml` runs **only on review-ready PRs or manual dispatch**. Required checks execute `bash verification/run_host_checks.sh`; they do **not** execute `run_safety_gates.sh`, which mixes independent contracts. The workflow summary explicitly states the retained raw-provider hazard and unavailable physical testing.

If the full host runner does not yet cover a new production safety path, extend its direct production tests *before* declaring the PR ready, rather than assuming existing tests cover it.

No hosted workflow contains private keystores or signing passwords. Run a signed in-place install only in the authorized release context with the original certificate.

## Parallel development and review efficiency

- Keep data-integrity/save-coordinator work in its own reviewed change boundary; only merge it once required tests are green and the currently selected contract is correctly documented. Do not rewrite existing sensitive paths while splitting unrelated work.
- Use distinct branches/worktrees or file ownership to develop independent [issue #3](https://github.com/pemunson90-cmd/Markdown-Desk/issues/3) performance and [issue #4](https://github.com/pemunson90-cmd/Markdown-Desk/issues/4) accessibility work. Each must be anchored to a correct common base; rebase/merge without force-pushing away other work.
- PR #10 has a broad verification surface. Consider a **review split** into (i) bounded completed renderer/contrast/accessibility repairs and (ii) durable save and recovery, **only when feasible without breaking tests or rewriting history destructively**. Do not churn a stable branch just to attain an arbitrary file count.
- Avoid repetitive whole-project test runs on unchanged inputs and repeated log files in Git. Maintain concise summaries with commands, commit hashes, checksums, failures, and links to full retained evidence.
- Keep `docs/STATUS.md` accurate on the active working branch; refresh active task, test provenance, blockers and next runnable independent work before any handoff. Historical logs/ADRs retain chronology even when superseded.

## Acceptance boundaries

Host simulation: runnable now. Emulator/device/actual provider, process death and Fold: separately evidenced. Release: strictly greater versionCode, canonical signature, non-destructive migration, and actual user device acceptance. No new installable APK becomes a release merely because a host workflow is green.

## Deliberate JSON/Archive unsigned packaging (Unified Workspaces)

The JSON acceptance workflow `.github/workflows/json-explorer-unsigned.yml` runs **only when manually dispatched**, not on ordinary push/checkpoint updates. It retains read-only GitHub permissions, exact source checkout, compile, scoped tests, per-branch concurrency cancellation and short artifact retention; unsigned artifacts are labeled with the **exact workflow commit SHA**, not an obsolete code32 release identifier. Do not dispatch merely to obtain a green badge or to deliver an unfinished APK. The separate review-ready pull-request host-regression workflow retains its current behavior; `skipped` is never `passed`.

The **single current integration and batch handoff** is [the authoritative Unified Workspaces queue](handoffs/ACTIVE_UNIFIED_WORKSPACES_QUEUE.md). Its gates override stale `STATUS.md`, old Professional M0–M7 auto-resume text, or PR #37's old `CURRENT_SOURCE.json` proposal for current status. No duplicate status authority. The approved cross-installation *private-state export/import* exclusion remains intact; local history/document safety is still mandatory. Actual hosted runs remain optional only when a concrete independent verification need justifies them; no signing keys or private files are accepted in CI.
