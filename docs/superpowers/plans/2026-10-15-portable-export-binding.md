# Portable export binding — architecture packet

Date: 2026-10-15. **DESIGN ONLY; proposed bridge, no source authorization.**
Goal: bind one complete captured workspace to exact persisted portable inventory
before provider access, preserving local capture and legacy compatibility.
Architecture: recommend a separate opt-in portable preparation before its first
BEGIN, sharing the accepted capture producer, primary accounting and lifetime owner.
Tech: existing streaming Java/minSdk26; no new dependency or storage authority.
Spec: [ADR-005](../../decisions/ADR-005-portable-private-bundle-format.md), approved
[local capture](2026-10-15-operation-data-capture.md) and
[scoped codec](2026-10-15-scoped-private-archive-codec.md) plans; sequencing in
[Professional campaign](2026-10-15-professional-campaign.md).

## Authority, evidence and scope

- Packet base `2bdf43f33d73e9ff6874eb4bf2362e3510906a43`, clean existing
  `codex/portable-export-binding`; no interrupted saved plan or unpublished changes.
- Current integration evidence read at `53e8f4452bd0250d8f74f692f441276ab3e95173`;
  PR23 is review-ready with required CI pending, main `b5796ba`. These are context,
  not this packet's merge/build claims. Root retains STATUS, runners, source integration.
- C1 inspected at `4a29200d1a78d573b7692fdae2cb9095267f117c` in its separate checkout:
  repair under lead ownership, not an accepted producer/activation contract.
- Own only this plan. No source/tests, Activity, runners, root status, PR/CI/build,
  provider receipt, import/adoption implementation or cleanup authority in this slice.
  This does not complete M1 or change release, signing or device gates.

## Actual contracts and missing boundary

`PrivateArchive.writeV2(UUID, StreamOpener, ArchiveSource, OutputStream,
PrivateBundleInventory.MetadataBudget)` reparses a frozen inventory, checks repeatable
sources against its rows, and returns framing `ArchiveSummary`. It owns opened source
streams; caller owns outer flush/close and fresh destination readback. It is not an
operation admission API, typed graph validator, durable acknowledgment or receipt.
`readPortable(InputStream, ScopedSink, LegacySpool, MetadataBudget)` returns provisional
framing evidence. Existing v1 read/write remain v1-only, types1–9 unchanged.
Inventory schema1 already carries preparation/root scope IDs, scope tab order and
activeEntryId, ordinary UUID/type/length/hash/identity, policy, origins and lineage.
It does not discover local state, allocate IDs or grant retained-content authority.

Actual MDOP `PrivateOperationRecord` at the C1 checkpoint accepts only version1,
phases A1/B2, artifacts map1/descriptor2/private6–8, controls PREPARE1/LINK2/DESCRIPTOR3.
D binds native-map and private-frame digests; tab ordinals persist order. It has no
inventory or active-tab identity, and `PrivateStateCodec.Tab` has no active flag.
Local domain2 (`LOCAL_CAPTURE_V1`) is a separate approved format from domain1.
Do not append inventory to D, reinterpret its count/hash, or alter approved A/B bytes.
`HistoryPins.begin(...)` rejects another active pin. A linked local pin cannot simply
acquire an export pin; a post-LINK conversion is a new durable handoff protocol.

## Alternatives and recommendation

| Approach | Assessment |
| --- | --- |
| **Opt-in portable preparation before its first BEGIN** | Recommend. One publication binds all needed evidence and one protection lifetime; local A/B remain intact. Requires a separately reviewed persistence protocol and exact producer contract. |
| LINK local capture, then bind I with another pin | Defer. Single-active-pin rule requires reviewed ownership transfer, crash recovery and gap-free source protection; closing first is unsafe. No existing shortcut API. |
| Build inventory at output from D/live UI | Reject. Old active selection is unavailable; later UI/native state and regenerated IDs cannot establish the captured workspace or exact retry. |

