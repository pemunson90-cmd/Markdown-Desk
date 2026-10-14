# Bound existing statistics completion delivery (2026-10-14)

Base6630609; independently authorized M2 follow-up to preview queue bounds.
Own LatestStatistics and its fixtures only. No Activity/EditorTools/count semantics,
250ms cadence, source limits or performance budget changes.

Reproduce RED first with the real helper: repeatedly request and execute worker
while main completions remain held. Existing helper drops stale results only when
main eventually runs; every completed scan can retain another callback/full input.

Keep one active calculation OR queued delivery plus one replaceable pending input.
Capture pending when the worker actually executes; requests replace it without
new worker tasks. Delivery holds that slot until consumed. Post only immutable
Result + generation, avoiding retention of the full source in the main callback.
Discard pending on invalidation/close; reject stale before calculation, after
calculation and before listener. Running calculation finishes naturally because
EditorTools does not support interruption; shutdown cannot stop a started count.

Invoke executor submission outside the state monitor so a blocked executor does
not prevent request/invalidation/close. A close/submission race may enqueue a task
which checks closed before touching input; executor rejection closes fail-safe.
Keep listener acceptance serialized with close; release active slot in finally,
including reentrant listener requests and callback exceptions. RuntimeException
from counting drops the failed result and permits pending/future work; fatal
Errors are outside recovery guarantees.

Tests: actual helper RED held-main regression, existing exact Unicode/locale/LF
semantics, pending replacement, A-B-A identities/generations, invalidation,
shutdown, executor rejection, reentrant/throwing listeners, direct executors,
real-thread completion latch/barriers and constrained pending retention. No
benchmark repeat/full suite. Host behavior is not Android/IME/Fold acceptance.
