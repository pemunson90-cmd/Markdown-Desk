#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
OUT="$ROOT/verification/build/history-executor"
mkdir -p "$OUT/classes" "$OUT/tmp"
FIXTURES="$(mktemp -d "$OUT/tmp/run-XXXXXXXX")"
trap 'rm -rf -- "$FIXTURES"' EXIT
javac --release 11 -sourcepath "$ROOT/apps/MarkdownDesk/src" -d "$OUT/classes" "$ROOT/verification/history_executor/ExecutorCheck.java" "$ROOT/verification/history_executor/ExecutorRestartProbe.java" "$ROOT/verification/history_maintenance/MaintenanceRecoveryCheck.java" "$ROOT/verification/history_maintenance/IntentCodecCheck.java"
java -Xmx128m -Djava.io.tmpdir="$FIXTURES" -cp "$OUT/classes" com.example.markdowndesk.ExecutorCheck
java -Xmx128m -Djava.io.tmpdir="$FIXTURES" -cp "$OUT/classes" com.example.markdowndesk.ExecutorCheck memory
python3 "$ROOT/verification/history_executor/run_restart.py"
