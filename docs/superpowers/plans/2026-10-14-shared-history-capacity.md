# Shared History Capacity Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement task-by-task, or the integrator's explicitly assigned execution method. Steps use checkbox tracking. Owner-delegated policy judgment permits local preparation; this document grants no device or release waiver.

**Goal:** Explicitly register a complete history bundle, persist its effective policy, and enforce one capacity boundary across every registered writer.

**Architecture:** Keep the existing private-parent monitor and legacy per-handle quotas until durable opt-in. A small control store records registration and policy generations; a capacity controller validates complete usage and admits writes under the same monitor. Deterministic completion reservations are a prerequisite, not reimplemented here.

**Tech Stack:** Existing Java11/JDK17, Android minSdk26, private files, SHA-256, injected `ProtectionStore.Durability`; no new dependencies.

**Spec:** [ADR-002](../../decisions/ADR-002-retention-journal.md), [history plan Task3](2026-10-14-history-retention.md), [ADR-001](../../decisions/ADR-001-safe-save-boundary.md).

## Global constraints and prerequisites

- Preserve package/version/signing, min26/target35, `allowBackup=false`, existing UI and mandatory SaveCoordinator protection. No Activity, coordinator, archive, provider or deletion changes.
- Preserve existing snapshot/preparation/completion v1 bytes, metadata v1 formats, exact 8,388,608-byte document limit, store 65,536 slots and each metadata root's 8MiB/4,096-entry caps.
- Source at planning contains read-only `HistoryStorage`, `HistoryGraph`, constructor-final store quotas and optional `HistoryPolicy` descriptors. These do not enforce shared admission.
- First integrate/review the separate completion prerequisite: quota-independent accounting, transaction-bound `.completion-stage`, exact full-stage retry, blocked partial-stage retry, unchanged legacy constructor quotas and no reduced-quota exemption. Reuse its tested APIs after inspecting the final diff; do not depend on an uncommitted worker copy.
- Register all four existing roles together: protection, catalog, identity and index. No lazy registration, implicit default-policy activation or partial all-writer rollout.
- No production opt-in caller in this slice. Tests exercise explicit registration; optional runtime integration remains separately gated.
- ADR-002 must explicitly adopt the bounded control allowance and uncertain-policy acknowledgement semantics below before this plan's policy integration. This plan does not silently override its earlier wording.

## Review focus

- Existing/new handles or process restart bypassing registered policy: Task2 and Task4 prove no legacy fallback.
- Sync callbacks reentering writers before reservations are visible: Task3 proves rejection before nested mutation.
- Rename succeeded but directory sync/acknowledgement failed: Task2/Task5 prove blocked uncertainty and explicit reconciliation.
- Lowered quota or full control space preventing safe completion: Task4 distinguishes zero-growth completion from fresh writes and administrative writes.
- Partial/corrupt inventory appearing smaller than real usage: Task3/Task4 require complete accounting and checked arithmetic; diagnostics never grant credit.

## Files and ownership

All Java paths below are under `apps/MarkdownDesk/src/com/example/markdowndesk/`.

- Create `HistoryControlStore.java`: bounded control codec, durable publication, registration/policy chain validation and reconciliation inspection.
- Create `HistoryCapacity.java`: public bundle API, effective policy and capacity decisions; no arbitrary-path or deletion API.
- Modify `HistoryStorage.java`: parent-state registration, mutation scope, control-root discovery and whole-parent generation invalidation.
- Modify `ProtectionStore.java`, `HistoryCatalog.java`, `HistoryMetadataIO.java`: admission at existing mutation boundaries. Narrow constructor checks in `HistoryIdentity.java`/`HistoryIndex.java` only if needed for role binding.
- Modify `HistoryGraph.java`: expose desired versus effective policy and control accounting/fingerprint. Preserve read-only behavior.
- Keep `HistoryPolicy.java` an immutable requested/profile descriptor; it must not become a mutable authority object.
- Create `verification/history_capacity/{run.sh,HistoryCapacityCheck.java,CapacityRestartProbe.java,run_restart.py,README.md}`. Integrator owns central runner/status/ADR reconciliation.
- Assign control/controller integration to one persistence owner. Do not parallel-edit store/metadata/graph files with completion or planner workers; a separate reviewer may inspect read-only.

## Exact API boundary

`HistoryCapacity` is public; all methods below throw `IOException`:

```java
static HistoryCapacity register(ProtectionStore store, HistoryCatalog catalog,
    HistoryIdentity identity, HistoryIndex index, HistoryPolicy initial,
    ProtectionStore.Durability durability);
static HistoryCapacity open(ProtectionStore store, HistoryCatalog catalog,
    HistoryIdentity identity, HistoryIndex index, ProtectionStore.Durability durability);
EffectivePolicy policy();
Usage inspect();
EffectivePolicy setPolicy(HistoryPolicy requested);
EffectivePolicy reconcile();
```

