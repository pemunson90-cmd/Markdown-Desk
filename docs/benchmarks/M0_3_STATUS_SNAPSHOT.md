# M0.3 `updateStatus()` snapshot validation

`verification/status_behavior/run.sh` parses `MainActivity.java` with the JDK
compiler AST, extracts the exact `updateStatus()` method, and compiles it in a
small host class. The test uses actual `EditorTools` and `UiTheme` sources plus
the existing drawable stub. Fake editor/text, views, buttons and resources
provide only the Android surface needed by this method. The resource fake uses
the singular/plural English forms in `res/values/plurals.xml`.

The behavior fixtures check empty text, dirty/saving/read-only button states,
Write/Read/Split status, word goal and cap, Unicode with CRLF, title, text,
and cursor preservation. A counted `CharSequence.toString()` establishes the
copy count. Before the source change, `bash verification/status_behavior/run.sh
test` exited 1 at the intended assertion: `one editor snapshot per status
update: expected 1, got 2`. The prior observable behavior checks passed.

The same harness has a `bench` mode using deterministic 10k/100k/500k word
fixtures from the M0.3 host baseline. On Debian 13.6 / Linux 6.18.44, AMD EPYC
9V74, container limit 4 CPU cores / 16 GiB, Temurin JDK 17.0.16+8 and
`-Xms128m -Xmx1g`, it ran one warmup then three wall-clock samples per size.
Each sample calls the actual extracted method. GC precedes each timed sample.
The heap number is maximum **post-operation used Java heap**, not peak memory
or RSS. With only three samples, median and individual samples are reported;
p95 would be misleading.

| Source state | Words | Text chars | Snapshots/update | Samples (ms) | Median (ms) | Max used heap (MiB) |
| --- | ---: | ---: | ---: | --- | ---: | ---: |
| Before | 10,000 | 57,020 | 2 | 5.129, 20.649, 1.821 | 5.129 | 1.7 |
| Before | 100,000 | 570,200 | 2 | 6.498, 5.336, 5.321 | 5.336 | 8.7 |
| Before | 500,000 | 2,851,000 | 2 | 27.929, 26.951, 26.763 | 26.951 | 22.7 |
| After | 10,000 | 57,020 | 1 | 4.718, 4.741, 3.428 | 4.718 | 1.6 |
| After | 100,000 | 570,200 | 1 | 9.076, 5.524, 6.191 | 6.191 | 5.7 |
| After | 500,000 | 2,851,000 | 1 | 26.727, 26.706, 27.333 | 26.727 | 13.7 |

The before method was read from `MainActivity.java` SHA-256
`216ec964fae0ac5fd121a7b5835639615323cc8dc654839c9ad70dd06f401025`.
The after method was read from file SHA-256
`7b891ddf60afe85f5797596462dd2400f06061d8d71465ed334c73a76334e4ee`.
The extracted `updateStatus()` method text hashes are
`c75da1329a3436d3ef440e3c1f78cbefe050b72a11dab74c63aa8c7a941e8de6`
(before) and
`4f4db9c8f74f970d9ed5a27bb8ac13efcc34e3e1910c2a2d3268824f995cf0f2`
(after). The before body hash was reconstructed from the after extracted body
by reversing only the three changed snapshot lines shown in the source diff;
the before full-file hash was recorded before the edit. After the change,
`bash verification/status_behavior/run.sh test` exited 0 with 24 assertions.

The deterministic finding is **two editor text snapshots reduced to one**,
with the checked status and button behavior preserved. The short timing series
is noisy (including a higher 100k median after the edit) and does not establish
a reliable speedup. The host `CountedText.toString()` copies its underlying
characters to expose the snapshot cost; it is not Android's `Editable`.
This test measures method wall time on a host with simulated Android-facing
objects. It does **not** measure typing latency, UI thread responsiveness,
device allocation/GC, WebView, SAF, phone or Fold behavior; all are **NOT RUN**.

Later M0 label repair adds dynamic Save action descriptions to updateStatus.
The samples above measure the exact earlier method hashes recorded here; they
are historical evidence, not measurements of every subsequent source revision.
