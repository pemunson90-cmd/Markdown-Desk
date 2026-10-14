# Operation quiescence and terminal recovery implementation plan

> **For agentic workers:** Use superpowers:executing-plans for these bounded tasks,
> with independent persistence/concurrency review before implementation and exact
> source review before integration. This checkpoint authorizes a plan only.

**Goal:** Enforce actual operation quiescence before releasing durable pins and
recover canonical CLOSE prefixes without overwriting retained evidence.

**Architecture:** A shared per-parent operation coordinator owns consumer admission,
tracked I/O and callback delivery. It freezes admission and drains existing work
outside the history monitor and mutation lease, then issues an exact terminal
capability. The pin backend consumes that capability under its existing single
lease, preserving current native/control formats and terminal reservations.

**Tech Stack:** Existing Java11 backend, JDK17/SDK37 offline; no new dependency,
Activity, provider adapter, runtime export consumer or signing input.

**Spec:** ADR-002; `2026-10-14-durable-pins.md`; the root/reviewer terminal protocol
rulings dated 2026-10-14, captured below. Source inspected at approved executor
checkpoint `367ca347a753d6333aed1dc8389a7d5e268ccf90`. Root integration status may
advance independently; rebase is not required, merge current main normally at a
coherent checkpoint. This document is the only file changed by the planning task.

## Global constraints and source findings

- Preserve package2.2.5/code18/min26/target35, native v1, BEGIN/CLOSE wire bytes,
  typed scope meanings and one shared 1MiB/256-entry allowance. No repair record,
  extra allowance, expiry, journal compaction, native deletion or provider I/O.
- `HistoryStorage` currently shares one parent monitor and rejects nested mutation
  through `MutationScope`. It has no lifetime registry for I/O or callbacks. A
  monitor acquisition, cancellation flag, interrupted thread, empty queue, or
  in-memory absence is not evidence that an operation has stopped accessing data.
- `HistoryPinRecord.inspect` already recognizes either canonical CLOSE prefix and
  converts its physical occupancy into reservation credit. That recognition grants
  no append, promotion, outcome-selection or release authority today.
- `HistoryPins.close`, ordinary `open/reconcile`, complete CLOSE.stage promotion,
  post-rename CLOSE reconciliation, and maintenance's closed-audit helper are all
  potential terminal acknowledgement paths. Guarding only explicit close is unsafe.
- New EXACT_SUBSET terminal publication or stage completion/promotion must freshly
  validate every persisted snapshot descriptor for either outcome. PREPARED inputs
  remain valid; do not impose pruning-complete optional metadata. ALL_BUNDLE
  cancellation validates mandatory native accounting/reservations, not a guessed
  original export manifest. Both scopes still allow quota-admitted immutable appends
  and reservation-preserving policy changes while an operation is running.
- Captured policy remains a valid ancestor; current registration/enrollment/roles,
  bootstrap hint, acknowledged authority where applicable, full control accounting,
  known retained evidence and terminal credit remain mandatory. Partial recovery
  needs dedicated control authority, not ordinary readiness that its stage blocks.
- A CLOSE receipt means release of pin protection, never verified archive success.
  Future consumers must independently verify output and persist their exact manifest.
  This slice neither reconstructs output progress nor makes provider callbacks safe.
- Reservations guarantee logical accounting only. Full control journals, unknown
  evidence, damaged sources, stuck I/O, physical ENOSPC and partial BEGIN can still
  prevent cancellation indefinitely. Compaction and BEGIN provenance are separate.

## Settled recovery matrix

CLOSE is exactly 126 bytes: outcome at zero-based offset93; checksum at94..125.
Let `n` be the current stage length, proved against exact bytes from its committed
BEGIN. No length or UUID filename alone establishes a matching prefix.

