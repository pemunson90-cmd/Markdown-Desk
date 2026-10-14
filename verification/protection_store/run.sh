#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
OUT="$ROOT/verification/build/protection-store"
mkdir -p "$OUT/classes" "$OUT/tmp"
RUN_FIXTURES=$(mktemp -d "$OUT/tmp/run-XXXXXXXX")
cleanup_fixtures() {
 python3 - "$RUN_FIXTURES" "$OUT/tmp" <<'CLEANUP'
from pathlib import Path
import shutil, sys
fixture, parent = map(Path, sys.argv[1:])
assert fixture.parent.resolve() == parent.resolve() and fixture.name.startswith('run-')
assert not fixture.is_symlink()
shutil.rmtree(fixture)
CLEANUP
}
trap cleanup_fixtures EXIT
javac --release 11 -d "$OUT/classes" \
 "$ROOT/apps/MarkdownDesk/src/com/example/markdowndesk/ProtectionStore.java" \
 "$ROOT/apps/MarkdownDesk/src/com/example/markdowndesk/HistoryStorage.java" \
 "$ROOT/verification/protection_store/ProtectionStoreCheck.java"
java -Djava.io.tmpdir="$RUN_FIXTURES" -cp "$OUT/classes" com.example.markdowndesk.ProtectionStoreCheck
python3 - "$OUT" "$RUN_FIXTURES" <<'PY'
import os, resource, signal, subprocess, sys
out, fixtures = sys.argv[1:]
def restrict_writes():
    signal.signal(signal.SIGXFSZ, signal.SIG_IGN)
    resource.setrlimit(resource.RLIMIT_FSIZE, (4096, 4096))
subprocess.run(["java", "-Djava.io.tmpdir=" + fixtures, "-cp", out + "/classes",
                "com.example.markdowndesk.ProtectionStoreCheck", "write-failure"],
               check=True, preexec_fn=restrict_writes)
PY
