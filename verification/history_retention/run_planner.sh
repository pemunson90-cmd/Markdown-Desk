#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
OUT="$ROOT/verification/build/history-pruner"
mkdir -p "$OUT/classes" "$OUT/tmp"
SRC="$ROOT/apps/MarkdownDesk/src/com/example/markdowndesk"
PLANNER=()
if [[ -f "$SRC/HistoryPruner.java" ]]; then PLANNER+=("$SRC/HistoryPruner.java"); fi
javac --release 11 -d "$OUT/classes" "$SRC/ProtectionStore.java" "$SRC/HistoryStorage.java" "$SRC/HistoryPolicy.java" "$SRC/HistoryCatalog.java" "$SRC/HistoryMetadataIO.java" "$SRC/HistoryIdentity.java" "$SRC/HistoryIndex.java" "$SRC/HistoryGraph.java" "${PLANNER[@]}" "$ROOT/verification/history_retention/HistoryGraphCheck.java" "$ROOT/verification/history_retention/HistoryPrunerCheck.java"
java -Xmx128m -Djava.io.tmpdir="$OUT/tmp" -cp "$OUT/classes" com.example.markdowndesk.HistoryPrunerCheck "${@}"
if [[ $# -eq 0 ]]; then
  java -Xmx128m -Djava.io.tmpdir="$OUT/tmp" -cp "$OUT/classes" com.example.markdowndesk.HistoryPrunerCheck memory
fi
