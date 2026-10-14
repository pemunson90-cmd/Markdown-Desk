# Incremental logical line index

Run `PATH="$JAVA_HOME/bin:$PATH" bash verification/line_index/run.sh`.
Add `bench` to run the matched host benchmark. JDK17 is reused; generated
classes/data remain in ignored `verification/build/line_index/`.

The harness extracts the production `KineticEditText.onTextChanged()` and
`logicalLineForOffset()` bodies and line-index field unchanged. Its superclass
supplies only the Android callback surface, including an early constructor
callback. The real production `LogicalLineIndex` is compiled alongside it.
The oracle independently counts LF up to every offset after each edit.

RED: the original callback read 440,002 characters for a one-character insertion
into a 220,000-character document. GREEN: 46,564 assertions, including 3,000
seeded insert/delete/replace edits, full document changes, LF boundaries,
CRLF and UTF16 offsets, large LF pastes/deletions, early constructor callback,
invalid bounds/known-length fallback, and no text snapshots. A valid one-character
insertion reads one character; deletion reads none of the surviving text.

Ordinary edits binary-search old LF offsets, scan inserted characters, splice
new LF offsets and shift remaining offsets. Complexity is O(inserted characters
+ following logical lines), with O(lines) retained index capacity. LF-containing
partial insertions also need temporary O(inserted LF) offsets. Full-document
replacement and inconsistent callback metadata use a single full scan. This is
not constant-time editing and does not optimize Android text layout or syntax.

`benchmark-2026-10-14.csv` records five samples per fixture/position/path. Each
sample is a batch of 20 insertion/deletion pairs, reported as average milliseconds
per edit. Three batches warm up each operation. String construction, initial
index build and explicit GC occur outside timing. The baseline callback is
extracted from `0918e76f13804797b68158a417c7a53970144ab9` (identical source
blob to `v2.2.5-baseline` used for the recorded run); the incremental callback invokes the actual
changed helper. Fixtures are exactly the existing HostBaseline 10k/100k/500k-word
texts (57,020/570,200/2,851,000 chars), including the same marker/blank lines.
Their hashes match `docs/benchmarks/M0_3_HOST_BASELINE.md` exactly. SHA256 values:

- 10k: `fd42ea658785c17a5e57df0553fee2466a4ca649b3869d2cdae48a536364b423`
- 100k: `6e7933a4d16193c99eae416d9bd0e4a0137658898c50e8291d19122c0cccb5d9`
- 500k: `1f4a9fe7d9838fb079d4dec4e6866f2502ce6cc7600e9baaade22bfa8cb00edd`

At the beginning of these documents, median milliseconds/edit were respectively
0.046305/0.788096/4.009088 baseline and 0.003653/0.000547/0.014560 incremental.
All sample values and middle/end results are retained in the CSV. JIT state and
host noise make the small timing series nonmonotonic; these are descriptive host
results, not a latency budget. Maximum post-operation used Java heap is reported,
including retained fixture strings and baseline garbage awaiting collection;
it is not peak memory, RSS or the allocation cost of one callback.

Environment: Debian13, Linux6.18.44, Intel Xeon Platinum8573C, container quota
4 CPUs/16GiB, Temurin17.0.16+8, `-Xms128m -Xmx1g`.
Measured source SHA256:

- `KineticEditText.java`: `be74bebcc18fb520c02c27ca02336a96de8259ebcb8c251f40343910a1c60b2c`
- `LogicalLineIndex.java`: `848138116780a0651dbb3c4c4c9eedf79d7f0e7c56e434268b6ca42b70254390`

No Android text layout, span interactions, IME composition, undo, scrolling,
frame timing, Fold or physical device acceptance is established by these host
fixtures; those checks remain NOT RUN.
