# Cold cancellation — ADR-006 second source slice

Run with the existing JDK17 on PATH:

```sh
bash verification/cold_capture_cancel/run.sh
```

The runner compiles production source with `--release 11` and runs 11 focused groups
at `-Xmx128m`. Fixtures use the actual registered store and actual PREPARE writer.
Cancellation runs in a real child JVM, discharging the original process owner by
process separation. No static owner reset/removal or reflection is used.

Coverage:

- Missing descriptor/map plus a bounded declared artifact stage: canonical embedded
  BEGIN204 and CANCELLED CLOSE126, exact retained old data identities/bytes/credits,
  pending state and unused LINK173/one-slot credit.
- Registered live/frozen/uncertain original owners, an unrelated replacement owner,
  quiescent and uncertain same-process Operations lifetimes, and insufficient shared
  metadata budget refuse without rewriting evidence.
- Partial/conflicting PREPARE/BEGIN, LINK.stage, unknown control, oversized and
  symlink data refuse without rewriting. Complete exact P/BEGIN stages need the
  explicit `PROMOTE_COMPLETE` option; default cancellation leaves them unchanged.
- Callback same-byte old-stage replacement and foreign terminal insertion refuse;
  callback terminal reentry and false quiescence are rejected by participant guards.
- Four distinct durability prefixes: BEGIN.stage, committed BEGIN, CLOSE.stage and
  committed CLOSE. Exact retry retains original IDs and all unused credits. Canonical
  CLOSE prefix lengths 0/94/125 reuse the existing append helper and preserve inode.
- Two independent fresh JVM reopens after normal cancellation, each stage promotion,
  each failure/retry and each CLOSE prefix recovery preserve classification, complete
  accounting, LINK funding and exact durable BEGIN/CLOSE bytes. Absent namespace
  remains ineligible; a present namespace with missing preparation directory keeps
  the directory reservation and is separately checked by two reopens.
- A subsequent genuine capture prepares, publishes, activates and reads its declared
  source while preserving old cancellation data and credits. All three pruning
  planner modes remain blocked.

Review boundary: only the private cancellation proof may account selected bounded
stale/short final artifact bytes as retained evidence. The public, normal
`PrivateOperationStore.inspect` remains strict: its ledger stays incomplete and new
capture admission stays blocked for invalid committed artifacts. Separate same-size
stale-hash and short-final cases preserve actual bytes and the persisted declaration;
canonical records/funding and strict refusal are checked in two fresh JVMs each.
Cancellation neither reads nor asserts original native source agreement with P.

The private proof binds canonical role roots, current typed control, complete funded
operation accounting, old physical control/data/stage identities and aggregate
byte/slot credits. Retained evidence plus one reusable refresh workspace are charged
before publication to the caller's existing shared 16MiB metadata budget. All control
reads are bounded and nofollow. The existing 1MiB/256-entry control allowance remains.
BEGIN publication, cold terminal claim and CLOSE publication share one primary lease;
every durability callback, including a throwing callback, revalidates the proof.

This is cancellation-only authority. It creates no LINK, captured binding, consumer
activation, data artifact or namespace; it repairs/deletes no LINK.stage. Completion
recovery, reversed original-graph proof, portable backup, runtime/UI integration,
full-heap/performance/migration, device durability and broad feature-completion gates
are excluded. The old 48-fault matrix, full host suite, APK builds and hosted CI are
not run by this runner. Existing maintenance/retirement guards are unchanged; their
prior independently reviewed admission coverage is not relabelled as a new run.

Compatibility regression check: `bash verification/history_operations/run.sh coordinator`
was RED on `53b1ded` at `OperationQuiescenceCheck.wholeTransfer` (line 102).
An ordinary counted consumer may request freeze/quiescence from its transfer callback;
its live resource still prevents a proof until actual closure. Cold-cancellation
synchronous durability callbacks have a separate participant marker and cannot make
those requests. All terminal claims continue rejecting every operation/source
participant. The marker is removed in `finally`, including throwing callbacks.

After the narrow marker fix, the unchanged coordinator runner passes all 59 checks;
the cold cancellation runner passes all 11 focused groups. Both compile production
source with `--release 11`. No full host suite, restart matrix, build or CI was run
for this compatibility correction.
