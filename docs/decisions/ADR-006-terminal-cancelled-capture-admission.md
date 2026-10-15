# ADR-006: New capture admission after terminal cancellation

Status: **PROPOSED — pending independent critical review; no source authorization from this ADR.**

Date: 2026-10-15

## Context and scope

The [operation-data capture plan](../superpowers/plans/2026-10-15-operation-data-capture.md)
retains pre-LINK cancellation evidence, data and unused credits, and blocks pruning
and new captures. This protects failed staging, but a committed cancelled preparation
with missing descriptor/map bytes cannot become a completed capture by retrying.
Its hashes cannot reconstruct missing bytes or archive IDs.

This proposal permits another internal capture after a narrowly classified terminal
cancellation. It changes no wire format, record ID, data domain or allowance. It
preserves pending state, all bytes/credits and conservative pruning/retirement blocks.
It does not promise that every failure is recoverable or enable backup UI.

If approved, it supersedes **only** the old plan's new-capture-block clause for the
eligible historical state below. Pruning blocks, retention, immutable evidence and
all other recovery restrictions remain. Historical plans/evidence are not rewritten.

## Exact eligible historical state

Each pending historical preparation must satisfy all of these conditions:

- A committed canonical PREPARE P, its exact embedded committed BEGIN, and a matching
  committed canonical CANCELLED CLOSE exist under the registered bundle/authority.
- No LINK, LINK.stage, PREPARE.stage, BEGIN.stage, CLOSE.stage or conflicting control
  exists for that preparation. Unknown/unbound control evidence remains blocking.
- Its complete ledger accounts for every physical entry and every future byte/slot.
  Recognized bounded declared artifact stages and missing declared artifacts are
  allowed; unknown paths, duplicate stage/final entries, symlinks, nonregular files,
  oversized stages and invalid committed artifacts are not.
- No old live/uncertain same-process preparation owner or consumer lifetime exists.
  A newly registered owner is distinguished by its exact new preparation identity;
  admission does not require the registration slot to be empty once it owns it.

Existing fully closed linked captures may coexist. Any other unfinished preparation
prevents a new capture. A released pre-LINK CLOSE does not satisfy this predicate.
Classification requires typed records and complete funding; filenames or diagnostic
strings alone cannot establish eligibility. Partial data is accounted evidence, not
proof that it equals a prefix of unavailable final bytes.

## Accounting, graph and new-capture admission

Keep `Control.pending` and `Ledger.pending` true for abandoned evidence. Introduce a
separate count of unfinished nonterminal preparations. The existing `pending > 1`
validity rule must count those unfinished preparations, so historical cancellations
plus one new P do not become invalid commitments.

Derive a narrow historical-cancellation capture-eligibility classification from the
actual control and ledger. `HistoryGraph` currently sets `pruneBlocked = !complete`;
separate these meanings only for this fully validated state: accounting/typed graph
validation may be complete while pruning remains blocked by retained pending evidence.
Do not make an incomplete graph complete by stripping arbitrary diagnostics, ignoring
preparation prefixes, suppressing unknown stages or replacing the baseline.

Manifest/native-helper/plan admission may accept that exact classified state despite
its pruning block. All native kinds, links, roles, reservations and current authority
must still validate normally. C0, L0 and the full graph fingerprint include **all**
old controls, data/stage bytes, identities and unused credits. The new native map is
frozen against that full original graph before its own PREPARE.

The writer retains its exact original baseline and an `initialPending` value. Its
expectation becomes initial pending OR its own pending preparation, with exact own
transitions and credit conversion. Every old entry/identity/hash remains checked;
new publication cannot absorb old-stage mutation or accept a newer baseline.

All prior artifact reservations, missing directory/entry reservations, unused LINK
credit and other control commitments remain charged. They cannot fund the new P,
artifacts or receipts. New admission uses the same shared budget for simultaneously
live metadata and existing aggregate data/control/namespace limits. Finite retained
journals or credits may still exhaust quota and refuse another capture.

## Cold cancellation and completion are separate authorities

A private, one-use cancellation-only capability may consume an exact P's already
funded BEGIN and CLOSE. It binds current typed control/ledger funding, canonical roots,
persisted IDs/template and cold quiescence. It may publish that embedded BEGIN then
canonical CANCELLED CLOSE even if D/native map/private artifacts are absent, partial
or stale. It never asserts that those bytes exist or that the capture source matches.
It issues no LINK, captured binding or activation authority; unused LINK credit stays.

