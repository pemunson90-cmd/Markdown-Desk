# Guarded native history integration

Continue the reviewed `HistoryController` contract in `verification/history_ui/PLAN.md`.
Root owns Activity, existing extracted harness compatibility and central runners.
The controller worker owns only its new backend and controller fixtures until handoff.

1. Register the reviewed controller runner. Add a separate lazy controller accessor
   on the existing I/O queue; retain the raw store accessor for independent reads.
   Obtain ready shared authority before ordinary save, protected-copy restore and
   legacy recovery protection. Initialization failure must preserve recovery reads
   and block provider writes without a legacy fallback.
2. Capture immutable source/destination/tab/action-revision at save submission;
   retain the same route across explicit conflict consent. Record optional history
   only after actual coordinator success. Keep provider success, dirty text,
   recovery and continuations unchanged when optional indexing fails.
3. Extend actual-method extraction fixtures before enabling the hooks. Cover
   failed initialization with zero provider writes, same/different URI and unsaved
   Save As, consent routing, newer edits and optional metadata failure separately.
4. Add one Workspace history action. Bind asynchronous delivery and confirmations
   to request generation, exact URI, tab identity, active tab and Activity lifetime.
   Page 100 versions at a time; offer captured naming, bounded read-only comparison
   and the existing restore-to-new-copy path. Preserve global Protected saves.
5. Review the actual methods and run focused lifecycle/recovery/controller checks,
   then one coherent clean host/unsigned-build gate and required hosted check.

No automatic pruning, export/import, pin-consuming runtime operation or new native
format. Physical Fold/SAF/IME/TalkBack, signing and upgrade gates remain open.


## Integration checkpoint

Controller source is independently approved at `43f7094`; the captured-array race
was reproduced and fixed before handoff. Activity and extracted callbacks now
have scoped source review, 39/320 save checks, 15/223 recovery checks plus seven
recovery UI callbacks, and 84 real-backend history-action assertions. The action
runner also passed through a path containing spaces; three temporary-source
sensitivity probes were observed RED. These do not prove Android paint or SAF.

Ruling: keep the existing metadata fallback unchanged; no ordinary provider
metadata failure after coordinator success was reproduced. Optional indexing
owns its diagnostic channel. Real quota saturation replaces the old constructor-
quota fixture because acknowledged shared policy is now authoritative; protection
assertions still require zero provider writes and retained exact evidence.

Ruling: no durable pin is needed for the nonresumable comparison held under the
common parent monitor and generation guard. Runtime export remains unavailable
until enforced operation quiescence and terminal recovery are reviewed.

PR #19 merged after required hosted success and is integrated normally. Final
whole-branch review added 32 independent actual-method/native-backend checks.
The clean required host suite and two audited unsigned builds passed at f6ea921.
Next: required hosted validation for PR #20, then continue the campaign. All physical
and canonical release gates remain separate and uncompleted.
