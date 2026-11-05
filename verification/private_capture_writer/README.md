# Actual capture writer checks

Use configured JDK17 on PATH and run `bash verification/private_capture_writer/run.sh`.
The fixtures use actual registered stores, immutable assembled MDOP records and the
production writer, including final local BEGIN/LINK and typed process source handoff.
Activation, provider output, crash acceptance, Android and whole-heap proof remain deferred.

The first writer operation persists only exact PREPARE under one primary mutation
lease. Its internal output FD is opened, written, synced and actually closed inside
that lease. No FD lifetime crosses a source/provider callback outside the monitor.
Any publication failure seals the original source owner; another same-process
attempt cannot guess that a failed internal resource or publication is quiescent.
No generic reset/rewrite/partial-stage repair is provided. Cold recovery remains a
separate unimplemented producer requirement.

Admission compares fresh complete initial control and operation-ledger domains to
the original plan, including completed legacy pins, before binding physical baseline
anchors or creating a stage. Native source predicates remain separate from explicit
own PREPARE transitions and the full ledger. Stage bytes/identity are recorded before
file durability callbacks; promotion expects the original stage identity before its
move callback. Every durability callback is followed by fresh full transition and
native-source verification. Unknown additions, changed old evidence and same-byte
inode replacements give no acknowledged writer. Written failure evidence remains.

The current transition admits absent→full PREPARE.stage→committed PREPARE,
funded namespace/preparation directories, and complete native-map/descriptor
stages→committed metadata. Each owned byte and slot consumes exactly its prior
future credit; baseline data stays unchanged. Partial metadata writes on failure
are retained without a replacement writer or automatic repair. Private-frame
live-prefix copying and final BEGIN/LINK are described below. Never ignore an operation
prefix or accept whichever state a callback leaves behind.

The writer also guards the entire synchronous durability invocation with the
preparation participant marker. A callback cannot manufacture quiescence while
its internal FD remains open, even after concurrent admission freeze. Borrowed
FD/durability calls are synchronous; they grant no deferred stream ownership.
The publication wrapper may nest inside a counted trusted source consumer callback;
it does not reject that existing participant or hold the source-owner lock. A
future private sink must supply its own exact internal mutation token: this wrapper
alone grants no sink, credit, stage or promotion authority.

One refresh workspace is prepaid from the original shared metadata budget. Control
decode/DTO/clone space is conservatively reserved; each fresh ledger uses only a
bounded child counter representing that prepaid workspace. Fresh control/ledger
snapshots now live only in a private synchronous withRefresh/withChecked action.
One active wrapper binds the owner thread and exact mutation-scope identity; its
private accessors reject expired, foreign and stale-lease reads. Finally clears
both whole DTO references on decode/action success or failure before reuse. No
provider or durability callback runs while a refresh action is active. Transition
retains separately admitted physical/maps only; sourceProof returns only the typed
native physical proof. Refresh allowance and budget charges remain unchanged.
Compact physical baseline tables receive separate admission. These logical bounds
are not the pending full simultaneous heap/resource proof for the producer.

Current focused result:87 assertions. Tests cover actual PREPARE bytes, exact native
map/descriptor bytes and credit conversion, no accidental BEGIN, pending credits,
existing CLOSED capture, six durability failure points, same-byte PREPARE/native
replacement, old private-file metadata changes, foreign operation evidence, and the
completed legacy-pin change that leaves an absent operation-ledger hash unchanged.
Raw-provider destructive hazard diagnostics are separate and unmodified.

Every native-source proof/recheck now looks up the installed capacity identity and
requires the originally accepted instance, its ready flag and acknowledged policy
through `capturePolicyLocked`. The accessor requires the primary mutation lease;
it returns policy provenance only, without ordinary writer admission, reconciliation
or state mutation. Exact typed transitions still validate intentional PREPARE.stage.
Actual callback regressions cover a failed nested open installing an unacknowledged
replacement and a caught ordinary writer check revoking the same object's readiness.
Both refuse before PREPARE promotion and retain failure evidence; public policy()
semantics remain unchanged.

## Actual private-frame sink

`copyPrivate(expectedClosedFrame, immutableFactory, copyWorkspace)` is the package
orchestration entry outside the history monitor. It retains an exact private task,
sink and CopyAttempt before invoking any source factory/read/close or sink callback;
all failures seal the writer and original source owner AFTER leaving the consumer
participant marker. No caller can forget that sealing by manually choreographing
sink/close/promotion. `retryPrivateClose()` retries retained actual handles only and
never resets failure, resumes append or creates a result.

One active writer token binds the exact original plan row and expected frame,
canonical stage/final paths and creator thread. Its allocation charges8 KiB from
the same budget before allocation, separately from copy helper reservations. Sink
construction performs no IO. The first bounded write creates only its funded new
stage, retains the actual output object/descriptor and captures its nofollow inode.
Opening failure with an unknown handle remains uncertain, not fabricated closure.
The private token acquires the primary monitor/administration lease internally;
it does not call public participant-rejecting entries. Complete full-ledger,
installed-authority and native-source proofs still apply.

