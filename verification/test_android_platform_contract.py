#!/usr/bin/env python3
"""Offline Android platform/build contract; never builds or signs an APK."""
from pathlib import Path
import re
import unittest
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
BUILD = ROOT / "apps/MarkdownDesk/build.sh"
MANIFEST = ROOT / "apps/MarkdownDesk/AndroidManifest.xml"
DOC = ROOT / "docs/BUILD.md"
ANDROID = "{http://schemas.android.com/apk/res/android}"

class ToolchainContract(unittest.TestCase):
    def test_documented_platform_matches_default_with_override(self):
        script = BUILD.read_text(encoding="utf-8")
        docs = DOC.read_text(encoding="utf-8")
        self.assertIn('ANDROID_PLATFORM="${ANDROID_PLATFORM:-android-37.0}"', script)
        self.assertIn('ANDROID_PLATFORM=android-37.0', docs)
        self.assertIn('ANDROID_JAR="$ANDROID_SDK_ROOT/platforms/$ANDROID_PLATFORM/android.jar"', script)
        self.assertIn('"$BT/aapt2"', script)
        self.assertIn('"$BT/d8"', script)
        self.assertIn('"$BT/zipalign"', script)
        self.assertNotIn('ANDROID_PLATFORM="${ANDROID_PLATFORM:-android-37}"', script)

    def test_manifest_identity_stays_one_source_of_truth(self):
        manifest = ET.parse(MANIFEST).getroot()
        sdk = manifest.find("uses-sdk")
        self.assertEqual(manifest.get("package"), "com.example.markdowndesk")
        self.assertEqual(int(manifest.get(ANDROID + "versionCode")), 39)
        self.assertEqual(manifest.get(ANDROID + "versionName"), "2.5.0-unified-workspaces")
        self.assertEqual(int(sdk.get(ANDROID + "minSdkVersion")), 35)
        self.assertEqual(int(sdk.get(ANDROID + "targetSdkVersion")), 37)

    def test_unsigned_validation_keeps_original_signer_private(self):
        script = BUILD.read_text(encoding="utf-8")
        self.assertIn('if [[ "${1:-}" == --unsigned ]]; then UNSIGNED=true', script)
        self.assertIn('if ! $UNSIGNED; then', script)
        self.assertIn('MARKDOWN_DESK_KEYSTORE', script)
        self.assertIn('apksigner', script)
        self.assertIn('javac --release 11', script)
        self.assertIn('if len(assets) != 25:', script)
        self.assertNotIn('keytool -genkey', script)

if __name__ == "__main__":
    unittest.main(verbosity=2)
