#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../.."
OUT=verification/build/navigation-ui
mkdir -p "$OUT"
javac -d "$OUT" verification/navigation_ui/ExtractNavigation.java
java -cp "$OUT" ExtractNavigation apps/MarkdownDesk/src/com/example/markdowndesk/MainActivity.java verification/navigation_ui/MainActivity.template "$OUT/MainActivity.java"
javac --release 11 -d "$OUT" verification/stubs/android/graphics/drawable/GradientDrawable.java apps/MarkdownDesk/src/com/example/markdowndesk/{UiTheme,NavigationChoices,MarkdownStructure}.java "$OUT/MainActivity.java" verification/navigation_ui/NavigationUiCheck.java
java -cp "$OUT" com.example.markdowndesk.NavigationUiCheck
