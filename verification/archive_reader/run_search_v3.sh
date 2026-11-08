#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../.."
OUT="$(mktemp -d)"
trap 'rm -rf -- "$OUT"' EXIT
APP=apps/MarkdownDesk/src/com/example/markdowndesk/workspace/archive
javac --release 11 -d "$OUT" "$APP/ArchiveSearchMatch.java" "$APP/ArchiveOverlap.java" \
 "$APP/ArchiveSearchSpec.java" verification/archive_reader/ArchiveSearchMatchCheck.java
java -cp "$OUT" com.example.markdowndesk.workspace.archive.ArchiveSearchMatchCheck
python3 verification/archive_reader/test_search_v3.py
