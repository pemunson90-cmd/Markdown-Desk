#!/usr/bin/env bash
set -euo pipefail
ROOT=$(cd "$(dirname "$0")/../.." && pwd)
python3 - "$ROOT" <<'PY'
import pathlib, subprocess, sys, tempfile
root=pathlib.Path(sys.argv[1])
with tempfile.TemporaryDirectory() as output:
    subprocess.run(['javac','--release','11','-sourcepath',str(root/'apps/MarkdownDesk/src'),'-d',output,str(root/'verification/local_protection_status/LocalProtectionStatusCheck.java')],check=True)
    subprocess.run(['java','-Xmx128m','-cp',output,'com.example.markdowndesk.LocalProtectionStatusCheck'],check=True)
PY
