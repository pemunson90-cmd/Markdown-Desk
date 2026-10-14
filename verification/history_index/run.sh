#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
OUT="$ROOT/verification/build/history-index"
mkdir -p "$OUT/classes" "$OUT/tmp"
javac --release 11 -sourcepath "$ROOT/apps/MarkdownDesk/src" -d "$OUT/classes" \
 "$ROOT/apps/MarkdownDesk/src/com/example/markdowndesk/ProtectionStore.java" \
 "$ROOT/apps/MarkdownDesk/src/com/example/markdowndesk/HistoryStorage.java" \
 "$ROOT/apps/MarkdownDesk/src/com/example/markdowndesk/EditorTools.java" \
 "$ROOT/apps/MarkdownDesk/src/com/example/markdowndesk/SaveCoordinator.java" \
 "$ROOT/apps/MarkdownDesk/src/com/example/markdowndesk/HistoryCatalog.java" \
 "$ROOT/apps/MarkdownDesk/src/com/example/markdowndesk/HistoryMetadataIO.java" \
 "$ROOT/apps/MarkdownDesk/src/com/example/markdowndesk/HistoryIdentity.java" \
 "$ROOT/apps/MarkdownDesk/src/com/example/markdowndesk/HistoryIndex.java" \
 "$ROOT/verification/history_index/HistoryIndexCheck.java"
java -Djava.io.tmpdir="$OUT/tmp" -cp "$OUT/classes" com.example.markdowndesk.HistoryIndexCheck
python3 - "$OUT/classes" "$OUT/tmp" <<'PY_RESTART'
import pathlib, subprocess, sys, tempfile
main = "com.example.markdowndesk.HistoryIndexCheck"
with tempfile.TemporaryDirectory(prefix="history-index-restart-", dir=sys.argv[2]) as root:
    seeded = subprocess.run(["java", "-cp", sys.argv[1], main, "seed", root],
                            check=True, text=True, capture_output=True, timeout=20)
    history_id = seeded.stdout.strip()
    sources = pathlib.Path(root) / "protection"
    before = {p.name: p.read_bytes() for p in sources.iterdir()}
    for _ in range(2):
        subprocess.run(["java", "-cp", sys.argv[1], main, "reopen", root, history_id],
                       check=True, timeout=20)
        assert before == {p.name: p.read_bytes() for p in sources.iterdir()}
print("PASS committed identities/index reopened in two independent JVMs; v1 source bytes unchanged")
PY_RESTART
python3 - "$OUT/classes" "$OUT/tmp" <<'PY_KILL'
import os, pathlib, selectors, signal, subprocess, sys, tempfile
main = "com.example.markdowndesk.HistoryIndexCheck"
points = [phase + "-" + operation for operation in ("file-sync", "move", "directory-sync")
          for phase in ("before", "after")]
for target in ("history_identity", "history_index"):
    for point in points:
        with tempfile.TemporaryDirectory(prefix="history-index-kill-", dir=sys.argv[2]) as root:
            child = subprocess.Popen(["java", "-cp", sys.argv[1], main, "child", root, target],
                                     stdin=subprocess.PIPE, stdout=subprocess.PIPE,
                                     stderr=subprocess.PIPE, text=True, bufsize=1)
            before = None
            try:
                with selectors.DefaultSelector() as selector:
                    selector.register(child.stdout, selectors.EVENT_READ)
                    while True:
                        if not selector.select(timeout=15):
                            raise RuntimeError("timeout at " + target + " " + point)
                        marker = child.stdout.readline().strip()
                        if marker not in points:
                            raise RuntimeError("unexpected boundary " + marker + ": " + child.stderr.read())
                        if before is None:
                            before = {p.name: p.read_bytes() for p in (pathlib.Path(root) / "protection").iterdir()}
                        if marker == point:
                            os.kill(child.pid, signal.SIGKILL)
                            assert child.wait(timeout=10) == -signal.SIGKILL
                            break
                        child.stdin.write("continue\n")
                        child.stdin.flush()
                for _ in range(2):
                    subprocess.run(["java", "-cp", sys.argv[1], main, "fault-reopen", root, target, point],
                                   check=True, timeout=15)
                    assert before == {p.name: p.read_bytes() for p in (pathlib.Path(root) / "protection").iterdir()}
            finally:
                if child.poll() is None:
                    child.kill()
                    child.wait(timeout=10)
                for stream in (child.stdin, child.stdout, child.stderr):
                    stream.close()
print("PASS 12 actual identity/index SIGKILL publication boundaries, each reopened twice; v1 envelopes unchanged")
PY_KILL
