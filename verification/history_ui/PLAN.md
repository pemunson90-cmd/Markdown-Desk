# Functional document history implementation plan

> For agentic workers: use `superpowers:executing-plans` task by task after root's
> technical review. Initial ownership is this plan/verification directory only.

**Goal:** Expose real per-document history, named captured revisions and bounded
comparison, and record optional ownership after verified saves.
**Architecture:** A pure Java `HistoryController` coordinates the existing native
backends on the Activity's single I/O executor. Main-thread captures and callbacks
bind immutable tab/URI/revision/request identities. Mandatory provider saves keep
`SaveCoordinator`; optional metadata errors have a separate result channel.
**Tech stack:** Existing Java 11/JDK 17, Android minSdk 26/targetSdk 35, native files,
existing executors and dialogs; no dependency or native-format change.
**Spec:** `docs/superpowers/plans/2026-10-14-history-retention.md`, Task 6;
`docs/decisions/ADR-002-retention-journal.md`; `docs/RELEASE_GATES.md`.

Inspected base: `e589563` (operation foundations). Parent subsequently reports
PR #18 merged as `31d4309` after required hosted run 38651804113 passed. Do not
interpret source integration as physical M0 completion or a release waiver.

## Source facts and intended boundaries

| Existing source/hook | Consequence |
| --- | --- |
| `MainActivity.getProtectionStore()` (2069) constructs only `protected_saves` lazily. | Preserve this raw **read** accessor; add a separate initialized controller accessor for mandatory writes. Do not make the raw accessor call the controller factory. |
| `lambda$restoreSessionOrRecovery$42` independently reads session and recovery; `protectLegacyRecovery` can call `putSnapshot`. | History startup failure must not hide recovery text/session or clear recovery. Require ready shared authority before the recovery protection write. |
| `saveInternal(boolean, Uri)` captures tab/text, strict bytes and `++capturedSaveRevision`; `resumeConflictSave` resubmits the same request with consent. | Capture source URI/tab identity at the same boundary and preserve that route across consent. The counter is an action revision, not a persisted content generation. |
| `lambda$saveInternal$35` calls coordinator, reads provider metadata, then posts `lambda$saveInternal$33`. | Place optional history indexing only after actual coordinator success, with its own catch. Never route its exception to `lambda$saveInternal$34`. |
| `lambda$saveInternal$33` adopts destination URI and preserves newer edits using captured text equality. | Do not derive Save As origin from the post-save tab; do not change these dirty/baseline/continuation semantics. |
| `launchSaveAs`/`onActivityResult` bind picker to tab ID; cancellation clears pending fields; successful selection captures the then-current revision. | No identity fork on picker launch, cancellation, failed save, or stale/closed picker tab. |
| `HistoryIdentity.resolve(uri,tabId)` uses exact URI or unsaved TAB namespace; `forkAfterVerifiedSaveAs(oldId,destinationUri)` retains an existing destination identity and appends provenance. | Reopen joins exact URI across different tab IDs. Different URI/equal content stays separate. Save As links origin; it does not move/merge old versions. No new history-ID field in `DocumentTab`/session format. |
| `HistoryIndex.recordVerified` rereads actual completion/source records. The coordinator result's transaction handle is not itself verified authority. | Pass only the returned transaction ID after success. Actual native completion remains authoritative if optional ownership publication fails. |
| `HistoryIdentity.Descriptor.matches` uses URI without a `legacy-recovery` exclusion. | `HistoryController.list` and preview membership must explicitly exclude `documentId == "legacy-recovery"`; global Protected saves remains available for those drafts. Do not silently merge legacy recovery into document history. |
| `HistoryCatalog.name(snapshotId,label)` names existing bytes, checks strict nonblank 256-byte labels, and is independently durable. | Captured naming writes a new attempted snapshot, then its checkpoint. A failure between them retains a protected orphan; report that exact outcome, never erase it. |
| `HistoryDiff.compare` validates both complete snapshot byte arrays sequentially; caps decoded preview at 256 KiB/10,000 lines per side, 1,000,000 work units, 2,000 output lines. | Reuse bounds and immutable `Preview`. Do not claim zero payload allocation: one full <=8 MiB snapshot byte array can be loaded per side. No large string is allocated for over-cap input. |
| `showProtectedSaves`/`launchProtectedCopy`/`restoreProtectedCopy` already offer global restore through the protected coordinator. | Preserve this escape path. Per-document rows can call the existing ID-based picker; no restore implementation rewrite or new replace action. |

