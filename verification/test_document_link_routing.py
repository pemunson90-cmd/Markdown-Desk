#!/usr/bin/env python3
"""Static guard for cross-workspace document links; native SAF still needs device QA."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / "apps/MarkdownDesk/src/com/example/markdowndesk"


class DocumentRoutingTests(unittest.TestCase):
    def test_renderer_renders_only_validated_internal_links(self):
        source = (JAVA / "MarkdownRenderer.java").read_text()
        self.assertIn("WorkspaceDeepLinks.isInternalLink(href)", source)
        self.assertNotIn('href.startsWith("markdowndesk:', source)

    def test_markdown_route_uses_protected_view_only_open(self):
        source = (JAVA / "MainActivity.java").read_text()
        body = source.split("private void openMarkdownDocumentLink(Uri link) {", 1)[1].split("\n    }", 1)[0]
        self.assertIn("WorkspaceDeepLinks.documentTarget(link.toString())", body)
        self.assertIn("openDocument(Uri.parse(source),0,false,true)", body)
        self.assertNotIn("takePermission(", body)
        self.assertNotIn("addFlags(", body)
        self.assertNotIn("FLAG_GRANT_READ_URI_PERMISSION,", body)
        self.assertIn(".isDocumentLink(uri.toString())", source)
        self.assertIn("openMarkdownDocumentLink(intent.getData())", source)

    def test_archive_to_document_uses_explicit_internal_activity(self):
        source = (JAVA / "ArchiveWorkspaceActivity.java").read_text()
        self.assertIn(".isDocumentLink(uri.toString())", source)
        self.assertIn(".setClass(ArchiveWorkspaceActivity.this,MainActivity.class)", source)
        self.assertNotIn("new Intent(Intent.ACTION_VIEW,uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)", source)

    def test_unified_search_does_not_treat_markdown_notes_as_archive_messages(self):
        source = (JAVA / "UnifiedSearchActivity.java").read_text()
        self.assertIn('ArchiveMarkdownIndexes.deepLink(annotation)', source)
        self.assertIn('WorkspaceDeepLinks.isDocumentLink(source)', source)
        self.assertIn('WorkspaceDeepLinks.isArchiveLink(source)', source)
        self.assertIn('markdown?MainActivity.class:ArchiveWorkspaceActivity.class', source)
        self.assertEqual(source.count('v->openAnnotationHit(a)'), 2)
        self.assertNotIn('v->openArchiveHit(a.chatId,a.messageId)', source)
        region = source.split('private void openAnnotationHit(', 1)[1].split(
            'private void openArchiveHit(', 1)[0]
        self.assertNotIn('FLAG_GRANT_READ_URI_PERMISSION', region)
        self.assertNotIn('takePersistableUriPermission', region)

    def test_unified_search_reuses_strict_calendar_spec(self):
        source = (JAVA / "UnifiedSearchActivity.java").read_text()
        self.assertIn("new ArchiveSearchSpec(term,speaker.getText().toString()", source)
        self.assertIn("error(\"Invalid search filters\",invalid)", source)
        self.assertIn("Speaker and date filters apply to indexed conversation messages only.", source)
        self.assertNotIn("private boolean goodDate(String date)", source)

    def test_unified_note_filters_preserve_kind_tag_source_across_pages(self):
        source = (JAVA / "UnifiedSearchActivity.java").read_text()
        engine = (JAVA / "workspace/archive/ArchiveAnnotationSearch.java").read_text()
        self.assertIn('noteKind=choose(', source)
        self.assertIn('noteTag=field("Exact tag (optional)")', source)
        self.assertIn('noteSource=choose(', source)
        self.assertIn('ArchiveAnnotationSearch.pageFiltered(', source)
        self.assertIn('moreNotes(token,term,100,type,tag,origin)', source)
        self.assertIn('moreNotes(token,term,offset+100,type,tag,origin)', source)
        self.assertIn('if(!type.isEmpty()&&!type.equals(a.type))continue', engine)
        self.assertIn('if(!desiredTag.isEmpty()&&!hasTag(a.tags,desiredTag))continue', engine)
        self.assertIn('if("markdown".equals(origin)&&!"markdown-document".equals(a.archiveId))continue', engine)
        self.assertIn('if("archive".equals(origin)&&"markdown-document".equals(a.archiveId))continue', engine)
        self.assertLess(engine.index('if(!type.isEmpty()&&!type.equals(a.type))continue'),
                        engine.index('if(seen++<start)continue'))
        self.assertIn('part.trim().equalsIgnoreCase(wanted)', engine)

    def test_transcript_export_streams_full_messages_with_exact_source_links(self):
        source = (JAVA / "ArchiveWorkspaceActivity.java").read_text()
        export = source.split("private void writeTranscript(", 1)[1].split(
            "private static void write(OutputStream", 1)[0]
        self.assertIn('database.messages(chat,index,50)', export)
        self.assertIn('for(int part=0;part<m.segments;part+=32)', export)
        self.assertIn('database.textChunk(m.id,part,32)', export)
        self.assertIn('.appendPath(conversation.archiveId)', export)
        self.assertIn('.appendPath(chat).appendPath(m.messageId)', export)
        self.assertIn('Source: [Open original message]', export)
        self.assertIn('transcriptField(conversation.name)', export)
        self.assertIn('transcriptField(m.role)', export)
        self.assertNotIn('WINDOW_MESSAGES', export)
        self.assertNotIn('CHUNK_PARTS', export)
        self.assertIn('return value.replace(\'\\\\r\',\' \').replace(\'\\\\n\',\' \')', export)

    def test_document_deep_link_is_not_externally_exported(self):
        manifest = (ROOT / "apps/MarkdownDesk/AndroidManifest.xml").read_text()
        self.assertNotIn('android:host="document"', manifest)


if __name__ == "__main__":
    unittest.main(verbosity=2)
