#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
JAVA_ONLY=false
if [[ "${1:-}" == "--java-only" ]]; then JAVA_ONLY=true; shift; fi
if (( $# > 1 )); then echo 'Usage: run.sh [--java-only] [output-directory]' >&2; exit 2; fi
OUT="${1:-$ROOT/verification/build/print-layout}"
mkdir -p "$OUT/classes" "$OUT/html"
javac --release 11 -d "$OUT/classes" "$ROOT/verification/stubs/android/graphics/drawable/GradientDrawable.java" "$ROOT/apps/MarkdownDesk/src/com/example/markdowndesk/UiTheme.java" "$ROOT/apps/MarkdownDesk/src/com/example/markdowndesk/ReaderTypography.java" "$ROOT/apps/MarkdownDesk/src/com/example/markdowndesk/MarkdownRenderer.java" "$ROOT/apps/MarkdownDesk/src/com/example/markdowndesk/DocumentPrintLayout.java" "$ROOT/verification/print_layout/PrintLayoutCheck.java"
java -Xmx128m -cp "$OUT/classes" com.example.markdowndesk.PrintLayoutCheck "$OUT/html"
if "$JAVA_ONLY"; then
 echo 'PASS Java framing/validation only; PDF checks NOT RUN (--java-only).'
else
 python3 "$ROOT/verification/print_layout/pdf_check.py" "$OUT"
fi
