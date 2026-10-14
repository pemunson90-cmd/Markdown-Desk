#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../.."
out="$PWD/verification/build/status-behavior"
javac_bin="${JAVAC:-javac}"
java_bin="${JAVA:-java}"
mkdir -p "$out/classes"
"$javac_bin" --release 11 -d "$out/classes" verification/status_behavior/ExtractStatus.java
"$java_bin" -cp "$out/classes" ExtractStatus \
  apps/MarkdownDesk/src/com/example/markdowndesk/MainActivity.java \
  verification/status_behavior/StatusHarness.template "$out/StatusHarness.java"
"$javac_bin" --release 11 -d "$out/classes" \
  verification/stubs/android/graphics/drawable/GradientDrawable.java \
  apps/MarkdownDesk/src/com/example/markdowndesk/UiTheme.java \
  apps/MarkdownDesk/src/com/example/markdowndesk/EditorTools.java \
  "$out/StatusHarness.java" verification/status_behavior/StatusBehavior.java
"$java_bin" -Xms128m -Xmx1g -cp "$out/classes" com.example.markdowndesk.StatusBehavior "${1:-test}"