| Evidence | Allowed explicit action after quiescence |
|---|---|
| Committed BEGIN, no CLOSE/stage | Fresh RELEASED with original manifest, or CANCELLED; existing exclusive-create publication |
| Exact CLOSE.stage prefix, 0 <= n <= 93 | Explicit CANCEL_UNBOUND only; append canonical CANCELLED suffix; never infer RELEASED |
| Exact CLOSE.stage prefix, 94 <= n < 126 | RECOVER_BOUND preserves encoded outcome; RELEASED requires original manifest |
| Exact complete CLOSE.stage, n = 126 | RECOVER_BOUND read-only sync/promotion; RELEASED requires original manifest; no rewrite |
| Already committed matching CLOSE | Quiesced immutable audit reconciliation; no source or manifest dereference; no outcome change |
| Partial BEGIN.stage | Preserve and block; no quarantine, promotion, deletion or guessed cancellation |
| Complete valid BEGIN.stage | Existing explicit validated promotion may remain; no consumer admission before durable acknowledgement and fresh binding |
| Invalid/nonprefix/oversized/nonregular/unknown/conflicting evidence | Preserve and block; no repair or audit shortcut |

Root ruling: the original persisted-manifest requirement applies to newly
requested RELEASED and partial/full RELEASED CLOSE.stage recovery. It does **not**
apply to committed historical CLOSED audits. Those may reference legitimately
retired native inputs and must remain reconcilable beside a committed prune intent.
Any acknowledgement that releases live/unreconciled protection still requires
freeze/drain. Adopting a committed closed pair permanently seals that operation
against resume, I/O admission and callback delivery in the current process.

For an exact prefix, physical bytes `n` plus reserved bytes `126-n` remain126;
the stage occupies the one terminal slot, so reserved entries are zero. With no
stage, reserve126 bytes and one entry. Append adds no entry and spends only this
operation's remaining credit. Unknown/conflicting evidence receives no exception.
Every existing control writer must retain the same admission invariant.

## Coordinator and capability contract

Proposed new `HistoryOperations.java` owns one shared coordinator in
`HistoryStorage.State`; separate handles cannot create independent gates. Its
private constructors produce these package-private types:

- `OperationKey`: canonical parent and four roles, bundle UUID, registration hash,
  operation UUID and exact committed BEGIN envelope hash. Bind captured policy
  ancestry through the existing pin validator; a policy update never changes key.
- `OperationHandle`: key plus process-instance nonce and checked monotonic epoch.
  No caller-supplied epoch, boolean quiescence assertion or public token constructor.
- `WorkKind { READER, WRITER }`, `TrackedInput`, `TrackedOutput`, `CallbackTicket`:
  registrations owned until actual I/O closure or callback completion.
- `DrainRequest`: an operation already frozen; it contains no terminal authority.
- `Quiescence`: exact key, nonce and epoch plus coordinator identity, minted only
  when all opening/open/in-flight/closing I/O and pending/running callbacks are zero.
- `AuditQuiescence`: a bounded batch of exact committed closed keys, each sealed
  against admission, for existing multi-pair audit/maintenance reconciliation.

Proposed interfaces (all may throw IOException; await also InterruptedException):

```
HistoryOperations forBundle(store, catalog, identity, index)
OperationHandle activateAcknowledged(String operationId, InputStream originalManifest)
TrackedInput openReader(OperationHandle operation, ReaderFactory factory)
TrackedOutput openWriter(OperationHandle operation, WriterFactory factory)
CallbackTicket post(OperationHandle operation, CallbackQueue queue, Runnable callback)
boolean cancelPending(CallbackTicket ticket)
DrainRequest freeze(OperationHandle operation)
Quiescence awaitQuiescent(DrainRequest request)
AuditQuiescence prepareClosedAudits()
```

`ReaderFactory.open()` returns InputStream; `WriterFactory.open()` returns
OutputStream; `CallbackQueue.post(Runnable wrapper)` schedules only the coordinator's
wrapper. These are internal seams for synthetic tests and later reviewed adapters,
not provider implementations in this slice. Delegate streams remain private to
tracked wrappers; no API returns an untracked delegate or a caller-closeable count.
The initial wrappers are thread-confined to their opening worker. A future adapter
requiring transfer must add a reviewed explicit ownership protocol.

Lifecycle is COLD_FROZEN -> OPEN -> FREEZING -> QUIESCENT -> TERMINAL. A terminal
failure retains frozen state; there is no finally-unfreeze path. Failed or expired
attempt tokens can be replaced only by fresh proof of the same frozen epoch.
Successful terminal acknowledgement seals the key permanently. Resuming a valid
active operation without a terminal stage requires explicit fresh manifest/source
validation and a new epoch; every old handle/ticket/capability then fails.

