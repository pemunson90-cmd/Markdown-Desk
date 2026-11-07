#!/usr/bin/env python3
"""Static failsafe checks for SAF sidecar identity; Android provider QA remains separate."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1] / "apps/MarkdownDesk/src/com/example/markdowndesk"


class SidecarPublicationChecks(unittest.TestCase):
    def test_per_chat_has_owned_generated_region(self):
        source = (ROOT / "archive/ArchiveMarkdownIndexes.java").read_text()
        chat = source.split("public static String perChat(", 1)[1].split(
            "public static String master(", 1)[0]
        self.assertIn("ownerMarker(archiveId,chatId)", chat)

    def test_short_sanitized_ids_get_stable_hash(self):
        source = (ROOT / "archive/ArchiveMarkdownIndexes.java").read_text()
        self.assertIn("public static String legacyFileName(", source)
        self.assertIn('original.matches("[A-Za-z0-9_-]{1,64}")', source)
        self.assertIn('hashPrefix(original,8)', source)
        self.assertIn("public static boolean matchesOwner(", source)

    def test_publisher_rejects_ambiguous_existing_before_stage(self):
        source = (ROOT / "ArchiveSidecarPublisher.java").read_text()
        start = source.index("private void publishDocument(")
        guard = source.index("!ArchiveMarkdownIndexes.matchesOwner", start)
        stage = source.index('String stageName=', start)
        self.assertLess(guard, stage)
        self.assertIn("original preserved", source)

    def test_legacy_name_never_overwritten_implicitly(self):
        source = (ROOT / "ArchiveSidecarPublisher.java").read_text()
        self.assertIn("ArchiveMarkdownIndexes.legacyFileName(", source)
        self.assertIn("!legacy.equals(safe) && child(folder,legacy)!=null", source)
        self.assertIn("Legacy Markdown notebook needs identity review", source)

    def test_same_chat_id_in_two_archives_retains_distinct_sources(self):
        source = (ROOT / "ArchiveSidecarPublisher.java").read_text()
        self.assertIn('a.archiveId.length()+":"+a.archiveId+a.chatId', source)
        index = (ROOT / "archive/ArchiveMarkdownIndexes.java").read_text()
        self.assertIn('(archiveId==null||archiveId.equals(a.archiveId))', index)


if __name__ == "__main__":
    unittest.main(verbosity=2)
