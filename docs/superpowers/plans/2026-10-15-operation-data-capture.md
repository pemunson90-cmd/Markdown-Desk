# Operation-data admission and durable capture → BEGIN

> **For agentic workers:** Use superpowers:executing-plans after independent
> architecture approval and source authorization. This is a proposed plan only.

**Goal:** Charge durable operation preparations to the existing primary authority,
then bind an exact persisted capture to a durable pin without a capture/publication gap.

**Architecture:** A bounded operation-data ledger joins the four-root native ledger;
a typed immutable preparation commitment funds exact artifacts and terminal records.
Native-source identity and operation-ledger identity remain distinct, and publication
accepts only enumerated transitions belonging to this exact preparation.

**Tech Stack:** Existing Java backend, JDK17/SDK37 offline, Android min26; no new
runtime dependency, provider, Activity, portable archive codec or automatic deletion.

**Spec:** Source base `ea289cb`; proposed backup design/ADR-005 at `d839225`, with
namespace/accounting clarifications at `be6d444` in `codex/backup-adoption-design`.
Those proposed documents are separate review inputs, not implemented formats.
Root requested phased work within ONE meaningful backup/capture integration boundary;
no separate product feature/PR or full gate for each helper checkpoint.

## Scope and immutable meanings

- Phase A is internal `PRIMARY_NATIVE_ONLY`: prove admission, persisted native map,
  local descriptor and atomic BEGIN. It is provisional, never complete backup or
  eligible output. Phase B adds caller-provided immutable private captures through
  existing `PrivateStateCodec`: up to 12 tabs, independent recovery, eight allowed
  preferences. No Activity capture adapter, provider/export/import consumer yet.
- Both phases create a local `CaptureDescriptor`, not ADR-005 portable inventory.
  It binds preparation UUIDv4, phase, bundle/registration and acknowledged policy,
  canonical four role names, native-source hash, initial operation-ledger hash,
  exact native-map artifact UUID/length/hash, ordered private-frame descriptors
  (kind, generated item/artifact UUID, length/hash), and explicit source-presence flags.
  Phase B distinguishes absent recovery from failed/unreadable required recovery.
- `HistoryArchiveManifest` remains the native ID→outer-ID map. Keep its v1 bytes and
  original `graphHash` as historical capture evidence; do not relabel that field.
  Descriptor source hash separately supports final source validation after own writes.
  Descriptor bytes, NOT map bytes, are the original pin manifest. Preparation UUID
  makes zero-native captures unique; identical retries reuse every persisted ID.
- Propose additive pin domain `LOCAL_CAPTURE_V1(2)`; existing domain1/pin bytes stay
  unchanged. Descriptor count is validated native-map count plus private-frame count;
  hash/length cover exact descriptor bytes. Neither domain grants output success.
  Descriptor+referenced map metadata together <=16 MiB; native entries <=16,380.
  A future portable inventory must fit the same combined budget, not another one.
- RELEASED/new retained-stage recovery still needs the exact persisted descriptor
  and quiescence. Historical committed CLOSED audits never dereference retired
  inputs/manifest. Capture/link receipts confer neither consumer admission nor export.

## Namespace, bytes and slots

- Fixed first-slice shape: `private_operations/<prepUUID>/map-<UUID>.native`,
  `capture-<UUID>.descriptor`, and Phase B `state-<UUID>.frame`; `.stage` is the only
  alternate artifact name. Canonical lowercase UUIDv4; no caller paths or filenames.
  Namespace root and preparation directories are charged slots, even when empty.
- Depth two below the namespace is accepted now. Future retained-import schemas
  require separate review, depth <=5. Globally <=65,536 ALL namespace entries,
  including the namespace root, directories, files/stages, links and unknown nodes.
  Count before descent via bounded DirectoryStream; no unbounded listFiles array,
  following links, arbitrary recursive traversal or fifth registered native role.
- Physical regular-file lengths (including failed stages) plus outstanding committed
  data-byte reservations share current primary quota with native actual bytes and
  native completion credit. Operation slots have the global namespace cap; native
  per-role caps remain unchanged. Directory slots have zero logical payload bytes.