Recommended consumer workflow is a future explicit **Back up private workspace**
coordinator: request portable preparation → receive a durable portable binding →
activate tracked immutable sources → invoke existing writeV2. Destination access,
readback and success receipt are its next packet, not this bridge. No backup UI
currently exists. A legacy local A/B capture is ineligible; request a new capture
once existing lifecycle rules permit, without claiming to recover its old selection.

## Required producer facts, not frozen API names

Before bridge source work, C2 and portable-input owners must agree these contracts;
portable-only inputs must not silently change the approved local C2 format:

1. One immutable private capture containing ordered tab identities/frame sources,
   active identity from the SAME capture, independently present/absent recovery and
   allowlisted preferences. Capture selection with tab membership, not in a later UI
   callback. A producer must supply a stable selection observation/validation rule;
   absence with nonempty tabs is a failure, never a default-first-tab guess.
2. Native map/source fingerprint and private frame length/digests, initial full
   operation ledger, authority/policy identity, plus stable source-open ownership.
   No caller callback inside monitor/lease; close/factory uncertainty survives handles.
3. Under the same mutation exclusion, complete retained receipt/reachable-content
   discovery and classification of ALL operation evidence; stable protection for
   retained namespaces not covered by ALL_BUNDLE. If none, prove actual absence.
4. A narrow preparation/finalization/reopen/activation surface with exact persisted
   identity, quiescence and error semantics. Publish real signatures and source file
   ownership after C1/C2 acceptance; this packet defines no guessed Java interface.

The portable coordinator persists I (exact schema1 inventory bytes), Dp (proposed
portable binding descriptor), native maps and private frames with all generated
preparation/operation/inventory-frame/scope/outer-entry/private-item/provenance IDs.
Dp binds I's length/hash/identity, native-map/private digests, captured selection and
complete source membership, registration/policy, and the source/ledger proof used
for final publication. Exact envelope/reference representation is a later decision;
no recursive self-hash, hash-cycle or mutable external reference is permissible.
Existing native-map outer IDs and private artifact→item→outer mappings must be
persistently related, not re-created from hash/URI/filename or enumeration order.

## Proposed persistence boundary and accounting freeze

This recommendation explicitly proposes a NEW portable preparation mode, a NEW
portable descriptor/inventory artifact commitment and a distinct pin-manifest domain.
Numeric phase/domain/kind codes, envelope version, filenames, PREPARE/LINK schemas
and exact credits are UNASSIGNED here. Extending MDOP version1 acceptance implicitly
is forbidden. The source-ready packet must choose additive version dispatch versus
separate framing, enumerate all affected readers/writers/recovery and freeze wire
fields, independent goldens and every prefix/credit state before implementation.
No new receipt/control type or additional allowance is authorized by this plan.

- Preflight immutable private sources without persistence; admit full physical and
  future disk/slot/control needs before creating any preparation/data stage.
- Persist/sync/read back funding and all generated IDs before funded artifacts.
  I and Dp must be final exact bytes before final source proof→BEGIN, held under one
  monitor/lease through acknowledgment; no native/UI/retained recapture in that gap.
- BEGIN must bind Dp, which binds persisted I/native/private evidence. Only exact
  durable link evidence and acknowledged operation activation can enable sources.
  Existing local LINK alone confers no portable eligibility. Retained content stays
  protected through the same operation lifetime, including cold recovery.
- Retries/reopen use identical I/Dp/maps/frames/BEGIN/LINK IDs and bytes. Replacing
  stale evidence with newer capture requires a separately admitted new attempt;
  it never rewrites the old one or omits its unresolved content.
- ONE primary quota covers native actual/reservations plus operation actual/future
  data, including I/Dp/stages/directories. ONE 1 MiB/256-entry control allowance covers
  all existing policies/pins/reservations and any proposed protocol commitments.
  Existing serialized descriptor cap16 MiB remains; define precisely how referenced
  metadata counts toward that combined bound, with no indirection escape.
- The later packet must give all-prefix conservation for absent/partial/exact stage/
  final/conflicting files, dirs, BEGIN/CLOSE/LINK and portable artifacts; deduplicate
  CLOSE credit only for the exact matching BEGIN owner. No new broad bypass,
  blanket own-directory exclusion or reuse of credits for different artifacts.
