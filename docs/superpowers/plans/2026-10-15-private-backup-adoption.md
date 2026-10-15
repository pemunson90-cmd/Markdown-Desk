# Private backup and non-destructive adoption — technical design

Source: `620275b6cc0aae6d139c353d16fd1d3ec26200b9`; design only, not implementation approval.
Goal: verified backups; import preserves current drafts/history and never writes user documents.
Java/JDK17, existing non-Gradle build, min26/target35; retain 2.2.5/code18.
[ADR-001](../../decisions/ADR-001-safe-save-boundary.md),
[ADR-002](../../decisions/ADR-002-retention-journal.md) and
[ADR-004](../../decisions/ADR-004-operation-lifetime-and-close-recovery.md) apply.
Consume approved ADR-004/`8fe3874` after implementation review; its APIs are not current.

## Observed API boundary

Paths below are under `apps/MarkdownDesk/src/com/example/markdowndesk/`.

| Current source/API | Consequence for this design |
| --- | --- |
| `PrivateArchive.write(ArchiveSource, OutputStream)` / `read(InputStream, EntrySink)` | Streaming framing only; callbacks provisional; caller settles flush/close. True EOF and terminal must verify. |
| `HistoryArchiveManifest.freeze(Graph, Inspection, IdSource)` | Pure conversion; no fresh filesystem proof, pin, persistence or output receipt. Native entry IDs are frozen separately from native UUIDs. |
| `HistoryPins.begin/resume`, `ManifestIdentity.read` | BEGIN generates an operation UUID and binds opaque original manifest bytes. ALL_BUNDLE protects a complete export; exact subset only describes snapshots. |
| `HistoryCapacity.ledger/admit` and `HistoryControlStore.inspect` | Account four native sibling roots and one control allowance. Neither knows an operation-data directory or import receipt yet. |
| `ProtectionStore.putSnapshot/prepare`, `HistoryCatalog.name`, `HistoryIdentity.resolve/forkAfterVerifiedSaveAs`, `HistoryIndex.recordVerified` | Generate IDs; snapshot writes also choose timestamps, index chooses max+1. Cannot replay a predetermined import map through these APIs. |
| `HistoryController.open` / `MainActivity.getProtectionStore` | Hardcode `protected_saves`, `history_catalog`, `history_identity`, `history_index`; controller opens/registers shared policy. No restored-bundle factory. |
| `DocumentTab`, `SessionStore`, `RecoveryStore` | No live tab history ID. Session JSON, independent recovery envelope and SharedPreferences have no common atomic commit. Activity allows 12 tabs. |

## Selection and bounded storage

Recommend an isolated, read-only restored bundle plus a committed restored-items
receipt. Keep the active native bundle and session as they are. This is additive
adoption of accessible private data, not automatic replacement of current settings.

Direct append appears smaller, but current readers validate whole native roots:
a partial append can create dangling edges and disrupt existing recovery/history.
Safe append needs durable visibility filtering in every reader, predetermined
writers, sequence allocation and URI collision rules. Native `HistoryIdentity`
rejects TAB origins and requires FORK targets to be URI identities; converting
imported lineage to live TAB aliases loses meaning or changes native semantics.
An atomic active-bundle pointer additionally requires rerouting every writer and
merging current drafts/history. Neither alternative is the minimum safe slice.

Add one app-private `private_operations` data namespace, generated UUID names only;
no archive name, URI, title or imported root basename determines a local path.
It is NOT a fifth native role or a second quota/control authority. A necessary,
separately reviewed `HistoryCapacity` extension accounts all its files, stages,
retained imports and outstanding commitments under the current primary quota and
shared mutation lease. Existing role checks and native root caps remain intact.
Unknown/unreadable entries block new operation writes; current recovery reads stay
independent. No importer opens an independently funded writable history bundle.

