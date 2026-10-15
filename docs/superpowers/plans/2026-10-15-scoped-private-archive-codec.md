# Scoped private archive codec — proposed schema and implementation plan

Date: 2026-10-15. **Schema c279dfaa approved; pure codec implementation authorized. No source/test pass yet.**
**Goal/architecture:** bounded v2 scope inventory plus unchanged native/private frames;
v1 remains unchanged. No adoption, ID generation or provider authority.
**Tech stack:** Java11-compatible production classes, installed JDK17; no dependencies.
**Spec:** [ADR-005](../../decisions/ADR-005-portable-private-bundle-format.md), source
`620275b6cc0aae6d139c353d16fd1d3ec26200b9`; the companion operation-data-capture plan
owns commitments/persistence/quiescence. This document implements none of them.

## Encoding primitives and version dispatch

All fields below are in listed order, with no padding or unspecified extensions.
`B/U/Q` = unsigned8/32/64 big-endian; reject Q above Long.MAX_VALUE and all tighter
bounds before allocation. `G` =16 raw UUID bytes, `H` =32 raw SHA-256 bytes.
`V` = G with UUIDv4/RFC4122 variant2; `N` = arbitrary native UUID bits, including
non-v4/zero IDs when allowed by native canonical-UUID validation. `S` = U byte count
then strict UTF-8; 1..4096 bytes for source-coordinate strings, no normalization.
`refs(n)` = n V fields. SHA-256 provides integrity, not authenticity or permissions.
UUID ordering compares all 16 bytes unsigned lexicographically, never UUID.compareTo.

Existing `PrivateArchive.write/read` remain v1-only, accepting exactly types 1..9.
A proposed `readPortable` examines the 19-byte header with bounded prefix replay:
version 1 delegates to unchanged v1 framing, version 2 selects the scoped parser;
other versions fail. Use a non-owning custom prefix stream, never SequenceInputStream
(which closes exhausted components). No dispatch path closes caller input.
V1 reserves the entire 16 MiB shared metadata budget before unchanged delegation;
reservation fails unless that budget is idle. Its private Index stays fully charged.
Trusted preadmitted LegacySpool callbacks stream descriptors/payloads to staging;
retain no Entry/index/metadata or invoke ordinary callbacks. Only bounded fixed
streaming scratch is allowed. Settle every nonnull begin once; release credit finally.
Success returns original source ArchiveSummary plus PROVISIONAL spool handle;
no staged-byte/durability claim. After return, same-budget replay verifies descriptor
order/type/ID/length/hash and payloads against source summary before typed validation.
Failure leaves provisional staging, no result. No synthetic scope/policy.
Caller owns spool format/admission/closure/durability; this codec defines no adoption.
A new explicit `writeV2` alone emits version 2. Do not add 10 to the v1 Type decoder.

V2 retains magic `4d4445534b4241434b55500d0a1a0a` + U(2), length 19.
Entry = B(1), B(type), V(entryId), Q(payloadLength), H(payload), payload:58+length.
The first and sole type 10 is BUNDLE_INVENTORY; its payload is <=16 MiB. Ordinary
types 1..9 keep existing caps: types 1/6/7 <=8,388,608+32,768, others <=65,536 bytes.
Terminal = B(0), U(E+1), Q(precedingLength), H(all preceding bytes):45 bytes, then EOF.
Summary hash covers the whole stream including terminal. No nesting/compression.
Type10 is proposed v2 framing, not an existing v1 type or native envelope kind.

## Inventory schema 1: exact fields

Payload begins `4d4442554e444c0a` (8 bytes, actual LF, not backslash+n), followed by:

| Offset | Field |
| --- | --- |
| 8 | U(schema=1), U(L complete inventory length including checksum) |
| 16 | V(preparationId), V(rootScopeId) |
| 48 | U(B scopes), U(E ordinary entries), U(O origins), U(GE lineage edges) |
| 64 | Q(P sum ordinary payload lengths), Q(A complete archive length) |
| 80 | B scope rows, E directory rows, O origin rows, GE lineage rows |
| L-32 | H(inventory bytes 0..L-33); require exact EOF |

Limits: 1<=B<=64, 0<=E<=16,380, 0<=O<=256, 0<=GE<=65,536; all totals are global.
Tables order by their own V key, strictly increasing; no duplicate table keys.
Nested UUID reference lists sort strictly increasing, except tab order (semantic).
References resolve after all inventory tables/checksum validate; no callbacks yet.

