#!/usr/bin/env bash
# Packaging repair only: portable, fail-fast, no generated signing identity.
# Requires JDK 17+, Android SDK platform 35+ and build-tools 35+ (choose exact pins).
set -euo pipefail
cd -- "$(dirname -- "$0")"
PROJ="$PWD"
UNSIGNED=false
if [[ "${1:-}" == --unsigned ]]; then UNSIGNED=true; shift; fi
[[ $# -le 1 ]] || { echo 'Usage: build.sh [--unsigned] [output-name]' >&2; exit 1; }
: "${ANDROID_SDK_ROOT:=${ANDROID_HOME:-}}"
: "${ANDROID_SDK_ROOT:?Set ANDROID_SDK_ROOT to an installed SDK}"
ANDROID_PLATFORM="${ANDROID_PLATFORM:-android-35}"
ANDROID_BUILD_TOOLS="${ANDROID_BUILD_TOOLS:-35.0.0}"
BT="$ANDROID_SDK_ROOT/build-tools/$ANDROID_BUILD_TOOLS"
ANDROID_JAR="$ANDROID_SDK_ROOT/platforms/$ANDROID_PLATFORM/android.jar"
if ! $UNSIGNED; then
: "${MARKDOWN_DESK_KEYSTORE:?Set canonical release keystore path}"
: "${MARKDOWN_DESK_STORE_PASSWORD:?Set private store password}"
: "${MARKDOWN_DESK_KEY_ALIAS:?Set canonical alias}"
: "${MARKDOWN_DESK_KEY_PASSWORD:?Set private key password}"
export MARKDOWN_DESK_STORE_PASSWORD MARKDOWN_DESK_KEY_PASSWORD
fi
TOOLS=("$BT/aapt2" "$BT/d8" "$BT/zipalign")
if ! $UNSIGNED; then TOOLS+=("$BT/apksigner"); fi
for p in "${TOOLS[@]}"; do
  test -x "$p" || { echo "Missing executable: $p" >&2; exit 1; }
done
test -f "$ANDROID_JAR"
if ! $UNSIGNED; then test -f "$MARKDOWN_DESK_KEYSTORE"; fi
for c in java javac python3 zip; do command -v "$c" >/dev/null; done
# Never package a source-only checkout as baseline parity.
python3 - <<'ASSETS'
from pathlib import Path
assets=list(Path('assets/splash').glob('*.webp'))
if len(assets) != 25:
 raise SystemExit('Exact 25 baseline splash assets required; publication clearance is separate')
ASSETS
DEFAULT_NAME=MarkdownDesk
if $UNSIGNED; then DEFAULT_NAME=MarkdownDesk-UNSIGNED; fi
NAME="${1:-$DEFAULT_NAME}"
[[ "$NAME" =~ ^[A-Za-z0-9._-]+$ ]] || { echo 'Invalid output name' >&2; exit 1; }
# Confine disposable outputs to this fixed project build directory.
OUT="$PROJ/build"
[[ ! -L "$OUT" ]] || { echo 'Refusing symlink build directory' >&2; exit 1; }
rm -rf -- "$OUT"
mkdir -p "$OUT/compiled" "$OUT/gen" "$OUT/classes" "$OUT/dex"
mapfile -t VERSION < <(python3 - <<'XML'
import xml.etree.ElementTree as E
r=E.parse('AndroidManifest.xml').getroot();a='{http://schemas.android.com/apk/res/android}'
print(r.attrib[a+'versionCode']);print(r.attrib[a+'versionName'])
XML
)
[[ ${#VERSION[@]} == 2 ]]
"$BT/aapt2" compile --dir "$PROJ/res" -o "$OUT/compiled/res.zip"
"$BT/aapt2" link -o "$OUT/base.apk" -I "$ANDROID_JAR" \
 --manifest "$PROJ/AndroidManifest.xml" -A "$PROJ/assets" --java "$OUT/gen" \
 --min-sdk-version 26 --target-sdk-version 35 \
 --version-code "${VERSION[0]}" --version-name "${VERSION[1]}" "$OUT/compiled/res.zip"
# Javac argfile paths are quoted; an ordinary extracted path may contain spaces.
python3 - <<'SOURCES'
from pathlib import Path
root=Path.cwd()
files=sorted(list((root/'src').rglob('*.java'))+list((root/'build/gen').rglob('*.java'))+list((root/'../../shared/editor_bridge').resolve().rglob('*.java')))
if not files:raise SystemExit('No Java sources')
(root/'build/sources.txt').write_text(''.join('"'+str(p).replace('\\','\\\\').replace('"','\\"')+'"\n' for p in files))
SOURCES
javac --release 11 -encoding UTF-8 -classpath "$ANDROID_JAR" -d "$OUT/classes" @"$OUT/sources.txt"
# D8 accepts a class archive, avoiding huge command lines.
python3 - <<'CLASSES'
from pathlib import Path
from zipfile import ZipFile, ZipInfo
root=Path('build/classes');files=sorted(root.rglob('*.class'))
if not files:raise SystemExit('No class files')
with ZipFile('build/classes.jar','w') as z:
 for p in files:z.writestr(ZipInfo(str(p.relative_to(root)),(1980,1,1,0,0,0)),p.read_bytes())
CLASSES
"$BT/d8" --min-api 26 --release --lib "$ANDROID_JAR" --output "$OUT/dex" "$OUT/classes.jar"
test -s "$OUT/dex/classes.dex"
cp "$OUT/base.apk" "$OUT/unsigned.apk"
(cd "$OUT/dex" && zip -q -X "$OUT/unsigned.apk" classes*.dex)
# Normalize ZIP metadata/order so clean unsigned rebuilds are byte-reproducible.
if $UNSIGNED; then
python3 - <<'NORMALIZE'
from pathlib import Path
from zipfile import ZipFile, ZipInfo
p=Path('build/unsigned.apk')
with ZipFile(p) as src, ZipFile('build/normalized.apk','w') as dst:
 for name in sorted(src.namelist()):
  old=src.getinfo(name);info=ZipInfo(name,(1980,1,1,0,0,0))
  info.compress_type=old.compress_type
  info.create_system=3;info.external_attr=0o100644 << 16
  dst.writestr(info,src.read(name))
p.replace('build/un-normalized.apk')
Path('build/normalized.apk').replace(p)
Path('build/un-normalized.apk').unlink()
NORMALIZE
fi
"$BT/zipalign" -f -p 4 "$OUT/unsigned.apk" "$OUT/aligned.apk"
if $UNSIGNED; then
  mv "$OUT/aligned.apk" "$OUT/$NAME.apk"
  "$BT/zipalign" -c -p 4 "$OUT/$NAME.apk"
  echo "Built UNSIGNED validation artifact (not installable as a release): $OUT/$NAME.apk"
  exit 0
fi
"$BT/apksigner" sign --ks "$MARKDOWN_DESK_KEYSTORE" \
 --ks-pass env:MARKDOWN_DESK_STORE_PASSWORD --key-pass env:MARKDOWN_DESK_KEY_PASSWORD \
 --ks-key-alias "$MARKDOWN_DESK_KEY_ALIAS" \
 --v1-signing-enabled true --v2-signing-enabled true --v3-signing-enabled true \
 --out "$OUT/$NAME.apk" "$OUT/aligned.apk"
"$BT/apksigner" verify --verbose --print-certs "$OUT/$NAME.apk"
"$BT/zipalign" -c -p 4 "$OUT/$NAME.apk"
echo "Built and signature-verified: $OUT/$NAME.apk"
