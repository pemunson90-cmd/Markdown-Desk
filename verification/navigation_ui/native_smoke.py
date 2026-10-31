#!/usr/bin/env python3
"""Disposable API35 emulator only; uses real app/UI, never Pat's documents."""
import os, re, subprocess, time, tempfile
from pathlib import Path
import xml.etree.ElementTree as ET
ADB=os.environ.get('ADB','adb')
PACKAGE='com.example.markdowndesk'
OUT=Path(os.environ.get('NATIVE_EVIDENCE','native-evidence')); OUT.mkdir(parents=True,exist_ok=True)

def run(*args,timeout=120):
    return subprocess.run([ADB,*args],check=True,stdout=subprocess.PIPE,stderr=subprocess.PIPE,text=True,timeout=timeout).stdout

def ui():
    run('shell','uiautomator','dump','/sdcard/md-navigation-ui.xml')
    return ET.fromstring(run('exec-out','cat','/sdcard/md-navigation-ui.xml'))

def matches(root,phrase,exact=False):
    def has(n):
        values=(n.get('text',''),n.get('content-desc',''))
        return any(v.casefold()==phrase.casefold() if exact else phrase.casefold() in v.casefold() for v in values)
    return [n for n in root.iter('node') if has(n)]

def tap(phrase,exact=False,scroll=False):
    for _ in range(12 if scroll else 4):
        root=ui(); found=matches(root,phrase,exact)
        if found:
            candidates=[n for n in found if n.get('clickable')=='true'] or found
            x1,y1,x2,y2=map(int,re.findall(r'\d+',candidates[0].get('bounds','')))
            if x2>x1 and y2>y1:
                run('shell','input','tap',str((x1+x2)//2),str((y1+y2)//2));time.sleep(.8);return
        if scroll: run('shell','input','swipe','540','1850','540','650','350')
        time.sleep(.5)
    raise AssertionError('Missing tappable '+phrase+'; '+ET.tostring(root,encoding='unicode')[:4000])

def present(phrase):
    root=ui()
    if not matches(root,phrase):raise AssertionError('Missing '+phrase+'; '+ET.tostring(root,encoding='unicode')[:4000])

def absent(phrase):
    if matches(ui(),phrase):raise AssertionError('Unexpected '+phrase)

def no_error():
    root=ui()
    for phrase in ('Could not open recent file','Permission Denial','keeps stopping'):
        if matches(root,phrase):raise AssertionError(phrase)

def capture(name):
    (OUT/(name+'.xml')).write_bytes(ET.tostring(ui()))
    with (OUT/(name+'.png')).open('wb') as f:subprocess.run([ADB,'exec-out','screencap','-p'],check=True,stdout=f,timeout=30)

def launch():
    run('shell','am','start','-W','-n',PACKAGE+'/.MainActivity');time.sleep(3)

def commands(query=''):
    tap('More actions',exact=True);tap('Editing tools',exact=True);tap('Command palette',exact=True)
    present('Search commands')
    if query:search('Search commands',query)

def search(hint,value):
    tap(hint,exact=True)
    run('shell','input','text',value.replace(' ','%s'));time.sleep(.6)

def quick_downloads():
    tap('Files',exact=True);tap('Quick Downloads (list)',scroll=True)

def main():
    run('shell','wm','size','1080x2400')
    run('shell','settings','put','system','font_scale','1.0')
    run('shell','mkdir','-p','/sdcard/Download/second')
    with tempfile.TemporaryDirectory() as temp:
        p=Path(temp)/'moonshot-navigation.md'
        p.write_text('# Start\n\nbody\n## Target\nfirst target\n\ntext\n\n## Target\nsecond target\n',encoding='utf-8')
        run('push',str(p),'/sdcard/Download/moonshot-navigation.md')
        run('push',str(p),'/sdcard/Download/second/moonshot-navigation.md')
    run('shell','appops','set',PACKAGE,'MANAGE_EXTERNAL_STORAGE','allow')
    launch();quick_downloads();tap('moonshot-navigation.md');no_error()
    print('PASS unchanged Quick Downloads opens file',flush=True)
    commands();present('22 of 22');capture('commands-dark-portrait')
    search('Search commands','outline');present('1 of 22');tap('Document outline',exact=True)
    present('Search headings or line numbers');present('3 of 3')
    search('Search headings or line numbers','target');present('2 of 3');present('line 4');present('line 9');capture('filtered-outline-duplicates')
    tap('line 4');absent('Search headings or line numbers');no_error()
    print('PASS filtered command opens searchable duplicate-heading outline and selects',flush=True)
    commands('nomatchingcommand');present('No matching results');tap('Clear',exact=True);present('22 of 22')
    search('Search commands','read mode');present('1 of 22');run('shell','input','keyevent','KEYCODE_ENTER');time.sleep(1)
    absent('Search commands');no_error();print('PASS empty/clear and keyboard command selection',flush=True)
    tap('Files',exact=True);tap('Browse files',scroll=True)
    focused=run('shell','dumpsys','activity','activities')
    if 'documentsui' not in focused.lower():raise AssertionError('Native DocumentsUI not launched')
    run('shell','input','keyevent','KEYCODE_BACK');time.sleep(1)
    print('PASS native Android Browse files remains primary',flush=True)
    quick_downloads();tap('second',exact=False);tap('moonshot-navigation.md');no_error()
    commands('recent documents');tap('Recent documents',exact=True)
    present('Search names or locations');search('Search names or locations','second');present('1 of 2');capture('filtered-recents-duplicate-name')
    tap('moonshot-navigation.md');no_error()
    run('shell','am','force-stop',PACKAGE);launch()
    commands('recent documents');tap('Recent documents',exact=True);search('Search names or locations','second');tap('moonshot-navigation.md');no_error()
    print('PASS duplicate-name Recent selection and reopening after force-stop',flush=True)
    commands();run('shell','input','keyevent','KEYCODE_HOME');time.sleep(1);launch();absent('Search commands')
    print('PASS background stop retires navigation dialog',flush=True)
    commands();run('shell','settings','put','system','accelerometer_rotation','0');run('shell','settings','put','system','user_rotation','1');time.sleep(2);absent('Search commands');capture('landscape-after-rotation')
    run('shell','settings','put','system','user_rotation','0');time.sleep(2)
    print('PASS configuration change retires navigation dialog',flush=True)
    run('shell','settings','put','system','font_scale','1.3');time.sleep(2);commands('outline');present('Document outline');capture('large-font-command-search');tap('Close',exact=True)
    run('shell','appops','set',PACKAGE,'MANAGE_EXTERNAL_STORAGE','deny');run('shell','am','force-stop',PACKAGE);launch();quick_downloads();present('Allow Downloads access')
    print('PASS revoked Downloads access still requests consent',flush=True)
    print('NATIVE NAVIGATION SMOKE PASS — API35 emulator; physical Fold7 not tested',flush=True)

if __name__=='__main__':
    try:main()
    except BaseException:
        try:capture('failure')
        except Exception:pass
        try:(OUT/'logcat.txt').write_text(run('logcat','-d','-t','500'))
        except Exception:pass
        raise
