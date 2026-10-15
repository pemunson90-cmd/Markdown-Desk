#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../.."
out=verification/build/outline-navigation
fixture=verification/outline_navigation
source=apps/MarkdownDesk/src/com/example/markdowndesk/MainActivity.java
if [[ "${1:-}" == "--baseline" ]]; then
    out="$out/baseline";mkdir -p "$out/source"
    git show 05e74ca2906695ebe6e7ae4e2d68b6b536a24a47:"$source" > "$out/source/Baseline.java"
    source="$out/source/Baseline.java";fixture="$fixture/baseline";shift
fi
mkdir -p "$out"
javac -d "$out" "$fixture/ExtractOutline.java"
java -cp "$out" ExtractOutline "$source" "$fixture/MainActivity.template" "$out/MainActivity.java"
javac --release 11 -d "$out" verification/stubs/android/graphics/drawable/GradientDrawable.java apps/MarkdownDesk/src/com/example/markdowndesk/{UiTheme,MarkdownRenderer,MarkdownStructure,SearchEngine,LatestPreview}.java "$out/MainActivity.java" "$fixture/OutlineCheck.java"
java -Xmx128m -cp "$out" com.example.markdowndesk.OutlineCheck "$@"
