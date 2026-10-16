# Bounded saved reading-place/bookmark helpers

Helper-only packet, not a user-visible feature. MainActivity/renderer/preview/session/
history/native Markdown are unchanged. Root owns the subsequent real Add/list/
restore/remove bookmark and reopen→Read journey in reader-position-contract.md.
No annotation, highlight, precise within-block DOM map or portable migration claim.

Run `PATH=/workspace/.command-library-env/jdk-17.0.16+8/bin:$PATH bash verification/reader_positions/run.sh`.
Actual new production helpers compile with --release11, pinned Android JSON host jar
and existing Context/SharedPreferences/Uri doubles; -Xmx64m. Latest result:151 checks
PASS. Initial absent-source runner was RED before implementation. The later forged
exact-hash/context/surrogate-end regression was RED before adding bounded exact
context verification. No broad suite, APK, device, hosted or aggregate run.
Actual new helpers compile against installed SDK37 android.jar with repository
`javac --release11 -encoding UTF-8 -classpath android.jar` strategy. Root owns
aggregate registration. No commits made; independent review pending.

## Stable API

`ReaderAnchor.Kind {TEXT,HEADING}`, `Status {EXACT,RELOCATED,UNRESOLVED}`.
`Cancellation.check()` may throw IOException. All capture/resolve calls are worker
operations over a caller-owned immutable CharSequence snapshot valid for the whole
call; there is no internal editor copy, provider access or retained source reference.

- `capture(CharSequence,int offset,Kind,Cancellation) -> Anchor`
- `resolve(Anchor,CharSequence,Cancellation) -> Resolution`
- immutable Anchor fields: kind,sourceHash,sourceLength,offset,quote,prefix,suffix.
- immutable Resolution fields: status,offset,matchedLength,hashedUnits,
  searchComparisons. UNRESOLVED uses offset=-1,matchedLength=0. Exact offsets and
  resolved quote ends never split supplementary codepoints. matchedLength marks
  the bounded captured passage, not necessarily an editor's original selection.
- package-private `restore(...)` validates persisted hashhex/kind/length/ranges,
  strict Unicode context and bounds; it is metadata hydration, not source authority.
  resolve additionally validates the bounded saved context on exact-hash matches.

Capture validates <=8MiB UTF16 and strict native UTF8 byte bound before copying only
bounded context. Hashing is a single streaming strict UTF8 pass using8192 scratch
bytes, no whole-document encoded clone. Invalid transient Unicode refuses only this
optional helper. Quote<=160 and prefix/suffix<=64 UTF16, all codepoint-safe. Empty
text restores only under the same exact fingerprint. HEADING uses the same context
mapping; caller supplies the current outline offset, no ordinal/fuzzy heading guess.

Changed text uses one KMP search for combined prefix+quote+suffix. Every KMP character
comparison is counted (including prefix-table construction); searchComparisons is
<=2*N+2*M, M<=288. Hash validation is separately reported as hashedUnits=N and does
not disappear into that search bound. Exact context validation costs <=288 bounded
comparisons, outside KMP. There are no per-candidate context rescans. Duplicate
matches/deletion/context change returns UNRESOLVED, without approximate jump. Two
matches suffice to refuse; incomplete scans never return a guessed match. Cancellation
is checked at entry/exit, at most4096 UTF16 units during hashing (including surrogate
pairs), and each4096 actual KMP comparisons. Reader source/HTML is never retained.

`ReaderPositionStore(Context)` retains SharedPreferences only. Its synchronous
worker-only methods use exact content URI strings, <=4096 UTF16, never titles,
decoded paths, grants or private history resolution:

- `read(String exactUri) -> DocumentState`, null means absent (corruption throws).
- `saveLocation(String,Anchor)`
- `addBookmark(String,String label,Anchor) -> Bookmark`
- `removeBookmark(String,String canonicalUUID)` and `forget(String)`.
- immutable DocumentState: uri,location,bookmarks (unmodifiable copied list).
- immutable Bookmark: id,label,createdAt,anchor; kind is bookmark.anchor.kind.

Null/empty/non-content URI refuses durable untitled bookmarks; no automatic Save As
migration. Different exact URI keys remain separate. Caller must wait for native
verified save before choosing a newly saved URI key. There is no provider access.
Labels<=80 strict UTF16.64 documents,16 bookmarks/doc,128 global. Updating any record
is allowed at capacity. New records evict ONLY least-recently-touched location-only
metadata; bookmarks are never evicted. Both location and bookmark creation may
replace that disposable location-only cache row. If no location-only victim exists,
new-record save refuses. Removed last bookmark drops an otherwise empty document.
Touches are monotonic bounded counters, not wall-clock ordering. Bookmark timestamps
are nonnegative wall-clock metadata. No document/session/history bytes are changed.

