# Production protected-save coordinator fixtures

Run with configured JDK17 bin on PATH:

```bash
bash verification/save_coordinator/run.sh
bash verification/save_coordinator/run_memory.sh
```

These import actual `ProtectionStore` and `SaveCoordinator`, with real temporary
files, file/directory sync and atomic moves on the host. Provider failure and
adapter failures are controlled boundaries. No Android/provider/power-loss
acceptance is implied. UTF8/BOM/CRLF, conflict/hash-bound consent, post-preparation
external changes, source-read failure, partial/zero writes, flush/close/read-back
failure, completion failure and pre-write corruption are checked. Captured bytes
are immutable; Unicode across validation buffers is preserved.

34cases/150assertions pass before storage memory repair. A failing corruption
case first demonstrated that persisted protection needed revalidation after the
provider re-read; `store.validate(transaction)` now does that before write.

Independent review reproduced OOME after mutation for exact8MiB source/edit
versions at128MiB heap. The memory runner preserves that fixture plus32MiB
explicit retained headroom while checking three saves (~48MiB protected data)
and listing. It is a host regression criterion, not a measured phone budget.
No automatic rollback/retry is used. The final check-to-write external race
remains explicit; generic SAF has no compare-and-swap guarantee. Low-level
provider-original-preservation failures remain separately visible.
