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
 "$ROOT/verification/protection_store/ProtectionStoreCheck.java" \
 "$ROOT/verification/protection_store/CompletionReservationCheck.java" \
 "$ROOT/verification/protection_store/CompletionRestartProbe.java"
case "${1:-all}" in
 all) java -Djava.io.tmpdir="$RUN_FIXTURES" -cp "$OUT/classes" com.example.markdowndesk.ProtectionStoreCheck
      java -Djava.io.tmpdir="$RUN_FIXTURES" -cp "$OUT/classes" com.example.markdowndesk.CompletionReservationCheck
      python3 "$ROOT/verification/protection_store/run_completion_restart.py" "$OUT" ;;
 physical) ;; # Only existing real filesystem-failure assertions.
 *) echo 'Usage: run.sh [all|physical]' >&2; exit 2 ;;
esac
python3 - "$OUT" "$RUN_FIXTURES" <<'PY'
import os, resource, signal, subprocess, sys, tempfile
out, fixtures = sys.argv[1:]
def run_child(command, preexec_fn=None):
    # RLIMIT_FSIZE also applies to inherited regular-file logs. Pipes preserve
    # assertion/error output when the host runner itself redirects to a file.
    result = subprocess.run(command, preexec_fn=preexec_fn, capture_output=True, text=True)
    print(result.stdout, end='')
    print(result.stderr, end='', file=sys.stderr)
    result.check_returncode()
def restrict_writes():
    signal.signal(signal.SIGXFSZ, signal.SIG_IGN)
    resource.setrlimit(resource.RLIMIT_FSIZE, (4096, 4096))
run_child(["java", "-Djava.io.tmpdir=" + fixtures, "-cp", out + "/classes",
                "com.example.markdowndesk.ProtectionStoreCheck", "write-failure"], restrict_writes)
for bound in (0, 4096):
    root = tempfile.mkdtemp(prefix="completion-write-", dir=fixtures) + "/store"
    command = ["java", "-Djava.io.tmpdir=" + fixtures, "-cp", out + "/classes", "com.example.markdowndesk.ProtectionStoreCheck"]
    run_child(command + ["completion-seed", root])
    def restrict_completion_writes():
        signal.signal(signal.SIGXFSZ, signal.SIG_IGN)
        resource.setrlimit(resource.RLIMIT_FSIZE, (bound, bound))
    run_child(command + ["completion-write-failure", root, str(bound)], restrict_completion_writes)
PY
