# Shared history capacity verification

Run `PATH="$JAVA_HOME/bin:$PATH" bash verification/history_quota/run.sh`.
The runner exercises actual production stores, policy ledger and graph at128MiB
host heap. Eighteen cases/91checks cover preexisting four-root usage, byte/slot
reservations, multiple handles, policy failures/retry/recovery, lowered quota,
metadata caps, graph bundle/proof invalidation and hostile ledger records.
Seventeen real SIGKILL boundaries cover six policy-update and eleven initial
registration points; each is inspected and explicitly recovered twice in fresh
JVMs. These are host-process observations, not Android/power-loss acceptance.

`HistoryPolicy.configure(store,catalog,identity,index)` explicitly enrolls the
whole sibling bundle. Constructor-only stores preserve their legacy quota and
wire behavior until enrollment. Quota choices are64/128/256/512MiB/1GiB;
automatic-count choices are0/5/10/20/50. Count0 disables optional automatic
retention; mandatory save protection remains required. `HistoryPolicy.current`
and `inspect` expose effective policy and actual/reserved/charged bytes, over-quota
and mutation-blocked state. The quota measures logical serialized history space,
not device free space or successful physical durability.

Policy changes persist bounded checksummed chained records before acknowledgment.
The ledger itself and every retained stage count toward charged history usage.
Administrative policy writes have a separate64KiB/256-record bound so an
over-quota store can change policy; this allowance cannot fund document payloads.
Lowering quota never deletes or rewrites records. Only exact validated completion
reservation conversion may proceed while over quota or pending policy recovery.

Prepublication failure retains the previous effective policy and stage. An
explicit matching request may retry an exact stage, including after reopening;
partial/changed stages remain intact and block ordinary mutation. A publication
with uncertain directory durability requires explicit `recoverPolicy`; it is
not silently rolled back or retried. Fresh processes require policy recovery
before normal mutation. Initial registration re-syncs both parent and ledger
directories even when a previous failed attempt already created the directory.

The ledger has no compaction yet. Bound exhaustion rejects policy changes rather
than deleting records. Private multiprocess writers, runtime settings, automatic
pruning/execution, export/import and device/Fold/upgrade acceptance are separate.
