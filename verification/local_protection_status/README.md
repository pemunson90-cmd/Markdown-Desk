# Read-only local protection status

Run with JDK17 on PATH:

```sh
bash verification/local_protection_status/run.sh
```

The fixture compiles the actual HistoryController and real native storage/capacity
dependencies. Only durability callbacks/failures are controlled; no accounting
implementation is copied. Latest result: **40 checks PASS** under128MiB heap.

Coverage: preserved64MiB/20 defaults; exact actual/pending-completion/control totals;
charged bytes include native/operation reservation fields once; no file/generation
change on inspection; immutable scalar fields/enum set; old controller follows
replacement installed authority and persisted policy; null/foreign authority refuses;
unknown files return incomplete/blocked flags; uncertain retained policy is not
promoted; accounted72MiB stages report overQuota without deletion; real generation,
authority replacement and policy changes through inspection callbacks refuse;
directory validation failure reports incomplete; unchecked access failure becomes
fixed sanitized IOException with no raw cause. No fatal Error is caught.

New API: HistoryController.inspectLocalProtection(), synchronous on existing I/O.
Status is a coherent point-in-time projection, not free disk space, pruning
eligibility or write admission. Incomplete totals are not an available-capacity
allowance. Four fixed enum flags replace raw backend diagnostics. Existing actual
bytes already include operation-data actual bytes; the reservation fields are
separate. The operation-reserved forwarding test exercises the zero-reservation
native fixture, not a new portable-operation lifecycle.

Test-first RED was missing DTO/API compilation; a later injected unchecked-access
failure was RED before exception sanitization. Existing history_ui runner also
passed123 functional checks,6 assertions in3 fresh JVMs and captured-revision race.
No capacity crash battery, APK/device test or broad aggregate ran. No Activity,
policy setter, settings controls, pruning, registration/reconciliation behavior or
serialized format was changed. Configurable policy UI and actual automatic
execution remain open requirements; this is the read-only first slice only.
