#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
OUT="$ROOT/verification/build/history-retention"
mkdir -p "$OUT/classes" "$OUT/tmp"
SRC="$ROOT/apps/MarkdownDesk/src/com/example/markdowndesk"
javac --release 11 -sourcepath "$ROOT/apps/MarkdownDesk/src" -d "$OUT/classes" "$SRC/ProtectionStore.java" "$SRC/HistoryStorage.java" "$SRC/HistoryPolicy.java" "$SRC/HistoryCatalog.java" "$SRC/HistoryMetadataIO.java" "$SRC/HistoryIdentity.java" "$SRC/HistoryIndex.java" "$SRC/HistoryGraph.java" "$ROOT/verification/history_retention/HistoryGraphCheck.java"
java -Xmx128m -Djava.io.tmpdir="$OUT/tmp" -cp "$OUT/classes" com.example.markdowndesk.HistoryGraphCheck
java -Xmx128m -Djava.io.tmpdir="$OUT/tmp" -cp "$OUT/classes" com.example.markdowndesk.HistoryGraphCheck memory
