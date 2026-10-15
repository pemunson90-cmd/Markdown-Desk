#!/usr/bin/env python3
"""Optional copied-source sensitivity checks. Never changes production source."""
from pathlib import Path
import os, subprocess, tempfile
here = Path(__file__).resolve().parent
root = here.parent.parent
source = (root / 'apps/MarkdownDesk/src/com/example/markdowndesk/MainActivity.java').read_text()
cases = [
 ('request guard', 'return request == this.historyRequest && !isFinishing()', 'return !isFinishing()', 'stale load suppressed scenario 0'),
 ('row color', 'row.setTextColor(MainActivity.this.palette.text);', 'row.setTextColor(0);', 'row binding theme 0'),
 ('orphan status', 'default: return "Protected snapshot · no completed save";', 'default: return "Captured revision";', 'orphan original retains source role and honest status')
]
with tempfile.TemporaryDirectory(prefix='history-ui-mutations-') as temp:
 for name, old, new, expected in cases:
  if source.count(old) != 1:
   raise SystemExit('Mutation anchor changed: ' + name)
  path = Path(temp) / 'MainActivity.java'
  path.write_text(source.replace(old, new))
  result = subprocess.run([str(here / 'run.sh'), str(path)], text=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
  if result.returncode == 0 or 'AssertionError: ' + expected not in result.stdout:
   print(result.stdout)
   raise SystemExit('Expected behavioral rejection missing: ' + name)
  print(name + ': expected RED — ' + expected)
print('3 copied-source sensitivity checks PASS; these are not pre-existing production regressions')
