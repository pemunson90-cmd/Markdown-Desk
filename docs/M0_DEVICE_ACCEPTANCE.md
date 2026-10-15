# Required M0 device evidence

This is an unrun owner checklist. Use the installed canonical 2.2.5 baseline for
baseline measurements; candidate behavior is a separate acceptance run. An
unsigned Codex APK is not installable and is not a signed update. Keep personal
files and signing material outside reports/Git.

## Galaxy Z Fold7 owner-run baseline checks

Owner-reported context is Galaxy Z Fold7, Android16 and One UI8.0. The reported
app version “2.25” has not been verified against the installed package. These
steps need no developer options or ADB and are all **NOT RUN** until recorded:

1. Open **Settings → Apps → Markdown Desk** and record the version displayed at
   the bottom. Keep existing documents and drafts; do not uninstall or clear data.
2. In Markdown Desk create a new synthetic test note, type a heading, a short
   list and `café 雪 🧭`, then save it with a unique test filename through the
   normal picker. Reopen that test file and check its text. Use only disposable
   test files, never a personal document for failure testing.
3. Check Write, Read and Split with the phone folded and unfolded. Enter
   Focus/View using the eye control; use Back to exit and check that the previous
   mode remains. Check the divider and lock/unlock using the synthetic note.
4. Open Appearance, scroll down, and change each theme. Record whether the same
   dialog remains open, scroll is retained and all text is readable. Repeat with
   a larger system font and TalkBack if available; record the exact conditions.

Record pass/fail and what happened for each case, rather than marking the whole
matrix passed. These checks cover the installed baseline only. Candidate
protected-save, interrupted-write and signed-upgrade acceptance require a
canonical signed candidate and separately prepared tests; an unsigned Codex
artifact cannot provide that evidence. Connecting the phone to a personal
computer alone does not give the remote Codex workspace ADB access.

Collect read-only context using the already installed Android platform-tools:

For optional ADB collection on the computer connected to the phone:

1. Open **Settings → About phone → Software information**, tap **Build number**
   seven times and enter the phone's unlock code when prompted.
2. Open **Settings → Developer options** and enable **USB debugging**. Connect
   the unlocked phone with a USB data cable.
3. On that computer run `adb devices -l` using its installed platform-tools.
   Accept the USB-debugging prompt on the phone. Continue when its state is
   `device`; `unauthorized` means the phone has not accepted the connection.
4. Run the collector below from this repository on that computer. This does not
   connect the phone to remote Codex; that requires a separate working ADB route.
   Review the generated context report before sharing it. Disable USB debugging
   afterward if you no longer need it.

```bash
python3 verification/device_context.py --adb /path/to/platform-tools/adb --output device-context.json
```

The collector does not stop the app, write files, change permissions, expose a
device serial, or call any UI/benchmark case passed. Review the report locally.

Use dedicated synthetic Markdown files matching the host fixtures in
`docs/benchmarks/M0_3_HOST_BASELINE.md` (10k/100k/500k words and8MiB boundary).
Open via the ordinary SAF picker. Preserve personal tabs/drafts before testing;
never uninstall the baseline or overwrite personal files to perform this matrix.

Record device model, Android version, installed app version, folded/unfolded or
phone portrait/landscape, keyboard state and exact fixture hash for each case.
Measure cold start/open, typing, find/search, tab switch, render and save with a
stated method and repeated samples. Do not invent missing milliseconds; mark
unobserved cases NOT RUN. Host timings are not phone/Fold timings.

Check Write/Read/Split, Focus/View entry/exit preserving context, locks/read-only
Save As, divider,12tabs, theme/dialog scroll preservation for all9themes,
Unicode/BOM/CRLF roundtrip, external edit conflicts, SAF grants and process
restart using synthetic drafts. Candidate protection acceptance must recover
both versions after a failed save and restore to a new copy without original
replacement. TalkBack, font scaling and measured touch bounds are device checks.

Attach only deliberately reviewed synthetic evidence. Canonical signing and
incremental-install tests require original private signing inputs locally and
separate release preparation; neither this checklist nor host results clear them.

## Integrated candidate acceptance record — not selected, NOT RUN

Prepare this record once a canonical signed candidate and the required synthetic
fixtures/provider harness are available. Do not repeat the baseline run for each
backend checkpoint. Candidate selection and observed device results are separate
from host/build evidence; this document selects no artifact or release.

| Run field | Recorded value |
| --- | --- |
| Candidate source commit | Not selected |
| Candidate APK SHA-256 / versionName / versionCode | Not selected |
| Candidate signer SHA-256 / signature verification | NOT RUN |
| Installed baseline package / version / signer | NOT RUN |
| Device / Android / One UI / date | NOT RUN |
| Provider / fixture hashes / harness version | NOT RUN |
| Keyboard / IME / system font / TalkBack conditions | NOT RUN |

