# ADR-002: Shared capacity, validated history graph, then journaled retention

- Date: 2026-10-14. Status: implementation contract; source review only, no runtime acceptance.
- Scope: Task3 of [history plan](../superpowers/plans/2026-10-14-history-retention.md), following [ADR-001](ADR-001-safe-save-boundary.md).
- Preserve mandatory SaveCoordinator protection, v1 records, 8MiB documents, min26, version/signing and migration boundaries. Optional UI and physical M0/M1 release gates remain pending.

## Source constraints and locking

Before Task3A1, ProtectionStore had no inventory/deletion API and constructor-final quota. Task3A1 adds read-only inventory; constructor quotas remain final and deletion is still unavailable. Catalog calls store while holding its monitor; Index holds its monitor while calling store, identity and catalog. Adding an outer store lock alone deadlocks against these existing paths. Index.Version.protectedFromPruning is always true; list results grant no deletion permission.

Task3A1 introduces package-private HistoryStorage rooted at the trusted canonical app getFilesDir() parent (explicit temp parent in host tests). Validate distinct direct sibling roots, ancestor symlinks, canonical equality before graph construction. Reject mixed parents and aliased roots. Persistent role/policy registration is planned for Task3A2. Private multi-process writers are unsupported; do not imply a JVM monitor excludes another process.

Replace store/catalog/HistoryMetadataIO per-root monitors with the SAME parent monitor, including constructors, identity and index operations. Nested calls are reentrant; no private monitor may precede it. Task3A1 preserves existing per-handle constructor quotas and per-root entry/byte limits. Task3A2 will preserve policy/reservations in a registry keyed by canonical root: opening another handle cannot reset policy/reservations. Constructor quotas initialize an unconfigured store only; conflicting later configuration fails rather than silently replacing it.

Inventory, existing stage creation/commit, naming and indexing run under this monitor in Task3A1. Shared admission, policy changes, operation pins and pruning will use it in later slices. Admission plus publication is one operation; publication becomes successful only after file sync, move, directory sync and fresh validation. A policy change linearizes under the same monitor. Persist selected policy with bounded validated metadata before acknowledging it. A pre-publication failure retains the previous committed policy and its stage blocks optional mutation. Rename followed by failed directory sync/readback is uncertain publication: preserve prior records, block mutations and require explicit durable reconciliation. Do not claim the old policy remains authoritative after restart or silently fall back to legacy admission.

### Future opt-in authority and availability

Registered startup distinguishes existing read-only handles from policy-ready
writers. Existing canonical role roots may be opened without creating directories
before explicit control reconciliation; constructors reject nested writes without
requiring effective policy, avoiding a constructor/open cycle. No new/unlisted
managed root may be created once control storage exists. The subsequent controller
validates registration, policy and a capacity ledger before enabling writers.

Capacity authority is separate from deletion proof: known retained anonymous or
optional metadata stages are fully charged and block pruning, but do not by
themselves make actual usage unknowable. Mandatory protection/admission uses
complete nofollow actual-file accounting and exact native completion reservations.
Invalid native references, unknown/nonregular physical entries, incomplete usage
or uncertain control authority still block. Optional metadata writers may reject
their own malformed state; optional indexing cannot retroactively negate a
verified save. No pruning is authorized by byte accounting alone.

## Task3A1: implemented read-only graph; Task3A2: planned shared admission

Ownership: new HistoryStorage.java, HistoryPolicy.java, HistoryGraph.java and verification/history_retention/{HistoryGraphCheck.java,run.sh}; narrow edits to ProtectionStore, HistoryCatalog, HistoryMetadataIO, HistoryIdentity and HistoryIndex. No Activity, coordinator, diff or archive-format changes; no unlink code in this slice.

Task3A1 APIs: immutable HistoryPolicy choices (not enforcement), store.inspect():Inventory and operation-scoped validated source callback; HistoryGraph.scan():Graph. Task3A2 will add shared capacity admission and explicit durable quota configuration; store.setQuotaBytes(long) is not implemented. Add package-private metadata inventory/read views and index ownership view exposing record ID, history ID, transaction ID, sequence and exact snapshot metadata. Keep filesystem paths internal; return immutable descriptors, never payload arrays.

Inventory records root role, generated filename/ID, type, actual file length, SHA-256 of the COMPLETE serialized envelope, validated reference fields, reservations and diagnostics. Payload hash alone is insufficient. Stream bounded reads with nofollow and before/after file identity checks. Unknown/corrupt/nonregular files or retained stages produce diagnostics and pruneBlocked=true; inspection must not manufacture a complete valid graph from a partial scan.

