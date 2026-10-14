# ADR-001: Preserve both versions before a fallible provider save

- Date: 2026-10-14
- Status: recoverability direction selected under owner delegation; detailed
  implementation and quota recommendations remain for review; no gate clearance
- Scope: document save boundary and its recovery contract; M1 preparation
- References: [roadmap](../ROADMAP.md), [release gates](../RELEASE_GATES.md),
  [provider probes](../../verification/document_io/README.md)

## Intent and current evidence

Protect the user's existing document bytes and the exact edited version submitted
for save before any potentially destructive provider operation. Keep user-owned
Markdown canonical, offline and accessed through scoped SAF. This design does
not introduce a new editor, workspace, cloud service or release.

The owner delegated the reliability choice on 2026-10-14: “I have no idea just
do what's best and most reliable”. The architect selects durable private
protection of both versions, mandatory before every provider mutation, while
retaining generic SAF save usability. Protection failure blocks the save.
Recovery is explicit restore-to-new-copy; there is no automatic rollback.
This guarantees recoverable protected bytes within the storage assumptions
below, not an unchanged canonical URI after provider failure. The delegation
does not waive physical-device gates or M0-to-M1 sequencing prerequisites.

The protected baseline is `v2.2.5-baseline`, merged at `d1288a8`, version
2.2.5/code18. Its `DocumentIO.write` opens `rwt`, falling back to `wt` when
descriptor acquisition fails or returns null. Either provider path can truncate
before completion. `MainActivity.lambda$saveInternal$35` checks the preflight
hash, writes, then reads back exact bytes. Verification detects failure after
mutation; it cannot undo truncation or ensure exclusive ownership of the URI.

Baseline recovery uses an app-private atomic envelope, but save does not require
that envelope to contain the attempted edit before writing. Recovery/session
debounces are 700/900 ms. `lambda$restoreSessionOrRecovery$42` also skips reading
recovery when session parsing throws. These are separate integration defects.

At design preparation, the provider safety probes expose five failures: two
oversized writes, a null descriptor classified writable, and two destructive
partial-write cases. A separate nested-list rendering safety probe is red.
This ADR supplies no test execution evidence and does not close those failures.

## Immediate fixes are separate from history

The bounded `DocumentIO` fixes need no new history format:

1. Reject encoded payloads above `MAX_DOCUMENT_BYTES` before any resolver call.
   Count UTF-8 bytes including BOM, not Java characters. Exact-limit writes must
   remain supported; reject ASCII and multibyte/BOM oversize payloads unchanged.
2. A null fallback `rw` descriptor must not set `Metadata.writable` true.
   Retain existing successful-descriptor and explicit-capability behavior.

These changes should first demonstrate failing desired-behavior tests, then pass
them alongside baseline-preservation tests. They do not fix partial writes.
Keep the low-level destructive-write probes visible; do not turn them green by
weakening assertions or substituting a successful recovery claim.

## Alternatives and selected direction

| Approach | Benefit | Limit / ruling |
| --- | --- | --- |
| Require a provider transaction or atomic replace | Can keep the canonical file unchanged on failure when the provider actually guarantees it | Generic SAF does not promise this. Rename/delete flags are not transactional replacement. Requiring it for all overwrite operations would restrict existing SAF usability; not selected as the common boundary. |
| Preserve both versions privately before the existing provider write | Works across current fallible provider paths; permits recovery even when the canonical file is truncated | Selected under owner delegation. Costs space and latency; does not guarantee the URI remains intact or eliminate external-write races. |
| Write, then attempt automatic rollback | Appears to repair common failures | Rejected: rollback is another fallible write and may overwrite an external edit. A recovery draft alone also omits the original version. |

Use the second approach as the common boundary. A provider-specific atomic path
may later strengthen it only with demonstrated capabilities and provider tests.
Never infer atomicity from `rwt`, successful `flush`, file descriptor `sync`, a
rename capability flag or successful read-back.

## Proposed transaction

Keep one small coordinator on the existing single I/O executor. `MainActivity`
captures UI state on the UI thread and submits an immutable request containing
tab/document ID, URI, captured edit revision, text/encoded bytes, baseline hash,
BOM, selection and operation type. Do not read mutable widgets from the worker.
“Latest edits” means the exact revision captured by this save request. Edits made
after capture remain dirty and require their own recovery; no claim extends to
keystrokes that have not crossed a persistence boundary.

1. **Validate request.** Enforce byte limits before provider mutation; bind the
   request and any explicit overwrite consent to a document ID and revision.
2. **Persist attempted edits.** On the I/O executor, synchronously commit and
   validate an immutable edited-version envelope. This must finish successfully
   inside the save operation, not merely enqueue the debounced recovery task.
   A failure aborts save without opening a destructive provider stream.
3. **Read and check source.** Read the current provider bytes without mutation.
   For an ordinary save, require the current hash to match the captured baseline.
   A missing baseline on an existing URI is a conflict/unknown-origin condition,
   not permission to overwrite. Read failure, revocation or unsupported input
   leaves the provider untouched and retains the edited envelope.
