# Portable wire codecs: A1 affected checks

Run with JDK17 (source/API target Java11, minSdk26):

```sh
export JAVA_HOME=/workspace/.command-library-env/jdk-17.0.16+8
export PATH="$JAVA_HOME/bin:$PATH"
bash verification/portable_wire_codecs/run.sh
bash verification/private_archive/run.sh codec
```

The focused runner checks independently packed legacy v1/v2 goldens, explicit
archive3/schema2 inventory and type11 framing, strict version rejection, original
parent monotonic accounting, incremental v1 workspace/index boundaries, and the
existing legacy spool failure/truncation lifecycle through a narrow selector.
The old central fixtures now describe the explicit v1 type set and actual shared
metadata exhaustion instead of requiring the superseded whole16MiB reservation.

Separate32MiB processes stream a9,437,187-byte type11 payload with24MiB already
held, and a capsule containing more than9MiB of UNKNOWN stage bytes. Neither
requires a payload array. Capsules validate names/order/counts/physical hashes,
raw context/policy envelopes and canonical closed domain1 legacy pairs. Invalid
context, stage/final conflicts, bounds, checksum, truncation and sink failures
refuse provisionally. Raw control/context bytes count as metadata; raw operation
data does not. Closing releases local accounting without refunding the original
parent debit. Byte/scratch bounds do not establish whole-coordinator heap bounds.

Framing accepts opaque type11 bytes; only the separate capsule codec checks its
grammar. Neither success establishes semantic backup completion, source routing,
activation, publication or import authority. Witness-to-baseline, full source
ledger/graph/native joins, trusted final-source byte comparisons, coordinated
adoption and producer omission proof remain later Store/B/C/coordinator work.
No filename prefix permits an omission. The codec accepts only UNKNOWN relation0
until a final-source comparison API exists; it never reconstructs failed bytes.
The canonical empty56-byte capsule is a wire grammar case; actual captures must
include mandatory raw context even when they contain no older operation.

This slice does not run the full scoped4732-check suite or its7 memory checks,
full host tests, APK builds, CI, device/SAF/IME acceptance or release gates.
