# Start or resume Markdown Desk work

1. Read [AGENTS.md](../AGENTS.md), especially the testing stop rule, and the owner's current request.
2. Inspect `git status`, fetch the relevant remote branch, and preserve local work before switching or editing. `V2.5.0` is the accepted 2.5.0/code39 shipping baseline; later source changes must be assessed against their actual scope.
3. Read [LIVE_WORK_LEDGER.md](handoffs/LIVE_WORK_LEDGER.md) for the active task, executed evidence, blockers, and next action. The old integration queue, STATUS chronology, and M0–M7 roadmap are historical references, not standing task orders.
4. Run `python3 verification/select_checks.py --base <last-verified-commit>` to propose the affected checks; see [TEST_SELECTION.md](TEST_SELECTION.md). Inspect the affected source and existing tests. Define the smallest relevant verification set and stopping condition. Reuse valid evidence and maintained test infrastructure. Do not repeat the entire host suite for every change or run app tests for documentation edits.
5. Complete the owner-authorized work locally, record meaningful checkpoints in the live ledger, and synchronize source to GitHub. Do not run GitHub Actions without explicit owner authorization.
6. Stop testing when the relevant checks pass. Finish delivery or the next part of the same assignment. Do not start an unrelated roadmap campaign.

For a new APK, consult [BUILD.md](BUILD.md) and [RELEASE_GATES.md](RELEASE_GATES.md). Keep signing material outside the repository; only the original signer may produce upgrades. Physical device/provider questions require device acceptance, not more host suites.

If interrupted, leave a recoverable commit and exact next operation in the live ledger. Do not create a second execution ledger.