4. **Resolve conflicts explicitly.** Retain the edited envelope when hashes
   differ. Existing overwrite/Save As/cancel choices remain explicit. Overwrite
   consent must name the observed source hash; if it changes again before the
   authorized attempt, require another conflict decision. Do not reuse a generic
   force boolean as unlimited authorization across later source changes.
5. **Protect the observed original.** Commit and validate an immutable envelope
   of the exact current source bytes, including BOM/line endings. Never use the
   editor text or an older cached baseline as the original. The initial scope
   may fail closed on unreadable, invalid-UTF-8 or oversized source; do not add
   lossy decoding to get past this step. Any future raw-byte recovery path needs
   its own bounded-read tests.
6. **Commit preparation.** Persist an immutable prepared-transaction record
   referencing both envelope IDs, lengths and hashes, the expected source hash,
   request revision and operation type. Reserve storage before mutation. A
   missing, corrupt or uncommitted envelope means the transaction is unprepared.
7. **Recheck and write.** Re-read/hash the source after preparation; abort on
   change. Then invoke the fallible low-level write once. This reduces the
   preflight race but cannot close the final check-to-write window without a
   provider compare-and-swap/transaction API. Retain both envelopes throughout.
8. **Verify and report.** Read back exact bytes. Only a match permits publishing
   success and a new baseline for the captured revision. Later editor revisions
   remain dirty. Write/flush/close/read-back failure is an uncertain provider
   outcome; retain both versions, never retry automatically, and report that
   the provider document may be incomplete. If recording a verified outcome
   fails, retain an unresolved prepared record and report uncertainty rather
   than clearing the recovery evidence.

A newly created Save As URI follows the same protection boundary: retain the
attempted edit and inspect/protect the returned destination bytes, even if empty.
Picker creation itself may already have created an empty file. Do not delete it
automatically on failure, and do not discard the tab's previous source identity
until the new save verifies. This requires a tested pending-destination model;
the current result handler changes `currentUri` before verification.

## Storage and durability contract

Store envelopes under an app-private directory with generated IDs, never paths
derived from titles or URIs. Each versioned envelope contains document/transaction
identity, role (original or attempted edit), exact bytes, byte count, SHA-256 and
recovery metadata. A self-contained envelope can use a length-delimited binary
payload or encoded bytes; select the representation in the implementation plan
and account for its full serialized space cost. Hashes detect corruption, not
malicious tampering. Avoid content deduplication in the first slice.

Write a unique staging file, flush and sync it, atomically move it within the
same private directory, then ensure the directory entry is durable. Validate
the committed envelope before treating it as protection. The existing
`AtomicCommit` is a useful starting point, but its rename alone is not evidence
of directory durability. The Android adapter needs explicit, tested file and
directory sync semantics; unsupported or failed durability operations fail
closed before provider mutation. Host fault injection proves ordering, while
actual Android process-kill/storage behavior remains a device gate. No storage
API establishes immunity to all hardware failure.

Version envelopes and prepared records are immutable. Record outcomes as new
records; a missing completion record remains unresolved. Startup enumerates
prepared records and validates referenced envelopes independently of mutable
indexes and session JSON. Orphan envelopes from interrupted preparation must be
discoverable and protected until safely reconciled. Never overwrite the single
legacy recovery slot with the only copy of another tab's pending transaction.

Keep existing session and recovery formats readable. Load session, legacy
recovery and transaction recovery independently so one parse error cannot hide
the others. Reconcile by document identity and explicit revision/provenance;
timestamps alone must not silently replace a different recovered version.
Do not change 12-tab semantics or lose additional recovered transactions when
all tabs are occupied; pending recoveries remain available without eviction.

## Failure recovery and retention

Offer explicit restore-to-new-copy of either original or attempted edit after
integrity validation. Use the document picker, verify the new copy, and retain
the protected records until recovery has been acknowledged. Replacing the
original later is a separate explicit operation with fresh conflict checks and
the same protection boundary. Do not automatically restore over the provider,
silently clear unresolved transactions, or describe a partial provider write as
an unchanged original.

Pruning must never remove unresolved transactions, their referenced envelopes,
the only recoverable copy, or named checkpoints without an explicit deletion
decision. Reserve space using actual serialized sizes, both versions, temporary
files and commit records. Space checks are advisory; actual write/sync failures
must still abort safely. Repeated save failures must not evict earlier recovery
evidence merely to make the next attempt fit.

The owner delegated policy judgment; another owner question is not required to
prepare the implementation. The following conservative values are architect
recommendations for later reviewed implementation, pending measured storage
overhead and save latency. They are not implemented defaults or accepted
performance budgets:

