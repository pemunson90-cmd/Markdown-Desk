#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
python3 verification/check_layout.py
java verification/ParseJava.java apps/MarkdownDesk/src shared apps/MarkdownDesk/test
mkdir -p verification/classes
java com.sun.tools.javac.Main -source 11 -target 11 -d verification/classes verification/stubs/android/graphics/drawable/GradientDrawable.java apps/MarkdownDesk/src/com/example/markdowndesk/UiTheme.java apps/MarkdownDesk/src/com/example/markdowndesk/MarkdownRenderer.java verification/RendererCheck.java
java -cp verification/classes com.example.markdowndesk.RendererCheck verification/rendered
# Optional browser proxy, not part of the passed host gates:
# node verification/reader_layout.cjs
