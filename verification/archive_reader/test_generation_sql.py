"""Actual SQLite FTS4 SQL validation of ArchiveDatabase v2 contracts.
Matches the Java onCreate/onUpgrade/reads/publish/cleanup SQL. No owner data.
"""
import sqlite3,tempfile,os,hashlib

def dbconn(path):
    c=sqlite3.connect(path)
    c.execute('PRAGMA journal_mode=WAL')
    return c

def v1(c):
 c.executescript('''
 CREATE TABLE sources(id TEXT PRIMARY KEY, uri TEXT NOT NULL, name TEXT, indexed_at INTEGER,status TEXT);
 CREATE TABLE conversations(id TEXT PRIMARY KEY,archive_id TEXT,title TEXT,created TEXT,updated TEXT,message_count INTEGER NOT NULL DEFAULT 0);
 CREATE INDEX conversation_dates ON conversations(updated);
 CREATE TABLE messages(row_id INTEGER PRIMARY KEY AUTOINCREMENT,conversation_id TEXT,ordinal INTEGER,message_id TEXT,role TEXT,created TEXT,segments INTEGER NOT NULL DEFAULT 0);
 CREATE INDEX message_position ON messages(conversation_id,ordinal);
 CREATE INDEX message_identity ON messages(conversation_id,message_id);
 CREATE TABLE parts(row_id INTEGER PRIMARY KEY AUTOINCREMENT,message_row INTEGER,ordinal INTEGER,start_offset INTEGER,content TEXT NOT NULL);
 CREATE INDEX parts_by_message ON parts(message_row,ordinal);
 CREATE VIRTUAL TABLE search_parts USING fts4(body,tokenize=unicode61);
 ''')

def migrate(c):
 c.executescript('''ALTER TABLE parts ADD COLUMN overlap_len INTEGER NOT NULL DEFAULT 0;
 CREATE INDEX IF NOT EXISTS conversation_source ON conversations(archive_id);
 CREATE TABLE active_versions(archive_id TEXT PRIMARY KEY,generation_id TEXT NOT NULL);
 INSERT INTO active_versions(archive_id,generation_id)
 SELECT id,id FROM sources WHERE status='ready' AND instr(id,'#staging#')=0;
 ''')

def add_chat(c,gen,archive,title,body,content_old=False):
 ch=gen+'/'+title
 c.execute('INSERT INTO conversations VALUES(?,?,?,NULL,NULL,1)',(ch,gen,title))
 c.execute('INSERT INTO messages(conversation_id,ordinal,message_id,role,created,segments) VALUES(?,0,?,?,?,1)',(ch,'m1','human','2026-11-05'))
 mid=c.execute('SELECT last_insert_rowid()').fetchone()[0]
 content=body if content_old else ''
 c.execute('INSERT INTO parts(message_row,ordinal,start_offset,content,overlap_len) VALUES(?,0,0,?,0)',(mid,content))
 part=c.execute('SELECT last_insert_rowid()').fetchone()[0]
 c.execute('INSERT INTO search_parts(docid,body) VALUES(?,?)',(part,body))
 return ch,mid

def visible(c):
 return c.execute('''SELECT c.id,av.archive_id,c.archive_id,c.title,c.created,c.updated,c.message_count
 FROM conversations c JOIN active_versions av ON av.generation_id=c.archive_id
 JOIN sources s ON s.id=av.generation_id WHERE s.status='ready' AND substr(c.id,1,length(c.archive_id)+1)=c.archive_id||'/' ORDER BY c.id''').fetchall()

def read(c,row):
 return c.execute('''SELECT CASE WHEN p.content<>'' THEN p.content ELSE substr(f.body,p.overlap_len+1) END
 FROM parts p JOIN search_parts f ON f.docid=p.row_id WHERE p.message_row=? ORDER BY p.ordinal''',(row,)).fetchall()

def search(c,q):
 return c.execute('''WITH matched AS (SELECT p.message_row,MIN(p.row_id) AS first_doc
 FROM search_parts JOIN parts p ON p.row_id=search_parts.docid
 WHERE search_parts MATCH ? GROUP BY p.message_row)
 SELECT m.conversation_id,c.title,m.message_id,m.role,m.created,m.ordinal,p.ordinal,
 snippet(search_parts,'[',']','…',0,16),av.archive_id,c.archive_id
 FROM search_parts JOIN matched h ON h.first_doc=search_parts.docid
 JOIN parts p ON p.row_id=search_parts.docid
 JOIN messages m ON m.row_id=h.message_row
 JOIN conversations c ON c.id=m.conversation_id
 JOIN active_versions av ON av.generation_id=c.archive_id
 JOIN sources s ON s.id=av.generation_id AND s.status='ready'
 AND substr(c.id,1,length(c.archive_id)+1)=c.archive_id||'/'
 WHERE search_parts MATCH ?
 ORDER BY COALESCE(m.created,c.updated) DESC,m.row_id ASC LIMIT 100 OFFSET 0''',(q,q)).fetchall()

