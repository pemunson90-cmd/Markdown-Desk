# ADR-002: Shared capacity, validated history graph, then journaled retention

- Date: 2026-10-14. Status: implementation contract; source review only, no runtime acceptance.
- Scope: Task3 of [history plan](../superpowers/plans/2026-10-14-history-retention.md), following [ADR-001](ADR-001-safe-save-boundary.md).
- Preserve mandatory SaveCoordinator protection, v1 records, 8MiB documents, min26, version/signing and migration boundaries. Optional UI and physical M0/M1 release gates remain pending.

## Source constraints and locking

Before Task3A1, ProtectionStore had no inventory/deletion API and constructor-final quota. Task3A1 adds read-only inventory; constructor quotas remain final and deletion is still unavailable. Catalog calls store while holding its monitor; Index holds its monitor while calling store, identity and catalog. Adding an outer store lock alone deadlocks against these existing paths. Index.Version.protectedFromPruning is always true; list results grant no deletion permission.

Task3A1 introduces package-private HistoryStorage rooted at the trusted canonical app getFilesDir() parent (explicit temp parent in host tests). Validate distinct direct sibling roots, ancestor symlinks, canonical equality before graph construction. Reject mixed parents and aliased roots. Persistent role/policy registration is planned for Task3A2. Private multi-process writers are unsupported; do not imply a JVM monitor excludes another process.

Replace store/catalog/HistoryMetadataIO per-root monitors with the SAME parent monitor, including constructors, identity and index operations. Nested calls are reentrant; no private monitor may precede it. Task3A1 preserves existing per-handle constructor quotas and per-root entry/byte limits. Task3A2 will preserve policy/reservations in a registry keyed by canonical root: opening another handle cannot reset policy/reservations. Constructor quotas initialize an unconfigured store only; conflicting later configuration fails rather than silently replacing it.

Inventory, existing stage creation/commit, naming and indexing run under this monitor in Task3A1. Shared admission, policy changes, operation pins and pruning will use it in later slices. Admission plus publication is one operation; publication becomes successful only after file sync, move, directory sync and fresh validation. A policy change linearizes under the same monitor. Persist selected policy with bounded validated metadata before acknowledging it; a failed policy commit preserves the previous effective policy and its retained stage blocks optional mutation.

## Task3A1: implemented read-only graph; Task3A2: planned shared admission

Ownership: new HistoryStorage.java, HistoryPolicy.java, HistoryGraph.java and verification/history_retention/{HistoryGraphCheck.java,run.sh}; narrow edits to ProtectionStore, HistoryCatalog, HistoryMetadataIO, HistoryIdentity and HistoryIndex. No Activity, coordinator, diff or archive-format changes; no unlink code in this slice.

Task3A1 APIs: immutable HistoryPolicy choices (not enforcement), store.inspect():Inventory and operation-scoped validated source callback; HistoryGraph.scan():Graph. Task3A2 will add shared capacity admission and explicit durable quota configuration; store.setQuotaBytes(long) is not implemented. Add package-private metadata inventory/read views and index ownership view exposing record ID, history ID, transaction ID, sequence and exact snapshot metadata. Keep filesystem paths internal; return immutable descriptors, never payload arrays.

Inventory records root role, generated filename/ID, type, actual file length, SHA-256 of the COMPLETE serialized envelope, validated reference fields, reservations and diagnostics. Payload hash alone is insufficient. Stream bounded reads with nofollow and before/after file identity checks. Unknown/corrupt/nonregular files or retained stages produce diagnostics and pruneBlocked=true; inspection must not manufacture a complete valid graph from a partial scan.

The implemented graph includes store records, catalog pins, identity routing/provenance and index ownership. Future operation pins and maintenance state must join it before export or deletion. Its fingerprint hashes deterministic sorted descriptors/edges plus effective policy and reservations. An operation-scoped validated-snapshot map avoids Catalog.all() rehashing an 8MiB source for every duplicate checkpoint. Only freshly validated immutable metadata is reused inside the common lock; discard the map on operation exit or mutation. No long-lived validity cache or retained payloads.

Task3A2 planned admission contract (not implemented by the read-only graph): policy defaults remain 64MiB/20 successful changes; quota choices 64/128/256/512MiB/1GiB and counts 0/5/10/20/50. Zero disables optional retention only. Lowering quota records over-quota state and never deletes data. All registered roots must be known before admitting shared-quota writes; lazily adding a root cannot omit pre-existing disk usage.

