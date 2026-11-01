#!/usr/bin/env python3
"""Disposable API35 emulator only: actual WebView Reading-mode footnotes and autolinks (code29).

Runs after native_smoke.py against the same installed test-signed APK. Uses the existing
Quick Downloads open route and command-palette Read mode; asserts on the real WebView
accessibility tree and on the ACTION_VIEW intent a tapped autolink produces.
"""
import sys, tempfile, time
from pathlib import Path
sys.path.insert(0, str(Path(__file__).resolve().parent))
from native_smoke import (run, ui, matches, tap, present, absent, no_error, capture, launch,
                          commands, search, quick_downloads, PACKAGE)
import re

DOC = ('# Reader links\n\n'
       'A sourced claim[^src] and a second one[^two].\n\n'
       'Visit https://autolink.example/path_a?x=1 today.\n\n'
       '[^src]: Primary source note.\n'
       '[^two]: Secondary note text.\n')

def node_with(phrase):
    found = matches(ui(), phrase)
    if not found: raise AssertionError('Missing ' + phrase)
    return found[0]

def main():
    run('shell', 'settings', 'put', 'system', 'font_scale', '1.0')
    run('shell', 'appops', 'set', '--uid', PACKAGE, 'MANAGE_EXTERNAL_STORAGE', 'allow')
    with tempfile.TemporaryDirectory() as temp:
        p = Path(temp) / 'reader-links.md'
        p.write_text(DOC, encoding='utf-8')
        run('push', str(p), '/sdcard/Download/reader-links.md')
    run('shell', 'am', 'force-stop', PACKAGE)
    launch(); quick_downloads(); tap('reader-links.md'); no_error()
    commands('read mode'); run('shell', 'input', 'keyevent', 'KEYCODE_ENTER'); time.sleep(2.5)
    absent('Search commands'); no_error()
    capture('reader-links-read-mode')
    present('Primary source note.'); present('Secondary note text.')
    absent('[^src]'); absent('[^two]'); absent('[^src]:')
    print('PASS footnote references and definitions render in native WebView Read mode', flush=True)
    url = 'https://autolink.example/path_a?x=1'
    n = node_with(url)
    (Path(__import__('os').environ.get('NATIVE_EVIDENCE', 'native-evidence')) / 'autolink-node.txt').write_text(str(n.attrib))
    run('logcat', '-c')
    x1, y1, x2, y2 = map(int, re.findall(r'\d+', n.get('bounds', '')))
    run('shell', 'input', 'tap', str((x1 + x2) // 2), str((y1 + y2) // 2)); time.sleep(2.5)
    log = run('logcat', '-d', '-b', 'main', '-b', 'system', '-t', '3000', timeout=60)
    view = [l for l in log.splitlines() if 'android.intent.action.VIEW' in l and 'autolink.example' in l]
    toast = matches(ui(), 'No app can open this link')
    if not view and not toast:
        raise AssertionError('Tapping the autolink produced no ACTION_VIEW for autolink.example')
    print('PASS tapped autolink dispatched ACTION_VIEW ' + ('(activity start logged)' if view else '(no handler installed; app toast shown)'), flush=True)
    run('shell', 'input', 'keyevent', 'KEYCODE_BACK'); time.sleep(1)
    print('READER LINKS NATIVE SMOKE PASS: API35 emulator; physical Fold7 not tested', flush=True)

if __name__ == '__main__':
    try: main()
    except BaseException:
        try: capture('reader-links-failure')
        except Exception: pass
        raise
