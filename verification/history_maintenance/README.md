# Bounded history maintenance recovery projection

Run with the installed JDK17:

```sh
PATH="/workspace/.command-library-env/jdk-17.0.16+8/bin:$PATH" bash verification/history_maintenance/run.sh
```

This host suite exercises the actual native store, catalog, identity, index,
graph, control and inert intent codec. It covers discovery, isolated candidate
validation, read-only installation, every public reader boundary, physical
accounting, hostile native references, authority drift and synthetic interrupted
removals. The restart driver opens seven intact/partial/all-absent fixtures in two
fresh JVMs each and compares exact survivors and all disk hashes. These are
synthetic retained-intent fixtures, **not** executor, unlink or SIGKILL acceptance.

`HistoryControlStore` recognizes only `history_control/prune.intent` and
`prune.intent.stage`, retaining typed evidence independently of the policy chain.
Both names, enrollment/marker stages, registration and policy share the existing
1MiB/256-entry administrative allowance. Registration and policy remain capped at
16KiB; complete intent framing is capped at64KiB/256members. Ordinary reconciliation
rejects either intent name before any control-stage promotion. Public readers
reject active intent/stage evidence until dedicated recovery succeeds, including
malformed, unreadable, symlink and directory artifacts.

Package-private `HistoryMaintenanceRecovery.open(store,catalog,identity,index,
durability)` acquires the parent monitor and existing mutation lease. It validates
exact registered roots, bootstrap hint, enrollment, current policy, committed
intent, all still-present removed members, anchors, identities and survivors.
Only `NoSuchFileException` explains a removed member's absence. Native removed
reference parsers verify framing, checksums and exact proof edges even when the
referenced source is already absent. Unknown files and all retained stages block.

The candidate flows explicitly through internal source/metadata reader overloads;
it never becomes provisional global authority. After validation, control files
are synced through read-only descriptors and parent/role/control directories are
synced. A fresh callback-free validation follows the **last** callback before
installation. Reentrant ordinary reads remain blocked and nested writers fail.
Failures preserve the existing installed capability and capacity acknowledgement.
The opener never enables writers, publishes files, promotes stages or unlinks.

The installed `HistoryTombstoneView` is a filtering capability, not a validation
cache. Each public read rebinds to the current control chain/intent and rebuilds a
fresh survivor proof. Every present removed member is checked against its exact
length/full-envelope SHA256 and captured file identity before it can be hidden.
Same-byte replacements of members, controls or registered directories invalidate
the capability. Exact members may become genuinely absent. Logical inventories,
reservations, direct lookups, catalog pins and index lists consistently exclude
the entire validated closure; actual bytes and occupied slots still report disk
usage. No mixed physical-record/logical-transaction graph is constructed.

`unaffectedFingerprint(graph, closure)` uses one canonical
`history-maintenance-unaffected-v1` domain before intent creation and after partial
removal: ordered registered role names, sorted surviving native envelope
descriptors, surviving semantic edges, identities/provenance and outstanding
reservations. It excludes the **entire** closure regardless of physical presence,
desired profile, constructor hint, physical usage and all transient control or
maintenance usage. Stable policy authority binds separately in the intent.

Boundary coverage includes a valid intent larger than16KiB, exact256-entry control
usage including enrollment, overflow/oversized sparse input, and streamed8MiB
native members with a128MiB heap and32MiB retained headroom. The original codec
suite retains pressure/floor/selected proof roundtrips, repaired-checksum attacks,
canonical framing/identity validation and the complete64KiB limit. Native v1
bytes are checked unchanged. No Activity/provider/coordinator/archive integration,
intent publication, pins, deletion executor or runtime activation is included.
Physical Android/SAF/Fold/TalkBack, sudden power loss, signing and upgrade gates
remain **NOT RUN**.

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
