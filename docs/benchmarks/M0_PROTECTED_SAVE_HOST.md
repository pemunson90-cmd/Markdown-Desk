# Protected save host measurements — 2026-10-14

Actual production `ProtectionStore`/`SaveCoordinator` at `c326b61`, JDK17,
256MiB heap, real host file/directory sync and same-directory atomic commit.
Use the exact original M0 fixture bytes from `verification/build/benchmarks/data`;
the edited version changes only the first ASCII byte, preserving size.
Each fixture uses a fresh store; three sequential saves intentionally accumulate
versions. No warmup. Later samples include growing quota/integrity scanning,
so these are not independent steady-state samples or an Android latency budget.

```csv
fixture,bytes,first_ms,second_ms,third_ms,median_ms,persisted_bytes
10k.md,57020,59.276,39.170,36.479,39.170,345939
100k.md,570200,35.086,54.159,65.152,54.159,3425031
500k.md,2851000,117.320,176.420,223.362,176.420,17109831
exact-8MiB.md,8388608,269.828,428.267,645.740,428.267,50335551
```

Three8MiB original/edit pairs persist50,335,551bytes including envelopes and
records, below64MiB quota. Four full pairs would exceed64MiB once metadata is
included. Pruning/configurable history remains required before release; the
current foundation fails closed without deleting earlier evidence. No unprotected
restore/save bypass is offered when quota is exhausted.

Reproduce once after host compilation:

```bash
export PATH="$JAVA_HOME/bin:$PATH"
javac --release 11 -cp verification/build/save-coordinator -d verification/build/save-coordinator verification/save_coordinator/ProtectionMemoryProbe.java verification/save_coordinator/ProtectionBenchmark.java
java -Xmx256m -cp verification/build/save-coordinator com.example.markdowndesk.ProtectionBenchmark verification/build/benchmarks/data
```

The result measures protected transaction I/O/validation on this host, excludes
Android Binder/WebView/UI and actual SAF providers, and is not comparable to
unprotected write-only timing as a data-safety guarantee. Device Fold7 baseline
and candidate measurements remain NOT RUN. No performance threshold is invented.