`EffectivePolicy` is immutable: bundle UUID, generation, whole control-record hash, quotaBytes, automaticCount. Only the controller constructs it. `Usage` exposes dataActualBytes, completionReservedBytes/Slots, dataChargedBytes, controlActualBytes/Entries, usageComplete, mutationBlocked, overQuota and sanitized diagnostics. No payloads or filesystem paths escape.

Package-private `HistoryStorage.mutation(File root)` returns `MutationScope implements AutoCloseable`; public mutators acquire it once under the parent monitor, before source reads or durability callbacks. `HistoryCapacity.admit(File root,long additionalBytes,long additionalReservedBytes,int additionalSlots,int additionalReservedSlots)` returns void and requires that scope. Add a separately typed completion-conversion method consuming an exact validated transaction/reservation descriptor from the prerequisite; never expose caller-chosen released bytes/slots as authority.

`ProtectionStore.setQuotaBytes(long)` delegates to the registered controller, retaining its effective automaticCount; unregistered calls fail explicitly. Ordinary writes in unregistered bundles continue using the invoking handle's constructor quota. Constructor arguments do not mutate registered policy.

## Control protocol and policy semantics

Use fixed direct sibling `history_control` beneath the canonical trusted parent; reject registration if it aliases any data role. Every managed constructor/mutator checks this fixed location before creating files, so fresh processes discover opt-in without a cached Java registry.

Control root existence, even empty or malformed, means **not legacy**. Without a complete valid registration, mutators fail closed. Initial registration validates the complete canonical role bundle and inventory before creating/syncing this root; a crash afterward may leave blocked registration work for explicit reconciliation. Read-only recovery of existing validated records remains available.

Use distinct control framing: magic `0x4d444843`, version1, one-byte type (1 registration, 2 policy), bounded body length, body, SHA-256 of complete preceding framing/body. Maximum16KiB per record; bounded strict UTF-8 strings, no trailing bytes. Control version1 is separate from existing document formats.

Registration body: bundle UUID, generation0, four ordered role IDs and direct-child root basenames, bootstrap constructor quota from the registering store handle, and initial effective quotaBytes/automaticCount. Bootstrap quota is a separate immutable reopen hint; it need not equal the initial effective policy and never grants capacity after opt-in. Validate role uniqueness, names (1–64 UTF-8 bytes; no separators, dot/dotdot or control characters), existing canonical roots and their dependencies. Store neither absolute paths nor content/title/URI metadata.

Policy body: same bundle UUID, positive generation, previous control-record SHA-256, quotaBytes, automaticCount. The next generation is checked `latest+1`, never timestamp order. Require an unbroken unique chain from registration; conflicting, missing, malformed or unknown records block mutation.

Names are `registration.control` and `policy-<16 lowercase hex generation>.control`; their deterministic `.stage` counterparts identify a pending generation. Stage bytes are the final envelope bytes. Use CREATE_NEW, file sync, same-directory atomic move, directory sync, byte/hash validation. Never overwrite/delete a retained stage or generation in this slice. Control chains eventually reaching their cap block further policy changes; compaction is separate reviewed work.

The control allowance is **one shared 1MiB/256-entry budget**, separately reported from ordinary history quota, covering registration/policy records and their stages plus future maintenance intents. It is not one allowance per handle/root/subsystem. Admission includes peak occupied entries/bytes. No snapshots, completions, catalog/index/identity records or arbitrary user strings may use it. No maintenance executor is introduced here. Actual ENOSPC/sync failures still fail normally.

Requested defaults are 64MiB/20; accepted quota choices are 64/128/256/512MiB/1GiB and counts 0/5/10/20/50. Count0 disables optional retention only. Small arithmetic-limit tests may exercise package-private ledger helpers without exposing a production arbitrary-quota bypass.

Before opt-in, different legacy handles keep their distinct constructor quotas. Registration explicitly replaces enforcement for every handle of the registered roots. Reopening a store after opt-in must supply the persisted bootstrap quota as its legacy constructor hint, even if effective quota later changed; a conflicting hint fails rather than replacing policy. Existing handles created before registration use effective shared policy afterward. Document this distinction in the constructor/API Javadoc.

`register` is idempotent for the exact same roles, bootstrap constructor hint and initial effective policy; conflicting roles/bootstrap configuration fail. `open` validates the entire bundle and reconciles complete control records before enabling writes; it never creates registration. Once the control root exists, unlisted roots under this parent cannot start new managed writers. Ordinary unrelated app-private files are not included or modified.

Set-policy outcomes:

