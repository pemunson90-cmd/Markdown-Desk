#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../.."
phase="${1:-changed}"
out="verification/build/preview-$phase"
mkdir -p "$out"
source=apps/MarkdownDesk/src/com/example/markdowndesk/MainActivity.java
extra=()
if [ "$phase" = baseline ]; then
  git show 6630609:apps/MarkdownDesk/src/com/example/markdowndesk/MainActivity.java > "$out/MainActivity.java"
  source="$out/MainActivity.java"
  extra=(baseline)
fi
javac -d "$out" verification/preview_behavior/ExtractPreview.java
java -cp "$out" ExtractPreview "$source" verification/preview_behavior/PreviewHarness.template "$out/PreviewHarness.java" "${extra[@]}"
helper=()
if [ "$phase" != baseline ]; then helper=(apps/MarkdownDesk/src/com/example/markdowndesk/LatestPreview.java); fi
javac --release 11 -d "$out" verification/stubs/android/graphics/drawable/GradientDrawable.java \
  apps/MarkdownDesk/src/com/example/markdowndesk/{UiTheme,ReaderTypography,MarkdownRenderer}.java "${helper[@]}" \
  "$out/PreviewHarness.java" verification/preview_behavior/PreviewBenchmark.java
java -Xms128m -Xmx1g -cp "$out" com.example.markdowndesk.PreviewBenchmark "$phase"
