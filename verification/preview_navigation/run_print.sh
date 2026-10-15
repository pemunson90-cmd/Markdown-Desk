#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../.."
out=verification/build/preview-print
mkdir -p "$out"
javac -d "$out" verification/preview_navigation/ExtractPrint.java
java -cp "$out" ExtractPrint apps/MarkdownDesk/src/com/example/markdowndesk/MainActivity.java verification/preview_navigation/PrintActivity.template "$out/MainActivity.java" apps/MarkdownDesk/src/com/example/markdowndesk/DocumentIO.java "$out/DocumentIO.java"
mapfile -t stubs < <(rg --files verification/preview_navigation/print_stubs -g '*.java')
javac --release 11 -d "$out" "${stubs[@]}" verification/stubs/android/graphics/drawable/GradientDrawable.java apps/MarkdownDesk/src/com/example/markdowndesk/{UiTheme,MarkdownRenderer,DocumentPrint}.java "$out/MainActivity.java" "$out/DocumentIO.java" verification/preview_navigation/PrintCheck.java
java -Xmx128m -cp "$out" com.example.markdowndesk.PrintCheck
# Separate JVM: exact production helper, explicitly controlled blocking/failing renderer only.
blocked="$out/blocked"
mkdir -p "$blocked"
javac --release 11 -d "$blocked" "${stubs[@]}" verification/stubs/android/graphics/drawable/GradientDrawable.java apps/MarkdownDesk/src/com/example/markdowndesk/{UiTheme,DocumentPrint}.java "$out/MainActivity.java" "$out/DocumentIO.java" verification/preview_navigation/{BlockingMarkdownRenderer,PrintRenderCheck}.java
java -Xmx128m -cp "$blocked" com.example.markdowndesk.PrintRenderCheck
