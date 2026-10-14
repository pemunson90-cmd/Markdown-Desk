# PrivateStateCodec v1 schema (inert Task5 prerequisite)

Base6630609. Root reviewed this schema before production code, including the
source-derived correction to preserve reversed cursor endpoints.
Only new codec and this verification directory are owned. No native file format,
SessionStore/RecoveryStore readers, archive types, preferences, UI, or storage
adoption changes. Native sessionv1, recoveryv2 and legacy pairs remain untouched.

## Source-derived values

DocumentTab/SessionStore persist source ID, URI, displayName, text, baseline hash /
modified, BOM, source readOnly/dirty, editor lock, UTF16 selection, nonnegative
scrollY values, mode0..2 and find/replace strings plus three search booleans.
RecoveryStore stores the common document/provenance/selection fields and timestamp;
it has no tab ID, dirty flag, search state, mode or scroll fields.
Find/replace UI is single-line, but persistence does not prohibit newline or NUL
characters. Preserve every valid Unicode code point in bounded metadata, including
newlines/NUL; no trim/normalization/truncation. Reject null required strings.

DocumentIO.encodeStrict prepends a BOM when its flag is true but permits a literal
leading U+FEFF in text when false. Therefore true requires an EFBBBF prefix and
excludes exactly those3bytes from cursor text counting. False decodes every byte,
including a literal leading U+FEFF. Double BOM with true retains the second U+FEFF
as text. Exact bytes and flag are preserved; no inferred flag or normalization.

Actual allowed preferences (existing MainActivity reads/writes + input controls):

| Key | Exact Java type | Range |
|---|---|---|
|ui_theme|Integer|0..8|
|font_size|Float|finite12..32|
|preview_font_size|Float|finite12..32|
|split_ratio|Float|finite0.28f..0.72f|
|word_goal|Integer|0..Integer.MAX_VALUE|
|line_numbers|Boolean|false/true|
|typewriter|Boolean|false/true|
|launch_splash|Boolean|false/true|

No current history SharedPreferences keys exist; HistoryPolicy/control records
are separate native metadata, not invented preference names. Exclude unused
`dark_theme`, onboarding `split_hint_seen`, recents, URI grants, signing, caches,
all custom-image settings/bytes and arbitrary XML. Omitted keys mean untouched;
no default expansion/deletion. Unknown keys, null values, wrong exact type and
nonfinite/out-of-range values fail the whole preference record.

## Exact proposed wire framing

All integers big-endian; no varints, Java serialization, JSON or compression.
One record per stream:

1.8ASCIIbytes `MDSTATE\n`.
2.int32 version1.
3.uint8 kind6(SESSION_TAB),7(RECOVERY),8(PREFERENCES), matching existing archive IDs.
4.int32 metadata byte length >=0.
5.int32 payload byte length >=0 (zero for preferences).
6.exact metadata bytes in fixed order below.
7.exact payload bytes, streaming.
8.32raw SHA-256 bytes over every preceding byte (header+metadata+payload).
9.true EOF, mandatory.

Header21bytes + checksum32bytes =53fixed non-payload bytes. For documents,
metadata<=32715 so **all non-payload overhead<=32768**, payload<=8,388,608
including any BOM, and complete frame<=PrivateArchive.MAX_DOCUMENT_ENTRY_BYTES.
For preferences, complete frame<=65536 (metadata<=65483, payload=0). Constant
bounds are checked before allocations/reads and before output header is emitted.
Every metadata string uses int32 UTF8 byte length then exact strict UTF8 bytes,
max4096bytes; nullable URI/hash alone use length-1 for null. Any other negative
length fails. Booleans are one byte exactly0or1. Floating values are IEEE754
binary32bits, finite and within their key's positive range. Signed integers use
canonical fixed-width bytes. Unknown kinds/version, malformed UTF8, invalid
length/range, checksum mismatch, missing/trailing bytes fail.

