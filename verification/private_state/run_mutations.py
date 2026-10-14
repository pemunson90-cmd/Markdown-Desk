#!/usr/bin/env python3
"""Fixture sensitivity, never represented as a failing earlier production implementation."""
from pathlib import Path
import os
import subprocess

root = Path(__file__).resolve().parents[2]
source = (root / 'apps/MarkdownDesk/src/com/example/markdowndesk/PrivateStateCodec.java').read_text()
mutants = [
    ('checksum', 'if (!MessageDigest.isEqual(expected, digest.digest())) throw new IOException("Private-state checksum mismatch");',
     'digest.digest();', 'Accepted corrupt frame byte'),
    ('trailing_bytes', 'if (in.read() != -1) throw new IOException("Trailing private-state bytes");',
     'in.read();', 'Accepted trailing byte'),
    ('cursor_range', 'if (start < 0 || end < 0 || start > units || end > units) throw new IOException("Cursor exceeds decoded text");',
     '// Deliberate test-only mutant: omit cursor range check.', 'Accepted invalid start 4'),
    ('theme_range', 'number <= 8', 'number <= 9', 'Accepted theme9'),
    ('payload_utf8', 'final CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()\n                .onMalformedInput(CodingErrorAction.REPORT)',
     'final CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()\n                .onMalformedInput(CodingErrorAction.REPLACE)', 'Accepted JDK malformed UTF8 oracle'),
]
for name, before, after, expected in mutants:
    if source.count(before) != 1:
        raise AssertionError(f'{name}: production mutation anchor changed')
    out = root / 'verification/build/private-state-mutations' / name
    out.mkdir(parents=True, exist_ok=True)
    mutant = out / 'PrivateStateCodec.java'
    mutant.write_text(source.replace(before, after))
    subprocess.run([os.environ.get('JAVAC', 'javac'), '--release', '11', '-d', str(out), str(mutant),
                    str(root / 'verification/private_state/PrivateStateCheck.java')], check=True,
                   capture_output=True, text=True)
    result = subprocess.run([os.environ.get('JAVA', 'java'), '-Xmx128m', '-cp', str(out),
                             'com.example.markdowndesk.PrivateStateCheck'], capture_output=True, text=True)
    output = result.stdout + result.stderr
    if result.returncode == 0 or expected not in output:
        raise AssertionError(f'{name}: expected behavioral assertion absent; exit={result.returncode}\n{output}')
    failure = next(line for line in output.splitlines() if 'java.lang.AssertionError:' in line)
    print(f'EXPECTED RED mutant {name}: {failure}')
print('PASS 5 deliberate mutation sensitivity checks; production codec is not mutated')
