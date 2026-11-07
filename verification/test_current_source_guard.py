#!/usr/bin/env python3
"""Isolated behavior tests: no network, Android SDK, or user documents required."""
import importlib.util
import json
from pathlib import Path
import subprocess
import tempfile
import unittest

SCRIPT = Path(__file__).with_name('check_current_source.py')
spec = importlib.util.spec_from_file_location('current_source_guard', SCRIPT)
guard = importlib.util.module_from_spec(spec)
spec.loader.exec_module(guard)


class GuardTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory(prefix='md-guard-fixture-')
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name)
        self.git('init', '-q', '-b', 'integration/work')
        self.git('config', 'user.email', 'fixture@example.invalid')
        self.git('config', 'user.name', 'Fixture')
        manifest = self.root / guard.MANIFEST
        manifest.parent.mkdir(parents=True)
        manifest.write_text('<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="example.app" android:versionCode="38" android:versionName="candidate"><uses-sdk android:minSdkVersion="35" android:targetSdkVersion="37"/></manifest>')
        self.git('add', '.')
        self.git('commit', '-qm', 'base')
        self.anchor = self.git('rev-parse', 'HEAD')
        self.state = {'schema_version': 1, 'development': {'branch': 'integration/work', 'anchor_commit': self.anchor, 'manifest': {'package': 'example.app', 'version_code': 38, 'version_name': 'candidate', 'min_sdk': 35, 'target_sdk': 37}}, 'installed': {'status': 'unconfirmed', 'source_commit': None, 'apk_sha256': None, 'certificate_sha256': None, 'owner_accepted': False, 'evidence': None}, 'evidence': ['docs/development/source.md']}
        p = self.root / 'docs/development/source.md'
        p.parent.mkdir(parents=True)
        p.write_text('test source\n')
        self.queue = self.root / guard.QUEUE
        self.queue.parent.mkdir(parents=True)
        self.save()
        self.git('add', '.')
        self.git('commit', '-qm', 'queue')
        self.git('update-ref', 'refs/remotes/origin/integration/work', 'HEAD')

    def git(self, *args):
        return subprocess.check_output(('git', '-C', str(self.root)) + args, stderr=subprocess.STDOUT, text=True).strip()

    def save(self):
        self.queue.write_text('# Single queue\n' + guard.OPEN + '\n```json\n' + json.dumps(self.state, indent=2) + '\n```\n' + guard.CLOSE + '\nOther tasks remain here.\n')

    def err(self, **kw):
        return '\n'.join(guard.check(self.root, **kw)[0])

    def test_accepts_descendant_without_install_claim(self):
        self.assertEqual('', self.err())
        self.assertIn('unconfirmed', ' '.join(guard.check(self.root)[1]))

    def test_rejects_missing_remote_ref(self):
        self.git('update-ref', '-d', 'refs/remotes/origin/integration/work')
        self.assertIn('missing fetched', self.err())

    def test_rejects_new_remote_commit_on_old_checkout(self):
        self.git('update-ref', 'refs/remotes/origin/integration/work', self.anchor)
        old = self.git('rev-parse', 'HEAD')
        (self.root / 'new').write_text('new')
        self.git('add', 'new')
        self.git('commit', '-qm', 'new')
        new = self.git('rev-parse', 'HEAD')
        self.git('checkout', '-q', '--detach', old)
        self.git('update-ref', 'refs/remotes/origin/integration/work', new)
        self.assertIn('stale checkout', self.err())

    def test_rejects_unrelated_anchor(self):
        self.state['development']['anchor_commit'] = 'f' * 40
        self.save()
        self.assertIn('anchor', self.err())

    def test_rejects_manifest_drift(self):
        p = self.root / guard.MANIFEST
        p.write_text(p.read_text().replace('versionCode="38"', 'versionCode="39"'))
        self.assertIn('manifest', self.err())

    def test_rejects_missing_or_duplicate_queue_record(self):
        self.queue.write_text('# missing')
        self.assertIn('exactly one', self.err())
        self.save()
        self.queue.write_text(self.queue.read_text() * 2)
        self.assertIn('exactly one', self.err())

    def test_rejects_missing_evidence(self):
        self.state['evidence'] = ['docs/missing.md']
        self.save()
        self.assertIn('missing/unsafe', self.err())

    def test_warns_dirty_without_deleting(self):
        p = self.root / 'draft.md'
        p.write_text('my unsaved work')
        self.assertEqual('', self.err())
        self.assertIn('dirty worktree', ' '.join(guard.check(self.root)[1]))
        self.assertIn('clean checkout', self.err(require_clean=True))
        self.assertEqual('my unsaved work', p.read_text())

    def test_release_fails_unconfirmed_install(self):
        self.assertIn('owner-accepted', self.err(require_installed=True))

    def test_verified_requires_real_signature_and_owner_approval(self):
        self.state['installed'] = {'status': 'verified', 'source_commit': self.anchor, 'apk_sha256': 'f' * 64, 'certificate_sha256': '0' * 64, 'owner_accepted': False, 'evidence': 'docs/development/source.md'}
        self.save()
        errors = self.err(require_installed=True)
        self.assertIn('signing certificate', errors)
        self.assertIn('owner acceptance', errors)

    def test_verified_provenance_accepts_valid_fixture(self):
        self.state['installed'] = {'status': 'verified', 'source_commit': self.anchor, 'apk_sha256': 'f' * 64, 'certificate_sha256': guard.CANONICAL_CERT, 'owner_accepted': True, 'evidence': 'docs/development/source.md'}
        self.save()
        self.assertEqual('', self.err(require_installed=True))

    def test_candidate_release_pair_comes_from_queue(self):
        self.assertEqual(('38', 'candidate'), guard.current_release_pair(self.root))

    def test_release_pair_rejects_manifest_drift(self):
        p = self.root / guard.MANIFEST
        p.write_text(p.read_text().replace('versionCode="38"', 'versionCode="41"'))
        with self.assertRaisesRegex(ValueError, 'manifest'):
            guard.current_release_pair(self.root)

    def test_guard_is_read_only(self):
        before = self.queue.read_bytes()
        self.assertEqual('', self.err())
        self.assertEqual(before, self.queue.read_bytes())


if __name__ == '__main__':
    unittest.main(verbosity=2)
