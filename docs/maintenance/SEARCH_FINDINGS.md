# Indexed search findings — 2026-11-07

Bounded review of the released 2.5.0 implementation. These are confirmed index-level defects, not evidence of lost source data. No runtime changes or APK were produced during this review.

## Source repair

The repair retains native FTS4 hits and checks eligible multipart misses against the original parts under the same read snapshot. It never rewrites indexed text or reopens the source URI. Schema v3 retains the code40 beta's `search_format` metadata so stable and beta databases both open. The only v2 upgrade operation adds that defaulted metadata column. A future package intended to replace the separately built code40 beta needs a version code above 40.

Indexed results carry generation-bound UTF-16 source coordinates. The reader marks the exact original passage above its regular Markdown view and keeps the existing return route. An unusually long token-phrase span shows both ends with an omission marker. Native row matches keep existing behavior. Newly recovered results treat `^` as a message-start anchor and embedded-quote clauses as conjunctions within a message.

Focused local checks are `verification/archive_reader/run_indexed_search_repair.sh`, `verification/archive_navigation/run.sh`, `verification/test_search_result_return.py` and the source-only SDK37 compile. Pat’s v42 Fold screenshots confirmed scrolling and highlighting for normalized and boundary-spanning matches. For subsequent source-only fixes and current build state, see the [live work ledger](../handoffs/LIVE_WORK_LEDGER.md). The findings below describe the released 2.5.0 implementation.

## 1. Accepted long phrases can disappear at segment boundaries

**Evidence:** `ArchiveDatabase.textSegment()` retains only 128 UTF-16 units of preceding text (`ArchiveDatabase.java`, currently line 140). `ArchiveSearchSpec` accepts phrases up to 1,024 units (currently line 19). FTS searches each persisted segment independently. A 216-character literal phrase crossing a boundary is therefore absent from both search rows, even though it exists in the reconstructed message. This applies to conversation imports and generic JSON indexing.

**User effect:** a pasted quotation can return no indexed result depending on where its words fall relative to a segment boundary.

**Existing-index constraint:** simply increasing the overlap constant fixes only newly imported rows. Existing v2 databases retain their old FTS bodies and `overlap_len`; the v1-to-v2 migration does not rebuild them. A complete repair must cover existing ready generations without changing source/message identity or corrupting reconstructed/exported text.

**Proposed repair:** define one supported search contract shared by query validation and indexing, then implement either a bounded boundary-search fallback for old indexes or an explicitly versioned, resumable rebuild of their FTS bodies and overlap metadata from stored original segments. Preserve cancellation and atomic publication. Do not synchronously rewrite a potentially gigabyte-scale database in `onUpgrade`. A larger character overlap alone is not a proof for arbitrarily long punctuation/whitespace runs under token-based matching; the boundary strategy must reflect FTS token semantics.

**Minimum acceptance:** a phrase crossing a boundary in an existing 128-unit-overlap fixture and in a new fixture must be found; reconstructed text must remain identical. Also cover the selected repair's cancellation/atomicity if it writes indexes. No 700 MB run is needed merely to prove phrase matching.

## 2. FTS matches do not always have a literal reader highlight

**Evidence:** `ArchiveDatabase.onCreate()` uses FTS4 `unicode61`, and `search()` constructs a quoted token phrase. This matches `foo bar` against `foo-bar`, and `cafe` against `café`. `ArchiveWorkspaceActivity.onPageFinished()` passes the original query to literal `WebView.findAllAsync()` (currently lines 560–562). That literal string is not present, so highlighting cannot identify the indexed hit, although the reader opens the matching message/segment window.

**User effect:** a valid result can open with no matching passage highlighted; a long segment makes the promised exact-hit navigation misleading.

**Proposed repair:** obtain the matched byte spans from the same FTS match (for example, FTS4 `offsets()`), convert UTF-8 byte positions to source UTF-16 positions, account for `overlap_len`, and carry the verified span plus generation/message/part identity to the reader. Highlight and scroll to that actual span. Keep multiple-token phrase and overlap-predecessor cases explicit. Do not replace token search with literal search silently, and do not guess a span from bracketed snippets, since source text can contain those brackets.

**Minimum acceptance:** punctuation and accented-text fixtures must resolve to the correct original span, with an overlap predecessor case. After that source fix, one actual Android WebView check answers the remaining scroll/highlight question. Repeating host suites cannot establish WebView behavior.

## Executed reproduction

A single in-memory SQLite FTS4 probe, using the production tokenizer and overlap policy, produced:

```text
Cross-boundary phrase length 216 literal exists True FTS count 0
query 'foo bar' FTS count 1 literal exists False
query 'cafe' FTS count 1 literal exists False
```

Reproduce locally with Python's existing SQLite binding; no dependency installation or application build:

```python
import sqlite3
c = sqlite3.connect(':memory:')
c.execute('CREATE VIRTUAL TABLE search_parts USING fts4(body,tokenize=unicode61)')
first = 'prefix ' + ' '.join('word' + str(i) for i in range(40))
second = ' ending suffix'
query = ' '.join('word' + str(i) for i in range(10, 40)) + ' ending'
for body in (first, first[-128:] + second):
    c.execute('INSERT INTO search_parts(body) VALUES (?)', (body,))
count = lambda q: c.execute(
    'SELECT count(*) FROM search_parts WHERE search_parts MATCH ?',
    ('"' + q + '"',)).fetchone()[0]
print('Cross-boundary phrase length', len(query),
      'literal exists', query in first + second, 'FTS count', count(query))
c.execute('DELETE FROM search_parts')
c.execute('INSERT INTO search_parts(body) VALUES (?)', ('foo-bar café',))
for q in ('foo bar', 'cafe'):
    print('query', repr(q), 'FTS count', count(q),
          'literal exists', q in 'foo-bar café')
```

This probe establishes SQLite index semantics. It does not execute the Android activity or assert observed device behavior. The existing-index requirement makes a one-line overlap change incomplete, so this review deliberately records a complete repair target rather than introducing a partial runtime fix.