- Unknown/malformed/conflicting or unproved partial evidence blocks portable
  publication/activation; preserve bytes. Fully funded stale/failed preparations
  retain safe ordinary quota-admitted writes under accepted C1 rules. No expiry,
  deletion, repair-by-truncation or successful-output claim follows from accounting.

## Completeness and memory/lifetime requirements

I covers ROOT native graph, captured tabs/order/selection, independent recovery,
preferences, every committed restored-item receipt and all reachable retained scopes,
including provenance. Validate native/private typed graphs, references and metadata;
framing/checksum success alone is insufficient. Preserve per-scope identities even
when native IDs/URIs/sequence/text coincide; retain repeated adoption instances.
Do not invent retained scope/receipt formats while their producer is unavailable.

Every failed/stale/provisional/cancelled attempt is either represented losslessly by
included valid content plus an EXACT field/payload/source-coordinate projection or
blocks whole backup before provider access. Timestamp, cancellation, newer draft,
equal text/hash or successful adoption alone proves no redundancy. Retained originals
and currently edited tabs are distinct. Unknown bytes cannot become fake valid frames.
A blocked outcome with bounded attempt IDs/byte counts is acceptable; preserve data,
expose no content/URIs in diagnostics. Current validated transport copies may be
excluded only through their exact capture binding, with no cleanup permission.

ONE shared16 MiB MetadataBudget covers simultaneously live inventory, map, descriptor,
ledger, typed graph, provenance and retained copies; serialized bounds grant no extra
RAM. Existing limits remain global (1 GiB stream;16,380 ordinary entries;64 scopes;
256 origins;65,536 lineage edges), not per scope; stream with <=64 KiB scratch.
Borrowed inventory callbacks must be synchronous/quiescent or copies/work must have
separate advance reservations. Keep the actual inventory/result owner open through
all borrowed consumers and quiescence. readPortable failure closes its owner internally;
callbacks cannot rely on a future successful result to fund retained/async work.
Close releases logical
charges, never revokes escaped rows or proves GC/heap reclamation. Audit coordinator
ownership before integration; failed owned close/factory leaves activation blocked.

## Dependency gates, concrete acceptance and open questions

- [ ] Root accepts reviewed C1 accounting and C2 native/private producer source,
  activation and source ownership; publish actual contracts before dispatching bridge.
- [ ] Resolve portable schema/domain/artifact commitments and every prefix's quota,
  slot and control conservation; independent wire goldens and compatibility fixtures
  must preserve domain1, local A/B v1, native-map and archive/private-state v1 bytes.
- [ ] Bind zero-native/two empty preparations distinctly; tabs A/B with B active
  remain B after live reorder/switch/edits and fresh-process retry. Missing/foreign
  active identity or sourceTabId duplicates fail before BEGIN/provider access.
- [ ] Exercise every crash boundary and fresh reopen twice; exact retry bytes/IDs,
  partial/conflicting stages, ENOSPC, callback changes and same-byte replacement
  never produce eligibility. Final proof and BEGIN have no mutation gap.
- [ ] Failed older draft plus newer live draft blocks without exact lossless proof;
  unknown staging, missing retained receipt/member and repeated imported scopes
  cannot disappear. Exact complete projection passes without deleting staging.
- [ ] Exact quota/control/metadata limits, simultaneous owners/copies, source open/
  read/close failures and retained borrowed work reject without premature release.
  No second active pin, later UI reread, untracked source or regenerated retry IDs.
- [ ] Independent architecture review resolves this packet before source-ready plan;
  source/fault/lifetime review and actual provider/readback journey remain later gates.

Open contract questions for root/producer owners: What exact C2 immutable capture and
selection validation surface will be accepted? What retained-receipt namespace and
stable enumeration/protection contract exists (or proves absence)? What exact lossless
projection can classify existing failed preparations? Which distinct descriptor/domain
and funding dispatch will be reviewed, and how does combined serialized metadata count
referenced I/maps? Which real activation/freeze/drain API will own all repeatable opens
through quiescence? Until answered, design is reviewable but implementation is blocked.

## Source-ready contract addendum — c3e5e7a baseline, 2026-10-15

