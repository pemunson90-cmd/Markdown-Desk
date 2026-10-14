#!/usr/bin/env python3
"""Check exact baseline bytes, allowing only documented build/test scaffolding."""
import hashlib
import json
from pathlib import Path

root = Path(__file__).resolve().parents[1]
meta = root / 'docs/provenance/2.2.5'
rows = json.loads((meta / 'inventory.json').read_text())['files']
scaffolding = {'apps/MarkdownDesk/build.sh', 'apps/MarkdownDesk/test/SplashStoreTest.java',
               'verification/run_host_checks.sh', 'verification/compile_unsigned.sh'}
exact = 0
for row in rows:
    path = root / row['repository_path']
    if row['archive_path'] in scaffolding:
        path = meta / 'original-scaffolding' / row['archive_path']
    assert path.is_file(), f'Missing baseline input: {path.relative_to(root)}'
    assert hashlib.sha256(path.read_bytes()).hexdigest() == row['sha256'], row['archive_path']
    exact += row['archive_path'] not in scaffolding
print(f'PASS baseline inventory: {len(rows)} archive files, {exact} unchanged imports, {len(scaffolding)} documented scaffolding originals preserved')
