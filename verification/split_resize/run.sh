#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../.."
out=verification/build/split-resize
mkdir -p "$out"
javac --release 11 -d "$out" verification/split_resize/ExtractUi.java
java -cp "$out" ExtractUi apps/MarkdownDesk/src/com/example/markdowndesk/MainActivity.java verification/split_resize/UiHarness.template "$out/MainActivity.java"
javac --release 11 -d "$out" "$out/MainActivity.java" apps/MarkdownDesk/src/com/example/markdowndesk/SplitResizeController.java verification/split_resize/UiTest.java verification/split_resize/SplitResizeControllerTest.java
java -cp "$out" com.example.markdowndesk.UiTest
java -cp "$out" com.example.markdowndesk.SplitResizeControllerTest
