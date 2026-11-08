#!/usr/bin/env python3
"""Source contracts for general JSON Inspector AJN2 notes and authenticated navigation.
Host JDK unit tests cover the pure path/reanchor model; device SAF/UI is separate.
"""
from pathlib import Path
import unittest

SRC=Path(__file__).resolve().parents[1]/"apps/MarkdownDesk/src/com/example/markdowndesk"

class JsonNoteWiring(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.activity=(SRC/"JsonExplorerActivity.java").read_text(encoding="utf-8")
        cls.notes=(SRC/"JsonInspectorNotes.java").read_text(encoding="utf-8")
        cls.identity=(SRC/"workspace/annotations/JsonInspectorNoteIdentity.java").read_text(encoding="utf-8")
        cls.paths=(SRC/"workspace/annotations/JsonPathCodec.java").read_text(encoding="utf-8")
        cls.anchor=(SRC/"workspace/annotations/VerifiedTextAnchor.java").read_text(encoding="utf-8")
        cls.links=(SRC/"workspace/WorkspaceDeepLinks.java").read_text(encoding="utf-8")
        cls.index=(SRC/"archive/ArchiveMarkdownIndexes.java").read_text(encoding="utf-8")
        cls.search=(SRC/"UnifiedSearchActivity.java").read_text(encoding="utf-8")
        cls.archive=(SRC/"ArchiveWorkspaceActivity.java").read_text(encoding="utf-8")
        cls.markdown=(SRC/"MainActivity.java").read_text(encoding="utf-8")

    def test_shared_journal_not_second_annotation_database(self):
        n=self.notes
        self.assertIn('new ArchiveJournal(new File(host.getFilesDir(),"claude-archive/annotations-v2.journal"))',n)
        self.assertIn('journal.save(row)',n)
        self.assertIn('journal.saveIfCurrent(before,changed)',n)
        self.assertIn('journal.deleteIfCurrent(row)',n)
        self.assertIn('AnnotationHistoryDialog.show(host,io,journal,row',n)
        self.assertIn('new ArchiveSidecarPublisher(host,Uri.parse(folder)).publish(title',n)
        self.assertNotIn('openOutputStream(uri',n)
        self.assertNotIn('deleteDocument(',n)

    def test_source_bound_selection_and_reanchor(self):
        n=self.notes
        self.assertIn('JsonInspectorNoteIdentity.create(uri.toString(),canonical',n)
        self.assertIn('JsonExplorerEngine.scan(',n)
        self.assertIn('VerifiedTextAnchor.matchesSelected(latest.value',n)
        self.assertIn('VerifiedTextAnchor.locate(found.value,match.quote',n)
        self.assertIn('if(at<0)throw new IOException("Quoted passage changed or is ambiguous")',n)
        self.assertIn('if(!live(uri,version))',n)
        self.assertIn('JsonPathCodec.parse(match.messageId)',n)
        self.assertIn('if(rows.truncatedTail)',n)
        self.assertIn('if(!WorkspaceDeepLinks.isInspectorLink(ArchiveMarkdownIndexes.deepLink(row)))',n)
        self.assertIn('if(!format(steps).equals(path))',self.paths)
        self.assertIn('if(matched>=0)return -1',self.anchor)

    def test_reader_controls_and_safe_link_admission(self):
        a=self.activity
        self.assertIn('new JsonInspectorNotes(this,worker',a)
        self.assertIn('readingNotes.annotateSelection(value,page.value)',a)
        self.assertIn('readingNotes.decorateValue(source,canonical,page.value,token',a)
        self.assertIn('readingNotes.showNotes()',a)
        self.assertIn('readingNotes.showBacklinks()',a)
        self.assertIn('this::navigateToSavedJsonNote',a)
        self.assertIn('if(pendingJsonNoteId!=null)readingNotes.jumpToNote(document,pendingJsonNoteId)',a)
        self.assertIn('WorkspaceDeepLinks.inspectorTarget(document.toString())',a)
        self.assertIn('if(!UnifiedTabs.openInspector(this,next))',a)
        self.assertIn('if(token!=revision||valueReader==null||isFinishing()||isDestroyed())return',a)

    def test_cross_reader_routes_and_search_type(self):
        self.assertIn('if("json-document".equals(a.archiveId))',self.index)
        self.assertIn('isInspectorLink(uri.toString())',self.markdown)
        self.assertIn('isInspectorLink(uri.toString())',self.archive)
        self.assertIn('boolean inspector=WorkspaceDeepLinks.isInspectorLink(source)',self.search)
        self.assertIn('NOTE_SOURCES={"","markdown","archive","json"}',self.search)
        self.assertIn('public static String inspectorTarget(String candidate)',self.links)
        self.assertIn('return inspectorTarget(candidate)!=null',self.links)
        self.assertIn('isInspectorLink(candidate)',self.links)
        self.assertIn('JsonInspectorNoteIdentity.SOURCE',self.notes)

if __name__=="__main__":
    unittest.main()
