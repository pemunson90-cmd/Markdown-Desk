#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
if [[ "${1:-codec}" != codec || $# -gt 1 ]]; then
  echo 'Only the read-only codec group is implemented.' >&2
  exit 2
fi
SRC="$ROOT/apps/MarkdownDesk/src/com/example/markdowndesk/HistoryControlStore.java"
if [[ ! -f "$SRC" ]]; then
  echo 'FAIL: HistoryControlStore codec implementation missing (Task1 RED)' >&2
  exit 1
fi
OUT="$ROOT/verification/build/history-capacity-codec"
mkdir -p "$OUT/classes" "$OUT/tmp"
javac --release 11 -d "$OUT/classes" "$SRC" "$ROOT/verification/history_capacity/HistoryControlCodecCheck.java"
java -Xmx64m -Djava.io.tmpdir="$OUT/tmp" -cp "$OUT/classes" com.example.markdowndesk.HistoryControlCodecCheck
