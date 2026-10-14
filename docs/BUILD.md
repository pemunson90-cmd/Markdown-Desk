# Reproducible unsigned baseline validation

Validated on Linux with Temurin **17.0.16+8**, Python **3.12.14**, Android SDK
platform **37.0 revision 2** (installed folder `android-37.0`), build-tools
**36.0.0** (aapt2 2.20-13193326; D8 8.10.9-dev). The original SDK 36/tooling
36.1.0 release records are historical provenance, not minimum requirements.
SDK input hashes are in `provenance/2.2.5/toolchain-SHA256SUMS.txt`.

Use the already configured `JAVA_HOME` and `ANDROID_SDK_ROOT` (or set them to
these installed versions on a fresh Linux runner). Do not generate signing keys.
The platform folder is `android-37.0` here, not `android-37`.

```bash
export PATH="$JAVA_HOME/bin:$PATH"
export ANDROID_PLATFORM=android-37.0
export ANDROID_BUILD_TOOLS=36.0.0
export ANDROID_SDK_ROOT="${ANDROID_SDK_ROOT:-$ANDROID_HOME}"
java -version
javac -version
# One-time dependency setup; no SDK/JDK reinstall on this environment:
bash verification/setup_host_deps.sh
# All subsequent commands use installed/local inputs only:
python3 verification/check_baseline_inventory.py
bash verification/run_host_checks.sh
python3 verification/check_unsigned_build.py
python3 verification/check_clean_rebuild.py
```

The host-only JSON JAR is `com.vaadin.external.google:android-json:0.0.20131108.vaadin1`,
SHA-256 `dfb7bae2f404cfe0b72b4d23944698cb716b7665171812a0a4d0f5926c0fac79`.
Setup downloads from Maven Central and verifies the pinned digest; host checks
reverify it and never fetch. It is ignored by Git and never included in the APK.
The host Android stubs simulate limited APIs; their passing tests are not
Android runtime, image decoding, SAF-provider or Fold-device proof.

## Build interface

```bash
bash apps/MarkdownDesk/build.sh --unsigned MarkdownDesk-2.2.5-UNSIGNED
# Equivalent validation wrapper:
bash verification/compile_unsigned.sh
```

`--unsigned` bypasses all four private signing-variable requirements and does
not require or invoke apksigner. It uses aapt2, javac `--release 11`, D8 and
zipalign. The full baseline's 25 splash files are required; missing assets fail
rather than producing a parity claim. No manifest/version or app behavior is
changed. ZIP ordering/timestamps are normalized for unsigned reproducibility.
Outputs are disposable, ignored files under `apps/MarkdownDesk/build/`.

`check_unsigned_build.py` clears all four signing variables before the real
build. It verifies package/version/min/target, alignment, every splash byte,
DEX presence, absence of JAR signature entries and APK signing blocks, and
expected apksigner verification failure. It invokes apksigner only to audit the
absence of a signature, never to sign. The unsigned APK is not a release or an
installable upgrade; do not distribute it as a shipping build.

`check_clean_rebuild.py` archives **committed HEAD** twice into fresh directories
(with spaces in their paths), copies only the pre-provisioned host JAR, runs
host tests and unsigned APK audits, then compares full APK SHA-256s. Commit
changes first. It does not fetch dependencies or reuse build outputs. These
runs were not performed inside an independently enforced network sandbox.

## Signed path

The default build path retains the original canonical signing requirements.
It was not executed here. Signing credentials must remain outside this public
repository and hosted CI. Never use a generated replacement/debug key to make
these validation artifacts installable. The PM-cleared source-only baseline may be merged/tagged independently of
future device/release acceptance. This tag never authorizes a new signed release.

## M0 regression and safety commands

`bash verification/run_host_checks.sh` now includes provider/session/renderer/
AST-extracted mode and strict inventory fixtures. These are host preservation
checks; their green result does not pass the separate desired safety/spec gate.
Run `bash verification/run_safety_gates.sh` to reproduce current provider-loss,
oversize/nullmetadata, nested-list and contrast failures. That gate exits nonzero
and is intentionally not hidden. See M0_2_REPORT.md for precise limits.

`PATH="$JAVA_HOME/bin:$PATH" bash verification/benchmarks/run.sh` records a matched
host-only benchmark; do not repeat it for unrelated edits or claim device latency.
