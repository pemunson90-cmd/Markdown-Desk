#!/usr/bin/env python3
"""Read-only guard against stale source/status; no checkout, reset, or network writes."""
import argparse
import json
from pathlib import Path
import re
import subprocess
import xml.etree.ElementTree as ET

STATE = 'docs/CURRENT_SOURCE.json'
MANIFEST = 'apps/MarkdownDesk/AndroidManifest.xml'
ANDROID = '{http://schemas.android.com/apk/res/android}'
BEGIN = '<!-- current-source:begin -->'
END = '<!-- current-source:end -->'


def git(root, *args):
    result = subprocess.run(['git', '-C', str(root), *args], text=True, capture_output=True)
    if result.returncode:
        raise ValueError('git ' + args[0] + ' could not verify requested revision')
    return result.stdout.strip()


def render_status(state):
    dev = state['development']
    manifest = dev['manifest']
    execution = state['execution']
    return '\n'.join([
        BEGIN, '# Current project status', '',
        'Generated summary of [CURRENT_SOURCE.json](CURRENT_SOURCE.json). Update that record first; regenerate this block with `python3 verification/check_current_source.py --print-status`.', '',
        f"- Active integration: `{dev['branch']}`; recorded source anchor `{dev['anchor_commit']}`.",
        f"- Source manifest: **{manifest['version_name']} / code{manifest['version_code']}**. This is an unfinished integration candidate, not device acceptance.",
        f"- Installed/owner-accepted baseline: **{state['installed']['status']}**. Never infer it from main, a version number, or a signed candidate.",
        f"- Execution state: **{execution['status']}**, observed {execution['observed_utc']}. This record does not claim ownership, a running worker, or permission to take over.",
        '- Before implementation: inspect existing ownership and dirty work, fetch the named integration branch without switching, then run `python3 verification/check_current_source.py`. A stale branch, manifest, or current summary fails the guard.',
        '- Before an in-place release: verify the actual installed identity and run `python3 verification/check_current_source.py --require-installed`; canonical signing and all existing release gates still apply.',
        f"- Next action: {state['next_action']}",
        '- Current evidence: ' + ', '.join(f'[{Path(p).name}]({p.removeprefix("docs/")})' for p in state['evidence']) + '.',
        '', 'Historical sections below are preserved verbatim. Their former “current”/“this branch” labels describe their original checkpoint, not today’s candidate. Read them only when needed for provenance.', END,
    ]) + '\n'


def check(root, require_installed=False):
    root = Path(root)
    errors, notices = [], []
    try:
        state = json.loads((root / STATE).read_text())
        if state['schema_version'] != 1:
            raise ValueError('unsupported state schema')
        dev = state['development']
        branch = dev['branch']
        git(root, 'check-ref-format', '--branch', branch)
        anchor = dev['anchor_commit']
        if not re.fullmatch('[0-9a-f]{40}', anchor):
            raise ValueError('source anchor must be an exact commit')
        try:
            git(root, 'merge-base', '--is-ancestor', anchor, 'HEAD')
        except ValueError:
            errors.append('source anchor is unavailable or not an ancestor of this checkout')
        tracking = 'refs/remotes/origin/' + branch
        try:
            git(root, 'rev-parse', '--verify', tracking + '^{commit}')
        except ValueError:
            errors.append('fetched integration ref is unavailable; fetch the named branch before implementation')
        else:
            try:
                git(root, 'merge-base', '--is-ancestor', tracking, 'HEAD')
            except ValueError:
                errors.append('checkout does not contain the fetched integration head; preserve local work and reconcile, do not reset')
        manifest = ET.parse(root / MANIFEST).getroot()
        actual = {'package': manifest.get('package'), 'version_code': int(manifest.get(ANDROID + 'versionCode')), 'version_name': manifest.get(ANDROID + 'versionName')}
        if actual != dev['manifest']:
            errors.append('source manifest differs from recorded development identity')
        installed = state['installed']
        if installed['status'] not in ('verified', 'unconfirmed'):
            errors.append('installed status must be verified or unconfirmed')
        elif installed['status'] == 'verified':
            commit = installed.get('commit') or ''
            if not re.fullmatch('[0-9a-f]{40}', commit):
                errors.append('verified installed baseline needs an exact source commit')
            else:
                try:
                    git(root, 'merge-base', '--is-ancestor', commit, 'HEAD')
                except ValueError:
                    errors.append('installed baseline source is unavailable or not preserved by ancestry')
        else:
            notices.append('installed/owner-accepted identity is unconfirmed; no device acceptance is claimed')
            if require_installed:
                errors.append('release gate requires a verified installed baseline')
        for path in state['evidence']:
            if not isinstance(path, str) or not (root / path).is_file() or not (root / path).resolve().is_relative_to(root.resolve()):
                errors.append('linked evidence is missing or outside the repository: ' + str(path))
        if state['execution']['status'] not in ('running', 'stopped', 'blocked', 'unknown'):
            errors.append('execution state must be running/stopped/blocked/unknown')
        status = (root / 'docs/STATUS.md').read_text()
        if not status.startswith(render_status(state)):
            errors.append('current status summary differs from CURRENT_SOURCE.json')
        notices.append('remote freshness is not inferred: fetch origin/' + branch + ' before using this guard to choose implementation source')
    except (OSError, ValueError, TypeError, KeyError, ET.ParseError) as exc:
        errors.append('current-source record cannot be verified: ' + str(exc))
    return errors, notices


def current_release_pair(root):
    """One recorded candidate pair, verified before extending legacy layout checks."""
    errors, _ = check(root)
    if errors:
        raise ValueError('; '.join(errors))
    manifest = json.loads((Path(root) / STATE).read_text())['development']['manifest']
    return str(manifest['version_code']), manifest['version_name']


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--require-installed', action='store_true')
    parser.add_argument('--print-status', action='store_true', help='print generated current header without modifying any files')
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[1]
    if args.print_status:
        print(render_status(json.loads((root / STATE).read_text())), end='')
        return 0
    errors, notices = check(root, args.require_installed)
    for message in notices:
        print('NOTE:', message)
    for message in errors:
        print('FAIL:', message)
    if not errors:
        print('PASS: current-source ancestry, manifest, evidence and status agree; release/device gates remain separate')
    return bool(errors)


if __name__ == '__main__':
    raise SystemExit(main())
