#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../.."
OUT="$(mktemp -d)"
trap 'rm -rf -- "$OUT"' EXIT
javac --release 11 -d "$OUT" \
  apps/MarkdownDesk/src/com/example/markdowndesk/archive/ArchiveJournal.java \
  verification/archive_reader/ArchiveJournalConditionalCheck.java
java -Xmx128m -cp "$OUT" com.example.markdowndesk.archive.ArchiveJournalConditionalCheck