One active backup/import preparation initially; at most 65,536 regular operation
data files globally, including stages, with checked arithmetic and explicit
per-operation expected lengths/slots. Native restored roots retain store 65,536
slots and each metadata root 4,096 entries/8 MiB. Every read/build also respects
those bounds. Retained operations consume capacity; exhaustion is an explicit
blocked result, never automatic eviction. Cleanup is a later reviewed operation.
Administrative receipts are <=16 KiB; BEGIN/receipt stages and reservations share
the existing **1 MiB/256-entry control allowance**, including pins. No payload or large mapping
uses it. Add recognized bounded receipt framing to `HistoryControlStore`; do not
put unrecognized files there. Reserve receipt space/slots before starting work.
Manifest/remap stage+final occupancy is quota-charged; logical reservation never
substitutes for actual write, file sync or directory sync.
Native mappings cap at **16,380 entries**; serialized manifest and mapping indexes
cap at **16 MiB** each. `PrivateArchive` caps are **1 GiB**, 65,536 entries and a
16 MiB framing index. For this initial single-bundle scope, allow at most 12 SESSION_TAB,
one RECOVERY and one PREFERENCES frame in addition to native entries. Include all
frame overhead and private-state bytes in combined preflight: manifest.archiveBytes
covers native records only. Too-large, staged, unknown or invalid sources fail the
whole capture; never truncate, silently omit native records or reconstruct them.

## Export capture and durable output evidence

1. On UI thread freeze copied tab values/text and only the eight allowlisted
   preferences. On the existing I/O queue capture recovery independently; capture
   fails visibly if a required present source cannot be read. Record exact capture
   order/revisions; this is a defined cut, not a claim that all files changed atomically.
2. Encode strict `PrivateStateCodec` frames once into operation data. Stream exact
   UTF-8, BOM and CRLF; malformed UTF-16/UTF-8 fails, never replaces characters.
   Assign UUIDv4 entry IDs once and persist them with order, lengths and hashes.
3. Under the native shared monitor, reconcile required authority/maintenance,
   scan fresh `HistoryGraph` and control evidence, and freeze every native record.
   Persist the original `HistoryArchiveManifest` with stage, file sync, same-directory
   move, directory sync and exact fresh readback. Persist a separate export descriptor
   with unique preparation UUID, native-manifest digest and private-state frame table.
   This <=16 MiB descriptor is the opaque original pin manifest; never regenerate it.
4. Still excluding native mutations, revalidate graph/policy/roots and every source
   envelope against the native manifest, then publish acknowledged EXPORT /
   ALL_BUNDLE BEGIN binding the descriptor via `HistoryPins`. The adapter
   needs one internal capture-and-BEGIN boundary; sequential unlocked calls are unsafe.
   Callbacks remain under existing reentrancy checks; revalidate after durability hooks.
   BEGIN chooses its UUID: persist its link to the unique descriptor hash. After death
   between BEGIN and link, match exactly that persisted descriptor, even for empty
   native history. Ambiguous/missing matches block; never guess or regenerate IDs.
5. Start ADR-004 tracked consumers only after acknowledged BEGIN. Enumerate native
   `SourceEntry`s using the original mapping; open bounded full native envelopes
   through a new role-bound adapter, not `Snapshot.bytes()` payloads. Each hash/write
   pass verifies expected envelope length/hash and inode/root identity. Read-only
   openers repeat exact bytes; missing/replaced input aborts, never synthesizes it.
6. Output only to a freshly selected create-document destination; inspect it as
   empty before opening a destructive stream. Nonempty/unknown destination fails
   without overwrite. Creation is not exclusive ownership: provider races may still
   damage that destination; native sources remain protected. Never delete failed output.
   Track open, writes, flush, close and callbacks through ADR-004 owned resources.
7. After `PrivateArchive.write` returns, settle destination flush/close, reopen a
   fresh input and run `PrivateArchive.read` through a verifying discard sink.
   Compare count/length/whole-stream hash and every descriptor to the frozen export;
   typed frames remain hash-bound. Readback input closure must also settle. No success follows from `write`, entry callbacks or pin CLOSE.
8. Freeze/drain all consumers outside the native monitor/lease. After positive
   quiescence, persist a bounded EXPORT_VERIFIED receipt with descriptor/manifest,
   BEGIN, destination identity and archive summary hashes. Receipt publication uses
   sync/move/directory-sync/readback. Then perform capability-checked RELEASED CLOSE
   with the original descriptor; report verified output and cleanup status separately.
   Missing receipt after death means unconfirmed output; explicitly re-read the same
   destination if available or export to a new destination. Never append/resume the
   provider byte stream. CLOSE failure keeps pins; cancellation never means output success.

