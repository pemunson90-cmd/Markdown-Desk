# Protection store host verification

These tests compile and invoke the actual production `ProtectionStore` and
`AndroidDurability`. They do not test a separate save-protection model.

Run with JDK17's bin directory on PATH:

```sh
bash verification/protection_store/run.sh
# Scoped real filesystem faults only:
bash verification/protection_store/run.sh physical
bash verification/protection_store/run_adapter.sh
# Optional standalone bounded-heap store/restore probe:
bash verification/protection_store/run_memory.sh
```

The current store suite has 31 cases / 272 assertions, including deterministic completion-stage proofs and retries. It checks exact BOM/CRLF/Unicode
bytes, defensive copies, restart/orphans, immutable preparation and completion,
identity/revision/role/hash links, the exact 8MiB boundary, bounded/strict metadata,
quota including retained stages and completion reservations, sync/move/directory
ordering and failure retention, root parent sync, corrupt outer checksum and
inner hash/length, oversized files, symlink paths/entries, and fresh committed
transaction validation immediately before provider mutation, reserved directory
entry capacity, disk-backed handle revalidation and legacy v1 envelope reading.

`run.sh` also starts a separate JVM with a Linux 4096-byte `RLIMIT_FSIZE` and
ignored `SIGXFSZ`. This exercises an actual failed filesystem write, retained
partial stage and restart of the previous snapshot (3 assertions). Python3 and
Linux `resource` are required for that test. Temporary artifacts stay under
ignored `verification/build/protection-store`. Restricted child output is relayed through pipes so file-size limits cannot silence assertions/errors in redirected logs. Each run cleans only its newly generated synthetic fixture directory after checks; product records are never cleaned by these tests.

The adapter suite has 27 assertions. Its labelled `android.system.Os` boundary
double performs real host file/directory sync and atomic rename, and injects
open/fstat/fsync/close/rename errors. It checks ordering, descriptor closure,
suppressed secondary close errors, directory type/nofollow flags, immutable
same-directory destinations and failure propagation. The production adapter is
also type-checked against SDK37.0 separately; the host double is not evidence of
Android storage durability or Android process-kill acceptance.

Store records use generated UUID filenames. Snapshot, prepared and completion
records are versioned binary envelopes with strict lengths and SHA-256 integrity.
Completion uses one transaction-bound `.completion-stage`; exact full stages may be explicitly synced and retried without rewriting, while matching partial stages remain protected and cannot retry. Actual staged bytes plus remaining byte/slot reservations conserve the original completion charge. Additional child-JVM real write limits leave zero/4096-byte matching prefixes (11 assertions each). Anonymous legacy stages never receive reservation credit. This prerequisite preserves per-handle constructor quotas and does not enable reduced-quota completion. Completion is a separate immutable record, and `Transaction.verified` reflects
completion when scanned; `complete()` does not mutate a prior object. Quota
includes every file and unresolved completion reservation. Snapshot handles contain metadata and byte counts without retaining payloads;
`bytes()` revalidates the disk record and returns a fresh copy. `snapshot(id)`
validates one lookup for restore. Snapshot writes and enumeration validation
stream payloads while preserving the v1 binary format. There is no deletion,
retention, mutable index, provider writing or automatic recovery/rollback here.

Initial observed RED: the compile-only production API scaffold rejected the
first exact-byte snapshot test with `IOException("Not implemented")`. Fresh
revalidation and strict malformed metadata each also had observed RED before
their implementation. The entry-capacity test also observed a concrete
self-invalidating write before the slot-reservation fix. The bounded-heap probe
reproduced an OOM after provider mutation before streaming/lazy snapshots; the
revised probe saves three paired 8MiB versions and verifies all six restores
under a 128MiB host heap. This establishes no Android memory budget. Full result and remaining limits are in
`docs/superpowers/plans/save-protection-task1-report.md`.
