#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../.."
out=verification/build/outline-navigation
mkdir -p "$out"
javac -d "$out" verification/outline_navigation/ExtractOutline.java
java -cp "$out" ExtractOutline apps/MarkdownDesk/src/com/example/markdowndesk/MainActivity.java verification/outline_navigation/MainActivity.template "$out/MainActivity.java"
javac --release 11 -d "$out" verification/stubs/android/graphics/drawable/GradientDrawable.java apps/MarkdownDesk/src/com/example/markdowndesk/{UiTheme,MarkdownRenderer,MarkdownStructure,SearchEngine,LatestPreview}.java "$out/MainActivity.java" verification/outline_navigation/OutlineCheck.java
java -Xmx128m -cp "$out" com.example.markdowndesk.OutlineCheck "$@"
