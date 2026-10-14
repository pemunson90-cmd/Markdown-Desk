# Issue 3 — debounced latest-only status statistics

The status refresh now displays current characters, title, Save state and mode
immediately. Until current word/line/read-time/goal counts are available it says
`Calculating statistics`; it never displays an old document/revision's counts
as current. After 250 ms without another edit, the UI captures one immutable
String and a dedicated statistics executor scans it. The helper retains at most
one running scan and one replaceable pending snapshot. Edits (including
suppressed programmatic replacements), document identity and locale invalidate
old results. Activity destruction removes the debounce and closes the helper
before shutting down its executor. Statistics never use the save or render
executors. BreakIterator word semantics and LF-only line semantics are preserved.

## Scoped verification

With Temurin JDK 17.0.16+8 first on PATH:

```sh
bash verification/statistics/run.sh
bash verification/status_behavior/run.sh test
bash verification/status_behavior/run.sh bench
```

Observed: **93 helper assertions** and **57 status assertions** pass. The helper
tests execute actual production source, including a real worker gated with
latches and a completion barrier: 1,000 replacement requests while it is running
submit no additional executor tasks; the original scan and newest replacement
complete, and only the newest result reaches the listener. Unicode fixtures in
US/French/Japanese/Thai locales match the original public counter and LF count.

The status harness extracts actual MainActivity statistics fields/methods,
TextWatcher and onDestroy through the compiler AST. Deterministic Android-facing
fakes exercise the pending display, exact settled strings, dirty/title/Save,
Write/Read/Split, read-only/saving priorities, word goals, cursor/text fidelity,
burst coalescing, stale posted completion, tab identity, suppressed same-length
replacement, locale invalidation and lifecycle shutdown. These are host checks,
not Android Editable, Handler, IME or device acceptance.

Integration must add `bash verification/statistics/run.sh` to the full host
runner alongside its existing status behavior check; the shared runner is owned
by the integrating agent. This scoped slice intentionally does not run the
unchanged full suite or unsigned builds again; those run once on integration.

## Matched host measurement

Before source: base `d8d595a`; existing exact extracted updateStatus harness.
After source: this change's exact extracted TextWatcher → updateStatus path,
with the same fixture construction, counted snapshot implementation, heap,
JDK, warmup, three samples and GC before each sample. After deferred samples
run snapshot → helper scan → result application synchronously through host fake
queues, excluding the intentional 250 ms debounce wait. The before timed path
contains updateStatus alone; the after immediate path also includes the watcher
and its mocked scheduling calls. This does not measure total keystroke work in
Android or the unchanged recovery/highlight/preview paths.

Environment: Debian 13, Linux 6.18.44, AMD EPYC 9V74; 5 CPUs reported by lscpu;
Temurin 17.0.16+8; `-Xms128m -Xmx1g`. Three samples are too few for p95.
Heap is maximum post-operation used Java heap, not peak memory or RSS.

| Words | Characters | Before samples ms / median | After immediate samples ms / median | After deferred samples ms / median | Max used heap MiB, before / after |
| ---: | ---: | --- | --- | --- | --- |
| 10,000 | 57,020 | 5.752, 10.649, 2.325 / 5.752 | 0.655, 0.167, 0.123 / 0.167 | 5.444, 12.356, 1.285 / 5.444 | 1.6 / 1.6 |
| 100,000 | 570,200 | 7.893, 5.153, 8.222 / 7.893 | 0.110, 0.121, 0.076 / 0.110 | 7.163, 5.015, 4.969 / 5.015 | 5.7 / 5.7 |
| 500,000 | 2,851,000 | 29.877, 24.911, 27.252 / 27.252 | 0.157, 0.087, 0.092 / 0.092 | 30.080, 27.603, 29.591 / 29.591 | 13.7 / 13.7 |

Immediate editor snapshots per refresh: **1 before, 0 after**. Deferred snapshots
per settled edit: **1 after**, and the worker still performs full exact scans.
The deterministic improvement is moving scans out of the synchronous status
refresh and coalescing bursts; these noisy samples establish no Android latency
budget or total CPU/heap reduction. An already-running obsolete scan finishes
before the latest request, and one full text snapshot still runs on the UI
thread after debounce. Phone/Fold/IME/TalkBack/memory/GC timings: **NOT RUN**.

Measured source SHA-256:

- MainActivity: `5fbf44702c6ba11994f8ecdbb55b1ae84752852419d7daf3006d485a73e32229`
- EditorTools: `8220e809bd77c90eb1df4c03eed9a85262349b6e87485a5db68b2eaf2b822b09`
- LatestStatistics: `0260666a69b6edca6150ad7eb99ee565e1717a351760795b0e86540fe978cb28`

These hashes identify this scoped measurement; later integrated source may
include independent accessibility changes. Historical earlier snapshot evidence
remains in [M0_3_STATUS_SNAPSHOT.md](M0_3_STATUS_SNAPSHOT.md).
