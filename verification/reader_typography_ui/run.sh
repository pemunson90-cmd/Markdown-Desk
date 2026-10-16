#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../.."
OUT=verification/build/reader-typography-ui
mkdir -p "$OUT"
javac --release 11 -d "$OUT" verification/reader_typography_ui/ExtractReaderUi.java
java -cp "$OUT" ExtractReaderUi apps/MarkdownDesk/src/com/example/markdowndesk/MainActivity.java verification/reader_typography_ui/ReaderUiHarness.template "$OUT/ReaderUiHarness.java" verification/reader_typography_ui/ReaderExportHarness.template "$OUT/ReaderExportHarness.java"
javac --release 11 -d "$OUT" apps/MarkdownDesk/src/com/example/markdowndesk/ReaderTypography.java "$OUT/ReaderUiHarness.java" verification/reader_typography_ui/ReaderUiCheck.java
java -cp "$OUT" com.example.markdowndesk.ReaderUiCheck

javac --release 11 -sourcepath apps/MarkdownDesk/src:verification/stubs -d "$OUT" verification/reader_typography_ui/ReaderPreviewCheck.java
java -cp "$OUT" com.example.markdowndesk.ReaderPreviewCheck

javac --release 11 -sourcepath apps/MarkdownDesk/src:verification/stubs -d "$OUT" "$OUT/ReaderExportHarness.java" verification/reader_typography_ui/ReaderExportCheck.java
java -cp "$OUT" com.example.markdowndesk.ReaderExportCheck
