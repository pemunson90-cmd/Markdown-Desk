#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
MODE="${1:-codec}"
case "$MODE" in
 codec|policy|admission|integration) ;;
 all)
  for group in codec policy admission integration; do bash "$0" "$group"; done
  python3 "$ROOT/verification/history_capacity/run_restart.py"
  exit ;;
 *) echo 'Usage: run.sh [codec|policy|admission|integration|all]' >&2; exit 2 ;;
esac
if [[ "$MODE" != codec ]]; then
 SRC="$ROOT/apps/MarkdownDesk/src/com/example/markdowndesk"
 OUT="$ROOT/verification/build/history-capacity"
 mkdir -p "$OUT/classes" "$OUT/tmp"
 javac --release 11 -sourcepath "$ROOT/apps/MarkdownDesk/src" -d "$OUT/classes" "$ROOT/verification/history_capacity/HistoryCapacityCheck.java"
 java -Xmx128m -Djava.io.tmpdir="$OUT/tmp" -cp "$OUT/classes" com.example.markdowndesk.HistoryCapacityCheck "$MODE"
 exit
fi
SRC="$ROOT/apps/MarkdownDesk/src/com/example/markdowndesk/HistoryControlStore.java"
if [[ ! -f "$SRC" ]]; then
  echo 'FAIL: HistoryControlStore codec implementation missing (Task1 RED)' >&2
  exit 1
fi
OUT="$ROOT/verification/build/history-capacity-codec"
mkdir -p "$OUT/classes" "$OUT/tmp"
javac --release 11 -sourcepath "$ROOT/apps/MarkdownDesk/src" -d "$OUT/classes" "$SRC" "$ROOT/verification/history_capacity/HistoryControlCodecCheck.java"
java -Xmx64m -Djava.io.tmpdir="$OUT/tmp" -cp "$OUT/classes" com.example.markdowndesk.HistoryControlCodecCheck