Common document metadata order:
`displayName`, nullable `sourceUri`, nullable lowercase64hex `sourceBaselineHash`,
int64 `sourceBaselineModified` (-1 unknown or >=0), bool `sourceReadOnly`,
bool `utf8Bom`, int32 `selectionStart`, int32 `selectionEnd`.
Each endpoint independently requires0<=endpoint<=streamed decoded UTF16 code-unit count, excluding
only an explicit flagged BOM. Cursor positions within surrogate pairs are allowed
because native Android offsets are UTF16 units and existing sources do not impose
a code-point boundary rule. Preserve reverse anchor/focus ordering: recovery capture
uses raw editor endpoints; this codec must not normalize selection direction.

Tab metadata appends:
required nonempty `sourceTabId`, bool `sourceDirty`, bool `editorLocked`,
int32 `editorScrollY`, int32 `previewScrollY` (both>=0), int32 `mode` (0..2),
required `findQuery`, required `replaceQuery`, bool `findCaseSensitive`,
bool `findWholeWord`, bool `findRegex`.
Source IDs are bounded inert strings, not paths or restored identities; current
new tabs use UUIDs but native session reader accepts other historical IDs. Do not
promise native ID validation that source does not perform. Future import generates
fresh private IDs and explicit mapping regardless of this provenance string.

Recovery metadata appends int64 timestamp>=0.
Preferences metadata: int32 entryCount0..8; entries strictly increasing by ASCII
key name, each with required string key, uint8 exact type tag1Boolean/2Integer/
3Float, then one bool byte or four integer/float bytes. Duplicates/out-of-order,
unknown key/type or trailing metadata bytes fail. Writer canonicalizes map order;
parser never silently overwrites duplicates.

## Exact proposed API and authority boundary

New public final `PrivateStateCodec` has immutable nested `Provenance`, `Tab`,
`Recovery` DTOs (final bounded strings/primitive fields), and `Record` with kind,
exactly one typed metadata value (tab/recovery/preferences), payload byte count
and streamed UTF16-unit count. Record never holds document bytes/text.

- `writeTab(Tab,int payloadBytes,InputStream payload,OutputStream frame)`
- `writeRecovery(Recovery,int payloadBytes,InputStream payload,OutputStream frame)`
- `writePreferences(Map<String,Object>,OutputStream frame)`
- `read(InputStream frame,OutputStream provisionalPayload):Record`

Methods throw IOException for malformed framing/content/metadata; input and output
streams remain caller-owned, never flushed/closed by codec. Writes may leave an
incomplete frame on streaming failure; output is provisional until successful
return. Reads stream payload to a **provisional** sink; no result is returned until
UTF8, cursor/ranges, full checksum and EOF verify. Sink bytes before failure must
never be adopted. Streaming uses a64KiB transfer array, a65540-byte decode buffer (64KiB +
4bytes for UTF8 carry) and a32768-character output buffer (64KiB), plus bounded
metadata, not document/session/archive-sized arrays. Zero-progress bulk streams
fall back to one-byte reads to prevent hangs.

All URI/baseline/readOnly/clean state is named `source*` provenance, not live
DocumentTab authority. No DocumentTab construction, ContentResolver, filesystem,
SharedPreferences or provider/grant API exists here. A future importer must
namespace-remap IDs and create `uri=null`, `baselineHash=null`,
`baselineModified=-1`, `dirty=true` drafts until explicit relink/protected save.
Future container enforces max12tabs, unique mapped IDs, active index, coherent
capture timestamp/revision, total staging budget, archive graph and adoption.
This codec alone proves none of those container/lifecycle properties.

## Verification before integration

There is no previous PrivateStateCodec; an absent API or deliberately failing
placeholder is only harness setup, not production regression evidence. Prioritize
independent handcrafted frames and
JDK strict UTF8 oracle cover fixed fields/canonical encoding, all preference
bounds/types/unknowns, invalid metadata/payload UTF8, NUL/newlines, BOM variants,
UTF16 cursor boundaries, checksum/truncation/trailing/length/zero-progress faults,
stream/output exceptions and no close/flush of caller streams. Writer/readback
roundtrip alone is insufficient. Stream12independent8MiB synthetic payloads through
bounded sink/source at128MiB heap with32MiB headroom; enforce max bulk read/write
requests and archive cap. No whole-session parsing, filesystem adoption, native
migration, grants, provider writes, restart/kill, device or M1 completion claims.