This addendum supersedes the unassigned wire/API questions above; it is a source
contract proposal for independent review, not implementation or backup availability.
Local writer/handoff `1fe3c464`, activation `4795c3cd`, and ADR-006 admission `af9eebf9`
are present at base `c3e5e7a460ad4db923c0b3c420a683db977ae018`. Cold cancellation is
owned separately. Rebase its accepted changes before touching Store/Pins/Operations.
No existing A/B/domain1/domain2/archive-v1/archive-v2/schema1 bytes change.

### Frozen version, field and size contract

All new integers are big-endian, lengths/counts nonnegative signed-Java representable;
UUID/hash encodings remain 16/32 raw bytes. Generated IDs are persisted UUIDv4.
`B` means the EXISTING binding field sequence, with phase3 PORTABLE accepted only by
MDOPv2; flags retain recovery bit0/preferences bit1, preferences required. Its length
is `210 + sum(four strict-UTF8 role-basename lengths)`; each name remains <=64 bytes.
`A` is existing77-byte artifact row `(kind:u8,id:UUID,item:UUID,ordinal:u32,length:u64,hash)`.

| Surface | Exact new contract; existing versions remain explicit |
| --- | --- |
| MDOPv2 | Existing magic/header/checksum: `MDOP,u32(2),type:u8,bodyLength:u32,body,SHA256(prefix)`; overhead45; types P1/L2/D3. Existing filenames and stage suffix unchanged. |
| Artifact kinds | map1, descriptor2, inventory3 (`inventory-ID.bundle`), evidence4 (`evidence-ID.capsule`), catalog5 (`sources-ID.catalog`), private6/7/8 unchanged. Kinds1–5 have ZERO item/ordinal0; private identity/order unchanged. |
| Portable Dp | `B,inventoryOuterId:UUID,map:A,rowCount:u32,rows:A[]`; rows kinds3/4/5 then private6/7/8, no descriptor2/self row. `F<=14`, length `65+B+77*(4+F)`. |
| Portable P | `B,directoryMask:u8,artifactCount:u32,rows:A[],exactBEGIN[204]`; sorted kinds1–8, <=19 rows; length `254+B+77*(5+F)`. Masks2/3 retain existing meaning. |
| Portable L | Existing173-byte body/fields, MDOP version2: preparation,operation,P hash,BEGIN hash,Dp hash. No new receipt/control kind. |
| Pin domain3 | `PORTABLE_CAPTURE_V1=3`; existing pin envelope v1, EXPORT/ALL_BUNDLE, empty sources; BEGIN204 and CLOSE126. Manifest count = complete inventory ordinary count INCLUDING evidence; length/hash = exact Dp, never I alone. |
| Inventory | Schema2 uses EXACT schema1 field order and checksum; permits directory/lineage type11 only in addition to1–9. Type11 identity is generated evidence-item ID; zero coordinates in its lineage. Scope/order/active/policy rules unchanged. |
| Archive | Explicit `writeV3` and version3 read dispatch; same inventory-first type10 frame,58-byte headers/45-byte terminal; schema2 mandatory. Type11 = OPERATION_EVIDENCE, payload <=1,073,741,824 bytes, opaque until capsule validation. MDOP artifact4 has that SAME cap; do not apply the8MiB private-frame or65,536-byte metadata-entry cap. V1/v2 reject11/schema2; existing write/writeV2 unchanged. |
| Bounds | One1GiB archive;16,380 ordinary entries INCLUDING capsules;64 scopes;256 origins;65,536 lineage edges;12/1/1 private rows per scope;65,536 total operation namespace slots; existing native/private limits unchanged. |

