#!/usr/bin/env python3
"""Source-level safety checks for Markdown reading notes integration.
These cannot replace Android/WebView/SAF behavior and compilation tests.
"""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT / "apps/MarkdownDesk/src/com/example/markdowndesk"

class MarkdownNotesIntegrationCheck(unittest.TestCase):
    def test_reader_actions_and_acknowledged_preview(self):
        source = (SRC / "MainActivity.java").read_text(encoding="utf-8")
        self.assertIn('addReadTool("Note","Annotate a selected passage in Reading mode"', source)
        self.assertIn('addReadTool("Notes","Browse and edit source-linked reading notes"', source)
        self.assertIn("this.readingNotes.decorate(noteUri,", source)
        self.assertIn("isCurrentPreviewPage(page)&&page.acknowledged&&this.previewReady", source)
        self.assertIn("()->this.previewRevision", source)

    def test_source_bound_note_routes_have_no_fake_uri_grant(self):
        source = (SRC / "MainActivity.java").read_text(encoding="utf-8")
        note = source.split("private void openMarkdownDocumentLink(Uri link) {",1)[1].split("\n    }",1)[0]
        self.assertIn("this.readingNotes.requestNote(source,noteId)", note)
        self.assertIn("openDocument(Uri.parse(source),0,false,true)", note)
        executable = "\n".join(line.split("//", 1)[0] for line in note.splitlines())
        self.assertNotIn("FLAG_GRANT_READ_URI_PERMISSION", executable.split("openDocument(")[0])
        self.assertNotIn("takePermission(", executable)
        self.assertIn("WorkspaceDeepLinks.documentTarget(link.toString())", note)

    def test_single_journal_and_revision_gate(self):
        controller = (SRC / "MarkdownReadingNotes.java").read_text(encoding="utf-8")
        self.assertIn('claude-archive/annotations-v2.journal', controller)
        self.assertIn("MarkdownNoteIdentity.owns(a,uri)", controller)
        self.assertIn("revision.get()!=revisionAtSelection", controller)
        self.assertIn("journal.save(a)", controller)
        self.assertIn("journal.deleteIfCurrent(a)", controller)
        self.assertIn("journal.saveIfCurrent(expected,a)", controller)
        self.assertIn("ArchiveSidecarPublisher(activity,Uri.parse(folder))", controller)
        self.assertIn("String publication=publish(uri,a.chatTitle)", controller)
        self.assertNotIn("new SQLiteDatabase", controller)

    def test_shared_writable_notes_folder_picker_and_sync(self):
        activity = (SRC / "MainActivity.java").read_text(encoding="utf-8")
        controller = (SRC / "MarkdownReadingNotes.java").read_text(encoding="utf-8")
        publisher = (SRC / "ArchiveSidecarPublisher.java").read_text(encoding="utf-8")
        self.assertIn('import android.content.Context;', activity)
        self.assertIn('REQUEST_READING_NOTES_TREE = 1014', activity)
        self.assertIn('addReadTool("Index folder"', activity)
        self.assertIn('new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)', activity)
        self.assertIn('handleReadingNotesTreeResult(i2,intent)', activity)
        self.assertIn('takePersistableUriPermission(tree,required)', activity)
        self.assertIn('DocumentsContract.getTreeDocumentId(tree)', activity)
        self.assertIn('.putString("archive_notes_tree",tree.toString()).commit()', activity)
        self.assertIn('this.readingNotes.syncIndexes()', activity)
        self.assertIn('new ArchiveSidecarPublisher(activity,Uri.parse(folder)).publishAll(data.annotations)', controller)
        self.assertIn('if(data.truncatedTail)throw', controller)
        self.assertIn('ArchiveMarkdownIndexes.matchesOwner(previous,archiveId,chatId)', publisher)
        self.assertIn('ArchiveMarkdownIndexes.replaceGeneratedRegion(previous,generated)', publisher)
        self.assertNotIn('new SQLiteDatabase', controller)

    def test_saved_document_backlinks_use_exact_destination_and_safe_existing_routes(self):
        activity = (SRC / "MainActivity.java").read_text(encoding="utf-8")
        controller = (SRC / "MarkdownReadingNotes.java").read_text(encoding="utf-8")
        self.assertIn('addReadTool("Backlinks"', activity)
        self.assertIn('this.readingNotes.showBacklinks()', activity)
        self.assertIn('ArchiveJournal.ReadResult data=journal.load()', controller)
        self.assertIn('if(a==null||!a.isLink()||a.target==null)continue', controller)
        self.assertIn('String destination=WorkspaceDeepLinks.documentTarget(a.target)', controller)
        self.assertIn('destination=MarkdownNoteIdentity.requireDocument(a.target)', controller)
        self.assertIn('if(current.equals(destination))inbound.add(a)', controller)
        self.assertIn('ArchiveMarkdownIndexes.deepLink(a)', controller)
        self.assertIn('WorkspaceDeepLinks.isDocumentLink(origin)', controller)
        self.assertIn('WorkspaceDeepLinks.isArchiveLink(origin)', controller)
        self.assertIn('Intent.ACTION_VIEW,link', controller)
        self.assertIn('MainActivity.class:ArchiveWorkspaceActivity.class', controller)
        self.assertNotIn('FLAG_GRANT_READ_URI_PERMISSION', controller)
        self.assertNotIn('FLAG_GRANT_WRITE_URI_PERMISSION', controller)

    def test_readonly_note_history_remains_source_bound(self):
        markdown=(SRC / "MarkdownReadingNotes.java").read_text()
        archive=(SRC / "ArchiveWorkspaceActivity.java").read_text()
        journal=(SRC / "archive/ArchiveJournal.java").read_text()
        history=(SRC / "AnnotationHistoryDialog.java").read_text()
        self.assertIn('"Edit note","History","Delete note"', markdown)
        self.assertIn('"Edit note","History","Delete annotation"', archive)
        self.assertIn('AnnotationHistoryDialog.show(activity,worker,journal,a,()->live(uri),this::message)', markdown)
        self.assertIn('AnnotationHistoryDialog.show(this,io,journal,a,', archive)
        self.assertIn('()->generation==historyGeneration,this::status', archive)
        self.assertNotIn('()->active!=null&&sourceChat!=null', archive)
        self.assertIn('journal.history(snapshot)', history)
        self.assertIn('HistoryResult history=new HistoryResult()', journal)
        self.assertIn('Annotation history has conflicting source identity', journal)
        self.assertIn('Annotation journal has an interrupted record', journal)
        self.assertNotIn('saveIfCurrent(', history)
        self.assertNotIn('deleteIfCurrent(', history)

    def test_dom_script_does_not_execute_source_data(self):
        script = (SRC / "workspace/annotations/MarkdownNoteScript.java").read_text(encoding="utf-8")
        self.assertIn("all.slice(start,end)!==quote", script)
        self.assertIn("valid.length!==1", script)
        self.assertIn("CSS.highlights.set", script)
        self.assertIn('case', script)
        self.assertNotIn("innerHTML=", script)
        self.assertIn("window.mdNoteJump", script)

    def test_shared_projection_uses_document_uri(self):
        source = (SRC / "archive/ArchiveMarkdownIndexes.java").read_text(encoding="utf-8")
        self.assertIn('"markdown-document".equals(a.archiveId)', source)
        self.assertIn('"markdowndesk://document/v1/"+enc(a.chatId)+"?note="+enc(a.id)', source)

if __name__ == "__main__":
    unittest.main(verbosity=2)
