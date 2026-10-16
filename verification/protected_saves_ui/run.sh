#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../.."
OUT=verification/build/protected-saves-ui
mkdir -p "$OUT"
javac --release 11 -d "$OUT" verification/protected_saves_ui/ExtractProtectedSaves.java
java -cp "$OUT" ExtractProtectedSaves apps/MarkdownDesk/src/com/example/markdowndesk/MainActivity.java verification/protected_saves_ui/ProtectedHarness.template "$OUT/ProtectedHarness.java"
mapfile -t STUBS < <(rg --files verification/workspace_ui/stubs/android/{view,widget,graphics} -g '*.java')
javac --release 11 -d "$OUT" "${STUBS[@]}" verification/workspace_ui/stubs/android/R.java verification/workspace_ui/stubs/android/os/{Handler,Build}.java verification/workspace_directory_loader/stubs/android/os/Looper.java verification/protected_saves_ui/Intent.java apps/MarkdownDesk/src/com/example/markdowndesk/UiTheme.java "$OUT/ProtectedHarness.java" verification/protected_saves_ui/ProtectedSavesCheck.java
java -Xmx64m -cp "$OUT" com.example.markdowndesk.ProtectedSavesCheck
