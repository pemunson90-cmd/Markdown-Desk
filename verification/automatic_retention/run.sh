#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
OUT="$ROOT/verification/build/automatic-retention"
mkdir -p "$OUT/classes" "$OUT/tmp"
FIXTURES="$(mktemp -d "$OUT/tmp/run-XXXXXXXX")"
trap 'rm -rf -- "$FIXTURES"' EXIT
javac --release 11 -sourcepath "$ROOT/apps/MarkdownDesk/src" -d "$OUT/classes" "$ROOT/verification/automatic_retention/AutomaticRetentionCheck.java" "$ROOT/verification/automatic_retention/AutomaticRestartProbe.java" "$ROOT/verification/history_executor/ExecutorCheck.java" "$ROOT/verification/history_maintenance/MaintenanceRecoveryCheck.java" "$ROOT/verification/history_maintenance/IntentCodecCheck.java"
java -Xmx128m -Djava.io.tmpdir="$FIXTURES" -cp "$OUT/classes" com.example.markdowndesk.AutomaticRetentionCheck

java -Xmx128m -Djava.io.tmpdir="$FIXTURES" -cp "$OUT/classes" com.example.markdowndesk.AutomaticRetentionCheck memory
if [[ "${1:-all}" != java ]]; then python3 "$ROOT/verification/automatic_retention/run_restart.py"; fi
