#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../.."
out=verification/build/preview-navigation
mkdir -p "$out"
javac -d "$out" verification/preview_navigation/ExtractNavigation.java
java -cp "$out" ExtractNavigation apps/MarkdownDesk/src/com/example/markdowndesk/MainActivity.java verification/preview_navigation/MainActivity.template "$out/MainActivity.java"
javac --release 11 -d "$out" verification/stubs/android/graphics/drawable/GradientDrawable.java apps/MarkdownDesk/src/com/example/markdowndesk/{UiTheme,MarkdownRenderer,LatestPreview}.java "$out/MainActivity.java" verification/preview_navigation/NavigationCheck.java
java -Xmx128m -cp "$out" com.example.markdowndesk.NavigationCheck "$@"
