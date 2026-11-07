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

    def test_legacy_migration_preserves_old_and_requires_source_proof(self):
        source = (ROOT / "ArchiveSidecarPublisher.java").read_text()
        section = source.split("private void publishChat(", 1)[1].split(
            "private void publishDocument(", 1)[0]
        self.assertIn("ArchiveMarkdownIndexes.legacyFileName(", section)
        self.assertIn("if(!legacy.equals(safe))", section)
        self.assertIn("Uri old=child(folder,legacy)", section)
        self.assertIn("Uri modern=child(folder,safe)", section)
        self.assertIn("if(modern==null)", section)
        self.assertIn("String previous=read(old)", section)
        self.assertIn("!ArchiveMarkdownIndexes.matchesOwner(previous,archiveId,chatId)", section)
        self.assertIn("identity is ambiguous; original preserved", section)
        self.assertIn("ArchiveMarkdownIndexes.replaceGeneratedRegion(previous,generated)", section)
        self.assertIn("publishDocument(folder,safe,migrated,archiveId,chatId)", section)
        self.assertNotIn("deleteDocument", section)
        self.assertNotIn("renameDocument", section)
        self.assertNotIn("publishDocument(folder,legacy", section)

    def test_existing_notebook_requires_strict_utf8_before_any_replace(self):
        source=(ROOT / "ArchiveSidecarPublisher.java").read_text()
        self.assertIn("StandardCharsets.UTF_8.newDecoder()",source)
        self.assertIn("onMalformedInput(CodingErrorAction.REPORT)",source)
        self.assertIn("onUnmappableCharacter(CodingErrorAction.REPORT)",source)
        self.assertIn("catch(CharacterCodingException malformed)",source)
        self.assertIn("malformed UTF-8; original preserved",source)
        self.assertNotIn("new String(out.toByteArray(),StandardCharsets.UTF_8)",source)
        self.assertLess(source.index("String previous=read(existing)"),
                        source.index("output=ArchiveMarkdownIndexes.replaceGeneratedRegion"))

    def test_same_chat_id_in_two_archives_retains_distinct_sources(self):
        source = (ROOT / "ArchiveSidecarPublisher.java").read_text()
        self.assertIn('a.archiveId.length()+":"+a.archiveId+a.chatId', source)
        index = (ROOT / "archive/ArchiveMarkdownIndexes.java").read_text()
        self.assertIn('(archiveId==null||archiveId.equals(a.archiveId))', index)


if __name__ == "__main__":
    unittest.main(verbosity=2)
