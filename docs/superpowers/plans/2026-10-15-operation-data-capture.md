# Operation-data admission and durable capture → BEGIN

> **For agentic workers:** Use superpowers:executing-plans after independent
> architecture approval and source authorization. Plan 7a6c72b3 is approved and root authorizes implementation; behavior is unverified.

**Goal:** Charge preparations to primary authority; atomically bind persisted captures to pins.
**Architecture:** One ledger, immutable PREPARE funding, separate native-source proof
and exact own transitions. No fifth role, quota or generic bypass.
**Tech Stack:** Existing Java, JDK17/SDK37 offline, Android min26; no new dependency.
**Spec:** Source `ea289cb`; proposed backup/ADR-005 `d839225`/`be6d444` on
`codex/backup-adoption-design`. These are review inputs, not implemented formats.

## Scope and immutable meanings

- Phase A is internal `PRIMARY_NATIVE_ONLY`: prove admission, persisted native map,
  local descriptor and atomic BEGIN. It is provisional, never complete backup or
  eligible output. Phase B uses immutable captures/PrivateStateCodec (bounds below).
  No Activity capture adapter, provider/export/import consumer yet.
- CaptureDescriptor is local, not portable inventory; absent recovery differs from failure.
- `HistoryArchiveManifest` remains the native ID→outer-ID map. Keep its v1 bytes and
  historical `graphHash`; descriptor source hash validates sources after own writes.
  Descriptor bytes, NOT map bytes, are the original pin manifest. Preparation UUID
  makes zero-native captures unique; identical retries reuse every persisted ID.
- Propose additive pin domain `LOCAL_CAPTURE_V1(2)`; existing domain1/pin bytes stay
  unchanged. Descriptor count is validated native-map count plus private-frame count;
  hash/length cover exact descriptor bytes. Neither domain grants output success.
  Future portable inventory must fit the same combined metadata budget, not a new one.
- RELEASED/new retained-stage recovery still needs the exact persisted descriptor
  and quiescence. Historical committed CLOSED audits never dereference retired
  inputs/manifest. Capture/link receipts confer neither consumer admission nor export.

## Namespace, bytes and slots

- Shape: `private_operations/<prepUUID>/map-<UUID>.native`,
  `capture-<UUID>.descriptor`, Phase B `state-<UUID>.frame`; only `.stage` alternates.
  Canonical generated UUIDv4 paths, no caller paths; root/preparation dirs cost slots.
- Depth two accepted now; separately reviewed retained-import schemas may reach five.
  Globally <=65,536 ALL entries including root, dirs, files/stages, links/unknowns.
  Count before descent with bounded DirectoryStream, no unbounded listFiles arrays,
  symlink following or arbitrary recursion. Directory payload charge is zero bytes.
- Physical regular-file lengths + committed future data bytes share primary quota
  with native actual/completion credit. Native role slot caps remain unchanged.
- Artifact length L: absent reserves L/one slot; recognized stage s<=L costs physical
  s/one slot + reserved L-s/zero slots; exact final costs L/one slot, no credit.
  Each absent required dir reserves one slot. Final+stage conflict, overlength,
  nonregular/unreadable/unknown entry blocks admission, receives no conversion credit.
- PREPARE-described partial data stages retain remaining budget even before complete
  hash validation; accounting credit grants no use, overwrite or cold resume.
  Unknown/malformed inventory blocks writes/pruning, never ordinary native recovery
  reads. No GC, deletion, expiry, compaction, extra allowance or availability promise.

## Frozen local schema and control conservation

- All scalars big-endian; u8 codes, nonnegative i32 counts/lengths, i64 byte counts;
  UUID16 is network-order bits, SHA32 raw SHA-256. No padding, trailing or optional
  unknown fields. Envelope: magic ASCII `MDOP`, i32 version1, u8 type, i32 bodyBytes,
  body, SHA32(header+body). Types PREPARE1/LINK2/DESCRIPTOR3; control <=16 KiB.
- Common binding C, ordered: prepUUID16, phase(u8 A1/B2), bundleUUID16, registration
  SHA32, policyGeneration64, policySHA32, bootstrapQuota64, quota64, automaticCount32,
  exactly four roles (u8 id1..4, i32 UTF8 length1..64, canonical registration basename),
  nativeSourceSHA32, initialLedgerSHA32, presenceFlags8 (bit0 recovery/bit1 preferences).
- Artifact row A, exactly77 bytes: kind8 (map1/descriptor2/tab6/recovery7/preferences8),
  artifactUUID16, itemUUID16, ordinal32, length64, envelopeSHA32. Map/descriptor item
  is zero UUID and ordinal0; private item/artifact IDs are distinct generated UUIDv4.
  Row order kind then ordinal; tab ordinals contiguous0..N-1, others0. No duplicates.
