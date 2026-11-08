#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../.."
OUT="$(mktemp -d)"
trap 'rm -rf -- "$OUT"' EXIT
"${JAVAC:-javac}" --release 11 -encoding UTF-8 -d "$OUT" \
  apps/MarkdownDesk/src/com/example/markdowndesk/archive/ArchiveJournal.java \
  apps/MarkdownDesk/src/com/example/markdowndesk/workspace/annotations/AnnotationTone.java \
  apps/MarkdownDesk/src/com/example/markdowndesk/workspace/archive/ArchiveScrollScript.java \
  apps/MarkdownDesk/src/com/example/markdowndesk/workspace/archive/ArchiveHighlightScript.java \
  apps/MarkdownDesk/src/com/example/markdowndesk/workspace/archive/ArchiveSelectionScript.java \
  verification/archive_reader/ArchiveHighlightScriptTest.java \
  verification/archive_reader/ArchiveHighlightDump.java \
  verification/archive_reader/ArchiveSelectionScriptCheck.java \
  verification/archive_reader/ArchiveSelectionScriptDump.java
"${JAVA:-java}" -cp "$OUT" com.example.markdowndesk.workspace.archive.ArchiveSelectionScriptCheck
"${JAVA:-java}" -cp "$OUT" com.example.markdowndesk.workspace.archive.ArchiveHighlightScriptTest
"${JAVA:-java}" -cp "$OUT" com.example.markdowndesk.workspace.archive.ArchiveSelectionScriptDump > "$OUT/capture.js"
"${JAVA:-java}" -cp "$OUT" com.example.markdowndesk.workspace.archive.ArchiveHighlightDump > "$OUT/highlight.js"
node --check "$OUT/capture.js"
node --check "$OUT/highlight.js"
node verification/archive_reader/archive_selection_dom.cjs "$OUT/capture.js"
node verification/archive_reader/archive_highlight_dom.cjs "$OUT/highlight.js"

"${JAVA:-java}" -cp "$OUT" com.example.markdowndesk.workspace.archive.ArchiveSelectionScriptDump restore > "$OUT/restore-selection.js"
"${JAVA:-java}" -cp "$OUT" com.example.markdowndesk.workspace.archive.ArchiveSelectionScriptDump observe > "$OUT/observe-selection.js"
node --check "$OUT/restore-selection.js"
node --check "$OUT/observe-selection.js"
node verification/archive_reader/archive_selection_restore_dom.cjs "$OUT/restore-selection.js"
