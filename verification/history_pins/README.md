# Durable operation-pin backend verification

Run with installed JDK17 (production source remains Java11):

```sh
bash verification/history_pins/run.sh
```

The runner compiles the actual native backend and uses synthetic private history only. It runs codec/discovery assertions, lifecycle/fault checks, a 16MiB streamed manifest under a 16MiB JVM heap, and external SIGKILL at production BEGIN/CLOSE durability callbacks. Each crash fixture is reopened twice in separate JVMs. Fixture cleanup is confined to the runner's temporary directory. No Android runtime caller, provider IO or production unlink is introduced.

The backend retains one active operation and immutable BEGIN/CLOSE audit pairs in the existing shared 1MiB/256-entry administrative allowance. BEGIN is bounded to 16KiB and 256 descriptors, with explicit stable enum wire values. EXACT_SUBSET accepts only standalone STORE/SNAPSHOT native envelopes, including snapshots belonging to PREPARED transactions. It does not require a complete pruning graph or spare data quota. ALL_BUNDLE and EXACT_SUBSET both freeze every current planner mode; ordinary admitted immutable writes and policy advancement remain available. A captured policy must remain an ancestor of the current valid chain.

CLOSE is exactly 126 bytes derived from BEGIN plus RELEASED/CANCELLED. Neither outcome asserts successful export, restore, or provider verification. BEGIN reserves those 126 bytes and one slot. Every policy admission and retained-policy-stage promotion preserves that reservation. A matching CLOSE prefix converts physical bytes/slot to reserved remainder only; it does not authorize append, rewrite or outcome selection. Complete canonical stages may be explicitly synced/promoted without replacing their inode. Partial or conflicting stages remain blocked and unchanged.

Manifest identity stores a domain, opaque caller count, byte length and SHA-256. The 16MiB byte limit is a streamed input bound, not a retained buffer allowance. Resume requires the exact independently persisted original manifest and grants no output progress or provider authority. CANCELLED may omit the manifest. New subset closure, including complete CLOSE.stage promotion, freshly validates pinned sources through the last callback. Already committed CLOSED audit reconciliation never dereferences retired sources. New ALL_BUNDLE closure validates mandatory native accounting without requiring optional pruning/index completeness.

Raw committed CLOSE bytes do not release protection after restart: explicit durable acknowledgement is required. Closed audits may reconcile beside an already committed maintenance intent through a narrow read-only audit path. That path cannot promote an intent, enable ordinary writer readiness, begin another pin or resume an active pin. This avoids a restart cycle between pin acknowledgement and maintenance survivor projection.

Acknowledgements bind retained pin names/hashes and current control file/directory identities. In the same process, missing known BEGIN/CLOSE evidence cannot become a legacy no-pin bundle or be forgotten by explicit open. There is no independent durable anchor capable of detecting wholesale removal of pin evidence before a fresh process starts; such an omission-detection protocol is outside this slice.

Coverage includes:

- Canonical framing, every single-byte BEGIN corruption, all truncated CLOSE lengths, unsupported descriptor kind, descriptor/byte bounds, every canonical CLOSE prefix and exact constant accounting.
- Both scopes/outcomes, PREPARED input and unrelated incomplete optional metadata, over-quota admission, manifest mismatch, policy ancestry, immutable appends, all planner modes and the maintenance pin gate.
- Full control bytes/slots, policy publication and retained-policy-stage reservation preservation, bounded retained audit exhaustion.
- Before/after file sync, rename and directory sync; zero/partial/full/unknown/conflicting stages; source/stage/control/root replacement; nested writes; manifest stream callbacks; candidate acknowledgement isolation.
- Closed audit reconciliation after protected native sources have been legitimately retired, including two fresh JVMs with an interrupted maintenance closure.

Runtime prerequisites remain: an independently persisted original manifest, operation quiescence on **all** terminal paths, separately reviewed terminal-stage repair, output verification and any selective planner-proof integration. Reservation guarantees accounting, not eventual cancellation after a partial stage or disk fault. There is no automatic expiry, deletion, compaction or garbage collection. Physical power-loss/Android/provider acceptance and signed builds are not exercised here.
