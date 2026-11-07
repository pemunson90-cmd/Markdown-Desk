#!/usr/bin/env python3
"""Native UI source affordance guard. Physical TalkBack and Fold7 remain required."""
from pathlib import Path
import unittest

APP=Path(__file__).resolve().parents[1]/"apps/MarkdownDesk/src/com/example/markdowndesk"

class AccessibilityContract(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.archive=(APP/"ArchiveWorkspaceActivity.java").read_text(encoding="utf-8")
        cls.search=(APP/"UnifiedSearchActivity.java").read_text(encoding="utf-8")
        cls.inspector=(APP/"JsonExplorerActivity.java").read_text(encoding="utf-8")

    def test_archive_symbol_button_talkback_names(self):
        a=self.archive
        for spoken in ("Back to previous Archive screen","More Archive actions",
                "Previous conversation messages","Next conversation messages",
                "Decrease reading text size","Increase reading text size"):
            self.assertIn(spoken,a)
        self.assertIn('b.setContentDescription(spoken)',a)
        self.assertIn('b.setMinHeight(dp(48))',a)
        self.assertIn('tab.setContentDescription("Open archive tab: "',a)

    def test_search_scope_form_labels(self):
        s=self.search
        for label in ("Back to previous workspace","Search sources",
                "Saved annotation kind","Saved annotation origin",
                "Authorized document folder scope"):
            self.assertIn(label,s)
        self.assertIn("root.setFitsSystemWindows(true)",s)
        self.assertIn("b.setMinHeight(dp(48))",s)

    def test_inspector_reading_and_system_windows(self):
        i=self.inspector
        self.assertIn("root.setFitsSystemWindows(true)",i)
        self.assertIn("options.setContentDescription(\"JSON reading options\")",i)
        self.assertIn('returnResults.setContentDescription("Return to the previous Unified Search results")',i)
        self.assertIn("b.setMinHeight(dp(48))",i)
        self.assertNotIn("setOnApplyWindowInsetsListener",i)

if __name__=="__main__":unittest.main()
