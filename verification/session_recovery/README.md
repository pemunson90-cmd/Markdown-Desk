# Session and recovery host fixtures

Run with JDK17 on PATH:

```bash
export PATH="/workspace/.command-library-env/jdk-17.0.16+8/bin:$PATH"
bash verification/session_recovery/run.sh
```

The runner verifies the pinned host-only Android JSON JAR's SHA-256 and uses
`javac --release 11` to compile the actual `DocumentTab`, `SessionStore`,
`RecoveryStore`, `AtomicCommit`, and `EditorTools` sources. The isolated Context
stub exposes only `getFilesDir()`. The JAR is never packaged in the app; provision
it once with `bash verification/setup_host_deps.sh` if absent.

Coverage includes every public DocumentTab field and copy mutation isolation;
12 tabs with all modes and true/false read-only, lock, dirty and find flags;
selection, scroll, null URI/hash and Unicode persistence; independent expected
field values; active/mode/scroll bounds and malformed-field defaults; empty,
absent, partial and malformed files; recovery v2 and legacy pairs; CRLF, literal
UTF-8 BOM, BOM metadata and Unicode byte fidelity; legacy/v2 precedence;
abandoned stages; successful and rejected atomic replacements; stage open,
ENOSPC write and actual FileDescriptor sync failures; successful retry; and
clear operations preserving the other store and an unrelated user-file sentinel.

Each run allocates and removes only its unique fixture directory under
`verification/build/session-recovery/tmp`. Classes go under the same build
directory. Stage symlink fixtures require a Linux host with `/dev/full` and
`/dev/null`. They use filesystem behavior to trigger failures; production code
has no test hooks and is unchanged.

This is host file persistence evidence. Reopening a new Context is not real
process death. The fixtures do not establish Android JSON/runtime equivalence,
Android storage/provider behavior, physical-device recovery, power-loss
durability, directory fsync guarantees, crash timing, or UI restoration of
selection/mode/locks. Store-level persistence does not prove MainActivity's
12-tab limit or selection validation. Malformed committed metadata raises
IOException rather than silently falling back to a staged file.
