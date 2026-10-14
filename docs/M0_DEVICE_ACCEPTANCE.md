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
