# Independent review: protected save tasks 1–2

Reviewed 2026-10-14 against baseline e05baac, ADR-001, current implementation plan, new-file diff, and latest working sources. The sources changed during review: this report includes the added pre-write store.validate(transaction) and the revised quota scan that processes one transaction at a time. No production/test edits or commits made by reviewer. Temporary reproduction sources are in /tmp.

## Verdict

**Spec compliance: substantial implementation, not complete acceptance.** Both exact byte versions are synchronously protected, committed, synced and revalidated before a single fallible provider mutation. Hash-bound consent, source recheck, uncertainty reporting, completion reservations, independent orphan snapshot enumeration and immutable completion records follow tasks 1–2. Latest pre-write validation fixes the earlier invalidation-during-source-recheck hole. Task 3/UI integration, operation provenance/UI consent binding, recovery presentation, device process death and power-loss acceptance remain outside this reviewed slice.

**Quality: changes requested for the entry-cap defect below.** Memory is a demonstrated constrained-heap limitation requiring resolution or a measured supported-device policy before near-limit production acceptance. No M0 or release approval. The existing low-level destructive provider probes remain separate and red as documented.

## Findings

### P2 — a successful commit can exceed MAX_ENTRIES and disable all subsequent recovery enumeration

ProtectionStore.java:195–210, 245–253. entries() accepts exactly 65,536 existing entries. commit() calls it before creating a new stage, but does not reserve an entry slot. The successful rename leaves 65,537 entries; snapshots(), transactions(), all later mutations, and construction of a restarted store then throw "Cannot enumerate protection directory". Byte quota does not prevent this: retained zero-byte stages are counted as zero bytes and are permitted by the existing interrupted-stage contract. Small actual envelopes can also reach the entry bound below 64MiB.

Observed reproduction: /tmp/ProtectionEntryProbe.java creates a fresh production store, seeds 65,536 zero-byte .stage files, and puts one one-byte attempted snapshot. Output: `putSnapshot succeeded; count=65537`, followed by enumeration IOException. Thus a successful API call leaves a state its own readers reject.

Fix: enforce available entry slots before creating a stage; reserve a completion entry when preparing, analogous to completion byte reservation, so subsequent attempts cannot consume it. Keep already-existing recoverable entries enumerable when at the cap. Add a boundary test with an injectable small cap or equivalent fixture. Do not prune retained evidence to make room.

### P2 — supported-size saves have a large heap requirement and can terminate after provider mutation

ProtectionStore.java:58, 106–112, 119–127, 134, 290–358; SaveCoordinator.java:64–87, 99–103. The snapshot/serialized-envelope/read-validation paths retain and clone several payload arrays, UTF-8 validation creates a full CharBuffer, completion rereads both snapshots, and public enumeration eagerly retains all payloads. The revised quota scan no longer retains the entire transaction history simultaneously, which is an improvement, but still rereads/hashes completed payloads while checking reservations.

Observed reproduction: /tmp/ProtectionMemoryProbe.java uses production store/coordinator with real host file/directory sync and atomic moves; saves exact 8MiB ASCII original and attempted versions, without editor or Android objects. Compiled against latest sources. `java -Xmx128m ... ProtectionMemoryProbe` reproducibly terminates with OutOfMemoryError at Snapshot.<init> -> readSnapshot -> readTransaction -> complete, after provider.write. Errors bypass the coordinator's Exception catch, so no SaveFailure is delivered. Durable prepared evidence already exists, so this is not proof of lost protected bytes.

Limits of the finding: `-Xmx256m` passes three such saves (~48MiB persisted), transactions(), and snapshots(); it also passes with an explicit additional 64MiB live allocation. No Android heap budget is inferred from these host settings. This does not establish that any specific supported phone fails.

Fix direction: reduce full-array copies/serialization buffers, validate UTF-8 and envelope hashes incrementally, use metadata-only reservation scanning and lazy validated recovery listing. Establish a constrained-heap regression bound tied to measured Android memory classes and editor headroom before accepting the advertised maximum document size. Catching OutOfMemoryError alone is not a memory fix.

## Verification observed

Ran with installed JDK17 on PATH:

- `bash verification/protection_store/run.sh`: 16 cases / 82 assertions; actual OS write-limit failure: 3 assertions, pass.
- `bash verification/protection_store/run_adapter.sh`: 27 assertions, pass; Android Os boundary is explicitly a host double.
- `bash verification/save_coordinator/run.sh`: 31 cases / 139 assertions, pass. These import production classes, not a contract model.
- Additional actual production probes above reproduced entry-cap failure and 128MiB OOM; 256MiB plus 64MiB headroom passed.

No unexplained failures in supplied suites. Initial script invocation lacked javac on PATH and was rerun with the configured JAVA_HOME; this was an environment invocation issue.

## Checked risks and remaining evidence limits

- Integrity/size: envelope checksum, payload length/hash, metadata encoding/bounds, IDs, transaction links and provenance are checked. Missing/corrupt referenced input fails explicitly. snapshots() can still recover valid orphans independently of corrupt transaction records. One corrupt snapshot causes snapshot enumeration to fail as the task's explicit-failure contract specifies; per-record salvage UI remains future work.
- Quota: actual files/stages and durable completion byte reservations are included; no automatic deletion. Repeated failed stages can consume quota, intentionally failing closed. No measured physical-free-space reserve is implemented; actual write/sync failures still stop mutation. The entry reservation is missing as described above.
- Symlinks: root/ancestor/static-entry symlinks are rejected; bounded reads use NOFOLLOW_LINKS. File creation/rename checks are not a hostile concurrent filesystem confinement mechanism, but the documented private root and single-executor scope does not imply such a threat model. The trusted Android getFilesDir parent must be canonicalized by the future factory; do not pass /data/user/0 aliases directly.
- Android durability: actual implementation propagates Os file/directory fsync and close errors, checks directory mode via fstat, and uses same-directory Os.rename. Relevant java.nio.file and Android Os operations are available at minSdk26 by API inspection. Host doubles prove call ordering/failure propagation only; actual Android fsync, API26 runtime, process-kill/storage and power-loss acceptance were not run here.
- Consent/races: stale consent and source changes before final check prevent mutation. Final check-to-provider-write race remains inherent and documented; storage revalidation adds work in that window but no generic SAF compare-and-swap exists. Binding consent to the originating document/revision must be enforced by the eventual UI request builder, because the pure Request accepts a hash string supplied by its caller.
- Provider errors: zero/partial write, flush/close/readback failure and mismatch retain both versions, do not rollback/retry, and report uncertainty. Completion failure similarly preserves prepared evidence.
- Coverage gaps: no production process-kill-at-every-boundary suite, API26 device runtime, full 64MiB recovery enumeration with actual editor memory, or actual Android disk-full/fsync fault was observed. The supplied coordinator suite runs with -Xmx1g and therefore does not detect the constrained-heap failure. No device, Fold, M0, or release gate is cleared by these results.
