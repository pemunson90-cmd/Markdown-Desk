#!/usr/bin/env bash
# Validation only: no signing, installation, publication, or SDK-version changes.
# Arguments: Android build-tools 35.0.0 directory, android-35/android.jar,
#            NEW output directory. Requires Java compiler module, Python3, zip.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
BT="$(realpath "$1")"; AJ="$(realpath "$2")"; OUT="$(realpath -m "$3")"
[[ ! -e "$OUT" ]] || { echo 'Use a new output directory'; exit 1; }
grep -q '^Pkg.Revision=35.0.0$' "$BT/source.properties"
grep -q '^AndroidVersion.ApiLevel=35$' "$(dirname "$AJ")/source.properties"
PROJ="$ROOT/apps/MarkdownDesk"
mkdir -p "$OUT"/{compiled,gen,classes,dex}
"$BT/aapt2" version
"$BT/aapt2" compile --dir "$PROJ/res" -o "$OUT/compiled/res.zip"
"$BT/aapt2" link -o "$OUT/base.apk" -I "$AJ" --manifest "$PROJ/AndroidManifest.xml" -A "$PROJ/assets" --java "$OUT/gen" --min-sdk-version 26 --target-sdk-version 35 --version-code 17 --version-name 2.2.4 "$OUT/compiled/res.zip"
python3 - "$ROOT" "$OUT" <<'PY'
from pathlib import Path
import sys
p=Path(sys.argv[1]);out=Path(sys.argv[2]);files=sorted(list((p/'apps/MarkdownDesk/src').rglob('*.java'))+list((p/'shared/editor_bridge').rglob('*.java'))+list((out/'gen').rglob('*.java')))
(out/'sources.txt').write_text(''.join('"'+str(p)+'"\n' for p in files));print('Compiling',len(files),'sources')
PY
# Host workaround: Java11 language and bytecode, not --release11 API enforcement.
java com.sun.tools.javac.Main -source 11 -target 11 -proc:none -encoding UTF-8 -classpath "$AJ" -d "$OUT/classes" @"$OUT/sources.txt"
python3 - "$OUT" <<'PY'
from pathlib import Path
from zipfile import ZipFile
import sys
out=Path(sys.argv[1]);r=out/'classes'
with ZipFile(out/'classes.jar','w') as z:
 for p in r.rglob('*.class'):z.write(p,p.relative_to(r))
PY
"$BT/d8" --min-api 26 --release --lib "$AJ" --output "$OUT/dex" "$OUT/classes.jar"
cp "$OUT/base.apk" "$OUT/unsigned.apk"
(cd "$OUT/dex" && zip -q -X "$OUT/unsigned.apk" classes*.dex)
"$BT/zipalign" -f -p 4 "$OUT/unsigned.apk" "$OUT/aligned-UNSIGNED-validation-only.apk"
"$BT/zipalign" -c -p 4 "$OUT/aligned-UNSIGNED-validation-only.apk"
"$BT/aapt2" dump badging "$OUT/aligned-UNSIGNED-validation-only.apk"
echo 'PASS compile, package, D8, alignment; UNSIGNED VALIDATION ONLY, no device test'
