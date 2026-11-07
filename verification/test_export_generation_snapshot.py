"""The original conversation snapshot must remain bound to its published index generation."""
from pathlib import Path
import sqlite3
import unittest

ROOT = Path(__file__).resolve().parents[1]
DB = ROOT / 'apps/MarkdownDesk/src/com/example/markdowndesk/ArchiveDatabase.java'
WORKSPACE = ROOT / 'apps/MarkdownDesk/src/com/example/markdowndesk/ArchiveWorkspaceActivity.java'
PDF = ROOT / 'apps/MarkdownDesk/src/com/example/markdowndesk/ArchiveTranscriptPdf.java'

class ExportGenerationSnapshotTest(unittest.TestCase):
    def test_export_snapshot_wiring(self):
        source = DB.read_text()
        self.assertIn('final String generationId;', source)
        self.assertIn('c.getInt(6),c.getString(2)', source)
        self.assertIn('synchronized String exportGeneration(Conversation selected)', source)
        self.assertIn('ArchiveTranscriptIntegrity.generation(selected.generationId,generation)', source)
        self.assertNotIn('ArchiveTranscriptIntegrity.generation(generation,generation)', source)
        self.assertRegex(WORKSPACE.read_text(), r'final String generation\s*=\s*database\.exportGeneration\(conversation\);')
        self.assertRegex(PDF.read_text(), r'final String generation\s*=\s*database\.exportGeneration\(conversation\);')

    def test_old_visible_snapshot_is_rejected_after_reimport_even_if_metadata_same(self):
        db=sqlite3.connect(':memory:')
        db.executescript('''
        CREATE TABLE active_versions(archive_id TEXT PRIMARY KEY, generation_id TEXT NOT NULL);
        CREATE TABLE conversations(id TEXT PRIMARY KEY,archive_id TEXT NOT NULL,title TEXT,message_count INTEGER);
        INSERT INTO active_versions VALUES('arc','arc#gen1');
        INSERT INTO conversations VALUES('arc#gen1/chat','arc#gen1','same title',50);
        ''')
        def current():
            return db.execute('SELECT generation_id FROM active_versions WHERE archive_id=?',('arc',)).fetchone()[0]
        # This is the same generation ID selected as c.archive_id by the Java conversation-list query.
        old=db.execute('SELECT archive_id,title,message_count FROM conversations WHERE id=?',('arc#gen1/chat',)).fetchone()
        self.assertEqual(old,('arc#gen1','same title',50))
        db.executescript('''
        INSERT INTO conversations VALUES('arc#gen2/chat','arc#gen2','same title',50);
        UPDATE active_versions SET generation_id='arc#gen2' WHERE archive_id='arc';
        ''')
        # The old public ID 'arc/chat' now resolves through the new generation.
        self.assertEqual(db.execute('SELECT title,message_count FROM conversations WHERE id=?',
            (current()+'/chat',)).fetchone(),('same title',50))
        self.assertNotEqual(old[0],current(), 'stale snapshot must fail generation check')
        db.close()

if __name__ == '__main__':unittest.main(verbosity=2)
