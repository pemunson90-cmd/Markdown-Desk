#!/usr/bin/env bash
# Disposable Android emulator only; RED baseline for inactive-tab long-press closure.
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
# Android's AlertDialog rows often expose their text on a non-clickable TextView;
# tapping that TextView's bounds still activates its clickable row parent.
nodes=[x for x in root.iter("node") if (x.get(field,"").casefold()==needle.casefold() if field=="text" else x.get(field)==needle) and (field=="text" or x.get("clickable")=="true")]
if len(nodes)!=1: raise SystemExit("Ambiguous/missing tap "+repr(needle)+": "+str(len(nodes)))
m=re.fullmatch(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]",nodes[0].get("bounds",""))
if not m: raise SystemExit("No bounds for "+needle)
a,b,c,d=map(int,m.groups())
print((a+c)//2,(b+d)//2)
PY
)"
  "$ADB" shell input tap $xy
  sleep .3
  if [[ "$id" == "Switch to "* ]]; then
    dump "after-tab-$step"
    python3 - "$DEST/last.xml" "$id" <<'PY'
import sys,xml.etree.ElementTree as ET
nodes=[x for x in ET.parse(sys.argv[1]).getroot().iter("node")
       if x.get("content-desc")==sys.argv[2]]
if len(nodes)!=1 or nodes[0].get("selected")!="true":
    raise SystemExit("Tab click did not select "+repr(sys.argv[2]))
PY
  fi
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
# The horizontal tab strip clips offscreen buttons from UIAutomator; inspect
# both the rightmost and leftmost viewports, not just one screenshot.
"$ADB" shell input swipe 55 115 335 115 500
sleep 1
dump after-relaunch-left
python3 - "$DEST/after-relaunch.xml" "$DEST/after-relaunch-left.xml" <<'PY'
import sys,xml.etree.ElementTree as ET
descs=set()
for path in sys.argv[1:]:
    descs.update(x.get("content-desc","") for x in ET.parse(path).getroot().iter("node"))
for label in ("Switch to project-readme.md","Switch to daily-journal.md","Switch to checklist.md"):
    if label not in descs: raise SystemExit("Restored tab not found across horizontal strip: "+label)
print("PASS: 27 selected native tab switches and three restored tabs after relaunch")
PY

tap "Switch to project-readme.md" content-desc
dump responsive-after-relaunch

# Reproduce a real long-press on a *different*, unsaved document while another
# document is selected. The action must never navigate away just to ask about
# closing; Cancel must keep original selection, and Discard must close exactly
# the targeted tab.
tap "Switch to daily-journal.md" content-desc
"$ADB" shell input swipe 55 115 335 115 400
sleep 1
dump before-longpress
xy="$(python3 - "$DEST/last.xml" <<'PY'
import re,sys,xml.etree.ElementTree as ET
nodes=[x for x in ET.parse(sys.argv[1]).getroot().iter("node")
       if x.get("content-desc")=="Switch to project-readme.md"]
if len(nodes)!=1:raise SystemExit("Offscreen long-press tab: "+str(len(nodes)))
m=re.fullmatch(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]",nodes[0].get("bounds",""))
if not m:raise SystemExit("Cannot long-press tab without bounds")
a,b,c,d=map(int,m.groups());print((a+c)//2,(b+d)//2)
PY
)"
set -- $xy
"$ADB" shell input swipe "$1" "$2" "$1" "$2" 1050
sleep 1
dump close-dialog
python3 - "$DEST/last.xml" <<'PY'
import sys,xml.etree.ElementTree as ET
nodes=list(ET.parse(sys.argv[1]).getroot().iter("node"))
texts={x.get("text","") for x in nodes}
if "Unsaved changes" not in texts:raise SystemExit("Long-press did not show guarded Unsaved changes dialog")
PY
tap "Cancel" text
dump after-close-cancel
python3 - "$DEST/last.xml" <<'PY'
import sys,xml.etree.ElementTree as ET
nodes=list(ET.parse(sys.argv[1]).getroot().iter("node"))
d={x.get("content-desc",""):x.get("selected") for x in nodes}
if d.get("Switch to daily-journal.md")!="true":
    raise SystemExit("BUG: long-press Cancel changed the active document")
if "Switch to project-readme.md" not in d:
    raise SystemExit("Cancel incorrectly removed targeted tab")
print("PASS: long-press Cancel keeps active document and targeted tab")
PY
# Repeat and discard only the target; no intermediate switches are allowed.
"$ADB" shell input swipe "$1" "$2" "$1" "$2" 1050
sleep 1
dump before-close-discard
tap "Discard" text
dump after-close-discard
python3 - "$DEST/last.xml" <<'PY'
import sys,xml.etree.ElementTree as ET
d={x.get("content-desc",""):x.get("selected") for x in ET.parse(sys.argv[1]).getroot().iter("node")}
if d.get("Switch to daily-journal.md")!="true":raise SystemExit("Long-press Discard changed active document")
if "Switch to project-readme.md" in d:raise SystemExit("Long-press Discard retained targeted tab")
print("PASS: long-press Discard removes only original target without switching")
PY
tap "New document" content-desc
tap "Blank document" text
dump before-clean-close
xy="$(python3 - "$DEST/last.xml" <<'PY'
import re,sys,xml.etree.ElementTree as ET
nodes=[x for x in ET.parse(sys.argv[1]).getroot().iter("node")
       if x.get("content-desc")=="Switch to Untitled.md" and x.get("selected")=="true"]
if len(nodes)!=1:raise SystemExit("Cannot find selected clean new tab")
m=re.fullmatch(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]",nodes[0].get("bounds",""))
a,b,c,d=map(int,m.groups());print((a+c)//2,(b+d)//2)
PY
)"
set -- $xy
"$ADB" shell input swipe "$1" "$2" "$1" "$2" 1050
sleep 1
dump clean-closed
"$ADB" shell input tap 180 60
dump still-responsive
echo 'PASS: native long-press targeted dirty tab Cancel/Discard, clean close, and touch responsiveness.' >> "$GITHUB_STEP_SUMMARY"

"$ADB" logcat -d -t 1200 > "$DEST/logcat.txt" || true
echo 'PASS native repeated tab switches and cold relaunch; owner Fold7 and large documents NOT RUN.' >> "$GITHUB_STEP_SUMMARY"
