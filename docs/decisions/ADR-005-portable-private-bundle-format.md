# ADR-005: Portable scoped private bundles and complete backup inventory

Date: 2026-10-15. Status: **architecture direction and scoped codec schema reviewed; implementation unverified**.
Independent review of `d839225` supports scoped v2 inventory, additive adoption and
bounded completeness. Later independent review approved codec schema `c279dfaa`;
root authorized that pure codec implementation. Reservation protocol, mutation/read
factories, MDOP bridge, adoption, actual source and test evidence remain separate gates.
The later operation-data/capture plan `7a6c72b3` also received independent
architecture approval and root implementation authorization. It defines exact
MDOP reservation/schema/credit transitions within the existing primary allowance;
source correctness and runtime verification remain unestablished.
Source examined: `620275b6cc0aae6d139c353d16fd1d3ec26200b9` and design `b2699bb`.
Scope: the portable format missing from the
[private backup/adoption design](../superpowers/plans/2026-10-15-private-backup-adoption.md).
Retain [ADR-001](ADR-001-safe-save-boundary.md), [ADR-002](ADR-002-retention-journal.md)
and [ADR-004](ADR-004-operation-lifetime-and-close-recovery.md) safety requirements.
This ADR neither activates export/import nor changes package/version/device gates.

## Source-backed problem

[`PrivateArchive`](../../apps/MarkdownDesk/src/com/example/markdowndesk/PrivateArchive.java)
currently accepts version **1**, types **1–9**, globally unique outer UUIDv4 IDs,
58-byte entry headers, and a 45-byte terminal count/length/SHA-256. It rejects
unknown version/type, duplicate outer IDs and bytes after the terminal. Its
terminal has no native bundle, native identity map, preferences schema or policy.
Entry payloads are opaque; `read` callbacks are provisional until full EOF verifies.
`write` hashes and streams each repeatable source separately, without flushing or
closing caller output. The existing 16 MiB framing-index budget charges 384 bytes
per entry; its 65,536 entry ceiling is not the effective memory bound.

[`HistoryArchiveManifest`](../../apps/MarkdownDesk/src/com/example/markdowndesk/HistoryArchiveManifest.java)
is a separate local frozen mapping, capped at 16,380 native entries and 16 MiB.
It is not embedded by v1. Preparation/completion share a native transaction UUID,
so even one native bundle already needs distinct outer IDs for those two entries.
Native URI aliases and ownership sequences are only unique inside their bundle.

The proposed adoption design keeps imported histories in isolated read-only
bundles and retained drafts outside the 12 live tabs. A later backup must include
these items without merging URI aliases, native UUIDs or sequence namespaces.
Global SESSION_TAB/RECOVERY/PREFERENCES limits of 12/1/1 cannot describe several
retained workspaces. Nested import provenance must survive repeated restoration.

## Alternatives and recommended decision

| Approach | Source impact and consequence |
| --- | --- |
| **V2 explicit inventory frame, same ordinary entry headers** | Recommended. New version dispatch and one typed manifest frame; types 1–9 retain payload/framing. Manifest assigns each entry exactly one scope before payload callbacks. Single global terminal and one global budget. |
| Outer directory plus length-delimited embedded v1 archives | Reuses v1 readers for each bundle, but needs a new container, child archive IDs/digests, global limits and provenance schema anyway. Nested read/write summaries and repeated hashing complicate tracked closure and global budgeting. Raw child archives duplicate staging and invite recursive expansion. Not selected. |
| Infer bundles from native UUIDs/URIs or hide metadata in INDEX/PREFERENCES | Invalid: collisions are legitimate across bundles, v1 typed validators reject invented payloads, and equal content cannot establish identity. |

Propose version **2**, with type **10 = BUNDLE_INVENTORY** explicitly reserved
**only in the new version**. Type10 does not exist in current source. Keep v1
reader/writer behavior and fixtures unchanged; no new writer emits v2 implicitly
through the current API. Unknown future versions/types remain hard failures.
A new typed v2 adapter uses the existing streaming framing primitives where safe;
do not weaken `Type.decode`, `validate` or EntrySink's provisional contract globally.

## Wire structure and inventory

Use the same 15-byte magic, big-endian version field and terminal envelope.
V2's first frame must be the sole type 10 inventory; it uses the same 58-byte
header and a new inventory-specific payload cap of 16 MiB. Other metadata types
retain 65,536-byte framing caps; document entries retain 8,388,608+32,768.
The inventory is checked before ordinary entry callbacks, but remains provisional
until every entry, terminal and EOF verifies. There are no nested archives,
compression, filesystem paths, optional unknown frames or extension skips.

