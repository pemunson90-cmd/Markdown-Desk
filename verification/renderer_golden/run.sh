#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../.."
out=verification/build/renderer-golden
mkdir -p "$out"
javac --release 11 -d "$out" \
  verification/stubs/android/graphics/drawable/GradientDrawable.java \
  apps/MarkdownDesk/src/com/example/markdowndesk/UiTheme.java \
  apps/MarkdownDesk/src/com/example/markdowndesk/MarkdownRenderer.java \
  verification/renderer_golden/RendererGolden.java \
  verification/renderer_golden/NestedListKnownFailure.java \
  verification/renderer_golden/TableDividerGolden.java
java -cp "$out" com.example.markdowndesk.RendererGolden
java -cp "$out" com.example.markdowndesk.NestedListKnownFailure
java -cp "$out" com.example.markdowndesk.TableDividerGolden
