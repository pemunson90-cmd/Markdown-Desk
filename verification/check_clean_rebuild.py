#!/usr/bin/env python3
"""Build committed inputs twice in fresh directories, without fetching dependencies."""
import hashlib
import io
import os
from pathlib import Path
import shutil
import subprocess
import tarfile
import tempfile

root = Path(__file__).resolve().parents[1]
revision = subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=root, text=True).strip()
archive = subprocess.check_output(['git', 'archive', revision], cwd=root)
dep = root / 'verification/build/deps/android-json-0.0.20131108.vaadin1.jar'
assert dep.is_file(), 'Run setup_host_deps.sh before this offline check'
env = os.environ.copy()
for name in ('MARKDOWN_DESK_KEYSTORE', 'MARKDOWN_DESK_KEY_ALIAS',
             'MARKDOWN_DESK_STORE_PASSWORD', 'MARKDOWN_DESK_KEY_PASSWORD'):
    env.pop(name, None)
env['PATH'] = str(Path(env['JAVA_HOME']) / 'bin') + os.pathsep + env['PATH']
digests = []
with tempfile.TemporaryDirectory(prefix='markdown-clean-rebuild-') as tmp:
    for label in ('first checkout', 'second checkout'):
        checkout = Path(tmp) / label
        checkout.mkdir()
        with tarfile.open(fileobj=io.BytesIO(archive)) as tar:
            tar.extractall(checkout, filter='data')
        # Provenance checks need the immutable tag, fetched from local Git only.
        subprocess.run(['git', 'init', '--quiet', str(checkout)], check=True)
        subprocess.run(['git', '-C', str(checkout), 'fetch', '--quiet', str(root),
                        'refs/tags/v2.2.5-baseline:refs/tags/v2.2.5-baseline'], check=True)
        dest = checkout / 'verification/build/deps' / dep.name
        dest.parent.mkdir(parents=True)
        shutil.copyfile(dep, dest)
        # These commands use only the installed SDK/JDK and the pinned local JAR.
        subprocess.run(['bash', 'verification/run_host_checks.sh'], cwd=checkout, env=env, check=True)
        subprocess.run(['python3', 'verification/check_unsigned_build.py'], cwd=checkout, env=env, check=True)
        apk = checkout / 'apps/MarkdownDesk/build/MarkdownDesk-2.2.5-UNSIGNED.apk'
        digests.append(hashlib.sha256(apk.read_bytes()).hexdigest())
assert digests[0] == digests[1], f'Clean rebuilds differ: {digests}'
print('PASS two clean committed-source builds: revision', revision)
print('Unsigned APK SHA-256:', digests[0])
print('No dependency downloads performed; existing SDK/JDK and pre-provisioned host JAR only.')
