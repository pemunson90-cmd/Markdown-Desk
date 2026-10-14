# ADR-003: Historical parallel maintenance proposal — superseded accounting

- Date:2026-10-14. Status: historical parallel proposal, superseded for registration/accounting by the reviewed `HistoryCapacity`/ADR-002 contract. No runtime or release acceptance.
- Existing v1 policy/native records remain readable and byte-identical. New
  maintenance enrollment is explicit; constructors never upgrade registrations.
  Device durability and canonical signing/upgrade remain separate gates.

## Registration and accounting prerequisite

Append a checksummed v2 capacity registration binding a distinct direct-sibling
`history_maintenance` root. Preserve prior root names, initial constructor quota,
policy choices and hash chain. Reject aliases/conflicting registrations and
version regression. Every later policy update preserves that binding. Readers
that cannot understand it must fail closed, never drop its accounting/pins.

Do not reuse the strict64KiB/256-record policy ledger or untyped ordinary stages.
Maintenance has a separate1MiB/256-entry allowance for typed small intents,
stages and terminal receipts; it never funds document payloads. Report its actual
and reserved bytes/slots separately from normal charged history. Hold64KiB/one
slot for a future prune intent; future operation begin must also reserve its
bounded close receipt before acknowledging a durable pin. Physical ENOSPC/sync
failure still blocks publication, regardless of logical allowance.

Validate root linkage, including parent and maintenance-directory sync, on
initial enrollment and retries. The prior policy remains effective after
prepublication failure; uncertain publication remains explicit and blocked until
safe recovery. Unknown, malformed, nonregular or retained staged maintenance
produces diagnostics and blocks pruning. Enrollment/accounting implements no
pin closure or unlink permission by itself.

First required RED test: `maintenanceOptInPreservesV1AndCountsExistingStages`.
Keep hashes of every prior record, prepopulate bounded maintenance staging,
enroll explicitly, reopen twice and verify accounting/diagnostics and unchanged
prior bytes. Include mixed/aliased roots, count/byte caps, policy downgrade,
parent-sync fault/retry and fresh-process enrollment interruption cases.

## Durable operation pins, before execution

Planned API: `begin(kind, exactInputs, manifest)`, `inspect()`,
`close(id, manifestHash, terminalOutcome)`. This API is not implemented yet.
A bounded sorted manifest names exact root-role/UUID/type/serialized-length/hash
inputs and their complete reference closure. Begin must commit before asynchronous
restore/export consumes those inputs. Active pins survive process restart and
invalidate previous pruning previews; incomplete stages block pruning.

A terminal close must bind the original manifest and independently safe local
retention. Closed pairs are immutable audit data: validate their own framing and
close binding, but do not dereference retired source files after allowed pruning.
Initially retain closed records; bound exhaustion stays explicit. Export receipts
are not replacements for the required surviving local same-history anchor.

## Journal and logical projection, before any unlink

A future executor consumes only freshly validated bounded DeletePlans under the
same private-parent monitor. Require maintenance enrollment, exact intent
serialization <=64KiB/256members, and actual successful file/move/directory sync
before publishing deletion authorization. Identity, policy, maintenance and
operation-pin records are never ordinary deletion members.

The intent binds exact members, registered roots/effective policy, preview proof,
fixed same-history anchors and an unaffected-graph fingerprint. Its canonical
logical projection excludes the entire authorized closure and active intent,
even while some members remain physically present. Recompute transactions,
ownership, pins, checkpoints, reservations, aggregates and diagnostics from that
projection. Filtering only a list of physical file descriptors is insufficient.
Physical accounting continues counting every file until actual unlink.

On replay, validate framing/checksum/root binding/unique members, exact hashes
of every remaining member, all unaffected links/pins and fixed anchors, then
compare the unaffected projection. Only listed absent members are acceptable.
Use a private provisional projection during validation; expose a tombstone only
after the complete proof succeeds. Re-sync the committed intent directory before
mutation after restart. Native snapshot/transaction/reservation, catalog/index
and graph readers must consume that same validated logical view.

Freeze all mutations, including completion, policy changes and pin close, while
an active prune intent is pending. Unlink only authorized checkpoint/index,
completion, preparation and exclusively owned snapshot members. Sync affected
directories, verify every intended member absent, sync all affected directories,
remove the intent and sync maintenance. Any failure stops; replay never enlarges
the selection. Stage/intent corruption retains every surviving byte and blocks
mutation rather than guessing cleanup.

Required production SIGKILL/fault cases cover intent file/move/directory sync,
every member unlink, all affected-directory syncs and intent removal. Reopen
and replay twice, retain exact anchors and all unrelated/protected versions,
validate index/catalog/reservations, and demonstrate no manufactured unresolved
transaction. Intent ENOSPC before durable publication must perform zero unlinks.

## Current boundary

Quota policy, pure preview planning and affected-line syntax have independent
host evidence; maintenance, pins, executor and runtime controls are not completed.
A pure plan neither frees quota nor proves ordinary repeated saves are unblocked.
M1 acceptance needs actual integration/repeated8MiB-save/fault/recovery evidence.
Physical Android/SAF/IME/Fold/TalkBack/canonical upgrade remain NOT RUN.

## Reconciliation correction — 2026-10-14

The original proposal above assumed the alternate `history_capacity` protocol.
Independent review found concrete authority failures in that implementation; it
was replaced before runtime activation. Current authority uses `history_control`
plus durable parent enrollment evidence, with ONE shared 1MiB/256-entry allowance
for enrollment, registration, policy, future pins and maintenance. Do not add a
second allowance or restore alternate v2 registration as effective policy.

Durable pins, tombstone projection and validated replay remain required before
execution. The next bounded slices are an inert intent codec, constrained
maintenance recovery, then explicit backend execution. Complete intent framing
is capped at64KiB, registration/policy stay capped at16KiB, and generic policy
reconciliation must never promote a prune-intent stage. No unlinks are authorized
by the current source. Parallel maintenance commit `90c077b` is retained in Git
history for review; it is not integrated or accepted by this correction.
