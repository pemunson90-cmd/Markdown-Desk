#!/usr/bin/env bash
# Manual pre-phone stress: authentic 46.7MB AJN2 fixture, 32MB heap, no APK or network.
set -euo pipefail
cd "$(dirname "$0")/../.."
OUT="$(mktemp -d)"
trap 'rm -rf -- "$OUT"' EXIT
"${JAVAC:-javac}" --release 11 -encoding UTF-8 -d "$OUT" \
  apps/MarkdownDesk/src/com/example/markdowndesk/archive/ArchiveJournal.java \
  verification/archive_reader/ArchiveJournalHistoryMemoryCheck.java
"${JAVA:-java}" -Xmx32m -cp "$OUT" com.example.markdowndesk.archive.ArchiveJournalHistoryMemoryCheck
