#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../.."
OUT=verification/build/style-contrast
mkdir -p "$OUT"
javac --release 11 -d "$OUT" verification/contrast/ExtractStyle.java
java -cp "$OUT" ExtractStyle apps/MarkdownDesk/src/com/example/markdowndesk/MainActivity.java "$OUT/StyleHarness.java"
javac --release 11 -d "$OUT" verification/contrast/stubs/android/graphics/drawable/GradientDrawable.java apps/MarkdownDesk/src/com/example/markdowndesk/UiTheme.java verification/contrast/PaletteContrastAudit.java "$OUT/StyleHarness.java" verification/contrast/StyleContrastCheck.java
java -cp "$OUT" com.example.markdowndesk.StyleContrastCheck
