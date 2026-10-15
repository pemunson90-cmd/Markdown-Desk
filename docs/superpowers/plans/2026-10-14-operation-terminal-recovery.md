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
- `ParticipantContext`: private maintained thread-local nesting depth for the
  complete registered factory/I/O/close/callback wrapper stack. It is not a caller
  boolean and cannot be cleared by a consumer. Keep it active through proof/future
  signaling and every synchronous dependent continuation, even after resource/work
  counts reach zero. Every terminal entry rejects this context before touching
  control state; exact capabilities cannot override the rejection.

Proposed interfaces (synchronous validation may throw IOException; completion
stages report subsequent failures without blocking their caller):

```
HistoryOperations forBundle(store, catalog, identity, index)
OperationHandle activateAcknowledged(String operationId, InputStream originalManifest)
TrackedInput openReader(OperationHandle operation, ReaderFactory factory)
TrackedOutput openWriter(OperationHandle operation, WriterFactory factory)
CallbackTicket post(OperationHandle operation, CallbackQueue queue, Runnable callback)
boolean cancelPending(CallbackTicket ticket)
DrainRequest freeze(OperationHandle operation)
DrainRequest freezeForTerminal(String operationId)
CompletionStage<Quiescence> quiesce(DrainRequest request)
CompletionStage<AuditQuiescence> prepareClosedAudits()
// Internal synchronous fast path; requires history monitor and mutation lease:
AuditQuiescence tryClosedAuditsNowLocked()
```

`ReaderFactory.open()` returns InputStream; `WriterFactory.open()` returns
OutputStream; `CallbackQueue.post(Runnable wrapper)` schedules only the coordinator's
wrapper. These are internal seams for synthetic tests and later reviewed adapters,
not provider implementations in this slice. Delegate streams remain private to
tracked wrappers; no API returns an untracked delegate or a caller-closeable count.
The initial wrappers are thread-confined to their opening worker. A future adapter
requiring transfer must add a reviewed explicit ownership protocol. Generic
wrappers cannot prevent a factory/delegate from retaining raw references or
spawning unregistered work: factories, delegates and runtime adapters are a trusted,
independently reviewed ownership boundary. An adapter that lets such work escape
is not eligible to use this coordinator; no malicious-delegate confinement claim.

Lifecycle is COLD_FROZEN -> OPEN -> FREEZING -> QUIESCENT -> TERMINATING -> TERMINAL.
An attempt that fails after claiming TERMINATING enters TERMINAL_RETRY, still
frozen; no finally-unfreeze path. A private single-attempt claim binds the exact
quiescence key/nonce/epoch and prevents token reuse or concurrent terminal attempts.
`freezeForTerminal` can prepare a new drained attempt from TERMINAL_RETRY only
after the earlier attempt has exited; it cannot steal a live TERMINATING claim.
Successful terminal acknowledgement seals the key permanently.

Activation is an atomic competing transition. `activateAcknowledged` rejects a
caller already holding a mutation lease, acquires its own history monitor/lease,
validates the exact committed active BEGIN with no CLOSE or terminal stage and
captures expected gate state/epoch. After callback-capable manifest/source work it
freshly validates all disk/control/source evidence, then checks that same expected
gate state/epoch and advances epoch+OPEN under history->coordinator locking before
releasing the history monitor. No callback runs while the coordinator lock is held.
Only COLD_FROZEN or a QUIESCENT gate that has never claimed a terminal attempt can
activate. Every old handle/ticket/capability becomes invalid at the new epoch.

Terminal entry, under the history monitor and its single mutation lease, atomically
validates Quiescence and claims TERMINATING under the coordinator lock **before**
any manifest, durability or other callback-capable work. Reentrant activation
rejects its nested lease; competing activation cannot pass the history lock and
then reopen the gate. Keep the claim across the entire no-stage prepublication
window, all writes/syncs and final acknowledgement. A no-stage terminal failure
still blocks consumer reactivation in this slice; explicit terminal retry remains
available. Reopening such an attempted operation would need a separately specified
safe abandonment/new-epoch protocol, not an absence check. Fresh-process discovery
still starts frozen and revalidates durable evidence independently.
Before acquiring that monitor, terminal entry rejects ParticipantContext. The
guard remains active through the signaling epilogue of a last close/callback;
an inline thenApply cannot release a pin by observing that counts are now zero.
Nested wrappers preserve outer depth on every exception/rejection path, and the
outermost finally clears context only after all its signaling has finished.

