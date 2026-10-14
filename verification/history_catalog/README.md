# Durable checkpoint metadata checks

Run with JDK17 on PATH:

```sh
bash verification/history_catalog/run.sh
```

The runner compiles the actual `ProtectionStore` and `HistoryCatalog` with
`javac --release 11`. Functional cases use real temporary directories and a
host durability adapter calling `FileDescriptor.sync`, atomic same-directory
move, and directory `FileChannel.force`. They cover exact snapshot bytes and
file hashes, independent reopen, duplicate labels, strict UTF-8 byte limits,
checksummed framing, source identity/document/hash validation, nonregular and
symlink rejection, retained failures, metadata bounds, concurrent handles,
readback corruption, and unchanged v1 snapshot/preparation/completion files.

The Python portion kills a real child JVM with SIGKILL before and after each
catalog stage file sync, move and directory sync. Each of the six interruptions
is checked in two fresh JVMs. Committed checkpoints validate; retained stages
explicitly prevent complete pin enumeration. All protected source bytes and
metadata/stage bytes survive the reopen checks without rewriting.

Catalog records have magic `MDHC`, version1, a four-byte body length, five
bounded length-prefixed strict UTF-8 strings (checkpoint ID, snapshot ID,
document ID, snapshot hash, label), an eight-byte creation time, and a SHA-256
of all preceding record bytes. IDs in filenames and source links are canonical
generated UUIDs. Names preserve their exact accepted bytes; only blankness is
tested without modifying the label. Records are capped at16KiB, labels256 UTF-8
bytes, other strings4096bytes. Total metadata is separately capped at8MiB and
4096 entries, counting every retained stage. No payload is cached in a catalog
entry and no source/private record is deleted.

Pass a separate catalog directory to `HistoryCatalog`, normally the private
`history_catalog` sibling of the protection directory, beneath a trusted
canonical parent. Catalog handles for the same normalized root serialize their
operations. This is not yet the shared protection/catalog graph lock planned
for retention; no deletion path exists in this slice. `list` validates every
committed checkpoint before filtering by document. Both `list` and
`pinnedSnapshotIds` fail explicitly on any stage, unknown/corrupt record, or
dangling source. `name` can append within the remaining metadata budget while
retaining failed stages; it validates all existing committed metadata first.
The constructor permits structurally valid stages so reopening itself does not
erase evidence. There is no cleanup, rename, deletion, UI, or dirty-buffer
checkpoint operation here.

These checks establish host behavior and host process interruption only.
Android app-UID filesystem durability, sudden power loss, SAF, physical Fold,
TalkBack, upgrade, and M0/M1 acceptance remain separate NOT RUN gates.
The integrator registers this runner in the shared host suite.
