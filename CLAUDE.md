# Required agent guidance

## MANDATORY: update stale docs after every PR/push

**Standing order from Pat: every PR and every push must leave current documentation accurate.** After opening, updating or merging a PR, and after every push, review the affected docs against the actual code and repository state. Update stale or outdated instructions, status, versions, paths, commands, links and handoffs before calling the work finished. Include known corrections with the change when possible; verify them again after publication. Do not make empty edits when the docs are already current.

- **Agentic work:** use the smallest capable model available for the documentation pass. Escalate only when the task needs it.
- **Regular chat or bots without model selection:** do the documentation work with the available model. Lack of model selection is not a reason to skip it.
- Update the existing authoritative document and its affected pointers. Preserve historical evidence as history; avoid creating competing status documents.
- End the PR/push closeout with the docs updated, or what was checked and why no update was needed. If blocked, name the stale file/claim, blocker and next action; do not claim the docs are current.
- Respect existing file ownership, approval and publication rules. Pass changes through the current coordinator at a safe boundary; preserve active work. This order does not authorize an otherwise unapproved push, merge, deletion, deployment or spending.
- Keep checks proportional to documentation changes. Do not start broad application suites or automatic push/CI loops just for this order. A docs-only follow-up still gets a freshness check; do not keep committing solely to update the follow-up's own commit identifier.

Read [AGENTS.md](AGENTS.md) before starting work. It is the shared contract for Claude and all other agents; delegated agents must receive its testing stop rule.

Read [LIVE_WORK_LEDGER.md](docs/handoffs/LIVE_WORK_LEDGER.md) for current scope, branch, evidence, and next action. The accepted shipping baseline is 2.5.0 / versionCode 39, published as `V2.5.0`. Old M0–M7 and integration handoffs are historical, not automatic task orders.

Every test must answer an unresolved question that could change the next decision. Stop once relevant checks pass; reopen only for affected changes, a failure, or a concrete concern. Documentation-only changes do not warrant application tests or builds. Reuse valid prior evidence, preserve working features/data/signing identity, and do not run GitHub Actions without explicit owner authorization.
