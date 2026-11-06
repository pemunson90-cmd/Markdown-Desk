import sqlite3, os, time, resource
p=os.environ.get('ARCHIVE_STRESS_DB','/tmp/fts_compact700.sqlite')
if os.path.exists(p): os.unlink(p)
t=time.monotonic()
c=sqlite3.connect(p,timeout=30,isolation_level=None)
c.executescript('''PRAGMA auto_vacuum=INCREMENTAL;PRAGMA journal_mode=WAL; PRAGMA synchronous=FULL;
CREATE TABLE sources(id TEXT PRIMARY KEY,uri TEXT NOT NULL,name TEXT,indexed_at INTEGER,status TEXT);
CREATE TABLE active_versions(archive_id TEXT PRIMARY KEY,generation_id TEXT NOT NULL);
CREATE TABLE conversations(id TEXT PRIMARY KEY,archive_id TEXT NOT NULL,title TEXT,created TEXT,updated TEXT,message_count INTEGER NOT NULL DEFAULT 0);
CREATE INDEX conversation_dates ON conversations(updated);
CREATE TABLE messages(row_id INTEGER PRIMARY KEY AUTOINCREMENT,conversation_id TEXT NOT NULL,ordinal INTEGER NOT NULL,message_id TEXT,role TEXT,created TEXT,segments INTEGER NOT NULL DEFAULT 0);
CREATE INDEX message_position ON messages(conversation_id,ordinal);
CREATE INDEX message_identity ON messages(conversation_id,message_id);
CREATE TABLE parts(row_id INTEGER PRIMARY KEY AUTOINCREMENT,message_row INTEGER NOT NULL,ordinal INTEGER NOT NULL,start_offset INTEGER NOT NULL,content TEXT NOT NULL,overlap_len INTEGER NOT NULL DEFAULT 0);
CREATE INDEX parts_by_message ON parts(message_row,ordinal);
CREATE VIRTUAL TABLE search_parts USING fts4(body,tokenize=unicode61);
''')
source='archive#staging#valid'
c.execute('BEGIN')
c.execute('INSERT INTO sources VALUES(?,?,?,?,?)',(source,'content://synthetic/source','700 MB test',None,'importing'))
body=('reliability Unicode and blank lines\\n'+' research text ABCD1234567890 '*400)[:9950]
segments=[body[:4096],body[4096:8192],body[8192:]]
count=0
for chat in range(1400):
 conv=f'{source}/chat-{chat}'
 c.execute('INSERT INTO conversations VALUES(?,?,?,?,?,?)',(conv,source,f'Test {chat}','2026-11-06','2026-11-06',50))
 for m in range(50):
  row=c.execute('INSERT INTO messages(conversation_id,ordinal,message_id,role,created,segments) VALUES(?,?,?,?,?,?)',(conv,m,f'{chat}-{m}','human','2026-11-06',3)).lastrowid
  overlap='';offset=0
  for k,segment in enumerate(segments):
   rid=c.execute('INSERT INTO parts(message_row,ordinal,start_offset,content,overlap_len) VALUES(?,?,?,?,?)',(row,k,offset,'',len(overlap))).lastrowid
   c.execute('INSERT INTO search_parts(docid,body) VALUES(?,?)',(rid,overlap+segment))
   overlap=(overlap+segment)[-128:];offset+=len(segment)
  count+=1
  if count%160==0:c.execute('COMMIT');c.execute('BEGIN')
 if (chat+1)%100==0:
  print(f'PROGRESS chats={chat+1} messages={count} elapsed={time.monotonic()-t:.1f}s dbMB={os.stat(p).st_size/1e6:.1f} maxRSSMB={resource.getrusage(resource.RUSAGE_SELF).ru_maxrss/1024:.0f}',flush=True)
c.execute('COMMIT')
print('STAGING-DONE',count,os.path.getsize(p),flush=True)
visible=c.execute("SELECT count(*) FROM conversations c JOIN sources s on c.archive_id=s.id WHERE s.status='ready'").fetchone()[0]
assert visible==0,visible
c.execute('BEGIN')
c.execute('UPDATE sources SET status=? WHERE id=?',('ready',source))
c.execute('INSERT INTO active_versions VALUES(?,?)',('archive',source))
c.execute('COMMIT')
c.execute('PRAGMA wal_checkpoint(TRUNCATE)')
rows=c.execute('SELECT count(*) FROM messages').fetchone()[0]
parts=c.execute('SELECT count(*) FROM parts').fetchone()[0]
assert rows==70000 and parts==210000
row=c.execute('SELECT row_id FROM messages LIMIT 1').fetchone()[0]
read=''.join(v[0] for v in c.execute("SELECT substr(f.body,p.overlap_len+1) FROM parts p JOIN search_parts f ON f.docid=p.row_id WHERE p.message_row=? ORDER BY p.ordinal",(row,)))
assert read==body, ('content mismatch',len(read),len(body))
print('PASS COMPACT',dict(conversations=1400,messages=rows,parts=parts,elapsed_s=round(time.monotonic()-t,2),db_MB=round(os.stat(p).st_size/1e6,1),maxRSS_MB=round(resource.getrusage(resource.RUSAGE_SELF).ru_maxrss/1024,1)),flush=True)
c.close()