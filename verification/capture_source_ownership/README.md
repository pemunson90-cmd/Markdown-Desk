# Preparation source ownership checks

Run with configured JDK17 on PATH:

```sh
bash verification/capture_source_ownership/run.sh
```

Uses production `PreparationSources`, the actual parent-scoped HistoryStorage state,
and the existing operation backend for cross-protocol callback checks. Current
focused result: 166 assertions. This is not an end-to-end capture or device gate.

The helper precedes durable PREPARE/BEGIN. A producer capability, canonical primary
root, preparation UUID and the caller's single shared monotonic metadata budget bind
one process-owned registration. Another handle cannot replace it, change budget,
change producer, or use another UUID/root in the same parent to bypass uncertainty.
There is intentionally no reset, close-all, cold reconstruction, successful-handoff
or activation API yet. Such a route must prove actual durable handoff in the producer;
this helper alone permits only one unfinished registration per primary parent.

An owned factory transfers exclusive stream ownership; retaining a raw alias is
outside this internal contract. Factory/read/close delegates run outside the history
monitor, with participant context retained through bookkeeping. Resource admission
precedes factory invocation and remains counted through idle/EOF/read failure until
actual successful close. An uncertain factory throw/null blocks the process owner
permanently; a throwing close may be retried on the original reader thread. Freeze
rejects new opens/reads and allows close retries. Synchronous claim requires the
primary mutation lease, frozen admission, no resources or running delegates and the
original registered owner. Its proof certifies source quiescence only, not valid
artifacts, durable capture, a successful backup, or authority to publish BEGIN.

The reader exposes only bounded caller-buffer reads and close, with no delegate or
payload-allocating convenience method. Registration reserves 2048 bytes plus retained
canonical-path characters; each admitted reader reserves512 bytes before allocation.
At most64 lifetime source admissions are allowed (the current14-frame private-state
maximum requires28 admissions for two passes). This is a helper-specific bound, not a
wire-format limit or a proof of the entire simultaneous16MiB metadata budget.

Tests cover idle and EOF ownership, read/open/close failures, actual close retry,
factory runtime/null uncertainty, same-parent UUID/producer/budget replacement,
reentrant close, thread confinement, actual cross-protocol freeze/drain entry,
blocked open/read/close during concurrent freeze, budget rejection and lease-bound
single claims. Existing operation callbacks retain their established ability to
request a freeze while outstanding work still prevents terminal authority; only
preparation participants are additionally rejected from those freeze/drain entries.
Shared Budget.charge is synchronized because source admission runs outside the
history monitor while other owners charge under it. The original implementation
reproduced8459 accepted one-byte charges from an8192-byte budget. The fixed runner
checks12 rounds with8 concurrent owners, exactly8192 accepted charges each. Workspace
reservation also checks identity against the original owner's budget; supplying a
new independent budget rejects. This proves admission serialization, not the entire
capture's simultaneous heap bound.
No source callback queue, future, provider, persistence or app UI is introduced.
