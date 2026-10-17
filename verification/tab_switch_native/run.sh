#!/usr/bin/env bash
# Disposable Android emulator only; do not run on the owner's installed app.
set -euo pipefail
ADB="$ANDROID_HOME/platform-tools/adb"
DEST="$RUNNER_TEMP/md-tab-switch"
mkdir -p "$DEST"
"$ADB" shell wm density 160
"$ADB" shell wm size 360x800
"$ADB" shell am force-stop com.example.markdowndesk
"$ADB" shell am start -W -n com.example.markdowndesk/.MainActivity
sleep 6
step=0
dump() {
  local label="$1"
  timeout 45 "$ADB" shell uiautomator dump /sdcard/md-tabs.xml >/dev/null
  "$ADB" pull /sdcard/md-tabs.xml "$DEST/$label.xml" >/dev/null
  cp "$DEST/$label.xml" "$DEST/last.xml"
  "$ADB" shell pidof com.example.markdowndesk >/dev/null
}
tap() {
  local id="$1" field="$2"
  step=$((step+1))
  dump "before-$step"
  local xy
  xy="$(python3 - "$DEST/last.xml" "$id" "$field" <<'PY'
import re,sys,xml.etree.ElementTree as ET
root=ET.parse(sys.argv[1]).getroot()
needle,field=sys.argv[2:]
nodes=[x for x in root.iter("node") if x.get(field)==needle and x.get("clickable")=="true"]
if len(nodes)!=1: raise SystemExit("Ambiguous/missing tap "+repr(needle)+": "+str(len(nodes)))
m=re.fullmatch(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]",nodes[0].get("bounds",""))
if not m: raise SystemExit("No bounds for "+needle)
a,b,c,d=map(int,m.groups())
print((a+c)//2,(b+d)//2)
PY
)"
  "$ADB" shell input tap $xy
  sleep .3
}
for template in "Project README" "Daily journal" "Checklist"; do
  tap "New document" content-desc
  tap "$template" text
done
for pass in $(seq 1 9); do
  tap "Switch to project-readme.md" content-desc
  tap "Switch to daily-journal.md" content-desc
  tap "Switch to checklist.md" content-desc
done
sleep 3
"$ADB" shell am force-stop com.example.markdowndesk
"$ADB" shell am start -W -n com.example.markdowndesk/.MainActivity
sleep 6
dump after-relaunch
python3 - "$DEST/after-relaunch.xml" <<'PY'
import sys,xml.etree.ElementTree as ET
nodes=list(ET.parse(sys.argv[1]).getroot().iter("node"))
descs={x.get("content-desc","") for x in nodes}
for label in ("Switch to project-readme.md","Switch to daily-journal.md","Switch to checklist.md"):
    if label not in descs: raise SystemExit("Restored tab missing: "+label)
print("PASS: 27 native tab switches and three restored tabs after relaunch")
PY
tap "Switch to project-readme.md" content-desc
dump responsive-after-relaunch
"$ADB" logcat -d -t 1200 > "$DEST/logcat.txt" || true
echo 'PASS native repeated tab switches and cold relaunch; owner Fold7 and large documents NOT RUN.' >> "$GITHUB_STEP_SUMMARY"
