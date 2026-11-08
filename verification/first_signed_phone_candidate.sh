#!/usr/bin/env bash
# First phone-test APK ONLY. Never run until all non-deferred features and
# locally applicable host/source gates pass. No GitHub Actions, main push or release.
set -euo pipefail
cd -- "$(dirname -- "$0")/.."
BRANCH="integration/unified-workspaces-20261106"
CERT="a163cea419108eb595f3dcb01de16feca878368d4ffc6efc58eb9a8358b6be01"
test "$(git branch --show-current)" = "$BRANCH" || {
 echo 'Refusing to package outside approved integration branch' >&2;exit 1;
}
test -z "$(git status --porcelain)" || {
 echo 'Refusing to package a dirty checkout' >&2;exit 1;
}
python3 - <<'PY'
from pathlib import Path
import re
lines=Path('docs/handoffs/LIVE_WORK_LEDGER.md').read_text().splitlines()
rows={}
for line in lines:
 m=re.match(r'\|\s*(T0[1-8])\s*\|\s*\*\*(.*?)\*\*\s*\|',line)
 if m:rows[m.group(1)]=m.group(2)
expected={f'T{x:02d}' for x in range(1,9)}
if set(rows)!=expected:raise SystemExit('Incomplete authoritative pre-APK feature checklist')
for k in sorted(expected):
 if rows[k]!='PRE-APK PASS':raise SystemExit(f'{k} remains unverified: {rows[k]}')
print('Complete-scope feature and pre-APK matrix accepted')
PY
: "${ANDROID_SDK_ROOT:=${ANDROID_HOME:-}}"
: "${ANDROID_SDK_ROOT:?Authentic Android SDK 37 is required}"
export ANDROID_BUILD_TOOLS="${ANDROID_BUILD_TOOLS:-36.1.0}"
for name in MARKDOWN_DESK_KEYSTORE MARKDOWN_DESK_STORE_PASSWORD \
            MARKDOWN_DESK_KEY_ALIAS MARKDOWN_DESK_KEY_PASSWORD; do
 [[ -n "${!name:-}" ]] || { echo "Missing local signing setting $name" >&2;exit 1; }
done
[[ -f "$MARKDOWN_DESK_KEYSTORE" ]] || { echo "Original release keystore missing" >&2;exit 1; }
command -v keytool >/dev/null
# Compare the certificate BEFORE the first APK can exist.
actual=$(keytool -list -v -keystore "$MARKDOWN_DESK_KEYSTORE" \
 -alias "$MARKDOWN_DESK_KEY_ALIAS" \
 -storepass:env MARKDOWN_DESK_STORE_PASSWORD \
 | awk '/SHA256:/{print $2;exit}' | tr -d ':' | tr '[:upper:]' '[:lower:]')
[[ "$actual" == "$CERT" ]] || {
 echo 'Wrong signing certificate: original installed-app identity required' >&2;exit 1;
}
code=$(python3 - <<'PY'
from xml.etree import ElementTree as E
r=E.parse('apps/MarkdownDesk/AndroidManifest.xml').getroot()
a='{http://schemas.android.com/apk/res/android}'
if r.attrib['package']!='com.example.markdowndesk':
 raise SystemExit('Package differs from established installed app')
v=int(r.attrib[a+'versionCode'])
if v<=38:raise SystemExit('Increase final versionCode above 38 before first signed candidate')
print(v)
PY
)
bash verification/run_current_phone_checks.sh
bash verification/compile_native_sources_only.sh
test -z "$(git status --porcelain)" || {
 echo 'Local verification modified tracked files; package refused' >&2;exit 1;
}
name="MarkdownDesk-2.5.0-phone-v$code"
# The FIRST operation here that can produce an APK, after all local gates.
(cd apps/MarkdownDesk && bash build.sh "$name")
apk="apps/MarkdownDesk/build/$name.apk"
test -s "$apk"
apksigner="$ANDROID_SDK_ROOT/build-tools/${ANDROID_BUILD_TOOLS:-36.1.0}/apksigner"
signed=$("$apksigner" verify --print-certs --verbose "$apk" |
 awk -F': ' '/Signer #1 certificate SHA-256 digest:/{print $2;exit}' |
 tr '[:upper:]' '[:lower:]')
[[ "$signed" == "$CERT" ]] || {
 rm -f -- "$apk";echo 'Signed APK has wrong certificate; deleted' >&2;exit 1;
}
echo "Signed phone-test candidate: $apk"
echo 'Owner must approve Fold7 tests before any main promotion or GitHub Release.'