I is inventory artifact3; its archive outer ID is Dp.inventoryOuterId, distinct from
all directory IDs. Native directory outer IDs reuse frozen native-map archiveIds;
private directory identities equal preflight itemIds; catalog binds their generated
outer IDs to artifactIds. Dp/map/I/catalog total SERIALIZED metadata <=16MiB,
not16MiB each. Evidence's structural header/rows count toward the same bound;
raw control bytes count as metadata too, raw document/data bytes do not. Dp/P remain
<=16,384 bytes individually. Count/metadata exhaustion rejects before P/provider.
Capsule bytes, all other payloads and framing share the SAME1GiB archive total:
`122 + I.length + 58*N + sum(payload lengths) <= 1,073,741,824`. Thus a capsule
at its individual cap cannot fit even alone. Stream/hash/copy/readback capsules >8MiB
with <=64KiB scratch; whole-archive or primary-quota exhaustion blocks the complete
backup before P/provider, never truncates, splits or omits failed scope.

Catalog grammar: `MDSRC01\n` (8 bytes),schema:u32(1),totalLength:u32,count:u32,
then outer-UUID-sorted97-byte rows `(outer:UUID,route:u8,owner:UUID,member:UUID,
offset:u64,length:u64,hash)`, then SHA256(prefix). Length `52+97*N`; N equals I's
ordinary-entry count. Routes1=NATIVE,2=OWN_FRAME,3=OWN_EVIDENCE,4=RETAINED.
NATIVE owner is local bundle/member is native embedded ID; I.type disambiguates
transaction roles. OWN owner is preparation/member is artifactId; offset must0.
RETAINED owner is trusted LOCAL adoption receipt ID/member is its scoped item ID;
resolve through that receipt only. No path/URI, imported catalog or hash grants routing.
Every row must join exactly one inventory row and exact local immutable source;
length/hash/embedded identity and native map membership agree before BEGIN.

### Lossless failed evidence, without pretending partial bytes are valid content

Persist one aggregate capsule even when empty: `MDEVID1\n` (8),schema:u32(1),
totalLength:u64,rowCount:u32,rows, SHA256(prefix); empty length56. Each row is
`role:u8,ownerPreparation:UUID,nodeId:UUID,kind:u8,state:u8,nameLength:u16,
name:UTF8,declaredLength:u64,declaredHash[32],actualLength:u64,actualHash[32],
futureBytes:u64,futureSlots:u32,relation:u8,rawLength:u64,rawBytes`.
Fixed row overhead is138 bytes plus name/raw bytes. Names are canonical single
basenames <=255 strict-UTF8 bytes; never paths. Rows sort by role, unsigned owner
UUID, name UTF8 bytes; duplicates fail. rowCount<=65,536+260; all count limits also
charge the existing source ledger/control namespace, never a fresh allowance.
Roles1=operation control,2=operation data,3=namespace directory,4=source context.
For role1 kind is P1/L2/BEGIN3/CLOSE4; role2 uses its declared artifact kind; directory
kind0. Role4 kinds1=enrollment,2=registration,3=policy,4=C0 preimage,5=ledger preimage,
6=graph preimage,7=native-source preimage; all use the SAME row grammar, state2,
ZERO owner/node, declared=actual exact bytes/hash, futureBytes/futureSlots0, relation0.
States0=absent,1=directory,2=committed file,3=stage. ZERO owner/node only where the
namespace root, source-context row or verified closed legacy pair has no preparation
identity; control node is persisted preparation/operation ID as appropriate. Role1
legacy BEGIN(kind3)/CLOSE(kind4) rows use ZERO owner, node=operation UUID, state2,
exact declared=actual/raw bytes and hashes, futureBytes/futureSlots0, relation0.
Allow ONLY a canonical completed domain1 pair: matching canonical filenames/UUID,
BEGIN purpose/scope/source shape valid under HistoryPinRecord, CLOSE binds that
BEGIN envelopeHash and manifestHash with valid RELEASED/CANCELLED outcome, no P
association, unmatched member or conflicting/staged control. Their registration and
BEGIN generation/policy hash must resolve through the included full raw context chain;
C0 witness retains both legacy rows; ledger witness preserves its unchanged operation-only hash-input sequence; capsule rows preserve raw legacy bytes. An unavailable old native manifest
is a preserved historical hash commitment, never activation proof. Every other
unowned/unclassifiable pair refuses whole backup. Unknown kinds/shapes remain blocking.
Absent/directory rows have raw/actual length0 and SHA256(empty); absent declaration
hash is32 zero bytes only for directories. File rawLength=actualLength and bytes/hash
are exact physical observations. Future byte/slot counters reproduce the typed
source-ledger reservations; actual plus future preserves each original commitment.
Relation0=UNKNOWN,1=PROVEN_EXACT_PREFIX; relation1 requires actual comparison with
available final bytes. ADR-006 bounded stages normally use0: final hash/length does
not prove a prefix. Never pad, reconstruct, truncate or promote unavailable content.