## Startup and shared-capacity lifecycle

Proposed pure factory: `HistoryController.open(ProtectionStore store,
ProtectionStore.Durability durability): HistoryController`. It directly constructs
`HistoryCatalog(parent/history_catalog, store, durability)`,
`HistoryIdentity(parent/history_identity, durability)` and
`HistoryIndex(store,catalog,identity,parent/history_index,durability)` once. It
uses the supplied actual `protected_saves` handle, never an Activity callback.

Under the common parent monitor, inspect `HistoryStorage.controlled(storeRoot)`.
If there is any durable enrollment/control footprint, call `HistoryCapacity.open`
for that exact bundle; never register fresh or fall back on failure. Only genuine
absence permits `HistoryCapacity.register(...,new HistoryPolicy(64 MiB,20),...)`.
Publish the controller only after registration/open reconciliation returns ready.
Constructors in controlled mode are read-only; existing corrupt/missing control,
partial pins and pending maintenance remain explicit blocked states. No executor
replay, terminal-pin repair, control deletion or best-effort policy reset here.

Activity keeps `getProtectionStore()` for read-only global inspection. New
`getHistoryController()` calls that accessor once, then the pure factory; there
is no reverse call or constructor/open cycle. All three mandatory write paths
(save, protected-copy restore, legacy recovery snapshot creation) obtain the
store from the successfully initialized controller. Initialization runs on `io`
before the write; no filesystem work runs on the main thread. A failed open may
leave read-only handles/evidence but never a published writable controller.
Recovery loading remains independently usable and reports protection failure.
Initialization failure blocks provider mutation explicitly; optional history
metadata corruption on an already controlled bundle is accounted by the existing
capacity ledger and can fail listing/indexing without turning a verified save
into failure. No legacy fallback is introduced after initialization failure.

## Controller API and concrete behavior

New `HistoryController.java`; no backend wire changes or generalized framework.
The following are proposed new interfaces, not claims that they already exist:

- `ProtectionStore protectionStore()` returns the initialized mandatory store;
  native mutation APIs still recheck live shared authority on every write.
- `String resolveDocument(String exactUri,String tabId)` delegates to identity
  resolution on `io`. No hash/title-based inference. Do not resolve on every edit.
- `List<HistoryIndex.Version> list(String historyId)` validates the full backend
  list, then returns an immutable per-document list excluding legacy recovery.
  Rows expose actual pending/orphan/unindexed/indexed status and names.
- `CapturedRevision(String tabId,String historyId,String exactUri,String title,
  long revision,byte[] strictUtf8)` freezes defensive bytes (<=8,388,608 including
  BOM) and validates strict UTF-8, nonnegative revision and metadata ranges.
  Activity obtains bytes with actual `DocumentIO.encodeStrict(text,bom)` on main
  at confirmation time. Never retain a live Editable/View or read the current
  tab from the I/O task.
- `NameResult nameCapturedRevision(CapturedRevision capture,String label)`
  validates the label before writing, revalidates exact identity/URI-or-tab binding,
  and holds the parent monitor through `putSnapshot(...,"attempted")` then
  `catalog.name`. Each native method owns its own mutation lease; no enclosing
  mutation lease that would make these calls fail as nested mutations.
  `NameResult` contains immutable snapshot/checkpoint IDs and a label-failure
  diagnostic if snapshot protection succeeded but naming failed. Failure before
  a known committed snapshot throws IOException. Never clear tab dirty/recovery,
  manufacture a successful transaction, or delete incomplete named captures.
  These standalone snapshots remain protected orphans plus optional catalog pins;
  standalone retention ownership is a later task.
