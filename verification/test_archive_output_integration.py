#!/usr/bin/env python3
"""Source-level gate for full Archive export receipts and safe quote printing.
Actual native PDF pagination / SAF provider behavior must still run on Android.
"""
from pathlib import Path
import unittest

ROOT=Path(__file__).resolve().parents[1]
APP=ROOT/"apps/MarkdownDesk/src/com/example/markdowndesk"

class ArchiveOutputIntegration(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.activity=(APP/"ArchiveWorkspaceActivity.java").read_text(encoding="utf-8")
        cls.pdf=(APP/"ArchiveTranscriptPdf.java").read_text(encoding="utf-8")
        cls.receipt=(APP/"ExportReceipt.java").read_text(encoding="utf-8")
        cls.runner=(ROOT/"verification/archive_reader/run_transcript_integrity.sh").read_text(encoding="utf-8")

    def test_both_exports_use_closed_stream_and_verified_readback_before_success(self):
        s=self.activity
        for name in ("writeTranscriptPdf","writeTranscript"):
            method=s.split("private void "+name+"(",1)[1]
            method=method.split("    private ",1)[0]
            self.assertIn("ExportReceipt.Sink out=new ExportReceipt.Sink(",method)
            self.assertIn('getContentResolver().openOutputStream(destination,"w")',method)
            self.assertIn("try(out)",method)
            self.assertIn("ExportReceipt.verify(check,out.receipt())",method)
            self.assertLess(method.index("try(out)"),method.index("ExportReceipt.verify("))
            self.assertLess(method.index("ExportReceipt.verify("),method.index("runOnUiThread(()->status("))
            self.assertIn("do not rely on the selected document",method)
        self.assertIn("out.flush()",s)

    def test_complete_pdf_does_not_depend_on_preview_cap(self):
        p=self.pdf
        self.assertIn("database.exportGeneration(conversation)",p)
        self.assertIn("database.requireExportGeneration(conversation.archiveId,generation)",p)
        self.assertIn("ArchiveTranscriptIntegrity.complete(conversation.messages,index)",p)
        self.assertIn("database.textChunkVerified(m.id,part,count)",p)
        self.assertIn("pdf.document.writeTo(destination)",p)
        self.assertNotIn("WINDOW_MESSAGES",p)

    def test_receipt_is_streaming_and_fails_on_mismatch(self):
        r=self.receipt
        self.assertIn("MessageDigest.getInstance(\"SHA-256\")",r)
        self.assertIn("if(!closed||bytes<=0)",r)
        self.assertIn("if(total!=receipt.length||!Arrays.equals",r)
        self.assertIn("Export output readback differs",r)
        self.assertIn("if(read==0)",r)
        self.assertIn("return completed",r)
        self.assertIn("ExportReceiptCheck.java",self.runner)

    def test_quote_print_uses_existing_protected_engine(self):
        s=self.activity
        self.assertIn('action("Print quote",v->printSelectedPassage())',s)
        self.assertIn("private void printSelectedPassage(){selectedPassageAction(false,true);}",s)
        self.assertIn("t.length>65536",s)
        self.assertIn("DocumentPrint.start(this",s)
        self.assertIn("new DocumentPrint.Capture(citation",s)
        self.assertIn("ArchiveCitations.citation(",s)

if __name__=="__main__":unittest.main()