Startup begins frozen **before any consumer is accepted**, including queued work
restored by a future runtime owner. Disk discovery may bind a gate but cannot open
it. `HistoryPins.resume` must no longer be an independent admission path: it either
returns a validated description through the coordinator or requires its operation
handle. Complete BEGIN promotion does not automatically activate consumers.

Admission reserves an opening ticket before calling a stream factory. An exception from a generic factory does not prove that it left no
resource or callback behind: retain an indeterminate opening registration and
freeze the operation. There is no caller-asserted clear method; this conservative
case may require fresh-process recovery. A later concrete opener may add a
reviewed ownership proof, not an exception-type heuristic. Freeze closes new admission immediately and rejects future reads/writes from obsolete
handles; an already running I/O call remains counted until it returns and the
owned stream actually closes. Closing I/O, flushes and delegate callbacks execute
outside both coordinator and history locks. A close exception does not certify
closure: retain the outstanding registration unless closure is positively proved
by the owned resource, and otherwise remain blocked for explicit retry.

A posted callback registers **before** calling the scheduler. Its only scheduled
object is a coordinator wrapper, which atomically claims a pending ticket or
suppresses delivery after freeze/epoch change. Pending cancellation atomically
removes delivery authority and clears the callback reference; separately tracked
resources remain counted until closed. A callback already running remains counted
through its `finally`, even after cancellation. A scheduler exception cannot allow
an already queued wrapper to execute unregistered. No new callback can appear
through a reader's completion after freeze. No user callback runs under a lock.

`awaitQuiescent` rejects calls while the caller holds the history monitor/mutation
lease or owns live operation I/O/is executing an operation callback. A participant
can request freeze but must schedule draining elsewhere. Timeout/interruption
leaves the gate frozen and returns no capability. Cancellation of a worker is a
request, not a decrement. Stuck external work can prevent progress; do not pretend
that a timeout drained it.

Lock order: never acquire history while holding the coordinator lock. Brief
history->coordinator validation is allowed, with no callback or wait under either.
Freeze/drain occurs after releasing the history monitor and mutation lease. Once
quiescent, the terminal entry point acquires the history monitor and one lease,
then briefly verifies exact gate/epoch/counts without waiting. Final validation
checks the capability again after the last callback. Ordinary unrelated native
writers retain existing quota admission; only operation consumers are frozen.

## Terminal APIs and filesystem protocol

Proposed `HistoryPins` interfaces replace capability-free terminal routes:

```
void close(Quiescence capability, Outcome outcome, InputStream originalManifest)
void recoverClose(Quiescence capability, RecoveryChoice choice, InputStream originalManifest)
// RecoveryChoice { CANCEL_UNBOUND, RECOVER_BOUND }
```

`HistoryPinTerminalRecovery.java` owns bounded prefix inspection and append under
the existing lease. `HistoryPins` owns source/control validation, durable
publication and acknowledgement. Existing no-capability close/reconcile routes
must reject terminal transitions rather than silently synthesize permission.
Public ordinary open/reconcile may perform the coordinator preflight outside the
history monitor; locked helpers accept explicit capabilities and never drain.

Committed-audit reconciliation uses `AuditQuiescence`, never a blanket boolean.
`prepareClosedAudits()` briefly inspects/binds exact closed keys under history
coordination, releases it, freezes/drains those gates, and returns the batch.
Reacquisition revalidates every key and retained pair; new/different/active/staged
pins reject. The same permit remains valid across the executor's exact intent
retirement because it binds operation keys, not a stale whole-control inventory.
Existing final control/directory identity checks still refresh after intent removal.
The executor's outer entry points must obtain this preflight outside their lease
and pass it into `reconcileMaintenanceAuditsLocked`; no nested public Pins.open.
Already sealed keys can satisfy zero-work proof without resurrecting consumers.

Partial-stage algorithm:

1. Under the lease, verify exact capability, canonical roles/bootstrap/enrollment,
   committed BEGIN, policy ancestry/current authority, retained evidence and complete
   shared accounting. Dedicated recovery explicitly syncs valid existing control
   authority without promoting unrelated stages or enabling ordinary writer
   readiness. Reject every incompatible stage or maintenance state.
