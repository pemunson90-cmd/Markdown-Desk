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
