#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../.."
out="$PWD/verification/build/malformed-renderer"
javac_bin="${JAVAC:-/workspace/.command-library-env/jdk-17.0.16+8/bin/javac}"
java_bin="${JAVA:-/workspace/.command-library-env/jdk-17.0.16+8/bin/java}"
mkdir -p "$out/classes"
"$javac_bin" --release 11 -d "$out/classes" \
  verification/stubs/android/graphics/drawable/GradientDrawable.java \
  apps/MarkdownDesk/src/com/example/markdowndesk/UiTheme.java \
  apps/MarkdownDesk/src/com/example/markdowndesk/MarkdownRenderer.java \
  verification/malformed_renderer/Probe.java
sha256sum apps/MarkdownDesk/src/com/example/markdowndesk/MarkdownRenderer.java \
  apps/MarkdownDesk/src/com/example/markdowndesk/UiTheme.java
failures=0
for probe_case in table_2000 table_5000 spaces_dangling_pipe fragments_1000 fragments_3000; do
  set +e
  timeout 10s "$java_bin" -Xms128m -Xmx1g -cp "$out/classes" \
    com.example.markdowndesk.Probe "$probe_case" > "$out/$probe_case.log" 2>&1
  probe_exit=$?
  set -e
  echo "CASE_RESULT $probe_case exit=$probe_exit"
  rg '^(CASE |OK |THREW )' "$out/$probe_case.log" || true
  if (( probe_exit != 0 )); then failures=$((failures + 1)); fi
done
echo "CASE_SUMMARY failures=$failures total=5 logs=$out"
(( failures == 0 ))