The frozen baseline covers EVERY preexisting operation control and operation-data
node, including committed cancelled P/BEGIN/CLOSE, partial stages, missing artifacts,
missing directories and unused credits. Include their raw metadata/content losslessly;
only a complete field/byte/source-coordinate projection may replace duplicated copies.
Current attempt's own P/Dp/I/catalog/capsule/copies/BEGIN/LINK are tagged
ACTIVE_OWN_TRANSPORT by exact preparation/artifact commitments and omitted from the
capsule to avoid recursive hashes. This affects export projection ONLY: full ledger,
quota, C0/L0 checks and physical transition proofs still include every own entry.
An older linked capture, equal bytes, newer draft or CANCELLED state is no omission
proof. Unclassifiable evidence blocks the whole backup before destination access.
Import retains capsules byte-exact in an admitted, inert quarantine with their source
scope/origin and inventory item ID; no replay into local controls, pin creation,
policy enrollment, URI authority, source-ID routing or deletion permission follows.
Re-export includes quarantined capsules unchanged as distinct retained items.

Source context is mandatory even with no older attempt: raw `history_enrollment.control`,
`registration.control`, and EVERY `policy-%016x.control` generation1→latest predecessor
chain, preserving all original bytes/names (registration itself supplies generation0).
Include all other source-control entries as their typed rows; maintenance/unknown
control still blocks portable admission. No latest-policy substitution is permitted.
Also retain exact byte preimages fed to existing `initialControlHash`, ledger fingerprint,
HistoryGraph fingerprint and NativeCaptureFingerprint, using fixed basenames
`baseline-c0`, `baseline-ledger`, `baseline-graph`, `baseline-native`; no new hash domain.
These bounded metadata witnesses preserve original sorted ledger identity strings,
physical observations/root names, totals/credits and policy binding. Validate C0 and
L0 against the frozen baseline before P; hash ledger/native witnesses to B.ledgerHash/
sourceHash through their EXISTING conversions, graph witness to native-map.graphHash.
Typed replay cross-checks every control and operation row, native inventory/member hash,
reservation and policy edge; opaque identity/path observations never become imported
filesystem authority. Historical P.sourceHash/ledgerHash whose original preimages were
never persisted remain opaque preserved historical commitments, not reconstructed
source/activation proofs. Validate their available canonical controls/policy references
and physical accounting only; UNKNOWN final-prefix relations remain UNKNOWN. This is
a capsule context section using existing hash inputs, not a new replay/authority layer.

### Concrete producer, owner and consumer chain

1. `MainActivity.capturePortableSelection()` is a NEW main-thread-only entry:
   freeze ordered original `DocumentTab.id`, scalar fields, active original ID (null iff
   empty), recovery and eight preferences under one capture read lease. Do NOT call
   current `captureActiveTab()` or copy whole text: its Editable.toString duplicates
   an8MiB document. Borrow existing inactive immutable String payloads; pin the active
   Editable through a NEW narrow main-thread read adapter with mutations excluded.
   Before taking the lease, finish composition preserving all already accepted text;
   if it cannot settle safely, refuse capture. During the lease disable editing and
   reject mutating IME/key/programmatic text/tab/recovery actions before acceptance,
   with one accessible busy indication; never acknowledge then discard input. No text,
   Runnable or arbitrary event payload is queued. Refreshes use at most one fixed
   invalidation bitmask and revalidate after release. Worker frame factories request
   main-thread getChars chunks
   <=16,384 UTF16 units, strict-encode with surrogate carry, stream existing v1 frames
   for preflight and writer second pass; no worker accesses Editable, no history lease
   waits on UI, no second full String/byte[] or unfunded pre-P spool exists. Metadata/
   selection never rereads later UI. Immutable capture includes the read lease rather
   than a copied payload. Release it only after all source readers actually close and
   final copy is durable and PreparationSources freezes further opens; uncertainty
   retains ownership until proved drain, without
   clearing/resetting PreparationSources. Each row retains original sourceTabId.
   Device IME/composition/accessibility proof remains NOT RUN; implementation needs
   host accepted-input/rejection/queue-bound checks before exposing this action.
