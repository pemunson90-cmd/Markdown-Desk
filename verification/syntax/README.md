# Affected-range syntax checks

Run `PATH="$JAVA_HOME/bin:$PATH" bash verification/syntax/run.sh`; add `bench`
for the matched host measurements in `verification/build/syntax/benchmark.csv`.
The recorded five-sample series is `benchmark-2026-10-14.csv` beside this file.

The runner executes the production highlighter against a functional host
`Editable`/span adapter and compares exact owned span ranges, types, flags, colors,
styles and overlap precedence to the unchanged `v2.2.5-baseline` highlighter from
Git. It exercises 4,800 deterministic edits, coalesced dirty-range mapping,
CR/LF/Unicode boundaries, matched and unmatched comments/fences, marker creation
through deletion, theme/Editable changes, invalid callbacks and both directions
of the existing 1,500,000-character highlighting limit. User/composition spans
and text survive applying highlight updates; unchanged line spans retain identity.

The extracted production Activity watcher and scheduler also run against host UI
boundaries: ordinary deltas coalesce for 90ms, suppressed tab loads cancel old
requests and explicit theme/tab requests force a full reset. These host boundaries
do not establish Android span relocation, IME, selection or device acceptance.

One state object keeps two dirty offsets, length, Editable identity, theme and a
raw marker flag. Existing or newly assembled `<!--`, triple backtick or triple
tilde markers conservatively require the original full pass, including unmatched
openers. Marker-free edits reuse the exact regexes and original pass order on
complete LF lines. A 65,536-character dirty/range limit forces full fallback;
there is no growing event queue or retained text snapshot. Fenced/comment-bearing
documents are not claimed optimized. The original size cutoff remains intact.

Matched 10k/100k/500k-word synthetic fixture bytes and hashes agree with the
existing host and line-index benchmarks. Five measured samples follow two warmup
batches, with ten single-character insert/delete highlights per batch; text
mutation is outside the timer. The production delta tracking, matching and span
adapter updates are inside it. Middle-position median milliseconds were:

| Words | Baseline | Affected range | Highlighting enabled |
| --- | ---: | ---: | --- |
| 10k | 4.9621 | 0.0117 | Yes |
| 100k | 49.3451 | 0.0140 | Yes |
| 500k | 0.0008 | 0.0015 | No: 2,851,000 characters exceeds the unchanged cutoff |

These are host adapter measurements of marker-free text, not Android typing
latency or a guarantee for adversarial regex inputs. Full fallback retains the
existing regex behavior and cost.

Recorded with Temurin17.0.16+8 on the same Linux host as the line-index
measurements, using the runner's `-Xmx128m` setting. Measured production inputs:

- `MarkdownSyntaxHighlighter.java`: `99354c363b8cb845c625a454e855c853c361c74b8f9a291052587b88b3fac96b`
- `MainActivity.java`: `29293d0dcb7db445442c7cf8ba71f5c51d84d0644c6ffee34fc3cb83ff4c6a23`
