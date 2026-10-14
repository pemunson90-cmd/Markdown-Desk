# Explicit journaled selected retention

Run with the installed JDK17:

```bash
export PATH=/workspace/.command-library-env/jdk-17.0.16+8/bin:$PATH
bash verification/history_executor/run.sh
```

This runner compiles actual production source with `--release 11`. It uses only
synthetic private history fixtures and never calls Activity, SAF or providers.
`HistoryMaintenanceExecutor` has no production runtime caller. It accepts only
an exact authorized SELECTED plan under the current installed, acknowledged
capacity policy. Both automatic modes, including retained automatic intents,
remain rejected pending typed per-root demand proofs.

The executor publishes the existing bounded intent through exclusive stage,
file sync, same-inode readback, atomic move and control directory sync. It then
installs the validated survivor projection. Every removal has a callback-free
full proof immediately before direct `Files.delete`; no injectable delete
adapter can replace a victim after validation. Checkpoints, ownership indexes,
completions, preparations and snapshots are removed in that order. Physical
accounting changes only when the corresponding files disappear.

A shared exact retirement capability blocks public writers and authority opens
after all closure members are absent, through intent removal and final directory
sync. A same-process retry or second handle uses that capability to validate
present-intent or no-intent terminal state. It refreshes retained CLOSED pin
audits under the existing lease, validates the complete physical survivor ledger,
and installs the prepared capacity together with clearing only the matching
view/capability. It never clears a view to make recursive ordinary reconciliation
succeed. Closed audit records retain their original bytes even when their old
native input is legitimately retired.

`ExecutorCheck` covers whole groups, multiple selected names, newest anchors,
stale proofs, changed selection/profile, superseded authority, policy A→B→A,
active pins, pre-enrollment mismatched bootstrap handles, full shared byte/slot allowance, large intents, unknown records and zero, partial and complete
retained intent stages, publication faults, actual directory-permission failure,
shared terminal retries, nested writers, same-byte file/root replacement, and
last data/control callback mutations. The memory fixture uses near-limit snapshot
pairs with 32MiB held live under `-Xmx128m`; snapshot payloads are streamed.

`run_restart.py` starts actual fresh JVMs and sends SIGKILL after a child reports a
named boundary. BEFORE unlink events run **before** final validation; AFTER events
run **after** actual direct unlink. File/move/directory events surround actual host
operations. It covers publication, each member kind, affected-directory syncs,
all four terminal data-directory syncs, intent unlink and final control sync.
Each fixture reopens twice in independent JVMs and compares exact surviving paths,
SHA256, inode identity and completion reservations. An actual OS `RLIMIT_FSIZE`
publication failure leaves retained stage evidence and zero native removals.
Changed member/survivor/policy and unknown/conflicting-stage fixtures reject on
both fresh reopens without further mutation.

All retained intent stages remain blocked, including complete canonical stages.
There is no repair, expiry, automatic pruning or generic-path deletion API. Host
SIGKILL and filesystem evidence do not establish sudden-power-loss, Android
app-UID, Fold, SAF, upgrade or release acceptance. The parent integration owns
central runner registration and the full host/two-unsigned-build gate.

Observed scoped run: **747 behavioral checks, 22 constrained-heap checks,
44 crash/OS-limit/corruption fixtures and 88 fresh JVM reopens passed**.
Dependency pin, capacity, maintenance, pure planner and retention graph runners
also passed. Independent persistence review approved exact source
`c36b093f2b2368af2a61cbc60bf81f56b0236d8a`, with 25 separate boundary probes passing
and the bootstrap defect reproduced before and verified after the fix. The
parent integration gate remains; this evidence does not enable a runtime caller.

Independent review exposed a pre-enrollment alternate bootstrap-hint handle that
could publish an intent before late rejection. A second targeted RED showed the
same late rejection after intent removal during retirement retry. The executor
now validates the executing handle against the registered immutable bootstrap
before publication admission or replay, including retained-capability retries.
Both fixtures assert that rejection leaves the entire file inventory unchanged.