**Scope row** (base 32 bytes): V(scopeId), B(kind:1 ROOT/2 RETAINED), B(flags),
U(originRefCount), refs, U(ordinaryEntryCount), B(activePresent), [V(activeEntryId)],
U(tabCount), tab entry UUIDs in captured order, B(policyKnown), [policy fields].
Flags bit0=native graph present, bit1=private-state set present; all other bits zero.
Empty present sets are explicit. ROOT flags=3; retained flags 1..3. Exactly one ROOT,
matching rootScopeId. originRefCount<=O; tabCount<=12, unique and exactly all type 6
entries of that scope. activePresent is 0 iff tabCount=0, otherwise 1 and names a tab.
Both optional markers accept only 0/1. Policy fields when known are N(sourceBundleId),
Q(generation), Q(quotaBytes), U(automaticCount), H(policyRecord):68 bytes.
Known policy requires native-present flag; legal quota MiB 64/128/256/512/1024 and
count 0/5/10/20/50. Unknown has no bytes, not inferred defaults. All policy is inert.

**Directory row** (base 93 bytes): V(entryId), V(scopeId), B(type 1..9), Q(length),
H(payload), G(identity), U(lineageRefCount), sorted lineage UUIDs.
For types 1..5/9 identity=N embedded native envelope ID; type determines native role
(store types 1..3, catalog type 4, identity type 5, index type 9). For types 6..8 identity=V private item ID,
not sourceTabId. Assign private item IDs once in persisted capture/receipts; the
codec never derives them from strings or generates IDs. lineageRefCount<=GE.
Entry IDs are globally unique including the inventory frame ID (which is absent from this directory). All other ID domains are
independent; equal bits across domains convey no equality or source authority.

**Origin row** (base 68 bytes): V(originId), H(sourceArchive), V(sourceScopeId),
U(parentCount), sorted parent origin UUIDs. parentCount<=O-1. Source scope is a
historical coordinate, not a current-scope reference. Require present parents,
acyclic graph, no self-edge. Every origin must be reachable from a scope origin
reference or a lineage origin (including ancestors); reject unattached metadata.

**Lineage row** (base 69 bytes): V(edgeId), V(currentEntryId), V(originId),
B(sourceType), G(sourceIdentity), U(textCoordinateCount), sorted text coordinates.
sourceType must equal current entry type. sourceIdentity=N for native, V otherwise.
Each text coordinate = B(tag), S(value), with unique increasing tags; count<=2.
Required tags: types 1/2/3/4 tag 1=documentId; type 9 tags 2/3=original/attempted documentId;
type 5 tag 4=TAB alias iff its preserved namespace is TAB, else none; type 6 tag 5=
sourceTabId; types 7/8 none. For type 5, inventory parsing permits zero/one tag 4;
the later native-payload gate enforces the TAB-namespace condition. Strings retain
exact source values including literal `legacy-recovery`; they are not UUIDs. This makes remapped textual coordinates
portable as well as native UUIDs. Every edge belongs to exactly one directory entry;
there is at most one edge per (entry,origin). For each (currentScope,origin,mappingDomain),
currentIdentity→sourceIdentity is functional and injective. Domain is shared for 2/3
and for 6..8; other native types have separate domains. Thus the two transaction frames
must agree on current/source transaction IDs, and private items cannot alias by type.
The typed gate also validates source-kind/reference/text consistency. Ancestor claims
remain inert; they alone never prove lossless projection or permit staging omission.

Lengths: let Rb/Rt/Re/Rp count scope-origin/tab/entry-lineage/origin-parent references,
C active scopes, K known policies, T sum(5+UTF8length) across lineage text fields.
`L = 112 + 32B + 16(Rb+Rt+C) + 68K + 93E + 16Re + 68O + 16Rp + 69GE + T`.
`P=sum(length)`, `A=122+L+58E+P<=1,073,741,824`. Checked addition/multiplication
only; declarations must equal measured consumption. No self-hash or circular total.

## Membership, scope and source-coordinate rules

Before ordinary callbacks validate inventory length, internal checksum AND outer
payload SHA, all crossrefs/counts and computed total declarations. Inventory/results
remain provisional through terminal+EOF. Ordinary wire frames follow directory
entryId order exactly. Compare all UUID/type/length/hash fields to the next row;
reject missing/extra/reordered frames, another type 10, premature terminal or trailing
bytes. Counts/totals include all scopes; ordinary entry counts per scope must agree.
Native-present flag gates types 1..5/9; private-present gates6..8. Each scope has
<=12 type 6, <=1 type 7, <=1 type 8; more than12 retained drafts across scopes is valid.
Native key `(scope,type,identity)` is unique. Preparation and completion share one
transaction identity but have separate types and outer entry IDs. Private item IDs
are unique within a scope across types 6..8, and may repeat in distinct scopes.

