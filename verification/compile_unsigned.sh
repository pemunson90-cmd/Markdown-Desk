#!/usr/bin/env bash
# Unsigned validation delegates to the current manifest-aware build, not 2.2.4.
# Configure ANDROID_SDK_ROOT, ANDROID_PLATFORM and ANDROID_BUILD_TOOLS.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
[[ $# == 0 ]] || { echo 'Configure SDK through environment; no positional arguments' >&2; exit 1; }
exec bash "$ROOT/apps/MarkdownDesk/build.sh" --unsigned MarkdownDesk-2.2.5-UNSIGNED