2. No-follow stat the existing direct CLOSE.stage; require regular file, 0..125
   length and exact prefix of the finite canonical outcomes. Choose only as the
   matrix permits. Match manifest hash/length for RELEASED before any append;
   never recapture current inventory. Manifest reads are callback-capable, bounded
   by the existing16MiB limit, followed by fresh authority/source/capability checks.
3. Capture same-inode stage/prefix and all relevant control, root and source
   identities. Validate exact subset sources or ALL_BUNDLE mandatory safe inventory,
   including PREPARED reservations, without requiring a complete pruning graph.
4. Open the existing file using WRITE+APPEND+NOFOLLOW_LINKS, with **no CREATE or
   TRUNCATE_EXISTING**. Do not use FileOutputStream(file,true), which may create a
   missing stage. Recheck size/identity/prefix and all proofs after the last
   callback. Append only `canonical[n..126)`; no observer/callback between final
   proof and actual write. Zero progress is an error, not an unbounded retry.
5. Read back exact126 bytes/identity, force the same append channel, close it and
   validate again. Direct FileChannel.force(true) is suitable; fault observers
   run before final validation or after the actual write/force, never between
   validated prefix and write. Failed partial writes retain the extended prefix
   and the corresponding smaller reservation; no rollback or rewrite.
6. Use the existing same-directory atomic stage->CLOSE move and directory sync.
   Validate exact bytes/inode, roots/control/ancestry, native source proof and
   capability after all callbacks. Only then acknowledge release and seal key.
   Full126-byte stage recovery starts with read-only sync at this step; it still
   requires the same quiescence, manifest and fresh-source rules.
7. Failure after rename leaves committed evidence but no premature release. A later
   quiesced committed-audit reconciliation retains bytes and does not dereference
   sources that may have legitimately retired after a previously durable close.

Fresh new CLOSE uses existing exclusive-create publication only when genuinely
absent. Recovery never creates a missing stage, changes encoded outcome, allocates
another record, replaces an inode or overwrites bytes. Existing durable CLOSED
retry can be idempotent only for the same outcome and exact binding.

## Partial BEGIN conclusion

The current protocol cannot prove after restart that a partial BEGIN was never
acknowledged. Filename, missing committed BEGIN/CLOSE, process-local counters and
current journal absence cannot distinguish an interrupted initial write from lost
or externally changed durable evidence. Therefore this slice performs no quarantine
rename and grants no fresh operation or reservation credit from such inference.
Quiescence alone proves that work is stopped now, not that acknowledgement never
occurred. A future provenance protocol must independently prove the never-ack state
and conserve raw bytes, inode and charged slot through any typed atomic rename.
It needs separate wire/admission/crash review; no placeholder repair file here.

## Review focus and proposed implementation tasks

Unimplemented tasks below require independent approval of this plan first.
Backend owner owns the coordinator, terminal backend, narrow helper wiring and
scoped fixtures. Root owns Activity/runtime adapters, STATUS, central runner
registration and integration gates. No implementation is claimed by this plan.

### Task1: Enforced operation lifetime and cold-start freeze

Files: create `apps/MarkdownDesk/src/com/example/markdowndesk/HistoryOperations.java`;
narrow `HistoryStorage.java` shared state; create
`verification/history_operation_recovery/{OperationQuiescenceCheck.java,run.sh,README.md}`.

- [ ] Write latch-driven production RED fixtures for an opening reader, in-flight
      read/write, failed factory with uncertain resource ownership, blocked/throwing
      close, running callback, queued callback after freeze,
      queue-post exception after enqueue, and delayed old-epoch delivery. Assert
      no terminal capability until actual completion, and no late callback runs.
- [ ] Add two-handle shared-gate tests, fresh frozen startup, wrong key/root/BEGIN/
      epoch/nonce rejection, self-drain rejection, interrupt/timeout preserving
      freeze and native worker completion needing the released history monitor.
- [ ] Implement the exact interfaces/lifecycle above with private tracked resources.
      Use deterministic barriers, never sleeping tests or a caller-supplied zero count.
- [ ] Run `bash verification/history_operation_recovery/run.sh coordinator`; require
      all named behavioral assertions PASS, then independent concurrency review and
      a coherent canonical-author commit. A missing-class scaffold is not behavior RED.

### Task2: Mandatory quiescence on every terminal route

