# M0 renderer input probe — 2026-10-14

`bash verification/malformed_renderer/run.sh` compiled the actual
`MarkdownRenderer` and `UiTheme` source with JDK 17 `javac --release 11` and
the existing host drawable stub. Each deterministic fixture ran in a separate
Java process with `-Xms128m -Xmx1g` and a 10-second timeout (five cases,
50-second maximum process budget). All source inputs were below the app's
8,388,608-byte document limit. The run exited **1** because two cases threw.
Neither case timed out. These are single-run failure probes, not timing
benchmarks.

Source hashes at compilation: `MarkdownRenderer.java`
`8606c18cbdc8a23e7be6029954eb5a9f768d6f54f3e5a10dc4d5af4b2df7d082`,
`UiTheme.java`
`573a3f1c6e5138244ba6ddd3997f7e3cb63bbfddf18086de6c56ec8ade2696a6`.

| Case | UTF-8 bytes | Source SHA-256 | Result | Render wall time |
| --- | ---: | --- | --- | ---: |
| `table_2000` | 16,006 | `c10ab07c5e9e6b7a21b31adb9ec37eb7a7d18f54614f1c76d71283882e5cb0af` | `StackOverflowError`, exit 1 | 6.755 ms |
| `table_5000` | 40,006 | `cf722ba689b9f34cd7f0823d773b2e256f7a134313edcd5bc35c71b351ee2734` | `StackOverflowError`, exit 1 | 8.860 ms |
| `spaces_dangling_pipe` | 20,031 | `28fa3635bd98cb5c868945f62be480fba94e913bf248afc2732b3cc4a0a391c2` | OK | 439.461 ms |
| `fragments_1000` | 10,001 | `8aeaea3542a6a3fb8aabbafc851f6dd96204d5834cd48fcb28ceb0bd8bb646f9` | OK | 166.638 ms |
| `fragments_3000` | 30,001 | `f5d5e1ff6a0447a9bbf64725d1499c2b31fbd607e5c653ee7bae9d0154b30284` | OK | 319.787 ms |

The wide table source is exactly `"|a".repeat(columns) + "|\n" +
"|---".repeat(columns) + "|\n" + "|b".repeat(columns) + "|\n"`.
The divider is a valid repeated column separator under the renderer's own
`TABLE_DIVIDER` pattern, which requires at least two columns. At 2,000 and
5,000 columns, the error stack repeatedly enters `java.util.regex.Pattern`
matching frames. This indicates recursive regex matching on the wide divider;
the exact Java stack can vary with VM stack size. A successful wide-table
render must not throw a Java `Error` for an under-limit source. The next
bounded fix candidate is to recognize separator cells iteratively, with
explicit grammar tests, instead of relying on the repeated regex group.

The `spaces_dangling_pipe` case has 10,000 spaces on each side of
`|---|---|`, followed by a trailing pipe. Fragment cases repeat
the literal sequence backslash-asterisk, space, backtick-code-backtick,
space 1,000 or 3,000 times on one line, exercising escaped and
code-fragment restoration. They completed within timeout; this does not
establish safety for all malformed input. The script preserves full per-case
logs under ignored `verification/build/malformed-renderer/` and prints case
hashes/results. Android provider, WebView, UI, and device behavior were
**NOT RUN**.

## Subsequent repair verification

The iterative separator-cell parser replaces recursive divider regex matching.
All five identical fixtures now exit0 without timeout, including both wide
tables. Independent tests cover actual2k/5kcolumn rendering, manual grammar
and14,000seeded short comparisons against the former regex. These are host
crash/grammar results, not Android timing guarantees. Full post-repair probe
output is retained in `docs/verification/2026-10-14/m0/malformed-after.log`.