Cold terminal recovery does not require an active consumer handle.
`freezeForTerminal(operationId)` rejects nested mutation-lease entry, briefly owns
the history monitor/lease, validates exact registered roots/bootstrap/enrollment,
committed BEGIN, operation UUID/hash, policy ancestry and complete control evidence,
then atomically binds/freezes its gate under history->coordinator locking. It
returns DrainRequest without entering OPEN or reading an original manifest. An
existing OPEN handle's participants remain counted and are drained normally; a
cold gate has accepted none. Partial/full matching CLOSE stages are eligible for
this discovery, as is a valid active BEGIN without a terminal stage. Partial BEGIN,
foreign/missing binding and conflicting evidence reject. This entry cannot append,
promote or acknowledge a receipt. CANCELLED recovery therefore works after loss of
the external manifest; RELEASED stage recovery still validates the original
persisted manifest at the terminal step. Already committed CLOSED pairs use the
separate audit-batch entry and can never activate.

Startup begins frozen **before any consumer is accepted**, including queued work
restored by a future runtime owner. Disk discovery may bind a gate but cannot open
it. `HistoryPins.resume` must no longer be an independent admission path: it either
returns a validated description through the coordinator or requires its operation
handle. Complete BEGIN promotion does not automatically activate consumers.

Admission reserves an opening ticket before calling a stream factory. An exception
from a generic factory does not prove that it left no resource or callback behind:
retain an indeterminate opening registration and freeze the operation. There is no
caller-asserted clear method; this conservative case may require fresh-process
recovery. A later concrete opener may add a reviewed ownership proof, not an
exception-type heuristic. Freeze closes admission immediately and rejects new
operation I/O; an already running call remains counted until it returns and the
owned stream actually closes. Freeze alone never decrements a live registration.

Wrappers cover every delegate-touching method, including all read/write overloads,
skip, available, mark/reset/markSupported, transferTo, flush and close; inherited
bulk helpers must route through guarded operations. Count an I/O call before
releasing the gate lock and never invoke a delegate while holding that lock.
Counted owner-close remains allowed after freeze so draining can finish; any
flush required to close remains part of that counted close, not fresh admission.
Closing I/O, flushes and delegate callbacks execute outside coordinator/history
locks. A close exception does not certify closure: retain the outstanding
registration unless closure is positively proved by the owned resource, and
otherwise remain blocked for explicit retry.

A posted callback registers **before** calling the scheduler. Its only scheduled
object is a coordinator wrapper, which atomically claims a pending ticket or
suppresses delivery after freeze/epoch change. Pending cancellation atomically
removes delivery authority and clears the callback reference; separately tracked
resources remain counted until closed. A callback already running remains counted
through its `finally`, even after cancellation. A scheduler exception cannot allow
an already queued wrapper to execute unregistered. No new callback can appear
through a reader's completion after freeze. No operation-delivery callback runs
under either lock; administrative manifest/durability callbacks retain the existing
history-lease contract and never run under the coordinator lock.

`quiesce` and `prepareClosedAudits` expose nonblocking completion only. There is no
production blocking await API. Count/state transitions mint proof only at actual
zero work and arrange completion after releasing both locks; no user continuation
runs under a lock as a result of coordinator-owned signaling. Completing a future
may run arbitrary synchronous continuations: retain ParticipantContext until
complete() and its inline continuations return. Participants may request freeze
and return to complete/close work. Completion does not itself release protection
or reopen admission.

Before minting proof, all registered operation-consumer/delegate work and owned
resource closure must have finished and counts/proof must be finalized. No such
source work, callback or delegate cleanup may remain in a later finally block.
Only coordinator metadata cleanup and proof/future signaling may remain. Signaling
can invoke arbitrary proof/terminal listeners; it is not a claim that complete()
runs no user code. Inline listeners retain ParticipantContext and cannot enter a
terminal path or resume old consumer admission. An independent asynchronous
terminal continuation may safely overlap this metadata-only signaling epilogue;
ThreadLocal does not order other threads or require literal Java wrapper return.
No shared signaling-frame count or additional pending-proof protocol is needed.

The outside-both-locks rule applies to every signal-producing entry, including
freeze, cancelPending, final participant decrement, close and callback completion.
Public participant/delegate entry and externally signal-producing entry reject
a caller already holding the history monitor or mutation lease before changing
counts or invoking a delegate. Thus last-close/cancellation cannot trigger a
registered future under a caller-owned outer synchronized block. Internal frozen
discovery may collect notices under its own coordination, but dispatches them only
after its entire lease and monitor scope has exited; it never assumes that leaving
an inner synchronized block releases an outer one. The no-signaling audit fast
path below is the explicit exception, not a general bypass.

