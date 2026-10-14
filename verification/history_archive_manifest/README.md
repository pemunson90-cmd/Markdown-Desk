# Frozen native-history manifest checks

This is a pure, inert Task 4 prerequisite for ADR-002. It freezes the mapping from
native role/kind/UUID to distinct archive UUIDv4 entries, full envelope lengths and
hashes. It does not publish pins, capture files atomically, write an archive to a
provider, validate native payloads, issue export receipts, or change a runtime
caller. [SCHEMA.md](SCHEMA.md) records the reviewed wire/API contract.

Run from the repository root with JDK 17 (the runner compiles with `--release 11`):

```sh
PATH=/workspace/.command-library-env/jdk-17.0.16+8/bin:$PATH verification/history_archive_manifest/run.sh
PATH=/workspace/.command-library-env/jdk-17.0.16+8/bin:$PATH python3 verification/history_archive_manifest/mutation_check.py
```

Observed local evidence on 2026-10-14:

- `HistoryManifestCheck`: **871 checks PASS**. Independently handwritten frames,
  all truncated prefixes, checksum and true EOF, explicit codes, canonical unsigned
  UUID order, duplicate native members/archive IDs, UUID version/variant, role name
  UTF-8 and reserved names, descriptor/policy/count/byte bounds, checked totals,
  archive feasibility, stream ownership/errors and defensive immutable metadata.
- Real native registration, snapshots, preparation/completion, named checkpoint,
  identity and index envelopes: exact lengths and full-envelope hashes; all six
  kinds retained. Preparation/completion share a native UUID but receive distinct
  archive IDs. Frozen registration/role/policy bindings are checked; failed ID
  generation returns no partial manifest. Repeated serialization reuses IDs.
- Actual `PrivateArchive.write/read` verifies category/ID/length compatibility and
  the existing v1 size calculation `64 + 58 * entries + nativeEnvelopeBytes`.
  Production archive code and native formats are unchanged.
- Synthetic immutable Graph DTOs (test-only reflective construction) exercise
  malformed descriptors, duplicate/excessive inventories, matching hashes that
  must not merge, role mismatches and canonical order independent of enumeration.
  These are admission probes, not evidence of valid native files or fresh authority.
- `ManifestMemoryCheck`: **16,392 checks PASS**, including every retained identity
  in the **16,380-entry maximum**. It streams a handcrafted metadata file through
  the real reader and writer at **`-Xmx128m` with 32 MiB held throughout**. Exact
  serialized size: **1,212,357 bytes**. The independent conservative index charge
  is **16 MiB**, not a measurement of JVM allocation. Counts 16,381, 65,536 and
  `Integer.MAX_VALUE` reject before any metadata body read. The fixture allocates
  no whole-frame/native-payload array; it tests maximal decode/replay, not the
  memory cost of constructing a caller-owned native Graph or filesystem capture.
- Five temporary copied-source guard removals were caught behaviorally: checksum,
  true EOF, archive-ID collision, native order and the whole-inventory count/index
  bound. The mutation command never edits the production worktree. These are
  failure-sensitivity checks for this new codec, not a claim of a pre-existing
  feature regression reproduced before implementation.

The **complete** manifest cap is 16 MiB. Separately, the conservative metadata
charge `4096 + 1024 * entries <= 16 MiB` yields public `MAX_ENTRIES = 16380`.
The existing archive count/index budget and 1 GiB total are also enforced. An
oversized inventory is rejected whole; there is no silent selection or truncation.
No source payload is opened by the manifest class. The only persisted names are
four bounded registered role basenames, never arbitrary paths or document URIs.

An old frozen Graph plus old matching control Inspection can still produce the
manifest for old evidence after current policy/files change. That is intentional provenance,
not a freshness check. Calling `freeze` again generates IDs again; retries must read
the persisted manifest to preserve its mapping. A future atomic capture/pin adapter must establish current
bundle, role, policy and source validity before use. A valid manifest checksum is
framing integrity, not authenticity or permission to export/delete/adopt data.

No broad host suite, benchmark, Android device, IME, Fold, SAF/provider, signing or
release gate was run for this inert unit. Root owns integration gates and runner
registration. Physical/runtime capture and publication behavior remain NOT RUN.
