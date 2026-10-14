# Save protection Task 1 implementation report

Date: 2026-10-14. Branch: `codex/m0-safety-regressions`. This worker made no
commit or push and did not modify Activity/coordinator/source versions.
Independent risk review and integration are owned by the root worker.

## Implemented contract

Added actual production `ProtectionStore.java` and `AndroidDurability.java`,
plus `verification/protection_store/` behavioral tests, runner scripts and
labelled host Android boundary doubles.

The planned constructor and operations are present, all throwing `IOException`
for fail-closed storage errors. `Snapshot` has final public metadata and `byteCount`, without retaining its
payload. `bytes()` rereads the disk record, validates envelope/content hashes
and exact metadata provenance, and returns a fresh caller-owned byte array.
`Transaction` has final public ID, snapshots, expected hash and verified state.
A separate immutable completion file supplies verified state during enumeration;
`complete()` does not change the already-returned prepared object.

Root additionally requested `Transaction validate(Transaction) throws
IOException`. It rereads the committed preparation and both linked snapshots,
checks exact object provenance and completion if present, and returns fresh
validated data without a sync/write or full-store content scan. This lets the
coordinator reject corruption/removal during its final provider read before it
opens a destructive provider stream. The root owns recording this API addition
in the plan and consumes it in Task 2. A subsequent root request added
`Snapshot snapshot(String id) throws IOException` for validated single-ID restore
lookup. This rejects path traversal/invalid UUIDs and rereads the committed
record without loading unrelated snapshot payloads.

Snapshot roles are exactly `original` and `attempted`. Required document ID,
nonnegative revision, matching source/attempt document ID, URI and revision,
different snapshot IDs, source hash matching expected hash, committed envelope
IDs/lengths/hashes and completion links are validated. Nullable optional URI and
title are preserved. Every string is bounded to 4096 UTF8 bytes with strict
encoding/decoding; invalid UTF16 input is rejected rather than replaced.

## File/storage behavior

Generated UUID filenames hold `.snapshot`, `.transaction` and `.complete`
records. Every record has magic, version, type, bounded body length and SHA256
checksum over the serialized header/body. Snapshots also carry the independent
exact-byte payload hash and length. Prepared/completion records include IDs,
source and attempt lengths/hashes, document ID, revision and expected source
hash. File size is checked before allocation; all binary/string lengths are
checked before reading/allocating; oversized/truncated/unknown-version/corrupt
committed inputs fail explicitly. Final reads use `NOFOLLOW_LINKS`.

Each commit creates a unique stage, writes/flushes/syncs the actual descriptor,
closes it, calls the adapter's same-directory atomic move, syncs the directory,
validates the parsed record and expected serialized checksum before
returning. Sync, move and validation failures propagate. Failed stages and files
are retained without deletion. Root construction validates existing ancestor
paths and entries, creates only the root under an existing parent, then syncs
parent and root; existing roots are also synced to handle a previous failed
creation. Symlink ancestors, roots and every symlink/nonregular entry reject
operations. The root is expected to be inside the application's private data
directory; private metadata is not logged by these classes.

`snapshots()` and `transactions()` enumerate independently without a mutable
index. Snapshot enumeration includes orphans and does not parse transactions;
transaction enumeration validates only its referenced snapshots and every
prepared/completion record. Stages are retained and excluded from committed
recovery enumeration. Both enumerations explicitly fail on invalid committed
records of their respective types. Enumeration syncs the directory before
returning committed protection, including a record left behind by an earlier
failed directory sync.

Configurable quota defaults to the declared 64MiB constant. Every file/stage
counts toward quota before a new stage is created. Preparation also reserves
actual completion-record space, and all later writes account for unresolved
completion reserves. Directory entry capacity is also reserved before creating
a stage: each operation needs one net entry, and preparation additionally
reserves a future completion slot. Failed stages count permanently. The 65536
entry limit therefore cannot accept a write that invalidates its own directory
enumeration. A valid completion consumes its reserved byte/entry slot. Actual
filesystem write/sync errors still propagate; quota is not a free-space or
hardware guarantee. No pruning, deduplication or migration is implemented.
In-process store instances share a per-root lock for quota/commit coordination.
Quota reservation validation reads one transaction at a time rather than
retaining a temporary list of all protection contents. Snapshot writing stores
a small prefix, a single captured payload and checksum directly to the file;
it does not create multiple large ByteArrayOutputStream copies. Snapshot reads
for lookup/enumeration validate hashes in 8KiB chunks without retaining payloads.
Only `bytes()` allocates one bounded payload array for its caller. Small
transaction/completion envelopes have a separate 32768-byte read bound.