The implemented graph includes store records, catalog pins, identity routing/provenance and index ownership. Future operation pins and maintenance state must join it before export or deletion. Its fingerprint hashes deterministic sorted descriptors/edges plus effective policy and reservations. An operation-scoped validated-snapshot map avoids Catalog.all() rehashing an 8MiB source for every duplicate checkpoint. Only freshly validated immutable metadata is reused inside the common lock; discard the map on operation exit or mutation. No long-lived validity cache or retained payloads.

Task3A2 planned admission contract (not implemented by the read-only graph): policy defaults remain 64MiB/20 successful changes; quota choices 64/128/256/512MiB/1GiB and counts 0/5/10/20/50. Zero disables optional retention only. Lowering quota records over-quota state and never deletes data. All registered roots must be known before admitting shared-quota writes; lazily adding a root cannot omit pre-existing disk usage.

Use checked long arithmetic: dataChargedBytes = actual serialized bytes across protection/catalog/identity/index and every data-root stage + outstanding completion bytes. Enrollment/registration/policy/future maintenance are separately counted in the ONE shared control allowance below; they cannot fund data capacity. Fresh writes require chargedBytes + peak additional bytes/reservations <= quota and each affected root's occupied+reserved slots within its existing cap. Preserve metadata 8MiB/4096-entry limits and store 65536 slots. Read/report over-quota inventories without comparing intermediate sums against the reduced quota. Do not mistake logical admission for physical free space or successful sync.

Completion converts an EXACT validated preparation's prior byte/slot reservation; it is permitted below the reduced quota only when net charged growth is zero and all integrity/physical durability checks pass. New snapshots, preparations and metadata receive no exemption. Replace current quota-limited unresolvedReservation accumulation with quota-independent validated accounting before implementing setQuotaBytes.

Prevent repeated failed completions from spending that reservation repeatedly: use a new deterministic transaction-bound .completion-stage, at most one per preparation, without rewriting legacy files. Its actual bytes plus unfilled reservation equal the exact completion envelope length; its occupied slot replaces the reserved slot. Reject oversized/foreign stages. A fully matching stage may be synced/moved/validated by explicit complete retry; partial or changed stages remain and block retry. Never overwrite or automatically delete them. Legacy anonymous .stage files remain fully charged and protected, never inferred as spendable credit. Gate the reduced-quota completion exemption on this tested contract.

## Second slice: pure planner

Add HistoryPruner.planAutomatic(requiredBytes,requiredEntries) and planSelected(checkpointIds), returning immutable DeletePlan or explicit blocked reasons. No execute/resume yet. Report requested capacity, eligible capacity and protected capacity; insufficient capacity cannot authorize partial unsafe selection.

Only freshly validated INDEXED_VERIFIED complete groups with unambiguous same-history ownership qualify; unindexed completions remain protected. Group closure includes completion, preparation, exclusive original/attempted snapshots, ownership .index and any explicitly selected checkpoint records. Shared snapshots or additional incoming references make the group ineligible in this first executor. Identity/provenance records are never pruned.

Protect unresolved transactions, unowned/orphan snapshots, every stage, named groups, explicit keep/export/restore pins and sole local recoverable groups. Automatic retention keeps the newest indexed pair per history and the configured newest count; oldest eligible persisted sequences go first. Timestamps and equal content do not establish identity/order. Unknown/corrupt/dangling inputs block all pruning; never auto-clean them.

Explicit selection can remove a named group only when ALL its checkpoint pins are selected and another fully validated local group of the SAME history survives. Export receipts are not local survivors. In this first slice even explicit selection cannot delete the newest indexed group per history: it is the fixed anchor. This also retains the global maximum index sequence, preventing current max(active)+1 allocation from reusing sequences after deletion. Removing this restriction requires a separately durable monotonic high-water protocol first.

Bind plans to full graph fingerprint, exact member envelope lengths/hashes, reference closure, selected labels, anchor IDs/hashes/ownership/routing and policy. A changed pin, survivor, identity, source, sequence or policy invalidates the preview. Product confirmation authorizes only that exact selection; it does not grant generic deletion or overwrite permission.

### Future pressure mode; current preview remains unchanged

