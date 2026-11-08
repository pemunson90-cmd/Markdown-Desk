#!/usr/bin/env python3
"""Current general JSON Find must be source-exact, bounded and truthful."""
from pathlib import Path
import unittest

ROOT=Path(__file__).resolve().parents[1]
APP=ROOT/"apps/MarkdownDesk/src/com/example/markdowndesk"

class CurrentJsonFind(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.activity=(APP/"JsonExplorerActivity.java").read_text(encoding="utf-8")
        cls.engine=(APP/"JsonRawSearchHit.java").read_text(encoding="utf-8")
        cls.fixture=(ROOT/"verification/json_explorer/JsonRawSearchHitCheck.java").read_text(encoding="utf-8")

    def test_engine_shared_with_source_hash_gate(self):
        s=self.engine
        self.assertIn("static SearchResult search(InputStream stream,String literal)",s)
        self.assertIn("Snapshot source=read(stream)",s)
        self.assertIn("Snapshot parsed=read(stream)",s)
        self.assertIn("MAX_BYTES=8*1024*1024",s)
        self.assertIn("MAX_HITS=500",s)
        self.assertIn("text.indexOf(literal",s)
        self.assertIn("return new SearchResult(source.sha256,matches,position>=0)",s)
        self.assertIn("onMalformedInput(CodingErrorAction.REPORT)",s)
        self.assertIn("sourceSha,parsed.sha256",s)

    def test_live_current_file_controls_limits_and_page_order(self):
        a=self.activity
        self.assertIn('button("Find JSON",v->findCurrentJson())',a)
        self.assertIn('private void findCurrentJson()',a)
        self.assertIn('private void startCurrentJsonSearch(String literal)',a)
        self.assertIn("JsonRawSearchHit.search(input,literal)",a)
        self.assertIn("showCurrentJsonHits(current,literal,found,0,token)",a)
        self.assertIn("start+100",a)
        self.assertIn("Previous 100",a)
        self.assertIn("Next 100",a)
        self.assertIn("result.limited",a)
        self.assertIn("LIMITED to 500; narrow query",a)
        self.assertIn("Use indexed Archive Search for large conversation files",a)
        self.assertIn('showIncomingRawHit(verify)',a)
        self.assertIn("UnifiedSearchJump.EXTRA_HASH,result.sha256",a)
        self.assertIn("UnifiedSearchJump.EXTRA_OFFSET,hit.offset",a)
        self.assertIn("token!=revision||request!=currentFindRequest||document==null||!current.equals(document)",a)
        self.assertIn("final long request=++currentFindRequest",a)
        self.assertIn("currentFindRequest++;",a)

    def test_no_new_mutating_json_writer_or_fake_700mb_claim(self):
        s=self.activity
        block=s.split("private void findCurrentJson()",1)[1].split(
            "private void showFailure(",1)[0]
        self.assertNotIn("openOutputStream(",block)
        self.assertNotIn("takePersistableUriPermission(",block)
        self.assertIn("8 MiB",block)
        self.assertIn("sourceHitShown=false;rawHitRequest++;",block)

    def test_executable_jdk_fixture_covers_overlaps_and_result_ceiling(self):
        t=self.fixture
        self.assertIn("JsonRawSearchHit.search(",t)
        self.assertIn("current.hits.size()==2",t)
        self.assertIn("limited.hits.size()==500&&limited.limited",t)
        self.assertIn("current.sha256.equals(hash(bom))",t)

if __name__=="__main__":unittest.main()
