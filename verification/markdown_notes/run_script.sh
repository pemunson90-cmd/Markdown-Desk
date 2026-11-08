#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../.."
OUT="$(mktemp -d)"
trap 'rm -rf -- "$OUT"' EXIT
javac --release 11 -d "$OUT" \
 apps/MarkdownDesk/src/com/example/markdowndesk/archive/ArchiveJournal.java \
 apps/MarkdownDesk/src/com/example/markdowndesk/workspace/annotations/AnnotationTone.java \
 apps/MarkdownDesk/src/com/example/markdowndesk/workspace/annotations/MarkdownNoteScript.java \
 verification/markdown_notes/MarkdownNoteScriptCheck.java \
 verification/markdown_notes/MarkdownNoteScriptDump.java
java -Xmx128m -cp "$OUT" com.example.markdowndesk.workspace.annotations.MarkdownNoteScriptCheck
java -Xmx128m -cp "$OUT" com.example.markdowndesk.workspace.annotations.MarkdownNoteScriptDump "$OUT/capture.js" "$OUT/boot.js"
command -v node >/dev/null || { echo 'Node.js is required for actual emitted DOM script regression' >&2; exit 1; }
node verification/markdown_notes/dom_contract.cjs "$OUT/capture.js" "$OUT/boot.js"