Future owners must chain terminal work asynchronously on an independent terminal
executor, never block an operation/callback executor or wait while holding history
coordination. A test-only blocking observer runs on an independent controller
thread. Self-owner/callback checks cannot detect arbitrary scheduler dependency
cycles; neither the coordinator nor this plan claims deadlock freedom for arbitrary
shared executors. An adapter that calls get/join while occupying an executor needed
by registered work violates the required lifecycle. Timeout, observer cancellation
or interruption leaves the gate frozen, changes no participant count and grants no
capability; quiescence may be requested again explicitly. Stuck work may prevent
progress indefinitely.
The runtime owner must use asynchronous terminal continuation; terminal context
validation enforces rejection of inline participant completion. No compulsory
service-owned terminal thread is added. Synchronous already-quiescent zero-work
entry remains allowed from an independent context, preserving existing host APIs.
The coordinator cannot prevent an arbitrary caller from attaching a continuation
to an already completed CompletionStage while holding its own lock; terminal entry
must still reject nested lease/participant context, and adapters may not do this.

Lock order: never acquire history while holding the coordinator lock. Brief
history->coordinator validation and atomic activation/terminal claims are allowed,
with no callback or wait under the coordinator lock. Freeze/drain completion occurs
outside the history monitor and mutation lease. Terminal entry claims TERMINATING
as described above; final validation checks the exact claim again after the last
callback. Ordinary unrelated native writers retain existing quota admission;
only operation consumers are frozen.

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
Public ordinary open/reconcile may initiate coordinator preflight outside history
coordination. Synchronous legacy entry points may consume an already-completed
zero-work proof or return QUIESCENCE_PENDING; they never block waiting for it. An
explicit asynchronous owner obtains proof before calling capability-taking methods.
Locked helpers accept explicit capabilities and never initiate or wait for drain.

Caller-owned monitor compatibility is explicit. The reviewed HistoryController.open
at integration source `3bc070a2` holds the parent synchronized block around
HistoryCapacity.open/register. Those existing native/capacity-only paths do not
acknowledge operation closure and retain their current behavior; do not insert an
operation drain or future completion into them. Releasing an inner synchronized
scope cannot release this caller-owned outer monitor.

Asynchronous preflight rejects entry while the caller already holds the history
monitor or a mutation lease. `tryClosedAuditsNowLocked` is the sole synchronous
audit-proof fast path for an already coordinated caller: exact committed closed
keys, zero opening/open/in-flight/closing I/O and pending/running operation-consumer
callbacks, no later consumer/delegate work, caller outside ParticipantContext, and
sealed or safely cold-frozen gates only. Another thread's metadata-only signaling
epilogue may overlap this claim. It creates no future, invokes no listener,
waits for nothing and accepts no active or staged operation. Otherwise it returns
QUIESCENCE_PENDING without initiating drain. Any caller factory that needs nonzero
operation recovery must move asynchronous preflight before its outer synchronized
region, then reacquire and freshly validate before publishing its result. Root
owns that factory restructuring if needed; no Activity/runtime adapter is added.
Zero-work discovered inside an existing lease must use this no-signaling fast path
or defer the entire preflight to its external owner, never signal a future while
pretending that only the inner monitor was held.

Committed-audit reconciliation uses `AuditQuiescence`, never a blanket boolean.
`prepareClosedAudits()` rejects nested mutation-lease entry, briefly acquires its
own history monitor/lease to inspect/bind exact closed keys, releases both, freezes
those gates, and completes the batch only after
actual drain. This discovery must also work during same-process Retirement through
its exact capability/control-only validator; it must not call ordinary
requireOperational, requireReady or recursive readView paths that retirement blocks.
Reacquisition revalidates every key and retained pair; new/different/active/staged
pins reject. The same permit remains valid across the executor's exact intent
retirement because it binds operation keys, not a stale whole-control inventory.
Existing final control/directory identity checks still refresh after intent removal.
The executor's outer entry points must obtain this preflight outside their lease
and pass it into `reconcileMaintenanceAuditsLocked`; no nested public Pins.open.
The synchronous executor may consume an immediately completed batch (including cold
closed-only zero-work discovery); otherwise it returns QUIESCENCE_PENDING before
acquiring its mutation lease and requires an asynchronous caller retry with proof.
Already sealed keys can satisfy zero-work proof without resurrecting consumers.

Partial-stage algorithm:

