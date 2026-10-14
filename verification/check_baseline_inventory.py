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
    if not path.is_file() or path.is_symlink() or any(parent.is_symlink() for parent in path.parents if parent != root):
        raise SystemExit(f'Missing or non-regular baseline input: {path.relative_to(root)}')
    if hashlib.sha256(path.read_bytes()).hexdigest() != row['sha256']:
        raise SystemExit(f'Baseline digest mismatch: {row["archive_path"]}')
    exact += row['archive_path'] not in scaffolding
runtime_roots = ('apps/MarkdownDesk/src/', 'apps/MarkdownDesk/res/',
                 'apps/MarkdownDesk/assets/', 'shared/')
expected = {row['repository_path'] for row in rows
            if row['archive_path'].startswith(runtime_roots)}
actual = set()
for directory in runtime_roots:
    base = root / directory
    if base.is_symlink():
        raise SystemExit(f'Symlink runtime directory: {directory}')
    for path in base.rglob('*'):
        if path.is_symlink():
            raise SystemExit(f'Symlink runtime input: {path.relative_to(root)}')
        if path.is_file():
            actual.add(str(path.relative_to(root)))
if expected != actual:
    raise SystemExit(f'Runtime inventory mismatch: missing={sorted(expected-actual)}, extra={sorted(actual-expected)}')
print(f'PASS baseline inventory: {len(rows)} archive files, {exact} unchanged imports, {len(rows)-exact} documented scaffolding originals preserved')
