#!/usr/bin/env python3
"""Two fresh JVM reopens per synthetic interruption; not executor/process-death acceptance."""
import hashlib
import os
from pathlib import Path
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "verification/build/history-maintenance"
BASE = ["java", "-Xmx128m", "-cp", str(OUT / "classes"), "com.example.markdowndesk.MaintenanceRestartProbe"]

def run(mode, parent, *args):
    process = subprocess.run(BASE + [mode, str(parent), *args], text=True, capture_output=True, timeout=90)
    if process.returncode:
        raise AssertionError(process.stdout + process.stderr)
    return process.stdout.strip()

def capture(parent):
    return {str(path.relative_to(parent)): hashlib.sha256(path.read_bytes()).hexdigest()
            for path in parent.rglob("*") if path.is_file()}

with tempfile.TemporaryDirectory(prefix="restart-", dir=OUT / "tmp") as temporary:
    for boundary in ("intact", "checkpoint", "index", "complete", "transaction", "snapshot", "all"):
        parent = Path(temporary) / boundary
        parent.mkdir()
        run("seed", parent, boundary)
        before = capture(parent)
        first = run("recover", parent)
        second = run("recover", parent)
        assert first == second, (boundary, first, second)
        assert before == capture(parent), boundary
        print("PASS two fresh JVM reopens:", boundary)
print("Maintenance fresh-process projection: 7 fixtures / 14 reopens PASS; no executor or unlink acceptance")
