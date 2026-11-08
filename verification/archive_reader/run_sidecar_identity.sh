#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../.."
OUT="$(mktemp -d)"
trap 'rm -rf -- "$OUT"' EXIT
"${JAVAC:-javac}" --release 11 -d "$OUT" \
  apps/MarkdownDesk/src/com/example/markdowndesk/archive/ArchiveJournal.java \
  apps/MarkdownDesk/src/com/example/markdowndesk/archive/ArchiveMarkdownIndexes.java \
  verification/archive_reader/SidecarIdentityCheck.java
"${JAVA:-java}" -Xmx128m -cp "$OUT" com.example.markdowndesk.archive.SidecarIdentityCheck
