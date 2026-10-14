# CI and execution policy — Markdown Desk Professional

This document defines **when to run tests** and **what their results establish**, not permission to waive any release or document-safety requirement. See [AGENTS.md](../AGENTS.md), [release gates](RELEASE_GATES.md), [ADR-001](decisions/ADR-001-safe-save-boundary.md), and [live status](STATUS.md).

## Work in Codex first

Use the existing Codex JDK/Android SDK workspace for local builds, targeted tests, critical reviews, commits and Git operations. Run the smallest relevant tests during editing; run the full host suite and unsigned-build/rebuild checks at integration checkpoints. Avoid GitHub API calls or hosted workflows for work that the local checkout handles. Trigger independent hosted verification deliberately—at review-ready PR boundaries, manual requests for a real portability concern, or an enforced repository rule.

**Draft PRs:** the GitHub Actions workflow does not run its hosted job automatically while a PR is draft. Developers must not interpret a skipped CI job as passing tests. Local tests and their logs should remain current. When the PR becomes **ready for review**, the hosted job runs; further ready-for-review branch changes trigger another run. Manual `workflow_dispatch` remains available for justified independent verification. Do not manually dispatch repeatedly to reproduce a known unchanged failure.

## Separate the two safety claims

### A. Required application-level recoverability (must be GREEN before merge/release)

Application behavior must **durably protect exact original and attempted edit bytes before opening a destructive provider writer**, fail closed when protection cannot commit, retain both versions through partial/zero-byte provider failures, and expose explicit restore to a **new copy**. The source revision/identity, Save As behavior, and recoveries must be correct.

The current production-path host fixtures in `bash verification/run_host_checks.sh` include the real `ProtectionStore`, Android adapter double, save coordinator, constrained-memory probe, save lifecycle, and recovery/mode tests. These are the **required hosted host checks**, with genuine assertions and a nonzero failure exit. They are evidence within their stated host/simulation boundaries, not Android device acceptance. Add further **production-path** tests as implementation grows. A green host run alone never establishes power-loss, SAF-provider, Fold, TalkBack, or upgrade readiness.

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
