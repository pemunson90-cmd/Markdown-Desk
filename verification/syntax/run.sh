#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../.."
OUT=verification/build/syntax
mkdir -p "$OUT/classes"
git show v2.2.5-baseline:apps/MarkdownDesk/src/com/example/markdowndesk/MarkdownSyntaxHighlighter.java > "$OUT/BaselineSource.java"
python3 - "$OUT" <<'PY'
import pathlib, sys
root = pathlib.Path(sys.argv[1])
root.joinpath('BaselineSyntaxHighlighter.java').write_text(root.joinpath('BaselineSource.java').read_text().replace('MarkdownSyntaxHighlighter', 'BaselineSyntaxHighlighter'))
PY
python3 verification/syntax/extract_schedule.py apps/MarkdownDesk/src/com/example/markdowndesk/MainActivity.java "$OUT/SchedulingHarness.java"
mapfile -t STUBS < <(find verification/syntax/stubs -type f -name '*.java' | sort)
((${#STUBS[@]} > 0)) || { echo 'Missing syntax host stubs' >&2; exit 1; }
javac --release 11 -d "$OUT/classes" "${STUBS[@]}" "$OUT/BaselineSyntaxHighlighter.java" "$OUT/SchedulingHarness.java" apps/MarkdownDesk/src/com/example/markdowndesk/MarkdownSyntaxHighlighter.java verification/syntax/SyntaxCheck.java verification/syntax/SchedulingCheck.java verification/syntax/SyntaxBenchmark.java
java -Xmx128m -cp "$OUT/classes" com.example.markdowndesk.SyntaxCheck "${1:-}"
java -Xmx128m -cp "$OUT/classes" com.example.markdowndesk.SchedulingCheck
if [[ "${1:-}" == "bench" ]]; then
 java -Xmx128m -cp "$OUT/classes" com.example.markdowndesk.SyntaxBenchmark | tee "$OUT/benchmark.csv"
fi
