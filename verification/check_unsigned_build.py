#!/usr/bin/env python3
"""Exercise the real unsigned build with no signing inputs, then audit its APK."""
import hashlib
import os
from pathlib import Path
import subprocess
import zipfile

ROOT = Path(__file__).resolve().parents[1]
env = os.environ.copy()
for name in ('MARKDOWN_DESK_KEYSTORE', 'MARKDOWN_DESK_KEY_ALIAS',
             'MARKDOWN_DESK_STORE_PASSWORD', 'MARKDOWN_DESK_KEY_PASSWORD'):
    env.pop(name, None)
env['PATH'] = str(Path(env['JAVA_HOME']) / 'bin') + os.pathsep + env['PATH']
subprocess.run(['bash', str(ROOT / 'apps/MarkdownDesk/build.sh'), '--unsigned',
                'MarkdownDesk-2.2.5-UNSIGNED'], env=env, check=True)
apk = ROOT / 'apps/MarkdownDesk/build/MarkdownDesk-2.2.5-UNSIGNED.apk'
bt = Path(env['ANDROID_SDK_ROOT']) / 'build-tools' / env['ANDROID_BUILD_TOOLS']
badging = subprocess.check_output([str(bt / 'aapt2'), 'dump', 'badging', str(apk)], text=True)
assert "package: name='com.example.markdowndesk' versionCode='18' versionName='2.2.5'" in badging
assert "minSdkVersion:'26'" in badging or "\nsdkVersion:'26'" in badging
assert "targetSdkVersion:'35'" in badging
subprocess.run([str(bt / 'zipalign'), '-c', '-p', '4', str(apk)], check=True)
with zipfile.ZipFile(apk) as z:
    names = z.namelist()
    assert not any(n.upper().startswith('META-INF/') and n.upper().endswith(('.SF', '.RSA', '.DSA', '.EC')) for n in names)
    assert 'classes.dex' in names and z.getinfo('classes.dex').file_size > 0
    assets = sorted((ROOT / 'apps/MarkdownDesk/assets/splash').glob('*.webp'))
    assert len(assets) == 25, 'Full baseline assets required; no placeholders'
    assert len([n for n in names if n.startswith('assets/splash/') and n.endswith('.webp')]) == 25
    for asset in assets:
        assert z.read('assets/splash/' + asset.name) == asset.read_bytes(), asset.name
# No APK v2/v3 signing block may exist. No signer is invoked by the unsigned path.
assert b'APK Sig Block 42' not in apk.read_bytes()
verification = subprocess.run([str(bt / 'apksigner'), 'verify', str(apk)], capture_output=True, text=True)
assert verification.returncode != 0, 'Unsigned artifact unexpectedly verifies as signed'
assert 'DOES NOT VERIFY' in verification.stderr + verification.stdout
print(badging.split('application-label:')[0].strip())
print('PASS genuinely unsigned APK: no signing credentials, no signatures, aligned, exact identity, all 25 assets byte-identical')
print('APK SHA-256:', hashlib.sha256(apk.read_bytes()).hexdigest())