- For expected artifact length L: absent => reserve L bytes/one slot; recognized
  stage of s<=L => physical s/one slot + remaining L-s/zero slots; exact final =>
  physical L/one slot, no remaining credit. Each required absent directory reserves
  one slot. Final+stage duplicates, overlength, unknown/nonregular or unreadable
  evidence block admission and receive no credit. Physical occupancy is never hidden.
- Pending data stages are recognized only through exact durable PREPARE descriptors;
  charge their remaining budget even when incomplete/hash-unverified, but mark them
  unusable for capture. Credit is accounting, not permission to overwrite or resume.
- Malformed/unknown operation evidence blocks new writers and all prune proofs;
  existing native snapshot/transaction recovery reads remain independently usable.
  No new allowance, GC, compaction, receipt expiry, deletion or universal availability.

## Local control frames and conservation

- New explicit local frame kinds: `PREPARE` and `CAPTURE_LINK`, canonical typed
  versioned framing with stable numeric codes, strict length/checksum and <=16 KiB
  complete envelopes. Golden bytes/schema review precedes production codecs.
  Names are `prep-<prepUUID>.reserve` / `.link` with existing `.stage` convention.
- PREPARE binds preparation UUID, registration/policy, source/initial-ledger hashes,
  descriptor identity, pin-generated operation UUID/exact BEGIN hash, and at most
  16 exact artifact descriptors (map+descriptor+
  14 private frames), directory commitments and fixed control credits. No payloads,
  arbitrary strings, variable receipt claim or output/provider evidence in control.
- CAPTURE_LINK binds prepUUID, operationUUID, PREPARE hash, BEGIN hash and descriptor
  hash: 128-byte body + 13-byte framing + 32-byte checksum = 173 bytes. PREPARE
  computes actual encoded length P; current ALL_BUNDLE BEGIN is 204 bytes; CLOSE126.
- Before PREPARE creation admit physical control + existing credits + P + 204 +
  126 + 173 bytes and FOUR slots. Marker, policies, all pin pairs, stages and these
  credits share the existing ONE 1 MiB/256 entries. Stage→final is same-slot rename.
- Durable PREPARE owns BEGIN204/one slot, CLOSE126/one slot and LINK173/one slot.
  Recognized exact complete BEGIN.stage consumes its 204/slot credit; after committed
  BEGIN, CLOSE credit transfers to the existing pin inventory, never counted twice
  or dropped. PREPARE reconstructs the exact BEGIN bytes: a matching partial stage
  consumes only its physical prefix/slot credit; it still blocks publication/recovery.
  Unknown prefixes get no credit. No partial-BEGIN repair/quarantine is introduced.
- LINK stage uses physical s + reserved(173-s), one occupied slot; promotion consumes
  its remaining credit. CLOSE retains existing canonical-prefix accounting/protocol.
  Receipt credit is immutable and cannot fund policy, a different receipt or data.
  Complete final bytes alone do not establish directory-durable acknowledgement.
- Every control writer/promotion, including policy/enrollment, pins, executor and
  recovery helpers, uses ONE aggregated checked-arithmetic credit inventory. Validate
  zero-growth allowance before promotion and again after the last callback.
- Only LINK is a new receipt in this slice. No EXPORT_VERIFIED/RESTORED receipt or
  output eligibility exists. Their exact peak credits must be added before enabling
  those future protocols; do not promise they can be funded by unused LINK credit.
  Retain PREPARE/LINK and closed pin audits; bounded journal eventually exhausts.
- Internal `HistoryPins.prepareBeginLocked(manifest)` returns an immutable template;
  `publishPreparedBeginLocked(preparationCapability, template)` consumes only the
  exact durable PREPARE credit. Existing `begin` generation/behavior/wire stays intact.
  No guessed UUID, arbitrary supplied BEGIN, or absence-based acknowledgement.

## Capture, callbacks and exact own transitions

1. Outside history/gate locks, strictly preflight immutable private captures with
   PrivateStateCodec into a counting/hash sink, no disk writes. Bound metadata and
   document lengths; regenerate deterministic frame bytes from the same caller-owned
   immutable captures only during this initial preparation. No native/provider reads
   through callbacks while locked. Frame suppliers are trusted ownership adapters;
   byte equality is verified, not inferred from an interface name.
