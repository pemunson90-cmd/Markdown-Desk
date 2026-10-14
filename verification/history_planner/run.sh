#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
OUT="$ROOT/verification/build/history-planner"
mkdir -p "$OUT/classes" "$OUT/tmp"
FIXTURES="$(mktemp -d "$OUT/tmp/run-XXXXXXXX")"
trap 'rm -rf -- "$FIXTURES"' EXIT
SRC="$ROOT/apps/MarkdownDesk/src/com/example/markdowndesk"
javac --release 11 -sourcepath "$ROOT/apps/MarkdownDesk/src" -d "$OUT/classes" "$SRC/ProtectionStore.java" "$SRC/HistoryStorage.java" "$SRC/HistoryPolicy.java" "$SRC/HistoryCatalog.java" "$SRC/HistoryMetadataIO.java" "$SRC/HistoryIdentity.java" "$SRC/HistoryIndex.java" "$SRC/HistoryGraph.java" "$SRC/HistoryPruner.java" "$ROOT/verification/history_planner/HistoryPrunerCheck.java" "$ROOT/verification/history_planner/HistoryPressureCheck.java"
java -Xmx128m -Djava.io.tmpdir="$FIXTURES" -cp "$OUT/classes" com.example.markdowndesk.HistoryPrunerCheck
java -Xmx128m -Djava.io.tmpdir="$FIXTURES" -cp "$OUT/classes" com.example.markdowndesk.HistoryPrunerCheck memory
java -Xmx128m -Djava.io.tmpdir="$FIXTURES" -cp "$OUT/classes" com.example.markdowndesk.HistoryPressureCheck
java -Xmx128m -Djava.io.tmpdir="$FIXTURES" -cp "$OUT/classes" com.example.markdowndesk.HistoryPressureCheck memory
