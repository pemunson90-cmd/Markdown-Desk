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
A pure caller profile is never deletion authority. Require all of:

- Current canonical four-root registration/enrollment and acknowledged policy.
- Fresh complete pruning graph and exact re-derived plan/proof/selection/demand.
- Plan policy equal to acknowledged effective quota/count, not merely a preview.
- No active/unreconciled operation pin, unknown control input or retained stage.
- Explicit authorization bound to exact proof fingerprint and selected IDs;
  automatic pressure additionally binds the exact current capacity request.
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

After all listed members are absent, validate surviving graph/anchors again,
sync every affected data directory, remove only the exact committed intent,
then sync its control directory. Clear the installed view only through a
lease-aware exact-intent helper after verified retirement, and explicitly
reconcile ordinary authority before future writes. If intent removal or its
final sync is uncertain, keep mutation blocked until a fresh no-intent or
exact-intent reconciliation. Restart may see absent intent only after data
removals were synced; restored intent remains idempotently replayable.

## Meaningful verification before runtime enablement

- [ ] Independent review of this plan against the completed pin API and actual
      recovery locking; resolve publication retry and final retirement states.
- [ ] RED production executor fixtures: stale policy/profile, changed preview,
      selected IDs, demands, anchors, members, active pins and unknown stages
      cause zero unlinks; same-history newest anchors survive.
- [ ] Actual OS write-limit/ENOSPC-like publication failure before intent commit
      causes zero unlinks and retains source/stage evidence.
- [ ] Functional whole-group and selected-checkpoint deletion; all survivors'
      exact original/attempted bytes, catalog/index readers, physical charges and
      reservations remain correct after each interrupted physical prefix.
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
