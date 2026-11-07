#!/usr/bin/env python3
"""Read-only source and release-evidence guard; the integration queue is the sole state record."""
import argparse
import json
from pathlib import Path
import re
import subprocess
import xml.etree.ElementTree as ET

QUEUE = 'docs/handoffs/ACTIVE_UNIFIED_WORKSPACES_QUEUE.md'
MANIFEST = 'apps/MarkdownDesk/AndroidManifest.xml'
OPEN = '<!-- source-contract:begin -->'
CLOSE = '<!-- source-contract:end -->'
ANDROID = '{http://schemas.android.com/apk/res/android}'
CANONICAL_CERT = 'a163cea419108eb595f3dcb01de16feca878368d4ffc6efc58eb9a8358b6be01'


def git(root, *args):
    result = subprocess.run(['git', '-C', str(root), *args], text=True, capture_output=True)
    if result.returncode:
        raise ValueError('git ' + ' '.join(args[:2]) + ' could not verify requested revision')
    return result.stdout.strip()


def source_record(root):
    """Find exactly one fenced JSON source-contract INSIDE the existing active queue."""
    queue = (Path(root) / QUEUE).read_text(encoding='utf-8')
    if queue.count(OPEN) != 1 or queue.count(CLOSE) != 1:
        raise ValueError('expected exactly one source-contract in authoritative queue')
    pattern = re.escape(OPEN) + r'\s*```json\s*(.*?)\s*```\s*' + re.escape(CLOSE)
    found = re.search(pattern, queue, re.DOTALL)
    if not found:
        raise ValueError('invalid fenced source-contract in authoritative queue')
    record = json.loads(found.group(1))
    if record.get('schema_version') != 1:
        raise ValueError('unsupported source-contract schema')
    return record


def safe_evidence(root, filename):
    if not isinstance(filename, str) or not filename.startswith('docs/'):
        return False
    root = Path(root).resolve()
    path = root / filename
    return path.is_file() and path.resolve().is_relative_to(root)


def check(root, require_installed=False, require_clean=False):
    root = Path(root).resolve()
    errors, notes = [], []
    try:
        if Path(git(root, 'rev-parse', '--show-toplevel')).resolve() != root:
            raise ValueError('guard must run at repository root')
        state = source_record(root)
        dev = state['development']
        branch, anchor = dev['branch'], dev['anchor_commit']
        git(root, 'check-ref-format', '--branch', branch)
        if not re.fullmatch(r'[0-9a-f]{40}', anchor):
            errors.append('invalid exact development anchor commit')
        else:
            try:
                git(root, 'merge-base', '--is-ancestor', anchor, 'HEAD')
            except ValueError:
                errors.append('recorded development anchor missing or not an ancestor of HEAD')
        remote = 'refs/remotes/origin/' + branch
        try:
            fetched = git(root, 'rev-parse', '--verify', remote + '^{commit}')
        except ValueError:
            errors.append('missing fetched integration head; fetch origin/' + branch + ' without switching worktrees')
        else:
            try:
                git(root, 'merge-base', '--is-ancestor', fetched, 'HEAD')
            except ValueError:
                errors.append('stale checkout: fetched integration head is not an ancestor of HEAD')
        root_manifest = ET.parse(root / MANIFEST).getroot()
        uses = root_manifest.find('uses-sdk')
        actual = {
            'package': root_manifest.get('package'),
            'version_code': int(root_manifest.get(ANDROID + 'versionCode')),
            'version_name': root_manifest.get(ANDROID + 'versionName'),
            'min_sdk': int(uses.get(ANDROID + 'minSdkVersion')),
            'target_sdk': int(uses.get(ANDROID + 'targetSdkVersion')),
        }
        if actual != dev['manifest']:
            errors.append('manifest and authoritative queue source identity differ')
        for evidence in state['evidence']:
            if not safe_evidence(root, evidence):
                errors.append('missing/unsafe source evidence: ' + str(evidence))
        installed = state['installed']
        if installed['status'] == 'unconfirmed':
            notes.append('installed app/source and owner acceptance unconfirmed; not release-ready')
            if require_installed:
                errors.append('release gate requires owner-accepted installed identity')
        elif installed['status'] == 'verified':
            sha = installed.get('source_commit')
            apk = installed.get('apk_sha256')
            cert = installed.get('certificate_sha256')
            if not isinstance(sha, str) or not re.fullmatch(r'[0-9a-f]{40}', sha):
                errors.append('installed source commit is not an exact SHA')
            else:
                try:
                    git(root, 'merge-base', '--is-ancestor', sha, 'HEAD')
                except ValueError:
                    errors.append('installed source commit absent from preserved ancestry')
            if not isinstance(apk, str) or not re.fullmatch(r'[0-9a-f]{64}', apk):
                errors.append('installed APK SHA-256 missing or invalid')
            if cert != CANONICAL_CERT:
                errors.append('installed signing certificate differs from canonical identity')
            if installed.get('owner_accepted') is not True:
                errors.append('installed identity lacks explicit owner acceptance')
            if not safe_evidence(root, installed.get('evidence')):
                errors.append('installed acceptance evidence missing/unsafe')
        else:
            errors.append('installed status must be unconfirmed or verified')
        dirty = git(root, 'status', '--porcelain', '--untracked-files=normal')
        if dirty:
            notes.append('dirty worktree detected; preserve all local changes, do not reset or switch automatically')
            if require_clean:
                errors.append('release/test gate requires clean checkout')
        notes.append('tracking refs are local snapshots: fetch the named integration ref before relying on source freshness')
    except (OSError, ValueError, TypeError, KeyError, AttributeError, ET.ParseError, json.JSONDecodeError) as exc:
        errors.append('source-contract cannot be verified: ' + str(exc))
    return errors, notes


def current_release_pair(root):
    """Return the single verified development manifest identity for legacy gates."""
    errors, _ = check(root)
    if errors:
        raise ValueError('; '.join(errors))
    declared = source_record(root)['development']['manifest']
    return str(declared['version_code']), declared['version_name']



def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--require-installed', action='store_true')
    parser.add_argument('--require-clean', action='store_true')
    parser.add_argument('--print-source', action='store_true', help='print queue source-contract only; never modify files')
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[1]
    if args.print_source:
        print(json.dumps(source_record(root), indent=2))
        return 0
    errors, notes = check(root, args.require_installed, args.require_clean)
    for note in notes:
        print('NOTE:', note)
    for error in errors:
        print('FAIL:', error)
    if not errors:
        print('PASS: source ancestry, fetched branch, manifest, queue evidence and installed-state claims agree (not device acceptance)')
    return 1 if errors else 0


if __name__ == '__main__':
    raise SystemExit(main())
