#!/usr/bin/env python3
"""Conservative source contracts for annotation colors, backlinks, history, and sidecars.
These checks complement executable DOM/Java tests, not physical SAF/Fold proof.
"""
from pathlib import Path
import unittest

ROOT=Path(__file__).resolve().parents[1]
JAVA=ROOT/"apps/MarkdownDesk/src/com/example/markdowndesk"

class AnnotationSafetyContracts(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.notes=(JAVA/"MarkdownReadingNotes.java").read_text(encoding="utf-8")
        cls.archive=(JAVA/"ArchiveWorkspaceActivity.java").read_text(encoding="utf-8")
        cls.index=(JAVA/"archive/ArchiveMarkdownIndexes.java").read_text(encoding="utf-8")
        cls.publisher=(JAVA/"ArchiveSidecarPublisher.java").read_text(encoding="utf-8")
        cls.tone=(JAVA/"workspace/annotations/AnnotationTone.java").read_text(encoding="utf-8")
        cls.mdscript=(JAVA/"workspace/annotations/MarkdownNoteScript.java").read_text(encoding="utf-8")
        cls.arscript=(JAVA/"workspace/archive/ArchiveHighlightScript.java").read_text(encoding="utf-8")

    def test_fixed_tones_legacy_compatibility_and_cas_edits(self):
        self.assertIn('COLORS={"violet","rose","mint","amber","blue","slate"}',self.tone)
        self.assertIn('return COLORS[0]',self.tone)
        self.assertIn('AnnotationTone.withTone',self.notes)
        self.assertIn('journal.saveIfCurrent(expected,a)',self.notes)
        self.assertIn('AnnotationTone.withTone',self.archive)
        self.assertIn('saveAnnotation(changed,a)',self.archive)
        self.assertIn('AnnotationTone.fromTags(a.tags)',self.mdscript)
        self.assertIn('AnnotationTone.fromTags(a.tags)',self.arscript)
        self.assertIn("violet|rose|mint|amber|blue|slate",self.arscript)
        for tone in ("violet","rose","mint","amber","blue","slate"):
            self.assertIn("md-archive-tone-"+tone,self.archive)
            self.assertIn("markdown-notes-"+tone,self.mdscript)

    def test_corrupt_tail_does_not_produce_definitive_backlinks(self):
        backlinks=self.notes.split("void showBacklinks()",1)[1].split(
            "private void showBacklinkActions",1)[0]
        self.assertIn("if(data.truncatedTail)",backlinks)
        self.assertIn("throw new java.io.IOException",backlinks)
        self.assertIn("Journal record interrupted",backlinks)
        self.assertIn('if(current.equals(destination))inbound.add(a)',backlinks)

    def test_sidecar_never_reinterprets_invalid_utf8_or_duplicate_markers(self):
        s=self.publisher
        self.assertIn("StandardCharsets.UTF_8.newDecoder()",s)
        self.assertIn("onMalformedInput(CodingErrorAction.REPORT)",s)
        self.assertIn("onUnmappableCharacter(CodingErrorAction.REPORT)",s)
        self.assertIn("Existing Markdown index contains malformed UTF-8; original preserved",s)
        self.assertNotIn("new String(out.toByteArray(),StandardCharsets.UTF_8)",s)
        self.assertIn("Repeated managed Markdown markers; original preserved",self.index)
        self.assertIn("Missing or repeated replacement markers",self.index)

    def test_archive_navigation_restores_authenticated_prior_chat(self):
        s=self.archive
        self.assertIn('state.putStringArrayList("archive_prior_chat_ids"',s)
        self.assertIn('state.getStringArrayList("archive_prior_chat_ids")',s)
        self.assertIn('if(priorArchiveChats.size()==16)priorArchiveChats.remove(0)',s)
        self.assertIn('rememberPriorArchiveVisit(c.id)',s)
        self.assertIn('ArchiveDatabase.Conversation previous=findConversation(prior)',s)
        self.assertIn('rememberReadingPlace()',s)
        self.assertIn('openChat(previous)',s)
        self.assertIn('priorArchiveChats.clear()',s)

if __name__=="__main__":unittest.main()
