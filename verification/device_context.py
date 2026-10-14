#!/usr/bin/env python3
"""Read-only device context; never manufactures UI/benchmark acceptance."""
import argparse
import json
import os
from pathlib import Path
import re
import shutil
import subprocess

PACKAGE='com.example.markdowndesk'
p=argparse.ArgumentParser(description=__doc__)
p.add_argument('--adb',default=os.environ.get('ADB','adb'))
p.add_argument('--serial',help='Select a device; omitted from the report')
p.add_argument('--output',type=Path,default=Path('MarkdownDesk-device-context.json'))
a=p.parse_args()
adb=shutil.which(a.adb)
if not adb:p.error('ADB is unavailable; use the already installed platform-tools adb with --adb')
base=[adb]+(['-s',a.serial] if a.serial else [])
def query(*args):
 return subprocess.check_output(base+list(args),text=True,stderr=subprocess.PIPE,timeout=30).strip()
try:
 if query('get-state')!='device':p.error('No ready Android device')
 package=query('shell','dumpsys','package',PACKAGE)
 def match(pattern):
  found=re.search(pattern,package)
  return found.group(1) if found else None
 report={'schema':1,'package':PACKAGE,'device_model':query('shell','getprop','ro.product.model'),
  'android_release':query('shell','getprop','ro.build.version.release'),
  'android_api':query('shell','getprop','ro.build.version.sdk'),
  'display_size':query('shell','wm','size'),
  'installed_version_name':match(r'versionName=([^\s]+)'),
  'installed_version_code':match(r'versionCode=(\d+)'),
  'memory_report':query('shell','dumpsys','meminfo',PACKAGE),
  'ui_acceptance':'NOT_RUN','benchmark_acceptance':'NOT_RUN',
  'notes':'Read-only context collection; no serial, user documents, screenshots, signatures or signing secrets collected.'}
 a.output.write_text(json.dumps(report,indent=2)+'\n',encoding='utf8')
 print('Device context written to',a.output)
except (subprocess.SubprocessError,OSError) as error:
 p.error('Device context unavailable: '+type(error).__name__)
