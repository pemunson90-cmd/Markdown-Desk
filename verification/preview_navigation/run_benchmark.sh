#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../.."
out=verification/build/preview-navigation-benchmark
mkdir -p "$out"
javac -d "$out" verification/preview_navigation/ExtractNavigation.java
java -cp "$out" ExtractNavigation apps/MarkdownDesk/src/com/example/markdowndesk/MainActivity.java verification/preview_navigation/MainActivity.template "$out/MainActivity.java" >&2
javac --release 11 -d "$out" verification/stubs/android/graphics/drawable/GradientDrawable.java apps/MarkdownDesk/src/com/example/markdowndesk/{UiTheme,ReaderTypography,MarkdownRenderer,LatestPreview}.java "$out/MainActivity.java" verification/preview_navigation/WrapBenchmark.java
java -Xmx128m -cp "$out" com.example.markdowndesk.WrapBenchmark
