#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../.."
OUT=verification/build/navigation-choices
mkdir -p "$OUT"
sources=(verification/navigation_choices/NavigationChoicesCheck.java)
if [[ -f apps/MarkdownDesk/src/com/example/markdowndesk/NavigationChoices.java ]]; then sources+=(apps/MarkdownDesk/src/com/example/markdowndesk/NavigationChoices.java); fi
javac --release 11 -d "$OUT" "${sources[@]}"
java -cp "$OUT" com.example.markdowndesk.NavigationChoicesCheck
