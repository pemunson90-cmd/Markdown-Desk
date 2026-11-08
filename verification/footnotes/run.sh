#!/usr/bin/env bash
# Operator-owned reader-footnote acceptance (written before implementation).
set -euo pipefail
cd "$(dirname "$0")/../.."
out=verification/build/footnotes
mkdir -p "$out"
javac --release 11 -d "$out" \
  verification/stubs/android/graphics/drawable/GradientDrawable.java \
  apps/MarkdownDesk/src/com/example/markdowndesk/UiTheme.java \
  apps/MarkdownDesk/src/com/example/markdowndesk/ReaderTypography.java \
  apps/MarkdownDesk/src/com/example/markdowndesk/workspace/WorkspaceDeepLinks.java apps/MarkdownDesk/src/com/example/markdowndesk/MarkdownRenderer.java \
  verification/footnotes/FootnoteGolden.java \
  verification/footnotes/FootnoteSyncDump.java
java -cp "$out" com.example.markdowndesk.FootnoteGolden "$@"
[[ "${1:-}" == --write-baseline ]] && exit 0
# Behavioral sync check executes the page's real scripts under Node (no browser).
NODE="${MD_NODE:-$(command -v node || echo '/mnt/c/Program Files/nodejs/node.exe')}"
java -cp "$out" com.example.markdowndesk.FootnoteSyncDump "$out/sync-cases.js"
cat "$out/sync-cases.js" verification/footnotes/footnote_sync.cjs | "$NODE" -