The export descriptor/private-state map remains local. **PrivateArchive v1 types
1–9 have no manifest-entry type.** Its terminal “manifest” is only count/length/hash.
Do not add a disguised manifest frame or imply the local mapping travels with it.
A portable import derives typed native identity from each embedded envelope below.
The local capture policy is NOT recoverable from v1; quota/automatic retention are
not allowlisted preferences. Import never activates captured deletion/policy authority.
First v1 slice covers one native bundle plus private state. Retained imports block
whole-private-backup output: never omit/flatten UUID/URI/sequence namespaces. Repeated
backup/restore needs [proposed ADR-005](../../decisions/ADR-005-portable-private-bundle-format.md): an explicit
bundle manifest/discriminator, bundle-scoped entry references and inert capture
policy provenance. Keep v1 readable; no INDEX/PREFERENCES payload disguise. Until
that extension and multi-bundle tests pass, do not claim complete private backup.
Source policy/control enrollment, pin journals, signing material, SAF grants, custom
images and private Library content are excluded. Preferences are exactly
`line_numbers`, `typewriter`, `launch_splash`, `ui_theme`, `word_goal`, `font_size`,
`preview_font_size`, `split_ratio`; omissions remain omissions. No wildcard prefs copy.

## Strict import, predetermined remapping and adoption

1. Admit bounded staging before reading an untrusted archive; grow reservation in
   checked chunks before writes if total length is unknown, never exceeding 1 GiB.
   `EntrySink` writes only generated staging paths. Flush/sync/close each stage;
   no callback publishes a restored item. Require `read` return and EOF, then durable
   staged-input receipt binding summary and persisted type/ID/length/hash table digest.
2. Decode native envelopes by archive type using extracted native validators;
   require native magic/version/type/checksum/full EOF and canonical embedded UUIDs.
   Build `(role, kind, native UUID) -> archive entry UUID` from validated content;
   reject duplicate native keys even when outer entry UUIDs differ. Preparation and
   completion share one native transaction UUID but must be distinct archive entries.
   Neither outer UUIDs nor content hashes establish native identity or routing.
3. Validate the complete graph before any adoption: snapshot payload hashes/roles;
   preparation and completion exact pair/length/hash/document/revision agreement;
   checkpoint snapshot/hash/document links; identity namespace/alias uniqueness and
   origin references; index history/transaction references, metadata equality and
   unique positive sequences. Completion cannot exist without preparation. Preserve
   legitimate pending/orphan/unindexed states explicitly; never fabricate completion.
   Parse SESSION_TAB/RECOVERY/PREFERENCES with `PrivateStateCodec.read`; require
   matching inner kind, exact payload/cursors, <=12 distinct sourceTabIds, <=1 recovery
   and <=1 prefs. Missing categories remain empty, never inferred from filenames.
4. Generate and persist one immutable remap plan before constructing restored native
   output. Domain-separate snapshot, transaction, checkpoint, identity/history and
   index IDs; enforce identity base-record ID=history ID, remap FORK record IDs and
   origin edges consistently. Map native document/tab IDs and portable sourceTabIds
   consistently where equal, including TAB aliases; recovery gets a new draft ID. Map
   every reference, including denormalized index metadata. Preparation/completion use
   one new transaction UUID. Preserve sequence order in the isolated namespace.
   Bind remap to input digest, exact descriptors, destination bundle ID and policy
   admission; cap mapping at 16 MiB. Reopen reuses those exact IDs; never regenerate.
5. Build new native v1 envelopes under the isolated operation bundle using narrow
   typed deterministic encoders and preadmitted writes. Preserve snapshot payloads
   byte-for-byte, metadata timestamps/revisions and historical URI/lineage semantics;
   recompute envelope hashes for mapped fields. Original staged bytes stay immutable.
   Existing auto-ID public methods are unsuitable. Partial stages stay charged;
   retry may promote only a fully matching stage, never truncate/overwrite it.
   Partial/mismatched output blocks that attempt and leaves current state unchanged.
6. Reopen and validate every restored root and all graph edges, plus private-state
   frames and the mapping digest. File/directory sync and stable inode checks precede
   an immutable IMPORT_COMMITTED receipt binding input, mapping and restored inventory
   hashes. That receipt alone linearizes restored-items visibility. Receipt stages
   are provisional; on restart a complete matching stage may be explicitly synced,
   promoted and verified. Missing/partial/conflicting evidence remains blocked.
7. Startup discovers committed receipts independently from session/recovery; expose
   all drafts and histories in a bounded paged Restored items view. No full replacement
   session/prefs write occurs at commit. If all 12 tabs are occupied, items remain
   accessible for preview and explicit restore-to-new-copy; never evict an existing tab.
   Opening a draft uses its persisted new ID, `uri=null`, `baselineHash=null`,
   `baselineModified=-1`, `dirty=true`, `readOnly=false`; preserve editor lock, BOM,
   exact text and valid selection. Source clean/read-only/URI/baseline fields remain
   provenance. No SAF permission is restored and import makes zero provider writes.
   Already-open mapped IDs are not reopened over newer edits. Keep the imported
   original accessible after session persistence fails or the app dies.
