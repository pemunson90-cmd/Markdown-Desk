# Terminal cancelled history: first admission slice

Run `bash verification/terminal_cancelled_admission/run.sh` with configured JDK17.
These are explicit canonical P/BEGIN/CANCELLED-CLOSE filesystem fixtures. They do not
invoke or claim a cold cancellation/restart API. Historical map/D bytes may be missing;
the fixture's opaque hashes are not a claim that unavailable bytes were reconstructed.

The structural control count discounts only canonical committed CANCELLED/no-LINK/
no-control-stage history. `Control.pending` and `Ledger.pending` remain true. Complete
ledger classification additionally requires current typed authority, closed pins,
fully accounted bounded declared data/missing artifacts, no other unfinished journal,
and an already present nonsymlink namespace. Missing old preparation directories keep
their distinct slot credits. Absent mask3 namespace stays ineligible; no old namespace
credit is borrowed or converted. Plan validates the original registered new owner,
root/preparation/Budget/idle binding; matching old registered owners are refused by
identity even if frozen/uncertain. The added Sources method inspects identity only.

Graph accounting completeness is separate from `pruneBlocked`. Only fully classified
historical accounting avoids the generic incomplete-preparation diagnostic. Native
validation and all other diagnostics still apply. Pruner, intent and maintenance
retirement guards are unchanged and keep refusing pending/pruning-blocked evidence.
Metadata freeze grants no publication authority. Plan/native capture accept this
specific historical baseline; Writer freshly binds its full original C0/L0 and all
old physical anchors, tracking initial pending OR its own preparation's pending state.
All prior bytes, byte/slot reservations and unused LINK credit remain in the baseline.

Selected new-capture activation requires all of its own artifacts final. Nonzero
global reservations must remain entirely attributable to eligible cancelled history;
they are never waived/debited. Original Budget identity and existing P/D/map/BEGIN/LINK
and source-envelope checks remain. No new independent metadata allowance was added;
caller snapshot preconditions and existing shared-budget workspace admissions remain.

Retained evidence now includes typed terminal historical data.stage hashes/inodes.
After validating all prior evidence, refresh monotonically retains already anchored
stages even during another unfinished P; it cannot forget them because global capture
eligibility changed. Newly mutable live-own prefixes remain excluded. Writer's
existing before-BEGIN retained-map admission already covers all baseline data keys.
No claim is made about persistent cross-discovery inode anchors or whole-heap/GC bounds.

Twelve groups pass: new capture→activate/native read→drain/CLOSE with exact old credits;
stage byte change/same-byte inode replacement/disappearance after handoff; namespace
present/absent and missing old directory; uncertain old owner; one versus two unfinished
journals including RELEASED/no-LINK refusal; cancelled LINK.stage refusal; refresh
during another P preserving old anchors while allowing its unpinned current prefix;
old-stage mutation before publication; all planner modes, stale selected execution,
intent admission and retained maintenance/retirement control validation refusal; and
ordinary-save positive control. The retained-maintenance probe invokes the actual
private Stable.control guard captured before the cancelled evidence; it does not
fabricate a retirement capability. The runner also uses the existing native/empty
plan subset, including the absent-ledger golden. Separately, HistoryManifestCheck
passed871 legacy checks. No old48-fault/96-reopen matrix, producer-wide gate, build/CI,
actual cold cancellation, two-restart cancellation test, portable backup or UI was run.
