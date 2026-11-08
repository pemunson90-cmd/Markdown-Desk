#!/usr/bin/env bash
# Compile entire Android source with authentic API37, without producing an APK.
# AAPT2 resources (.ap_), javac classes and D8 dex are disposable.
set -euo pipefail
cd -- "$(dirname -- "$0")/.."
ROOT="$PWD"
APP="$ROOT/apps/MarkdownDesk"
: "${ANDROID_SDK_ROOT:=${ANDROID_HOME:-}}"
: "${ANDROID_SDK_ROOT:?Provide an installed Android SDK}"
ANDROID_PLATFORM="${ANDROID_PLATFORM:-android-37.0}"
ANDROID_BUILD_TOOLS="${ANDROID_BUILD_TOOLS:-36.1.0}"
BT="$ANDROID_SDK_ROOT/build-tools/$ANDROID_BUILD_TOOLS"
ANDROID_JAR="$ANDROID_SDK_ROOT/platforms/$ANDROID_PLATFORM/android.jar"
[[ "$ANDROID_PLATFORM" == "android-37.0" ]] || {
  echo "Refusing non-API37 source gate: $ANDROID_PLATFORM" >&2; exit 1;
}
for tool in "$BT/aapt2" "$BT/d8" java javac jar python3; do
  if [[ "$tool" == */* ]]; then [[ -x "$tool" ]] || { echo "Missing $tool" >&2; exit 1; }
  else command -v "$tool" >/dev/null || { echo "Missing $tool" >&2; exit 1; }
  fi
done
[[ -f "$ANDROID_JAR" ]] || { echo "Missing Android 17 android.jar" >&2; exit 1; }
TMP="$(mktemp -d)"
trap 'rm -rf -- "$TMP"' EXIT
mkdir -p "$TMP/compiled" "$TMP/gen" "$TMP/classes" "$TMP/dex"
mapfile -t VERSION < <(python3 - "$APP/AndroidManifest.xml" <<'PY'
import sys,xml.etree.ElementTree as ET
root=ET.parse(sys.argv[1]).getroot()
a='{http://schemas.android.com/apk/res/android}'
sdk=root.find('uses-sdk')
if sdk is None:raise SystemExit('Missing manifest uses-sdk')
minimum=int(sdk.attrib[a+'minSdkVersion'])
target=int(sdk.attrib[a+'targetSdkVersion'])
if minimum!=35 or target!=37:raise SystemExit('Expected supported min35/target37 source gate')
if root.attrib['package']!='com.example.markdowndesk':
 raise SystemExit('Package identity changed; refusing source gate')
print(minimum);print(target)
print(root.attrib[a+'versionCode']);print(root.attrib[a+'versionName'])
PY
)
[[ ${#VERSION[@]} == 4 ]] || { echo "Incomplete manifest identity" >&2; exit 1; }
python3 - "$APP/assets/splash" <<'PY'
import sys
from pathlib import Path
assets=list(Path(sys.argv[1]).glob('*.webp'))
if len(assets)!=25:raise SystemExit('Missing or extra splash assets: expected 25')
PY
"$BT/aapt2" compile --dir "$APP/res" -o "$TMP/compiled/res.zip"
# .ap_ is a disposable resource object; it is not an installable APK.
"$BT/aapt2" link -o "$TMP/compiled/resources.ap_" -I "$ANDROID_JAR" \
 --manifest "$APP/AndroidManifest.xml" -A "$APP/assets" --java "$TMP/gen" \
 --min-sdk-version "${VERSION[0]}" --target-sdk-version "${VERSION[1]}" \
 --version-code "${VERSION[2]}" --version-name "${VERSION[3]}" \
 "$TMP/compiled/res.zip"
python3 - "$ROOT" "$TMP" <<'PY'
import sys
from pathlib import Path
root=Path(sys.argv[1]);tmp=Path(sys.argv[2])
sources=sorted([*(root/'apps/MarkdownDesk/src').rglob('*.java'),
                *(root/'shared/editor_bridge').rglob('*.java'),
                *(tmp/'gen').rglob('*.java')])
if not sources:raise SystemExit('No Java source files')
def quoted(p):return '"'+str(p).replace('\\','\\\\').replace('"','\\"')+'"'
(tmp/'sources.txt').write_text('\n'.join(map(quoted,sources))+'\n')
print('Source count:',len(sources))
PY
javac --release 11 -encoding UTF-8 -classpath "$ANDROID_JAR" \
 -d "$TMP/classes" @"$TMP/sources.txt"
# Exercise the actual compiled coordinator terminal cancellation guard on the host.
javac --release 11 -encoding UTF-8 -classpath "$ANDROID_JAR:$TMP/classes" \
 -d "$TMP/classes" verification/archive_reader/CoordinatorCancelGuardCheck.java
java -cp "$TMP/classes:$ANDROID_JAR" com.example.markdowndesk.CoordinatorCancelGuardCheck
# Test fixture is not application bytecode.
rm -f "$TMP/classes/com/example/markdowndesk/CoordinatorCancelGuardCheck.class"
jar --create --file "$TMP/classes.jar" -C "$TMP/classes" .
"$BT/d8" --min-api "${VERSION[0]}" --release --lib "$ANDROID_JAR" \
 --output "$TMP/dex" "$TMP/classes.jar"
test -s "$TMP/dex/classes.dex"
python3 - "$TMP" <<'PY'
import sys,hashlib
from pathlib import Path
tmp=Path(sys.argv[1]);files=sorted((tmp/'dex').glob('classes*.dex'))
if not files:raise SystemExit('D8 did not produce dex')
print('SOURCE-ONLY PASS: full Java/AAPT2/D8; no APK produced')
for path in files:
 print(path.name,path.stat().st_size,hashlib.sha256(path.read_bytes()).hexdigest())
PY
# Trap destroys .ap_, .jar, .class and .dex. No APK is ever assembled.
