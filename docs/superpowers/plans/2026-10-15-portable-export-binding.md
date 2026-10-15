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
