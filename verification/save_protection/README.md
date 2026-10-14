# ADR-001 save-protection acceptance model

**Model only. No production code is imported or changed. A pass does not prove
production safety, filesystem durability, Android SAF acceptance or M1 completion.**

This executable sketch follows
[ADR-001](../../docs/decisions/ADR-001-safe-save-boundary.md). Run:

```bash
bash verification/save_protection/run.sh
```

Observed 2026-10-14: **20 unittest methods passed**, exit 0; parameterized methods
exercise additional fault combinations. Log output stays in
`verification/build/save-protection/model-results.txt`. Python 3.10+ is required.
The central host runner and existing DocumentIO red safety probes are untouched.

## Executable assertions

| Boundary | Contract exercised |
| --- | --- |
| Private protection | Faults at write, flush, file sync, rename, directory sync and validation for attempted/original/prepared records stop provider mutation. |
| Restart model | Only records crossing the modeled directory-sync boundary survive; prepared references must validate independent hashes, lengths and document/revision identity. |
| Provider failure | Destructive zero/partial writes, flush/close faults, corrupt read-back and revoked read-back keep both exact versions and never publish success, retry or rollback automatically. |
| Outcome failure | All six outcome commit fault boundaries retain preparation and both versions; no success is published. |
| Integrity | Corrupt bytes, hash, length, missing envelope and mismatched document/revision invalidate prepared recovery. Restore refuses corrupt records before destination writes. |
| Conflict | Missing baseline, different source, stale hash consent, and a change after preparation stop writes. Valid consent protects the actually observed external version. |
| Remaining race | A provider edit after the final check can be overwritten. This passing observation documents a limit; it does not assert exclusive ownership or atomicity. |
| Quota/pruning | Exhaustion blocks repeated attempts without evicting earlier evidence; pending references, orphans, named and sole-recoverable records are exempt; interrupted eligible pruning retains protection. |
| Restore | Explicit distinct new-copy destination required; original and submitted edits restore byte-exactly, with read-back verification. Failed/mismatching destination copies retain evidence. |
| Revision | Success belongs to the captured revision; a later revision remains dirty. |
| Input policy | Oversized attempt causes zero provider reads/writes; invalid UTF8, oversized or unreadable source fails closed after retaining the edit. |

Fixtures use literal UTF8/BOM/CRLF bytes and independent SHA-256 calculations.
Faults are injected into state-machine boundaries. This checks a design contract,
not source strings, and does not reinterpret partial canonical-file destruction
as original-URI preservation.

## Model limitations and implementation constraints

The durable dictionary models ordering; there is no real file, sync syscall,
serialization format, Android grant or actual process kill. Its byte-count plus
128-byte record overhead is illustrative. Production quota must count actual
serialized records, both payloads, staging, preparation/outcomes and free-space
reserve. A 64 MiB quota is the ADR recommendation, not an implemented default.
The model pins every envelope and prepared transaction conservatively; it does
not implement the proposed successful-history retention policy or user deletion.
Restore destination distinction is object identity here; production must bind the
picker-selected URI/document identity and reject reuse of the canonical target.

The model is a reviewable acceptance sketch, not a drop-in production adapter or
validated history implementation. Before runtime integration, port these cases
to the actual coordinator/storage interfaces so the test assertions execute real
classes. Real filesystem durability tests must cover staging/rename/directory
sync failures and restart scanning from bytes on disk. Android process-kill,
provider/Fold/device acceptance remains **NOT RUN**.

Production must validate a versioned self-contained envelope before protection
counts as committed, including transaction/document/role/revision, exact payload
length/hash and generated safe identity. Prepared records bind both envelopes
and the observed source hash. Persistence uses one I/O executor; request bytes
and consent are immutable captures. Outcomes are append-only; corruption/missing
outcomes remain unresolved. Session, legacy recovery and transaction scanning
load independently, including orphan discovery and full 12-tab handling. No
unprotected overwrite bypass, automatic provider rollback, implicit evidence
acknowledgement or new backup behavior is permitted by this model.

Save As pending-destination identity, UI/tab lifecycle, legacy migration,
independent loader failures, actual serialized format validation and full
12-tab recovery are **not modeled here** and need their own integration cases.

## Next minimal production seam

After the prerequisite gates/review or an approved sequencing change, start with
a small app-private `ProtectionStore` outside MainActivity:

- Immutable request/envelope/reference records with byte payloads and hashes.
- `commitEnvelope`, `commitPrepared`, `commitOutcome`, `readValidated`, and
  `scanUnresolved` operations, with an injected private-file durability adapter.
- Adapter boundaries for unique staging write, flush/file sync, atomic same-dir
  move, directory sync and committed-byte validation. Unsupported durability
  operations fail closed; `AtomicCommit.replace` alone is insufficient.
- A reservation/eligibility policy that excludes unresolved, orphan, named and
  sole-recoverable data from automatic deletion.

First implement/test that seam against real host temporary files and controlled
fault injection, then a pure `SaveCoordinator` depending on `ProtectionStore`
and a provider read/write adapter. Only that coordinator may reach destructive
provider open after validated preparation and recheck. Keep MainActivity wiring,
Save As identity, independent startup recovery and explicit picker restoration
as subsequent reviewed slices; do not claim this model clears their gates.