The current pure planner treats the configured count as a protected floor. At
64MiB that intentionally cannot reclaim one of roughly three paired8MiB groups
while count20 is selected. Before runtime automatic pruning, add a separately
tested pressure mode treating count20 as the desired upper target: retain up to20
when capacity permits, and under quota pressure remove eligible oldest indexed
groups down to the fixed newest same-history anchor. Count0 still disables
automatic retention. Named, pending, orphan, unindexed, shared and live-pinned
sources remain protected. Bind the mode and actual requested capacity into the
preview/intent proof; do not silently reinterpret existing floor previews.
Expose blocked capacity, quota increase and explicit review/export options. This
planned refinement grants no current deletion authority.

## Third slice: bounded journal and executor, separately reviewed

Only after planner review add execute(DeletePlan), resume(), journal codec and tombstone-aware readers. Registration/policy and later maintenance share one separately accounted 1MiB/256-entry control allowance for bounded administrative records/intents/stages only; it cannot fund payloads or ordinary metadata. Allow one active prune intent, at most 256 members/64KiB including proof. Reject larger plans for explicit replanning into bounded operations. ENOSPC before durable intent means zero unlinks.

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

Execution split: Task3A1 implements shared monitor/inventory/read-only graph/cache, retaining existing constructor quotas and commit wire protocol. The [shared-capacity plan](../superpowers/plans/2026-10-14-shared-history-capacity.md) defines subsequent opt-in registration, effective durable policy, all-writer admission and reconciliation. Its control allowance cannot fund document payloads or ordinary history metadata. The next prerequisite implements deterministic completion staging and quota-independent reservations while retaining those constructor quotas and denying an over-quota completion exemption. Implement shared admission, mutable quota and reservation-bound completion staging only after that slice is reviewed. The framing codec now assigns ownership INDEX the explicit wire ID9 under the bounded metadata cap, preserving prior IDs and framing version. Native ownership envelope framing is tested; typed graph reconstruction and full history round-trip remain pending export/import adapters.

Task4 framing IDs must be independent generated UUIDv4 values bound in the export
manifest to typed native identities. Preparation and completion share a native
transaction UUID but require distinct archive entry UUIDs. Persist mappings across
hash/write/retry passes; remap shared native identity separately on import. Export
validated configuration via typed preferences, never restore old control
registration/policy authority as effective local policy.

Independent completion checkpoint `809d41b` observed7cases/86assertions and six
before/after retry SIGKILL cases in its environment. Its test fixtures are retained
in reconciliation; the live producer uses reviewed `b511fee` with stronger fresh/
reused-stage inode checks and read-only retries. These are source checkpoints,
not shared-policy or physical-device acceptance.

## Reviewed opt-in authority checkpoint — 2026-10-14

`HistoryCapacity` now implements explicit registration/open/reconciliation and
all-writer shared admission (source reviewed at `1fa6d80`, tests at `7e474cb`).
A durable parent enrollment marker prevents fresh-process legacy fallback when
control storage is missing. Marker and marker stage count in the same1MiB/256
allowance as control records. Interrupted enrollment without complete binding
stays blocked; no reconstruction/default inference is performed. Constructors
open existing canonical roles read-only before reconciliation. Every public
writer validates its role/dependencies and rejects same-thread nested mutation
before callbacks. Policy publication binds synced inode identity; post-move
uncertainty blocks ALL writes until explicit validated reconciliation.

Known optional damaged metadata remains physically charged and prune-blocking;
it does not by itself invalidate complete native protection accounting. Unknown,
nonregular, unreadable or uncertain inputs still fail closed. Exact zero-growth
completion requires native proof, ready authority and root binding; lowering
quota does not authorize any fresh write or bypass native caps. Graph binds the
exact registered roles and acknowledged effective policy separately from the
caller profile. Profile planner previews are hypothetical, not mutation authority.

An explicit pure pressure mode is now source-reviewed (`8baa6eb`), separately
bound from legacy count-floor/selected proofs. It performs no unlink or admission.
Future execution still requires durable pins, exact authorization, intent codec,
maintenance recovery/tombstones and all interruption tests described above.
The parallel extra-root/allowance proposal in ADR-003 is historical and superseded
for accounting; preserve one existing control allowance. No runtime activation
or physical/signing/upgrade acceptance follows from these source checkpoints.

Inert intent framing is now reviewed at `7c422a2`: complete envelope <=64KiB,
strict typed canonical proof/closure/anchor decoding, explicit metadata bindings
and bounded identity descriptors. It has no filesystem caller and grants no
policy/confirmation/deletion authority. The next maintenance-recovery slice must
validate stable authority and every unaffected survivor before installing an
exact logical tombstone, while retaining physical charges for present members.
Ordinary control reconciliation must never promote an intent stage.
