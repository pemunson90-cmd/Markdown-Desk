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
        self.assertIn('if("archive".equals(origin)&&(a.archiveId==null||a.archiveId.isEmpty()||', engine)
        self.assertIn('"markdown-document".equals(a.archiveId)))continue', engine)
        self.assertLess(engine.index('if(!type.isEmpty()&&!type.equals(a.type))continue'),
                        engine.index('if(seen++<start)continue'))
        self.assertIn('part.trim().equalsIgnoreCase(wanted)', engine)

    def test_transcript_export_streams_full_messages_with_exact_source_links(self):
        source = (JAVA / "ArchiveWorkspaceActivity.java").read_text()
        export = source.split("private void writeTranscript(", 1)[1].split(
            "private static void write(OutputStream", 1)[0]
        self.assertIn('database.messages(chat,index,50)', export)
        self.assertIn('for(int part=0;part<m.segments;part+=32)', export)
        self.assertIn('database.textChunkVerified(m.id,part,count)', export)
        self.assertIn('ArchiveTranscriptIntegrity.message(index+row,m.ordinal,m.segments)', export)
        self.assertIn('ArchiveTranscriptIntegrity.complete(conversation.messages,index)', export)
        self.assertIn('database.requireExportGeneration(conversation.archiveId,generation)', export)
        self.assertIn('.appendPath(conversation.archiveId)', export)
        self.assertIn('.appendPath(chat).appendPath(m.messageId)', export)
        self.assertIn('Source: [Open original message]', export)
        self.assertIn('transcriptField(conversation.name)', export)
        self.assertIn('transcriptField(m.role)', export)
        self.assertNotIn('WINDOW_MESSAGES', export)
        self.assertNotIn('CHUNK_PARTS', export)
        self.assertIn(r"return value.replace('\r',' ').replace('\n',' ')", export)

    def test_complete_pdf_export_keeps_full_indexed_source_and_fails_closed(self):
        activity = (JAVA / "ArchiveWorkspaceActivity.java").read_text()
        pdf = (JAVA / "ArchiveTranscriptPdf.java").read_text()
        db = (JAVA / "ArchiveDatabase.java").read_text()
        self.assertIn('action("Export PDF",v->exportTranscriptPdf())', activity)
        self.assertIn('REQUEST_TRANSCRIPT_PDF=8204', activity)
        self.assertIn('setType("application/pdf")', activity)
        self.assertIn('ArchiveTranscriptPdf.export(database,conversation,out)', activity)
        self.assertIn('PDF export incomplete; do not rely on any partial document', activity)
        self.assertIn('ArchiveTranscriptIntegrity.message(index+row,m.ordinal,m.segments)', pdf)
        self.assertIn('database.textChunkVerified(m.id,part,count)', pdf)
        self.assertIn('ArchiveTranscriptIntegrity.complete(conversation.messages,index)', pdf)
        self.assertIn('database.requireExportGeneration(conversation.archiveId,generation)', pdf)
        self.assertIn('MAX_LAYOUT_UNITS=8192', pdf)
        self.assertIn('pdf.document.writeTo(destination)', pdf)
        self.assertNotIn('WINDOW_MESSAGES', pdf)
        self.assertIn('ArchiveTranscriptIntegrity.part(startPart+found,c.getInt(0))', db)
        self.assertIn('ArchiveTranscriptIntegrity.complete(take,found)', db)

    def test_unified_markdown_match_uses_hash_and_exact_offset_after_open(self):
        search = (JAVA / "UnifiedSearchActivity.java").read_text()
        activity = (JAVA / "MainActivity.java").read_text()
        verify = (JAVA / "workspace/search/UnifiedSearchJump.java").read_text()
        self.assertIn('else openMarkdownHit(file,match,term)', search)
        self.assertIn('intent.putExtra(UnifiedSearchJump.EXTRA_URI,file.uri.toString())', search)
        self.assertIn('intent.putExtra(UnifiedSearchJump.EXTRA_HASH,file.hash)', search)
        self.assertIn('intent.putExtra(UnifiedSearchJump.EXTRA_LITERAL,literal)', search)
        self.assertIn('intent.putExtra(UnifiedSearchJump.EXTRA_OFFSET,match.offsetUtf16)', search)
        self.assertIn('captureUnifiedSearchJump(intent)', activity)
        self.assertGreaterEqual(activity.count('resolveUnifiedSearchJump(uri)'), 2)
        self.assertIn('UnifiedSearchJump.matches(request.uri,', activity)
        self.assertIn('request.hash,this.baselineHash,this.editor.getText()', activity)
        self.assertIn('request.literal,request.offset,this.dirty', activity)
        self.assertIn('jumpToSourceOffset(request.offset)', activity)
        self.assertIn('this.pendingUnifiedJump=null; // One-shot', activity)
        self.assertIn('!baselineHash.equalsIgnoreCase(searchHash)', verify)
        self.assertIn('offset>displayedText.length()-literal.length()', verify)
        self.assertIn('displayedText.charAt(offset+i)!=literal.charAt(i)', verify)

    def test_archive_deep_link_requires_same_indexed_source_and_exact_shape(self):
        activity = (JAVA / "ArchiveWorkspaceActivity.java").read_text()
        route = activity.split("private boolean handleDeepLink(Intent intent){", 1)[1].split(
            "private void rememberReadingPlace()", 1)[0]
        self.assertIn('WorkspaceDeepLinks.isArchiveLink(uri.toString())', route)
        self.assertIn('segments.size()!=4', route)
        self.assertIn('String sourceArchive=segments.get(1),chat=segments.get(2),message=segments.get(3)', route)
        self.assertIn('!sourceArchive.equals(c.archiveId)', route)
        self.assertLess(route.index('!sourceArchive.equals(c.archiveId)'),
                        route.index('database.findMessageOrdinal(chat,message)'))

    def test_shared_notebook_markdown_source_routes_back_to_markdown_reader(self):
        activity = (JAVA / "ArchiveWorkspaceActivity.java").read_text()
        actions = activity.split("private void showAnnotationActions(ArchiveJournal.Annotation a){", 1)[1].split(
            "private void copyMaster()", 1)[0]
        self.assertIn('if("markdown-document".equals(a.archiveId))', actions)
        self.assertIn('WorkspaceDeepLinks.isDocumentLink(link)', actions)
        self.assertIn('.setClass(this,MainActivity.class)', actions)
        self.assertIn('a.archiveId.equals(c.archiveId)', actions)
        self.assertNotIn('FLAG_GRANT_READ_URI_PERMISSION', actions)

    def test_master_copy_fails_closed_on_partial_journal(self):
        activity = (JAVA / "ArchiveWorkspaceActivity.java").read_text()
        copy = activity.split("private void copyMaster(){", 1)[1].split(
            "private void chooseNotesFolder(){", 1)[0]
        self.assertIn('if(snapshot.truncatedTail)', copy)
        self.assertLess(copy.index('if(snapshot.truncatedTail)'),
                        copy.index('ArchiveMarkdownIndexes.master(snapshot.annotations)'))

    def test_async_reader_uses_captured_chat_and_viewport(self):
        activity = (JAVA / "ArchiveWorkspaceActivity.java").read_text()
        render = activity.split("private void showReader(boolean keepScroll){", 1)[1].split(
            "private void setArchiveDisplayMode", 1)[0]
        self.assertIn('final String renderedChat=active.id', render)
        self.assertIn('final ArchiveViewport initial=viewport', render)
        self.assertIn('database.messages(renderedChat,', render)
        self.assertIn('!renderedChat.equals(active.id)', render)

    def test_archive_reader_back_returns_to_same_search_page_and_filters(self):
        activity = (JAVA / "ArchiveWorkspaceActivity.java").read_text()
        self.assertIn('returnSearchTerm=term;returnSearchScope=scope;returnSearchOffset=Math.max(0,offset)', activity)
        self.assertIn('if(returnSearchTerm!=null)action("Results",v->returnToSearch())', activity)
        self.assertIn('if("reader".equals(viewMode)&&returnSearchTerm!=null){returnToSearch();return;}', activity)
        self.assertIn('showSearch(term,scope,offset)', activity)
        self.assertIn('state.putString("return_search_term",returnSearchTerm)', activity)
        self.assertIn('state.putString("search_spec_speaker",currentSearch.speaker)', activity)
        self.assertIn('state.putString("search_spec_from",currentSearch.fromDate)', activity)
        self.assertIn('state.putString("search_spec_to",currentSearch.toDate)', activity)
        self.assertIn('currentSearch=new ArchiveSearchSpec(savedQuery,', activity)
        self.assertIn('returnSearchTerm=null;returnSearchScope=null;returnSearchOffset=0', activity)

    def test_archive_selection_bindings_refuse_stale_reader_and_wrong_range(self):
        activity = (JAVA / "ArchiveWorkspaceActivity.java").read_text()
        capture = activity.split("private void captureSelection(){", 1)[1].split(
            "private void saveAnnotation(", 1)[0]
        helper = (JAVA / "workspace/archive/ArchiveSelectionScript.java").read_text()
        highlight = (JAVA / "workspace/archive/ArchiveHighlightScript.java").read_text()
        self.assertIn('ArchiveSelectionScript.capture()', capture)
        self.assertNotIn('all.indexOf(t)', capture)
        self.assertIn('selectedGeneration!=generation||selectedWeb!=reader', capture)
        self.assertIn('!selectedChat.equals(active.id)', capture)
        self.assertIn('sourceGeneration!=generation||active==null||!chat.equals(active.id)', capture)
        self.assertIn('last-first!=selectedQuote.length()', capture)
        self.assertIn('if(!present){status("Selected message is no longer loaded;', capture)
        self.assertIn('node===startNode', helper)
        self.assertIn('text.slice(start,end)!==quote', helper)
        self.assertNotIn('positions.length===1', highlight)
        self.assertIn('if(valid.length===1)at=valid[0]', highlight)

    def test_archive_backlinks_are_saved_link_only_and_source_verified(self):
        activity = (JAVA / "ArchiveWorkspaceActivity.java").read_text()
        self.assertIn('action("Backlinks",v->showIncomingArchiveLinks())', activity)
        block = activity.split("private void showIncomingArchiveLinks(){", 1)[1].split(
            "private void showAnnotationActions(", 1)[0]
        self.assertIn('if(data.truncatedTail)throw', block)
        self.assertIn('!a.isLink()||a.target==null', block)
        self.assertIn('WorkspaceDeepLinks.isArchiveLink(a.target)', block)
        self.assertIn('!archiveId.equals(parts.get(1))||!chat.equals(parts.get(2))', block)
        self.assertIn('database.findMessageOrdinal(chat,messageId)', block)
        self.assertIn('if(ordinal<0)continue', block)
        self.assertIn('showAnnotationActions(incoming.get(which))', block)
        self.assertNotIn('FLAG_GRANT_READ_URI_PERMISSION', block)

    def test_archive_import_rejects_lossy_utf8_and_orphan_surrogate_escapes(self):
        source = (JAVA / "ArchiveImportCoordinator.java").read_text()
        parser = (JAVA / "archive/ArchiveStreamParser.java").read_text()
        self.assertIn('ArchiveStreamParser.strictUtf8Reader(counting)', source)
        self.assertIn('onMalformedInput(CodingErrorAction.REPORT)', parser)
        self.assertIn('onUnmappableCharacter(CodingErrorAction.REPORT)', parser)
        self.assertIn('if(awaitingLowSurrogate && !Character.isLowSurrogate(ch))', parser)
        self.assertIn('if(!awaitingLowSurrogate && Character.isLowSurrogate(ch))', parser)
        self.assertIn('if(awaitingLowSurrogate)throw error(', parser)

    def test_json_folder_search_is_bounded_and_opens_verified_raw_match(self):
        source = (JAVA / "WorkspaceLiteralSearch.java").read_text()
        ui = (JAVA / "UnifiedSearchActivity.java").read_text()
        self.assertIn('boolean json="application/json".equalsIgnoreCase(entry.mimeType)||name.endsWith(".json")', source)
        self.assertIn('json=file.json', source)
        self.assertIn('if(file.json)openJsonHit(file,match,term)', ui)
        self.assertIn('Open verified raw passage in JSON Inspector', ui)
        json_route = ui.split("private void openJsonHit(",1)[1].split(
            "private void openMarkdownHit(",1)[0]
        self.assertIn('.setClass(this,JsonExplorerActivity.class)', json_route)
        self.assertIn('new Intent(Intent.ACTION_VIEW,file.uri)',json_route)
        self.assertIn('.putExtra(UnifiedSearchJump.EXTRA_URI,file.uri.toString())',json_route)
        self.assertIn('.putExtra(UnifiedSearchJump.EXTRA_HASH,file.hash)',json_route)
        self.assertIn('.putExtra(UnifiedSearchJump.EXTRA_LITERAL,literal)',json_route)
        self.assertIn('.putExtra(UnifiedSearchJump.EXTRA_OFFSET,match.offsetUtf16)',json_route)
        self.assertNotIn('takePersistableUriPermission',json_route)
        self.assertIn('8L<<20',source)
        self.assertIn('16L<<20',source)

    def test_json_inspector_exact_raw_hit_is_bounded_source_verified_and_one_shot(self):
        inspector=(JAVA / "JsonExplorerActivity.java").read_text()
        verifier=(JAVA / "JsonRawSearchHit.java").read_text()
        self.assertIn('private void showIncomingRawHit(Intent launch)',inspector)
        self.assertIn('JsonRawSearchHit.verify(input,expectedUri,current.toString(),hash,literal,at)',inspector)
        self.assertIn('new SpannableString(found.snippet)',inspector)
        self.assertIn('new BackgroundColorSpan(palette.accentSoft)',inspector)
        self.assertIn('Verified raw JSON · line ',inspector)
        self.assertIn('state.putBoolean("jsonSourceHitShown",sourceHitShown)',inspector)
        self.assertIn('request!=rawHitRequest',inspector)
        self.assertIn('rawHitRequest++;showIncomingRawHit(intent)',inspector)
        self.assertIn('MAX_BYTES=8*1024*1024',verifier)
        self.assertIn('UnifiedSearchJump.matches(requestedUri,actualUri,sourceSha,actualSha,',verifier)
        self.assertIn('onMalformedInput(CodingErrorAction.REPORT)',verifier)
        self.assertNotIn('openOutputStream(',verifier)

    def test_document_deep_link_is_not_externally_exported(self):
        manifest = (ROOT / "apps/MarkdownDesk/AndroidManifest.xml").read_text()
        self.assertNotIn('android:host="document"', manifest)


if __name__ == "__main__":
    unittest.main(verbosity=2)
