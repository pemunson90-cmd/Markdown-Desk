#!/usr/bin/env python3
"""No APK until the complete feature list, local gates and original signer are verified."""
from pathlib import Path
import unittest

ROOT=Path(__file__).resolve().parents[1]
RELEASE=ROOT/"verification/first_signed_phone_candidate.sh"

class SignedPhoneGate(unittest.TestCase):
    @classmethod
    def setUpClass(cls):cls.script=RELEASE.read_text(encoding="utf-8")
    def test_all_feature_sections_required_before_packaging(self):
        s=self.script
        self.assertIn("for x in range(1,9)",s)
        self.assertIn("if rows[k] not in allowed",s)
        self.assertIn("'FINAL CHECKS REQUIRED' if k=='T08' else 'SOURCE COMPLETE'",s)
        self.assertIn("Incomplete authoritative pre-APK feature checklist",s)
        self.assertIn('git branch --show-current',s)
        self.assertIn('git status --porcelain',s)
    def test_exact_original_certificate_before_build(self):
        s=self.script
        self.assertIn("a163cea419108eb595f3dcb01de16feca878368d4ffc6efc58eb9a8358b6be01",s)
        self.assertIn('keytool -list -v',s)
        self.assertIn('Wrong signing certificate',s)
        self.assertLess(s.index('Wrong signing certificate'),s.index('bash build.sh "$name"'))
        self.assertIn("Signer #1 certificate SHA-256 digest:",s)
        self.assertIn("Increase final versionCode above 38",s)
    def test_source_then_host_then_first_package(self):
        s=self.script
        self.assertLess(s.index('bash verification/run_current_phone_checks.sh'),
                        s.index('bash verification/compile_native_sources_only.sh'))
        self.assertLess(s.index('bash verification/compile_native_sources_only.sh'),
                        s.index('bash build.sh "$name"'))
        self.assertLess(s.index('bash verification/compile_native_sources_only.sh'),s.index('PRE-APK PASS: complete host'))
        self.assertIn('export ANDROID_BUILD_TOOLS="${ANDROID_BUILD_TOOLS:-36.1.0}"',s)
        self.assertNotIn('gh workflow',s)
        self.assertNotIn('git push',s)
        self.assertNotIn('gh release create',s)

if __name__=="__main__":unittest.main()
