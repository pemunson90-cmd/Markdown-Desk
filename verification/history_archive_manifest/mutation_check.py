#!/usr/bin/env python3
"""Temporary copied-source mutations prove selected acceptance checks detect weakened guards.
Does not modify the worktree. Run explicitly; the default runner uses production code only.
"""
from pathlib import Path
import subprocess
import tempfile

root = Path(__file__).resolve().parents[2]
source = root / 'apps/MarkdownDesk/src'
manifest = source / 'com/example/markdowndesk/HistoryArchiveManifest.java'
original = manifest.read_text()
mutations = {
    'checksum': ('if(!MessageDigest.isEqual(expected,digest.digest()))', 'if(false)'),
    'true-eof': ('if(in.read()!=-1)', 'if(false)'),
    'archive-id-collision': ('if(!ids.add(entry.archiveId))', 'if(false)'),
    'native-order': ('if(prior!=null&&ORDER.compare(prior,entry)>=0)', 'if(false)'),
    'whole-inventory-count': ('count>MAX_ENTRIES||', 'false||'),
}
# The constructor independently repeats some defenses. Remove matching final admission guard
# as well where necessary, so the mutation actually weakens acceptance rather than redundancy.
secondary = {
    'archive-id-collision': ('if(!archiveIds.add(entry.archiveId))', 'if(false)'),
    'native-order': ('if(entry==null||prior!=null&&ORDER.compare(prior,entry)>=0)', 'if(entry==null)'),
    'whole-inventory-count': ('indexCharge(count)>MAX_INDEX_BYTES', 'false'),
}
for name, (before, after) in mutations.items():
    if original.count(before) != 1:
        raise SystemExit('Mutation location changed: ' + name)
    mutated = original.replace(before, after)
    if name in secondary:
        before, after = secondary[name]
        if mutated.count(before) != 1:
            raise SystemExit('Secondary location changed: ' + name)
        mutated = mutated.replace(before, after)
    with tempfile.TemporaryDirectory(prefix='manifest-mutation-') as temp:
        temp = Path(temp)
        edited = temp / 'HistoryArchiveManifest.java'
        edited.write_text(mutated)
        compiled = subprocess.run(['javac', '--release', '11', '-sourcepath', str(source), '-d', str(temp),
            str(edited), str(Path(__file__).with_name('HistoryManifestCheck.java')),
            str(Path(__file__).with_name('ManifestMemoryCheck.java'))], capture_output=True, text=True)
        if compiled.returncode:
            raise SystemExit('Mutation did not compile: ' + name + '\n' + compiled.stderr)
        result = subprocess.run(['java', '-Xmx128m', '-cp', str(temp),
            'com.example.markdowndesk.' + ('ManifestMemoryCheck' if name == 'whole-inventory-count' else 'HistoryManifestCheck')], capture_output=True, text=True)
        if result.returncode == 0 or 'AssertionError' not in result.stderr:
            raise SystemExit('Mutation was not caught behaviorally: ' + name + '\n' + result.stderr)
        failure = next(line.strip() for line in result.stderr.splitlines() if 'AssertionError' in line)
        print(name + ': caught — ' + failure)
print('Manifest mutation sensitivity: 5 deliberate guard removals caught')
