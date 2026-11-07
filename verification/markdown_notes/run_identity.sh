#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../.."
OUT="$(mktemp -d)"
trap 'rm -rf -- "$OUT"' EXIT
javac --release 11 -d "$OUT" \
  apps/MarkdownDesk/src/com/example/markdowndesk/archive/ArchiveJournal.java \
  apps/MarkdownDesk/src/com/example/markdowndesk/archive/ArchiveMarkdownIndexes.java \
  apps/MarkdownDesk/src/com/example/markdowndesk/workspace/annotations/MarkdownNoteIdentity.java \
  apps/MarkdownDesk/src/com/example/markdowndesk/workspace/annotations/AnnotationTone.java \
  verification/markdown_notes/AnnotationToneCheck.java \
  verification/markdown_notes/MarkdownNoteIdentityCheck.java
java -Xmx128m -cp "$OUT" \
  com.example.markdowndesk.workspace.annotations.MarkdownNoteIdentityCheck

java -Xmx128m -cp "$OUT" com.example.markdowndesk.workspace.annotations.AnnotationToneCheck
