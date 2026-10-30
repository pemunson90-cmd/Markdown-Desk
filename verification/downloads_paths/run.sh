#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
OUT="$(mktemp -d)"
trap 'rm -rf -- "$OUT"' EXIT
javac --release 11 -d "$OUT" \
  "$ROOT/apps/MarkdownDesk/src/com/example/markdowndesk/DownloadsPaths.java" \
  "$ROOT/verification/downloads_paths/DownloadsPathsCheck.java"
java -Xmx64m -cp "$OUT" com.example.markdowndesk.DownloadsPathsCheck
