#!/usr/bin/env python3
import hashlib
from pathlib import Path
import selectors
import subprocess
import sys
import tempfile

out = Path(sys.argv[1]).resolve()
command = ["java", "-Djava.io.tmpdir=" + str(out / "tmp"), "-cp", str(out / "classes"),
           "com.example.markdowndesk.HistoryMaintenanceRestartProbe"]
points = ["enroll-before", "parent-directory-before", "parent-directory-after", "maintenance-directory-before",
          "maintenance-directory-after", "file-before", "file-after", "move-before", "move-after",
          "policy-directory-before", "policy-directory-after"]

def capture(root):
    return {str(path.relative_to(root)): (path.stat().st_size, hashlib.sha256(path.read_bytes()).hexdigest())
            for path in root.rglob("*") if path.is_file()}

for point in points:
    root = Path(tempfile.mkdtemp(prefix="maintenance-restart-", dir=out / "tmp"))
    process = subprocess.Popen(command + ["enroll", str(root)], stdin=subprocess.PIPE, stdout=subprocess.PIPE, text=True, bufsize=1)
    try:
        with selectors.DefaultSelector() as selector:
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
    finally:
        if process.poll() is None:
            process.kill()
            process.wait(timeout=10)
        process.stdin.close()
        process.stdout.close()
    stopped = capture(root)
    preserved = {name: data for name, data in stopped.items()
                 if name.startswith("store/") or name == "history_capacity/00000000000000000000.policy"}
    for _ in range(2):
        subprocess.run(command + ["inspect", str(root)], check=True, stdout=subprocess.PIPE)
        assert capture(root) == stopped, "Read-only reopen changed evidence at " + point
    recovered = None
    for _ in range(2):
        subprocess.run(command + ["recover", str(root)], check=True, stdout=subprocess.PIPE)
        current = capture(root)
        assert all(current.get(name) == data for name, data in preserved.items()), "Enrollment changed v1 bytes at " + point
        if any(name.endswith(".policy-stage") for name in stopped):
            assert sum(size for size, digest in current.values()) == sum(size for size, digest in stopped.values()), "Exact stage retry grew bytes"
        if recovered is not None:
            assert current == recovered, "Repeated enrollment changed bytes"
        recovered = current
    print("PASS maintenance SIGKILL " + point + ": read-only reopen twice, enrollment retry twice")
print("History maintenance SIGKILL: 11 enrollment boundaries PASS")

root = Path(tempfile.mkdtemp(prefix="maintenance-staged-reopen-", dir=out / "tmp"))
subprocess.run(command + ["seed-staged", str(root)], check=True, stdout=subprocess.PIPE)
staged = capture(root)
for _ in range(2):
    subprocess.run(command + ["inspect-staged", str(root)], check=True, stdout=subprocess.PIPE)
    assert capture(root) == staged, "Fresh staged reopen changed retained evidence"
print("History maintenance: preexisting stage fresh-process reopen twice PASS")
