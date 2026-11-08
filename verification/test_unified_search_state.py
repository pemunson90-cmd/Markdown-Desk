#!/usr/bin/env python3
"""Source-level lifecycle guard. Physical Fold/Android behavior still requires device acceptance."""
from pathlib import Path
import unittest

ROOT=Path(__file__).resolve().parents[1]
ACTIVITY=ROOT/"apps/MarkdownDesk/src/com/example/markdowndesk/UnifiedSearchActivity.java"

class SearchLifecycleState(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.source=ACTIVITY.read_text(encoding="utf-8")

    def test_query_and_filters_round_trip_without_sensitive_result_serialization(self):
        source=self.source
        names=("query","speaker","from","to","tag","scope","kind","source","folder_uri","executed")
        for name in names:
            self.assertIn('"search_'+name+'"',source)
        self.assertIn("out.putString(\"search_query\",query.getText().toString())",source)
        self.assertIn("saved.getString(\"search_query\",\"\")",source)
        self.assertIn('saved.getBoolean("search_executed",false)',source)
        self.assertNotIn('out.putParcelableArrayList("search_hits"',source)
        self.assertNotIn('out.putSerializable("search_results"',source)

    def test_folder_selection_restores_stable_uri_not_index(self):
        source=self.source
        self.assertIn('out.putString("search_folder_uri",roots.get(index).root.uri.toString())',source)
        self.assertIn('roots.get(i).root.uri.toString().equals(savedFolder)',source)
        self.assertIn('folder.setSelection(i+1)',source)
        self.assertNotIn('out.putInt("search_folder_index"',source)

    def test_spinner_indices_clamped_and_expensive_scan_not_restarted(self):
        source=self.source
        restore=source.split('private void restoreSearchState(Bundle saved){',1)[1].split(
            '@Override protected void onSaveInstanceState',1)[0]
        self.assertIn('UnifiedSearchScope.values().length-1',restore)
        self.assertIn('NOTE_TYPES.length-1',restore)
        self.assertIn('NOTE_SOURCES.length-1',restore)
        self.assertIn('Tap Search to refresh results after rotation.',restore)
        self.assertNotIn('search();',restore)
        self.assertNotIn('WorkspaceSearchLoader.trySearch',restore)

    def test_change_of_generation_still_cancels_previous_results(self):
        source=self.source
        self.assertIn('searchExecuted=true;',source)
        self.assertIn('cancelActive();',source)
        self.assertIn('if(token!=generation',source)
        self.assertIn('if(folderJob!=null){folderJob.cancel();folderJob=null;}',source)

if __name__=="__main__":unittest.main(verbosity=2)
