# Portable funding preflight estimator

Run `bash verification/portable_funding/run.sh` with an existing compatible JDK
on PATH (`--release 11`). Only
this runner executes; the B1 fixture class is compiled and reused for real setup,
preflight, capture measurement and plan construction. Its broad main is not run.

This is a test-only candidate accounting model, not allocation/heap proof,
publication/activation authority or a production consumer. It observes actual B1
control/envelope/artifact/node/row/path/name dimensions, then checks a single debit
on that same original parent. No second metadata allowance or refund is used.
Read-only production accessors require the exact original budget and
measured, live inputs; they expose dimensions and immutable indexed coordinates,
with no source or writer authority. The estimator checkpoints do not reparse B1
metadata using independent codec budgets. The one-byte-under case refuses without changing the failed debit
or creating PREPARE/transport artifacts; closing B1 inputs does not refund parent.

`latest-output.txt` records this execution's exact counts and charge components.
Small path/time-length differences affect repeated runs; expected results are
checked properties, not fixed byte totals. The three required fixtures are minimum
preferences, two streamed 8 MiB tabs, and twelve tabs plus recovery/preferences.

Measured originals use actual observed strings. Unborn own inode/time strings use
explicit proposed enforcement caps of128/64 UTF16 units; private names/paths are
checked against255 UTF8 bytes/4096 units. Those future checks are NOT implemented
in production. The constant-space CURRENT native cursor's256KiB allowance is a
named candidate, not an established bound; no current-cardinality record maps
are charged/assumed. Fixed five-control/two-ledger snapshot slots and12B/8192 object
multipliers also await consumer allocation validation.

Dedicated behavioral fixtures verify budget identity (including equal remaining
credit), null/unmeasured/closed/failed/pending rejection, invalid ordinals, exact
serialized coordinates and capsule dimensions. Those fixtures are separate from
the four estimator checkpoints.

See [report.md](report.md) for results, remaining prerequisites and the required
second-capture evidence version-dispatch contract. No full suite/APK/CI ran.
