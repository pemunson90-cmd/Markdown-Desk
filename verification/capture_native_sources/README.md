# Native capture source proof

Run with the existing JDK: `PATH="$JAVA_HOME/bin:$PATH" bash verification/capture_native_sources/run.sh`.
The runner compiles production dependencies from source in a temporary directory;
no app consumer or required-runner registration is added.

`NativeCaptureFingerprint` is the internal source predicate for the future
`PrivateOperationStore.prepare/commitCapture` lifecycle. Its package-private API is:

- `capture(frozenMap, initialCompleteGraph, initialControl, store, catalog, identity,
  index, bootstrapQuotaBytes, sharedBudget)` → immutable logical source identity.
- `prove(freshControl, freshInstalledAcknowledgedPolicy)` → one `PhysicalProof`.
- `PhysicalProof.recheck(freshControl, freshInstalledAcknowledgedPolicy)` re-enumerates
  membership, streams hashes and checks the same physical identities.
- `PhysicalProof.close()` invalidates that proof. A second outstanding proof is
  rejected. Failed authorized rechecks invalidate the token; rejected foreign or
  unlocked calls cannot release another owner's workspace.

Every entry point requires the primary mutation monitor/lease. Proof tokens belong
to their creating thread; close is idempotent while the owner holds the lease.
The caller supplies **fresh** control and installed acknowledged policy on each
pass, checks the full ledger/exact preparation transitions independently, rechecks
after every durability callback, and retains one lease from final proof through
BEGIN. This helper grants no accounting, pin, activation or publication authority.
It does not persist state, call callbacks, copy sources or construct imports.

The initial complete typed graph and frozen manifest must match exactly, including
initial `graphHash`. Subsequent checks never construct/suppress a Graph or compare
its full fingerprint. Exact original envelope hashes preserve typed semantics,
edges, history identities and reservation owners; future map adoption/type
validation remains separate. The logical hash binds the four canonical absolute
role paths, bundle/registration/bootstrap, exact captured policy generation/hash/
quota/count, native envelope descriptors and native completion reservation bytes/
slots. It excludes generated archive IDs and operation/control publication details.
Native envelope bytes exclude operation actual bytes and future credits. Empty
native maps still bind the exact authority and policy.

Fresh passes enumerate exact four-root membership and the enrollment/registration/
historical-policy subset. New, missing, staged, unknown, nonregular and symlink
native members fail closed. File checks bind nofollow fileKey, size, mtime,
creation time and SHA-256; unavailable fileKey fails closed. Root keys are checked
without directory mtimes, which legitimate publications change. A proof detects
same-byte replacement during its callback lifetime; a later fresh proof deliberately
has no persistent inode anchor to a previous discovery. The logical hash itself
contains no physical inode identity.

The caller's **same** `HistoryStorage.Budget` must already charge every simultaneously
retained map, graph, control/policy snapshot and copy. The helper charges 80 KiB fixed,
2048 bytes per expected native/authority entry, 2048 bytes per directory and four
bytes per UTF-8 byte of retained paths/names (the control/parent directory estimate
conservatively uses the control path for both). Charges precede retained table,
path, attribute and scratch allocation. These accounting units cover Java objects,
reference/sort/seen tables, one proof workspace and transient path/hash/attribute
objects; they are conservative accounting units, not measured JVM object sizes.
One 64 KiB buffer streams payloads; there are no payload arrays or per-pass
snapshots/budgets. Workspace reservation is reused; neither failure nor close refunds
the monotonic budget. Old supplied snapshots must cease being simultaneously owned
or remain charged by the caller.

Focused evidence: 22 actual-backend scenarios, including all six native kinds,
unresolved completion reservation, empty capture, native membership faults,
callback replacement of native/registration/policy/enrollment/root identities,
policy A→B→A, role/bootstrap/acknowledgement guards, initial map/graph mismatch,
physical policy stage/addition, proof thread/lifetime rules and an 8 MiB native
payload with only 128 KiB of shared budget remaining. A legitimate funded
PREPARE→partial map stage→map/descriptor→BEGIN→LINK sequence preserves the native
proof while the actual full graph is incomplete and operation accounting is checked
separately. The thread-ownership test failed before its fix; the initial suite failed
for the absent helper before implementation. No full host gate, APK build, device
check, CI, producer or consumer activation is claimed by this packet.
