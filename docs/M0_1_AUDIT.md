# M0.1 initial audit — 2026-10-14

Issue: https://github.com/pemunson90-cmd/Markdown-Desk/issues/1

Base commit: `60f3acbdfca399e99f5f1754a4d66b3fddd1f4b4`.

## Actually executed

- Cloned repository and inspected all tracked paths using `git ls-files`.
  Nine files, all planning/configuration; no application source.
- Searched attached workspace filenames for ZIP/APK, Android jar, build scripts
  and Markdown Desk artifacts. No exact 2.2.5 source ZIP or baseline APK found.
  `library-files`, `shared` and `scratch` expose no matching artifacts.
- Inspected managed environment readiness and network policy: connected;
  package-manager hosts allowed through the configured proxy; no secret bindings.
- Ran `java -version`: shell PATH selects OpenJDK 21.0.12.1.
  Ran `javac -version`: failed, command not found on PATH.
- Ran `"$JAVA_HOME/bin/java" -version` and `"$JAVA_HOME/bin/javac" -version`:
  Temurin 17.0.16+8 and javac 17.0.16, respectively (success).
- Listed `"$ANDROID_HOME/platforms"` and `"$ANDROID_HOME/build-tools"`:
  only `android-37.0` and `36.0.0`. Read installed `source.properties` to
  corroborate API 37.0/revision 2 and build-tools 36.0.0.
- Ran `"$ANDROID_HOME/build-tools/36.0.0/aapt2" version`:
  `Android Asset Packaging Tool (aapt) 2.20-13193326` (success).
- Ran `"$ANDROID_HOME/build-tools/36.0.0/d8" --version`:
  `D8 8.10.9-dev`, build `a7ad18a70460b799d0482e497c109a75bf7f91de`
  (success).
- Ran `"$ANDROID_HOME/platform-tools/adb" devices -l`: empty device list.

These are environment checks, not an Android application build or functional
verification. No dependencies were installed and no signing keys were accessed
or generated. The build will need to select `JAVA_HOME/bin` explicitly rather
than rely on the current PATH.

## Missing inputs / blockers

1. Exact `MarkdownDesk-2.2.5-source.zip`, including `apps/`, `shared/` and
   `verification/`. No archive digest or extracted inventory can be reported
   without it. ChatGPT Library access is unavailable through this session's tools.
2. Explicit public-distribution clearance for bundled artwork and other assets,
   especially all 25 splashes, before source publication. Asset review has not
   occurred; no placeholders or asset omissions were introduced.
3. Required build-tools 36.1.0 is not installed. Resolve using the existing SDK
   after source inspection; do not claim 36.0.0 is equivalent. Platform 37.0
   meets the documented 35+ range but has not been validated against this source.
4. Signed baseline APK for independent checksum/certificate verification, kept
   outside this public repo. Private signing keys and credentials are not inputs
   for this unsigned-build task.
5. Device/emulator access for runtime checks and an actual Fold for owner
   acceptance; neither is established by host tool availability.

## Verification not run

- Archive integrity, SHA256SUMS, source inventory or byte-for-byte import comparison.
- Source/privacy/asset audit and public publication decision.
- `verification/run_host_checks.sh` (runner absent).
- `apps/MarkdownDesk/build.sh` and clean/offline unsigned rebuild (source absent).
- Compiled manifest inspection: package, 2.2.5/code 18, minSdk 26, targetSdk 35.
- Baseline APK digest/certificate validation. Values in import documentation are
  historical release records, not independently confirmed in this session.
- Functional, emulator, phone/Fold, data safety, upgrade and benchmark checks.

## Behavior and release disposition

No application files were changed. Verification of native editing, Markdown
Reading, Write/Read/Split, Focus/View, split divider, tabs/12-tab semantics,
outline, command palette, find/replace/regex, font sizing, statistics, typewriter,
SAF open/save, external edits/read-back, session/recovery, lock protection,
HTML/share/PDF, nine themes, 25 splashes, orientation/Fold layouts and eye/lock
controls is deferred until exact source and test access exist. None is claimed
as runtime-verified. No migration or installed-user-data effects are introduced
by this documentation-only change. No release, baseline tag or merge is proposed.

## Historical provenance request

No Library artifacts were accessible or uploaded. Historical versions, SDKs and
recovery bundles are not interchangeable with the authoritative 2.2.5 source.
Preserve their original bytes and record filename, version, origin and SHA-256
when supplied. Audit public redistribution rights and private material first;
never publish private signer/recovery material or personal notes. Local SDK ZIPs
belong to the configured environment and are not evidence of project history.
