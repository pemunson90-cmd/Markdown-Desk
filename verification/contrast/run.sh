#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../.."
OUT=verification/build/contrast
mkdir -p "$OUT"
javac --release 11 -d "$OUT" verification/stubs/android/graphics/drawable/GradientDrawable.java apps/MarkdownDesk/src/com/example/markdowndesk/UiTheme.java verification/contrast/PaletteContrastAudit.java verification/contrast/ReadableForegroundCheck.java verification/contrast/SplashHintContrastCheck.java
java -cp "$OUT" com.example.markdowndesk.ReadableForegroundCheck
java -cp "$OUT" com.example.markdowndesk.SplashHintContrastCheck
java -cp "$OUT" com.example.markdowndesk.PaletteContrastAudit "$@"
bash verification/contrast/run_style.sh
