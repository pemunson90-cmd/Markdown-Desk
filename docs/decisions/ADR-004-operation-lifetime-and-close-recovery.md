# ADR-004: Operation lifetime and canonical pin-close recovery

Date: 2026-10-15

Status: accepted design; implementation and behavioral verification pending.
This extends ADR-002 within its existing single control allowance. It does not
revive ADR-003's superseded extra root or allowance.

## Context

Durable BEGIN/CLOSE records retain operation sources across process death. Their
terminal reservation is accounting credit, not proof that consumers have stopped
using those sources or that physical writes will succeed. A partial CLOSE can be
a valid canonical prefix while still blocking release. Current monitor/mutation
leases do not track asynchronous resource or callback lifetimes.

Backup/export must not release pins while its readers, writers or callbacks can
still use them. A CLOSE receipt cannot establish successful provider output.

## Decision

One shared per-parent operation coordinator binds exact registration/roles,
operation UUID, BEGIN hash, process nonce and checked epoch. Consumer admission,
stream opening, I/O, closure and callback delivery are tracked through private
owned wrappers. Delegates and caller-closeable counters do not escape. Adapters
remain trusted internal code; this is cooperative coordination, not a sandbox
against arbitrary same-process code or private-file tampering.

Freeze rejects new admission and old-epoch work. Actual registered consumer work
and positively established owned closure must finish before a terminal capability
is minted. Indeterminate opening/closure remains blocking. Cold terminal discovery
can bind a frozen operation without opening consumers or requiring a manifest for
cancellation. Activation and a terminal attempt exclude each other atomically.

No backend waits for drain under the history monitor or mutation lease. Private
participant context covers factories, I/O, closure, callbacks and proof signaling;
terminal entry rejects inline participant continuations even after counts become
zero. Future completion occurs outside both locks, including caller-owned history
coordination. An independent async continuation may overlap a metadata-only
signaling epilogue once all registered consumer work is finished. This does not
detect arbitrary caller-created scheduler cycles or make external blocking joins
safe. No compulsory terminal thread is introduced.

Every terminal route requires exact capability checks, including full-stage
promotion and audit acknowledgement. Synchronous already-quiescent audit paths
may use an exact no-signaling fast path; locked helpers never drain. Existing
maintenance preflights run outside its mutation lease, preserving retirement and
the final control/root/survivor proofs.

Explicit CLOSE recovery may append only the missing suffix of an exact canonical
126-byte prefix on its existing inode, without create, truncate or rewrite. Up
to 93 bytes leaves outcome unbound: explicit recovery may choose CANCELLED. At
94 bytes or more, preserve the encoded outcome. Fresh RELEASED closure and
RELEASED-bound stage recovery require the original independently persisted manifest. A complete stage still
requires quiescence, exact readback, sync/promotion and final callback-safe proof.
Physical occupancy plus remaining terminal credit stays 126 bytes in the same
slot/allowance; no repair record is introduced.

Committed historical CLOSED audits remain immutable and reconcilable without
source or manifest dereference, including legitimately retired inputs. Adopting
one permanently seals that operation against consumer resumption. Partial BEGIN,
nonprefix, conflicting or unknown evidence remains preserved and blocked: absence
does not prove a BEGIN was never acknowledged.

## Consequences and gates

Native and control wire formats, policy ancestry and the single 1 MiB/256-entry
allowance remain unchanged. Ordinary unrelated immutable appends retain quota
admission. No runtime export consumer is enabled by this decision.

Stuck work, unknown evidence, full journals, physical ENOSPC or partial BEGIN may
still prevent cancellation. Compaction, a sufficient BEGIN provenance protocol
and a fresh-process arbitrary-evidence-loss anchor remain separate work. No
unlimited availability or universal corruption protection is promised.

The reviewed [implementation plan](../superpowers/plans/2026-10-14-operation-terminal-recovery.md)
defines exact APIs, ownership and adversarial tests. Implementation must pass
resource/callback/epoch/lock-order checks, canonical prefix and OS write-failure
fixtures, real SIGKILL/fresh-process reopens, existing native regressions and the
clean host/unsigned integration gate before runtime consumers are considered.
Android/provider/Fold, canonical signing and upgrade acceptance remain separate.
