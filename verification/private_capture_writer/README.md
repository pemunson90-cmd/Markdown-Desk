# Actual capture writer checks

Use configured JDK17 on PATH and run `bash verification/private_capture_writer/run.sh`.
The fixtures use actual registered stores, immutable assembled MDOP records and the
production writer. This is a local publisher test, not completed capture, activation,
provider output, crash acceptance, Android or whole-heap proof.

The first writer operation persists only exact PREPARE under one primary mutation
lease. Its internal output FD is opened, written, synced and actually closed inside
that lease. No FD lifetime crosses a source/provider callback outside the monitor.
Any publication failure seals the original source owner; another same-process
attempt cannot guess that a failed internal resource or publication is quiescent.
No generic reset/rewrite/partial-stage repair is provided. Cold recovery remains a
separate unimplemented producer requirement.

Admission compares fresh complete initial control and operation-ledger domains to
the original plan, including completed legacy pins, before binding physical baseline
anchors or creating a stage. Native source predicates remain separate from explicit
own PREPARE transitions and the full ledger. Stage bytes/identity are recorded before
file durability callbacks; promotion expects the original stage identity before its
move callback. Every durability callback is followed by fresh full transition and
native-source verification. Unknown additions, changed old evidence and same-byte
inode replacements give no acknowledged writer. Written failure evidence remains.

The current transition admits absent→full PREPARE.stage→committed PREPARE,
funded namespace/preparation directories, and complete native-map/descriptor
stages→committed metadata. Each owned byte and slot consumes exactly its prior
future credit; baseline data stays unchanged. Partial metadata writes on failure
are retained without a replacement writer or automatic repair. Private-frame
live-prefix copying and BEGIN/LINK remain unfinished. Never ignore an operation
prefix or accept whichever state a callback leaves behind.

The writer also guards the entire synchronous durability invocation with the
preparation participant marker. A callback cannot manufacture quiescence while
its internal FD remains open, even after concurrent admission freeze. Borrowed
FD/durability calls are synchronous; they grant no deferred stream ownership.
The publication wrapper may nest inside a counted trusted source consumer callback;
it does not reject that existing participant or hold the source-owner lock. A
future private sink must supply its own exact internal mutation token: this wrapper
alone grants no sink, credit, stage or promotion authority.

One refresh workspace is prepaid from the original shared metadata budget. Control
decode/DTO/clone space is conservatively reserved; each fresh ledger uses only a
bounded child counter representing that prepaid workspace. Fresh snapshots remain
internal to the call chain and are discarded before reuse; no borrowed rows escape.
Compact physical baseline tables receive separate admission. These logical bounds
are not the pending full simultaneous heap/resource proof for the producer.

Current focused result:87 assertions. Tests cover actual PREPARE bytes, exact native
map/descriptor bytes and credit conversion, no accidental BEGIN, pending credits,
existing CLOSED capture, six durability failure points, same-byte PREPARE/native
replacement, old private-file metadata changes, foreign operation evidence, and the
completed legacy-pin change that leaves an absent operation-ledger hash unchanged.
Raw-provider destructive hazard diagnostics are separate and unmodified.

Every native-source proof/recheck now looks up the installed capacity identity and
requires the originally accepted instance, its ready flag and acknowledged policy
through `capturePolicyLocked`. The accessor requires the primary mutation lease;
it returns policy provenance only, without ordinary writer admission, reconciliation
or state mutation. Exact typed transitions still validate intentional PREPARE.stage.
Actual callback regressions cover a failed nested open installing an unacknowledged
replacement and a caught ordinary writer check revoking the same object's readiness.
Both refuse before PREPARE promotion and retain failure evidence; public policy()
semantics remain unchanged.
