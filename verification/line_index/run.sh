#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../.."
OUT="$PWD/verification/build/line_index"
SRC="apps/MarkdownDesk/src/com/example/markdowndesk"
mkdir -p "$OUT/classes"
python3 verification/line_index/extract.py "$SRC/KineticEditText.java" "$OUT/LineIndexHarness.java" LineIndexHarness
HELPER=()
if [[ -f "$SRC/LogicalLineIndex.java" ]]; then HELPER+=("$SRC/LogicalLineIndex.java"); fi
javac --release 11 -d "$OUT/classes" "${HELPER[@]}" "$OUT/LineIndexHarness.java" verification/line_index/LineIndexCheck.java verification/line_index/LineIndexMemoryCheck.java
java -cp "$OUT/classes" com.example.markdowndesk.LineIndexCheck
java -Xmx128m -cp "$OUT/classes" com.example.markdowndesk.LineIndexMemoryCheck
if [[ "${1:-}" == "bench" ]]; then
  git show 0918e76f13804797b68158a417c7a53970144ab9:apps/MarkdownDesk/src/com/example/markdowndesk/KineticEditText.java > "$OUT/KineticBaseline.java"
  python3 verification/line_index/extract.py "$OUT/KineticBaseline.java" "$OUT/BaselineLineHarness.java" BaselineLineHarness
  javac --release 11 -cp "$OUT/classes" -d "$OUT/classes" "$OUT/BaselineLineHarness.java" verification/line_index/LineIndexBenchmark.java
  java -Xms128m -Xmx1g -cp "$OUT/classes" com.example.markdowndesk.LineIndexBenchmark | tee "$OUT/benchmark.csv"
fi