Proposed inventory payload magic is eight ASCII bytes `MDBUNDL\n`, then uint32
schema=1 and uint32 serialized length. Section counts/string lengths use uint32;
byte totals/quota/generation use uint64; kinds/optional-value markers use uint8
with exact enumerations (optional 0 absent, 1 present). Hashes use 32 raw bytes, UUIDs
use 128 bits and strings strict UTF-8 <=4,096 bytes. Enforce signed-Java representable
ranges and the tighter bounds before allocation. Append SHA-256 of all preceding
inventory bytes. The codec plan must freeze field order and independent golden
bytes before implementation; no JSON parser or unbounded map allocation.

Sections, in canonical UUID order unless otherwise specified:

1. **Capture header:** unique preparation UUID, root-scope UUID, scope/entry/origin/
   lineage-edge counts, ordinary payload-byte total and complete archive-byte total.
   The latter includes inventory/header/terminal sizes, not a self-referential hash.
2. **Scopes:** unique exported scope UUID, ROOT or RETAINED kind, origin-node
   references, ordinary-entry count and optional active-tab entry UUID. Exactly one
   ROOT represents the captured current workspace. A scope contains zero or one
   native graph and zero or one private-state set, including an explicitly empty set.
   Tab order is an ordered list of entry UUIDs; no filename/time sorting restores it.
3. **Policy provenance per scope:** UNKNOWN or KNOWN. KNOWN includes captured source
   native bundle UUID, generation, quotaBytes, automaticCount and policy-record hash,
   using current legal ranges. Imported v1 policy is UNKNOWN, never defaulted.
   These fields are observations only; no enrollment, root names or raw controls.
4. **Ordinary-entry directory:** globally unique outer entry UUID, scope UUID,
   type1–9, payload length/hash, and typed embedded native UUID for types 1–5/9.
   Private-state entries instead carry a stable item UUID for lineage and tab order.
   Native key is `(scope, role, kind, embedded UUID)`, not the outer UUID alone.
   Each entry also names bounded lineage-edge references; empty means no known origin.
5. **Origin graph and lineage edges:** defined below. No source path or live grant.

The inventory describes every ordinary frame exactly once; it does not describe
itself. Wire ordinary frames follow directory order by outer entry UUID. Compare
frame UUID/type/length/hash against the next descriptor, then validate typed payload
and embedded native identity. Reject missing/extra/reordered frames, wrong scope,
duplicate typed native keys and any unreferenced directory entry. The terminal
counts type 10 plus all ordinary entries. Empty backup still has type 10 and terminal.
The inventory frame's own outer ID is unique and absent from its directory.
The enclosing payload/terminal hashes bind inventory bytes without a hash cycle.

## Exact completeness and retained-state boundary

A whole-private-backup capture includes the current native graph, all captured
live tabs, independently readable recovery and the eight allowlisted preferences;
also every committed restored-item receipt and every native graph/private-state
item reachable from those receipts. Capture receipt discovery under the same
operation-data mutation exclusion as native capture; revalidate inventory before
pin publication. Retained imported content is immutable and protected for the
operation even though the native ALL_BUNDLE pin alone cannot protect that namespace.
Operation-data admission must block its cleanup while a descriptor references it.

ROOT permits <=12 SESSION_TAB, <=1 RECOVERY and <=1 PREFERENCES. Each RETAINED scope
has the same limits, separately counted; total retained tabs may exceed 12. A new
import wraps the source ROOT as RETAINED, preserves all other source scopes and
adds provenance, rather than folding them into its new live ROOT. Live-tab edits
and the retained imported original are distinct items even when bytes match.
Global bounds below apply across every scope; no per-scope reset grants more memory.

Repeated copies of the same imported archive remain separate local adoption
instances/scopes. Exact aliases, native UUIDs, index sequences and sourceTabIds may
repeat across scopes. They must not repeat where native/private-state validation
requires uniqueness inside one scope. No content-based deduplication or history
merging. Current snapshot bytes, BOM/CRLF, labels, metadata and valid lineage remain
byte/field exact except explicitly mapped IDs in newly constructed import records.

A committed import's staged original archive/envelopes are redundant transport
copies only after a durable lossless projection proof maps **every** validated
source field/payload and source coordinate to retained records/provenance. Such
proven copies need not become nested archive payloads. Until that proof exists,
whole backup is blocked; do not assume successful adoption makes staging redundant.
No cleanup/deletion authority follows from this format.

