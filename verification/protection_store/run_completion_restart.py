#!/usr/bin/env python3
"""Real SIGKILL at every completion-stage retry sync/move/directory boundary."""
import hashlib
import os
from pathlib import Path
import selectors
import subprocess
import sys
import tempfile

out = Path(sys.argv[1]).resolve()
command = ["java", "-Djava.io.tmpdir=" + str(out / "tmp"), "-cp", str(out / "classes"),
           "com.example.markdowndesk.CompletionRestartProbe"]
points = ["file-before", "file-after", "move-before", "move-after", "directory-before", "directory-after"]


def snapshot(root):
    return {p.name: (p.stat().st_size, hashlib.sha256(p.read_bytes()).hexdigest()) for p in root.iterdir()}


for point in points:
    parent = Path(tempfile.mkdtemp(prefix="completion-restart-", dir=out / "tmp"))
    root = parent / "store"
    subprocess.run(command + ["create", str(root)], check=True, stdout=subprocess.PIPE)
    initial = snapshot(root)
    immutable = {name: value for name, value in initial.items() if not name.endswith(".completion-stage")}
    total = sum(size for size, digest in initial.values())
    process = subprocess.Popen(command + ["retry", str(root)], stdin=subprocess.PIPE, stdout=subprocess.PIPE,
                               text=True, bufsize=1)
    try:
        selector = selectors.DefaultSelector()
        selector.register(process.stdout, selectors.EVENT_READ)
        while True:
            if not selector.select(timeout=20):
                raise AssertionError("Timeout at " + point)
            line = process.stdout.readline().strip()
            if not line:
                raise AssertionError("Retry exited before " + point)
            if line == point:
                process.kill()
                assert process.wait(timeout=10) == -9
                break
            process.stdin.write("continue\n")
            process.stdin.flush()
        selector.close()
    finally:
        if process.poll() is None:
            process.kill()
            process.wait(timeout=10)
        process.stdin.close()
        process.stdout.close()
    stopped = snapshot(root)
    for _ in range(2):
        subprocess.run(command + ["check", str(root), point], check=True, stdout=subprocess.PIPE)
        assert snapshot(root) == stopped, "Reopen changed records at " + point
    for _ in range(2):
        subprocess.run(command + ["finish", str(root), point], check=True, stdout=subprocess.PIPE)
        current = snapshot(root)
        assert sum(size for size, digest in current.values()) == total, "Retry grew bytes at " + point
        assert len(current) == 4, "Retry grew entries at " + point
        assert all(current.get(name) == value for name, value in immutable.items()), "v1 bytes changed at " + point
    print("PASS completion SIGKILL retry " + point + ": two fresh inspections and two fresh completions")
print("Completion retry SIGKILL: 6 boundaries PASS")
