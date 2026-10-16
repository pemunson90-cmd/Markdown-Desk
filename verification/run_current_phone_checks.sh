#!/usr/bin/env bash
# 2026-10-16: explicit current-phone verification boundary.
# Owner approved postponing cross-installation private-state export/import.
# This runs the normal host regression including local saves, recovery, history,
# editing, workspace and retention safety. Only the 18 fully portable/archive
# capture/export/import test families named below are NOT RUN under this gate.
# The full original suite remains unchanged and its failures remain genuine.
# No Android device, signer, SAF provider, physical Fold or release acceptance.
# Exit nonzero on any executed failure; never mark deferred families PASS.
set -euo pipefail
cd "$(dirname "$0")/.."
OUT="$PWD/verification/build/host"
mkdir -p "$OUT/renderer" "$OUT/splash" "$OUT/tmp"
python3 verification/test_baseline_inventory.py
python3 verification/check_baseline_inventory.py --baseline-ref v2.2.5-baseline
python3 verification/check_layout.py
python3 verification/check_live_appearance.py
java verification/ParseJava.java apps/MarkdownDesk/src shared apps/MarkdownDesk/test
javac --release 11 -d "$OUT/renderer" verification/stubs/android/graphics/drawable/GradientDrawable.java apps/MarkdownDesk/src/com/example/markdowndesk/UiTheme.java apps/MarkdownDesk/src/com/example/markdowndesk/ReaderTypography.java apps/MarkdownDesk/src/com/example/markdowndesk/MarkdownRenderer.java verification/RendererCheck.java
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
bash verification/reader_typography/run.sh
bash verification/reader_positions/run.sh
bash verification/reader_typography_ui/run.sh
bash verification/mode_behavior/run.sh
bash verification/editor_undo/run.sh
bash verification/workspace_tree_reader/run.sh
bash verification/workspace_roots/run.sh
bash verification/workspace_favorites/run.sh
bash verification/workspace_directory_loader/run.sh
bash verification/workspace_search/run.sh
bash verification/workspace_search_loader/run.sh
bash verification/workspace_search_ui/run.sh
bash verification/search_hit_verifier/run.sh
bash verification/workspace_ui/run.sh

bash verification/contrast/run.sh --gate

bash verification/recovery_loader/run.sh
bash verification/status_behavior/run.sh test
bash verification/statistics/run.sh
bash verification/preview_behavior/run.sh
bash verification/preview_navigation/run.sh
bash verification/outline_navigation/run.sh
bash verification/preview_navigation/run_print.sh
bash verification/print_layout/run.sh --java-only
bash verification/docx_export/run.sh --core-only
bash verification/docx_publication/run.sh
bash verification/docx_ui/run.sh
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
bash verification/history_ui/run.sh
bash verification/local_protection_status/run.sh
bash verification/local_protection_policy/run.sh
bash verification/history_ui_integration/run.sh
bash verification/protected_saves_ui/run.sh
echo 'OWNER-DEFERRED PORTABLE TEST: private_archive (NOT RUN; retained in verification/run_host_checks.sh)'
echo 'OWNER-DEFERRED PORTABLE TEST: portable_wire_codecs (NOT RUN; retained in verification/run_host_checks.sh)'
echo 'OWNER-DEFERRED PORTABLE TEST: portable_operation_records (NOT RUN; retained in verification/run_host_checks.sh)'
echo 'OWNER-DEFERRED PORTABLE TEST: portable_internal_dispatch (NOT RUN; retained in verification/run_host_checks.sh)'
echo 'OWNER-DEFERRED PORTABLE TEST: scoped_private_archive (NOT RUN; retained in verification/run_host_checks.sh)'
echo 'OWNER-DEFERRED PORTABLE TEST: private_state (NOT RUN; retained in verification/run_host_checks.sh)'
echo 'OWNER-DEFERRED PORTABLE TEST: history_archive_manifest (NOT RUN; retained in verification/run_host_checks.sh)'
bash verification/history_retention/run.sh
bash verification/history_planner/run.sh
bash verification/history_capacity/run.sh all
bash verification/automatic_retention_policy/run.sh
bash verification/control_inspection_bounds/run.sh

bash verification/history_pins/run.sh
bash verification/native_terminal_validation/run.sh
bash verification/native_terminal_workspace/run.sh
bash verification/history_operations/run.sh
bash verification/history_operation_data/run.sh
echo 'OWNER-DEFERRED PORTABLE TEST: capture_native_sources (NOT RUN; retained in verification/run_host_checks.sh)'
echo 'OWNER-DEFERRED PORTABLE TEST: capture_source_ownership (NOT RUN; retained in verification/run_host_checks.sh)'
echo 'OWNER-DEFERRED PORTABLE TEST: capture_witnesses (NOT RUN; retained in verification/run_host_checks.sh)'
echo 'OWNER-DEFERRED PORTABLE TEST: portable_baseline (NOT RUN; retained in verification/run_host_checks.sh)'
echo 'OWNER-DEFERRED PORTABLE TEST: private_frame_preflight (NOT RUN; retained in verification/run_host_checks.sh)'
echo 'OWNER-DEFERRED PORTABLE TEST: private_capture_plan (NOT RUN; retained in verification/run_host_checks.sh)'
echo 'OWNER-DEFERRED PORTABLE TEST: portable_capture_plan (NOT RUN; retained in verification/run_host_checks.sh)'
echo 'OWNER-DEFERRED PORTABLE TEST: private_capture_writer (NOT RUN; retained in verification/run_host_checks.sh)'
echo 'OWNER-DEFERRED PORTABLE TEST: private_capture_activation (NOT RUN; retained in verification/run_host_checks.sh)'
echo 'OWNER-DEFERRED PORTABLE TEST: terminal_cancelled_admission (NOT RUN; retained in verification/run_host_checks.sh)'
echo 'OWNER-DEFERRED PORTABLE TEST: cold_capture_cancel (NOT RUN; retained in verification/run_host_checks.sh)'
bash verification/history_maintenance/run.sh
bash verification/history_executor/run.sh
bash verification/automatic_retention/run.sh

echo 'CURRENT-PHONE HOST GATE PASS: executed checks above only; owner-deferred portable tests remain NOT RUN.'