Each <=65,536-byte append verifies the old explicit prefix/physical identity and
all old/own entries. The expected new SHA-256 comes from rereading only that verified
old prefix then hashing the known incoming append BEFORE writing. It never adopts
a hash from whichever ledger appears afterward, depends on MessageDigest.clone or
accepts a partial-stage resume. The one64 KiB scratch buffer is included in the
writer's prepaid refresh workspace and child accounting. Direct output writes use
no extra payload buffer. Typed own-stage bytes consume exact future byte credit;
the stage slot consumes its pre-reserved slot. Final-stage promotion additionally
requires full prepared length/hash. Prefix rereads/full transition checks can have
quadratic IO cost; no latency guarantee is inferred.

The sink syncs only a matching complete stage, retaining its native physical proof
through source close. It actually closes its own output before the helper may close
the Reader. If sync/verification fails, physical output closure is still attempted;
throwing/uncertain closure keeps the original handle state for retry. Cleanup after
failure/freeze/authority loss uses the exact token and may close the FD without
ordinary admission. Source ownership stays live while sink closure is unresolved.

Only a successful CopiedFrame bound to this owner/original frame/sink plus actual
FD closure permits finalization under one lease. It freshly checks full stage bytes/
identity, source-close callback effects, control/data credits, installed policy and
the retained native proof, then performs only expected stage→final move+directory
sync with checks after every callback. This copy step alone produces no BEGIN or
captured result; final publication requires the separate commit operation below.

The runner includes7 new actual backend cases: exact preference copy/credit use,
changed valid regeneration, same-byte stage/native replacement during source close,
sink durability failure and authority loss with retained source/cleanup, and one
streamed8 MiB tab plus preferences. One representative8 MiB copy took1590ms on this
host during functional verification; that is an observation, not a benchmark budget.
Original87 P/native assertions still pass. Initial private-copy tests failed for
missing orchestration; the first zero-stage check caught truncating-open changing
mtime, so its expected zero envelope is recorded after the known same-inode open.
No broad suite/build/CI/device, crash fault matrix/full heap proof, cold recovery or
activation is included in this packet.


## Final BEGIN/LINK and source handoff

`commitCapture(registeredPins)` runs outside history, freezes the original Sources
owner, then claims its exact zero-resource/zero-running proof inside one primary
lease. All declared artifacts must already be final, with exact bytes/hash/inode,
and no live copy may remain. A fresh native physical proof stays open across BEGIN
and LINK durability. Every synchronous durability invocation rechecks the original
source claim, current installed capacity/policy and Pins identity, complete old/own
control+data expectations, physical anchors and native source bytes. No exclusion is
released between final proof and BEGIN. Any failure seals this writer and owner;
there is no same-process repair/adoption or result revival.

A privately constructed, creator-thread, lexically lease-bound one-use PreparedBegin
capability lets only the registered Pins publish this plan's exact204-byte BEGIN.
The Transition admits only that complete stage and its original-inode promotion.
The single retained-pin-stage diagnostic is allowed only alongside the exact named,
fully decoded204-byte/hash-bound owned BEGIN.stage; all other diagnostics or pins
fail. Its complete stage transfers126-byte/one-slot CLOSE credit to the pin inventory
exactly once; the remaining operation control credit is173-byte/one-slot LINK. LINK
then follows its exact stage→promotion+directory-sync protocol. Any pre-LINK CLOSE,
including a valid cancellation receipt introduced during a callback, prevents LINK
and completion. Existing domain2 activation rejection is preserved.

Before the first BEGIN write, the same original budget admits8 KiB for transient
capabilities/control buffers/scalar result, plus the complete replacement retained
operation-evidence maps:1024+2*Java-name-length bytes per committed operation-control
name and final data key, matching existing HistoryStorage accounting. Old evidence
remains live and unchanged during validation. After fresh durable LINK proof, the
writer remembers all exact complete old+new operation evidence before handoff, so
same-process deletion of the whole capture cannot revert to an older empty baseline.
These are conservative logical admissions, not a measured whole simultaneous heap
or GC bound.

Only then is a privately constructed scalar CapturedBinding created. It holds the
canonical root, preparation/operation IDs and P/BEGIN/D/LINK hashes; its transient
original claim reference is consumed during handoff and cleared before return. It
retains no writer, Graph, factories, payload or copy/codec buffers. Sources/Storage
remove only the exact registered old owner with its original claim and this valid
one-use binding, under the primary lease. Replaying the consumed binding cannot
clear a newly registered preparation owner. No activation/resource handle is created.

The11 completion cases cover native phase A and copied-private phase B success,
exact complete-BEGIN.stage/CLOSE and LINK credit conversion, BEGIN/LINK file-sync and committed-LINK directory-sync failures,
foreign Pins, missing artifacts, live source ownership, native/artifact same-byte
replacement, pre-LINK CLOSE, consumed handoff replay against a new owner, and complete
post-success capture disappearance against retained evidence. One child JVM freshly
reopens durable control+data and verifies the exact LINK and future CLOSE credit;
this is a durable inspection invariant, not cold recovery or crash-fault acceptance.

The runner also includes WriterSnapshotScopeCheck: reflected test-only access to
the private action verifies expired C/L rejection and cleared references, thread
and exact-lease guards, null/nested action rejection preserving the outer scope,
IOException/RuntimeException/Error cleanup, ordinary retry and no active snapshot
at actual native metadata durability callbacks. The test introduces no production
callback or getter. This closes writer lexical lifetime only; whole-path numeric
funding, portable P/activation, and terminal integration remain unproved/deferred.
