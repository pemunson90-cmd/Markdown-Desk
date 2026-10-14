#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
OUT="$PWD/verification/build/host"
mkdir -p "$OUT/renderer" "$OUT/splash" "$OUT/tmp"
python3 verification/test_baseline_inventory.py
python3 verification/check_baseline_inventory.py --baseline-ref v2.2.5-baseline
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

# Required behavior/recoverability; raw provider hazards stay a separate manual diagnostic.
bash verification/document_io/run.sh
bash verification/session_recovery/run.sh
bash verification/renderer_golden/run.sh
bash verification/mode_behavior/run.sh

bash verification/contrast/run.sh --gate

bash verification/recovery_loader/run.sh
bash verification/status_behavior/run.sh test
bash verification/statistics/run.sh
bash verification/preview_behavior/run.sh
bash verification/line_index/run.sh
bash verification/syntax/run.sh

bash verification/save_lifecycle/run.sh --safety

# Actual immutable protection + coordinator fixtures (model/probes stay separate).
bash verification/protection_store/run.sh
bash verification/protection_store/run_adapter.sh
bash verification/save_coordinator/run.sh
bash verification/save_coordinator/run_memory.sh
bash verification/protection_restart/run.sh
bash verification/split_resize/run.sh
bash verification/history_catalog/run.sh
bash verification/history_index/run.sh
bash verification/history_diff/run.sh
bash verification/private_archive/run.sh all
bash verification/history_retention/run.sh
bash verification/history_planner/run.sh
bash verification/history_capacity/run.sh all

bash verification/history_maintenance/run.sh