- DESCRIPTOR body = C, map A, privateCount32, private A rows (<=14). Phase A has no
  private rows/flags; Phase B <=12 tabs, <=1 recovery, exactly1 preferences and flags
  agree with rows. Frame length <= PrivateStateCodec's per-kind limits; strict codec
  validation required. Map count <=16,380; map+descriptor bytes together <=16 MiB.
- PREPARE body = C, directoryMask8 (bit0 absent namespace/bit1 absent prep dir;
  bit1 required), artifactCount32, all A rows (map+descriptor+private, <=16), exact
  canonical BEGIN204. C and rows must match descriptor; BEGIN binds that descriptor
  hash/length/count, bundle/registration/policy, EXPORT/ALL_BUNDLE/domain2, no sources.
  PREPARE holds no recursive self-hash; descriptor excludes its own artifact row.
- LINK body = prepUUID16, operationUUID16, PREPARE-SHA32, BEGIN-SHA32, descriptor-SHA32;
  exactly173 bytes including envelope. Names `prep-<UUID>.reserve` / `.link`, `.stage`
  alternate. BEGIN stays existing204 bytes; CLOSE126. Existing native/pin/map bytes
  unchanged; bundle UUID follows current authority, newly allocated IDs are UUIDv4.
- Independent golden vector: UUID suffixes1=prep/2=bundle/3=map/4=descriptor/5=operation,
  each `00000000-0000-4000-8000-00000000000n`; phase1, generation0, both quotas67108864,
  automaticCount10, roles store/catalog/identity/index; source/ledger/registration/
  policy/map SHA fields repeat bytes11/22/33/44/55(hex), flags0, map length212,
  native-map/BEGIN count0, privateCount0, directoryMask3, artifactCount2; D gives its row.
  BEGIN derives from D and operation5; P uses that BEGIN, LINK derives from P/B/D.
  Complete length/SHA256 (codec vectors, not a semantically validated native map):
  D361 `990d77ac4677f257c9bc2e746e739232d5802b3f02c248c24210d55c45bd9b41`;
  B204 `1548e74c29a6196e7e9c0aa81f85dbfcae71c75205013287e50dad3a97321959`;
  P643 `a11eecf61d1bf1c61dba8825b65b65f2da50b66953959207faafef4e5c0b7f65`;
  L173 `1cb4f91da7862cf36c37880de51a4d045f35c9bbcf889ffd4539512a36d7ecdd`.
- Before PREPARE, admit physical control + existing credits + encoded P +204+126+173
  bytes/FOUR slots inside ONE1MiB/256 including marker, policies, pins and all stages.
  Complete PREPARE.stage derives the same credits before promotion, without ack.
  PREPARE owns BEGIN204/1, CLOSE126/1, LINK173/1. Exact partial BEGIN s consumes its
  physical s/1, leaving204-s/0; it STILL blocks, no repair/quarantine/ack by absence.
- At exact complete BEGIN.stage, existing pin inventory already owns CLOSE126/1:
  aggregated inventory suppresses PREPARE's same CLOSE credit then and after committed
  BEGIN. Deduplicate only by exact prepared BEGIN binding; no global suppression.
  Conflicting stage+final/unknown prefix blocks and gets no conversion credit.
- LINK stage costs physical s/1 + reserved173-s/0; exact promotion exhausts its credit.
  CLOSE uses existing canonical-prefix credit. No bytes alone prove directory ack.
  Every writer/promotion uses ONE aggregate inventory; check allowance before any
  promotion and after last callback. Credits cannot fund another receipt/policy/data.
- Only LINK is new receipt; no EXPORT_VERIFIED/RESTORED writer/output eligibility.
  Future protocols must reserve their own exact peak credits before enabling them.
  Retain PREPARE/LINK/closed audits: finite journal eventually exhausts.
- `HistoryPins.prepareBeginLocked(manifest)` internally allocates immutable template;
  `publishPreparedBeginLocked(preparationCapability, template)` consumes that exact
  durable credit. Ordinary begin rejects domain2 without that prepared capability;
  domain1 behavior stays. No caller-guessed/reconstructed ID or unbound activation.

## Capture, callbacks and exact own transitions

