#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
OUT="$ROOT/verification/build/history-operations"
mkdir -p "$OUT/classes" "$OUT/tmp"
FIXTURES="$(mktemp -d "$OUT/tmp/run-XXXXXXXX")"
trap 'rm -rf -- "$FIXTURES"' EXIT
javac --release 11 -sourcepath "$ROOT/apps/MarkdownDesk/src" -d "$OUT/classes" "$ROOT/verification/history_pins/HistoryPinsCheck.java" "$ROOT"/verification/history_operations/*.java
for check in "$ROOT"/verification/history_operations/*Check.java; do
  case "${1:-all}:$(basename "$check" .java)" in
    all:*|coordinator:OperationQuiescenceCheck|terminal:OperationTerminalCheck|prefix:ClosePrefixCheck) ;;
    *) continue ;;
  esac
  java -Xmx128m -Djava.io.tmpdir="$FIXTURES" -cp "$OUT/classes" "com.example.markdowndesk.$(basename "$check" .java)"
done
if [[ "${1:-all}" == all || "${1:-all}" == restart ]]; then
  python3 "$ROOT/verification/history_operations/run_restart.py"
fi
