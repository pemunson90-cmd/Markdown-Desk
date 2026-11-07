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

    def test_no_source_mutation_or_silent_page_cap_change(self):
        self.assertIn('final int PAGE_SIZE = 100',self.engine)
        self.assertIn('final int MAX_TRANSCRIPT_MESSAGES = 200',self.engine)
        self.assertIn('onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)',self.engine)
        self.assertNotIn('openOutputStream(document',self.activity)

if __name__=='__main__':unittest.main()
