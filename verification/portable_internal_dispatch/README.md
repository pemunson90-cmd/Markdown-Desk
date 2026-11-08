# Internal portable control dispatch

Run with JDK17 on PATH:

```sh
bash verification/portable_internal_dispatch/run.sh
```

The runner compiles Java11 sources and reuses the existing real registered baseline
fixture and evidence wire helpers; it does not run their complete mains. The final
focused run passed **73 checks** with `-Xmx48m`.

It checks closed MDOPv2/domain3 controls, exact P-selected BEGIN/LINK/CLOSE joins,
preserved unused LINK credit, baseline raw-control copying, version/domain/owner
conflicts, unknown versions, and byte-exact evidence read/write. Generic v1 record
decoders/factories still refuse portable P/BEGIN/LINK. CLOSE has no domain field:
its existing framing decoder stays unchanged and its binding is checked against
the exact owning BEGIN.

Portable unclosed or staged prefixes can retain valid typed accounting but remain
unready for ordinary Pins open/recovery/resume/reconcile. An existing ordinary
Pins handle also refuses resume before reading the supplied manifest. Legacy cold
cancellation explicitly remains v1-only. A dedicated future portable coordinator
must authenticate its own prepared flow; it must not globally mark such prefixes
ready.

Partial P raw bytes remain UNKNOWN inert evidence. No stage promotion, source-owner
reset, credit reuse, activation, publication, replay or success authority follows.
The control fixtures use structurally valid typed P/BEGIN/LINK/CLOSE bound to a real
registration; artifact declarations and preimage rows are parser fixtures, not
validated backup data. No actual Writer second-capture lifecycle is exercised.

During implementation the existing affected checks also passed: operation-record
codec72, evidence codec53, baseline59 and large-stage1. The final73-check fixture
then covered the added duplicate-owner and committed-LINK/BEGIN requirements.
No aggregate suite, APK, CI or device check ran.
