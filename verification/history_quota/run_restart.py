#!/usr/bin/env python3
import hashlib
from pathlib import Path
import selectors
import subprocess
import sys
import tempfile

out = Path(sys.argv[1]).resolve()
command = ["java", "-Djava.io.tmpdir=" + str(out / "tmp"), "-cp", str(out / "classes"),
           "com.example.markdowndesk.HistoryQuotaRestartProbe"]
points = ["file-before", "file-after", "move-before", "move-after", "directory-before", "directory-after"]

def capture(root):
    return {str(p.relative_to(root)): (p.stat().st_size, hashlib.sha256(p.read_bytes()).hexdigest())
            for p in root.rglob("*") if p.is_file()}

initial_points = ["register-before", "parent-directory-before", "parent-directory-after", "policy-directory-before", "policy-directory-after",
                  "file-before", "file-after", "move-before", "move-after", "commit-directory-before", "commit-directory-after"]

for mode, point in [("change", point) for point in points] + [("register", point) for point in initial_points]:
    root = Path(tempfile.mkdtemp(prefix="quota-restart-", dir=out / "tmp"))
    process = subprocess.Popen(command + [mode, str(root)], stdin=subprocess.PIPE, stdout=subprocess.PIPE, text=True, bufsize=1)
    try:
        selector = selectors.DefaultSelector()
        selector.register(process.stdout, selectors.EVENT_READ)
        while True:
            if not selector.select(timeout=30):
                raise AssertionError("Timeout before " + point)
            line = process.stdout.readline().strip()
            if not line:
                raise AssertionError("Process exited before " + point)
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
    stopped = capture(root)
    for _ in range(2):
        subprocess.run(command + ["initial-inspect" if mode == "register" else "inspect", str(root)], check=True, stdout=subprocess.PIPE)
        assert capture(root) == stopped, "Inspection changed files at " + point
    native = {name: data for name, data in stopped.items() if name.startswith("store/")}
    recovered = None
    for _ in range(2):
        subprocess.run(command + ["initial-recover" if mode == "register" else "recover", str(root)], check=True, stdout=subprocess.PIPE)
        current = capture(root)
        if mode == "change":
            assert len(current) == len(stopped), "Policy retry grew entries at " + point
            assert sum(size for size, digest in current.values()) == sum(size for size, digest in stopped.values()), "Policy retry grew bytes at " + point
        if recovered is not None:
            assert current == recovered, "Second explicit recovery changed records at " + point
        recovered = current
        assert all(current.get(name) == value for name, value in native.items()), "Native records changed at " + point
    print("PASS policy SIGKILL " + mode + " " + point + ": inspected twice, explicit recovery twice")
print("History policy SIGKILL: 6 update + 11 initial-registration boundaries PASS")