| Decision | Alternatives | Proposed direction for review |
| --- | --- | --- |
| Retention quota | Fixed byte quota; device-free-space fraction with a cap; user-managed unlimited growth | Propose a configurable 64 MiB global quota for persisted envelopes/records, with staging space and a measured free-space reserve checked separately. Quota includes protected records; it never authorizes deleting them. |
| Successful-version retention | Count per document; age plus count; quota-driven oldest automatic versions | Propose at most 20 automatic successful versions per document, subject to the global quota. Prune oldest eligible versions first. Preserve at least the last recoverable version; named checkpoints and unresolved transactions are exempt from count-based pruning. |
| Quota exhausted | Stop overwrite; prune eligible automatic history then retry; bypass protection | Prune only eligible automatic history, then stop overwrite if protection cannot commit. Offer explicit user-managed export/deletion; never silently delete protected records or offer an unprotected overwrite bypass. |
| Automatic history frequency | Every verified save; elapsed time; meaningful-content change | Initially retain successful content changes, bounded by the proposed quotas. Every save attempt still synchronously protects both required versions before mutation, even when optional automatic history is disabled. |
| Required guarantee for generic providers | Recoverable original bytes; canonical URI unchanged on any failure | Selected: byte-exact recoverability of the observed original and submitted edit, within the stated storage assumptions. Canonical URI immutability is not promised. |

Protection is mandatory synchronous work on the I/O executor, not an optional
background optimization. Measure its worst-case space and latency on the matched
M0 fixtures, including two near-limit versions and envelope overhead. A 64 MiB
quota may hold substantially fewer than 20 large versions. Do not reduce byte
integrity, sync ordering or failure protection to meet a timing target; revise
the recommendation with measured evidence instead.

Backup/export of app-private history remains separate from user-owned document
backup. Honor the existing privacy/backup configuration; adding snapshots is not
authorization to enable platform/cloud backup. App-private files remain subject
to uninstall/app-data clearing and device loss. Do not log document content,
private URIs, signing material or recovery bundles, or put them in Git/CI.

## Required acceptance cases, not observed passes

- Exact-limit/BOM/Unicode fidelity and oversized write rejected before mutation;
  null descriptor does not fabricate writability.
- Every protection failure (quota, permission, write, sync, rename, validation,
  directory durability) prevents any destructive provider open.
- Process termination injected at each transaction boundary recovers intact
  original/edited envelopes or proves provider mutation never started.
- Preflight conflict and another change after consent perform zero writes;
  document the remaining race with a provider changing after the final check.
- Zero-byte and partial writes, flush/close errors, mismatched read-back,
  revoked read-back and repeated retries preserve both protected versions.
  Assert no automatic rollback and no false save success.
- Edits during save, tab switching/closing, Save As cancellation/failure, stale
  callbacks and process restart keep identity/revision and dirty state correct.
- Malformed session with valid recovery, inverse failure, legacy migration,
  corrupt envelope/hash, missing outcome and full 12-tab state recover safely.
- Pruning never deletes protected/named/sole-recoverable versions; interrupted
  pruning and out-of-space retry preserve previous recovery evidence.
- Explicit restore-to-new-copy leaves the original provider URI untouched and
  verifies the destination. Actual Android SAF/device/process-death cases remain
  separately recorded; host doubles do not establish device acceptance.

## Milestone and integration ruling

This is reviewable M1 design preparation while local M0 work continues. M0
foundation/benchmark/safety gates remain open; M1 runtime history integration
must wait for its prerequisite gates or an explicitly approved sequencing change.
No milestone, device/Fold, provider, upgrade or release acceptance is implied.

The delegated product choice resolves the intended guarantee as recoverable
original bytes, not an unchanged provider URI. The literal M1 exit wording,
“no original document destruction,” must be reconciled explicitly with this
decision in the controlling roadmap/release documentation before acceptance;
this ADR alone does not declare that gate passed or edit those documents.
The original-preservation low-level probes remain red for destructive generic
provider writes. Retain those probes and their assertion strength, and add
transaction-level tests proving byte-exact recovery of both versions, fail-closed
protection and no automatic rollback. Their distinct outcomes must remain
visible rather than replacing a red primitive result with a green transaction
claim. No sequencing exception or physical-gate waiver has been approved.

After the prerequisite gates and implementation review, implement in slices:
transaction-envelope storage/fault tests; coordinator/provider fault tests;
narrow MainActivity integration and independent recovery loading; then history
UI/retention. Preserve package/signing identity, versions until an authorized
release, the baseline fallback and existing modes/locks/assets. No replacement
key, signed APK, destructive migration or new backup behavior is part of this ADR.

## Implementation checkpoint (2026-10-14)

Minimum mandatory protection, hash/revision-bound coordinator, Activity save and
explicit-copy recovery are implemented and independently reviewed. Streaming v1
envelopes, lazy validated snapshot handles, entry/completion reservations and
bounded Unicode validation close the observed host failures. The current fixed
64MiB foundation quota is implemented; configurable retention/20-version history
and safe pruning remain proposed work, not completed defaults. Earlier sections
record the design-time baseline; current source handlers no longer adopt Save As
before verification or skip independent legacy-draft protection.

Actual production fixtures pass; unprotected primitive truncation probes remain
red and explicitly separate. Fold7 runtime, Android app-UID durability, provider,
power-loss and release checks remain NOT RUN. No milestone acceptance or source
identity/asset/signing change is implied. See STATUS.md and the implementation
plan for exact current counts and required external inputs.