Native payload validation remains a separate strict no-mutation gate before adoption:
magic/version/kind/embedded ID/checksum/EOF, complete per-scope graph references,
preparation/completion equality, checkpoint/identity lineage/index metadata and
sequence constraints. Snapshot text/BOM/CRLF is never normalized. Decode private
frames via PrivateStateCodec; require inner kind match and distinct sourceTabId
strings within each scope, valid UTF-8/cursors and the existing eight-key allowlist.
Framing success does not claim typed graph validation or completeness of local data.

Native `documentId` is an opaque nonempty <=4096-byte string, not a native UUID;
TAB identity aliases and private `sourceTabId` are also strings. Import maps textual
identities by `(scope,exact string)` only where native relationships require it.
Do not interpret `legacy-recovery` as malformed UUID or attach it to a live tab.
Persist its original coordinate; imported read-only views retain recovery provenance.
No equal payload, URI or timestamp merges scopes/items. Native URI/title, labels,
revision/timestamp and other unmapped fields remain exact historical observations.

Import maps `(scope,native domain,UUID)` deterministically from its persisted plan:
shared transaction domain for types 2/3, snapshot domain for1, checkpoint4, index9,
and identity-record/history relationships for5. Remap all references, base identity
ID=history ID, FORK origins, TAB aliases, and duplicated snapshot metadata in INDEX;
never treat duplicate index metadata as independent identities. Private item UUID
and target live tab UUID are separate mappings; sourceTabId stays provenance.
Every transformed textual coordinate has the required lineage field above, and
every mapped native reference resolves through that origin's typed source coordinates.
Nested provenance retains old origin nodes/source coordinates; byte-identical origin
nodes may be shared, conflicting same-ID nodes fail. Map `(adoption instance,edgeId)`
to unique exported edge IDs when copying edges to new entries/scopes; otherwise two
imports of one archive collide. Persist all edge/entry/scope mappings before writes;
reopen uses exact mappings, never fresh generation. A repeated import creates new
local scopes even when source archive/native IDs/URI/sequence/sourceTabId repeat.
Codec cannot validate an unavailable ancestral archive; origin claims remain inert.

## One combined retained-metadata budget

Keep the ADR's 16 MiB index ceiling, shared by codec plus typed-validation/remap
participants while simultaneously live. Initial conservative charge:
`4096 + 4096B + 1024(E+1) + 256O + 128GE + 32(Rb+Rt+Re+Rp)`.
Add `64+2*declaredUTF8Bytes` per retained string copy, plus any extra arrays/containers
or validator objects not covered by fixed slots. Retained fixed tables/index arrays
must fit prepaid row slots; no unmetered graph copies. Charge before allocation; count
simultaneous copies. Fixed entry slots include framing uniqueness, directory and
native-key bookkeeping; do not also create an uncharged legacy HashSet/index.
Inventory is streamed, not first copied into a 16 MiB byte array. One 64 KiB transfer
buffer and bounded scalar/hash scratch are separate fixed streaming overhead;
charge additional decoding/raw-string buffers to the metadata budget while live.
No payload arrays. Process scopes sequentially/release temporary maps. Budget
shortfall rejects the whole archive/capture; never silently drop members/provenance.

Native root caps and document 8 MiB limits still apply. Inventory<=16 MiB and the
persisted combined pin descriptor<=16 MiB are independent serialized bounds; neither
grants extra RAM or a new control allowance. Existing 1 MiB/256 control includes all
pins/receipts/reservations. The operation-data plan owns disk accounting and leases.

## Independently packed proposed fixtures (zero-based offsets)

These are design arithmetic/golden candidates, computed with Python struct/hashlib
without calling a production codec. They are **not** v1 compatibility or decoder
passes. `q(n)=00000000-0000-4000-8000-` followed by 12-digit lower-case hex(n).
Both use prep=q(1), root=q(2), inventory frame=q(3); one ROOT flags 3, no origins,
lineages, tabs/active entry or policy. Lists empty and counters zero unless stated.

**Empty:** E=0, P=0, L=144, A=266. Inventory offsets 80 scopeId,96 kind 1,97 flags 3,
98 originCount 0,102 entryCount 0,106 active 0,107 tabCount 0,111 policy 0,112 checksum.
Archive offsets: header0..18; inventory header19..76; payload77..220;
terminal221..265 declares count 1 and precedingLength 221.
Inventory checksum(last 32) = `ee3e0304d1d47b24e7cec1aaa686ca6af1e8ef284a1aef58c4df5cba393c30ae`.
Whole inventory SHA = `f4c06cc6183e4002279e60abb13a20cbb8ca7bd712e59727cedd82e41bc9680c`.
Whole archive SHA = `610ef92d1a44d0d330d85b17ed2e594fbb925d437ec0aee57d173f3dd85173f2`.

