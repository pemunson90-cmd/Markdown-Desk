# M0.3 host baseline — 2026-10-14

This is a **host algorithm baseline**, from checkout `d1288a8` on branch
`codex/m0-safety-regressions`. It is not an Android device or UI latency result.
Run with `PATH="$JAVA_HOME/bin:$PATH" bash verification/benchmarks/run.sh`. The script compiles actual
`EditorTools`, `SearchEngine`, `MarkdownRenderer`, and `UiTheme` sources using
JDK 17 `javac --release 11`, with the existing host-only drawable stub. It
uses `-Xms128m -Xmx1g`; generated fixtures and working output stay under
ignored `verification/build/benchmarks/`. The [sample CSV](M0_3_host_samples.csv)
records all results; the tool always generates the same fixture bytes.

Host: Debian 13.6, Linux 6.18.44, AMD EPYC 9V74, Temurin 17.0.16+8. The
container allows 4 CPU cores (`cpu.max=400000 100000`) and 16 GiB memory
(`memory.max=17179869184`). Timings are wall clock from `System.nanoTime`.
Each operation had one warmup and three measured calls; the median below is
the middle of those three, **not p95**. The CSV has each sample. Maximum heap
column is the highest post-operation used Java heap after a `System.gc()`
before each sample. It is **not peak allocation or process RSS**. File timings
include host page-cache effects and do not represent SAF providers.

Fixture pattern: ten lowercase ASCII words per line, one `alpha` replaced by
`needle` every 100 lines, plus a blank line after each 100-line group. Search
uses literal `needle`, case-insensitive whole-word mode, with 10/100/500
matches. All three word fixtures are below the app's 8,388,608-byte limit.
The boundary fixture repeats the ten-word line to **exactly 8,388,608 bytes**;
the last partial line is intentional. It exercises host `Files.readAllBytes`
only, not `DocumentIO`, SAF, decoding policy, or device open behavior.

| Fixture | Bytes | Words | Lines | SHA-256 |
| --- | ---: | ---: | ---: | --- |
| 10k | 57,020 | 10,000 | 1,011 | `fd42ea658785c17a5e57df0553fee2466a4ca649b3869d2cdae48a536364b423` |
| 100k | 570,200 | 100,000 | 10,101 | `6e7933a4d16193c99eae416d9bd0e4a0137658898c50e8291d19122c0cccb5d9` |
| 500k | 2,851,000 | 500,000 | 50,501 | `1f4a9fe7d9838fb079d4dec4e6866f2502ce6cc7600e9baaade22bfa8cb00edd` |
| exact 8 MiB | 8,388,608 | 1,471,686 | 147,169 | `da33bc62526c4dd047557206e9ec72fe39db6f2a5f9775753a6e89da66e79911` |

Median wall time in milliseconds (three samples each):

| Operation | 10k | 100k | 500k | exact 8 MiB |
| --- | ---: | ---: | ---: | ---: |
| `EditorTools.wordCount` | 2.129 | 4.962 | 21.510 | — |
| `EditorTools.lineCount` | 0.487 | 0.218 | 1.094 | — |
| `EditorTools.sha256` | 1.462 | 0.786 | 2.081 | — |
| `SearchEngine.findAll` | 0.866 | 3.503 | 16.523 | — |
| `MarkdownRenderer.render` (Paper, 18sp) | 15.943 | 80.038 | 363.513 | — |
| host `Files.readAllBytes` | 0.145 | 0.274 | 1.089 | 3.252 |
| host `Files.write` | 0.293 | 0.831 | 1.936 | — |

The 500k renderer samples were 363.513, 511.288, and 363.357 ms; the maximum
sampled post-operation used heap was 159.3 MiB. Three samples are too few for
a stable tail percentile. Smaller operation timings also reflect JIT and
cache effects, so a few rows do not increase monotonically with fixture size.

Source inspection gives a bounded M2 candidate: `MainActivity.updateStatus()`
calls `editor.getText().toString()` twice, then scans the entire text through
`wordCount` and `lineCount`; `onTextChanged` invokes it during edits. Separate
the immediate dirty/save UI update from deferred statistics, coalesce pending
statistics work, and refresh statistics on tab/document switches and before
explicit document info. Preserve current status semantics and add behavioral
tests around rapid edits and switches. For preview, the current 180 ms debounce
still renders the full source string, and the render token only discards stale
HTML afterward. Measure on Android before choosing an incremental renderer or
changing the debounce. No production optimization is included here.

**NOT RUN:** cold app launch, document open through SAF, typing/input latency,
scroll, cursor, tab switch, WebView layout/paint, saved Android document writes,
folded/unfolded behavior, phone/Fold memory and GC, and device thermal effects.
The host timings cannot establish responsiveness budgets or largest supported
device document size. Those require instrumented device measurements on matched
fixtures, including the 8 MiB policy boundary.
