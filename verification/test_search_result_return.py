#!/usr/bin/env python3
"""Source-level return-to-results / dirty-editor preservation contract."""
from pathlib import Path
import unittest

ROOT=Path(__file__).resolve().parents[1]
APP=ROOT/"apps/MarkdownDesk/src/com/example/markdowndesk"

class SearchResultReturn(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.search=(APP/"UnifiedSearchActivity.java").read_text(encoding="utf-8")
        cls.main=(APP/"MainActivity.java").read_text(encoding="utf-8")
        cls.archive=(APP/"ArchiveWorkspaceActivity.java").read_text(encoding="utf-8")
        cls.inspector=(APP/"JsonExplorerActivity.java").read_text(encoding="utf-8")
        cls.jump=(APP/"workspace/search/UnifiedSearchJump.java").read_text(encoding="utf-8")
    def test_result_marker_on_every_source_route(self):
        self.assertIn('public static final String EXTRA_RETURN_RESULTS',self.jump)
        self.assertEqual(self.search.count('putExtra(UnifiedSearchJump.EXTRA_RETURN_RESULTS,true)'),4)
        self.assertIn('openMarkdownHit(hit.file,hit.match,hit.term)',self.search)
        self.assertIn('openJsonHit(hit.file,hit.match,hit.term)',self.search)
        self.assertIn('openAnnotationHit(ArchiveJournal.Annotation annotation)',self.search)
        self.assertIn('openArchiveHit(ArchiveDatabase.Hit hit)',self.search)
    def test_archive_exact_hit_uses_verified_source_coordinates(self):
        self.assertEqual(self.search.count('openArchiveHit(h)'),2)
        self.assertIn('ArchiveWorkspaceActivity.indexedPassageBundle(hit)',self.search)
        self.assertIn('openIndexedPassage(hit,findConversation(hit.chatId),false)',self.archive)
        self.assertIn('database.searchPassage(hit,start,end)',self.archive)
        self.assertIn('e.scrollIntoView({block:',self.archive)

    def test_markdown_return_does_not_discard_dirty_tabs(self):
        s=self.main
        self.assertIn('this.returnUnifiedSearch=intent.getBooleanExtra(UnifiedSearchJump.EXTRA_RETURN_RESULTS,false)',s)
        self.assertIn('} else if (this.returnUnifiedSearch) {',s)
        self.assertIn('startActivity(results)',s)
        self.assertIn('FLAG_ACTIVITY_REORDER_TO_FRONT|Intent.FLAG_ACTIVITY_SINGLE_TOP',s)
        block=s.split('} else if (this.returnUnifiedSearch) {',1)[1].split('} else if (this.filesRail',1)[0]
        self.assertNotIn('finish()',block)
        self.assertNotIn('discard',block)
        self.assertNotIn('setText(',block)
        self.assertIn('this.returnUnifiedSearch=false;',block)
    def test_archive_return_preserves_reader_and_source_identity(self):
        s=self.archive
        self.assertIn('returnUnifiedSearch=intent.getBooleanExtra(UnifiedSearchJump.EXTRA_RETURN_RESULTS,false)',s)
        self.assertIn('returnUnifiedSearch=state.getBoolean("return_unified_search",false)',s)
        self.assertIn('state.putBoolean("return_unified_search",returnUnifiedSearch)',s)
        self.assertIn('if("reader".equals(viewMode)&&returnUnifiedSearch)',s)
        self.assertIn('rememberReadingPlace()',s)
        self.assertIn('startActivity(results)',s)
        self.assertIn('returnUnifiedSearch=false;',s)
        self.assertIn('!sourceArchive.equals(c.archiveId)',s)
    def test_inspector_return_is_not_a_tab_close(self):
        s=self.inspector
        self.assertIn('returnResults=button("Return to search results",v->finish())',s)
        self.assertIn('if(!openedFromSearch){\n                saveInspectorPosition();\n                closeInspectorTabForSwitch();',s)
        self.assertIn('jsonOpenedFromSearch',s)
        self.assertGreaterEqual(s.count('getBooleanExtra(UnifiedSearchJump.EXTRA_RETURN_RESULTS,false)'),2,
                'Both new and reused Inspector activities must honor JSON annotation return marker')
        self.assertIn('intent.hasExtra(UnifiedSearchJump.EXTRA_URI)||',s)

if __name__=="__main__":unittest.main()
