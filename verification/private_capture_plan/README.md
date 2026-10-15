# Private capture record planning

Run: `PATH="$JAVA_HOME/bin:$PATH" bash verification/private_capture_plan/run.sh`.
The runner compiles production dependencies into a temporary directory and uses a
128 MiB heap. This packet adds only `PrivateCapturePlan` and its own fixtures.
Private-frame preflight c3177b8 (including source-tab identity keys) is a dependency.

`PrivateCapturePlan.create` consumes canonical four roots, the original complete
native map/Graph/control/operation ledger, the original OPEN/idle preparation owner
and shared Budget, fixed generated preparation/operation/map/descriptor UUIDs,
canonical successful ClosedFrames and the captured bootstrap quota. The primary
mutation monitor/lease and `owner.requireIdleBindingLocked` are mandatory. A claimed,
frozen, uncertain, live-resource, different-preparation or different-budget owner
cannot assemble. This initial idle check grants no final source quiescence proof.

The result retains final references/scalars only:
`nativeMap`, `nativeSources`, `sourceOwner`, immutable `privateFrames`, `prepare`,
`descriptor`, `link`, `initialLedgerHash` and summed new `artifactBytes`. It retains
no factories, source payloads, serialized map array or decoded private-state Record.
The caller must already charge the live map/Graph/control/ledger, immutable captures
and preflight ownership to the same budget. Assembly reserves **256 KiB fixed plus
4,096 bytes per frame** before allocating its tables, records or clones. The native
predicate helper is called inside assembly and separately charges its existing
workspace to that same budget; there is no extra 16 MiB allowance or refund.
Manifest construction already proves uniqueness among its immutable native outer
IDs. A bounded <=33-member set covers bundle/new IDs and is checked while streaming
the original entries, avoiding a second 16K-member index. This is conservative
metadata admission, not whole-capture heap/GC approval.

Initial evidence must agree exactly: complete diagnostic-free graph/control/ledger,
no active pin, authority/pin stages, maintenance or pending operation, matching
operation fingerprint/counters and control usage, and canonical present/absent
ledger representation. Present ledger tables are rehashed in the existing ledger
domain to reject mutations carrying stale hashes. Historical acknowledged CLOSED
captures remain valid initial evidence. The initial source predicate is freshly
created from the matching typed graph/map; its mandatory exact native-file proof
performs local reads. The rest of assembly streams immutable metadata without
provider/source callbacks, directory creation, persistence or publication.

Private input is never silently sorted. Empty means Phase A; Phase B requires
0–12 SESSION_TAB rows with contiguous ordinals, optional RECOVERY ordinal0 and
exactly one final PREFERENCES ordinal0. Every ClosedFrame must belong to the same
owner. Repeated strict-decoded source-tab ID hashes are rejected before any PREPARE
publication. Hash collision is conservative optional refusal, not identity authority.
All new generated IDs must be UUIDv4/variant2 and distinct from each other, the
bundle and native-map outer IDs. Existing preparation/operation collisions are
also refused; caller lists are detached and result lists are immutable.

Frozen manifest bytes are streamed into a counting/SHA-256 discard sink, including
its checksum; no serialized map is retained. Artifact1 + private rows build D;
D binds exact domain2 BEGIN (count=native entries+private frames); all rows and BEGIN
build P; exact P/BEGIN/D hashes build LINK. Phase flags and missing-directory credits
come from the canonical table and initial ledger root membership. BEGIN remains
204 bytes, CLOSE credit126 and LINK173. Existing MDOP/map/pin wire formats stay intact.

`initialLedgerHash` is exactly SHA-256 of ASCII `MDOP-initial-ledger-v1\n`, one byte
present0/1, then the raw 32-byte ledger fingerprint only when present. The absent
independent golden is
`d85d1350026fd0c27784a3126cd3adff553265dc45b173b353b13c049645825e`.

Admission requires old graph charged bytes + all new artifact lengths <= quota;
actual control + existing credit + P +204+126+173 <=1 MiB and existing actual/reserved
control slots +4 <=256; operation actual/reserved entries +missing-root slot +new
preparation directory +artifact slots <=65,536. Overflow fails closed. Native bytes
are not copied into operation data; the new map stores their exact frozen mapping.
A valid record plan is neither durable funding nor publication/activation authority.

Focused evidence: **28 scenario groups**, primarily real native backends and codec
frames: zero-native exact preparation identity/distinct descriptors, native completion
credit, all14 frames and presence flags, order/ordinal/prefs/source-ID/owner/UUID
rejection, immutable list isolation, absent/present ledger goldens and MDOP roundtrips,
mutated/null ledger tables, stale graph/policy, live/frozen owners and budget failure,
pending captures/active pins/stages/unknown namespace, and prior acknowledged CLOSED
capture with no source-owner reset. Nine real 8 MiB frames reject 64 MiB quota and
pass after a fresh 128 MiB policy capture. A real valid 253-entry control chain rejects
four new slots. Explicit scalar funding-boundary probes test exact/+1 data/control
bytes/control slots/namespace slots, reserved credits and overflow using the private
funding routine; they do not claim an actual complete 65K-entry backend graph.
The initial suite failed for the absent helper; null-ledger validation failed with
NPE before being repaired to an IOException rejection. Test-only seeded records
exercise closed/pending evidence, not a production persistence writer.

No PREPARE writer, second-pass hash match/copy, PreparationTransition, durability
callback handling, final Sources.Proof, BEGIN/LINK publication, source handoff,
consumer activation, required-runner registration, full host gate, APK build, CI or
device verification is included or claimed. Root owns those integration obligations.
