#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../.."
OUT="$(mktemp -d)"
trap 'rm -rf -- "$OUT"' EXIT
"${JAVAC:-javac}" --release 11 -encoding UTF-8 -d "$OUT" \
  apps/MarkdownDesk/src/com/example/markdowndesk/archive/ArchiveStreamParser.java \
  verification/archive_reader/ArchiveUtf8SourceCheck.java
"${JAVA:-java}" -cp "$OUT" com.example.markdowndesk.archive.ArchiveUtf8SourceCheck