2. Under common monitor/one lease require exact installed ready authority, complete
   native capture graph, valid full operation/control accounting, no maintenance,
   active pin or other unfinished preparation. Generate prep/artifact/native-map IDs
   once using internal generation, then encode metadata and exact sizes/hashes.
   The locked pin helper allocates operation UUID/exact BEGIN from that descriptor,
   without publication/acknowledgement. Persist/sync/read back PREPARE retaining that
   binding before ANY data directory/stage or post-preflight frame supplier invocation.
3. Persist bounded map/descriptor metadata and private frames only at committed paths.
   A synchronous `copyPrivateFrame(prep, item, source)` owns source closure outside
   locks; shared preparation state counts its full callback lifetime. Each bounded
   internal disk write acquires the existing lease and enforces remaining credit;
   no caller-supplied stream/factory/close code runs inside the monitor/lease.
   `admitPreparedArtifactLocked` accepts only its exact live preparation capability
   and funded conversion, never a general bypass of blocked ordinary admission.
   No writable storage delegate escapes. No waits/join: commit rejects while a source
   call is live; context guards reject nested preparation/publication from callbacks.
   Finalization syncs/readbacks exact complete bytes before atomic promotion+dirsync.
4. Under one final monitor/lease, require no preparation I/O/callbacks, freshly prove
   native sources and every artifact, then publish BEGIN through a narrow locked pin
   helper using the exact descriptor. Do not release exclusion between final source
   proof and BEGIN. Durability callbacks are the existing audited exception; recheck
   all identities/hashes/policy and transition state after each and before ack.
5. Persist LINK after durable BEGIN. Return a captured binding only after its durable
   acknowledgement. BEGIN without LINK already protects native pruning, but cannot
   activate a local-capture consumer. Cold discovery uniquely matches committed
   PREPARE+descriptor to BEGIN domain/hash/count/length/authority, including empty
   native map; it finishes that exact link, never guesses or generates another ID.

- `NativeCaptureFingerprint` binds exact native envelopes/edges/identity/reservations,
  canonical roles/bootstrap, registration and exact acknowledged policy. It excludes
  operation/control publication mechanics ONLY for this separate source predicate.
- Full `OperationLedgerSnapshot` binds every typed/unknown entry, physical identity,
  length/hash, stage/state, committed future byte/slot credits and control commitments.
  Graph proofs always bind this complete ledger; source hash cannot replace them.
- A `PreparationTransition` binds initial ledger and exact allowed next changes:
  PREPARE publication; named directories; each declared artifact absent→stage→final;
  BEGIN creation/promotion; LINK creation/promotion; associated credit transfers.
  Permit only expected per-write stage growth for the in-process owned copy token.
  Reconstruct the initial ledger by reversing ONLY these exact declared transitions.
  Advance expected ledger/generation after each own action; every OTHER entry and
  native source/control authority remains unchanged. No prep-prefix wildcard,
  blanket own-directory exclusion, stale full-graph comparison or generation reset.
- Native writes between initial capture and final publication invalidate this capture;
  they cannot be absorbed into its map. Failure leaves charged evidence. A retry
  requires the original source predicate and exact persisted IDs/artifacts; it never
  silently recaptures a newer graph or updates the descriptor/policy.

## Cold recovery and mandatory proof integration

- Partial PREPARE, partial artifact, partial LINK, noncanonical/conflicting/unknown
  stages remain preserved and blocked; no rewrite/truncate/new IDs or generic prefix
  repair. Only complete exact stages may explicitly sync/revalidate/promote. A live
  initial copy may grow its owned stage; failure/closure/death ends that token.
- Complete PREPARE.stage promotion rechecks authority/source/allowance before data
  creation. Recognized committed preparation keeps all credits after crash, even
  before directories, BEGIN or LINK. Missing known committed evidence cannot be
  erased by reopen; use retained name/hash baseline as pins do. No fresh-process
  arbitrary-tail-loss detection promise. No source callbacks reconstructed on reopen.
- New graph fields distinguish native actual/reserved and operation actual/future;
  totals charge both. All planner modes protect operation bytes/slots; operation
  files never become native records or deletion members. Pending incomplete capture
  blocks pruning. Completed closed captures remain charged, protected ledger entries.
