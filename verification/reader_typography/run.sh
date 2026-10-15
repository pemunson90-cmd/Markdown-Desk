#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../.."
out=verification/build/reader-typography
mkdir -p "$out"
javac --release 11 -d "$out" verification/stubs/android/graphics/drawable/GradientDrawable.java apps/MarkdownDesk/src/com/example/markdowndesk/UiTheme.java apps/MarkdownDesk/src/com/example/markdowndesk/MarkdownRenderer.java apps/MarkdownDesk/src/com/example/markdowndesk/ReaderTypography.java verification/reader_typography/Capture.java verification/reader_typography/ReaderTypographyCheck.java
java -cp "$out" com.example.markdowndesk.ReaderTypographyCheck
