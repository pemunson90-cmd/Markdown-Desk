# Portable capture producer checkpoint

Run from the repository root with JDK17 on PATH:

```sh
JAVA_HOME=/workspace/.command-library-env/jdk-17.0.16+8 \
PATH=/workspace/.command-library-env/jdk-17.0.16+8/bin:$PATH \
bash verification/portable_capture_plan/run.sh
```

The runner compiles only the affected producer dependency closure with Java11
APIs, then executes the real registered-backend fixtures under a64MiB heap.
Payload frames use bounded generated streams: two8MiB tabs succeed without frame
arrays/spools, and nine8MiB tabs fail the real64MiB primary quota before PREPARE.

Coverage includes original funded Graph/control/ledger/witness identity, original
same-owner ClosedFrames and active B, I/catalog/Dp/P/L exact joins, mandatory raw
context and four actual canonical witness hashes, cancelled UNKNOWN stage bytes
and missing credits, physical replacement, control-slot/metadata refusals, strict
cursor sequencing, independent97-byte catalog and old local-v1 descriptor goldens.
Catalog/record decoding demonstrates inert structure; generic portable activation
continues to reject. No provider, PREPARE publication, writer, UI, import/adoption,
route4 retention, device, APK, CI or broad regression journey is claimed.

The ignored `verification/build/b1-task/report.md` records this run's exact parent
budget debit/headroom. Writer/copy/activation/readback overlap on that same original
budget remains the next B2 integration obligation; producer success does not prove
that overlap fits. C1 strict originals also require a later authentic own-transport
transition after PREPARE, with no filename-prefix or caller-boolean exception.

Review regressions bind every selected tab/recovery scalar and all eight preference
key/value pairs to commitments privately captured from actual preflight decodes.
The codec streams its existing canonical metadata writers for both commitments;
new workspace/hash admissions use actual bounded scalar lengths on the original
budget. A12-tab+recovery+preferences fixture preserves all14 supported private rows.
Whole-cursor abandonment closes the actual failed witness and physical reader;
normal row closure still requires EOF, while genuine closure uncertainty retains
its actual cursor/owner for retry. Existing PrivateStateCheck is also compiled/run
separately once when those canonical metadata writers change.
