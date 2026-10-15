#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../.."
out=verification/build/preview-navigation
mkdir -p "$out"
javac -d "$out" verification/preview_navigation/ExtractNavigation.java
source=apps/MarkdownDesk/src/com/example/markdowndesk/MainActivity.java
extract_args=()
check_args=()
if [[ "${1:-}" == "--baseline" ]]; then
    git show 116b180c80d7395a7c23297979f0cdc90ae9c7e2:"$source" > "$out/Baseline.java"
    source="$out/Baseline.java"; extract_args=(--baseline); check_args=(--baseline); shift
fi
java -cp "$out" ExtractNavigation "$source" verification/preview_navigation/MainActivity.template "$out/MainActivity.java" "${extract_args[@]}"
javac --release 11 -d "$out" verification/stubs/android/graphics/drawable/GradientDrawable.java apps/MarkdownDesk/src/com/example/markdowndesk/{UiTheme,MarkdownRenderer,LatestPreview}.java "$out/MainActivity.java" verification/preview_navigation/NavigationCheck.java
java -Xmx128m -cp "$out" com.example.markdowndesk.NavigationCheck "${check_args[@]}" "$@"
