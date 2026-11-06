#!/usr/bin/env python3
"""Regression checks for current-source selection and deliberately triggered CI."""
import importlib.util
import json
from pathlib import Path
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]
SCRIPT = ROOT / "verification/check_current_source.py"

class CurrentSourceTests(unittest.TestCase):
    def setUp(self):
        self.assertTrue(SCRIPT.is_file(), "current-source guard is not implemented")
        spec = importlib.util.spec_from_file_location("current_source", SCRIPT)
        self.guard = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(self.guard)
        self.root = Path(tempfile.mkdtemp(prefix="markdown-source-test-"))
        self.git("init", "-q", "-b", "work")
        self.git("config", "user.email", "fixture@example.invalid")
        self.git("config", "user.name", "Fixture")
        path = self.root / self.guard.MANIFEST
        path.parent.mkdir(parents=True)
        path.write_text('<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="example.app" android:versionCode="38" android:versionName="candidate"/>')
        self.git("add", ".")
        self.git("commit", "-qm", "baseline")
        sha = self.git("rev-parse", "HEAD")
        self.state = {"schema_version": 1, "development": {"branch": "work", "anchor_commit": sha, "manifest": {"package": "example.app", "version_code": 38, "version_name": "candidate"}}, "installed": {"status": "unconfirmed", "commit": None}, "execution": {"status": "unknown", "observed_utc": "2026-11-06T20:58:43Z"}, "evidence": ["docs/evidence.md"], "next_action": "Continue only owned work; retain all final gates."}
        (self.root / "docs").mkdir()
        (self.root / "docs/evidence.md").write_text("Historical evidence retained.\n")
        self.git("update-ref", "refs/remotes/origin/work", sha)
        self.save()

    def git(self, *args):
        return subprocess.check_output(["git", "-C", str(self.root), *args], text=True, stderr=subprocess.STDOUT).strip()

    def save(self):
        (self.root / self.guard.STATE).write_text(json.dumps(self.state))
        (self.root / "docs/STATUS.md").write_text(self.guard.render_status(self.state) + "\n## Historical checkpoints\nOriginal text.\n")

    def test_accepts_descendant_without_claiming_device_acceptance(self):
        errors, notices = self.guard.check(self.root)
        self.assertEqual([], errors)
        self.assertTrue(any("unconfirmed" in n for n in notices))

    def test_manifest_version_drift_is_blocking(self):
        p = self.root / self.guard.MANIFEST
        p.write_text(p.read_text().replace('versionCode="38"', 'versionCode="39"'))
        self.assertTrue(any("manifest" in e for e in self.guard.check(self.root)[0]))

    def test_unrelated_anchor_is_blocking(self):
        self.state["development"]["anchor_commit"] = "f" * 40
        self.save()
        self.assertTrue(any("anchor" in e for e in self.guard.check(self.root)[0]))

    def test_newer_fetched_integration_head_is_blocking(self):
        (self.root / "next").write_text("new work")
        self.git("add", "next"); self.git("commit", "-qm", "next")
        ahead = self.git("rev-parse", "HEAD")
        self.git("update-ref", "refs/remotes/origin/work", ahead)
        self.git("checkout", "--detach", self.state["development"]["anchor_commit"])
        self.assertTrue(any("fetched integration" in e for e in self.guard.check(self.root)[0]))

    def test_status_drift_is_blocking(self):
        (self.root / "docs/STATUS.md").write_text("Old code26 is current")
        self.assertTrue(any("status" in e for e in self.guard.check(self.root)[0]))

    def test_missing_linked_evidence_is_blocking(self):
        self.state["evidence"] = ["docs/missing.md"]
        self.save()
        self.assertTrue(any("evidence" in e for e in self.guard.check(self.root)[0]))

    def test_device_gate_requires_verified_installed_identity(self):
        self.assertTrue(any("installed" in e for e in self.guard.check(self.root, require_installed=True)[0]))

    def test_verified_installed_commit_must_be_preserved(self):
        self.state["installed"] = {"status": "verified", "commit": "f" * 40}
        self.save()
        self.assertTrue(any("installed" in e for e in self.guard.check(self.root)[0]))

class WorkflowTests(unittest.TestCase):
    def test_current_candidate_passes_existing_identity_guards(self):
        for script in ("check_layout.py", "check_live_appearance.py"):
            result = subprocess.run(["python3", str(ROOT / "verification" / script)], capture_output=True, text=True)
            self.assertEqual(0, result.returncode, result.stderr)

    def test_json_acceptance_has_no_push_trigger_and_keeps_manual_gate(self):
        text = (ROOT / ".github/workflows/json-explorer-unsigned.yml").read_text()
        self.assertNotIn("\n  push:", text)
        self.assertIn("\n  workflow_dispatch:", text)
        self.assertIn("bash verification/document_io/run.sh", text)
        self.assertIn("zipalign", text)

if __name__ == "__main__":
    unittest.main()
