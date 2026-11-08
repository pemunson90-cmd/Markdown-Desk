#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../.."
OUT="$(mktemp -d)"
trap 'rm -rf -- "$OUT"' EXIT
"${JAVAC:-javac}" --release 11 -encoding UTF-8 -d "$OUT" \
  apps/MarkdownDesk/src/com/example/markdowndesk/workspace/archive/ArchiveScrollScript.java \
  apps/MarkdownDesk/src/com/example/markdowndesk/workspace/archive/ArchiveSearchHitScript.java \
  verification/archive_reader/ArchiveSearchHitDump.java
"${JAVA:-java}" -cp "$OUT" com.example.markdowndesk.workspace.archive.ArchiveSearchHitDump > "$OUT/search-hit.js"
node verification/archive_reader/archive_search_hit_dom.cjs "$OUT/search-hit.js"
