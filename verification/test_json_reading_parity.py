#!/usr/bin/env python3
"""Static safety contract for JSON Explorer controls; native Fold/TalkBack still require testing."""
from pathlib import Path
import unittest

ROOT=Path(__file__).resolve().parents[1]
SRC=ROOT/'apps/MarkdownDesk/src/com/example/markdowndesk/JsonExplorerActivity.java'
ENGINE=ROOT/'apps/MarkdownDesk/src/com/example/markdowndesk/JsonExplorerEngine.java'

class JsonReadingParity(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.activity=SRC.read_text(encoding='utf-8')
        cls.engine=ENGINE.read_text(encoding='utf-8')

    def test_persistent_shared_scalable_reading(self):
        a=self.activity
        self.assertIn('prefs.getFloat("preview_font_size",18f)',a)
        self.assertIn('prefs.edit().putFloat("preview_font_size",readingSp).apply()',a)
        self.assertIn('return Math.max(12f,Math.min(32f,original*readingSp/18f))',a)
        self.assertIn('if(root!=null)applyReadingFont(root)',a)
        self.assertIn('if(base instanceof Float)((TextView)target).setTextSize(scaleFont((Float)base))',a)
        self.assertIn('b.setTag(Float.valueOf(14))',a)
        self.assertIn('v.setTag(Float.valueOf(size))',a)

    def test_focus_can_be_reversed_and_does_not_destroy_reader(self):
        a=self.activity
        self.assertIn('focusReading?"Exit Focus mode":"Enter Focus mode"',a)
        self.assertIn('focusReading=!focusReading;updateReadingFocus()',a)
        self.assertIn('actionRow.setVisibility(focusReading?View.GONE:View.VISIBLE)',a)
        self.assertIn('if(focusReading)getWindow().addFlags',a)
        self.assertIn('else getWindow().clearFlags',a)
        self.assertNotIn('entries.removeAllViews()',a.split('private void updateReadingFocus()',1)[1].split('private void showReadingOptions()',1)[0])

    def test_state_retains_reading_mode_and_json_path(self):
        a=self.activity
        self.assertIn('state.getBoolean("jsonFocusReading",false)',a)
        self.assertIn('state.putBoolean("jsonFocusReading",focusReading)',a)
        self.assertIn('state.putStringArrayList("jsonPathNames",names)',a)
        self.assertIn('state.putIntArray("jsonPathIndices",indices)',a)
        self.assertIn('state.putInt("jsonOffset",offset)',a)
        self.assertIn('options.setContentDescription("JSON reading options")',a)

    def test_full_archive_reader_is_explicit_and_reuses_process_owned_import(self):
        a=self.activity
        archive=(ROOT/'apps/MarkdownDesk/src/com/example/markdowndesk/ArchiveWorkspaceActivity.java').read_text(encoding='utf-8')
        router=(ROOT/'apps/MarkdownDesk/src/com/example/markdowndesk/workspace/WorkspaceRouter.java').read_text(encoding='utf-8')
        self.assertIn('Index full Claude export for complete reading',a)
        self.assertIn('showFullArchiveImportChoice()',a)
        self.assertIn('setPositiveButton("Index for full reading"',a)
        self.assertIn('Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_ACTIVITY_SINGLE_TOP',a)
        self.assertIn('putExtra(com.example.markdowndesk.workspace.WorkspaceRouter.EXTRA_IMPORT_JSON_URI',a)
        self.assertIn('EXTRA_IMPORT_JSON_URI',router)
        self.assertIn('private boolean handleIncomingInspectorImport(Intent intent)',archive)
        self.assertIn('if(!handleIncomingInspectorImport(getIntent())',archive)
        self.assertIn('if(handleIncomingInspectorImport(intent))return',archive)
        self.assertIn('importArchive(selected);',archive)
        self.assertIn('advertised.equals(selected.toString())',archive)
        self.assertIn('!"content".equalsIgnoreCase(selected.getScheme())',archive)
        self.assertIn('Preview is limited to 200 messages',a)

    def test_no_source_mutation_or_silent_page_cap_change(self):
        self.assertIn('final int PAGE_SIZE = 100',self.engine)
        self.assertIn('final int MAX_TRANSCRIPT_MESSAGES = 200',self.engine)
        self.assertIn('onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)',self.engine)
        self.assertNotIn('openOutputStream(document',self.activity)

if __name__=='__main__':unittest.main()
