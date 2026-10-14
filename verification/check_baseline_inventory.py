#!/usr/bin/env python3
"""Audit exact baseline bytes, independently of later reviewed runtime changes."""
import argparse
import hashlib
import io
import json
from pathlib import Path
import subprocess
import tarfile
import tempfile

REPO = Path(__file__).resolve().parents[1]
BASELINE_COMMIT = 'd1288a80d4fc174c2b2a62805642218f5fa2d516'

def check(root):
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

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--baseline-ref', choices=['v2.2.5-baseline'])
    args = parser.parse_args()
    if not args.baseline_ref:
        check(REPO)
        return
    commit = subprocess.check_output(['git', 'rev-parse', args.baseline_ref+'^{commit}'], cwd=REPO, text=True).strip()
    if commit != BASELINE_COMMIT:
        raise SystemExit('Baseline tag moved; provenance pin mismatch')
    archive = subprocess.check_output(['git', 'archive', commit], cwd=REPO)
    with tempfile.TemporaryDirectory(prefix='markdown-baseline-proof-') as tmp:
        root = Path(tmp)
        with tarfile.open(fileobj=io.BytesIO(archive)) as tar:
            tar.extractall(root, filter='data')
        check(root)
        rows = json.loads((root/'docs/provenance/2.2.5/inventory.json').read_text())['files']
        expected = {row['repository_path']: row['sha256'] for row in rows if '/assets/' in row['archive_path']}
    # Current assets are still required exact, even on a behavior-changing branch.
    asset_root = REPO/'apps/MarkdownDesk/assets'
    if asset_root.is_symlink():
        raise SystemExit('Symlink current asset root')
    actual = set()
    for path in asset_root.rglob('*'):
        if path.is_symlink():
            raise SystemExit('Symlink current asset entry: '+str(path.relative_to(REPO)))
        if path.is_file():
            actual.add(str(path.relative_to(REPO)))
    if set(expected) != actual:
        raise SystemExit('Current bundled asset inventory differs from cleared baseline')
    for name, digest in expected.items():
        path = REPO/name
        if path.is_symlink() or any(parent.is_symlink() for parent in path.parents if parent != REPO) or hashlib.sha256(path.read_bytes()).hexdigest() != digest:
            raise SystemExit('Current bundled asset digest/type differs: '+name)
    print('PASS immutable source baseline '+commit+' and current exact assets; current runtime changes require diff/tests/review')

if __name__ == '__main__':
    main()
