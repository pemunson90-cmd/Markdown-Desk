# Sequential current-store terminal validation prerequisite

Run `bash verification/native_terminal_validation/run.sh` with JDK17 on PATH.
This exercises the new `ProtectionStore.validatePinNativeLocked` predicate against
`pinInventory` terminal acceptance and exact actual/reservation totals, including
legitimate later appends, malformed snapshots/transactions/references/completions,
full/partial/corrupt/orphan completion stages and legacy anonymous stages.

The supplied original Budget is charged **512 KiB once**, before workspace
allocation. Passes reuse the allowance; no per-member cumulative debit or fresh
Budget is created. The 8 KiB hash scratch is reusable. Existing snapshot readers
stream with 8 KiB scratch; transaction raw/prefix buffers are at most32 KiB.
The separate fresh-child8 MiB heap probe reads an8 MiB snapshot, excluding payload
allocation on the new validation path. These are resource-contract checks, not an
Android heap measurement or proof of a256 KiB whole-operation envelope.

Conservative simultaneous temporary allowance within512 KiB:

- Up to four snapshot metadata DTOs during committed-completion comparison:
  128 KiB (each includes up to three4096-byte strings, UTF16 and object overhead).
- Transaction raw/checksum-copy buffers and completion prefix:96 KiB.
- Generated completion envelope/BAOS transient copies:128 KiB conservative cap.
- Metadata encode/decode temporaries:64 KiB.
- Hash scratch, paths, wrappers, attributes and digests:96 KiB.

This sum is512 KiB; it deliberately overcounts mutually exclusive peaks. It
assumes ordinary supported JVM/Android object/provider metadata sizes, not an API
bound on arbitrary provider-defined opaque fileKey objects. Retained visitor rows,
root anchors and the original Budget object are owned/accounted by their callers.
Supported store root representation is capped at4096 UTF16 code units before
workspace allocation; accepted leaves are canonical UUIDs and known suffixes
(maximum53 characters). The validator rejects null keys, foreign workspace/store,
missing lease, reentry, history-generation changes and root-key replacement.

Visitors receive synchronous borrowed metadata and actual BasicFileAttributes;
they must separately fund retained rows and must not invoke provider/durability
callbacks or mutate history. The generation and current-member post-visitor checks
provide defense in depth. This pass is not an atomic filesystem snapshot: an
external writer can alter an earlier member without generation changes. A funded
retained identity table and a complete second pass across every durability
callback remain required for HistoryPins. No Pins caller uses this API yet.

Acceptance preserves the old terminal diagnostic policy: only retained-stage
warnings are tolerated for store members. The new sequential implementation does
not reproduce the old inventory's whole-map16 MiB resource exhaustion; instead
it has explicit fixed reader funding and the existing65536 physical-entry bound.
It does not cap ordinary appends, alter old pinInventory behavior or authorize
terminal publication. Exhausted retained-table capacity is a later caller concern.

Review regressions also exercise transaction growth between the first size check
and the reader's allocation attribute sample. A test-only File subclass triggers
sparse growth on the second `toPath()` call and reflection invokes the real private
`readBody`; an8 MiB child must receive IOException before allocation, rather than
OOM. `readEnvelopeBytes(File,long)` now checks its exact allocation sample against
the supplied maximum; transaction readers supply32768 while ordinary envelope
callers keep MAX_ENVELOPE_BYTES. The test contains no production hooks.

A second independently funded workspace cannot reenter the same ProtectionStore
instance while a visitor is running. The test catches nested refusal inside the
visitor, verifies the outer pass completes, then reuses both workspaces normally.
The guard is per instance under the primary monitor; cross-handle visitor misuse
is outside this prerequisite's agreed same-instance contract.