1. Validation/admission rejection before creating a stage: effective policy and files unchanged.
2. Stage exists but no committed record: retain it; mark policy mutation blocked. A full exact next-generation stage may be explicitly retried by `reconcile`; partial/changed stages remain blocked and are never rewritten.
3. Move succeeded but directory sync/read-back failed: the new policy may be committed. Return an uncertainty error, retain prior in-memory policy only as last acknowledged information, and block ordinary mutations. Do not claim rollback.
4. `reconcile` revalidates the complete chain and stage state, re-syncs required file/directory durability, then publishes the actual latest validated policy. Reconcile is idempotent; it cannot pick a desired profile over disk authority.

Fresh-process writes require successful `open`/reconciliation. Lowering quota may acknowledge overQuota=true without deleting anything because control writes use their separately bounded allowance. No new data mutation is allowed while policy authority is uncertain; already validated read-only inspection remains available.

## Task1 — Control codec and bounded disk inspection

**Files:** new `HistoryControlStore.java`; new capacity runner/check fixture. No writer hooks yet.
**Interfaces:** package-private `Inspection inspect()` containing validated registration, latest policy record, exact stage descriptor, actual control usage and diagnostics; immutable descriptors only.

- [ ] Write codec/disk tests for valid generation chains, exact bytes/checksum, duplicate roles, traversal names, malformed lengths/UTF-8, unknown type/version, broken previous hash, forks, overflow, trailing bytes and symlink/nonregular entries.
- [ ] Run `bash verification/history_capacity/run.sh codec`; observe RED for missing implementation or specific desired assertions, not an unrelated build failure.
- [ ] Implement bounded encode/read/inspection, including before/after file identity checks and streaming bounded inventory. Control inspections do not publish policy or change files.
- [ ] Run the codec group GREEN; verify corrupt/oversized cases retain their exact bytes and never allocate according to untrusted lengths.
- [ ] Review and commit this internal prerequisite as one coherent unit; no production activation.

## Task2 — Registration, policy publication and reconciliation

**Files:** control store, new `HistoryCapacity`, parent state in `HistoryStorage`; capacity fixture.
**Interfaces:** implement the public bundle API above, initially callable only in tests/internal code until Task3 hooks are complete.

- [ ] Write RED cases for idempotent registration, mixed parents/role aliases, pre-existing usage, conflicting bootstrap policy, failed initial directory creation/sync, generations, lowering over quota and control-budget exhaustion.
- [ ] Add failure points before/after stage creation/write/file sync/move/directory sync/read-back; assert pre-stage rejection versus blocked uncertainty exactly as defined above.
- [ ] Run `bash verification/history_capacity/run.sh policy` RED, then implement publication through injected durability and explicit reconciliation. Mutating controller operations use the same parent monitor.
- [ ] Add reopen tests: empty/partial control root never falls back; committed but unacknowledged policy becomes effective only after successful reconciliation; exact full stage retry consumes no extra generation/slot; partial stage remains unchanged.
- [ ] Run policy tests GREEN and review uncertainty behavior. Do not expose registration through Activity or claim all-writer enforcement before Task3.

## Task3 — All-writer admission and reentrancy boundary

**Files:** storage/controller; store snapshot/prepare/complete entry points; catalog naming; identity resolve/fork and index record mutation entry points; metadata commit hook.
**Interfaces:** one mutation scope at each outer public mutator, admission at the lowest stage-creation boundary. Internal commit helpers require the scope but do not reacquire it.

- [ ] Write RED tests where aggregate usage across four roots exceeds shared quota while each native root remains below its own limit. Include anonymous stages and outstanding completion reservations.
- [ ] Write a barrier-driven two-handle race for the last available capacity; exactly one eligible write succeeds, and failed admission creates zero stage files.
- [ ] Add reentrant sync callbacks attempting snapshot, preparation, completion, naming, identity/index writes and policy changes; every nested mutation rejects before creating files or changing generation. Read-only nested validation remains allowed, with existing operation-context generation checks retained.
- [ ] Implement parent mutation scope with try/finally release. Other threads wait on the monitor; same-thread nested mutators fail explicitly. Acquire scope before any callback-capable validation, not only before commit.
- [ ] Hook every writer/constructor to fixed control-root discovery and registered-role checks. Legacy absence preserves existing admission; present-but-unopened/invalid control state blocks writes. Existing handles consult shared parent state on every mutation.
- [ ] Admit checked actual data bytes + outstanding completion bytes + peak additional bytes/reservations, and each root's occupied/reserved slots. Staging-to-destination rename does not duplicate data. Native metadata caps still apply. Incomplete accounting or corrupt/unknown records grant no capacity.
- [ ] Run `bash verification/history_capacity/run.sh admission` GREEN; run existing store/catalog/identity/index fixtures once to verify legacy compatibility and unchanged v1 bytes. Review all stage-creation sites for missing hooks.

