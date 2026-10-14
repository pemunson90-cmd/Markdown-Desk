"""SIGKILL actual production save in one JVM; verify records in a fresh JVM.

Host process interruption only: not Android app-UID durability or sudden power loss.
"""
import os
from pathlib import Path
import selectors
import signal
import subprocess
import sys
import tempfile

classpath = sys.argv[1]
main = "com.example.markdowndesk.RestartProbe"
points = [f"durable-{i}" for i in range(1, 13)] + [
    "provider-open", "provider-partial", "provider-written", "readback"
]
for point in points:
    with tempfile.TemporaryDirectory(prefix="protected-restart-") as root:
        child = subprocess.Popen(["java", "-cp", classpath, main, "save", root],
                                 stdin=subprocess.PIPE, stdout=subprocess.PIPE,
                                 stderr=subprocess.PIPE, text=True, bufsize=1)
        try:
            with selectors.DefaultSelector() as selector:
                selector.register(child.stdout, selectors.EVENT_READ)
                while True:
                    if not selector.select(timeout=10):
                        raise RuntimeError(f"timeout waiting for {point}")
                    marker = child.stdout.readline().strip()
                    if not marker:
                        raise RuntimeError(f"save exited before {point}: {child.stderr.read()}")
                    if marker == point:
                        os.kill(child.pid, signal.SIGKILL)
                        assert child.wait(timeout=10) == -signal.SIGKILL
                        break
                    if marker not in points:
                        raise RuntimeError(f"unexpected boundary: {marker}")
                    child.stdin.write("continue\n")
                    child.stdin.flush()
            subprocess.run(["java", "-cp", classpath, main, "verify", root, point],
                           check=True, timeout=10)
        finally:
            if child.poll() is None:
                child.kill()
                child.wait(timeout=10)
            for stream in (child.stdin, child.stdout, child.stderr):
                stream.close()
print("PASS 16 production SIGKILL/restart boundaries; host process death, Android/power loss NOT RUN")
