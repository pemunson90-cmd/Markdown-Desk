# Latest statistics host checks

Run `bash verification/statistics/run.sh` with JDK17. The runner compiles the
actual LatestStatistics and EditorTools sources. Deterministic queue fixtures
check burst replacement, invalidation of posted results, document IDs and
shutdown. Real-thread latch/barrier fixtures check requests during an active
worker without sleeps. Unicode/locale fixtures retain public wordCount and LF
lineCount semantics. This is host concurrency evidence, not Android timing.

Activity integration is checked by `verification/status_behavior/run.sh test`;
measurement and limitations are in
[`docs/benchmarks/M0_3_DEBOUNCED_STATISTICS.md`](../../docs/benchmarks/M0_3_DEBOUNCED_STATISTICS.md).

## Bounded completion follow-up (2026-10-14)

[Plan](PLAN.md) records this bounded M2 slice. On baseline6630609, the real helper
retained1000main callbacks when1000requests each ran the worker before main was
allowed to consume anything. [Raw RED evidence](delivery-red-2026-10-14.txt)
records the failed one-callback assertion; those callbacks captured full Request
texts despite only the latest result ultimately being accepted.

The helper now owns one active calculation **or main delivery**, plus one latest
pending immutable input. Main delivery carries only Result(counts/keys/locale)
and generation, not the full source Request. Holding the active slot until
consumption bounds callbacks as well as worker submission. Invalidation clears
pending and rejects a running/posted result; close also prevents new admission.
EditorTools does not support interruption: a started count may finish naturally.

Executor submission happens outside the state monitor, preserving request/close
progress if submission is blocked. A task admitted just after close checks closed
before counting. Rejected executors close fail-safe. RuntimeException during
calculation drops that result and releases the slot; listener exceptions propagate
but a finally block releases the slot and schedules latest reentrant work. Fatal
Errors remain outside recovery guarantees. Counts, locale/revision/document API,
Activity's250ms debounce and its acceptance checks are unchanged.

Observed with Temurin17.0.16+8:

- `bash verification/statistics/run.sh`: **29delivery assertions +94existing
  statistics assertions PASS**. Existing locale/Unicode/LF semantics remain
  compared with actual EditorTools; its real-thread completion latch now proves
  no second count until main consumes the first callback.
- Delivery checks run at `-Xmx128m`:1000held-main request/worker cycles retain one
  callback, A-B-A keys reject old generation, invalidation/close drop pending and
  posted work, direct executors and reentrant throwing listeners progress,
  calculation RuntimeException and executor rejection release/close correctly,
  and close races a deliberately blocked worker submission without sleeps.
-32successive8MiB pending Strings are replaced while retaining32MiB headroom and
  holding one small completed result. This proves bounded pending retention;
  those large Strings are deliberately not counted, so it is not peak scan memory
  or a document-size policy/budget.

Independent review supplements the real-counter fixtures with deterministic
blocked-counter probes against the exact helper for invalidation and1000replacements
during counting, close during counting, RuntimeException recovery and maximum one
active calculation. Those probes exercise scheduling, not EditorTools semantics.

No benchmarks or full suite were repeated for this follow-up. Android/IME/Fold,
device latency/GC, process death and user-facing release acceptance remain NOT RUN.
