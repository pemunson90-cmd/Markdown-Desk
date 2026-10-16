#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../.."
out=verification/build/preview-navigation
mkdir -p "$out"
javac -d "$out" verification/preview_navigation/ExtractNavigation.java
activity_source=apps/MarkdownDesk/src/com/example/markdowndesk/MainActivity.java
source="$activity_source"
extract_args=()
check_args=()
if [[ "${1:-}" == "--baseline" ]]; then
    git show 116b180c80d7395a7c23297979f0cdc90ae9c7e2:"$source" > "$out/Baseline.java"
    source="$out/Baseline.java"; extract_args=(--baseline); check_args=(--baseline); shift
fi
if [[ "${1:-}" == "--print-hazards" ]]; then
    git show 6ebe5637235824d5314d5ff51abd575f3c2bf630:"$activity_source" > "$out/PrintBaseline.java"
    source="$out/PrintBaseline.java"; extract_args=(--print-only)
fi
java -cp "$out" ExtractNavigation "$source" verification/preview_navigation/MainActivity.template "$out/MainActivity.java" "${extract_args[@]}"
javac --release 11 -d "$out" verification/stubs/android/graphics/drawable/GradientDrawable.java apps/MarkdownDesk/src/com/example/markdowndesk/{UiTheme,ReaderTypography,MarkdownRenderer,LatestPreview}.java "$out/MainActivity.java" verification/preview_navigation/NavigationCheck.java
java -Xmx128m -cp "$out" com.example.markdowndesk.NavigationCheck "${check_args[@]}" "$@"
