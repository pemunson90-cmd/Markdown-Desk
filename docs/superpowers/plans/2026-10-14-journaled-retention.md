# Journaled retention execution — next prerequisite

Status: draft for independent review; no executor implemented or enabled.
Base: main6630609 plus the pending reviewed durable-pin prerequisite. This plan
continues ADR-002, not the superseded extra-root/allowance proposal in ADR-003.
Owner standing authorization permits local implementation after technical review;
physical acceptance and production release remain separate gates.

## Bounded source contract

Implement a package-private HistoryMaintenanceExecutor over the exact existing
store/catalog/identity/index bundle and its acknowledged HistoryCapacity. No
Activity, provider, save coordinator, native envelope or policy-format rewrite.
The first executor implements SELECTED deletion only and rejects both automatic
modes. Pressure execution waits for a typed per-root admission-demand protocol;
aggregate reclaimed entries cannot prove a requesting root has capacity. A pure
caller profile is never deletion authority. Require all of:

- Current installed capacity handle, readiness, canonical four-root registration/
  enrollment and exact acknowledged bundle ID/generation/control hash/quota/count.
- Fresh complete pruning graph and exact re-derived plan/proof/selection/demand.
- Plan policy equal to acknowledged effective quota/count, not merely a preview.
- No active/unreconciled operation pin, unknown control input or retained stage.
- Explicit authorization bound to exact SELECTED proof fingerprint and IDs, with
  zero capacity demand. Rebuild the planning profile from effective authority,
  rerun selected planning and compare canonical proofs before publication.
- Complete control accounting, including every terminal reservation, and room in
  the same1MiB/256 allowance for the complete64KiB/256-member bounded intent.

Initial operations are explicit host-callable execution and replay, with no
runtime/automatic pruning caller. Reject empty, stale or insufficient-capacity
plans before any publication. No partial plan or arbitrary filesystem path.
The immutable intent contains the exact removed closure, surviving anchors,
identity routing, policy and unaffected logical fingerprint as already encoded
by HistoryPruneIntent. All native sources remain byte-identical until committed
intent authority and a validated tombstone are both established.

## Locking, publication and replay

One canonical parent monitor and one mutation lease span admission, publication,
validation and deletion. New internal locked helpers must require that lease;
never call a public administration method recursively or relax nested mutation
rejection. Callback-capable durability operations must be followed by fresh
callback-free identity/hash/reference validation before the next destructive
step. A callback must not mutate through another public handle.

Publish only the fixed prune.intent.stage via exclusive creation, full write,
file sync, exact same-inode readback, key-preserving same-directory move,
directory sync and final stable authority validation. Failure before durable
intent permits ZERO unlinks. Retain failed stages; ordinary reconciliation must
never promote them. An explicit full-stage retry policy needs separate reviewed
proof; the initial implementation may conservatively block all retained stages.
Partial/unknown/conflicting stages are never rewritten, deleted or inferred as
authorized intent. Physical ENOSPC is independent of logical reservation.

Open/install the existing validated maintenance tombstone through a lease-aware
internal recovery helper. Before every unlink, validate current intent, every
still-present member's exact length/envelope hash/inode and all unaffected
references/anchors. Genuine absence is accepted only for an exact intent member.
A changed/nonregular/foreign member or survivor stops without additional deletion.
Use typed role+UUID+kind mapped to the registered direct root; no caller paths.

Order: checkpoints, ownership index, completion, preparation, then exclusively
owned snapshots. Sync each affected directory after deletion; correctness relies
on the complete tombstone projection, not on transient physical ordering.
Never free physical bytes/slots for a member still present; never invent pending
transactions or completion reservations from an explained removed preparation.

After all listed members are absent, enter a shared mutation-blocking retirement
state retaining the exact installed intent capability. Validate surviving graph/
anchors, sync all affected data directories, then freshly validate callback-free
closure absence, survivor proof, control/registration/policy and inode identities.
Unlink only the exact committed intent, sync its control directory, freshly
validate the no-intent terminal state, clear only that exact installed view, and
reconcile capacity through a lease-requiring internal helper before new writes.
No injectable callback may intervene between final victim validation and unlink.

Current APIs cannot do this by calling ordinary reconciliation: readView demands
an intent while an installed projection exists. Add an explicit lease-aware
terminal validator that inspects survivors without recursively revalidating the
missing-intent view. It must retain closure and anchor proof, reject conflicting
control inputs/root replacements, and establish directory durability before
clearing exactly the matching view. Never use unconditional installView(null).
Public recovery open and reconcile remain wrappers over internal helpers which
require an existing lease rather than acquiring nested administration.

If intent removal/final sync is uncertain, retain the retirement capability and
block writes. Same-process retry or a second handle must validate either the
exact still-present intent for replay, or the verified no-intent terminal state.
Fresh-process absence is safe only because all member removals were synced before
intent unlink; a restored intent remains idempotently replayable.

## Meaningful verification before runtime enablement

- [ ] Independent review of this plan against the completed pin API and actual
      recovery locking; resolve publication retry and final retirement states.
- [ ] RED production executor fixtures: stale policy/profile, changed preview,
      selected IDs, unsupported automatic demands, anchors, members, active pins
      and unknown stages
      cause zero unlinks; same-history newest anchors survive.
- [ ] Actual OS write-limit/ENOSPC-like publication failure before intent commit
      causes zero unlinks and retains source/stage evidence.
- [ ] Functional whole-group and selected-checkpoint deletion; all survivors'
      exact original/attempted bytes, catalog/index readers, physical charges and
      reservations remain correct after each interrupted physical prefix.
- [ ] Superseded authority handle and policy A→B→A with equal final values reject
      stale authorization; exact generation/hash still bind the plan.
- [ ] Final control-sync failure with same-process retry and second-handle open;
      intent-unlink failure with present/absent outcomes; last data-directory sync
      callback changes a survivor and causes zero subsequent unlinks.
- [ ] Callback/reentrant/second-handle tests at sync/move/unlink boundaries,
      same-byte inode replacement and no callbacks after final validation.
- [ ] Child JVM SIGKILL before/after intent sync, move, directory sync, each
      member kind unlink, each affected directory sync, intent unlink and final
      control sync. Two independent fresh-process reopens/replays per boundary;
      no extra members or unrelated bytes removed. Retained invalid input blocks.
- [ ] Bounded256-member/64KiB proof and control allowance boundaries; constrained
      heap with near-limit snapshot pairs, no payload materialization.
- [ ] Independent persistence review of exact committed source, then scoped
      dependency regressions and one clean full host/two-unsigned-build gate.

A host filesystem SIGKILL test is not sudden-power-loss or Android app-UID/SAF
proof. No physical Fold, signing, upgrade, export/import or M1 completion claim.
Partial pin-stage resolution, exact persisted export manifest and operation
quiescence remain runtime prerequisites for pin-consuming user operations.

## Later automatic pressure prerequisite

Bind a typed demand to registered role, additional physical bytes/slots and
completion-reserved bytes/slots. Recompute actual deficits, prove selected
bytesByRoot/entriesByRoot satisfy every per-root metadata/store limit as well as
shared quota, and recheck ordinary admission after verified retirement. Until
that protocol and root-deficit tests pass, reject automatic executor modes; the
existing pure previews remain available without execution authority.

Independent plan review at463983b identified terminal-state, typed-demand and
exact-authority gaps above. This revision addresses the selected-only contract;
re-review against completed pin source is still required before code.
