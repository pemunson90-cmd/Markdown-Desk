# Inert history prune-intent codec

Run with the installed JDK17:

```sh
PATH="$JAVA_HOME/bin:$PATH" bash verification/history_maintenance/run.sh
```

This host fixture uses actual native store/catalog/identity/index writes and fresh
production graph/planner scans. It checks pressure, floor and selected proof
round trips, full native envelope hashes/lengths, fixed anchors, URI/TAB identity
routing and Save As provenance, explicit authorization, immutable copies, repaired
checksum attacks on framing/roles/UUIDs/duplicate members/reference closure/anchor
order/capacity/count0/UTF8, and the complete 64KiB envelope limit. The memory process
uses an 8MiB native source with a 128MiB heap and 32MiB retained headroom, then grows
a native selected proof until framing and authority overhead make the complete
intent exceed 64KiB while its preview proof still fits. Payload bytes never enter
the intent. The fixture checks that native v1 bytes remain unchanged.

`HistoryPruneIntent` is package-private and has no runtime caller or filesystem
operation. `create` receives an immutable validated graph snapshot, an exact
preview, separate immutable policy binding, explicit exact authorization and a
supplied unaffected fingerprint. It requires the graph/profile/policy and preview
fingerprint/selected IDs to match. The binding is codec evidence: supplying it is
not acknowledgement of durable policy or permission to delete. The unaffected
fingerprint is carried unchanged; computing and validating the future graph with
the complete deletion closure excluded belongs to reviewed maintenance integration.
Checksums establish byte integrity, not the origin of authorization.

The future names are `history_control/prune.intent` and
`history_control/prune.intent.stage`. The existing control parser still treats
these as unknown entries and its 16KiB registration/policy cap remains unchanged.
This slice implements no maintenance open/discovery, live pin authority,
publication, tombstone-aware readers, replay, executor, sync or unlink. The
separately planned 1MiB/256-entry control allowance and durable policy/controller
prerequisites remain necessary before any future integration. No device or
process-death deletion acceptance is claimed.

The v1 big-endian envelope has a 12-byte header (`MDHP` magic, int version1,
int body length), a bounded body, and SHA256 of header plus body. Total size,
including checksum and every proof/identity/authorization field, is at most65536
bytes. The body contains binary bundle UUID, generation, complete effective-policy
envelope hash, quota/count, preview and authorization fingerprints, unaffected
fingerprint, length-delimited canonical preview proof, sorted authorized checkpoint
UUIDs and sorted complete identity envelope descriptors. Identity descriptors use
fixed role IDs1–4 (store/catalog/identity/index), fixed kind IDs1–6
(snapshot/transaction/complete/checkpoint/identity/index), UUID, complete native
envelope length and hash. These IDs belong only to this codec. Unknown IDs,
noncanonical ordering, duplicates and trailing bytes are rejected. UUID text inside
the original proof must use canonical lowercase format; generated filenames must
be exactly UUID plus the role's typed kind, never caller-supplied unlink paths.

The existing canonical preview subframe is parsed strictly into immutable typed
members, groups, references, anchors and routing descriptors. Legacy floor/selected
and explicit pressure domains retain their exact original bytes and SHA256. Every
removed group must have its exact native five-member pair plus selected checkpoints,
all nine native ownership/reference edges, and its complete selection. Identity
records are never removed. Each history retains a disjoint later-sequence anchor;
external preview pins cannot be removed. All immutable identity envelope descriptors
from the supplied graph are included conservatively, because its public inventory
does not map separate FORK envelopes to histories. Large identity inventories thus
require bounded replanning rather than omitted provenance evidence. No arbitrary
`DeletePlan` Java object is serialized; the approved narrow planner helper streams
only the existing bounded canonical proof without changing its bytes.
