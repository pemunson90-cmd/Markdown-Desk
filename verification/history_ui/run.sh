#!/usr/bin/env bash
set -euo pipefail
ROOT=$(cd "$(dirname "$0")/../.." && pwd)
OUT=$(mktemp -d)
trap 'rm -rf "$OUT"' EXIT
if [[ ! -f "$ROOT/apps/MarkdownDesk/src/com/example/markdowndesk/HistoryController.java" ]]; then
  echo 'HistoryController implementation missing: new-feature acceptance cannot run yet' >&2
  exit 1
fi
javac --release 11 -sourcepath "$ROOT/apps/MarkdownDesk/src" -d "$OUT" "$ROOT"/verification/history_ui/*Check.java
java -Xmx128m -cp "$OUT" com.example.markdowndesk.CaptureRaceCheck
java -Xmx128m -cp "$OUT" com.example.markdowndesk.HistoryControllerCheck
