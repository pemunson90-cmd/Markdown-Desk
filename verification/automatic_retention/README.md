# Opt-in automatic floor retention — phase2 backend only

Owned production changes are HistoryPruner.java and HistoryMaintenanceExecutor.java.
No Recovery/Intent codec, Controller, Activity, runtime scheduler or portable-state
source was changed. This packet alone does not enable a UI or finish M1 automatic
retention. Durable consent was introduced by phase1 commit20e5414.

`planAutomaticFloorBatch()` is a pure one-group preview above each document's floor,
with zero fabricated demand. NO_WORK is a null-plan result, distinct from blocked
or incomplete graph. Existing planAutomatic(0,0) remains its original empty preview;
pressure and selected planner behavior stay intact. Names, latest floor/anchor,
external/live pins, shared snapshots, unindexed/orphan records and pending-save
reservations keep their original protections. The pure planner does not grant
consent: installed policy authority is checked by the executor.

`executeAutomaticFloor(installed, Cancellation)` requires the actual current bundle,
fresh policy/control evidence and explicit automaticEnabled/count>0. Disabled returns
without intent/unlink. It builds its plan internally; no caller preview is deletion
authority. AutomaticResult has immutable status/capacity/deletedBytes/deletedGroups.
Statuses are DISABLED, NO_WORK, CANCELLED_BEFORE_PUBLICATION and COMPLETED; blocked,
stale, uncertain or partial IO throws IOException and retains journal evidence.
The inert cancellation flag is checked before planning and before first stage
creation. Once publication starts, finish one journal attempt; do not roll back a
stage or guess whether a throwing move committed. Post-publication cancellation
cannot abandon the committed closure.

Private exact-intent Removal capabilities gate one shared remove/validate/retire
engine. Selected authorization and committed-selected replay guards are unchanged;
both replay entry points additionally reject the other mode's in-memory Retirement.
Floor capabilities require one unnamed 5-member group, no selected names/pressure
or demand, and the exact consent-bearing policy hash. Fresh complete consent/control
checks occur before physical member unlinks and intent unlink/retirement, including
retries. Existing actual fileKey comparisons, envelope hashes, directory identities,
pin/pending proofs and shared control reservations remain in use. No string/hash
substitute for fileKey equality was introduced.

`replayAutomaticFloor()` handles only an explicitly committed floor intent matching
current enabled policy, reconciles closed audits under that exact intent, installs
only the existing restricted read-only recovery view, and completes the same exact
closure. No ordinary mutable capacity is exposed until terminal reconciliation.
Stages/unknown/legacy-consent/disabled/selected/pressure intents fail closed. No
intent and no in-memory retained view means ordinary HistoryCapacity.openLocked,
not a new plan or inferred delete. This also handles fresh process restart after
intent unlink: data roots were synced before intent unlink. Present intent replays
exact remaining closure; an incomplete surviving graph still blocks.

Run with installed JDK17 on PATH:

```
bash verification/automatic_retention/run.sh       # finite + memory + real SIGKILL
bash verification/automatic_retention/run.sh java  # finite + memory only
bash verification/history_executor/run.sh
bash verification/history_retention/run.sh
```

TDD initial RED: compilation failed on the absent planner/executor APIs; a fixture
transaction-lookup typo was corrected before GREEN. GREEN finite suite261 checks,
maximum 8MiB-document victim pair plus 32MiB live-pressure 2 checks (128MiB heap),
automatic 38 actual SIGKILL boundaries / 76 fresh JVM reopens PASS. The restart runner
checks stage file sync/move/directory publication, all 5 member before/after unlinks
and directory syncs, all 4 data-root retirement syncs, intent unlink and final control
sync. Two independent reopens per boundary compare exact survivor SHA256+inodes,
no surplus paths, named/newest/pending contents and completion reservation totals.
Each child wait is bounded; tests never depend on an infinite provider or GC.
Finite tests also inject six publication IO failures, each member before/after
failure, retained mode-crossing retirement failure, malformed stages, stale authority,
zero/legacy/explicit disabled policy, full 256-entry control allowance, external/live
pins, two-history floors, unindexed/orphan/shared records, cancellation before/after
cutoff, nested execute/replay, and byte-identical victim/survivor/root replacement.
A direct invalid-policy replacement fails before native unlink. No-intent replay
is independently checked to do no inferred cleanup. Existing selected executor 747,
selected memory 22, retention planner 94 and planner memory 6 checks PASS; selected
SIGKILL/OS-limit 44 fixtures / 88 fresh JVM reopens PASS.

Resource delta versus selected baseline: same bounded raw/graph/Stable/recovery
scanners and same engine, no additional whole-graph/map retention. Added flag,
result and private capability objects hold fixed fields/references; a floor plan is
one group / 5 members versus selected <=256 members. Floor has additional transient
bounded control inspections for consent. A passed 128MiB fixture is not a whole-job
heap ceiling/latency promise. The existing shared bundle monitor remains held during
scan/deletion; future worker queue isolation will not make saves lock-free. No new
funding framework, background thread, timeout, scheduling or automatic-pressure
execution is introduced here.

No APK, device, process-death storage-reordering or complete lost-work acceptance
is claimed by host SIGKILL tests. Independent review is required before commit.