The Android adapter uses public Android `Os.fsync`, `Os.rename`, `Os.open`,
`Os.fstat` and `Os.close`. SDK37's public `OsConstants` omits `O_DIRECTORY`;
SDK compilation initially failed on that identifier. The verified implementation
opens with `O_RDONLY | O_NOFOLLOW | O_CLOEXEC`, then requires `S_ISDIR(fstat(fd))`
before directory fsync. Directory descriptors are closed on fstat/fsync failure;
a secondary close error is suppressed behind the primary error. Unsupported
sync/rename operations fail closed. Rename rejects a preexisting destination or
cross-directory move.

## Observed verification

JDK used: `/workspace/.command-library-env/jdk-17.0.16+8/bin` on PATH.
SDK used: installed `android-sdk/platforms/android-37.0/android.jar`.
Bytecode target: Java11, matching the current project build.

- Before behavior implementation, eleven initial behavioral cases compiled
  against the actual production API scaffold. The first exact-byte snapshot
  case failed at `ProtectionStore.putSnapshot` with `IOException("Not
  implemented")`; the scaffold provided declarations only. This is the
  observed initial RED, not a claim that every initial case independently ran.
- Adding fresh transaction revalidation first failed with `IOException
  ("Transaction revalidation not implemented")`; implementation then passed
  removal and corrupt-preparation checks.
- Adding malformed metadata rejection first failed with `AssertionError:
  malformed metadata silently replaced`; strict encoding then passed it.
- The 65536-entry regression first failed with `AssertionError: entry cap
  permits self-invalidating store`; entry/complete-slot reservations then
  rejected mutation while preserving enumeration, and exact-capacity completion
  succeeds. Snapshot lookup first failed with `IOException("Snapshot lookup not
  implemented")`; fresh handle access now rejects disk corruption/deletion.
- Before memory changes, the matched three-save probe under `-Xmx128m` failed
  with `OutOfMemoryError` in Snapshot construction during `complete()`, after
  provider mutation. Streaming writes and disk-backed metadata resolved the
  observed failure without relaxing integrity.
- `bash verification/protection_store/run.sh`: PASS, 19 cases / 96 assertions
  and a separate 3 assertions for a real filesystem write failure. The write
  failure JVM is constrained with Linux `RLIMIT_FSIZE=4096`; it preserves a
  partial stage and restarts with the previous snapshot intact.
- `bash verification/protection_store/run_adapter.sh`: PASS, 27 assertions on
  actual production AndroidDurability with the labelled host Os boundary
  double. This checks open/fstat/fsync/close/rename errors, closure/suppression,
  regular-file rejection, nofollow flags and immutable atomic-move destinations.
- `bash verification/protection_store/run_memory.sh`: PASS under `-Xmx128m`,
  three paired 8MiB saves, three verified transactions, six snapshots and six
  exact-byte restores. This is a host regression check, not an Android memory
  budget. Root integrates its coordinator probe with 32MiB additional headroom
  separately; this worker does not claim that run.
- `javac --release 11 -classpath <installed SDK37 android.jar> -d
  verification/build/protection-store/android ProtectionStore.java
  AndroidDurability.java`: PASS, actual SDK type checking.
- `bash verification/run_host_checks.sh`: PASS, exit0 at the first implementation
  checkpoint before the streaming/entry-slot revision. Existing SearchEngine
  and SessionStore unchecked-operation compiler notices remain; there were no
  test failures. The standard runner does not yet include these new runners;
  root integration owns any shared runner update.

Low-level destructive provider safety probes were not edited or rerun by this
worker. The green store/host results do not supersede their known RED contract.
No APK, device, emulator, SAF/provider or release acceptance was performed here.

## Remaining concerns and integration requirements

- Independent risk review is still required by the plan; root is coordinating it.
- Actual Android process termination, directory-fsync support, storage pressure,
  power loss, memory use and maximum-size latency require device evidence.
- The store assumes an app-private directory, one application process and no
  concurrent foreign filesystem writer. It detects symlinks/corruption and uses
  nofollow reads, but it does not claim an interprocess transaction lock or
  protection against malicious same-UID racing root/entries.
- Repeated failures consume quota permanently in this slice. This deliberately
  blocks further protection rather than deleting unresolved/orphan evidence.
  No recovery or deletion UI is part of Task 1.
- Snapshot metadata is immutable and disk-backed. Before provider mutation use
  `validate(t)`; before UI restoration use `snapshot(id).bytes()`. Fresh byte
  retrieval rejects changed metadata, corrupted files and missing protection.
- Enumerations and quota checks still hash bytes from disk, so quota/accounting
  latency and Android memory use need measurement. The 128MiB host regression
  has been observed passing, but establishes no device performance budget.
- SHA256 detects accidental corruption, not malicious authenticated tampering.
  These files remain subject to uninstall/app-data clearing and device loss.
