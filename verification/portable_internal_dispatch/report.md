# Frozen parser leaf for independent review

Base: `48126b3a1fb3e737dea04000a3ea424ef76fd8a0`.
Scope: internal structural dispatch/owner joins only; no lifecycle or funding implementation.

- `HistoryControlStore`: bounded internal MDOP version1/2 and pin-domain dispatch,
  with each selected codec still validating its complete envelope. Generic codecs unchanged.
- `HistoryPinRecord.Inventory`: nonnative BEGIN must match exactly one typed P;
  unowned native domain1 behavior remains available to its existing lifecycle.
- `PrivateOperationStore.control`: expected LINK uses the owning P's version;
  portable active/staged records remain unready. Closed controls retain existing
  reservation arithmetic. The cold-cancellation readiness bypass explicitly refuses v2.
- `PrivateOperationStore.PortableBaseline`: missing/committed LINK classification
  uses the owning P's version; raw old controls and credits are retained.
- `PrivateOperationEvidence`: explicit internal dispatch plus committed-control
  owner, version/domain, BEGIN/CLOSE and LINK commitments. Unowned pairs remain
  completed domain1 only. Full policy-chain checks and provisional raw sinks remain.

New control-retention references use the existing precharged per-control capture
allowance and bounded256-entry namespace; no Budget, allowance, source lifetime,
external callback, generic factory or persistent hash domain was added.

Evidence: initial behavioral RED rejected closed portable controls; final focused
runner PASS73. During implementation existing RecordCheck PASS72, EvidenceCheck
PASS53, baseline PASS59 and large-stage PASS1. `git diff --check` passed.

Unresolved joins intentionally remain: authentic portable Writer prepared-transition
publication, prepaid whole-path funding, activation using real inventory outer IDs,
tracked source/output/readback completion and terminal coordinator. No such API was
invented here. Internal structural decoding is not a substitute for these joins.
This packet proves parser retention of older portable controls, not an executed
Writer first/second backup, artifact reconstruction, activation or portable recovery.
