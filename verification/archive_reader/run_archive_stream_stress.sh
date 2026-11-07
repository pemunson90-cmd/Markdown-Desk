#!/usr/bin/env bash
# Manual pre-release host stress only: exercise the real parser without creating a 700MB archive.
set -euo pipefail
cd -- "$(dirname -- "$0")/../.."
OUT="$(mktemp -d)"
trap 'rm -rf -- "$OUT"' EXIT
"${JAVAC:-javac}" --release 11 -encoding UTF-8 -d "$OUT" \
  apps/MarkdownDesk/src/com/example/markdowndesk/archive/ArchiveStreamParser.java \
  verification/archive_reader/ArchiveStreamStressCheck.java
"${JAVA:-java}" -Xmx96m -cp "$OUT" com.example.markdowndesk.archive.ArchiveStreamStressCheck
