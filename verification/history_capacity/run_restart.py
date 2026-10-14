#!/usr/bin/env python3
"""Real SIGKILL/fresh JVM evidence, not a sudden-power-loss simulation."""
import hashlib
import os
from pathlib import Path
import select
import resource
import shutil
import signal
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "verification/build/history-capacity-restart"
OUT.mkdir(parents=True, exist_ok=True)
subprocess.run(["javac", "--release", "11", "-sourcepath", str(ROOT / "apps/MarkdownDesk/src"),
                "-d", str(OUT), str(Path(__file__).with_name("CapacityRestartProbe.java"))], check=True)
BASE = ["java", "-Xmx128m", "-cp", str(OUT), "com.example.markdowndesk.CapacityRestartProbe"]

def run(mode, parent, *args):
    result = subprocess.run(BASE + [mode, str(parent), *args], text=True, capture_output=True, timeout=30)
    if result.returncode:
        raise AssertionError(result.stdout + result.stderr)
    return result.stdout.strip()

def data(parent):
    return {str(p.relative_to(parent)): hashlib.sha256(p.read_bytes()).hexdigest()
            for role in ("store", "catalog", "identity", "index") for p in (parent / role).iterdir() if p.is_file()}

def interrupt(parent, boundary, prefix=None):
    def limits():
        signal.signal(signal.SIGXFSZ, signal.SIG_IGN)
        resource.setrlimit(resource.RLIMIT_FSIZE, (prefix, prefix))
    arguments = ["interrupt", str(parent), boundary] if prefix is None else ["limited-policy", str(parent), str(prefix)]
    child = subprocess.Popen(BASE + arguments, text=True, preexec_fn=limits if prefix is not None else None,
                             stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    try:
        ready, _, _ = select.select([child.stdout], [], [], 20)
        if not ready:
            raise AssertionError("timeout at " + boundary)
        line = child.stdout.readline().strip()
        if line != "BARRIER " + boundary:
            raise AssertionError(line + child.stderr.read())
        before = data(parent)
        os.kill(child.pid, signal.SIGKILL)
        child.wait(timeout=10)
        if child.returncode != -signal.SIGKILL:
            raise AssertionError("child did not receive SIGKILL")
        return before
    finally:
        if child.poll() is None:
            child.kill()
            child.wait()

with tempfile.TemporaryDirectory(prefix="capacity-restart-", dir=OUT) as temporary:
    base = Path(temporary)
    for boundary in ("enrollment-stage-sync", "enrollment-move", "registration-root-created",
                     "registration-stage-sync", "registration-move", "policy-stage-sync",
                     "policy-move", "policy-directory-sync"):
        parent = base / boundary
        parent.mkdir()
        before = interrupt(parent, boundary)
        mode = "blocked" if boundary in ("enrollment-stage-sync", "enrollment-move", "registration-root-created") else "reconcile"
        generation = "1" if boundary.startswith("policy-") else "0"
        # Each probe starts a new JVM. Native records must remain byte-identical.
        for _ in range(2):
            run(mode, parent, generation)
            after = data(parent)
            if not before.items() <= after.items():
                raise AssertionError("original data changed at " + boundary)
        print("PASS SIGKILL " + boundary + " / two fresh JVMs")
    for condition in ("lost-root", "malformed-marker", "marker-stage", "partial-policy", "full-control"):
        parent = base / condition
        parent.mkdir()
        run("seed", parent)
        if condition in ("lost-root", "malformed-marker", "marker-stage"):
            shutil.rmtree(parent / "history_control")
        if condition == "malformed-marker":
            (parent / "history_enrollment.control").write_bytes(b"broken")
        if condition == "marker-stage":
            (parent / "history_enrollment.control").rename(parent / "history_enrollment.control.stage")
        if condition == "partial-policy":
            (parent / "history_control/policy-0000000000000001.control.stage").write_bytes(b"partial")
        if condition == "full-control":
            for i in range(255):
                (parent / "history_control" / ("unknown-" + str(i))).touch()
        before = data(parent)
        retained = {str(p.relative_to(parent)): p.read_bytes() for p in parent.rglob("*") if p.is_file()}
        for _ in range(2):
            run("blocked", parent)
            if data(parent) != before or retained != {str(p.relative_to(parent)): p.read_bytes() for p in parent.rglob("*") if p.is_file()}:
                raise AssertionError("blocked restart mutated " + condition)
        print("PASS fresh process " + condition)
    for prefix in (0, 9):
        parent = base / ("policy-prefix-" + str(prefix))
        parent.mkdir()
        run("seed", parent)
        before = interrupt(parent, "policy-prefix-" + str(prefix), prefix)
        stage = parent / "history_control/policy-0000000000000001.control.stage"
        retained = stage.read_bytes()
        for _ in range(2):
            run("blocked", parent)
            if data(parent) != before or stage.read_bytes() != retained:
                raise AssertionError("limited prefix changed on restart")
        print("PASS SIGKILL policy-prefix-" + str(prefix) + " / actual OS write limit before file sync / two fresh JVMs")
    parent = base / "valid-full-control"
    parent.mkdir()
    run("seed", parent)
    run("fill-control", parent)
    run("reconcile", parent, "254")
    run("reconcile", parent, "254")
    print("PASS valid shared 256-entry control allowance / two fresh JVMs")
    parent = base / "never-enrolled"
    parent.mkdir()
    run("legacy", parent)
    run("legacy", parent)
    print("PASS genuine enrollment absence / two fresh JVMs")
print("History capacity restart: 10 SIGKILL boundaries / 7 fresh-process cases PASS")
