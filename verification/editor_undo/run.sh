#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../.."
OUT=verification/build/editor-undo
mkdir -p "$OUT"
javac --release 11 -d "$OUT" verification/editor_undo/ExtractUndo.java
java -cp "$OUT" ExtractUndo apps/MarkdownDesk/src/com/example/markdowndesk verification/editor_undo/UndoHarness.template "$OUT/UndoHarness.java"
javac --release 11 -d "$OUT" "$OUT/UndoHarness.java" verification/editor_undo/EditorUndoCheck.java
java -cp "$OUT" EditorUndoCheck
