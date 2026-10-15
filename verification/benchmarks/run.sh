#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../.."
OUT="$PWD/verification/build/benchmarks"
JAVAC="${JAVAC:-javac}"
JAVA="${JAVA:-java}"
mkdir -p "$OUT/classes" "$OUT/data"
"$JAVAC" --release 11 -d "$OUT/classes" \
  verification/stubs/android/graphics/drawable/GradientDrawable.java \
  apps/MarkdownDesk/src/com/example/markdowndesk/UiTheme.java \
  apps/MarkdownDesk/src/com/example/markdowndesk/ReaderTypography.java apps/MarkdownDesk/src/com/example/markdowndesk/MarkdownRenderer.java \
  apps/MarkdownDesk/src/com/example/markdowndesk/EditorTools.java \
  apps/MarkdownDesk/src/com/example/markdowndesk/SearchEngine.java \
  verification/benchmarks/HostBaseline.java
"$JAVA" -Xms128m -Xmx1g -cp "$OUT/classes" \
  com.example.markdowndesk.HostBaseline "$OUT/data" | tee "$OUT/results.csv"