Use checked long arithmetic: chargedBytes = actual serialized bytes across protection/catalog/identity/index/policy and every stage + outstanding completion bytes. Fresh writes require chargedBytes + peak additional bytes/reservations <= quota and each affected root's occupied+reserved slots within its existing cap. Preserve metadata 8MiB/4096-entry limits and store 65536 slots. Read/report over-quota inventories without comparing intermediate sums against the reduced quota. Do not mistake logical admission for physical free space or successful sync.

Completion converts an EXACT validated preparation's prior byte/slot reservation; it is permitted below the reduced quota only when net charged growth is zero and all integrity/physical durability checks pass. New snapshots, preparations and metadata receive no exemption. Replace current quota-limited unresolvedReservation accumulation with quota-independent validated accounting before implementing setQuotaBytes.

Prevent repeated failed completions from spending that reservation repeatedly: use a new deterministic transaction-bound .completion-stage, at most one per preparation, without rewriting legacy files. Its actual bytes plus unfilled reservation equal the exact completion envelope length; its occupied slot replaces the reserved slot. Reject oversized/foreign stages. A fully matching stage may be synced/moved/validated by explicit complete retry; partial or changed stages remain and block retry. Never overwrite or automatically delete them. Legacy anonymous .stage files remain fully charged and protected, never inferred as spendable credit. Gate the reduced-quota completion exemption on this tested contract.

## Second slice: pure planner

Add HistoryPruner.planAutomatic(requiredBytes,requiredEntries) and planSelected(checkpointIds), returning immutable DeletePlan or explicit blocked reasons. No execute/resume yet. Report requested capacity, eligible capacity and protected capacity; insufficient capacity cannot authorize partial unsafe selection.

Only freshly validated INDEXED_VERIFIED complete groups with unambiguous same-history ownership qualify; unindexed completions remain protected. Group closure includes completion, preparation, exclusive original/attempted snapshots, ownership .index and any explicitly selected checkpoint records. Shared snapshots or additional incoming references make the group ineligible in this first executor. Identity/provenance records are never pruned.

Protect unresolved transactions, unowned/orphan snapshots, every stage, named groups, explicit keep/export/restore pins and sole local recoverable groups. Automatic retention always keeps the newest indexed pair per history. The configured count is a desired maximum subject to quota, not a protected minimum: count-only maintenance retains that many recent groups (at least the newest pair), while capacity pressure may reclaim older otherwise-eligible automatic groups within that count. Oldest eligible persisted sequences go first. Timestamps and equal content do not establish identity/order. Unknown/corrupt/dangling inputs block all pruning; never auto-clean them.

Explicit selection can remove a named group only when ALL its checkpoint pins are selected and another fully validated local group of the SAME history survives. Export receipts are not local survivors. In this first slice even explicit selection cannot delete the newest indexed group per history: it is the fixed anchor. This also retains the global maximum index sequence, preventing current max(active)+1 allocation from reusing sequences after deletion. Removing this restriction requires a separately durable monotonic high-water protocol first.

Bind plans to full graph fingerprint, exact member envelope lengths/hashes, reference closure, selected labels, anchor IDs/hashes/ownership/routing and policy. A changed pin, survivor, identity, source, sequence or policy invalidates the preview. Product confirmation authorizes only that exact selection; it does not grant generic deletion or overwrite permission.

## Third slice: bounded journal and executor, separately reviewed

Only after planner review add execute(DeletePlan), resume(), journal codec and tombstone-aware readers. Maintenance has a separately accounted 1MiB/256-entry allowance for small intents/stages only; it cannot fund payloads or ordinary metadata. Allow one active prune intent, at most 256 members/64KiB including proof. Reject larger plans for explicit replanning into bounded operations. ENOSPC before durable intent means zero unlinks.

Under the common lock, rescan and compare the exact preview; validate every member and survivor; commit a versioned checksummed intent by stage sync, atomic move and directory sync. The intent contains member root roles/UUIDs/types/lengths/envelope hashes, removed reference closure, immutable identity/ownership proof and surviving anchor references. No arbitrary paths or duplicate members. Only this durable validated intent linearizes deletion authorization.

Install its logical tombstone before physical unlink. Store transaction/snapshot/reservation scans, catalog validation and Index.readAll must recognize EXACT intent-covered members as one logically removed group, including its ownership .index and selected checkpoints. They must still validate every surviving edge and anchor. Missing group members explained by a valid intent must not create fictitious unresolved transactions or disable unrelated recovery. Outside-intent missing/changed references remain errors.

Replay first validates the complete intent, each still-present member's exact length/hash, anchor bytes and unaffected references before ANY unlink. Absent members are accepted only as members of that committed intent. Store a second canonical fingerprint excluding the entire intended deletion closure, then compare that unaffected graph during replay; the original full fingerprint naturally differs after partial deletion. Reject conflicting/corrupt/unknown journal state without mutation. Hold new writes/pins/prunes until replay completes; bounded reads may expose validated survivors.

