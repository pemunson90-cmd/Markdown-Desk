# Durable automatic-retention consent (phase1 only)

Production files: HistoryControlStore.java and HistoryCapacity.java. No cleanup runtime,
planner/executor, controller, Activity, HistoryPolicy or portable-state format edits.
No automatic-enabled UI is authorized by this prerequisite alone.

New `HistoryCapacity.setPolicy(HistoryPolicy, boolean automaticEnabled)` requires the
current installed ready authority and fresh complete accounting even for a triple
no-op. It appends one chained type3 policy record when quota/count/consent changes.
Enabled+count0 is rejected. The existing one-argument setter still appends exact
legacy type2 bytes every time, conservatively disabling consent. Enrollment and
all historical policies retain their bytes, default quota64MiB/count20 and disabled
consent. New records are a strict checksummed variant: same legacy body plus one
byte0/1; unsupported types, malformed booleans and trailing bytes fail closed.
Existing generic Policy inventory/stage/hash-chain handling recognizes this variant;
no stage becomes effective before explicit successful reconciliation. Old readers
reject unknown type3, never reinterpret it as a legacy policy.

The consent boolean is immutable metadata, not a disconnected preference or deletion
capability. Existing operation bindings retain exact generation/hash semantics; the
fixture checks old/current binding matches across opt-in and opt-out. Policy Apply
never deletes pre-existing native files, including named and latest indexed history.
Every append still charges its actual encoded length (one byte more than legacy)
and one shared control slot, preserves current reservations and physical file-key
checks, and uses the unchanged publication/reconciliation engine.

Run with installed JDK17 on PATH:

```
bash verification/automatic_retention_policy/run.sh
bash verification/history_capacity/run.sh codec
bash verification/history_capacity/run.sh policy
bash verification/history_capacity/run.sh admission
bash verification/control_inspection_bounds/run.sh
```

TDD: initial new runner failed compilation on the absent flag, explicit setter and
variant encoder (19 missing-API diagnostics). GREEN:81 assertions covering independent
wire bytes, legacy bytes/defaults, strict flags/framing, reopen, stale authority and
blocked triple no-op, six finite publication/reconciliation fault boundaries, valid
and malformed staged variants, broken hash chains, no native deletion and historical
operation binding semantics, and shared control capacity. Existing codec209,
policy49, admission61 and control-inspection-bounds30 assertions PASS. Final command
output is summarized by the implementation handoff; no broader suite/device claim.

Resource-test distinction: real mixed valid chains fill255 then256 entries and prove
refusal causes no artifacts; a real sparse unknown file exhausts bytes and proves
fail-closed/no artifacts. A policy-only valid chain cannot approach1MiB before the
256-entry ceiling. Therefore exact1MiB byte acceptance/refusal is separately tested
through the unchanged production `admitControl` predicate using a reflected immutable
Inspection with modeled accounted sibling occupancy. This does not claim a real
near1MiB valid operation/pin ledger fixture. No new allocations scale with source
payload or quota: one immutable boolean per decoded record/EffectivePolicy, at most
one extra encoded byte per variant and existing bounded codec/publication copies.
No whole-job/whole-heap bound is claimed.

No crash battery, APK, Android/device/process-death or full automatic-retention
acceptance was run. Execution/replay, scheduling and actual opt-in UI remain later
reviewed phases. No commits in this worker scope.
