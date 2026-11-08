# Local protection policy bridge

Run with existing JDK17 on PATH:

```sh
bash verification/local_protection_policy/run.sh
```

Actual HistoryController/capacity/control/native stores, no simulated policy
algorithm. Reuses the existing local_protection_status fixture's real-store and
controlled durability boundary without modifying it. Final **41 checks PASS**
under128MiB. First run was RED for absent update API/automaticEnabled status field.

The new controller API updates only against a genuine observed status containing
private root/bundle/generation/control-hash evidence. A canonical File is expected
intent context, not physical root identity or independent admission proof. Current
installed capacity and its fresh complete/unblocked accounting remain authoritative.
The original private two-argument status constructor remains display-only compatible;
its null root cannot authorize writes. No public raw policy evidence is exposed.

Supported existing codec choices:64/128/256/512/1024MiB and0/5/10/20/50 targets;
enabled requires a positive target. Exact triple no-op still performs fresh checks,
then leaves files/generation unchanged. Actual changes invoke existing explicit
setPolicy once and read fresh status. Failure is unconfirmed: publication may have
succeeded; there is no retry/rollback/reconcile or constructor-quota fallback.

Tests cover defaults disabled, explicit enable/disable/reopen, all existing choices,
invalid values, no-op bytes/generation, scalar ABA, cloned identical-policy foreign
root, same-root equal-generation new bundle, replacement installed same-policy
authority, private hash comparison (test reflection only), display-only refusal,
lower-than-used72MiB preserved native data with growth refusal/exact completion,
incomplete no-op refusal, uncertain stage no repair, callback policy/replacement
invalidation, post-append sampling failure with exactly one durable generation,
stale retry refusal and original fatal Error propagation.

Also ran unchanged local_protection_status (41 checks: prior40 behavior plus its
generic final-field assertion for automaticEnabled) and history_ui (123 functional,
captured-revision race and6 assertions across3 fresh JVMs). No broad capacity crash
battery/APK/device work. No Activity, Capacity/ControlStore/Executor, serialized
format, pruning/deletion/scheduling or portable code changed. This bridge does not
enable UI controls or establish automatic runtime execution/consent acceptance.
