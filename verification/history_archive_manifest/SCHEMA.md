# Frozen native-history archive mapping v1

Base `c0b7b80`, bounded Task 4 prerequisite reviewed by root before implementation.
Own only new `HistoryArchiveManifest.java` and this directory. No filesystem,
pin publication, capture adapter, provider IO, archive-format edits or runtime
caller. Manifest is a future **local persisted artifact**, not an archive entry.

## Source evidence and authority

HistoryGraph.Graph records expose logical role, native filename/kind/UUID and
exact serialized envelope length/SHA256. EffectivePolicy supplies acknowledged
bundle/generation/control hash/quota/count, but Graph has no registration hash.
The pure freeze API therefore takes both frozen Graph and captured
HistoryControlStore.Inspection. Require complete/usageComplete graph, no graph
diagnostics/prune block, non-null acknowledged effective policy; require VALIDATED,
complete control chain/accounting, registration/latest records and no retained
stages/maintenance/diagnostics. Match latest policy's exact bundle/generation/hash/
quota/count to graph.effectivePolicy and match registration's four ordered role
IDs/basenames to graph.registeredRoleNames. Bind exact registration envelope hash
and bundle, role bindings and captured effective policy into the manifest.

No check establishes that separately supplied graph/control snapshots were
captured atomically or remain current. Graph fingerprint is frozen **provenance**,
not a fresh proof after pin/policy/control/source changes. Future capture+pin
adapter must acquire current authority and revalidate exact inputs atomically.
Decoded bytes are likewise untrusted inert metadata, never proof of acknowledged
runtime policy, native content validity, export success or deletion permission.

## Native mapping

Enumerate ALL graph.records; no selection/filtering/truncation. Reject unexpected
role/kind/stage, missing/corrupt descriptor, noncanonical native UUID/hash, and
filename mismatch (must exactly equal UUID + '.' + native kind). Every pair
(role,nativeUUID,kind) is distinct even when envelope hashes match. Native
.transaction and .complete files share their transaction UUID and **must receive
different archive IDs**. No content deduplication or fresh remapping on retry.

Typed role codes match native control/intent IDs:1STORE,2CATALOG,3IDENTITY,4INDEX.
Typed native kind codes match native intent IDs:1SNAPSHOT,2TRANSACTION,3COMPLETE,
4CHECKPOINT,5IDENTITY,6INDEX. Kind fixes valid role and archive Type mapping:
SNAPSHOT→SNAPSHOT(archive1), TRANSACTION→PREPARATION(2), COMPLETE→COMPLETION(3),
CHECKPOINT→CHECKPOINT(4), IDENTITY→IDENTITY(5), INDEX→INDEX(9). No ordinal encoding.

Canonical order is role code, then native UUID's unsigned 128-bit order, then kind
code. Build/sort validated native descriptors once, then call IdSource exactly
once per descriptor in that order. Every generated archive ID must be distinct
RFC 4122 variant 2/version 4 UUID; callback failure/null/duplicate/non-v4 aborts the
whole freeze without a returned partial mapping. Native/bundle UUIDs retain the
source codec's arbitrary UUID version/variant support; they are never archive IDs.

DTOs hold typed enums, UUID objects, long exact envelope lengths and private 32-byte
hashes (defensive byte-copy accessors). No payload arrays, filenames, document URI,
global directory enumeration or content-based identity inference. Lists/maps are
immutable. Native filenames may be validated at freeze but are not persisted as
paths. Canonical frozen entry IDs are reused after decode for every hash/write/
retry pass. Writer never calls IdSource.

## Exact local manifest wire and bounds

Big-endian fixed widths; domain of 8 ASCII bytes `MDHMFST\n`, int32 version1,
int32 complete serialized bytes (including final checksum), int32 entryCount,
int64 total native envelope bytes, bundleUUID16 bytes, registrationSHA25632 bytes,
int64 captured policy generation, policySHA25632 bytes, int64 quota, int32 count,
graphSHA25632 bytes. Then exactly 4 ordered role bindings: uint8role, int32 UTF8name
length1..64, exact strictUTF8name bytes. Names follow existing registration name
rules: nonempty, distinct, no '.'/'..', slash/backslash/colon/control characters.

Then entryCount records of exactly 74 bytes each: uint8role, uint8nativekind,
nativeUUID16 bytes, archiveUUID16 bytes, int64 serialized envelope length,
SHA25632 bytes. Final32 bytes SHA256 over every preceding byte, then true EOF.
Unknown version/domain/code, noncanonical order, duplicate native key/archiveID,
invalid role-kind/archiveUUID, bad length/range/checksum/truncation/trailing bytes
fails whole decode. No prefix/partial manifest is returned.

Serialized size is212 + sum(four UTF8name lengths) +74*entryCount, checked with
long arithmetic before allocation/output. **Complete serialized cap 16 MiB**.
Separately, conservative retained-index/bookkeeping charge is4096 +1024*count
<=16MiB. Explicit MAX_ENTRIES=16380 is the stricter derived bound; also enforce
PrivateArchive.MAX_ENTRIES and count*PrivateArchive.INDEX_BYTES_PER_ENTRY within
PrivateArchive.MAX_INDEX_BYTES. The charge is a policy bound, not an assertion
about exact JVM allocation. It includes mapping/list/set/sort overhead for this
codec, not caller-owned Graph storage. No caller can raise the bound.

Snapshot envelopes use existing document-entry cap 8 MiB+32768; transaction/complete
metadata <=32768; catalog/identity/index <=16384. Lower framing bounds retain
native 45 bytes (store) / 44 bytes (other) minimum envelope sizes. Total native length
is checked for overflow and must equal the declared sum. Derived v1 archive size
is64+58*count+total native length and must fit the existing 1 GiB archive cap; reject
whole oversized capture, never truncate. These fixed archive-overhead constants
are compatibility-tested against actual PrivateArchive.write.

## API and acceptance

Package-private pure factory:
`freeze(HistoryGraph.Graph,HistoryControlStore.Inspection,IdSource):HistoryArchiveManifest`.
IdSource returns UUID and may throw IOException. Public stream methods
`write(OutputStream)` and `read(InputStream)` preserve caller stream ownership.
Public immutable bindings/entries and count/serialized-size/index-charge/derived
archive-size observations support future adapters. No method validates filesystem
freshness or resolves a native descriptor to a path. Repeated write/decode/write
must produce exact same bytes and archive IDs.

Tests use real native registration/graph/preparation/completion to prove distinct
archive IDs despite one native UUID and exact full-envelope hashes. Independently
handwritten manifests probe strict parsing, order/duplicates/codes/hash, size/count/
checked-sum failures and replay stability. Actual PrivateArchive compatibility
proves typed mapping and frame-byte calculation. Synthetic maximal metadata
stream tests exactly 16,380 entries and rejection at 16,381 at 128 MiB heap with 32 MiB
headroom, bounded metadata-only IO and immutable retained DTOs. No repeated broad
suite or benchmarks; no atomic capture/pins/provider/device/release claims.
