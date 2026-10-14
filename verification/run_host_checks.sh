#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
OUT="$PWD/verification/build/host"
mkdir -p "$OUT/renderer" "$OUT/splash" "$OUT/tmp"
python3 verification/check_baseline_inventory.py
python3 verification/check_layout.py
python3 verification/check_live_appearance.py
java verification/ParseJava.java apps/MarkdownDesk/src shared apps/MarkdownDesk/test
javac --release 11 -d "$OUT/renderer" verification/stubs/android/graphics/drawable/GradientDrawable.java apps/MarkdownDesk/src/com/example/markdowndesk/UiTheme.java apps/MarkdownDesk/src/com/example/markdowndesk/MarkdownRenderer.java verification/RendererCheck.java
java -cp "$OUT/renderer" com.example.markdowndesk.RendererCheck "$OUT/rendered"
javac --release 11 -d "$OUT/renderer" apps/MarkdownDesk/src/com/example/markdowndesk/SearchEngine.java apps/MarkdownDesk/src/com/example/markdowndesk/EditorTools.java apps/MarkdownDesk/src/com/example/markdowndesk/AtomicCommit.java verification/BaselineBehaviorCheck.java
java -Djava.io.tmpdir="$OUT/tmp" -cp "$OUT/renderer" com.example.markdowndesk.BaselineBehaviorCheck
JSON_JAR="$PWD/verification/build/deps/android-json-0.0.20131108.vaadin1.jar"
test -f "$JSON_JAR" || { echo 'Run bash verification/setup_host_deps.sh once before offline checks' >&2; exit 1; }
echo "dfb7bae2f404cfe0b72b4d23944698cb716b7665171812a0a4d0f5926c0fac79  $JSON_JAR" | sha256sum --check --status
mapfile -t STUBS < <(find apps/MarkdownDesk/test/stub -name '*.java' | sort)
javac --release 11 -cp "$JSON_JAR" -d "$OUT/splash" "${STUBS[@]}" apps/MarkdownDesk/src/com/example/markdowndesk/SplashStore.java apps/MarkdownDesk/test/SplashStoreTest.java
java -Djava.io.tmpdir="$OUT/tmp" -cp "$OUT/splash:$JSON_JAR" SplashStoreTest
# Optional browser proxy: node verification/reader_layout.cjs (not a host gate).
