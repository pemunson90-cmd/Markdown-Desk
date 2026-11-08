#!/usr/bin/env python3
"""Static Android wiring contract for hash-bound JSON search -> exact node navigation."""
from pathlib import Path
import unittest

ROOT=Path(__file__).resolve().parents[1]
JAVA=ROOT/"apps/MarkdownDesk/src/com/example/markdowndesk"

class JsonNodeNavigation(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.raw=(JAVA/"JsonRawSearchHit.java").read_text(encoding="utf-8")
        cls.locator=(JAVA/"JsonNodeLocator.java").read_text(encoding="utf-8")
        cls.inspector=(JAVA/"JsonExplorerActivity.java").read_text(encoding="utf-8")
        cls.search=(JAVA/"UnifiedSearchActivity.java").read_text(encoding="utf-8")

    def test_immutable_verified_raw_source_only(self):
        self.assertIn("JsonNodeLocator.locate(text,offset,literal.length())",self.raw)
        self.assertIn("catch(IOException malformedOrAmbiguous)",self.raw)
        self.assertIn("keepSnapshot?parsed.bytes:null",self.raw)
        self.assertIn("sourceSha,parsed.sha256",self.raw)
        self.assertIn("MAX_BYTES=8*1024*1024",self.raw)

    def test_narrow_bounded_parser_and_unambiguous_identity(self):
        s=self.locator
        self.assertIn("MAX_DEPTH=64,MAX_KEY=512,MAX_OBJECT_KEYS=4096",s)
        self.assertIn("if(keys.size()>=MAX_OBJECT_KEYS||!keys.add(key))",s)
        self.assertIn("if(from>=first&&until<=end)",s)
        self.assertIn("parser.at!=source.length()",s)
        self.assertIn("Collections.unmodifiableList",s)

    def test_exact_search_routes_to_inspector(self):
        s=self.search
        self.assertIn("if(hit.file.json)openJsonHit(hit.file,hit.match,hit.term)",s)
        self.assertIn("UnifiedSearchJump.EXTRA_HASH,file.hash",s)
        self.assertIn("UnifiedSearchJump.EXTRA_OFFSET,match.offsetUtf16",s)
        self.assertIn("JsonExplorerActivity.class",s)

    def test_explicit_node_click_reverifies_before_navigation(self):
        s=self.inspector
        self.assertIn('dialog.setPositiveButton("Jump to exact JSON node"',s)
        self.assertIn("JsonRawSearchHit.verifyWithSnapshot(",s)
        self.assertIn("new ByteArrayInputStream(verified.snapshot)",s)
        self.assertIn("expectedRevision!=revision||expectedRequest!=rawHitRequest",s)
        self.assertIn("path.clear();path.addAll(target);offset=0",s)
        self.assertIn("displayPage(next,target,0,token)",s)
        self.assertIn('else dialog.setPositiveButton("Inspect raw passage",null)',s)

    def test_back_to_results_preserves_json_session(self):
        s=self.inspector
        self.assertIn('returnResults=button("Return to search results",v->finish())',s)
        self.assertIn("jsonOpenedFromSearch",s)
        self.assertIn("if(!openedFromSearch)closeInspectorTabForSwitch()",s)
        self.assertIn("setIntent(intent); // Never replace the current intent when tab admission failed.",s)

if __name__=="__main__":
    unittest.main()