- `HistoryDiff.Preview preview(String historyId,String leftId,String rightId)`
  requires both IDs to belong to that history's validated non-legacy list, then
  compares freshly validated snapshots while holding the common parent monitor
  and an enclosing `store.withValidatedSources` generation guard. Return only
  immutable Preview, never Snapshot handles, lazy streams or a deferred closure.
- `IndexResult recordVerifiedSave(SaveRoute route,String transactionId)` resolves
  the captured source identity only after successful coordinator completion. For
  a changed destination URI, call `forkAfterVerifiedSaveAs`; otherwise resolve
  the destination normally. Then call `index.recordVerified`. Capture failures
  as optional diagnostic outcomes; preserve the provider save result. `SaveRoute`
  contains captured tab ID, source URI, destination URI and revision, not current UI state.
  Verify actual completed transaction and matching destination/tab/revision before
  any resolve/fork mutation; reject unverified/foreign records without alias writes.
  Existing destination identity survives; unsaved origin keeps its TAB identity.

All controller methods perform bounded existing backend work synchronously on
`io`; the Activity owns asynchronous scheduling, request tokens and delivery.
No new executor, retry loop, automatic pruning, policy control, export or import.

## Why a nonresumable diff need not acquire a durable operation pin

The controller holds the exact common parent monitor from membership validation
through both payload reads and the complete comparison. Existing writers and the
pending selected executor use that monitor. `withValidatedSources` checks the
storage generation before return; reentrant mutation from a durability callback
must invalidate the result. The controller invokes no caller callback inside
this critical section. A process death ends comparison and releases all handles;
there is no future resume or provider write whose sources must survive it.

This reasoning is **single-process cooperative coordination**, consistent with
`HistoryStorage`; it is not a cross-process file lock or protection from arbitrary
private-file tampering. Native read/hash checks still fail changed sources. A
bounded but nontrivial comparison can delay mandatory saves on `io`; measure if
new evidence requires scheduling work, do not invent a numeric device budget.
Independent review must challenge both reentrant mutation and the real selected
executor's monitor use before treating this as accepted runtime behavior.

## Narrow UI delivery plan (after controller review)

Add one Workspace action, “History for this document”, beside the unchanged global
Protected saves action. Resolve the captured exact URI/tab identity on `io`.
Present native versions and names, capture a named current revision, select two
versions for comparison, and reuse existing restore-to-new-copy by snapshot ID.
Use bounded pages of 100 rows with explicit previous/next controls and total count;
never silently omit history or allocate labels for the whole inventory at once.
Diff displays `DisplayLine.display()` and explicit incomplete reason/size/BOM
information. No HTML injection or new document mutation in comparison.

Capture a monotonically increasing UI request token plus tab ID and exact URI.
Reject delivery after Activity shutdown, newer request, tab close, URI reassignment
or tab switch; verify membership again before diff/restore dispatch. Naming may
finish for the captured revision after subsequent edits, but never replaces text,
marks the new revision clean or dismisses a newer dialog. Bind a positive naming
confirmation to its original tab/URI; cancel a stale confirmation before capture.
Do not show a late optional indexing error on a different document; retain it in
the originating action result and keep global protected inspection reachable.

SaveRoute must survive `resumeConflictSave(request.withConsent(...))`. The optional
indexing catch must surround only history work and must not absorb coordinator
failures. Preserve existing success callback text-equality/newer-edits behavior.
The worker catch also surrounds provider metadata lookup, but actual
`DocumentIO.metadata` catches ordinary query/close/probe exceptions and returns
fallback metadata. There is no reproduced post-success metadata failure here;
do not invent one with a throwing stub or change that boundary speculatively.

## Review focus and executable acceptance