Failed, provisional, uncommitted or unknown operation data is **not** declared
restored content and cannot be smuggled through a valid typed frame. Whole backup
fails before provider output whenever such data lacks proof of being a redundant
copy of included content. Preserve it locally and report unresolved attempt IDs/
byte totals without exposing text/URIs in logs. An abandoned failed export's staging
may be excluded only if the same exact captured drafts/history are still included;
newer edits are not proof that an older captured draft is redundant. The current
backup's own freshly validated staging is excluded as its exact transport copy.
No automatic deletion, user dismissal flag or timestamp establishes redundancy.
This intentionally favors explicit blocked completeness over silent omission.

## Portable provenance and deterministic remapping

Represent provenance as a bounded DAG, not nested archive bytes. An origin node
contains a persisted UUID, original source archive SHA-256, source exported scope
UUID, and zero or more parent-origin UUIDs. Preserve existing nodes/edges exactly;
create one new node per imported source scope when adopting a new archive.
All parent nodes must be present; reject cycles, duplicate conflicting node IDs,
self-edges and missing origins. UUID collision with differing content blocks import;
never overwrite prior provenance. Source archive hash is integrity provenance, not
authority or an instruction to open a location.

A lineage edge records `(current scope/item key, origin node, source type/kind,
source embedded UUID or private item UUID)`. This retains source coordinates when
native IDs are remapped. On the next export rewrite only the current-scope portion
through the persisted export map; origin-node source coordinates stay immutable.
If repeated adoption changes current native IDs, copy existing lineage edges to
the new current key and add the immediate origin edge. Their referenced origin DAG
preserves older imports without reconstructing original native bytes or discarding
old ID relationships. Deduplicate only byte-identical provenance nodes/edges,
not user items. Bound the complete lineage count, not merely ancestry depth.

Before output or adoption, persist one generated map for operation UUID, every
scope, every outer entry, native typed IDs, item IDs and provenance references.
Preparation/completion share a mapped transaction UUID but have distinct outer IDs;
identity base-record/history IDs agree; FORK origins, TAB aliases and index metadata
references map consistently inside each scope. UUIDv4 outer/scope/item IDs are data,
never paths. Existing source IDs need only satisfy their native schema, not UUIDv4.
Export reuses staged descriptors across hashing, output, retries and readback.
Import resume reuses its exact remap plan; never regenerate IDs after interruption.
Multiple restored scopes get distinct local bundle handles, with no live alias merge.

The original manifest bound into BEGIN is the complete independently persisted
export descriptor containing a unique preparation UUID, the exact portable inventory
and native-map/private-state digests. Its combined envelope must fit the existing
16 MiB pin-manifest cap; otherwise reject capture. Persist the inventory as an exact
slice/reference whose digest is bound; never rebuild it while writing. Empty native
history cannot produce an ambiguous BEGIN→capture link. Exact matching original
bytes are required for resume/RELEASED; missing/ambiguous evidence blocks.

## Whole-archive bounds and implementation ownership

| Resource | Proposed hard bound, shared across the archive |
| --- | --- |
| Complete stream | 1 GiB including inventory, all headers and terminal; no compression |
| Ordinary entries | <=16,380 total; type 10 adds one, still within framing ceiling 65,536 |
| Native mappings | <=16,380 aggregate, never 16,380 per scope |
| Scopes / origin nodes / lineage edges | <=64 / <=256 / <=65,536 |
| Serialized inventory / persisted combined descriptor | Each <=16 MiB; inner/native maps count toward combined descriptor when embedded |
| Retained metadata charge | <=16 MiB for framing IDs, scope/entry/native/remap/lineage indexes and retained strings together |
| Streaming payload memory | One <=65,536-byte transfer buffer per sequential pass; no archive/document payload arrays |

The retained-metadata admission formula is at least
`4096 + 4096*scopes + 1024*(ordinaryEntries+1) + 256*origins + 128*lineageEdges`
plus `64 + 2*declaredUTF8Bytes` per retained string copy and container overhead not
covered by those fixed charges. Reserve before allocation/callback; account for
simultaneously live copies, graph validation maps and decoding buffers. Release
charges with lifetimes; process scopes sequentially and spool immutable descriptors
to charged private files rather than retaining several whole graphs. Never treat
separate 16 MiB budgets as additive permission for an unbounded working set.
Effective count limits may bind below the advertised entry maxima. Reject the whole
capture before output if its source cannot fit; never truncate inventory/provenance.

