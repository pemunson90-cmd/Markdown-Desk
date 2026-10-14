#!/usr/bin/env python3
"""Baseline audit must reject extra or substituted runtime inputs."""
import io
import tarfile
import hashlib
import json
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest

SCRIPT = Path(__file__).with_name('check_baseline_inventory.py')

class InventorySafetyTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.root = Path(self.tmp.name)
        (self.root / 'verification').mkdir()
        shutil.copyfile(SCRIPT, self.root / 'verification/check_baseline_inventory.py')
        meta = self.root / 'docs/provenance/2.2.5'
        meta.mkdir(parents=True)
        path = 'apps/MarkdownDesk/src/example.java'
        p = self.root / path
        p.parent.mkdir(parents=True)
        p.write_bytes(b'baseline bytes')
        (meta / 'inventory.json').write_text(json.dumps({'files': [{'archive_path': path, 'repository_path': path, 'sha256': hashlib.sha256(p.read_bytes()).hexdigest()}]}))
    def tearDown(self):
        self.tmp.cleanup()
    def run_check(self):
        return subprocess.run(['python3', str(self.root / 'verification/check_baseline_inventory.py')], capture_output=True, text=True)
    def test_exact_inputs_pass(self):
        self.assertEqual(self.run_check().returncode, 0)
    def test_additional_source_fails(self):
        (self.root / 'apps/MarkdownDesk/src/extra.java').write_text('new runtime code')
        self.assertNotEqual(self.run_check().returncode, 0, 'Extra Java enters APK; must reject')
    def test_additional_asset_fails(self):
        p = self.root / 'apps/MarkdownDesk/assets/private.txt'
        p.parent.mkdir(parents=True)
        p.write_text('unapproved asset')
        self.assertNotEqual(self.run_check().returncode, 0, 'Extra packaged asset must reject')
    def test_modified_source_fails(self):
        (self.root / 'apps/MarkdownDesk/src/example.java').write_text('changed')
        self.assertNotEqual(self.run_check().returncode, 0)
    def test_missing_source_fails(self):
        (self.root / 'apps/MarkdownDesk/src/example.java').unlink()
        self.assertNotEqual(self.run_check().returncode, 0)
    def test_symlink_substitution_fails(self):
        p = self.root / 'apps/MarkdownDesk/src/example.java'
        external = self.root / 'external.java'
        external.write_bytes(p.read_bytes())
        p.unlink(); p.symlink_to(external)
        self.assertNotEqual(self.run_check().returncode, 0, 'Digest alone does not make a symlink an exact import')

class PinnedAssetSafetyTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.root = Path(self.tmp.name)
        repo = SCRIPT.resolve().parents[1]
        archive = subprocess.check_output(['git', 'archive', 'v2.2.5-baseline'], cwd=repo)
        with tarfile.open(fileobj=io.BytesIO(archive)) as tar:
            tar.extractall(self.root, filter='data')
        shutil.copyfile(SCRIPT, self.root / 'verification/check_baseline_inventory.py')
        subprocess.run(['git', 'init', '--quiet', str(self.root)], check=True)
        subprocess.run(['git', '-C', str(self.root), 'fetch', '--quiet', str(repo), 'refs/tags/v2.2.5-baseline:refs/tags/v2.2.5-baseline'], check=True)
    def tearDown(self):
        self.tmp.cleanup()
    def run_check(self):
        return subprocess.run(['python3', str(self.root / 'verification/check_baseline_inventory.py'), '--baseline-ref', 'v2.2.5-baseline'], capture_output=True, text=True)
    def test_pinned_exact_assets_pass(self):
        self.assertEqual(self.run_check().returncode, 0)
    def test_coordinated_asset_and_mutable_inventory_change_fails(self):
        p = next((self.root / 'apps/MarkdownDesk/assets/splash').glob('*.webp'))
        p.write_bytes(b'changed artwork')
        inventory = self.root / 'docs/provenance/2.2.5/inventory.json'
        data = json.loads(inventory.read_text())
        for row in data['files']:
            if row['repository_path'] == str(p.relative_to(self.root)):
                row['sha256'] = hashlib.sha256(p.read_bytes()).hexdigest()
        inventory.write_text(json.dumps(data))
        self.assertNotEqual(self.run_check().returncode, 0, 'Current mutable inventory must not redefine cleared baseline')
    def test_additional_dangling_asset_symlink_fails(self):
        (self.root / 'apps/MarkdownDesk/assets/dangling').symlink_to('missing')
        self.assertNotEqual(self.run_check().returncode, 0, 'Dangling symlink must reject')
    def test_additional_asset_directory_symlink_fails(self):
        (self.root / 'apps/MarkdownDesk/assets/linked').symlink_to(self.root / 'docs', target_is_directory=True)
        self.assertNotEqual(self.run_check().returncode, 0, 'Directory symlink must reject')

if __name__ == '__main__':
    unittest.main()
