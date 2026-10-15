#!/usr/bin/env bash
set -euo pipefail
ROOT=$(cd "$(dirname "$0")/../.." && pwd)
OUT=$(mktemp -d)
trap 'rm -rf "$OUT"' EXIT
javac --release 11 -sourcepath "$ROOT/apps/MarkdownDesk/src" -d "$OUT" \
  "$ROOT/apps/MarkdownDesk/src/com/example/markdowndesk/HistoryArchiveManifest.java" \
  "$ROOT"/verification/history_archive_manifest/*Check.java
java -Xmx128m -cp "$OUT" com.example.markdowndesk.HistoryManifestCheck
java -Xmx128m -cp "$OUT" com.example.markdowndesk.ManifestMemoryCheck
