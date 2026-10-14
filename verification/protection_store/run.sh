#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
OUT="$ROOT/verification/build/protection-store"
mkdir -p "$OUT/classes" "$OUT/tmp"
javac --release 11 -d "$OUT/classes" \
 "$ROOT/apps/MarkdownDesk/src/com/example/markdowndesk/ProtectionStore.java" \
 "$ROOT/verification/protection_store/ProtectionStoreCheck.java"
java -Djava.io.tmpdir="$OUT/tmp" -cp "$OUT/classes" com.example.markdowndesk.ProtectionStoreCheck
python3 - "$OUT" <<'PY'
import os, resource, signal, subprocess, sys
out = sys.argv[1]
def restrict_writes():
    signal.signal(signal.SIGXFSZ, signal.SIG_IGN)
    resource.setrlimit(resource.RLIMIT_FSIZE, (4096, 4096))
subprocess.run(["java", "-Djava.io.tmpdir=" + out + "/tmp", "-cp", out + "/classes",
                "com.example.markdowndesk.ProtectionStoreCheck", "write-failure"],
               check=True, preexec_fn=restrict_writes)
PY
