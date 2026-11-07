#!/usr/bin/env python3
"""Structural safety checks for sequential authorized-root search, pagination and truthfulness."""
from pathlib import Path
import unittest

ROOT=Path(__file__).resolve().parents[1]
SRC=(ROOT/"apps/MarkdownDesk/src/com/example/markdowndesk/UnifiedSearchActivity.java")

class UnifiedAllRootsSearch(unittest.TestCase):
    @classmethod
    def setUpClass(cls):cls.source=SRC.read_text(encoding="utf-8")

    def test_default_scope_does_not_silently_omit_saved_folders(self):
        s=self.source
        self.assertIn('"All granted saved folders"',s)
        self.assertIn('else pendingRoots.addAll(roots)',s)
        self.assertIn('totalRoots=pendingRoots.size()',s)
        self.assertIn('No saved folders. Indexed Archive and notes may still have matches',s)

    def test_one_physical_search_and_nonreentrant_delivery(self):
        s=self.source
        self.assertIn("WorkspaceSearchLoader.trySearch(this",s)
        self.assertIn("if(folderJob!=null)return",s)
        self.assertIn("folderJob=null;",s)
        self.assertIn("rootSearchHandler.post(()->scanNextRoot(token,term))",s)
        self.assertIn("if(ticket==null)",s)
        self.assertIn("Protected folder search engine is busy",s)

    def test_permissions_limits_and_failure_are_visible(self):
        s=self.source
        self.assertIn("next.access!=WorkspaceRootStore.Access.CURRENT",s)
        self.assertIn("skippedRoots++",s)
        self.assertIn("limitedRoots++",s)
        self.assertIn("failedRoots++",s)
        self.assertIn('?" · INCOMPLETE":""',s)
        self.assertIn("r.reasons",s)

    def test_bounded_dedup_pagination_and_source_navigation(self):
        s=self.source
        self.assertIn("seenFolderHits.add(key)",s)
        self.assertIn("private static final int FOLDER_PAGE=100",s)
        self.assertIn("Math.min(folderHits.size(),shownFolderHits+FOLDER_PAGE)",s)
        self.assertIn('button("More folder matches ("+',s)
        self.assertIn("if(hit.file.json)openJsonHit",s)
        self.assertIn("else openMarkdownHit",s)

    def test_cancel_drops_old_results_and_pending_work(self):
        s=self.source
        self.assertIn("pendingRoots.clear();folderHits.clear();seenFolderHits.clear()",s)
        self.assertIn("generation++",s)
        self.assertIn("if(token!=generation||isFinishing()||isDestroyed())return",s)
        self.assertIn("if(folderJob!=null){folderJob.cancel();folderJob=null;}",s)

if __name__=="__main__":unittest.main()
