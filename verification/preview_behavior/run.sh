#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../.."
out=verification/build/preview-behavior
mkdir -p "$out"
javac -d "$out" verification/preview_behavior/ExtractPreview.java
java -cp "$out" ExtractPreview apps/MarkdownDesk/src/com/example/markdowndesk/MainActivity.java \
  verification/preview_behavior/PreviewHarness.template "$out/PreviewHarness.java"
javac --release 11 -d "$out" verification/stubs/android/graphics/drawable/GradientDrawable.java \
  apps/MarkdownDesk/src/com/example/markdowndesk/{UiTheme,ReaderTypography,MarkdownRenderer,LatestPreview}.java \
  "$out/PreviewHarness.java" verification/preview_behavior/PreviewBehavior.java
java -Xmx128m -cp "$out" com.example.markdowndesk.PreviewBehavior
