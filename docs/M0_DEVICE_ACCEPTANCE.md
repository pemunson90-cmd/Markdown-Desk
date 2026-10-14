# Required M0 device evidence

This is an unrun owner checklist. Use the installed canonical 2.2.5 baseline for
baseline measurements; candidate behavior is a separate acceptance run. An
unsigned Codex APK is not installable and is not a signed update. Keep personal
files and signing material outside reports/Git.

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
