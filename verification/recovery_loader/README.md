# Recovery loading regression

```bash
export PATH="/workspace/.command-library-env/jdk-17.0.16+8/bin:$PATH"
bash verification/recovery_loader/run.sh
```

The JDK17 runner uses `javac --release 11`, verifies the pinned host Android JSON
JAR's SHA-256, and writes generated sources/classes/unique temporary fixture
directories only under `verification/build/recovery-loader`.

`ExtractMethods.java` uses javac AST source positions to copy the exact
`MainActivity.lambda$restoreSessionOrRecovery$42` method, `mergeNewerRecovery`
helper, and `MODE_EDIT`, `MODE_PREVIEW`, `MAX_TABS` constants from current source
into a host shell. Neither loader nor merge logic is reimplemented in fixtures.
It fails if requested members are missing or duplicated. The real SessionStore,
RecoveryStore, DocumentTab, AtomicCommit and EditorTools classes are compiled.
The isolated Context exposes only a file directory; Intent is an identity token,
and the post queue collects callbacks for explicit draining. The UI callback
`lambda$restoreSessionOrRecovery$41` is intentionally a recorder.

Coverage checks callback posting and exact forwarding; valid session with
missing/malformed recovery; both missing; missing session with valid recovery;
both malformed with both independent errors retained; malformed session with
valid v2 recovery and its original error; newer matching recovery merge including
lock/scroll/find preservation; older recovery; unmatched append; and a full
12-tab session preserving every tab and forwarding the unmerged draft.

The regression was independently RED before the loader fix: seven original
positive cases passed, while malformed session + valid v2 recovery delivered
null draft and both malformed files retained only one error. Reproduce the
underlying failure by placing `{broken` in `open-tabs.json`, writing a valid v2
RecoveryStore draft in the same fixture files directory, then invoking the actual
loader. The original enclosing SessionStore try block skipped RecoveryStore.read
entirely when session parsing threw. The desired behavior reads each checkpoint
independently, forwards the valid draft with the session IOException, and retains
both errors when both reads fail.

This proves host loader outputs and actual merge behavior, not Android process
death, Handler threading, Intent handling, recovery-dialog visibility, activity
restoration or physical-device behavior. The callback collector does not execute
the app's UI restore branch. In particular, forwarding an unmatched draft when
all 12 tabs are full does not prove the UI offers that draft for recovery.
