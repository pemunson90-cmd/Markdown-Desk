#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../.."
OUT=verification/build/docx-ui
mkdir -p "$OUT"
javac --release 11 -d "$OUT" verification/docx_ui/ExtractDocxUi.java
java -cp "$OUT" ExtractDocxUi apps/MarkdownDesk/src/com/example/markdowndesk/MainActivity.java verification/docx_ui/DocxHarness.template "$OUT/DocxHarness.java" apps/MarkdownDesk/src/com/example/markdowndesk/DocumentDocxExport.java
mapfile -t STUBS < <(rg --files verification/workspace_ui/stubs/android/{view,widget,graphics} -g '*.java')
javac --release 11 -d "$OUT" "${STUBS[@]}" verification/workspace_ui/stubs/android/R.java verification/workspace_ui/stubs/android/os/{Handler,Build}.java verification/workspace_directory_loader/stubs/android/os/Looper.java verification/workspace_tree_reader/stubs/android/net/Uri.java verification/docx_ui/Intent.java verification/docx_ui/proxy_stubs/android/app/Activity.java verification/docx_ui/proxy_stubs/android/os/Bundle.java apps/MarkdownDesk/src/com/example/markdowndesk/DocxPickerActivity.java apps/MarkdownDesk/src/com/example/markdowndesk/UiTheme.java "$OUT/DocxHarness.java" verification/docx_ui/DocxUiCheck.java
java -Xmx64m -cp "$OUT" com.example.markdowndesk.DocxUiCheck "$@"
javac --release 11 -cp "$OUT" -d "$OUT" verification/docx_ui/DocxPickerCheck.java
java -Xmx64m -cp "$OUT" com.example.markdowndesk.DocxPickerCheck
