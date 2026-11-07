#!/usr/bin/env python3
"""Offline invariants for the integrated PR #37 efficiency repairs."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]


def read(path):
    return (ROOT / path).read_text(encoding="utf-8")


class IntegratedEfficiencyTests(unittest.TestCase):
    def test_json_hosted_package_manual_only(self):
        workflow = read(".github/workflows/json-explorer-unsigned.yml")
        self.assertIn("\n  workflow_dispatch:", workflow)
        self.assertNotIn("\n  push:", workflow)
        self.assertIn("markdown-desk-acceptance-${{ github.sha }}-unsigned", workflow)
        self.assertIn("MarkdownDesk-acceptance-UNSIGNED.apk", workflow)
        self.assertIn("cancel-in-progress: true", workflow)
        self.assertIn("persist-credentials: false", workflow)
        self.assertIn("retention-days: 7", workflow)
        self.assertIn("bash verification/document_io/run.sh", workflow)
        self.assertNotIn("json-code32-unsigned", workflow)

    def test_single_authoritative_source_record(self):
        queue = read("docs/handoffs/ACTIVE_UNIFIED_WORKSPACES_QUEUE.md")
        self.assertEqual(1, queue.count("<!-- source-contract:begin -->"))
        self.assertEqual(1, queue.count("<!-- source-contract:end -->"))
        self.assertFalse((ROOT / "docs/CURRENT_SOURCE.json").exists())
        self.assertTrue(read("docs/STATUS.md").startswith("<!-- historical-status-redirect:"))
        guard = read("verification/check_current_source.py")
        self.assertIn("source_record(root)", guard)
        self.assertNotIn("CURRENT_SOURCE.json", guard)

    def test_manifest_checks_keep_historical_allowlist(self):
        for script in ("check_layout.py", "check_live_appearance.py"):
            content = read("verification/" + script)
            self.assertIn("from check_current_source import current_release_pair", content)
            self.assertIn("('18','2.2.5')", content)
            self.assertIn("('30','2.3.2-emaillinks1')", content)
            self.assertIn("current_release_pair(root)", content)

    def test_single_downloads_invocation_and_guard_registration(self):
        for name in ("run_host_checks.sh", "run_current_phone_checks.sh"):
            runner = read("verification/" + name)
            self.assertEqual(1, runner.count("bash verification/downloads_browser/run.sh"), name)
            self.assertEqual(1, runner.count("python3 verification/check_current_source.py"), name)
            self.assertEqual(1, runner.count("bash verification/source_extraction/run.sh"), name)
            self.assertEqual(1, runner.count("test_current_source_guard.py"), name)
            self.assertEqual(1, runner.count("test_pr37_integration.py"), name)

    def test_renderer_host_compilers_include_internal_link_dependency(self):
        for runner in sorted((ROOT / "verification").rglob("*.sh")):
            content = runner.read_text(encoding="utf-8")
            if "MarkdownRenderer.java" in content and "javac" in content:
                self.assertIn("workspace/WorkspaceDeepLinks.java", content,
                              "renderer compile missing current link validator: " + str(runner))

    def test_deferred_portable_tests_remain_visible(self):
        phone = read("verification/run_current_phone_checks.sh")
        self.assertEqual(18, phone.count("OWNER-DEFERRED PORTABLE TEST:"))
        for name in ("private_archive", "private_state", "cold_capture_cancel"):
            self.assertIn("OWNER-DEFERRED PORTABLE TEST: " + name, phone)

    def test_shared_ast_parser_preserves_extractor_contracts(self):
        for script in ("verification/preview_behavior/ExtractPreview.java",
                       "verification/status_behavior/ExtractStatus.java"):
            content = read(script)
            self.assertIn("ParsedJavaSource.parse(source)", content)
            self.assertTrue("parsed.raw" in content or "parsed::raw" in content,
                            "extractor must use shared exact-source slicer")
            self.assertNotIn("ToolProvider.getSystemJavaCompiler()", content)
        for path in ("verification/preview_behavior/run.sh",
                     "verification/preview_behavior/run_benchmark.sh",
                     "verification/status_behavior/run.sh"):
            self.assertIn("verification/source_extraction/ParsedJavaSource.java", read(path))
        self.assertIn("--release 11", read("verification/source_extraction/run.sh"))


if __name__ == "__main__":
    unittest.main(verbosity=2)
