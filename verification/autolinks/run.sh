#!/usr/bin/env bash
# Operator-owned Reading-mode autolink acceptance (written before implementation).
set -euo pipefail
cd "$(dirname "$0")/../.."
out=verification/build/autolinks
mkdir -p "$out"
javac --release 11 -d "$out" \
  verification/stubs/android/graphics/drawable/GradientDrawable.java \
  apps/MarkdownDesk/src/com/example/markdowndesk/UiTheme.java \
  apps/MarkdownDesk/src/com/example/markdowndesk/ReaderTypography.java \
  apps/MarkdownDesk/src/com/example/markdowndesk/MarkdownRenderer.java \
  verification/autolinks/AutolinkGolden.java
java -cp "$out" com.example.markdowndesk.AutolinkGolden "$@"