1. Outside locks, preflight with PrivateStateCodec into a counting/hash sink, no disk
   writes; step3 owned-resource/close rules apply to preflight too. Bound metadata and
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
   Current complete aggregate accounting permits ordinary native appends/policy changes;
   it never requires the old capture predicate. Such changes invalidate capture only.
   No writable delegate escapes; no waits/join. Registration covers opening, live
   source and owned close, NOT just callbacks. Failed close retains resource+count
   and blocks commit across handles; only retry of actual owned close can release it,
   never finally-decrement/boolean confirmation. Guard remains through closure/signals.
   Factory failures must account for every acquired resource; trusted reviewed adapters
   cannot retain raw delegates/spawn untracked work. Unknown ownership stays blocked.
   Finalization follows successful closure, exact sync/readback and atomic move+dirsync.
4. Under one final monitor/lease, require no preparation I/O/callbacks, freshly prove
   native sources and every artifact, then publish BEGIN through a narrow locked pin
   helper using the exact descriptor. Do not release exclusion between final source
   proof and BEGIN. Durability callbacks are the existing audited exception; recheck
   all identities/hashes/policy and transition state after each and before ack.
5. Persist LINK after durable BEGIN. Return a captured binding only after its durable
   acknowledgement. BEGIN without LINK already protects native pruning, but cannot
   activate a local-capture consumer. Cold discovery uniquely matches committed
   PREPARE+descriptor to BEGIN domain/hash/count/length/authority, including empty
   native map; absent terminal evidence, finish only that link, never generate IDs.
   Quiesced cancellation before LINK remains allowed. Pre-LINK CLOSE evidence abandons
   capture/activation permanently; retain data and unused LINK credit, block pruning/
   new captures, but not ordinary writes solely for known funded abandoned evidence.

- `NativeCaptureFingerprint` binds exact native envelopes/edges/identity/reservations,
  canonical roles/bootstrap, registration and exact acknowledged policy. It excludes
  operation/control publication mechanics ONLY for this separate source predicate.
- Full `OperationLedgerSnapshot` binds every typed/unknown entry, physical identity,
  length/hash, stage/state, committed future byte/slot credits and control commitments.
  Graph proofs always bind this complete ledger; source hash cannot replace them.
- A `PreparationTransition` binds initial ledger and exact allowed next changes:
  PREPARE publication; named directories; each declared artifact absent→stage→final;
  BEGIN/LINK publication and credits; quiescence-authorized canonical CLOSE before
  LINK abandons capture, never completes it or authorizes a fabricated LINK.
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

- Partial PREPARE/BEGIN and unknown/nonprefix/unbound control stages block authority.
  Exact LINK prefix at its expected path, bound to committed PREPARE+BEGIN/173B credit,
  is fully accounted optional receipt evidence: ordinary writers may proceed; no LINK
  ack, activation or partial repair. No generic ignore-stages rule. Partial artifacts
  also block capture/repair, not complete accounting. Preserve all bytes; no rewrite/
  truncate/new IDs. Complete exact stages alone may sync/revalidate/promote; an initial
  live copy can grow its stage only until its token ends on failure/closure/death.
- Complete PREPARE.stage promotion rechecks authority/source/allowance before data
  creation. Recognized committed preparation keeps all credits after crash, even
  before directories/BEGIN/LINK. Retain known name/hash evidence across handles; no
  fresh-process arbitrary-tail-loss claim or reconstructed source callbacks.
- New graph fields distinguish native actual/reserved and operation actual/future;
  totals charge both; operation files are protected, never native deletion members.
  All planner modes bind this ledger. Pending incomplete/abandoned capture
  blocks pruning/new captures. Closed capture ledger never dereferences retired natives.
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
  exact control/quota limits across all writers, cold complete BEGIN.stage CLOSE
  deduplication, partial/conflicting stages, integer overflow, empty-directory floods,
  depth/links/unknowns; native recovery reads survive uncertain operation inventory.
  Stale PREPARE, exact LINK prefixes and abandoned cancellation permit funded native
  appends/policy while rejecting capture/pruning; ordinary begin cannot bypass domain2.
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
  Changed streams, throwing open/read/close, idle-live source and second-handle reopen
  cannot bypass ownership counts; retry close/hostile callbacks never mint false proof.
  Races/budget failure give no BEGIN/success. Buffers <=65,536 bytes; no payload arrays.
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
  root owns ONE coherent feature PR/clean-host/two-unsigned gate, runners and status.

## Review focus / exclusions

Review domain2/schema/goldens, exact staged-BEGIN credit owner, resource-close proof,
reversible own transitions and all terminal ledger paths before source authorization.
Portable v2, retained-import/remap/receipts, UI, output/adoption and compaction are
separate work. Failed/retained staging needs a future bounded lossless-projection
proof before omission from backup; no such proof or complete-backup claim here.