2. Add `PrivateCapturePlan.createPortable(File store, File catalog, File identity,
   File index, HistoryArchiveManifest frozen, HistoryGraph.Graph graph,
   HistoryControlStore.Inspection control, PrivateOperationStore.Ledger baseLedger,
   PreparationSources owner, UUID preparation, UUID operation, UUID mapId,
   UUID descriptorId, List<PrivateFramePreflight.ClosedFrame> frames,
   long bootstrapQuotaBytes, PortableSelection selection, PortableInputs inputs,
   HistoryStorage.Budget sharedBudget)`; existing create stays local. Inputs owns exact frozen I/catalog/capsule sources,
   retained receipt membership and generated IDs; no provider callbacks. Preflight
   existing PrivateStateCodec frames via existing PreparationSources/ClosedFrame;
   compare original sourceTabId and its existing sourceTabIdHash, reject duplicate/
   foreign selection, map active original ID to its one inventory tab entry BEFORE P.
3. `PrivateCaptureWriter.prepare`/write/copy/`commitCapture(HistoryPins)` gain only
   version-dispatched portable rows/sinks. Extend authentic CapturedBinding with
   persisted version/domain; no public reconstruction from DTO/hash. Keep original
   owner/Budget, freeze→claim→final proof→BEGIN→LINK→one-use handoff under same lease.
   No second pin, later selection capture or reset of uncertain owners.
4. Store/Pins validate v2 artifacts and Dp→I→catalog→typed per-scope graph joins;
   source table includes inventory outer ID as a separately declared tracked input.
   Existing `HistoryOperations.activateCaptured(binding,budget)` returns the handle;
   existing `openCapturedSource(handle,item)` supplies every declared native/private/
   evidence/I source. Retained route4 requires exact local receipt and operation-owned
   protection through drain; if receipt namespace is absent, prove absence locked.
5. NEW `PrivateBackupCoordinator` owns the above flow, builds ArchiveSource in I
   order. After activation/semantic preflight, request SAF destination and call
   writeV3 with tracked inventory/sources; own output flush/actual close, fresh
   readback, summary comparison, freeze/drain and exact capturedManifest→CLOSE.
   Failures retain evidence/lifetime; framing success alone never displays success.
6. NEW `PrivateRestoreCoordinator` stages readPortable v1/v2/v3 provisionally, validates
   PrivateStateCodec and strict native graph/reference/manifest joins, capsule grammar
   and completeness, then persists a deterministic remap/adoption receipt before any
   visibility. Persist remap by (source scope,native namespace,identity), with one
   transaction namespace for prepare/completion; distinct outer IDs stay distinct.
   Repeat adoptions get fresh scopes. `PrivateRestoreReceipt` owns trusted local routes;
   imported source IDs/policy/URIs/catalog stay inert. Strict readers, admitted protective
   copies and explicit restore-to-new-copy feed existing history picker. Additive
   read-only retained drafts exceed12 without eviction; opening a draft needs a free
   live slot. Apply preferences only by a separate explicit action; no user-file writes.

### One metadata owner, affected proof and next file ownership

