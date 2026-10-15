#!/usr/bin/env bash
set -euo pipefail
ROOT=$(cd "$(dirname "$0")/../.." && pwd)
OUT=$(mktemp -d)
trap 'rm -rf "$OUT"' EXIT
SRC="$ROOT/apps/MarkdownDesk/src"
HERE="$ROOT/verification/history_ui_integration"
javac --release 11 -d "$OUT" "$HERE/ExtractHistoryMethods.java" "$ROOT/verification/ExtractStrictEncoding.java"
java -cp "$OUT" ExtractHistoryMethods "${1:-$SRC/com/example/markdowndesk/MainActivity.java}" "$HERE/MainActivity.template" "$OUT/MainActivity.java"
java -cp "$OUT" ExtractStrictEncoding "$SRC/com/example/markdowndesk/DocumentIO.java" "$OUT/MainActivity.java"
mapfile -t stubs < <(find "$HERE/stubs" -name '*.java' | sort)
javac --release 11 -sourcepath "$SRC" -d "$OUT" "${stubs[@]}" "$ROOT/verification/stubs/android/graphics/drawable/GradientDrawable.java" "$OUT/MainActivity.java" "$ROOT/verification/history_ui/HistoryControllerCheck.java" "$HERE/HistoryActionsCheck.java"
java -Xmx128m -cp "$OUT" com.example.markdowndesk.HistoryActionsCheck