BEGIN publication, the exact terminal claim and CLOSE publication must share one
primary lease. There must be no unlocked interval permitting competing completion.
Every durability callback rechecks source-lifetime exclusion, old physical evidence,
exact own transitions and aggregate credits. No source/provider callback runs locked.
A live/uncertain same-process owner is not cleared, reset or called cold; genuine
restart must discharge that prior process lifetime. Existing canonical CLOSE-prefix
recovery may finish its exact bound terminal record under its reviewed quiescence
rules. Complete P/BEGIN stages need explicit typed promotion before cancellation;
this cancellation path creates no data and proves no capture success.

Partial P or BEGIN, conflicts and unknown control evidence remain unchanged and
blocked with a diagnostic. Missing bytes are never guessed, rewritten or truncated.
Any LINK.stage excludes historical eligibility, even if fully funded and followed by
CANCELLED CLOSE: this proposal grants no LINK repair, deletion or post-CLOSE promotion.

Completion recovery separately requires complete exact artifacts, the original native
source predicate and an explicit reversed-own-state typed graph proof matching the
persisted original graph binding. Reverse only proven own transitions/credits; never
choose a newer graph, ignore a prefix or adopt arbitrary partial stages. Complete exact
stages alone may be synced/revalidated/promoted using existing IDs and bytes.

## Mandatory deletion and maintenance proof

Admission flexibility must not become deletion authority. Independent source review
and changed-path tests must demonstrate that **every** deletion/maintenance entry
remains blocked for this state:

- `HistoryPruner.planAutomatic`, `planAutomaticPressure` and `planSelected` all reach
  the shared `complete && !pruneBlocked` guard.
- `HistoryPruneIntent` admission retains both completeness and pruning guards.
- `HistoryMaintenanceExecutor` entry/revalidation and retained retirement paths retain
  their complete-ledger and `operations.pending` refusals, including stale preselected
  plans, second handles and projected/all-absent retirement checks.
- Ordinary funded native saves remain possible under current authority; they confer
  neither capture freshness nor permission to prune/retire failed staging.

These are required implementation proof obligations, not claims of tests run for this
proposal. Operation data remains protected and outside native deletion membership.

## Source touchpoints and bounded checks

First implementation slice:

- `PrivateOperationStore`: typed historical classification, separate unfinished count,
  complete funding and retained pending state.
- `HistoryGraph`: independently represented accounting completeness/pruning block and
  full baseline binding; no weakened native validation.
- `HistoryArchiveManifest`, `NativeCaptureFingerprint`, `PrivateCapturePlan`: exact
  classified capture admission, matching full original evidence and shared budget.
- `PrivateCaptureWriter`: accepted initial pending state, unchanged old-entry anchors
  and exact own-transition accounting.

Second slice: narrow Store/Pins cancellation capability and HistoryOperations cold
terminal claim hooks; only necessary PreparationSources identity inspection, never
owner reset/removal by cancellation. Existing Pruner/Intent/Executor guards are review
and verification touchpoints; changing them is not authorized by this proposal.

Minimal distinct checks must cover:

1. Genuine cold cancellation with missing D/map and a bounded data.stage, then new
   capture admission with all old bytes, identities and credits unchanged.
2. Old cancelled P plus new P passes unfinished-count validation; two unfinished Ps
   still fail, and every old-stage/control mutation invalidates new publication.
3. All planner modes, stale intent/selected-plan execution and retirement paths remain
   blocked; ordinary funded saves still work.
4. LINK.stage, partial P/BEGIN, conflicting controls, unaccounted/oversized/symlink
   data and a live/uncertain old owner remain refused without rewriting evidence.
5. Two independent fresh JVM reopenings preserve cancellation classification, complete
   accounting, retained credits and exact durable records; no same-process cold reset.
6. Completion recovery rejects a changed reversed original graph and never reuses
   cancellation authority to create LINK, a captured result or consumer activation.

## Portable backup gate

Local retention and a graph hash are not lossless portable inclusion. Before backup
UI is enabled, the portable path must include all failed staging/control evidence and
its necessary reconstruction/accounting metadata without silently omitting this scope.
This ADR changes no archive format and authorizes no incomplete-backup claim. Pruning
remains conservative while that work and producer-wide fault/heap acceptance are open.
