"""Run production candidate SQL against actual FTS4 and unchanged v1/v2/v3 rows."""
import json
from pathlib import Path
import re
import sqlite3

ROOT = Path(__file__).resolve().parents[2]
SOURCE = (ROOT / 'apps/MarkdownDesk/src/com/example/markdowndesk/ArchiveDatabase.java').read_text()

def strings(java):
    return ''.join(json.loads(s) for s in re.findall(r'"(?:\\.|[^"\\])*"', java))

def sql_between(start, end):
    return strings(SOURCE.split(start, 1)[1].split(end, 1)[0])

def check(version, beta=False):
    db = sqlite3.connect(':memory:')
    create = SOURCE.split('public void onCreate', 1)[1].split('public void onUpgrade', 1)[0]
    for statement in re.findall(r'db\.execSQL\((.*?)\);', create, re.S):
        sql = strings(statement)
        if version < 3 and sql.startswith('CREATE TABLE sources'):
            sql = sql.replace(',search_format INTEGER NOT NULL DEFAULT 2', '')
        if version == 1:
            if sql.startswith(('CREATE TABLE active_versions', 'CREATE INDEX conversation_source')):
                continue
            sql = sql.replace(',overlap_len INTEGER NOT NULL DEFAULT 0', '')
        db.execute(sql)
    db.execute("INSERT INTO sources(id,uri,status) VALUES('ready','content://unavailable','ready')")
    db.execute("INSERT INTO sources(id,uri,status) VALUES('stage','content://unavailable','importing')")
    if version >= 2:
        db.execute("INSERT INTO active_versions VALUES('ready','ready')")
    if beta:
        db.execute("UPDATE sources SET search_format=3 WHERE id='ready'")
    for name in ('ready', 'stage'):
        db.execute("INSERT INTO conversations(id,archive_id,title) VALUES(?,?,?)",
                   (name + '/chat', name, 'Chat'))
    first = 'prefix ' + ' '.join('word' + str(i) for i in range(40))
    second = ' ending suffix'
    term = ' '.join('word' + str(i) for i in range(10, 40)) + ' ending'
    assert len(term) == 216

    def add(ordinal, parts, sender='human', generation='ready'):
        msg = db.execute("INSERT INTO messages(conversation_id,ordinal,message_id,role,created,segments)"
                         " VALUES(?,?,?,?,?,?)",
                         (generation + '/chat', ordinal, f'id{ordinal}', sender,
                          f'2026-11-{ordinal:02}', len(parts))).lastrowid
        prior = ''
        for part, text in enumerate(parts):
            overlap = prior[-(1024 if beta else 128):]
            cols = 'message_row,ordinal,start_offset,content'
            vals = (msg, part, len(prior), text if version == 1 else '')
            if version >= 2:
                cols += ',overlap_len'
                vals += (len(overlap),)
            doc = db.execute(f'INSERT INTO parts({cols}) VALUES({",".join("?" for _ in vals)})', vals).lastrowid
            db.execute('INSERT INTO search_parts(docid,body) VALUES(?,?)', (doc, overlap + text))
            prior += text
        return msg

    old = add(2, [first, second])
    native = add(3, [term])
    other = add(4, ['plain', ' text'], 'assistant')
    add(5, [first, second], generation='stage')
    fts_before = db.execute('SELECT docid,body FROM search_parts ORDER BY docid').fetchall()
    upgrade = SOURCE.split('public void onUpgrade', 1)[1].split('synchronized void beginImport', 1)[0]
    if version < 3:
        for statement in re.findall(r'db\.execSQL\((.*?)\);', upgrade, re.S):
            sql = strings(statement)
            if version == 1 or 'ADD COLUMN search_format' in sql:
                db.execute(sql)
    parts_before = db.execute('SELECT * FROM parts ORDER BY row_id').fetchall()
    rows_before = db.execute('SELECT * FROM messages ORDER BY row_id').fetchall()
    query = sql_between('String candidates=', 'int skip=')
    # Optional filters are assembled between the fixed WHERE and ORDER BY.
    query = query.replace(' ORDER BY', ' AND m.role=? COLLATE NOCASE ORDER BY')
    rows = db.execute(query, ('"' + term + '"', 'human')).fetchall()
    assert [r[9] for r in rows] == [native, old], rows
    assert rows[0][10] is not None
    native_sql = sql_between('private static void nativeSearchHit', 'new String[]{phrase,Long.toString(doc)}')
    result = db.execute(native_sql, ('"' + term + '"', rows[0][10])).fetchone()
    assert result[4] == term and result[5], result
    assert (rows[1][10] is None) == (not beta)
    all_rows = db.execute(query.replace(' AND m.role=? COLLATE NOCASE', ''), ('"' + term + '"',)).fetchall()
    assert [r[9] for r in all_rows] == [other, native, old]
    assert db.execute('SELECT * FROM parts ORDER BY row_id').fetchall() == parts_before
    assert db.execute('SELECT * FROM messages ORDER BY row_id').fetchall() == rows_before
    assert db.execute('SELECT docid,body FROM search_parts ORDER BY docid').fetchall() == fts_before
    assert db.execute("SELECT generation_id FROM active_versions WHERE archive_id='ready'").fetchone() == ('ready',)
    assert db.execute("SELECT search_format FROM sources WHERE id='ready'").fetchone() == (3 if beta else 2,)

for schema in (1, 2, 3):
    check(schema)
check(3, beta=True)
print('PASS: actual FTS4 candidate SQL, legacy overlap, metadata upgrade, active generations and filters')