Unlink selected checkpoints and ownership index first, then completion, preparation, and exclusively owned snapshots, syncing each affected directory; correctness relies on the tombstone, not this order. After verifying every listed member absent, sync ALL affected directories, unlink the intent and sync the journal directory. Failure retains intent when present and stops; replay is idempotent, never adds members, never frees unrelated reservations. Journal stages are preserved and block pruning, not guessed into committed authorization.

## Required evidence and boundaries

Task3A1 evidence covers shared locking, validated inventories, bounded operation caches, fingerprints, corrupt/staged inputs and preserved per-handle constructor quotas. Task3A2 must first demonstrate RED, then GREEN against actual production classes: sibling/root validation; two-handle quota persistence; barrier-driven name/index/save/scan contention without deadlock; metadata/stage combined accounting; lowering quota preserves bytes; pre-existing completion byte/slot conversion; failed completion retry cannot grow beyond its reservation; no physical-sync bypass; overflow and per-root caps. Verify v1 bytes unchanged after reopen.

Graph/planner tests cover every protected root, cross-history survivor rejection, duplicate checkpoint validation work bounded by unique snapshots, unindexed protection, shared snapshot rejection, exact references/fingerprint changes, newest explicit deletion rejected, monotonic next index sequence after allowed deletion, capacity shortfall and corrupt graphs yielding zero mutation. Use disk-generated near-limit fixtures at constrained heap; inspect actual reads/hashes, not source strings.

Executor tests inject failure and SIGKILL real child JVMs before/after intent file sync/move/directory sync, every checkpoint/index/completion/preparation/snapshot unlink, affected directory sync, intent unlink and journal sync. Reopen and replay twice; verify exact surviving original/attempt bytes, absent intended groups, valid index/catalog reads and reservations, no fabricated pending transaction, no additional deletion. Include changed survivor/member, truncated intent, unknown stage, full maintenance allowance and physical write failure.

Run targeted retention/store/coordinator/catalog suites after affected changes, then one integration host/build gate under [CI policy](../CI_AND_WORKFLOW_POLICY.md). Independent critical persistence review precedes executor integration. These requirements are planned, not reported passes; physical Android/SAF/process-death/Fold/upgrade acceptance remains separate.

Task4 later needs a pinned, repeatable bounded serialized-record stream adapter for PrivateArchive.SourceEntry. The current codec alone does not validate history source graphs or authorize provider writes. Keep export destination protection and streaming access in that separate reviewed slice; no generic unprotected overwrite escape.

Execution split: implement shared monitor/inventory/read-only graph/cache first, retaining existing constructor quotas and commit wire protocol. Implement shared admission, mutable quota and reservation-bound completion staging only after that slice is reviewed. The framing codec currently has no ownership INDEX wire type; define its serialization explicitly before the export/import adapter claims full history round-trip.


2026-10-14 completion prerequisite checkpoint (`809d41b`): deterministic
transaction-bound stages replace anonymous completion stages for new completion
attempts. Exact prefix bytes occupy only the validated preparation reservation;
partial or altered stages are retained and block retry. Exact full stages can
retry sync/move/validation without new stage allocation. v1 committed wire bytes
remain unchanged. Host tests and six SIGKILL retry boundaries pass; persistent
shared capacity, dynamic quota and reduced-quota exemptions remain separate.


2026-10-14 planning clarification: `planAutomatic(requiredBytes,requiredEntries)`
requests minimum capacity to reclaim, with entries meaning protection-store slots.
The caller computes the deficit from fresh combined charged usage, peak additional
write demand and per-root limits; the planner does not guess provider/save demand.
Plans report freed entries per root. Zero/zero is count-only maintenance.
The desired-count/quota priority follows ADR-001's "subject to global quota":
a hard minimum of20 would make repeated8MiB saves impossible at the default64MiB.
Named, unresolved, orphan, shared and newest/sole-local groups remain protected.
A planner result alone never authorizes an unlink or proves repeated saves work;
executor, admission integration and interruption tests remain required.


Pure-planner selection ruling (2026-10-14): positive capacity deficits select
only globally oldest eligible groups until both requested minima are satisfied;
count trimming does not precede that pressure selection. Zero/zero maintenance
selects oldest count-excess groups in a bounded prefix and reports
`moreCountMaintenance`. Replan from a fresh graph after execution. An explicit
selection or positive deficit exceeding one bounded proof returns zero members
and a limit/shortfall reason. The preview framing budget is not a journal wire
format or authorization to delete; execution must enforce its exact own bounds.