Use only synthetic files and drafts, with expected original and attempted bytes
recorded before failure cases. Keep existing personal tabs/settings and install
history intact. A prepared test provider must expose controlled partial-write,
readback and permission failures; ordinary picker success cannot establish those
cases. Observe actual device behavior and attach reviewed evidence references in
the final column. Unsupported cases remain NOT RUN with the missing prerequisite.

| Case | Device scenario and required observation | Result | Evidence / conditions |
| --- | --- | --- | --- |
| SAF-1 | Save and reopen through actual local and available third-party SAF providers; exact Unicode/BOM/CRLF bytes survive, including the 8MiB boundary and rejected oversized input. | NOT RUN | — |
| SAF-2 | Revoke a synthetic document grant before read/save; surface the failure, preserve dirty edits and protected evidence, and avoid adopting a failed destination. | NOT RUN | — |
| SAF-3 | Externally edit the synthetic source before save and during the prepared final-check race; observe conflict handling, consent bound to the captured revision/hash, and remaining provider-race limits. | NOT RUN | — |
| SAF-4 | Inject truncate/partial write, flush/close failure and readback mismatch; report failure/uncertainty and recover the exact original plus attempted bytes, with no automatic rollback. | NOT RUN | — |
| REC-1 | Restore each protected version to a newly selected copy, verify exact bytes, reject the original source destination and preserve newer dirty edits and existing tabs. | NOT RUN | — |
| REC-2 | Kill the candidate process at prepared private-commit and provider-write boundaries, then reopen twice; inspect retained stages/transactions and recover both versions without an inferred successful save. | NOT RUN | — |
| REC-3 | Restart with synthetic dirty/session/legacy recovery state, a full 12-tab workspace and one deliberately invalid private record; expose recoverable drafts and diagnostics without hiding independent protection. | NOT RUN | — |
| SPACE-1 | Exercise actual low storage and logical quota exhaustion separately; failed protection blocks provider writer opening, preserves existing bytes and gives an actionable error. Retry after space remediation. | NOT RUN | — |
| SPACE-2 | Once quota controls exist: lower quota below usage, reopen and retry; retain every file, report over-quota state and verify completion uses only its exact reserved credit. Controls are currently unimplemented. | NOT RUN | Requires implemented quota controls |
| SPACE-3 | Once a maintenance UI and startup replay are enabled: preview/confirm only eligible exact groups; named/pinned/unresolved/orphan/newest/sole-local groups survive. Interrupt pruning and replay twice; changed/corrupt proofs cause zero unauthorized deletion. The SELECTED-only backend exists; no runtime deletion caller is enabled. | NOT RUN | Requires maintenance UI/startup replay and signed candidate |
| UPG-1 | Verify original signer, matching package and increasing versionCode before an incremental install over the existing signed app; no uninstall or data clear. Existing documents, grants, drafts, tabs and preferences survive. No candidate is selected. | NOT RUN | Requires canonical signed candidate |
| EDIT-1 | Software and available hardware keyboards: typing, Unicode composition, selection/cursor, cut/copy/paste, undo/redo and find/replace retain expected text and focus; typewriter behavior remains usable. | NOT RUN | — |
| UI-1 | Folded/unfolded, portrait/landscape and keyboard shown/hidden: Write/Read/Split, Focus/View entry/exit, locks/read-only Save As, divider drag and accessible adjustment preserve context and reachable controls. | NOT RUN | — |
| UI-2 | Open/switch/close/restore 12 synthetic tabs; check full-slot behavior, names, selection, dirty state and recovery without losing a tab or silently replacing content. | NOT RUN | — |
| UI-3 | All nine themes, large font and TalkBack: readable header/dialog/Save/tabs/selected states, accessible names and measured touch bounds; Appearance changes in place and retains scroll. | NOT RUN | — |
| UI-4 | Inspect all 25 approved splash assets on device for intact artwork, scaling and launch behavior; repeat orientation/Fold transitions under the actual selection conditions. | NOT RUN | — |
| UI-5 | Rapidly edit and switch tabs while preview loads; change font/theme and Write/Read/Split, fold/unfold, then scroll. Only the current document is acknowledged; duplicate/stale callbacks do not reset the viewport or store another tab's coordinates. | NOT RUN | Requires selected signed candidate |
| PRINT-1 | Print a synthetic document while rendering is pending, then edit/switch tabs and change modes. The PDF must contain the captured source/title without changing editor mode or Focus/View. Exercise unavailable service, delayed load, cancellation and Activity recreation; no stale document or second overlapping print is submitted. | NOT RUN | Requires selected signed candidate and actual print service |
| PERF-1 | Matched 10k/100k/500k-word and boundary fixtures: repeated cold open, typing, search, tab switch, render and save measurements with method/distribution and device memory observations. | NOT RUN | — |

Record PASS or FAIL only for the observed row and conditions; preserve failures,
missing prerequisites and exact evidence references. This package requests no
new phone session now. Android app-UID durability, sudden power loss, actual
providers, Fold behavior and incremental upgrade remain uncleared until observed;
host process-kill and unsigned-build results cannot fill this matrix.
