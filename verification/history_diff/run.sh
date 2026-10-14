#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
OUT="$ROOT/verification/build/history-diff"
mkdir -p "$OUT/classes" "$OUT/tmp"
javac --release 11 -d "$OUT/classes" \
 "$ROOT/apps/MarkdownDesk/src/com/example/markdowndesk/ProtectionStore.java" \
 "$ROOT/apps/MarkdownDesk/src/com/example/markdowndesk/HistoryStorage.java" \
 "$ROOT/apps/MarkdownDesk/src/com/example/markdowndesk/HistoryDiff.java" \
 "$ROOT/verification/history_diff/HistoryDiffCheck.java"
java -Djava.io.tmpdir="$OUT/tmp" -cp "$OUT/classes" com.example.markdowndesk.HistoryDiffCheck
java -Djava.io.tmpdir="$OUT/tmp" -Xmx128m -cp "$OUT/classes" com.example.markdowndesk.HistoryDiffCheck memory