## Task4 — Effective-policy graph and completion conversion

**Files:** controller, `HistoryGraph`, narrow `ProtectionStore` conversion hook; graph/capacity fixtures.
**Interfaces:** graph exposes distinct `desiredProfile` (caller descriptor) and nullable `effectivePolicy` (registered authority). Preserve any compatibility `policy` field only as a clearly documented profile; future deletion authorization must require effectivePolicy.

- [ ] Write RED tests proving a caller-supplied graph profile cannot override registered quota/count, reset policy via another handle, hide control bytes or keep an old fingerprint across policy changes.
- [ ] Read effective policy/control inventory under the parent monitor; fingerprint bundle roles, durable generation/hash, control descriptors/diagnostics and completion reservations. Graph scanning remains read-only; it does not reconcile or register.
- [ ] Write RED conversion tests after lowering quota: exact pre-existing preparation completes with zero charged growth; new snapshot/preparation/catalog/identity/index writes reject. Corrupt source/completion, changed/partial stage, missing reservation, uncertain policy or positive net growth reject.
- [ ] Reuse the prerequisite's validated conversion descriptor; actual stage bytes plus unfilled reservation remain E, and occupied stage replaces the reserved slot. Never infer credit from a legacy anonymous stage. Retained stages continue blocking pruning; only the exact eligible completion path receives the narrow admission exception.
- [ ] Run `bash verification/history_capacity/run.sh integration` and existing graph/planner fixtures GREEN. Update planner expectations for profile versus authority without adding execution/deletion permission.

## Task5 — Restart evidence and final integration review

**Files:** capacity restart fixture/driver/README; integrator later wires central checks and reconciles ADR/status.

- [ ] Add real child-JVM SIGKILL barriers at registration-root creation, registration stage sync/move, policy stage sync/move and policy directory sync before return. Reopen and reconcile twice; verify exact data-record bytes, authoritative generation and no legacy mutation bypass.
- [ ] Include full control allowance, full matching stage, partial stage, post-move uncertainty and a second-handle constructor with conflicting role/quota hints. Report each injected boundary, not a generic power-loss claim.
- [ ] Run `python3 verification/history_capacity/run_restart.py` GREEN and the targeted capacity suite. Do not rerun unrelated benchmarks.
- [ ] Independent persistence reviewer checks actual diff, durability order, role discovery on every writer, reservation provenance, control allowance isolation and no deletion paths.
- [ ] Integrator runs one complete host/build gate under repository CI policy, then records true results and reconciles ADR-002. Source integration does not establish Android/app-UID/power-loss/SAF/Fold/upgrade acceptance.

## RED/GREEN behavioral matrix

| Boundary | RED demonstration | Required GREEN evidence |
| --- | --- | --- |
| Legacy isolation | Shared lock incorrectly shares/reset quotas | Different legacy handles retain their original behavior until explicit opt-in |
| Complete registration | Unregistered sibling/new handle bypasses policy | All known roles bound; unknown managed roots reject; exact reopen succeeds |
| Restart | JVM registry lost, old constructor quota accepted | Durable control discovery blocks fallback and requires valid reconciliation |
| Aggregate admission | Metadata/stages/reservations omitted | Exact combined charge; last-capacity race has one winner; no rejected-write stage |
| Native limits | Large shared quota bypasses root cap | 8MiB/4096 metadata and 65536 store-slot limits still reject |
| Reentrancy | Sync callback spends uncommitted reservation | All nested mutations reject before filesystem changes; scope releases on error |
| Mutable policy | Descriptor/reopened handle changes authority | Only durable generation changes effective policy; graph binds exact authority |
| Uncertain commit | Exception falsely implies old durable policy | Mutations blocked; explicit sync/revalidation adopts actual disk outcome |
| Lowered quota | Safe completion rejected or fresh writes exempted | Only exact zero-growth completion conversion proceeds, with all durability checks |
| Control allowance | Policy capacity funds data or resets per handle | One reported 1MiB/256-entry ceiling; no payload/ordinary metadata borrowing |
| Corruption/overflow | Partial sums interpreted as available space | Explicit blocked diagnostics, checked arithmetic and zero mutation |
| Process interruption | Restart picks partial stage or loses reservation | Exact original/attempt bytes retained; deterministic repeated reconciliation |

## Completion boundary

This slice ends with tested explicit backend opt-in and shared admission. It does not prune, compact policy generations, change UI defaults, export/import records or authorize user-document writes. A full control journal or malformed retained policy stage deliberately blocks policy progression until a separately reviewed recovery/compaction operation exists. Planner/executor and optional Activity enablement remain separate work.
