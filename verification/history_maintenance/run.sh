#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
OUT="$ROOT/verification/build/history-maintenance"
mkdir -p "$OUT/classes" "$OUT/tmp"
FIXTURES="$(mktemp -d "$OUT/tmp/run-XXXXXXXX")"
trap 'rm -rf -- "$FIXTURES"' EXIT
SRC="$ROOT/apps/MarkdownDesk/src/com/example/markdowndesk"
javac --release 11 -sourcepath "$ROOT/apps/MarkdownDesk/src" -d "$OUT/classes" "$SRC/HistoryPruneIntent.java" "$ROOT/verification/history_maintenance/IntentCodecCheck.java"
java -Xmx128m -Djava.io.tmpdir="$FIXTURES" -cp "$OUT/classes" com.example.markdowndesk.IntentCodecCheck
java -Xmx128m -Djava.io.tmpdir="$FIXTURES" -cp "$OUT/classes" com.example.markdowndesk.IntentCodecCheck memory