**Native+private:** E=2, P=275, L=330, A=843. Directory rows at 112 and 205, checksum 298.
First row: entry=q(4), type 1, identity=`00000000-0000-0000-0000-000000000007`, length 218.
Second: entry=q(5), type 8, identity=q(6), length 57. Scope entryCount=2, others as empty.
Native snapshot body uses existing v1 serialization, in order: native ID string above,
`legacy-recovery`, null URI, empty title, revision 0, `attempted`, timestamp 0,
lowercase SHA-256 of payload as 64 ASCII hex characters, byteCount 5, bytes `efbbbf0d0a`.
Strings use signed int32 byte lengths (null=-1). Body 173; native header is
`4d445053 00000001 01 000000ad`; append body then SHA-256(header+body), yielding 218.
Payload SHA = `f01a374e9c81e3db89b3a42940c4d6a5447684986a1296e42bf13f196eed6295`.
Snapshot SHA = `1f02c2934746abf0c7373b94ca38c38fc996d237bb2f6b95aaa23e08a8a26dc4`.
Private preferences prefix is `4d4453544154450a 00000001 08 00000004 00000000 00000000`;
append its SHA-256, yielding 57 (empty allowlist). Prefs SHA =
`41f1667e50ecc306fa19fd3bf0bca6d7bec72dad64cd7534825f2bbc34e5cacd`.
Archive: inventory payload77..406; snapshot header407..464/payload465..682;
prefs header683..740/payload741..797; terminal798..842 declares count 3/length 798.
Inventory SHA = `2bbf872d9aeebeaf9823b1df23cb07f38ad4133c7f828392cd1e71e663283100`.
Whole archive SHA = `9be88cf0fe6ee6b1a4fb1355fde9dcb2467495a0f87ba003a633fe713b46e3ac`.
This orphan attempted snapshot is legitimate; the format must not invent completion.

## Proposed code ownership, interfaces and verification gates

Create `PrivateBundleInventory.java`: pure canonical inventory read/write, immutable
rows and shared `MetadataBudget`; no files/ID generation. Extend `PrivateArchive`
with explicit v2 routines and `readPortable`, keeping v1 interfaces and enum intact.
Proposed `writeV2(UUID inventoryId, StreamOpener frozenInventory, ArchiveSource,
OutputStream, MetadataBudget)` returns ArchiveSummary. Parse/hash the exact persisted
inventory and recheck its bytes on write; stream ordinary repeatable sources in order.
Proposed `readPortable(InputStream, ScopedSink, LegacySpool, MetadataBudget)` returns
v1(source summary, provisional spool) or v2 inventory retained under budget until closed.
ScopedSink receives v2 entries only. Every nonnull begin has exactly one finish, even
on copy/hash failure; primary failure wins with finish failure suppressed. Caller
owns outer streams, codec owns source streams; no receipt/outer flush/close/pin release.
The local MDOP v1 descriptor cannot silently receive this inventory: a separately
reviewed bridge/version must bind its exact identity before output eligibility.

- [ ] Task1: independently encode fixtures above and positive scope/policy/lineage
  fixtures; assert every field/offset/hash and negative exact-bound/overflow failures.
- [ ] Task2: implement pure inventory codec and shared budget only after approval;
  reject duplicate/missing refs, cycles, wrong optional markers and every truncation.
- [ ] Task3: implement explicit v2 framing/dispatch; verify inventory membership and
  terminal/EOF, changed second-pass sources, source/sink closure failures and zero
  ordinary callbacks for invalid inventory. Assert outer-close count 0 on v1/v2 success,
  failure and truncated dispatch; legacy full-budget reservation excludes other users.
- [ ] Task4: unchanged actual v1 archive/state/manifest fixtures must pass; additionally
  v1 rejects version 2/type 10, v2 rejects misplaced 10, and native arbitrary UUID/string
  identity domains remain intact. No v1 golden pass is claimed by this document.
- [ ] Task5: bounded-heap adversarial tests charge simultaneous graphs/strings/edges;
  repeated scopes with same native IDs/URIs/sequences/sourceTabIds and >12 retained
  drafts remain distinct; per-scope duplicates fail. Verify exact aggregate limits,
  multibyte 4096-byte coordinates, literal legacy-recovery and denormalized index links.

Future runner: `verification/scoped_private_archive/run.sh`, then affected existing
archive/state/manifest runners and independent review. No decoder/device/release pass.
