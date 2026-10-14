# Durable operation pins implementation plan

> **For agentic workers:** Use superpowers:executing-plans for the bounded tasks below, with an independent persistence review of the exact final source.

**Goal:** Persist conservative operation protection across restart without runtime consumers or deletion authority.

**Architecture:** One active immutable BEGIN plus deterministic CLOSE per operation, retaining all audit pairs inside the existing shared administrative allowance. Explicit reconciliation separates decoded disk evidence from acknowledged durable state. Every graph/planner path refuses active or unreconciled pins; no caller-supplied omission can bypass protection.

**Tech Stack:** Existing Java11 native backend, installed JDK17/SDK37, no new dependency or signing input.

**Spec:** `docs/decisions/ADR-002-retention-journal.md` and root/reviewer bounded durable-pin contract (2026-10-14).

## Global constraints

- Preserve package2.2.5/code18/min26/target35, native v1 bytes and existing prune proof bytes.
- One shared1MiB/256-entry control allowance, including enrollment, all stages, policy, maintenance and pins. No additional root allowance.
- One active operation; immutable retained BEGIN/CLOSE audit pairs. No deletion, expiry, garbage collection, provider IO or runtime caller.
- EXACT_SUBSET supports only standalone validated STORE/SNAPSHOT envelopes; valid unresolved recovery inputs remain eligible without requiring a pruning-complete graph.
- ALL_BUNDLE freezes pruning only across the four registered roots, including later appends. Both scopes admit ordinary immutable writes and reservation-preserving policy changes; captured policy remains a valid ancestor. It binds manifest domain/hash/count/length; resume requires the original independently persisted manifest, never a fresh capture.
- CLOSE is fixed canonical bytes derived from BEGIN plus RELEASED or CANCELLED. No caller timestamp, text, output digest or archive-success assertion.
- Reserve CLOSE bytes and one entry before BEGIN. Enforce the unfilled reservation across every control writer. A valid prefix matching either canonical outcome gives accounting credit only, never permission to choose an outcome, append or rewrite.
- Only a complete matching retained stage may be explicitly synced/promoted. Partial BEGIN/CLOSE stages remain preserved and blocked. Reservation is accounting, not guaranteed eventual cancellation; terminal repair remains a separately reviewed runtime prerequisite.
- Parent monitor and mutation lease precede callbacks. Revalidate policy/enrollment, role binding, file identity and operation evidence after the last callback before acknowledgement.
- Complete CLOSE bytes alone do not release protection. New EXACT_SUBSET RELEASED and CANCELLED both freshly validate pinned sources. Already committed CLOSED audit pairs reconcile without dereferencing retired inputs. ALL_BUNDLE cancellation may omit the manifest after ready authority and complete mandatory accounting, without claiming manifest/export validity.
- Active/unreconciled pins block all existing graph/planner modes and incompatible maintenance recovery. Existing readers may still read protected native inputs.
- Root owns MainActivity, STATUS, central runner registration and the final full integration/hosted gate.

## Review focus

- An unrelated control write cannot spend reserved CLOSE capacity.
- A committed CLOSE following failed directory sync cannot release a pin through graph or maintenance readers.
- Reentrant callback writes and replacement of synced stages cannot acquire authority.
- An exact subset may contain an unresolved recovery snapshot, but may never silently widen to metadata/reference closure.
- Resume must use the original manifest identity; retained partial stages cannot be repaired by guessing or rewritten outcomes.

### Task1: Typed codec and shared control evidence

**Files:** new pin record/manifest codec; narrow `HistoryControlStore.java`, `HistoryCapacity.java`; `verification/history_pins` codec fixtures/runner.

**Interfaces:** `HistoryPinRecord` owns enum Purpose(EXPORT,RESTORE), Scope(EXACT_SUBSET,ALL_BUNDLE), Domain(NATIVE_HISTORY_V1), Outcome(RELEASED,CANCELLED); immutable BEGIN/CLOSE records; complete BEGIN <=16KiB/256 descriptors; CLOSE exactly126 bytes. Names are `pin-<canonical UUID>.begin[.stage]` / `.close[.stage]`. BEGIN includes registration hash, captured policy generation/hash and manifest identity. CLOSE includes operation UUID, exact BEGIN hash, manifest hash and outcome. Inspection exposes pin validity/stages/active operation and unfilled close reservation separately from policy chain. Bound caller manifest bytes explicitly to16MiB and count to77824; stream with8KiB buffer, retaining no manifest bytes. Count/domain are opaque binding metadata, not parsed export semantics.

- [x] Observed production behavioral RED: typed BEGIN previously invalidated otherwise ready control authority.
- [x] Implement bounded canonical framing, typed scope, operation identity, exact policy/bundle binding, deterministic outcomes and retained stages.
- [x] Make every control admission include pin close reservation; malformed/duplicate/conflicting pairs block.
- [x] Initial codec/discovery:909 assertions, including every CLOSE prefix and constant charge; old control vectors14 cases/209 assertions. Lifecycle/full-allowance tests follow in Task2/3.

### Task2: Explicit durable lifecycle and mandatory freeze

**Files:** new `HistoryPins.java`; narrow storage/capacity/store/graph/maintenance hooks; production lifecycle fixtures.

**Interfaces:** `HistoryPins.open(store,catalog,identity,index,durability)` explicitly reconciles; `begin(Purpose,Scope,Collection<String> snapshotIds,ManifestIdentity)` requires current ready capacity, generates UUID and persists exact native snapshot descriptors. `ManifestIdentity.read(Domain,long count,InputStream)` hashes bounded independently supplied bytes. `resume(String operationId,InputStream originalManifest)` compares exact bound bytes and validates supported inputs; it confers no output/progress/provider authority. `close(String operationId,Outcome,InputStream manifestOrNull)` requires original manifest for RELEASED, permits absent manifest for CANCELLED, and validates current EXACT_SUBSET inputs for either new outcome. A stable control-only recovery path can reconcile complete stages/receipts without the ordinary policy-stage promotion cycle; it never enables ordinary writers. Installed acknowledgement records exact pin inventory/file identities after the last callback. Graph/planner and maintenance check this mandatory state. No graph-complete dependency for snapshot subset capture or cancellation.

- [ ] Write native subset and full-bundle lifecycle tests before implementation, including a snapshot belonging to an unresolved transaction.
- [ ] Publish BEGIN/CLOSE by bounded stage, sync, move, directory sync and callback-free final validation.
- [ ] Preserve complete-stage idempotent retry and reject partial-stage mutation.
- [ ] Require persisted manifest for resume; reject changed input, authority, scope and evidence.
- [ ] Block floor/pressure/selected planners and incompatible maintenance recovery while active/unreconciled; retain legacy no-pin proof bytes.
- [ ] Verify targeted production behavior and exact native bytes; commit.

### Task3: Fresh-process, crash and adversarial evidence

**Files:** `verification/history_pins/{run.sh,run_restart.py,PinRestartProbe.java,README.md}`.

- [ ] Two fresh JVM reconciliations at actual BEGIN/CLOSE file sync, move and directory sync boundaries.
- [ ] Physical partial stages, full/overflow allowance, close capacity preservation, callback failures and file replacement.
- [ ] Closed audit validation with retired inputs; omitted/different manifest rejection; unresolved subset input acceptance.
- [ ] Independent exact-source review, scoped regression runs and final coherent commits.
- [ ] Root performs central registration, full clean unsigned/host and hosted gates; physical Android/power loss/signing remain NOT RUN.