The coordinator retains ONE original HistoryStorage.Budget(16MiB). Add a charging
constructor/adapter to PrivateBundleInventory.MetadataBudget that debits that same original budget
before reservations/allocations; close may release codec-local use but must not
restore the parent monotonic debit. Internal prepaid child workspaces may spend only
already charged amounts. Do not construct a second full-limit budget per helper.
The current readPortable v1 branch reserves ALL16MiB before calling read: replace
that branch with an internal `readV1Budgeted(InputStream, LegacySpool, MetadataBudget)`
using the existing v1 framing loop and incrementally prepaid index, not the old
whole-limit reservation. Reserve128KiB fixed reader/wrapper/transfer workspace before
allocation, then384 bytes PER entry before allocating its UUID/index node/growth
workspace. Keep original MAX_ENTRIES/MAX_INDEX_BYTES, duplicate/header/type/hash/
terminal/EOF checks and provisional callback/closure semantics; public v1 wire bytes
and write API stay unchanged. PrivateArchive owns this reader/index change in A;
coordinator and spool separately precharge their simultaneous state to the SAME
parent. A small valid v1 must stage→validate→adopt with nonzero coordinator charges;
large actual index/metadata exhaustion must refuse truthfully without resetting the
budget, omitting entries or granting a second allowance. Test fixed/per-entry boundary
failures plus small-v1 success; no v1 dispatch may retain the16MiB upfront debit.
Simultaneous charge includes capture leases/scalar snapshots/bounded text chunks, graph/control/ledger and
physical anchors, native map/fingerprint, all record clones/encoding transients,
I/capsule/catalog owners/indexes, typed validators/remap/provenance, source tables,
preflight512KiB plus attempts, writer transitions/refresh work, readback overlap and
tracked wrappers/FDs. Streams <=64KiB scratch; existing codec65540-byte carry remains
explicit. Inventory/result owner survives all borrowed callbacks and quiescence;
failed open/close retains uncertainty and its owner, never releases for early retry.

Minimal affected proof before UI: measured conservative retained-object bounds for
this simultaneous owner set, charged boundary refusal before P, one capsule streamed
without payload arrays, source-table/open/readback peaks, borrowed rows across failure,
and child-workspace/prepaid accounting. Serialized limits and logical close are not
heap proof. Existing canonical editor/String payload heap is baseline document storage,
not a new capture metadata copy; measure baseline plus bounded adapter/chunk/frame
workspace with one8MiB ASCII tab and a multi-tab fixture. Both must complete capture,
activate/read and stream export within the shared metadata limit, without whole-text
allocation or mutation escaping the read lease. Whole-heap/performance/migration/
regression batteries remain feature gates.
Next sequential ownership (class names denote exact `.java` basenames in the existing
source package): A owns PrivateOperationRecord, HistoryPinRecord, PrivateBundleInventory,
PrivateArchive, NEW PrivateOperationEvidence and wire fixtures. B owns NEW PortableSelection,
PrivateCapturePlan, PrivateCaptureWriter, PrivateFramePreflight, PreparationSources,
HistoryStorage budget adapter. C, after cold-worker integration, owns PrivateOperationStore,
HistoryPins, HistoryOperations and retained-route protection. D owns NEW
PrivateBackupCoordinator/PrivateRestoreCoordinator/PrivateRestoreReceipt, extracted
strict native readers, then MainActivity capture/action/retained-draft picker only.
Keep these within the existing draft integration chain; no generic framework layer.
Minimum changed-path tests: independently encoded v1/v2/v3+MDOP goldens; tab A/B with
B active after live switch and fresh retry; cancelled missing-final stage UNKNOWN
roundtrip; exact metadata/quota/control/slot prefix boundaries; foreign catalog/graph
and scoped-ID collisions; repeated additive restore→export preserving inert evidence;
>8MiB capsule streaming, historical policy-chain omission and capture-lease mutation refusal.
For each new artifact/control size n: absent `(actual0,future n,slots0+1)`, stage k
`(k,n-k,1+0)`, exact final `(n,0,1+0)`; control P itself preadmitted, not self-funded.
BEGIN/LINK/CLOSE sizes204/173/126 and ONE matching CLOSE credit remain unchanged;
partial/conflicting controls reject, data stages retain UNKNOWN relation, duplicate
stage+final rejects. Golden prefixes include k=0,1,n-1,n and pre/post-dir-mask states,
ENOSPC/readback failure and two fresh reopens; no old credit funds new publication.
No tests/build/source/UI/push/PR ran in this packet. Version3/schema2 and the evidence
representation are explicit engineering review boundaries; ADR-005 additive defaults
resolve product behavior. No further product decision or owner permission is needed.