Separate reader_positions/state_v1 preference holds versioned JSON. At most524288
serialized UTF16 units and2MiB encoded UTF8, checked without an encoded whole-string
clone. The UTF16 cap implies a tighter worst-case valid UTF8 size than2MiB; the byte
check is nevertheless explicit, not independently advertised as reachable exact cap.
Private strict grammar preflight uses <=8 nesting levels, <=1024 containers, <=128
array items and <=12 fields/object, before org.json allocation. These accommodate
the schema maxima. It checks decoded duplicate keys (including Unicode escapes),
proper escaped strings, integer grammar, trailing input, and forbids permissive
comment/single-quote/bare-value syntax. Hydration checks exact field sets, version,
all enums/ranges/hashhex/Unicode, duplicate URIs and global bookmark IDs. Unknown or
corrupt/oversize state is kept intact and ordinary methods refuse to rewrite it.

Writes check commit() and return no success on false; Android may already have
changed in-memory preferences. No unsafe rollback or native/grant mutation follows.
Errors contain fixed messages without backend causes/source/URI diagnostics. Fatal
Errors propagate; no reset/cleanup of unrelated preferences is attempted.

## Meaningful evidence and limits

Fixtures verify actual independent SHA256 UTF8 oracles, exact/one-over source limits
(including multibyte8MiB), invalid surrogates, codepoint contexts/endpoints, no
source.toString/subSequence path, moved/duplicate/deleted/empty/EOF/heading anchors,
actual adversarial KMP comparison counts and cancellation during both hashing and
search. Store fixtures cover reopen/URI/SaveAs separation, immutability, LRU touches,
64 fully bookmarked refusal,16/128 limits without eviction, exact/over serialized
size, real escaping-driven write-cap refusal preserving prior bytes, checked commit
false (including changed process memory), corrupt/deep/decoded-duplicate/version/
range/enum/hash/Unicode preservation and escaped labels. Reflection checks immutable
metadata fields, not absence of the editor's canonical source.

Minimum allocation convention: caller's existing <=8MiB source snapshot is outside
these helpers; anchor hashing adds one8192-byte scratch plus fixed digest state;
KMP uses <=288-unit combined pattern and <=288-int failure table; retained anchor
text totals<=288 units plus64 hash characters. Store parses bounded JSON/state only,
with capped object counts and raw string; this is not a portable whole-heap or
cumulative-allocation proof.64MiB is a host test setting, not a measured Android
budget. Host doubles do not prove Android preference durability or WebView/Fold/
TalkBack/selection accuracy. No UI feature or process-death final-scroll guarantee.

## Independent-review correction

Independent review found post-hash bounded context/early-return cancellation could
produce metadata after retirement. Added deterministic immutable CharSequence
fixtures that only set an external cancellation flag after hashing, without changing
source text. Pre-fix runner was RED on all four cases: capture, EXACT, duplicate
UNRESOLVED and empty-anchor UNRESOLVED. The correction checks Cancellation after
capture context extraction and uses one terminal gate for every normal resolve
return, outside finally. Streaming hash/KMP comparisons/storage behavior unchanged.
Final focused139 checks PASS; actual SDK37 helper compile repeated PASS. Source
freeze pending correction re-review; no commits or broader suite/UI claims.

## Strict ASCII JSON escape correction

A separate actual-source probe found Java Character.digit and Android JSON accept
non-ASCII fullwidth hexadecimal digits. Literal `\\uＦＦＦＦ` decoded to U+FFFF in a
stored label despite the strict JSON contract. A new five-operation regression was
RED for read/saveLocation/addBookmark/removeBookmark/forget; mutations also rewrote
that malformed representation. The tiny validator correction now accepts only ASCII
0-9/a-f/A-F in Unicode escapes before org.json. All five paths refuse with exact raw
preferences preserved and zero commits; adjacent valid ASCII escapes roundtrip.
ReaderAnchor and its approved terminal-cancellation fix are unchanged. Final151
focused checks and actual SDK37 helper compilation PASS; no broader tests, feature
expansion, source format changes or commits. Root independent tiny-fix review pending.
