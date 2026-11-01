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
  apps/MarkdownDesk/src/com/example/markdowndesk/MarkdownRenderer.java \
  verification/footnotes/FootnoteGolden.java
java -cp "$out" com.example.markdowndesk.FootnoteGolden "$@"
