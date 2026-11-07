#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../.."
OUT="$(mktemp -d)"
trap 'rm -rf -- "$OUT"' EXIT
"${JAVAC:-javac}" --release 11 -encoding UTF-8 -d "$OUT" \
  apps/MarkdownDesk/src/com/example/markdowndesk/ArchiveTranscriptIntegrity.java \
  apps/MarkdownDesk/src/com/example/markdowndesk/ExportReceipt.java \
  verification/archive_reader/ArchiveTranscriptIntegrityCheck.java \
  verification/archive_reader/ExportReceiptCheck.java
"${JAVA:-java}" -cp "$OUT" com.example.markdowndesk.ArchiveTranscriptIntegrityCheck

"${JAVA:-java}" -cp "$OUT" com.example.markdowndesk.ExportReceiptCheck
