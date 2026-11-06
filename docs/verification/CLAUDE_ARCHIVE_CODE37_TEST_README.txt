Markdown Desk 2.4.2 — Claude Archive Import 700MB Recovery Test (code37)

This is a user-test candidate built locally. No GitHub Actions were used.

Install the APK on top of the existing Markdown Desk. Do not uninstall or clear
app data. The package and original release signing identity are unchanged.

Main changes
- Local indexing is owned by a process-level coordinator instead of the
  Archive Research Activity; Fold7 size/configuration changes no longer cancel
  work simply by destroying the screen.
- A process interruption is detected on next Archive Research launch; restart
  safely from the beginning. No unsupported claim of mid-file checkpoints.
- Progress shows bytes read, messages and conversations indexed, last update,
  free storage and stages. Cancel and retry controls provided.
- Import keeps the screen awake while the Activity is visible; Android can
  still terminate app processes in the background. No foreground service was
  introduced in this test candidate.
- New database schema v2 migrates old data without deletion; new text chunks
  are no longer duplicated in separate reader and FTS storage.
- Completed generations are published by a short pointer transaction, and
  obsolete/incomplete generations are cleaned in bounded transactions.
- Indexed library and search hide unfinished generations; old completed data
  stays selected until replacement is committed.
- Fold7 archive screen uses native system-window fitting for status/nav bars.

Synthetic local tests:
- 700MB-scale source, 70,000 messages, 210,000 parts; compact DB ~958MB.
- Java core long-message, invalid JSON and journal tests passed.
- SQLite v1->v2 migration, replacement, reader reconstruction, FTS4 search,
  old-generation cleanup and failure visibility tests passed.
- Java/Android API36 compiler, D8 dex verification, apksigner and zipalign PASS.
- Editor classes.dex and all 25 splash assets are identical to the code36 APK.

Limitations:
- No Android emulator/physical Fold7 execution from this sandbox; your
  installation and import test is still required.
- This is NOT the finished seamless reader/annotation parity release.
- Import only continues while the application process remains alive; if killed,
  reopening Archive Research offers a safe restart from the beginning.
- If the original v1 database already contains large expired stages, cleanup
  makes pages reusable but may not shrink the SQLite database file immediately.

Test: Archive Research (Test) > Import Claude conversations.json. Do not
repeatedly reimport if low-storage warning appears. Capture the message and
visible stage/counts if an error occurs.