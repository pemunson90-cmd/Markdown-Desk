#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
OUT="$ROOT/verification/build/history-maintenance"
mkdir -p "$OUT/classes" "$OUT/tmp"
SRC="$ROOT/apps/MarkdownDesk/src/com/example/markdowndesk"
javac --release 11 -d "$OUT/classes" "$SRC/ProtectionStore.java" "$SRC"/History*.java "$ROOT/verification/history_quota/HistoryQuotaCheck.java" "$ROOT"/verification/history_maintenance/*.java
java -Xmx128m -Djava.io.tmpdir="$OUT/tmp" -cp "$OUT/classes" com.example.markdowndesk.HistoryMaintenanceCheck
python3 "$ROOT/verification/history_maintenance/run_restart.py" "$OUT"
