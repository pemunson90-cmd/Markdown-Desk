# Bounded completion reservation implementation plan

> Execute natively in the assigned isolated worktree; independent persistence review before integration.

Goal: prevent failed completion attempts from consuming more than the preparation's reserved bytes and slot, while preserving all v1 native envelope bytes.

Architecture: validate committed preparation and its source snapshots, derive exact COMPLETE bytes, and prove only the transaction's deterministic `<id>.completion-stage` against those bytes. An absent stage reserves all bytes and one slot; an exact prefix reserves only its unwritten suffix and no slot; a full match reserves nothing. Invalid stage proofs reject admission and report diagnostics without credit. Every recognized stage blocks pruning. No quota configuration or reduced-quota exemption.

Files: ProtectionStore.java (proof, accounting, deterministic completion and local recursive target guard); HistoryStorage.java (only narrow stage filename classification); protection_store/ProtectionStoreCheck.java and focused history_retention/HistoryGraphCheck.java fixtures.

Constraints: min26, v1 envelope bytes unchanged, immutable handle quotas retained, anonymous snapshot/preparation/legacy stages unchanged and fully charged. No deletion, append to partial stage, automatic cleanup, Activity/coordinator/journal/pruner work or multi-process safety claim.

- [x] Add failing production tests for deterministic repeated failures, full retry preserving identity and bytes, prefix/zero/altered/oversized/foreign/orphan stages, duplicate completion/stage, corruption, recursive same-target callback, per-handle low quota and conserved bytes/slots. Run focused store command and observe expected RED.
- [x] Implement shared stage proof and checked quota-independent reservation accumulation, deterministic completion with read/sync/move/dirsync/readback and same-target guard. Re-run store command including physical write-limit mode.
- [x] Add graph fixtures for stage diagnostic, fingerprint and exact conservative reservation proof; run graph fixture including bounded heap.
- [x] Run targeted store/graph/coordinator/lifecycle/restart commands once affected inputs stabilize. Root owns new restart boundaries; report those as not run here until integrated.
- [x] Inspect diff, obtain independent actual-source review, fix findings, verify affected tests and commit bounded changes. Report exact evidence and remaining limitations.

Verification checkpoint (JDK17, host only): store31 cases/272 assertions, actual snapshot write-limit3 assertions, completion write-limit0 and4096 each11 assertions; graph94 checks plus6 bounded-heap checks; coordinator35 cases/157 assertions; lifecycle `--safety`34 cases/233 assertions; default16 actual SIGKILL boundaries and3 transaction-bound retry boundaries, each retry reopened twice. Commands were the corresponding `verification/{protection_store,history_retention,save_coordinator,save_lifecycle,protection_restart}/run.sh`, with `--safety` or `--completion-retry` where noted. No full suite, build, hosted CI, Android/app-UID/power-loss or device acceptance claimed.

Independent source review reproduced a new-stage identity replacement during `syncFile` that could publish an unsynced replacement. Added a failing native test, then the new-stage identity check across sync; targeted store/graph/lifecycle-safety/retry-SIGKILL passed afterward. Retained-stage changed-byte/replaced-identity checks, and successful/failed publication invalidating the operation-scoped source map, also pass. No remaining source blocker reported before commit.

An earlier capacity fixture run failed because workspace inodes were exhausted by accumulated disposable fixtures; no storage behavior was changed to bypass it. Root's per-run fixture cleanup was integrated, and the final native capacity/physical-write suite passed. Root owns integration documents and full committed-source gates. This branch cherry-picked root restart fixture5f9bb89 as037ffc4 and cleanup4bd135a as4483ca8; integration already has their originals.
