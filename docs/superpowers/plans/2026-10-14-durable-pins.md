# Durable operation pins implementation plan

> **For agentic workers:** Use superpowers:executing-plans for the bounded tasks below, with an independent persistence review of the exact final source.

**Goal:** Persist conservative operation protection across restart without runtime consumers or deletion authority.

**Architecture:** One active immutable BEGIN plus deterministic CLOSE per operation, retaining all audit pairs inside the existing shared administrative allowance. Explicit reconciliation separates decoded disk evidence from acknowledged durable state. Every graph/planner path refuses active or unreconciled pins; no caller-supplied omission can bypass protection.

**Tech Stack:** Existing Java11 native backend, installed JDK17/SDK37, no new dependency or signing input.

**Spec:** `docs/decisions/ADR-002-retention-journal.md` and root/reviewer bounded durable-pin contract (2026-10-14).

## Global constraints

- Preserve package2.2.5/code18/min26/target35, native v 1 bytes and existing prune proof bytes.
- One shared 1MiB/256-entry control allowance, including enrollment, all stages, policy, maintenance and pins. No additional root allowance.
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

**Interfaces:** `HistoryPinRecord` owns enum Purpose(EXPORT,RESTORE), Scope(EXACT_SUBSET,ALL_BUNDLE), Domain(NATIVE_HISTORY_V1), Outcome(RELEASED,CANCELLED); immutable BEGIN/CLOSE records; complete BEGIN <=16KiB/256 descriptors; CLOSE exactly 126 bytes. Names are `pin-<canonical UUID>.begin[.stage]` / `.close[.stage]`. BEGIN includes registration hash, captured policy generation/hash and manifest identity. CLOSE includes operation UUID, exact BEGIN hash, manifest hash and outcome. Inspection exposes pin validity/stages/active operation and unfilled close reservation separately from policy chain. Bound caller manifest bytes explicitly to 16MiB and count to77824; stream with 8KiB buffer, retaining no manifest bytes. Count/domain are opaque binding metadata, not parsed export semantics.

- [x] Observed production behavioral RED: typed BEGIN previously invalidated otherwise ready control authority.
- [x] Implement bounded canonical framing, typed scope, operation identity, exact policy/bundle binding, deterministic outcomes and retained stages.
- [x] Make every control admission include pin close reservation; malformed/duplicate/conflicting pairs block.
- [x] Initial codec/discovery:909 assertions, including every CLOSE prefix and constant charge; old control vectors14 cases/209 assertions. Lifecycle/full-allowance tests follow in Task2/3.

### Task2: Explicit durable lifecycle and mandatory freeze

**Files:** new `HistoryPins.java`; narrow storage/capacity/store/graph/maintenance hooks; production lifecycle fixtures.

**Interfaces:** `HistoryPins.open(store,catalog,identity,index,durability)` explicitly reconciles; `begin(Purpose,Scope,Collection<String> snapshotIds,ManifestIdentity)` requires current ready capacity, generates UUID and persists exact native snapshot descriptors. `ManifestIdentity.read(Domain,long count,InputStream)` hashes bounded independently supplied bytes. `resume(String operationId,InputStream originalManifest)` compares exact bound bytes and validates supported inputs; it confers no output/progress/provider authority. `close(String operationId,Outcome,InputStream manifestOrNull)` requires original manifest for RELEASED, permits absent manifest for CANCELLED, and validates current EXACT_SUBSET inputs for either new outcome. A stable control-only recovery path can reconcile complete stages/receipts without the ordinary policy-stage promotion cycle; it never enables ordinary writers. Installed acknowledgement records exact pin inventory/file identities after the last callback. Graph/planner and maintenance check this mandatory state. No graph-complete dependency for snapshot subset capture or cancellation.

- [x] Native subset/full-bundle lifecycle fixtures include PREPARED inputs. Initial discovery RED preceded implementation; lifecycle fault REDs exposed same-byte source replacement, retained-policy promotion, post-promotion callback validation and omitted known evidence.
- [x] Publish BEGIN/CLOSE by bounded stage, sync, move, directory sync and callback-free final validation.
- [x] Preserve complete-stage idempotent retry and reject partial-stage mutation.
- [x] Require persisted manifest for resume; reject changed input, authority, scope and evidence.
- [x] Block floor/pressure/selected planners and incompatible maintenance recovery while active/unreconciled; retain legacy no-pin proof bytes.
- [x] Verify targeted production behavior and exact native bytes; coherent lifecycle checkpoint.

### Task3: Fresh-process, crash and adversarial evidence

**Files:** `verification/history_pins/{run.sh,run_restart.py,PinRestartProbe.java,README.md}`.

- [x] Two fresh JVM reconciliations at actual BEGIN/CLOSE file sync, move and directory sync boundaries.
- [x] Physical partial stages, full/overflow allowance, close capacity preservation, callback failures and file replacement.
- [x] Closed audit validation with retired inputs; omitted/different manifest rejection; unresolved subset input acceptance.
- [ ] Independent exact-source review, scoped regression runs and final coherent commits.
- [ ] Root performs central registration, full clean unsigned/host and hosted gates; physical Android/power loss/signing remain NOT RUN.

## Review refinements and evidence

Independent review required a zero-growth shared-reservation check before ordinary policy-stage promotion; both byte and slot overages now preserve the stage. Complete CLOSE.stage recovery carries newly closed source validation through all later reconciliation callbacks, distinct from already committed audit receipts. A narrow closed-audit-only reconciliation path permits a valid committed maintenance intent without native source dereference, intent promotion or ordinary writer readiness. Known retained pin names/hashes cannot disappear from the current process's acknowledged history; no fresh-process wholesale omission-detection guarantee is claimed.

Runtime consumers remain prohibited until operation quiescence is enforced on every terminal path and partial-stage terminal repair is independently designed/reviewed. The current backend does not guarantee eventual cancellation.

Observed scoped regressions: control codec14 cases/209 assertions; capacity policy49, admission61, integration42 checks; maintenance intent codec73/103 checks, recovery65 cases/578 checks,8MiB streaming member under 128MiB heap and14 fresh JVM reopens. Final pin counts and exact commit review are recorded in the completion handoff.

Final scoped pin run:909 codec/discovery assertions,332 lifecycle assertions,4 streamed-manifest assertions under a 16MiB heap;20 externally killed durability/partial-stage fixtures and42 fresh JVM reopens (including closed-audit maintenance). Complete BEGIN.stage byte/slot reservation overage is rejected before promotion. Existing planner81/5 and pressure60/9 assertions pass unchanged.