- `HistoryGraph`, pressure/floor/selected planning, executor execute/replay, tombstone
  revalidation, survivor fingerprint and shared retirement all require exact current
  operation ledger. Extend stable survivor proof with a distinct operation-ledger
  domain independent of native deletion closure; no ignored operation namespace.
- Preserve legacy hashes when namespace/commitments are genuinely absent. Nonempty
  ledger adds a domain-separated binding to existing hash fields, without changing
  prune-intent wire layout. Old intent plus newly appeared operation evidence fails
  validation; ordinary operation creation is forbidden during maintenance anyway.
- `HistoryCapacity.prepareTerminalLocked` currently constructs Usage from native
  graph totals directly: replace that bypass with complete physical+future operation
  accounting after last callbacks. Candidate/final retirement capability binds same
  ledger before clearing view/installing ready authority, including no-intent retry.
  Closed-audit reconciliation stays source/manifest independent and lease-aware.

## Tasks, ownership and executable evidence

- [ ] 1. Behavioral RED fixtures first in `verification/history_operation_data`:
  actual policy/native writers at exact shared quota/control byte+slot limits; empty
  directory floods, wrong depth/symlink/unknown nodes; stages and integer overflow;
  native recovery readable while new admission rejects uncertain operation inventory.
- [ ] 2. Implement `PrivateOperationStore`, local commitment/descriptor codec and
  ledger; narrow HistoryControlStore/HistoryCapacity/HistoryStorage admission hooks.
  HistoryPinRecord owns additive domain2 and centralized credit decoding. Existing
  pin/native/map bytes stay unchanged. Enumerate ALL writer/promotion call sites.
- [ ] 3. Implement internal native-only `prepare`, `commitCapture`, `recoverCapture`
  and `inspect` APIs; locked Pins publication/link binding; HistoryOperations must
  reject domain2 activation until exact durable LINK validation; ArchiveManifest source
  validation helper without wire change. Test zero-native distinct preparations,
  every own transition, changed outside file/policy A→B→A, bootstrap mismatch,
  same-byte replacement during callbacks and forbidden source/consumer reentrancy.
- [ ] 4. Add Phase B `preflightPrivate`/`copyPrivateFrame` using PrivateStateCodec;
  dirty/BOM/CRLF tabs, independently present/absent recovery and exact preferences.
  Changed repeat stream, throwing source/close, retained source reference, callback
  races and budget rejection must produce no BEGIN/consumer success. Private payloads
  stream with <=65,536-byte buffers; no document/frame arrays or multiple full maps.
- [ ] 5. Update Graph/Pruner/MaintenanceRecovery/Executor and terminal capacity paths.
  Test all planner modes, projected partial/all-absent closure, second-handle retirement,
  closed audits+intent, ledger mutation at last callback; assert exact survivor bytes,
  unchanged reservations and no additional deletion. Existing strong fixtures stay.
- [ ] 6. Real SIGKILL before/after PREPARE sync/rename/dirsync, each data artifact,
  BEGIN publication and pre-LINK/LINK boundaries; reopen twice in independent JVMs.
  RLIMIT/ENOSPC partial stages preserve bytes/identity/credits; exact retries never
  regenerate IDs. Constrained-heap full-budget cases include simultaneous native
  graph/map/descriptor/ledger indexes, strings and copies in ONE 16 MiB metadata
  budget (not each); measure peak allocation and reject before oversized allocation.
- [ ] 7. Independent exact-source persistence/concurrency review, scoped existing
  capacity/pin/operations/manifest/planner/executor/controller regressions as affected;
  root owns one coherent clean-host/two-unsigned gate, central runners and status.

## Review focus and decisions before source

Confirm additive LOCAL_CAPTURE_V1 domain and local PREPARE/LINK frame golden schema;
exact credit transfer for retained BEGIN.stage; immutable-capture adapter ownership
outside locks; complete own-transition proof despite generation changes; legacy empty
ledger compatibility and every terminal bypass. This plan authorizes no source yet.
Portable v2 inventory/schema, retained-import projection/remap/receipt budgets, actual
UI capture completeness, output verification, import adoption and journal compaction
remain separate reviewed work. Future failed/retained staging cannot be silently
omitted from whole backup: only a bounded explicit lossless-projection proof could
permit omission; this slice provides no such proof or complete-backup claim.