1. Under the lease, atomically claim TERMINATING from exact Quiescence before
   callbacks; verify canonical roles/bootstrap/enrollment,
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
   terminal claim after all callbacks. Only then acknowledge release and seal key.
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
      epoch/nonce rejection, nonblocking completion while owner work remains,
      observer interrupt/timeout preserving freeze and native worker completion
      needing the released history monitor. Exercise a single-thread callback queue
      with independent terminal continuation: requesting freeze must return promptly
      so pending work can finish; no get/join on that queue.
- [ ] Exercise every delegate-touching wrapper method and owner-close after freeze;
      no count drops merely because admission closed. Test callbacks spawned by
      delegated close and queue-post failures through the documented trusted adapters.
- [ ] Register hostile nonasync thenApply listeners before final proof signaling:
      terminal entry must reject without changing evidence while ParticipantContext
      remains active. Cover factory, I/O, close, callback, exception and scheduler
      rejection paths; nested wrappers preserve depth and finally clears it only
      after signaling, with no context leak. Every coordinator-triggered completion
      listener must observe Thread.holdsLock false for both locks.
- [ ] Prove no registered consumer/delegate/source cleanup occurs after proof mint.
      Test same-queue async continuation after wrapper return as one schedule, and
      independent async terminal work overlapping only metadata signaling as another;
      neither may start before actual work and owned closure finish. Async dispatch
      rejection leaves the operation frozen and produces no terminal receipt.
- [ ] With a future already registered, attempt final close, cancelPending and
      freeze from a caller-owned history monitor/lease: reject before delegate/count
      change and never invoke listeners under the outer lock. Exercise internal
      frozen-discovery notice dispatch only after its entire coordination exits.
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
- [ ] Test frozen cold discovery for partial/full CLOSE.stage without any consumer
      activation. CANCELLED with missing manifest must obtain DrainRequest and recover;
      RELEASED still rejects without its original manifest. Partial BEGIN stays blocked.
- [ ] Test activation racing a held terminal manifest callback and final proof, plus
      nested activation from manifest/file/move/directory callbacks. No OPEN transition
      is possible after TERMINATING claim, including a failure before stage creation;
      terminal retry succeeds while consumer activation remains blocked. Also race an
      activation that wins first: old Quiescence must fail before publication.
- [ ] Implement close/recovery capability consumption and audit preflight outside
      locks, with fresh callback-free checks under the existing lease. Keep active
      pin graph/planner freeze mandatory throughout terminal uncertainty.
- [ ] Test same-byte source/control/root replacement and nested writes at manifest,
      file/move/directory callbacks; test pre-enrollment alternate bootstrap handles.
      Exact subset CANCELLED and RELEASED both validate current sources; ALL_BUNDLE
      cancellation admits legitimate PREPARED with incomplete optional pruning data.
- [ ] Test cold committed CLOSED audits with missing retired sources and no external
      manifest beside a committed prune.intent; execute/replay twice, preserving
      exact audit bytes. Repeat during same-process Retirement after intent removal;
      audit preflight must use the exact retirement path without ordinary readiness.
      Attempted operation/read/callback resume must always reject.
- [ ] Exercise the caller-owned monitor shape of HistoryController.open: unchanged
      native/capacity paths require no drain; exact closed-only zero-work audit uses
      tryClosedAuditsNowLocked without future/listener creation. Nonzero recovery
      returns QUIESCENCE_PENDING and requires external preflight before reentry.
      Check lock order and fresh validation after reacquisition; a zero-work result
      found inside a lease must never inline-complete a future there.
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

1. Are the trusted adapter ownership requirements explicit enough to reject raw
   delegate escape, unregistered spawned work and blocking shared-executor waits?
   Generic wrappers cannot enforce correctness inside arbitrary factories/delegates;
   this plan deliberately supplies no runtime adapter or general deadlock guarantee.
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

## Plan-review refinements

Review of `dbd6e19` identified missing cold-stage quiescence discovery and an
activation/terminal race before stage publication. This revision adds frozen
`freezeForTerminal`, atomic final activation and TERMINATING ownership, and
mandatory nonblocking quiescence/audit completion. It also makes delegated method
coverage, owner-close, trusted adapter limits and Retirement-aware audit discovery
explicit. These are design corrections only; no implementation or behavioral test
result is claimed. Review of `82f42ba` additionally requires maintained participant
context through future signaling, all-entry enforcement of completion outside both
locks, and the caller-owned monitor/no-signaling audit split. The clarified root
ruling permits independent terminal work during metadata-only signaling after all
operation-consumer work and owned closure finish; it does not require wrapper return
or a shared frame counter. Inline proof listeners remain guarded even though they
can run arbitrary code. Final technical plan approval is pending the revised commit.
