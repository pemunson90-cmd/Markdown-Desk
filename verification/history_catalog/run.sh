#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
OUT="$ROOT/verification/build/history-catalog"
mkdir -p "$OUT/classes" "$OUT/tmp"
javac --release 11 -d "$OUT/classes" \
 "$ROOT/apps/MarkdownDesk/src/com/example/markdowndesk/ProtectionStore.java" \
 "$ROOT/apps/MarkdownDesk/src/com/example/markdowndesk/HistoryCatalog.java" \
 "$ROOT/verification/history_catalog/HistoryCatalogCheck.java"
java -Djava.io.tmpdir="$OUT/tmp" -cp "$OUT/classes" com.example.markdowndesk.HistoryCatalogCheck
python3 - "$OUT/classes" <<'PY'
import os, selectors, signal, subprocess, sys, tempfile
main = "com.example.markdowndesk.HistoryCatalogCheck"
points = [phase + "-" + operation for operation in ("file-sync", "move", "directory-sync")
          for phase in ("before", "after")]
for point in points:
    with tempfile.TemporaryDirectory(prefix="catalog-restart-") as root:
        child = subprocess.Popen(["java", "-cp", sys.argv[1], main, "child", root],
                                 stdin=subprocess.PIPE, stdout=subprocess.PIPE,
                                 stderr=subprocess.PIPE, text=True, bufsize=1)
        try:
            with selectors.DefaultSelector() as selector:
                selector.register(child.stdout, selectors.EVENT_READ)
                while True:
                    if not selector.select(timeout=10):
                        raise RuntimeError("timeout at " + point)
                    marker = child.stdout.readline().strip()
                    if marker not in points:
                        raise RuntimeError("unexpected boundary " + marker + ": " + child.stderr.read())
                    if marker == point:
                        os.kill(child.pid, signal.SIGKILL)
                        assert child.wait(timeout=10) == -signal.SIGKILL
                        break
                    child.stdin.write("continue\n")
                    child.stdin.flush()
            # Two independent reopen processes establish repeatable, nonmutating recovery.
            for _ in range(2):
                subprocess.run(["java", "-cp", sys.argv[1], main, "reopen", root, point],
                               check=True, timeout=10)
        finally:
            if child.poll() is None:
                child.kill()
                child.wait(timeout=10)
            for stream in (child.stdin, child.stdout, child.stderr):
                stream.close()
print("PASS 6 actual catalog SIGKILL boundaries, reopened twice; Android/power loss NOT RUN")
PY
