#!/usr/bin/env python3
"""Local change-based check planner. Never packages, signs, or invokes CI."""
import argparse
import json
from pathlib import Path
import re
import subprocess
import sys
import time

ROOT = Path(__file__).resolve().parents[1]
CATALOG = ROOT / 'verification/check_catalog.json'


def git_paths(root, base):
    # Diff against base includes staged AND unstaged edits. Disable rename folding
    # so both old and new dependency ownership are considered.
    tracked = subprocess.check_output(
        ['git', 'diff', '--no-renames', '--name-only', '-z', base, '--'], cwd=root)
    untracked = subprocess.check_output(
        ['git', 'ls-files', '--others', '--exclude-standard', '-z'], cwd=root)
    return sorted(set(p.decode() for p in (tracked + untracked).split(b'\0') if p))


def is_production(path):
    return path.startswith(('apps/MarkdownDesk/src/', 'shared/')) and path.endswith('.java')


def is_document(path):
    return path.endswith('.md') and not path.startswith('verification/')


def source_dependents(root, changed):
    """Conservative Java identifier dependency closure, not a call-graph claim.

    Includes inherited/type references and shell/extractor fixtures via the explicit
    catalogue. Android resource indirection and unknown paths require manual review.
    """
    sources = list((root / 'apps/MarkdownDesk/src').rglob('*.java'))
    sources += list((root / 'shared').rglob('*.java'))
    affected = set(changed)
    tokens = {str(p.relative_to(root)): set(re.findall(r'\b[A-Za-z_$][\w$]*\b',
              p.read_text(encoding='utf-8'))) for p in sources}
    while True:
        names = {Path(p).stem for p in affected if is_production(p)}
        more = {p for p, words in tokens.items() if names & words}
        if more <= affected:
            return affected
        affected |= more


def plan(root, catalog, paths):
    affected = source_dependents(root, paths)
    selected, covered = [], set()
    for check in catalog['checks']:
        family = catalog['families'][check['family']]
        owned = set(family['inputs']) | set(family['test_paths'])
        hits = sorted(owned & affected)
        # A newly added fixture in an existing family still invalidates its runner.
        family_dir = check['family'] if not Path(check['family']).suffix else None
        hits += [p for p in paths if family_dir and p.startswith(family_dir + '/')]
        if not hits:
            continue
        selected.append({**check, 'reason_paths': sorted(set(hits))})
        covered.update(p for p in paths if p in owned or
                       (family_dir and p.startswith(family_dir + '/')))
    # Changed shared source can be covered via a tested dependent.
    for p in paths:
        if is_production(p) and any(p in catalog['families'][c['family']]['inputs']
                                    for c in selected):
            covered.add(p)
    ignored = [p for p in paths if is_document(p)]
    unknown = sorted(set(paths) - covered - set(ignored))
    # A mapping is a candidate test set, not proof of behavioral completeness.
    return {'changed': paths, 'documentation_only': bool(paths) and len(ignored) == len(paths),
            'checks': selected, 'unmapped': unknown,
            'native_compile_required': any(is_production(p) or p.startswith(
                ('apps/MarkdownDesk/res/', 'apps/MarkdownDesk/assets/')) or
                p == 'apps/MarkdownDesk/AndroidManifest.xml' for p in paths)}


def validate(root, catalog):
    errors, seen = [], set()
    for c in catalog['checks']:
        command = tuple(c['command'])
        if command in seen:
            errors.append('duplicate command: ' + ' '.join(command))
        seen.add(command)
        if not c.get('question') or not (root / command[1]).is_file():
            errors.append('missing question or runner: ' + c['id'])
        if c['family'] not in catalog['families']:
            errors.append('missing input family: ' + c['id'])
    for name, family in catalog['families'].items():
        for p in family['inputs'] + family['test_paths']:
            if not (root / p).is_file():
                errors.append('stale input ownership: ' + name + ' -> ' + p)
    return errors


def main():
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument('--base', default='HEAD', help='last relevant verified commit; default HEAD includes working edits')
    ap.add_argument('--paths', nargs='+', help='explicit paths for planning examples; cannot be combined with --run')
    ap.add_argument('--run', action='store_true', help='execute selected host checks, stop at first failure')
    ap.add_argument('--audit', action='store_true', help='validate inventory and list retirement candidates; no tests')
    ap.add_argument('--json', action='store_true')
    args = ap.parse_args()
    if args.run and args.paths:
        ap.error('--run uses actual git changes; --paths is planning only')
    catalog = json.loads(CATALOG.read_text())
    errors = validate(ROOT, catalog)
    if errors:
        print('\n'.join(errors), file=sys.stderr)
        return 2
    if args.audit:
        print(json.dumps({'checks': len(catalog['checks']), 'inventory_errors': errors,
              'review_candidates': [c['id'] for c in catalog['checks'] if c['kind'] in ('static', 'tooling')],
              'policy': 'Candidates are not deletions. Retire only with a replacement or obsolete-risk rationale.'}, indent=2))
        return 0
    result = plan(ROOT, catalog, args.paths or git_paths(ROOT, args.base))
    if args.json:
        print(json.dumps(result, indent=2))
    else:
        for c in result['checks']:
            print(c['id'] + ': ' + c['question'])
            print('  ' + ' '.join(c['command']))
        if result['documentation_only']:
            print('Documentation only: review diff/links; no application tests.')
        if result['native_compile_required']:
            print('Integration check: bash verification/compile_native_sources_only.sh (run once before delivery).')
        if result['unmapped']:
            print('UNMAPPED: assign a question/check or document a manual review; no automatic green:',
                  ', '.join(result['unmapped']))
        print('STOP after selected checks pass. This is a conservative candidate set, not release authorization.')
    if result['unmapped']:
        return 2
    if args.run:
        if not result['checks']:
            print('No host checks executed; an empty plan is not a test pass.')
            return 0
        records = []
        out = ROOT / 'verification/build/selected-results.json'
        out.parent.mkdir(parents=True, exist_ok=True)
        for c in result['checks']:
            start = time.monotonic()
            done = subprocess.run(c['command'], cwd=ROOT)
            records.append({'id': c['id'], 'exit_code': done.returncode,
                            'seconds': round(time.monotonic() - start, 3)})
            out.write_text(json.dumps({'base': args.base, 'plan': result, 'results': records,
                           'native_compile': 'NOT RUN by selector',
                           'release_acceptance': 'NOT ESTABLISHED'}, indent=2) + '\n')
            if done.returncode:
                return done.returncode
        print('Selected host checks passed. Native/device/release gates are separate; stop here.')
    return 0


if __name__ == '__main__':
    sys.exit(main())
