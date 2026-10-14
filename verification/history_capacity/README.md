# History capacity: read-only control prerequisite

Run with the existing JDK17 (`JAVA_HOME=/workspace/.command-library-env/jdk-17.0.16+8`, with its `bin` on `PATH`):

```bash
bash verification/history_capacity/run.sh codec
```

This runner compiles only the actual `HistoryControlStore` and its disk/codec
fixture using Java11 bytecode. It runs at a 64MiB heap. No Android build, hosted
CI, controller, registration publication, writer admission or runtime activation
is included. Other runner groups intentionally fail as unavailable.

Task1 RED was observed before implementation: the runner failed explicitly with
`HistoryControlStore codec implementation missing (Task1 RED)`. The subsequent
codec group tests actual serialized bytes and filesystem state, including a
sparse 512MiB entry that cannot fit into the fixture JVM's heap. The test creates
synthetic private roots only; it supplies no private data or signing inputs.

## Internal API and proof limits

`HistoryControlStore(File trustedCanonicalParent)` requires an existing canonical
parent and inspects its fixed direct child `history_control`. Construction and
`inspect()` never create, sync, move, promote or delete files. Callers implementing
future mutation must use the common parent monitor and explicit durable
open/reconciliation. A JVM monitor does not exclude another process.

`encodeRegistration`, `encodePolicy` and `decode` implement bounded framing.
`policyFilename`, `stageFilename` and `nextGeneration` provide deterministic names
and checked sequencing. Encoding returns at most 16KiB; descriptors never expose
payload arrays or absolute paths. Ordered role basenames are 1–64 strict UTF8
bytes; separators, colon, dot/dotdot and control characters are rejected. Colon
is rejected to keep raw URI/drive syntax out of descriptors. Registration roles
are exactly 1 protection, 2 catalog, 3 identity, 4 index, with distinct names.

`Inspection` returns immutable records/entries/stages, actual control bytes and
entry count, `usageComplete`, `chainComplete` and sanitized diagnostics:

- `UNREGISTERED` requires the control root to be absent.
- `VALIDATED` means complete, unique committed control-chain bytes with physically
  valid role roots. It does **not** acknowledge effective policy or grant writes.
- `BLOCKED` covers empty, malformed, partial, unknown, over-budget or staged
  control state. A validated committed chain prefix may still be reported;
  callers must check completeness and state before using it as authority.
- `PROVISIONAL` is a retained full matching next policy stage, or a sole complete
  registration stage with valid physical roles. The stage is never included in
  `latest`. Multiple, stale, foreign, partial or mismatched stages are `INVALID`.

Every stage consumes its actual bytes and occupied entry. The single control
allowance is 1MiB/256 entries, independent of data quota. Inventory streams at most
256 entries. When a 257th entry is observed, the returned bytes/count describe the
bounded inspected subset and `usageComplete=false`; they are never usable
capacity credit. Unknown regular files and oversized entries are counted without
allocating according to their contents or declared lengths. Nonregular,
unreadable or changing entries make usage incomplete. Unknown names are omitted
from descriptors and diagnostics so arbitrary filenames are not surfaced.

The codec checks nofollow file identity before/after bounded reads (or stat-only
oversized inspection), and directory identity around enumeration. It validates
all four existing canonical direct sibling directories and rejects symlink
ancestors, role aliases and control-root aliases. This is control/root proof;
it does not validate the ordinary history records or cross-object constructor
dependencies that a future bundle controller must check.

## Wire version 1

All integers are big-endian. The maximum **entire** envelope is 16KiB:

| Field | Encoding |
| --- | --- |
| Magic | int32 `0x4d444843` |
| Version | int32 `1` |
| Type | unsigned byte: 1 registration, 2 policy |
| Body length | int32, exact remaining body size |
| Body | Typed fields below |
| Checksum | 32 raw SHA256 bytes over the entire preceding header and body |

The immutable descriptor's `envelopeHash` is SHA256 of the **complete envelope,
including its checksum**. Policy `previousHash` refers to that complete-envelope
hash, never just the checksum or body hash. No body/envelope trailing bytes are
accepted; lengths are checked before allocation and UTF8 decoding is strict.

Registration body: UUID as two int64 values; int64 generation 0; exactly four
ordered `(unsigned-byte role ID, int32 UTF8 byte length, basename bytes)` pairs;
int64 bootstrap constructor quota; int64 initial effective quota; int32 initial
automatic count. Bootstrap is a separate immutable reopen hint and may differ
from the initial effective quota. It grants no capacity after future opt-in.

Policy body: UUID as two int64 values; positive int64 generation; 32 raw previous
complete-envelope SHA256 bytes; int64 quota; int32 automatic count. Supported
policy quotas are 64/128/256/512MiB/1GiB and counts 0/5/10/20/50. Chain inspection
sorts by generation and requires each checked `previous+1`, same bundle and exact
previous hash; timestamps never determine policy order.

Names are `registration.control`, `policy-<16 lowercase hex generation>.control`
and each committed name with `.stage` appended. Future publication/reconciliation
must reuse those exact final envelope bytes, with no stage rewriting or cleanup.

Tests cover golden registration/policy framing, distinct bootstrap policy,
strict UTF8 and byte boundaries, approved policy choices, malformed
magic/version/type/length/checksum/trailing bytes, role/order/path rejection,
missing/broken/foreign/forked/overflowing generations, provisional and invalid
stages, canonical physical roots, symlink/nonregular/unknown entries, full
entry-cap inspection, over-cap accounting and byte-preserving inspection.

This prerequisite does not establish durability, crash-restart adoption,
shared writer admission, Android app-UID behavior or sudden power-loss safety.
Those remain separate implementation and review gates.
