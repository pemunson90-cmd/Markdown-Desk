# Stable history identity and optional verified index

Run the production-path host checks with the installed JDK17:

```sh
PATH=/workspace/.command-library-env/jdk-17.0.16+8/bin:$PATH bash verification/history_index/run.sh
```

The runner compiles the real `ProtectionStore`, `SaveCoordinator`, `HistoryCatalog`,
`HistoryIdentity`, `HistoryIndex` and shared metadata protocol. The initial RED
run failed because the production metadata classes did not exist. GREEN coverage:
19 behavioral cases / 275 assertions, two independent committed-state JVM reopen
checks, and actual SIGKILL at 12 identity/index publication boundaries (before
and after file sync, atomic move and publication directory sync), each reopened
twice. Every crash check compares the complete v1 snapshot/preparation/completion
files byte for byte with the source state before the optional operation.

Identity rulings:

- `resolve(exactUri, tabId)` preserves the exact nonempty native SAF URI spelling;
  it does not normalize, hash, resolve provider aliases or request grants. Same
  bytes at different URIs remain separate. Native URI is primary regardless of
  legacy snapshot document ID or reopened tab ID.
- A null/empty URI uses a separate TAB namespace and the exact nonempty tab ID.
  Only snapshots with a null/empty URI can match that alias. URI and TAB strings
  cannot collide across namespaces. Legacy `documentId` values remain intact in
  original envelopes and returned metadata; this backend does not rewrite them,
  invent document aliases, import URI grants or join histories by content/time.
- `forkAfterVerifiedSaveAs(oldId, destinationUri)` has an explicit controller
  precondition: call only after the real coordinator returned successfully with
  verified destination bytes and durable completion. Picker cancellation and
  every failed save must skip the call. The module itself cannot verify a SAF
  save and does not write the provider. A new destination gets a generated UUID
  with immutable origin provenance. An already bound destination keeps its ID;
  a new old-ID origin appends an immutable FORK edge. Duplicate origins are
  idempotent. Returning to the same identity adds no self-origin.
- The guard fixture calls the actual coordinator, including denied reads,
  truncating write failures and read-back mismatches. It demonstrates the future
  controller contract, not an implemented Activity flow or UI acceptance.

Index rulings:

- `recordVerified(historyId, transactionId)` uses fresh store transactions and
  snapshots, requires a valid actual completion and a matching native URI (or
  URI-empty tab alias), and stores both versions' full source metadata. A stale
  coordinator `Result.transaction.verified` flag is not used as evidence.
- Each verified transaction has at most one immutable ownership record. Repeated
  recording is idempotent. A global positive sequence is allocated as persisted
  maximum + 1 under the index root lock; duplicates, conflicting owners and
  overflow fail explicitly. Indexed groups sort by this sequence. Timestamps
  are display metadata. Unindexed verified groups remain in a separate state
  and deterministic UUID order, followed by pending pairs and orphan snapshots.
- Optional indexing never turns a successful provider save into a save failure.
  The future caller must report success plus a separate history error. Actual
  completions still establish recoverable versions when no optional record was
  ever published. Interrupted stages visibly block this optional enumeration;
  the original `ProtectionStore` API remains independently available.
- Every list validates the complete source/reference and optional metadata graph
  before filtering. Invalid completions, snapshot links, checkpoints, indices,
  identities, unknown entries and retained optional stages throw `IOException`.
  There is no partial list, fabricated completion, payload cache or pruning
  authorization. Each `Version` contains only immutable metadata and checkpoint
  names; all returned lists, descriptor origins and names are immutable.
- The minimal `HistoryCatalog.all()` addition returns a fresh immutable complete
  checkpoint scan under its existing lock. Indexing consumes it once per source
  scan, avoiding one full catalog scan per document. Snapshot validation remains
  streaming; no catalog caching or source format change is introduced.

Pass separate private sibling roots (normally `history_identity` and
`history_index`) beneath a trusted canonical existing parent; do not place them
inside the protection directory. Generated UUID filenames contain no URI or
content-derived path. Each metadata directory permits at most 4096 entries and
8 MiB including stages, each record at most 16 KiB, each strict UTF8 string at
most 4096 bytes. Direct protocol inventory tests accept the exact caps before
rejecting cap + 1, independently of wrapper stage rejection. Checksummed versioned
records use file sync, same-directory atomic move, directory sync, readback and
retained failures. Locks serialize all handles of each canonical root in one
process, consistent with the current store protocol.

Scope remains backend-only: no Activity, save coordinator, source store,
retention/deletion, external grants or runtime enablement changes. Roots lack a
shared cross-module graph lock; concurrent source changes are detected through
fresh metadata comparison, and common graph locking belongs to the retention
slice. Full source scans and checkpoint source validation perform disk I/O and
must run off the UI thread. Host faults/restarts do not establish Android app-UID
filesystem, SAF provider, sudden power-loss, device/Fold or release acceptance.
