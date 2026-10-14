#!/usr/bin/env python3
"""Host feasibility probe only: real fsync/rename, synthetic data, no app code."""
import hashlib
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

PAYLOAD = b"\xef\xbb\xbf# synthetic \xf0\x9f\xa7\xad\r\n"
BOUNDARIES = ("staged", "file-synced", "renamed", "directory-synced", "validated")


def envelope(payload=PAYLOAD):
    return json.dumps({"version": 1, "length": len(payload),
        "sha256": hashlib.sha256(payload).hexdigest(), "hex": payload.hex()},
        sort_keys=True).encode("ascii")


def validate(path):
    record = json.loads(path.read_bytes())
    if record["version"] != 1:
        raise ValueError("unsupported version")
    payload = bytes.fromhex(record["hex"])
    if len(payload) != record["length"] or hashlib.sha256(payload).hexdigest() != record["sha256"]:
        raise ValueError("envelope length/hash mismatch")
    return payload


def commit(root, payload=PAYLOAD, *, failure=None, boundary=lambda name: None):
    """One synthetic envelope; failure arguments inject adapter errors, not OS faults."""
    staged, committed = root / "envelope.stage", root / "envelope.json"
    with staged.open("xb") as stream:
        stream.write(envelope(payload))
        stream.flush()
        boundary("staged")
        if failure == "file-sync":
            raise OSError("injected file-sync adapter failure")
        os.fsync(stream.fileno())
        boundary("file-synced")
    if failure == "rename":
        raise OSError("injected rename adapter failure")
    # Same-directory rename; destination does not exist in this unique tempdir.
    os.rename(staged, committed)
    boundary("renamed")
    descriptor = os.open(root, os.O_RDONLY | os.O_DIRECTORY)
    try:
        if failure == "directory-sync":
            raise OSError("injected directory-sync adapter failure")
        os.fsync(descriptor)
    finally:
        os.close(descriptor)
    boundary("directory-synced")
    if failure == "validation":
        committed.write_bytes(b"injected corrupt envelope")
    if validate(committed) != payload:
        raise ValueError("committed payload changed")
    boundary("validated")
    return committed


def child(root):
    def announce(name):
        print(name, flush=True)
        # Parent explicitly acknowledges each boundary or SIGKILLs this child.
        if sys.stdin.readline() != "continue\n":
            raise RuntimeError("missing parent acknowledgement")
    commit(Path(root), boundary=announce)


def fresh_process_validation(path):
    completed = subprocess.run([sys.executable, __file__, "--validate", str(path)],
                               capture_output=True, text=True, check=False)
    return completed.returncode == 0


class Probe(unittest.TestCase):
    def test_real_commit_and_fresh_process_hash_validation(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            path = commit(root)
            self.assertEqual(validate(path), PAYLOAD)
            self.assertTrue(fresh_process_validation(path))
            self.assertFalse((root / "envelope.stage").exists())
            print("OBSERVED host file fsync + same-directory rename + directory fsync succeeded", flush=True)

    def test_selected_process_kill_boundaries(self):
        for stop in BOUNDARIES:
            with self.subTest(boundary=stop), tempfile.TemporaryDirectory() as directory:
                root = Path(directory)
                process = subprocess.Popen([sys.executable, __file__, "--child", directory],
                    stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)
                try:
                    for boundary in BOUNDARIES:
                        self.assertEqual(process.stdout.readline().strip(), boundary)
                        if boundary == stop:
                            process.kill()  # Real SIGKILL, not a simulated restart.
                            process.wait(timeout=5)
                            self.assertLess(process.returncode, 0)
                            break
                        process.stdin.write("continue\n")
                        process.stdin.flush()
                    committed = root / "envelope.json"
                    staged = root / "envelope.stage"
                    # Linux process termination leaves the running kernel/cache intact.
                    if stop in ("staged", "file-synced"):
                        self.assertFalse(committed.exists())
                        self.assertTrue(staged.exists())
                    else:
                        self.assertTrue(fresh_process_validation(committed))
                        self.assertFalse(staged.exists())
                    print("OBSERVED SIGKILL boundary=" + stop + "; committed=" + str(committed.exists()), flush=True)
                finally:
                    if process.poll() is None:
                        process.kill()
                        process.wait(timeout=5)
                    process.stdin.close()
                    process.stdout.close()
                    process.stderr.close()

    def test_injected_failures_block_mutation(self):
        for failure in ("file-sync", "rename", "directory-sync", "validation"):
            with self.subTest(failure=failure), tempfile.TemporaryDirectory() as directory:
                root = Path(directory)
                provider = root / "synthetic-provider.md"
                provider.write_bytes(b"synthetic original\r\n")
                writes = 0
                with self.assertRaises((OSError, ValueError)):
                    commit(root, failure=failure)
                    # Reachable only after protection successfully returns.
                    writes += 1
                    provider.write_bytes(b"synthetic replacement")
                self.assertEqual(writes, 0)
                self.assertEqual(provider.read_bytes(), b"synthetic original\r\n")

    def test_corrupt_or_missing_committed_envelope_rejected(self):
        for fault in ("missing", "truncated", "payload", "hash", "length"):
            with self.subTest(fault=fault), tempfile.TemporaryDirectory() as directory:
                path = commit(Path(directory))
                if fault == "missing":
                    path.unlink()
                elif fault == "truncated":
                    path.write_bytes(b"{")
                else:
                    record = json.loads(path.read_bytes())
                    field, value = {"payload": ("hex", b"corrupt".hex()),
                                    "hash": ("sha256", "0" * 64),
                                    "length": ("length", 999)}[fault]
                    record[field] = value
                    path.write_text(json.dumps(record), encoding="ascii")
                self.assertFalse(fresh_process_validation(path))


if __name__ == "__main__":
    if len(sys.argv) == 3 and sys.argv[1] == "--child":
        child(sys.argv[2])
    elif len(sys.argv) == 3 and sys.argv[1] == "--validate":
        try:
            if validate(Path(sys.argv[2])) != PAYLOAD:
                raise ValueError("unexpected fixture bytes")
        except (OSError, ValueError, KeyError, TypeError):
            sys.exit(1)
    else:
        print("HOST FILESYSTEM PROBE ONLY — process kill is not power-loss or Android durability proof", flush=True)
        unittest.main(verbosity=2)
