# Scoped private archive codec verification

Run `bash verification/scoped_private_archive/run.sh` with the installed JDK17.
Only the two pure production codec classes and these fixtures are explicit compilation
inputs; `-sourcepath apps/MarkdownDesk/src` resolves their actual source dependency closure.
No Android/provider/filesystem adapter is substituted.

`ScopedArchiveCheck` independently packs the approved 266/843-byte archives using
DataOutputStream and SHA-256, then checks exact canonical output. Native snapshot
and private preference fixture bytes are opaque to this framing slice; these checks
are not native/private graph or payload acceptance. Existing private_archive,
private_state and history_archive_manifest runners retain their own original assertions.

`FramingCheck` covers every truncation of the small v1/v2 fixtures, exact callback
settlement, primary/suppressed errors, zero ordinary callbacks before inventory
outer-hash/reference verification, caller stream ownership, frozen-source change,
first inventory source-close rollback and full idle 16 MiB legacy reservation.
The legacy result retains the original source summary and a provisional caller spool;
these tests intentionally do not claim staged bytes were verified or made durable.

`InventoryCheck` exercises canonical scopes, shared transaction/private identity
mapping domains, arbitrary native UUIDs, retained scopes with 13 total drafts,
inert policy, reachable/cyclic/dense origin graphs, exact coordinate tags and strict
UTF-8 bounds. Dense-parent budget rejection reproduces the UUID/array-slot undercharge
found in initial review. Row collections are immutable borrowed views; callers must
release references before closing the inventory, and reserve any additional copies
or typed validation data from the same MetadataBudget. Closing releases logical
ownership only: escaped immutable lists/rows remain readable. An explicit probe
asserts this limitation; close cannot revoke references or force garbage collection.
A future coordinator must keep the inventory/result open through all borrowed
consumers and quiescence, separately reserve retained copies, and audit lifetimes
before integration. ScopedSink callbacks receive borrowed rows before a result exists;
callbacks and borrowed-metadata work must be synchronous/quiescent on return. A later
read failure closes the owner internally. No borrowed rows may outlive completion/failure
without a successful result kept open; asynchronous/independently retained copies need
separate pre-reservation and cleanup. Arbitrary callers retaining uncharged objects are outside this
accounting contract; the constrained-heap experiment does not establish such a bound.

`MemoryCheck` runs in a separate 48 MiB heap with 36 MiB held. It streams a generated
8,421,376-byte payload through read and two-pass write without a payload array,
checks matching stream hashes, exact metadata admission and caller-held budget conflict.
This is evidence for the stated workload, not an exact JVM allocator proof.

No staging format, durable spool, MDOP bridge, provider output, adoption, permission
restore, live-state mutation or complete private-backup claim is implemented here.
