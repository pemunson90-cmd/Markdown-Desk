# Operation lifetime and terminal recovery checks

Run `bash verification/history_operations/run.sh` with JDK17. Optional selectors:
`coordinator`, `terminal`, `prefix`, `restart`. The runner compiles production Java
with `--release 11`; Android minSdk26 compatibility also needs the integration APK
gate. New production calls use API26-or-earlier Java APIs; no Java9 convenience
factory, platform InputStream.transferTo invocation, runtime provider or new
dependency is introduced. TrackedInput supplies its own bounded transferTo loop,
keeping delegate reads and external output callbacks inside the whole-work guard.

The initial production RED showed capability-free `HistoryPins.close` releasing a
pin. A later mixed-journal RED showed active-BEGIN reconciliation bypassing an
older CLOSED pair's live resource. Both now exercise mandatory terminal lifetime
authority. Missing-class/API compiler failures are setup evidence, not behavior RED.

The coordinator tests use real production handles, factories, streams and callback
wrappers. Latches exercise opening/in-flight/closing resources, callback suppression
and drain, epoch invalidation, failed ownership transfer, inline future listeners,
outer-monitor rejection and metadata-only signaling overlap. A capability is issued
only by the shared coordinator; fixtures never supply zero-work booleans. Fixture
waits are bounded test observers, not blocking production APIs.

Prefix tests cover both canonical outcomes at every length 0..126, original manifest
binding for RELEASED, exact suffix bytes and inode retention, shared reservation
conservation and callback mutation rejection. Ordinary open/reconcile never promotes
a CLOSE.stage. Explicit `HistoryPins.forRecovery` validates a cold retained journal
without acknowledging it or enabling writers; `freezeForTerminal` obtains actual
frozen operation authority before `recoverClose` performs any append or promotion.
The no-signaling closed-audit fast path can operate under an existing history lease;
nonzero work requires asynchronous preflight outside the caller's monitor.

The Python parent sends actual SIGKILL at append/force, read-only file sync, rename
and directory sync boundaries, then reopens each fixture twice in separate JVMs.
RLIMIT_FSIZE produces real partially appended suffixes; these remain canonical
prefixes with conserved credit and are recovered explicitly. Inventory comparison
checks every file's bytes/inode and rejects extra files or deletions. This is host
process-crash evidence, not sudden-power-loss or Android filesystem evidence.

Historical committed CLOSED pairs reconcile without their original manifest or
retired native sources; adoption permanently forbids consumer resumption. Receipts
mean only pin release. Partial BEGIN, noncanonical/conflicting evidence, uncertain
factory/close ownership and damaged bound sources can remain blocked indefinitely.
There is no quarantine, journal compaction, extra control allowance, provider caller,
export-success claim or general scheduler-deadlock guarantee. Runtime adapters must
independently enforce raw delegate ownership and avoid blocking shared executors.


Reviewed source checkpoint: `cf0bf818ef551e97ca6bc6a98120475fd343296f`.
Independent reviewer:24 production probes PASS. Focused coordinator59, terminal5,
prefix1658 assertions PASS. The full earlier operation run passed48 actual
SIGKILL/OS-limit/manifest fixtures with96 fresh reopens; subsequent changes were the
whole-transfer and in-flight scheduling guards, each reproduced RED then GREEN.
The integration owner runs the combined full host and unsigned-build gate once.