def cleanup(c,gen):
 ids=[x[0] for x in c.execute('SELECT id FROM conversations WHERE archive_id=? OR id LIKE ? OR id LIKE ?', (gen,gen+'/%',gen+'#pending#%'))]
 for chat in ids:
  while True:
   parts=[x[0] for x in c.execute('SELECT p.row_id FROM parts p JOIN messages m ON m.row_id=p.message_row WHERE m.conversation_id=? LIMIT 200',(chat,))]
   if not parts: break
   with c:
    for row in parts:
     c.execute('DELETE FROM search_parts WHERE docid=?',(row,))
     c.execute('DELETE FROM parts WHERE row_id=?',(row,))
  with c:
   c.execute('DELETE FROM messages WHERE conversation_id=?',(chat,))
   c.execute('DELETE FROM conversations WHERE id=?',(chat,))
 with c:c.execute('DELETE FROM sources WHERE id=?',(gen,))

with tempfile.TemporaryDirectory() as folder:
 path=os.path.join(folder,'archive.db')
 c=dbconn(path);v1(c)
 # Prior completed v1 dataset and failed 160-message stage.
 c.execute("INSERT INTO sources VALUES('old','content://old',NULL,NULL,'ready')")
 c.execute("INSERT INTO conversations VALUES('old/oldchat','old','Old',NULL,NULL,1)")
 c.execute("INSERT INTO messages(conversation_id,ordinal,message_id,role,segments) VALUES('old/oldchat',0,'oldmsg','human',1)")
 oldmid=c.execute('SELECT last_insert_rowid()').fetchone()[0]
 c.execute("INSERT INTO parts(message_row,ordinal,start_offset,content) VALUES(?,0,0,'old exact text')",(oldmid,))
 oldpart=c.execute('SELECT last_insert_rowid()').fetchone()[0]
 c.execute('INSERT INTO search_parts(docid,body) VALUES(?,?)',(oldpart,'old exact text'))
 c.execute("INSERT INTO sources VALUES('old#staging#failure','content://old',NULL,NULL,'importing')")
 c.execute("INSERT INTO conversations VALUES('old#staging#failure#pending#0','old','Interrupted',NULL,NULL,1)")
 c.commit()
 migrate(c)
 assert c.execute('SELECT * FROM active_versions').fetchall()==[('old','old')]
 assert len(visible(c))==1
 assert read(c,oldmid)==[('old exact text',)]
 # Stage new data, old remains visible until pointer swap.
 gen='old#staging#new';c.execute('INSERT INTO sources VALUES(?,?,?,?,?)',(gen,'content://old','new',None,'importing'))
 newchat,newmid=add_chat(c,gen,'old','newchat','pineapple imported words')
 c.commit()
 assert len(visible(c))==1 and visible(c)[0][0]=='old/oldchat'
 assert not search(c,'"pineapple"'), 'incomplete search leak'
 # Commit pointer only, old still physically present.
 with c:
  c.execute("UPDATE sources SET status='ready' WHERE id=?",(gen,))
  c.execute('INSERT OR REPLACE INTO active_versions VALUES(?,?)',('old',gen))
 assert len(visible(c))==1 and visible(c)[0][0]==newchat
 assert search(c,'"pineapple"') and not search(c,'"old"')
 assert read(c,newmid)==[('pineapple imported words',)]
 # Gen 'old' cleanup must NOT prefix-delete gen 'old#staging#new'.
 cleanup(c,'old')
 assert visible(c)[0][0]==newchat and read(c,newmid)==[('pineapple imported words',)]
 cleanup(c,'old#staging#failure')
 assert len(visible(c))==1
 # Explicitly verify the overlap is removed from the reading text.
 c.execute('INSERT INTO messages(conversation_id,ordinal,message_id,role,segments) VALUES(?,1,?,?,2)',(newchat,'m2','assistant'))
 msg=c.execute('SELECT last_insert_rowid()').fetchone()[0]
 for ix,(body,overlap) in enumerate([('apple and pears',0),('pears and berries',5)]):
  c.execute("INSERT INTO parts(message_row,ordinal,start_offset,content,overlap_len) VALUES(?,?,?,?,?)",(msg,ix,ix*15,'',overlap))
  part=c.execute('SELECT last_insert_rowid()').fetchone()[0]
  c.execute('INSERT INTO search_parts(docid,body) VALUES(?,?)',(part,body))
 c.commit()
 assert read(c,msg)==[('apple and pears',),(' and berries',)]
 # Failed reimport never replaces current pointer.
 bad='old#staging#failed-again'
 c.execute("INSERT INTO sources VALUES(?,?,?,?,?)",(bad,'content://old','bad',None,'importing'))
 add_chat(c,bad,'old','broken','pineapple interrupted')
 c.commit()
 assert len(search(c,'"pineapple"'))==1
 cleanup(c,bad)
 assert len(visible(c))==1
 print('PASS v1→v2 migration, old ready preservation, atomic publish, FTS4 search isolation, bounded GC, compact text and overlap')
 c.close()