#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../.."
out=verification/build/renderer-golden
mkdir -p "$out"
javac --release 11 -d "$out" \
  verification/stubs/android/graphics/drawable/GradientDrawable.java \
  apps/MarkdownDesk/src/com/example/markdowndesk/UiTheme.java \
  apps/MarkdownDesk/src/com/example/markdowndesk/MarkdownRenderer.java \
  verification/renderer_golden/RendererGolden.java \
  verification/renderer_golden/NestedListKnownFailure.java
java -cp "$out" com.example.markdowndesk.RendererGolden
if java -cp "$out" com.example.markdowndesk.NestedListKnownFailure >"$out/nested-list-known-failure.txt" 2>&1; then
  echo 'UNEXPECTED PASS: nested-list known failure changed; review fixture and renderer'
  exit 1
fi
rg -F -q 'got <<ul data-source-offset="0"><li>parent</li><li>child</li><li>sibling</li></ul>>' "$out/nested-list-known-failure.txt" || {
  cat "$out/nested-list-known-failure.txt"
  echo 'Unexpected nested-list failure; review actual exception'
  exit 1
}
echo "KNOWN FAIL nested list; reproduction in $out/nested-list-known-failure.txt"
