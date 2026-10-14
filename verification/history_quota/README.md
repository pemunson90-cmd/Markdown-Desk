# Superseded parallel capacity protocol

The `history_capacity` implementation and its 18-case/17-SIGKILL fixtures at
`11713385b8f98b8181524d85aa342e4069219ee0` remain preserved in Git history.
They are historical evidence, not current authority acceptance. Independent
production probes exposed uncertain-policy completion, reentrant writes, unsynced
policy-inode replacement and fresh-process missing-root legacy fallback.

The coherent replacement uses `HistoryCapacity`, `history_control` and durable
parent enrollment evidence, sharing one 1MiB/256-entry administrative allowance.
Run `bash verification/history_capacity/run.sh all` for the maintained production
fixtures, including fresh-process failures and actual interrupted publication.
See its README and ADR-002 for current contracts and acceptance limits.

The parallel pure planner and its 427/19 assertions at `6d55d9a` are likewise
preserved in history. The maintained planner, explicit mode/proof boundaries and
actual native quota-pressure fixture run through
`bash verification/history_planner/run.sh`. No deletion authority is implemented.
