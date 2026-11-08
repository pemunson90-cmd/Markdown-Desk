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
        self.assertIn('openArchiveHit(ArchiveDatabase.Hit hit,String term)',self.search)
    def test_archive_exact_hit_keeps_verified_span_and_generation(self):
        self.assertEqual(self.search.count('openArchiveHit(h,term)'),2)
        for field in ('PART,hit.partOrdinal','TERM,term','GENERATION,hit.generationId'):
            self.assertIn('putExtra(ArchiveWorkspaceActivity.EXTRA_SEARCH_'+field+')',self.search)
        self.assertIn('if(c.generationId.equals(searchGeneration))',self.archive)
        self.assertIn('chunkOffset=Math.max(0,intent.getIntExtra(EXTRA_SEARCH_PART,0)-1)',self.archive)
        for field in ('TEXT,hit.matchedText','START,hit.matchStart','END,hit.matchEnd'):
            self.assertIn('putExtra(ArchiveSearchHitScript.EXTRA_MATCH_'+field+')',self.search)
        self.assertIn('pendingSearchMatch=actual;pendingSearchMessage=message',self.archive)
        self.assertIn('pendingSearchGeneration=searchGeneration',self.archive)
        self.assertIn('active.generationId.equals(pendingSearchGeneration)',self.archive)
        self.assertIn('ArchiveSearchHitScript.jump(pendingSearchMessage,pendingSearchPart,quote)',self.archive)
        self.assertNotIn('pendingFindTerm=searchTerm',self.archive)
        self.assertIn('focusOrdinal<viewport.start()||focusOrdinal>=viewport.end()',self.archive)
        self.assertIn('if(!restorePosition&&navigationOrdinal>=0)',self.archive)
        self.assertIn('if(e)e.scrollIntoView()',self.archive)

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
        self.assertIn('if(!openedFromSearch)closeInspectorTabForSwitch()',s)
        self.assertIn('jsonOpenedFromSearch',s)
        self.assertGreaterEqual(s.count('getBooleanExtra(UnifiedSearchJump.EXTRA_RETURN_RESULTS,false)'),2,
                'Both new and reused Inspector activities must honor JSON annotation return marker')
        self.assertIn('intent.hasExtra(UnifiedSearchJump.EXTRA_URI)||',s)

if __name__=="__main__":unittest.main()
