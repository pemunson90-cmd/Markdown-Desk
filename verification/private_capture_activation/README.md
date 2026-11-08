# Same-process captured-source activation

Run with configured JDK17: `bash verification/private_capture_activation/run.sh`.
This compiles the affected closure and runs only the new activation fixture. Writer
fixtures supply actual durable captures; their publication/copy matrices are not run.

`HistoryOperations.activateCaptured(returnedBinding, originalBudget)` accepts only
an authentic privately constructed same-process completion result and the exact
producer Budget object. Identity admission precedes control/map/table allocation.
The result retains that small Budget identity, without writer/Graph/copy buffers.
Current acknowledged roots/policy, durable pin acknowledgement, active exact BEGIN,
committed exact P/LINK, D binding/table and native map are freshly checked. All named
native/private envelopes are hashed with stable nofollow attributes. The original
capture's validated graph provenance comes from the returned binding; this method
cannot adopt cold records or manufacture a binding. New ordinary native appends may
coexist, and current authority is checked separately from captured historical policy.
Generic `activateAcknowledged` still rejects domain2.

A6 MiB admission from the SAME original budget covers bounded control decode/clones,
D metadata and reusable hash scratch. Existing ledger tables/evidence charge that
budget directly. Before map decoding/new source tables, charge4096 plus, per bounded
possible map/private row,2048 +4*(native-parent Java path length+160) bytes. The map
row upper bound is min(16380, declared map envelope bytes/74); this conservatively
covers temporary decoded entries/copies plus retained paths/hash/attributes/tables.
Each attempted source open separately admits16 KiB for stream/resource/digest wrappers
and bounded skip/transfer scratch before allocation or FD acquisition. Reservations
are cumulative, with no refund or unlimited-reactivation promise. This is logical
admission, not a measured simultaneous GC/whole-heap acceptance claim.

`openCapturedSource(handle, existingItemUUID)` resolves only declared map archive IDs
and private item IDs. No caller path/factory or delegate escapes. Generic reader and
writer factories reject captured handles before calling them. A resource is counted
before source open; wrappers are allocated before the nofollow stream is acquired.
Unknown opening failure freezes admission and preserves uncertainty, rather than
inventing resource closure. Every read checks the activation's fresh source inode/
length/mtime/creation anchors; bulk requests are capped at65536. Exact whole-envelope
SHA/length/true EOF are checked while consuming bytes. Default InputStream skip uses
these checked reads; reset is unsupported. Validation failure freezes admission.
EOF is validation, not physical closure. Early close releases ownership only after
the actual stream close succeeds; it makes no source-consumption/output-success claim.
A throwing close retains the original tracked stream/resource so the same close can
be retried, including after freeze. No cross-discovery old-inode guarantee is claimed.

The existing `freeze → quiesce → Pins.close` path owns terminal authority. After exact
drain, `capturedManifest(proof)` supplies a bounded in-memory stream over the already
validated immutable D bytes for the existing exact manifest comparison; it opens no
FD and invokes no provider. It also supports exact terminal-retry/committed proofs.
The capture gate retains declared source metadata while active or in TERMINAL_RETRY.
Successful terminal Claim.finish clears its CapturedData reference, releasing that
historical gate's avoidable references to source tables/anchors and the original
budget. A separate bounded immutable D byte array remains for terminal/idempotent
manifest checks. No payloads or source factories are retained. This does not revoke
caller-held wrappers or claim that garbage collection occurred.

Six focused functional groups pass: authentic activation/native+private reads after an
ordinary append, same-budget rejection/undeclared factory+item refusal/drain+RELEASED
CLOSE; missing LINK; changed private envelope before activation; changed bytes with
restored original mtime rejected by EOF SHA and subsequently closed; hashed skip and
throwing real-source-close retry delaying drain before CANCELLED CLOSE. The close
fault wraps the retained actual filesystem stream via test reflection, closes that
same real FD, throws, then proves retry executes the same wrapper again. A separate
unchanged coordinator smoke run passed59 checks. The48-fault/96-reopen matrix,
producer-wide gate, builds/CI, cold adoption/recovery, provider output and app UI were
not run or enabled by this checkpoint.

The terminal-retention regression first failed because the historical gate still
held CapturedData after successful CLOSE recovery. It now proves TERMINAL_RETRY
retains that data, successful TERMINAL clears it, and descriptor reads/idempotent
CLOSE work after a named native source disappears. Terminal source admission remains
refused. Only the focused activation runner was repeated for this narrow repair;
the unchanged59-check coordinator smoke was not rerun.