Native restored-store caps (65,536 store slots; 4,096 entries/8 MiB per metadata
root), private document 8 MiB and codec string/cursor limits also apply. Disk staging,
remap, all retained bundles and commitments remain charged to primary shared quota
under the proposed operation-data extension. One existing 1 MiB/256-entry control
allowance includes all pins/receipts/reservations; this format adds no allowance.
The operation-data ledger must bind every admission, graph/pressure projection,
maintenance replay and retirement proof, including `HistoryCapacity.prepareTerminalLocked`
which currently constructs usage from only native `graph.actualBytes`. Propagate
charges/fingerprint into graph/pruner/executor proofs without making operation
files deletion candidates. All control preflights must count receipt reservations
alongside pin credit; extending `HistoryCapacity.ledger` alone is insufficient.

Persisting a descriptor/native map itself changes the operation ledger. The exact
admission plan must bind native/source capture identity separately from the full
charged ledger before/after each enumerated preparation transition. Prove all
unrelated entries unchanged; charge every own stage/final/reserved byte and slot.
Neither freezing the old full graph fingerprint forever nor blanket exclusion of
the operation's directory is valid. Full graph/replay/retirement proofs still bind
the current complete ledger. Reentrant or unrelated changes invalidate the proof.

Before any operation write, the separate proposed companion plan
`docs/superpowers/plans/2026-10-15-operation-data-capture.md` must define a durable
typed preparation commitment, its states, physical/future byte+slot conversion,
and receipt+BEGIN+CLOSE peak reservation in existing shared allowances. It must
cover crashes before BEGIN, after BEGIN/before the link and at every conversion;
"reserve space" alone is insufficient. This ADR does not duplicate that protocol.

The namespace must have a fixed schema, not recursive arbitrary-directory discovery:
the initial capture plan uses preparation-UUID directories containing typed generated
artifact files (depth two). Future retained-bundle shapes require separate typed
review, maximum depth five below `private_operations`; the companion plan owns the
exact layout. The aggregate 65,536-entry bound includes every directory, regular
file/stage, link and unknown node,
counted before descent with bounded enumeration; no unbounded `listFiles()` array.
Inspect without following links; unknown shape/depth/nonregular entries block writes
without recursion or deletion. Directory slots cannot evade admission by having no
payload bytes. Failure fixtures include empty-directory floods and nested/link trees.

A proposed `PrivateBundleInventory` codec owns canonical scoped framing/bounds;
`PrivateArchive` gets explicit v1/v2 dispatch and a separate v2 typed source/sink
adapter, preserving v1 behavior. Backup/import coordinators own closure, lossless
projection proofs and per-scope native validation/remapping. Native store codecs
remain v1. Receipt/repository code owns trusted local scope routing; MainActivity
only exposes adopted items. Restored access must use extracted strict no-mutation
readers/factory: ordinary unenrolled native constructors can create/sync directories.
Do not instantiate them against imports, fund another capacity registration or use
a throwing-durability adapter to pretend a writable constructor is read-only.
These are proposed responsibilities, not existing APIs.

## Validation, compatibility and gates

The planned typed importer uses the existing strict framing reader for v1 and
persists one synthetic source scope/item map with UNKNOWN policy. Its resulting receipts
must satisfy this ADR's projection proof before later whole-private-backup output.
A v1 reader rejects v2 immediately. No downgrade translation silently drops scopes.
A v2 import remains additive: live restored drafts have remapped IDs/null URI/unknown
baseline/dirty=true. Eight allowed preferences apply only by explicit separate action;
retention policy is inert, no SAF grants/user-document writes, no tab eviction.
Imported snapshot restore requires a trusted scoped route and admitted primary
protective copy before the existing picker; never pass a foreign native ID directly.

Required independent tests include v1 golden compatibility; v2 independently encoded
fixtures; exact total/index/lineage caps; all truncations and forged scope/membership/
policy/embedded-ID pairs; duplicate UUIDs across scopes; same transaction across two
entry types; cyclic provenance; repeated import/export/import with two colliding
native bundles and >12 retained drafts; zero-native captures with different drafts;
and failed/unprojected stages blocking whole backup. Verify no payload/metadata or
provenance omission, no source-policy activation and byte-exact BOM/CRLF restoration.

Run constrained-heap tests with full budget charged alongside other live allocations;
measure actual peak retained objects and reject designs whose conservative accounting
underestimates them. Fault/SIGKILL tests cover persisted inventory/remap, BEGIN link,
receipts, repeated fresh-process reopen and zero premature visibility/success.
Provider fresh-readback/owned closure/quiescence remains required after format checks.
No implementation, host test, Android/provider/Fold, signing or release pass is claimed.

Next review decision: freeze exact schema/golden fixtures and the separate admission
protocol after direction review; no source work is authorized by this ADR.
The already eligible operation-data admission/persisted capture slice remains separate;
its descriptor must leave no ambiguity between native mapping and full capture binding.
