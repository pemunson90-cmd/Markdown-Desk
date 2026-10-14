# Read-only retention foundation checks

Run with JDK17: `bash verification/history_retention/run.sh`.

This compiles and exercises the actual `ProtectionStore`, catalog, identity, index,
shared private-parent monitor and `HistoryGraph`; the graph has no write,
admission, planner, execution or unlink API. `HistoryPolicy` supports explicit shared-capacity enrollment in the separate
`verification/history_quota` suite. Constructor-only stores preserve their native
quota behavior. New completions use transaction-bound stages; existing v1
serialized snapshot/preparation/completion records remain unchanged.

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
implement shared quota admission. Dynamic quota/persisted policy and transaction-bound completion stages now have
separate production tests. The pure planner below previews eligible closures.
Execution, UI integration, Android/provider/Fold, power-loss and release
acceptance remain separate work.


## Pure planner checks

Run `bash verification/history_retention/run_planner.sh`:427native-fixture
assertions and19near-limit memory assertions. Three actual8MiB original/edit
pairs at128MiB heap with32MiB retained headroom can plan oldest-pair reclamation
under desired-count20. No file is deleted or written by a planner call.

Count-only maintenance returns a bounded oldest count-excess batch and
`moreCountMaintenance`; after execution the caller must rescan/replan.
Capacity requests mean minimum bytes and protection-store slots to reclaim,
not incoming write size. They select globally oldest eligible groups until
both minima are met; an insufficient bounded plan grants zero members.
Named automatic groups, unresolved/unindexed/orphan/shared versions, every
newest same-history pair and sole-local recovery remain protected. Explicit
selection requires all checkpoint pins and a surviving newest same-history pair.

Plans bind full envelope lengths/hashes, source metadata, persisted ownership,
routing, effective policy and fresh graph fingerprint. `isCurrent` rejects stale
previews; its true result is not unlink authorization. The canonical preview
proof is bounded at64KiB/256members with128bytes reserved for future framing;
this is not an implemented journal format. Ledger policy changes on the same
Graph are tested rather than using only a different-object comparison.

Durable operation pins, maintenance registration, journaled execution and
logical tombstone projections must precede runtime deletion. A safe plan alone
neither frees quota nor proves normal repeated saves are unblocked.
