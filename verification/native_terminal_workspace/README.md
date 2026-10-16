# Prepaid current-native terminal workspace

Run with the existing JDK17, no tool download:

    PATH=/workspace/.command-library-env/jdk-17.0.16+8/bin:$PATH bash verification/native_terminal_workspace/run.sh

The runner compiles actual production source with `--release 11` and uses
`-Xmx32m`. It verifies a bounded, reusable CURRENT-native table, not Pins terminal
integration or a complete consumer/funding/heap/lifecycle proof.

Admission on the supplied original monotonic Budget is exactly:

    W = 512KiB + 64KiB + 4*sum(rootUTF8)
        + N*(2048 + 4*(maxRootUTF8 + 1 + 2*64))

The 512KiB component funds ProtectionStore's substantive sequential native
validator. The 64KiB component covers this workspace's 8KiB hash buffer, four
root anchors, proof/visitor/digest/channel/attribute temporaries and bounded
canonical/path admission work. Per member, 2048 bytes accounts for Slot, table
reference/seen state, hash/time/key references and supported-provider opaque key
objects; the string term funds both a full path and a separate ASCII leaf name.
Root paths must be canonical sibling paths of at most4096 UTF16 units, whose
actual UTF8 lengths determine admission. Valid native leaf names are <=64 ASCII
bytes. The factory charges fixed admission temporaries first, then all table/path
credit, then the native validator before either retained workspace is allocated.
Failed later admission does not refund prior debits. Passes never charge again.

These are conservative accounting conventions, not portable upper bounds on
Java heap usage or cumulative allocation. Opaque fileKey has no portable size
maximum, and key/attribute/path/digest/channel representations depend on the
private-filesystem/JVM provider. The validator independently bounds snapshot and
transaction parsing; no payload-sized array is created by this workspace. The
single Slot table is sorted by in-place heapsort once per capture; recheck uses
binary lookup and reusable seen bits. Fresh transient metadata/attrs/paths are
bounded live; no second inventory/map or independent Budget is created.

N is an admitted resource maximum for all current physical members across four
roles. It is independent of any original capture/BEGIN membership. Legal append
before capture is accepted if it fits; growth during a callback fails the saved
proof. N+1 refuses without writes/callbacks and can retry after membership fits.
This does not guarantee terminal progress under unlimited legal append growth.

Files match length, SHA256 (or null for oversized known metadata, as the existing
terminal predicate permits), actual Objects.equals(fileKey), and last-modified
time. Four role directories match directory type, actual non-null key and
creation time. Parent timestamps are deliberately not bound. Store records and
completion reservations use the substantive native validator; metadata roles
preserve HistoryStorage.inspect's known-name acceptance, retained stages and
oversized entries while rejecting other diagnostics.

One active Proof binds owner thread, workspace and exact mutation-scope object.
A new same-thread lease cannot reuse it. Correct-monitor owner failure after
the lease ends clears evidence even before a replacement lease exists. The
admitted monitor is retained independently of later root-path alias changes.
Unauthorized thread/unlocked/nested calls
do not revoke a legitimate proof. An owner stale-lease or validation failure
clears slots/keys/anchors; owner close also clears them and can release after the
lease ends. No mutable/source/Budget authority escapes the passive lease identity.

Fixtures cover exact debit/count/attrs, post-reservation append, exactN/N+1 and
retry without writes or fresh credit, changed hashes, byte-identical distinct
inode replacement, role-root replacement, changed membership, reordered entries,
metadata stages/oversized/unknown/symlink handling, valid partial completion
reservation and corrupt prefix, distinct equal real keys and test-only rendering/
hash collisions, cleared references, foreign/unlocked/nested/closed/stale-lease
proof rejection, including owner/no-current-lease cleanup and store-root alias
replacement. `latest-output.txt` retains actual execution evidence. No APK,
device, hosted CI or broad suite is run here.
