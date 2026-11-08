# Required agent guidance

Read [AGENTS.md](AGENTS.md) before starting work. It is the shared contract for Claude and all other agents; delegated agents must receive its testing stop rule.

Read [LIVE_WORK_LEDGER.md](docs/handoffs/LIVE_WORK_LEDGER.md) for current scope, branch, evidence, and next action. The accepted shipping baseline is 2.5.0 / versionCode 39, published as `V2.5.0`. Old M0–M7 and integration handoffs are historical, not automatic task orders.

Every test must answer an unresolved question that could change the next decision. Stop once relevant checks pass; reopen only for affected changes, a failure, or a concrete concern. Documentation-only changes do not warrant application tests or builds. Reuse valid prior evidence, preserve working features/data/signing identity, and do not run GitHub Actions without explicit owner authorization.