- [ ] **Controller tests first:** create `HistoryControllerCheck.java` and `run.sh`
  in this directory, compiling actual pure native sources with `--release 11`.
  Real temp roots/registration/snapshots/coordinator/provider bytes; no backend
  substitutes. Assert URI reopen across tab IDs, equal bytes/different URI,
  unsaved TAB isolation, legacy recovery exclusion, immutable results, names and
  byte-identical BOM/CRLF/whitespace captures. Test exact/cap+1 labels/bytes and
  malformed UTF-8 before publication; label publication failure retains snapshot.
- [ ] **Startup/process tests:** fresh legacy initialization and exact four roles;
  fresh-JVM controlled reopen before mandatory write; changed/missing registration,
  root mismatch, retained control/pin stage and maintenance block mutation without
  fallback. Optional semantic corruption under registered complete accounting must
  leave mandatory verified saves successful and history diagnostics explicit.
  Keep session/recovery readable when startup authority cannot be established.
- [ ] **Verified-save routing tests:** drive actual coordinator success/failure,
  conflict/cancel, same-URI save, unsaved Save As, existing destination identity,
  failed destination write and failed optional identity/index publication. Prove
  no fork/index on failed save and that optional failure preserves actual saved
  bytes, completion, success callback and newer dirty text. Retry is idempotent
  for an already indexed transaction; no origin inferred from content.
- [ ] **Diff tests:** actual byte/line/work/output limits, foreign-history and
  legacy IDs, changed/deleted snapshots, strict encoding and immutable Preview.
  Use a latch in real durability to block inside validation; a second-thread
  native mutation must remain blocked until comparison exits. A same-thread
  reentrant mutation must invalidate the result through the generation guard.
  Inspect the eventual executor's monitor; do not claim a process-death pin test.
- [ ] **Activity tests before hooks:** extract actual `saveInternal`, submission,
  save worker/success/conflict retry, picker and new history callbacks into host
  harnesses, following existing `verification/save_lifecycle` and
  `verification/recovery_loader` patterns. Controlled executor/main ordering
  covers newer edits, switch/close, same-ID URI change, stale naming confirmation,
  shutdown, full 12 tabs, view-only/lock state, Focus/View and cancelled pickers.
  Extend owned fixtures first; root owns compatibility changes to existing runners.
- [ ] **Implement controller then review:** only new controller plus owned fixtures
  until root approves the exact startup/identity/diff protocol. Run focused UI,
  history index/catalog/diff and affected save/recovery checks when source changes
  justify them. Return exact canonical commit and observed results for independent
  review before narrow Activity integration.
- [ ] **Integration:** root reviews actual Activity diff and M0/source eligibility,
  registers the runner, runs the coherent host/unsigned gate once, then required
  hosted validation. No automatic runtime export/restore-pin or deletion feature.

Unreleased-candidate gates remain: physical Fold/SAF/IME/TalkBack, Android app-UID
fsync/ENOSPC/process death, signing/upgrade and the M0 device matrix. Host code and
source review do not waive them. No history feature or test pass is claimed by
this plan; only source inspection and planning have been performed in this task.

## Controller implementation checkpoint

The bounded new-class/controller fixtures are implemented; Activity integration
remains pending and root-owned. `README.md` records exact commands/evidence and
scope. Source findings were corrected after inspection of `DocumentIO.metadata`:
ordinary metadata exceptions are already caught internally; no speculative
post-save metadata fix or throwing metadata stub was added.

Ruling: `SaveRoute` includes the captured action revision and actual completion
validation precedes any identity write, so a forged/unverified transaction cannot
create Save As provenance before indexing rejects it. The source URI remains a
trusted captured input because destination transactions cannot prove old URI.

Ruling: the concurrency latch is at the real `inspectSources` directory-sync
callback. It proves validation-stage exclusion/reentrant generation detection;
source scope, not a fabricated callback, establishes coverage through comparison.

Independent review found a validate-before-copy race in `CapturedRevision`.
Default regression was observed RED then GREEN after validating the owned clone.
No production callback or mutable capture accessor was introduced for the test.
