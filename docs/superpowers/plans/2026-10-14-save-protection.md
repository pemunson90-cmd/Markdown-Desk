# Durable save protection implementation plan

> For agentic workers: use superpowers:subagent-driven-development. Execute continuously under AGENTS.md and the owner's instruction to finish the app without checkpoint handoffs.

Goal: protect exact original and attempted bytes before any provider mutation, fail closed, expose protected versions for explicit restore to a new copy.
Architecture: an Android-independent immutable file store with an injected durability adapter; a pure save coordinator; a narrow Android/provider adapter and existing Activity integration. Existing session/recovery envelopes remain readable. No automatic rollback.
Tech stack: Java11 bytecode, installed JDK17/SDK37.0/build-tools36.0.0, current non-Gradle build.
Spec: docs/decisions/ADR-001-safe-save-boundary.md.

## Global constraints

- Package com.example.markdowndesk, min26/target35, 2.2.5/code18 unchanged until release preparation; canonical signing identity unchanged.
- Exact 8,388,608-byte limit per version including BOM. Preserve exact bytes, titles/URI metadata private; no logs/content/keys in Git/CI.
- Mandatory fail-closed file sync, same-directory atomic commit and directory sync; actual Android durability remains a device gate.
- 64MiB configurable global store quota, initially no automatic deletion. Include all files/stages; never prune unresolved/orphan protection.
- Low-level destructive provider probes stay red and visible. Recoverability tests are a separate contract; no atomic-URI claim.
- User has delegated design choices and explicitly instructed continuous implementation. No repeated design/plan approval prompts. Local minimum save protection repairs the existing data-loss boundary before optional history/workspace features; no milestone/release acceptance is implied.

## Review focus

Corrupt/oversized envelopes must fail closed; source changes after overwrite consent must conflict again; directory sync failure prevents mutation; failed/closed/switched Save As preserves identity; unresolved records and orphan attempted edits survive restart and full tabs.

### Task 1: Immutable protection store and Android durability
Files: create apps/MarkdownDesk/src/com/example/markdowndesk/ProtectionStore.java and AndroidDurability.java; verification/protection_store/ host tests.
Interfaces: ProtectionStore(File root, Durability adapter, long quotaBytes). Durability syncFile(FileDescriptor), move(File stage,File destination), syncDirectory(File directory), all IOException. Snapshot putSnapshot(String documentId,String uri,String title,long revision,byte[] bytes,String role). Snapshot fields id/documentId/uri/title/revision/role/timestamp/hash and bytes() validated defensive copy. Transaction prepare(Snapshot original,Snapshot attempted,String expectedHash); Transaction fields id/original/attempted/expectedHash/verified. void complete(Transaction); Transaction validate(Transaction) rechecks persisted preparation and referenced snapshots without mutation. List<Transaction> transactions(); List<Snapshot> snapshots(). Both enumerate independently and fail explicitly on invalid committed input rather than silently hiding corruption. Snapshots include orphans. UUID filenames, bounded binary envelopes with integrity validation; no shared mutable index. Fail-closed quota before write. AndroidDurability uses actual Android Os fsync/open/close for directory and atomic same-dir move.
- [x] Write behavioral failing tests (actual production imports) for byte fidelity, restart, immutability, hash/length corruption, source/attempt links, quota, symlinks, injected write/file-sync/move/directory-sync/validation failures.
- [x] Observe RED before production implementation.
- [x] Implement store and adapter; validate every committed envelope before returning. Protect orphan/staged files from implicit deletion. App-private root creation synced in parent directory.
- [x] Run host suite and SDK compilation; independent risk review.

### Task 2: Pure coordinator
Files: create SaveCoordinator.java and verification/save_coordinator/.
Interfaces: Provider read():byte[]; write(byte[]):void. Request(documentId,uri,title,revision,attempted,baselineHash,consentHash,newDestination). Result transaction/hash. save(Request,Provider) protects attempted first, reads and checks source, protects original, prepares, rechecks, writes once, reads back, completes. ConflictException.observedHash; protection/uncertain outcomes retain IDs and exact protected bytes. No automatic retry/rollback.
- [x] RED tests for partial/zero writes, flush/close/readback failure, conflicts/stale consent/final race, unsupported UTF8/size, attempts preserved on read failure, every durability failure blocks writes.
- [x] Implement and prove actual store/coordinator restart retrieval, not model-only behavior.
- [x] Review ordering, failure propagation and limits.

### Task 3: Activity save integration and protected recovery
Files: MainActivity.java, new ProtectedDocumentSaver.java if needed; existing save_lifecycle/recovery fixtures extended.
- [x] RED extracted Activity tests: every write passes coordinator; missing baseline requires explicit conflict consent bound to observed hash; Save As passes pending destination and original tab identity remains until verified success.
- [x] Integrate Android directory adapter on existing I/O executor. Capture immutable request on UI thread. Persist attempted edit synchronously inside save, not debounced legacy recovery. On failure show uncertainty and accessible protected recovery action.
- [x] Expose protected originals/attempts independently of session parsing and 12-tab capacity. Restore only to a newly selected copy, with verified bytes and no original overwrite. Keep existing legacy formats and modes.
- [ ] Run integrated host/unsigned/clean-rebuild, independent critical review. Keep PR draft while applicable safety gates remain unresolved.

### Task 4: History/retention/backup after foundational protection
- [ ] Build named checkpoints, history/diff and explicit restore-to-new-copy using validated snapshots; configure automatic policy and safe pruning (never unresolved/named/sole recovery).
- [ ] Add private state export/import with strict bounds and migration tests; keep platform backup disabled.
- [ ] Run failed-save/lost-work rehearsal fixtures; retain device acceptance NOT RUN until observed.

## Execution ledger

Ruling 2026-10-14: implement minimum mandatory protection as existing-boundary remediation before optional M1 features. It is the dependency needed to fix observed data loss, not clearance of physical or release gates. Owner's latest instruction overrides skill approval pauses and checkpoint stopping; no approval has been inferred for signing, publication of private inputs or fake green gates.
Task1 and2 share only declared store interface; task3 consumes their concrete contract. Task4 depends on reviewed transaction integrity. Every task's tests use actual production code; models/probes remain separately labeled.

Ruling: trusted Android getFilesDir parent is canonicalized before constructing store, because OS-owned /data/user/0 aliases may be symlinks. User-derived paths are never resolved into store filenames.

Task1 complete: scoped reviewR1 closes entrycap/memory findings; Task2 complete:35production cases157assertions; Task3 code/review complete withcaptured revision retry andstrictcodec, finalcommitted clean buildpending. Task4 blocked byunfulfilled M0 prerequisites; actualFold7 access/owner-run context requested, no gates waived.