Files: narrow `HistoryPins.java`, `HistoryMaintenanceExecutor.java` and existing
lease-aware audit helper; `OperationTerminalCheck.java` in the new fixture folder.
Preserve HistoryPinRecord encoding and existing control allowance semantics.

- [ ] Write production REDs proving old close, full CLOSE.stage promotion,
      committed-close reconciliation and maintenance audit entry cannot release
      protection while any registered participant remains. Exercise every entry
      with a second handle; no caller-omitted capability path is allowed.
- [ ] Implement close/recovery capability consumption and audit preflight outside
      locks, with fresh callback-free checks under the existing lease. Keep active
      pin graph/planner freeze mandatory throughout terminal uncertainty.
- [ ] Test same-byte source/control/root replacement and nested writes at manifest,
      file/move/directory callbacks; test pre-enrollment alternate bootstrap handles.
      Exact subset CANCELLED and RELEASED both validate current sources; ALL_BUNDLE
      cancellation admits legitimate PREPARED with incomplete optional pruning data.
- [ ] Test cold committed CLOSED audits with missing retired sources and no external
      manifest beside a committed prune.intent; execute/replay twice, preserving
      exact audit bytes. Attempted operation/read/callback resume must always reject.
- [ ] Run `bash verification/history_operation_recovery/run.sh terminal` and affected
      pin/executor runners; require all PASS before critical helper review/commit.

### Task3: Canonical append recovery and conservation

Files: create `HistoryPinTerminalRecovery.java`; narrow `HistoryPins.java`;
`ClosePrefixCheck.java`, `OperationRestartProbe.java`, `run_restart.py`, README.

- [ ] Write actual-file RED fixtures for every prefix length0..125 of both canonical
      outcomes. For n<=93 require explicit CANCEL_UNBOUND; for n>=94 preserve the
      outcome. Assert RELEASED without/wrong manifest leaves every byte unchanged.
- [ ] Implement existing-inode append/readback/force/promotion with explicit recovery
      choice and exact capability, preserving126 total bytes and one slot throughout.
- [ ] Test full126 read-only promotion; >126/nonprefix/bad checksum/symlink/directory/
      duplicate/conflicting stages; partial BEGIN of every representative shape.
      All rejected evidence remains byte/inode-identical and fully charged.
- [ ] Test stage disappears between validation/open: no file recreated. Inject
      same-byte replacement, callback mutation, partial append, file/move/directory
      sync failure, full shared byte/slot allowance and ordinary control writers
      attempting to consume reserved suffix credit. No extra files or unlinks.
- [ ] Real child JVM SIGKILL before/after append, outcome-byte persistence, suffix
      force, move, directory sync and acknowledgement; actual OS write-size failure
      at multiple suffix lengths. Reopen twice per fixture with consumers initially
      frozen. Check exact original prefix/inode, final outcome, all native bytes,
      reservations, audit bytes and refusal to resume retired operation keys.
- [ ] Run scoped runner `all`, affected pins/capacity/maintenance/executor regressions
      once on final source, and constrained-heap manifest checks. Independent exact
      source review precedes canonical commits; parent owns the clean full host/two
      unsigned-build gate. Host SIGKILL is not sudden-power-loss or Android proof.

## Questions for independent review

1. Does tracked thread-confined I/O plus scheduler-owned callback tickets cover all
   future consumer escape paths? This plan deliberately supplies no runtime adapter;
   one that exposes raw delegates or starts work before admission must be rejected.
2. Does the proposed audit batch preflight preserve the cold CLOSED+intent path and
   exact retirement helper without waiting under the common monitor or accepting
   changed pairs? Confirm every terminal entry point, not merely public close.
3. Is direct existing-channel append/force plus the stated callback barriers enough
   for host identity assurance on supported filesystems? Multi-process mutation is
   outside the existing single-process protocol; do not imply an inter-process lock.
4. Confirm permanent block is acceptable for uncloseable participant resources,
   corrupt sources and partial BEGIN. These are explicit availability limits, not
   reasons to bypass quiescence or invent provenance/compaction authority.

No product permission question or runtime enablement is implied. The next action
is independent review of this plan, followed only by explicitly assigned bounded
implementation. Current backend partial-stage blocks remain unchanged.
