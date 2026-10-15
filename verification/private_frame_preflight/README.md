# Private frame preflight

Run: `PATH="$JAVA_HOME/bin:$PATH" bash verification/private_frame_preflight/run.sh`.
The runner compiles actual production dependencies into a temporary directory and
runs with a 96 MiB heap. It does not register a required runner or activate capture.

`PrivateFramePreflight.create(owner, sharedBudget)` calls the original
`PreparationSources.reserveMetadata(sharedBudget, 512 KiB)` **before** constructing
its workspace or invoking callbacks. A foreign budget fails. Each immutable
scalar request reserves another 2,048 bytes through that same owner before its
Attempt allocation. The owner separately reserves its existing 512-byte source
registration when opening. Charges are monotonic; close/failure does not refund.

The workspace is confined to its creating thread, allows one active attempt and
at most 14 lifetime attempts, and becomes terminal after an authorized run fails.
Invalid thread/reentrant calls cannot disturb the active owner's run. Root supplies
kind, ordinal, generated item/artifact IDs and a trusted immutable-frame factory;
the helper checks local kind/ordinal/UUID shape, while root owns complete table
order/cardinality/presence and cross-row ID uniqueness. `attempt(...)` freezes
those scalars without calling the factory. Root must keep the same caller-owned
immutable captures for the later copy, charging all their memory separately.

`Attempt.run()` opens a process-owned Reader and stores it before any read. A
private InputStream adapter forwards only bounded Reader bulk reads, validates
counts/progress and hashes/counts every raw frame byte, **including its checksum**.
The raw frame passes through actual `PrivateStateCodec.read` into an internal
discard sink; success therefore requires checksum, UTF-8/BOM/cursor/metadata
validation and true EOF. The decoded kind must equal the frozen request. Whole
frame limits are 8,421,376 bytes for tab/recovery and 65,536 bytes for preferences.
No payload array, caller sink, raw delegate or writable resource escapes.

A `ClosedFrame` returns only after actual Reader.close succeeds. Its constructor
is private; its final fields contain kind/ordinal/IDs, exact whole-frame byte length/
SHA-256 and codec payload-byte/UTF-16-unit counts. For tabs, `sourceTabIdHash` is SHA-256 of the strict decoded sourceTabId's UTF-8
bytes; it is null for recovery/preferences. This adds at most 4,096 bytes of temporary
hash input and a 64-hex scalar within existing reservations, while retaining no source
ID string or decoded Tab. Equal IDs necessarily share the key; a hash collision
conservatively rejects an optional table. Root must reject duplicate tab keys before
PREPARE. A private retained source-owner
reference supports `belongsTo(owner)` by identity; no codec Record, factory or
payload is retained in the result. Root must reject results from another owner
before combining them with its final Sources.Proof. A ClosedFrame grants no
persistence, terminal quiescence, pin, BEGIN or consumer authority.

Read/validation failures still attempt actual close. Close failure is attached to
an earlier failure where possible, and the original Reader remains retained by
both the Attempt and active workspace. `retryClose()` retries that exact Reader;
it never returns a result or revives preflight, even after eventual close success.
Factory exceptions/null returns leave the existing owner permanently uncertain;
there is no invented close handle or successful cleanup receipt. Factory retention
in the Attempt is cleared when consumed, including failure. Callback operations
are outside the primary history monitor and reject preparation/operation participant
reentry. Before final publication, root independently freezes and claims the same
source owner under the primary lease after all preparations actually finish.

The **one 512 KiB workspace admission** conservatively covers one serialized real
codec read: metadata <=65,483 bytes (tab/recovery <=32,715), transfer[65,536], existing
UTF-8 carry[65,540], CharBuffer[32,768 characters]=65,536 bytes, six conservative
string decodes at 4,096+8,192+8,192 bytes each, and wrapper/digest/DTO/map headroom.
The reviewed codec's 65,540-byte carry buffer is an explicit existing exception;
all helper buffers and provider bulk requests are <=65,536 bytes. No codec change
or literal claim that every buffer is <=64 KiB is made. Codec read allocates its
own bounded temporary metadata/transfer buffers each call; calls never overlap,
and the helper retains no decoded Record or buffer between calls. Accounting is
for bounded live ownership, not a GC/heap measurement or whole-capture memory
proof. Root must charge simultaneous native maps, graph/control snapshots,
immutable captures, rows/copies and future codec buffers to the same budget.
Trusted source adapters must not retain supplied buffers or spawn untracked work.

Focused evidence: 30 scenarios using real dirty BOM/CRLF/Unicode tab, recovery and
preferences frames; exact IDs/hash/length/owner identity and equal/distinct sourceTabId keys; malformed metadata/UTF-8/
checksum, truncation/trailing/wrong kind; IO/Error open/read/close failures; null
factories; bad/stalled read counts; retained repeated close retries; budget rejection
before callbacks, foreign budgets and lifetime limits; reentrant/foreign-thread/
primary-monitor rejection; blocked open/read/close ownership and an 8 MiB streamed
source with bounded delegate reads. Initial tests failed for the missing helper;
owner-identity checks failed before the added binding. No full host gate, APK build,
CI, device checks, PREPARE, second-pass copy hash match, BEGIN/LINK, handoff or root
producer/table implementation is claimed.
