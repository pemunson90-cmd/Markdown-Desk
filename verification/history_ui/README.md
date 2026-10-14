# Functional document history controller

This directory implements the reviewed controller portion of
[PLAN.md](PLAN.md). `HistoryController` uses the real protection, identity,
catalog, index, capacity and bounded-diff backends. MainActivity integration is a
separate root-owned change: no UI, save callback, session format or backend was
edited here. The controller is synchronous and must run on the existing I/O queue.

```sh
PATH=/workspace/.command-library-env/jdk-17.0.16+8/bin:$PATH verification/history_ui/run.sh
```

Observed local evidence on 2026-10-14: **123 functional checks**, **six assertions
in three fresh JVMs**, and the **owned-byte capture race check** pass with JDK 17,
`--release 11` and `-Xmx128m`. The runner executes actual production classes over
synthetic native files, real sync/atomic move operations and an in-memory provider.
It does not use mock implementations of the history backends.

Coverage includes:

- Exact URI reopening across tab IDs, distinct URI spellings/equal bytes,
  12 isolated unsaved TAB identities, legacy recovery kept out of per-document
  list/diff membership while retaining its native protection snapshot.
- Strict immutable capture of BOM/CRLF/whitespace/Unicode bytes, action revision,
  metadata and identity. Exact 8 MiB document and 256-byte label boundaries;
  malformed UTF-8, invalid metadata and alias mismatch reject. Label failures
  before move and after move retain the protected snapshot and report an
  unconfirmed label outcome; a subsequent ordinary save still succeeds.
- Actual coordinator completions checked before any optional identity/fork/index
  publication; mismatched tab/destination/revision and pending/failed saves cannot
  establish ownership. Same-URI save, existing destination identity, unsaved Save
  As origin and idempotent indexing retry are exercised. Optional identity/index
  publication failures do not change provider bytes or remove native completion.
- Four-role startup, acknowledged policy preservation and automatic retention OFF
  retaining mandatory protection. Missing role, corrupt enrollment, partial policy,
  maintenance and pin stages block writes with zero provider writes and unchanged
  retained envelopes. Fresh JVMs require explicit open before native mutation and
  reject corrupt enrollment/partial pin startup without legacy fallback.
- Under a registered bundle, optional catalog/identity/index semantic corruption
  remains fully charged: mandatory coordinator saves succeed while optional
  history operations report failures.
- Real diff membership, byte/line/work/output bounds, immutable results and changed
  source rejection. An actual `ProtectionStore.inspectSources` directory-sync
  callback holds validation open; `ThreadMXBean` observes another native writer
  BLOCKED on the validation thread's monitor. A reentrant mutation in that actual
  validation callback invalidates the generation and rejects the result.

The latch demonstrates **validation-stage** exclusion, not measurement of the
inner diff algorithm. Source scope holds the common parent monitor and enclosing
`withValidatedSources` guard through membership, both payload reads and complete
`HistoryDiff.compare`; no Snapshot handle, lazy stream or caller callback escapes
that operation. This is single-process cooperative protection for a nonresumable
read. It is not a cross-process lock or durable operation pin. Existing readers
validate byte integrity; no arbitrary private-file tampering guarantee is added.
Diff retains existing caps and can load one full <=8 MiB snapshot byte array per
side in sequence; this is not zero-copy or a measured Android latency budget.

The initial missing-class runner failure was new-feature setup, not a regression
proof. Two behavioral failures were observed and fixed during implementation:

- A rejected invalid indexing route initially claimed that a save remained
  verified. The diagnostic now reports only optional indexing status; the caller's
  coordinator result remains the sole provider-success outcome.
- Independent review reproduced caller-buffer mutation between validation and
  copying. The default race fixture observed that actual constructor boundary and
  failed before the fix. The constructor now checks size before allocation, copies,
  validates the owned copy and retains those exact bytes. The fixture uses real
  thread-stack observation, with no production callback; inability to observe the
  boundary fails explicitly rather than passing an unexercised race check.

`recordVerifiedSave` independently verifies destination/tab/revision/completion,
but the source URI in `SaveRoute` is trusted provenance from the Activity capture;
it cannot be reconstructed from a destination transaction. Runtime integration
must freeze that route before tab URI adoption and preserve it across conflict
consent. No identity fork is invoked merely on picker launch or cancellation.
The Activity must report `IndexResult` separately and preserve newer dirty text,
URI adoption, recovery and successful save continuations. Those actual Activity
hooks, stale callbacks, locks, Focus/View and picker/full-tab behavior are **NOT
RUN in this controller-only change**; their extraction tests remain in the plan.

`open` publishes no partially initialized controller. Keep the separate raw
protection read accessor available when shared authority cannot initialize;
mandatory write paths require successful controller startup. Do not silently
fall back to legacy writes. This unit does not repair partial pins/maintenance,
execute deletion, alter quota policy, export/restore archives or adopt private state.

No unchanged full suite, performance benchmark, Android device, Fold, SAF, IME,
TalkBack, app-UID durability, signing or upgrade acceptance was run. These remain
unreleased-candidate integration and physical gates; no M0/M1 completion or release
waiver is claimed. Root owns runner registration and the coherent integration gate.
