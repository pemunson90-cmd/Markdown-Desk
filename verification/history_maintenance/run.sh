#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
OUT="$ROOT/verification/build/history-maintenance"
mkdir -p "$OUT/classes" "$OUT/tmp"
FIXTURES="$(mktemp -d "$OUT/tmp/run-XXXXXXXX")"
cleanup_fixtures() {
 python3 - "$FIXTURES" "$OUT/tmp" <<'CLEANUP'
from pathlib import Path
import shutil, sys
fixture, parent = map(Path, sys.argv[1:])
assert fixture.parent.resolve() == parent.resolve() and fixture.name.startswith('run-')
assert not fixture.is_symlink()
shutil.rmtree(fixture)
CLEANUP
}
trap cleanup_fixtures EXIT
SRC="$ROOT/apps/MarkdownDesk/src/com/example/markdowndesk"
javac --release 11 -sourcepath "$ROOT/apps/MarkdownDesk/src" -d "$OUT/classes" "$SRC/HistoryPruneIntent.java" "$ROOT/verification/history_maintenance/IntentCodecCheck.java"
java -Xmx128m -Djava.io.tmpdir="$FIXTURES" -cp "$OUT/classes" com.example.markdowndesk.IntentCodecCheck
java -Xmx128m -Djava.io.tmpdir="$FIXTURES" -cp "$OUT/classes" com.example.markdowndesk.IntentCodecCheck memory
