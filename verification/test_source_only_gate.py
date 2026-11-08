#!/usr/bin/env python3
"""Make source verification incapable of accidentally becoming an APK build."""
from pathlib import Path
import unittest

ROOT=Path(__file__).resolve().parents[1]
SCRIPT=ROOT/"verification/compile_native_sources_only.sh"

class SourceOnlyGate(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.content=SCRIPT.read_text(encoding="utf-8")

    def test_real_android_source_pipeline(self):
        c=self.content
        self.assertIn('"$BT/aapt2" compile',c)
        self.assertIn('"$BT/aapt2" link',c)
        self.assertIn('javac --release 11',c)
        self.assertIn('"$BT/d8" --min-api',c)
        self.assertIn('android-37.0',c)
        self.assertIn('root.attrib[\'package\']!=\'com.example.markdowndesk\'',c)
        self.assertIn('minimum!=35 or target!=37',c)
        self.assertIn('len(assets)!=25',c)

    def test_no_apk_assembly_signing_or_ci(self):
        c=self.content
        self.assertIn('trap \'rm -rf -- "$TMP"\' EXIT',c)
        self.assertIn('"$TMP/compiled/resources.ap_"',c)
        self.assertNotIn("zipalign",c)
        self.assertNotIn("apksigner",c)
        self.assertNotIn("classes*.dex)",c) # no ZIP insertion of DEX into a package
        self.assertNotIn('build.sh --unsigned',c)
        self.assertNotIn('gh workflow',c)
        self.assertNotIn('MARKDOWN_DESK_KEYSTORE',c)

if __name__=="__main__":unittest.main()
