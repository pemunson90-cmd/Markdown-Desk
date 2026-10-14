#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
OUT="$ROOT/verification/build/history-pins"
mkdir -p "$OUT/classes" "$OUT/tmp"
FIXTURES="$(mktemp -d "$OUT/tmp/run-XXXXXXXX")"
trap 'rm -rf -- "$FIXTURES"' EXIT
javac --release 11 -sourcepath "$ROOT/apps/MarkdownDesk/src" -d "$OUT/classes" "$ROOT/verification/history_pins/HistoryPinsCheck.java" "$ROOT/verification/history_pins/PinLifecycleCheck.java" "$ROOT/verification/history_pins/PinRestartProbe.java" "$ROOT/verification/history_pins/PinManifestCheck.java" "$ROOT/verification/history_pins/PinMaintenanceProbe.java" "$ROOT/verification/history_maintenance/MaintenanceRecoveryCheck.java" "$ROOT/verification/history_maintenance/IntentCodecCheck.java"
java -Xmx128m -Djava.io.tmpdir="$FIXTURES" -cp "$OUT/classes" com.example.markdowndesk.HistoryPinsCheck
java -Xmx128m -Djava.io.tmpdir="$FIXTURES" -cp "$OUT/classes" com.example.markdowndesk.PinLifecycleCheck
java -Xmx16m -Djava.io.tmpdir="$FIXTURES" -cp "$OUT/classes" com.example.markdowndesk.PinManifestCheck
python3 "$ROOT/verification/history_pins/run_restart.py"
