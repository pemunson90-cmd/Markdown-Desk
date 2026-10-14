#!/usr/bin/env python3
"""Baseline audit must reject extra or substituted runtime inputs."""
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

if __name__ == '__main__':
    unittest.main()
