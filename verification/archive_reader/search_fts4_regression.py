"""Offline regression for ArchiveDatabase search semantics; no Android SDK needed.
SQLite's FTS4 snippet() must not run in a grouped result query.
"""
import sqlite3

c=sqlite3.connect(":memory:")
c.executescript("""
CREATE TABLE sources(id TEXT PRIMARY KEY,status TEXT);
CREATE TABLE conversations(id TEXT PRIMARY KEY,archive_id TEXT,title TEXT,updated TEXT);
CREATE TABLE messages(row_id INTEGER PRIMARY KEY,conversation_id TEXT,message_id TEXT,role TEXT,created TEXT,ordinal INTEGER);
CREATE TABLE parts(row_id INTEGER PRIMARY KEY,message_row INTEGER,content TEXT);
CREATE VIRTUAL TABLE search_parts USING fts4(body,tokenize=unicode61);
""")
c.executemany("INSERT INTO sources VALUES(?,?)",[("ready","ready"),("incomplete","importing")])
for n in range(1,105):
    c.execute("INSERT INTO conversations VALUES(?,?,?,?)",(f"c{n}","incomplete" if n==104 else "ready",f"Chat {n}","2026-11-05"))
    c.execute("INSERT INTO messages VALUES(?,?,?,?,?,?)",(n,f"c{n}",f"m{n}","human" if n%2 else "assistant","2026-11-05",n))
    for part in range(260 if n==1 else 1):
        body=f"pineapple content {n} segment {part}"
        c.execute("INSERT INTO parts(message_row,content) VALUES(?,?)",(n,body))
        doc=c.execute("SELECT last_insert_rowid()").fetchone()[0]
        c.execute("INSERT INTO search_parts(docid,body) VALUES(?,?)",(doc,body))
SQL="""WITH matched AS (
SELECT p.message_row,MIN(p.row_id) AS first_doc
FROM search_parts JOIN parts p ON p.row_id=search_parts.docid
WHERE search_parts MATCH ? GROUP BY p.message_row)
SELECT m.conversation_id,c.title,m.message_id,m.role,m.created,m.ordinal,
snippet(search_parts,'[',']','…',0,16)
FROM search_parts JOIN matched h ON h.first_doc=search_parts.docid
JOIN messages m ON m.row_id=h.message_row
JOIN conversations c ON c.id=m.conversation_id
JOIN sources s ON s.id=c.archive_id AND s.status='ready'
WHERE search_parts MATCH ?
ORDER BY COALESCE(m.created,c.updated) DESC,m.row_id ASC
LIMIT ? OFFSET ?"""
def run(limit,offset):
    return c.execute(SQL,('"pineapple"','"pineapple"',limit,offset)).fetchall()
first,second=run(60,0),run(60,60)
assert len(first)==60 and len(second)==43
assert len({r[2] for r in first+second})==103
assert not any(r[0]=="c104" for r in first+second)
assert all("[pineapple]" in r[6] for r in first+second)
assert run(1,0)[0][0]=="c1"
print("PASS: unique paginated FTS4 results; source readiness; 260 matches in one message")
