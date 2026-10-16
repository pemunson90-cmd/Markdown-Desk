# Portable operation records — A2 codec checkpoint

```sh
export JAVA_HOME=/workspace/.command-library-env/jdk-17.0.16+8
export PATH="$JAVA_HOME/bin:$PATH"
bash verification/portable_operation_records/run.sh
```

The focused Java11 fixture packs expected envelopes independently. It checks
legacy phase1/phase2 MDOP and domain1/domain2 bytes, portable phase3 MDOPv2
Dp/P/L, domain3 BEGIN204/CLOSE126, canonical artifact identities/order, count and
size boundaries, checksum/truncation failures and generic dispatch refusal.
Maximum portable tables contain14 private frames and19 P artifacts.

`PrivateOperationRecord.decodePortable`, portable factories and
`HistoryPinRecord.decodePortable` / `beginPortable` are structural codec entry
points. Generic MDOP readers remain v1; generic pin BEGIN readers remain domains1
and2. Existing Store/Pins/Operations parsers therefore cannot resume or activate
portable records. Deliberate version dispatch in C must follow authentic producer
and payload joins. A decoded DTO, descriptor hash or pin does not grant authority.

Record-local checks bind P's exact Dp length/hash, policy fields, required metadata
and private rows, catalog count/length, and the known minimum archive payload.
The serialized Dp/map/inventory/catalog metadata sum shares16MiB; evidence has its
own individual1GiB cap and still shares the archive total. These partial bounds do
not validate the complete archive or charge a future coordinator's live heap.
Inventory directory membership/counts, native-map/source joins, catalog routing,
capsule witnesses and evidence metadata accounting, actual source protection and
publication remain B/C/coordinator obligations. Inventory outer IDs are checked
against identities available in these records; all directory joins require I.
The future exact graph-input witness must retain the original nullable constructor
profile; A2 adds no graph fields or reconstructed proof.

Only this codec fixture runs here. Full host suites, APK/CI builds, producer
activation, device/SAF acceptance and release gates remain outside this checkpoint.
