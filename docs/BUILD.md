# Local build and verification

The accepted 2.5.0/code39 APK is published as `V2.5.0`. Reuse that artifact when distributing the release; publishing or changing documentation does not require a rebuild. Current work and evidence belong in [LIVE_WORK_LEDGER.md](handoffs/LIVE_WORK_LEDGER.md).

## Toolchain used for 2.5.0

- JDK 17, compiling application Java with `--release 11`.
- Android API 37 platform (installed folder `android-37.0`).
- Android build-tools **36.1.0**: AAPT2, D8, zipalign, and apksigner.
- Python 3, Bash, and ZIP utilities.
- Host tests that use `org.json`: pinned `com.vaadin.external.google:android-json:0.0.20131108.vaadin1`. This JAR is **host-only**, never packaged in the APK; Android supplies the runtime JSON API.

Reuse existing installations. Set these explicitly so standalone build and source-verification commands use the same toolchain:

```bash
export JAVA_HOME="/path/to/jdk17"
export PATH="$JAVA_HOME/bin:$PATH"
export ANDROID_SDK_ROOT="/path/to/sdk"
export ANDROID_PLATFORM=android-37.0
export ANDROID_BUILD_TOOLS=36.1.0
```

If the host JAR is missing, `bash verification/setup_host_deps.sh` downloads it once from Maven Central and checks SHA-256 `dfb7bae2f404cfe0b72b4d23944698cb716b7665171812a0a4d0f5926c0fac79`. Subsequent tests use local inputs. Do not fetch replacements or change the dependency because its version looks old without identifying a relevant incompatibility or defect.

## Choose verification for the change

Follow the testing stop rule in [AGENTS.md](../AGENTS.md). Use affected maintained suites, reuse valid prior results, and stop when the selected checks pass. Documentation edits need wording/link/diff review, not app compilation. The broad `run_current_phone_checks.sh` is an integration/release gate, not the default response to every edit. `run_host_checks.sh` additionally includes the deferred cross-install private-state families and is not the ordinary current-phone gate.

For changed Android code/resources when a full source compile is relevant:

```bash
bash verification/compile_native_sources_only.sh
```

This runs API37 AAPT2 resource compilation/linking, generated `R.java`, Java compilation, and D8 without producing an installable APK. Its disposable outputs are removed. It proves source compilation, not physical Fold behavior or file-provider behavior.

Do not use GitHub Actions without explicit owner authorization. Browser layout probes are optional host diagnostics and may need a separately provisioned Playwright installation; they do not establish Android WebView acceptance.

## New signed builds

Finish the assigned feature/fix scope and applicable local checks before producing any APK. Follow [RELEASE_GATES.md](RELEASE_GATES.md). New updates must keep package `com.example.markdowndesk`, use the original signing identity, and increase versionCode beyond the published **39**. The manifest currently uses minSdk35 and targetSdk37; preserve non-destructive upgrades and existing user data.

`apps/MarkdownDesk/build.sh` is the maintained packaging implementation. Its signed path requires these private environment settings:

- `MARKDOWN_DESK_KEYSTORE`
- `MARKDOWN_DESK_STORE_PASSWORD`
- `MARKDOWN_DESK_KEY_ALIAS`
- `MARKDOWN_DESK_KEY_PASSWORD`

Never print or commit their values. Never substitute a new/debug signer. After the required gates and authorization, invoke `bash apps/MarkdownDesk/build.sh <output-name>` and verify identity, signature, alignment, and upgrade compatibility. The script replaces its disposable `apps/MarkdownDesk/build/` directory; preserve any deliverable outside it first.

`verification/first_signed_phone_candidate.sh` records the **2.5.0 first-candidate** workflow. It pins the old integration branch, checklist, version floor, and output naming. It must be reviewed for a future release rather than blindly rerun as a general release command. This documentation does not authorize an intermediate APK.

Historical unsigned baseline/reproducibility scripts remain available for a specific packaging investigation. They build APKs and must not be invoked as routine source checks. Historical baseline/toolchain provenance is under [docs/provenance/2.2.5](provenance/2.2.5/README.md).

## Evidence

[2.5.0 executed gate log](verification/PHONE_CANDIDATE_20261107.txt) and [artifact identity](verification/PHONE_CANDIDATE_20261107.json) record the released candidate. Host stubs and bounded memory fixtures do not establish every Android provider/device behavior. The owner's acceptance is recorded separately in the live ledger.
