"""SQLite behavior for production v3 SQL: boundary hits and metadata-only upgrade."""
import json
from pathlib import Path
import re
import sqlite3

root=Path(__file__).resolve().parents[2]
source=(root/'apps/MarkdownDesk/src/com/example/markdowndesk/ArchiveDatabase.java').read_text()
helper=(root/'apps/MarkdownDesk/src/com/example/markdowndesk/workspace/archive/ArchiveSearchMatch.java').read_text()
overlap=int(re.search(r'OVERLAP_UNITS=(\d+)',helper)[1])

def strings(java):
    return ''.join(json.loads(s) for s in re.findall(r'"(?:\\.|[^"\\])*"',java))

c=sqlite3.connect(':memory:')
create=source.split('@Override public void onCreate',1)[1].split('@Override public void onUpgrade',1)[0]
for statement in re.findall(r'db\.execSQL\((.*?)\);',create,re.S):
    c.execute(strings(statement))
c.execute("INSERT INTO sources VALUES('old','content://one','old',1,'ready',2)")
c.execute("INSERT INTO active_versions VALUES('stable','old')")
c.execute("INSERT INTO conversations VALUES('old/chat','old','chat',NULL,NULL,1)")
c.execute("INSERT INTO messages VALUES(1,'old/chat',0,'message','human',NULL,2)")
first='prefix '+' '.join('word'+str(i) for i in range(40))
second=' ending suffix'
query=' '.join('word'+str(i) for i in range(10,40))+' ending'
assert len(query)>128 and len(query)<=overlap
for n,(body,prefix) in enumerate(((first,''),(second,first[-overlap:]))):
    c.execute('INSERT INTO parts VALUES(?,?,?,?,?,?)',(n+1,1,n,0 if n==0 else len(first),'',len(prefix)))
    c.execute('INSERT INTO search_parts(docid,body) VALUES(?,?)',(n+1,prefix+body))

# Execute the actual production search SELECT (unfiltered variant).
expression=source.split('String sql=',1)[1].split(';\n        try(Cursor',1)[0]
sql=strings(expression).replace(' ORDER BY COALESCE(m.created',
    ' WHERE search_parts MATCH ? ORDER BY COALESCE(m.created').replace(' LIMIT  OFFSET ',' LIMIT 100 OFFSET 0')
rows=c.execute(sql,('"'+query+'"',)*2).fetchall()
assert len(rows)==1 and rows[0][10], 'cross-boundary source phrase not found'
row=rows[0]
byte_offsets=list(map(int,row[10].split()))
begin=byte_offsets[2];end=byte_offsets[-2]+byte_offsets[-1]
actual=row[11].encode()[begin:end].decode()
assert actual==query
reconstructed=''.join(body[n:] for body,n in c.execute(
    'SELECT f.body,p.overlap_len FROM parts p JOIN search_parts f ON f.docid=p.row_id ORDER BY p.ordinal'))
assert reconstructed==first+second, 'index overlap changed reconstructed source'

# v2 schema metadata migration must preserve active generation and all stored text.
old=sqlite3.connect(':memory:')
old.execute('CREATE TABLE sources(id TEXT PRIMARY KEY,uri TEXT,name TEXT,indexed_at INTEGER,status TEXT)')
old.execute("INSERT INTO sources VALUES('ready','content://one','notes retained',1,'ready')")
upgrade=source.split('db.execSQL("ALTER TABLE sources',1)[1].split(');',1)[0]
old.execute('ALTER TABLE sources'+strings('"'+upgrade))
assert old.execute('SELECT * FROM sources').fetchone()==('ready','content://one','notes retained',1,'ready',2)

# First-match token offsets preserve the actual punctuation/accent text.
for body,term,expected in [('foo-bar café','foo bar','foo-bar'),('😀 café foo','cafe foo','café foo')]:
    c.execute('DELETE FROM search_parts')
    c.execute('INSERT INTO search_parts(docid,body) VALUES(1,?)',(body,))
    result=c.execute('SELECT body,offsets(search_parts) FROM search_parts WHERE search_parts MATCH ?',('"'+term+'"',)).fetchone()
    values=list(map(int,result[1].split()))
    assert result[0].encode()[values[2]:values[-2]+values[-1]].decode()==expected
print('PASS: production search SQL, boundary phrase, source reconstruction, legacy metadata and normalized spans')
