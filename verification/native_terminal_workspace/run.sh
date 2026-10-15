#!/usr/bin/env bash
set -euo pipefail
ROOT=$(cd "$(dirname "$0")/../.." && pwd)
OUT=$(mktemp -d)
trap 'rm -rf "$OUT"' EXIT
javac --release 11 -sourcepath "$ROOT/apps/MarkdownDesk/src" -d "$OUT" "$ROOT/verification/native_terminal_workspace/NativeTerminalWorkspaceCheck.java"
java -Xmx32m -cp "$OUT" com.example.markdowndesk.NativeTerminalWorkspaceCheck
