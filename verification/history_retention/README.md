# Read-only retention foundation checks

Run with JDK17: `bash verification/history_retention/run.sh`.

This compiles and exercises the actual `ProtectionStore`, catalog, identity, index,
shared private-parent monitor and `HistoryGraph`; the graph has no write,
admission, planner, execution or unlink API. `HistoryPolicy` is an optional
fingerprinted descriptor only. Native constructor quotas, completion reservation
calculation, anonymous stages and v1 serialized formats remain unchanged.

The regular fixtures cover distinct direct sibling roles, root/symlink aliases,
mixed-parent dependencies, multiple store handles with different legacy quotas,
exact whole-envelope SHA-256 and actual serialized byte counts across all four
roots, unresolved completion bytes/slots, identities/FORK origins, persisted
ownership sequences, pins, orphans and unindexed sources. Every validated source
remains protected; a complete graph grants no deletion permission.

A barrier at a real store directory sync holds a scan while actual naming,
index publication and native snapshot/preparation/completion operations through
a second handle contend. After release, all operations finish and a complete
rescan includes all writes. Native directory-sync callbacks also exercise
reentrant writes at the final index read and an empty catalog read: operation
contexts reject the resulting stale view. Foreign, expired and mutated contexts
are rejected. Duplicate checkpoints reuse a fresh unique-source inventory under
the parent monitor; directory-sync counts observe the actual production path.
No payload arrays are held by inventory or graph descriptors.

Corrupt and missing links, unknown and oversized files, symlinks and retained
stages produce sanitized diagnostics and `pruneBlocked=true`; scans preserve the
existing files. Oversized native metadata and oversized unknown files are not
hashed or materialized. `usageComplete` describes actual regular-file accounting;
when a scan is incomplete, its references and completion reservations may be
partial and its charged bytes are not a complete capacity proof. Directory or
budget limits explicitly mark incomplete usage. Fingerprints bind root roles,
exact sorted file descriptors/envelope hashes, references, constructor quota,
reservations and optional policy fields.

The separate `-Xmx128m` fixture retains 32MiB headroom while scanning a native
near-8MiB disk snapshot and duplicate names. It then adds 512 valid checkpoint
envelopes derived from a native writer's record and checks the default conservative
16MiB graph metadata budget returns an incomplete, blocked graph with a budget
diagnostic while reporting all actual regular-file usage. This is bounded host
heap evidence, not an Android memory or latency budget.

Private multi-process writers are unsupported. Combined accounting does not
implement shared quota admission. Dynamic quota/persisted policy, transaction-bound
completion stages, pruning plans/execution, UI integration, Android/provider/Fold,
power-loss and release acceptance remain separate work.
