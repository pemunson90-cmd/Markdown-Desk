# Explicit shared history capacity

Use the existing JDK17, with its `bin` on `PATH`:

```bash
export PATH=/workspace/.command-library-env/jdk-17.0.16+8/bin:$PATH
bash verification/history_capacity/run.sh all
```

Supported groups are `codec`, `policy`, `admission`, `integration`, and `all`.
The default is `codec`; invalid modes exit 2. `all` runs the four groups and
the real restart driver once. Restart evidence can also run independently:

```bash
python3 verification/history_capacity/run_restart.py
```

The fixtures compile actual production Java using Java11 bytecode. Codec tests
use 64MiB heap; controller/admission/graph tests and child JVMs use 128MiB.
Only synthetic app-private files are used. No Activity caller opts into this
backend, and no pruning, compaction, provider write or archive operation is added.

## Authority and accounting

`HistoryCapacity.register` explicitly binds protection, catalog, identity and
index as distinct canonical direct siblings, plus immutable bootstrap quota and
initial policy. `open`/`reconcile` validate and sync disk authority before enabling
writes. Constructor hints preserve legacy per-handle quotas before enrollment;
after enrollment every writer uses effective shared quota. Reopen hints must
match the persisted bootstrap quota, independently of the current effective quota.

The fixed parent `history_enrollment.control` contains the exact registration
control envelope, binding the same bundle UUID, roles and registration hash.
Publication syncs its stage, atomically moves it and syncs the parent **before**
creating `history_control`. Marker or marker-stage presence disables legacy
fallback even when the entire control root is missing. Malformed markers and
incomplete initial registration remain blocked; this slice neither reconstructs
missing authority nor deletes retained evidence. Initial interruption before a
complete registration stage can therefore leave enrollment intentionally blocked.

One shared 1MiB/256-entry control allowance covers the parent marker and its stage
plus all control records/stages. It is separately reported and cannot fund data.
Control framing remains magic `0x4d444843`, version1, type1 registration or type2
policy, bounded body length and SHA256 checksum; maximum entire envelope 16KiB.
Existing snapshot/preparation/completion and optional metadata v1 bytes are unchanged.

Data admission counts actual bytes across all four roots, every retained stage,
and exact outstanding native completion byte/slot reservations. It retains the
store 65,536-slot limit and each metadata root's 8MiB/4,096-entry limits. Unknown,
nonregular or unreadable entries and invalid reservation proofs grant no capacity.
Fully accounted optional semantic/size damage remains charged and blocks pruning
and the affected optional writer; mandatory protection can still proceed when
aggregate quota and accounting permit it.

Public mutators acquire the shared parent mutation lease before callback-capable
validation. Same-thread nested mutation rejects; other threads serialize on the
monitor. Typed role/dependency checks cover pre-enrollment handles, including
quota setters. This is a single-process protocol, not an inter-process lock.

Only a freshly validated transaction-bound native completion conversion receives
the zero-growth exception after lowering quota. Matching full deterministic
completion stages retry through read-only sync without rewriting; partial,
changed or foreign stages, corrupt sources, missing preparations and uncertain
policy authority reject. Other writers still require available shared capacity.

`HistoryGraph.desiredProfile` (and compatibility `policy`) is caller preference.
Nullable `effectivePolicy` is acknowledged authority for that exact four-root
bundle. Its fingerprint includes control entries/hashes, effective generation,
bundle, quota/count, diagnostics and native completion reservations. Scans never
register, reconcile, promote or delete files.

## Observed verification

- Codec: 14 cases/209 assertions, including strict framing/UTF8/roles, broken/forked
  chains, bounded oversized inventory, symlinks and retained stages. Original
  Task1 RED was an explicit missing-codec failure.
- Policy: 49 checks covering registration, bootstrap hints, generation publication,
  injected file/move/directory-sync failures and exact reconciliation.
- Admission: 61 checks covering all writers, reentrancy, typed roles/dependencies,
  aggregate data/stages/reservations, the last-capacity two-handle race, native
  metadata limits, optional damage and independent legacy constructor quotas.
- Integration: 40 checks covering desired/effective policy, exact graph role binding,
  final enrollment recheck, lowering quota, second-handle completion and negative
  conversion proofs. A valid chain at exactly 256 shared entries rejects another
  policy without a stage while allowing ordinary data and exact completion.
- Restart: 10 actual SIGKILL barriers, each reopened twice in independent JVMs:
  enrollment stage-sync/move; control-root creation; registration stage-sync/move;
  policy stage-sync/move/directory-sync; zero/9-byte policy-stage prefixes created
  by actual OS write limits before file sync. Seven additional fresh-process
  cases cover missing root, malformed marker, marker stage, partial policy,
  corrupt full control, a valid full shared allowance, and genuine never-enrolled
  absence. Existing native data records remain byte-identical.

The affected legacy store/catalog/identity/index, read-only graph and pure planner
runners passed locally using their unchanged scripts with `JDK_JAVAC_OPTIONS`
supplying the actual sourcepath. Integrator owns permanent runner dependency
updates and the full host/unsigned-build gate. These checks prove host process
interruption behavior; Android app-UID, sudden power loss, SAF, Fold and upgrade
acceptance remain separate unrun gates.