8. Imported native history is addressed by `(restoredBundleId, mappedHistoryId)`
   through a read-only facade, never active `resolveDocument` or save routing.
   Historical URI aliases remain inert inside that facade; do not infer a new live
   tab history ID by content equality. Existing `launchProtectedCopy(snapshotId,...)`
   resolves the primary store: imported IDs cannot be passed directly. The trusted
   facade validates `(bundleId,snapshotId,hash)`, admits an exact protective copy in
   the primary store, then passes that new ID to the existing guarded picker flow.
   No arbitrary path/source URI grants authority. New saves use protected-save flow.
   Applying the eight settings is a separate explicit single SharedPreferences commit
   from a verified receipt; preserve missing keys and report failure independently.
   There is no atomic history/session/preferences adoption claim.

No automatic pruning: executor stays SELECTED-only, failed attempts remain charged,
and imported records never become active-bundle survivor proof.

## File ownership and next eligible source slices

New components below are **proposed**, not existing callable methods.
Create `PrivateOperationStore.java` for immutable data descriptors/remaps and receipt
publication; extend `HistoryCapacity`, `HistoryControlStore`, `HistoryStorage`,
`HistoryGraph`, `HistoryPruner`, `HistoryMaintenanceExecutor` and pin preflights.
Bind one authoritative operation-data ledger into all admission, graph/pressure,
replay and retirement proofs, including `prepareTerminalLocked`; operation files
are never native deletion candidates. Include receipt credit in every shared-control
reservation check; extending `ledger` alone is insufficient. Create
`PrivateBackupCoordinator.java` for capture, stream adapters and verified-output
state; add a package-private capture entry in `HistoryController.java` so shared
native handles/authority cannot be assembled from arbitrary callers.
Extract stream decode/encode helpers within `ProtectionStore.java`,
`HistoryCatalog.java`, `HistoryIdentity.java`, `HistoryIndex.java` as needed for
strict imported envelopes and predetermined IDs; public ordinary writers stay intact.
Create `PrivateImportCoordinator.java` for typed graph/remap/commit and a read-only
`RestoredItems.java` facade. `MainActivity.java` captures state and shows/open items;
`SessionStore`/`RecoveryStore` keep their current independent persistence contracts.

Next slice: **operation-data admission and persisted capture→BEGIN**, after ADR-004
review, without provider UI/importer activation. Prove total-ledger propagation and
crash-safe fixed IDs; then review export receipts, strict import and accessible
adoption separately. This document approves no source changes.

## Required verification and remaining risks

Add production-class tests under `verification/private_backup` and
`verification/private_import`; run existing archive/state/manifest/pins/capacity and
history-controller runners only when affected. No tests were run for this design.

- Capture races: graph/policy/root replacement before BEGIN, reentrant durability
  callbacks, changing repeatable openers, 16,380/16,381 mappings, combined 1 GiB
  framing, metadata caps, terminal/replay ledger changes and full shared-control credit.
- Invalid input: all native kind/UUID/reference collisions, forged completion,
  namespace/sequence conflict, bad UTF-8/BOM/cursors, duplicate prefs/tabs, every
  truncated frame/terminal and trailing bytes. Assert zero active-state/provider writes.
- Real OS write/flush/close/sync/move failures and ENOSPC in staging, mapping, provider
  output/readback and receipts; no verified output without fresh complete readback.
- Real child-JVM SIGKILL at descriptor sync/move, BEGIN/link gap, provider completion,
  receipt publication, each remapped file and import commit. Reopen twice; fixed IDs,
  no old-source rewrite, current drafts/history byte-identical, no premature visibility.
- Concurrent editing/Activity replacement and 12 full tabs; malformed session plus
  valid recovery plus committed import remain independently accessible. Persisted
  settings application failure cannot hide drafts. Verify exact BOM/CRLF payloads.

Host faults do not prove Android/provider durability; locks remain single-process.
Readback cannot promise permanence or provider exclusivity; retained invalid stages
can exhaust capacity. Android/Fold/SAF/TalkBack/process-death, canonical signing and
upgrade acceptance remain separate NOT RUN gates; no release/M1 completion claim.
