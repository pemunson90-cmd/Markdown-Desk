#!/usr/bin/env bash
# Run immediately after run.sh in the same disposable emulator instance.
# Every long-press is a genuine Android touch gesture, not a direct method call.
set -euo pipefail
ADB="$ANDROID_HOME/platform-tools/adb"
DEST="$RUNNER_TEMP/md-tab-switch"
mkdir -p "$DEST"
step=0

dump() {
  local name="$1"
  timeout 45 "$ADB" shell uiautomator dump /sdcard/md-longpress.xml >/dev/null
  "$ADB" pull /sdcard/md-longpress.xml "$DEST/longpress-$name.xml" >/dev/null
  cp "$DEST/longpress-$name.xml" "$DEST/longpress-last.xml"
  "$ADB" shell pidof com.example.markdowndesk >/dev/null
}
locate() {
  python3 - "$DEST/longpress-last.xml" "$1" "$2" <<'PY'
import sys,re,xml.etree.ElementTree as ET
root=ET.parse(sys.argv[1]).getroot()
needle,field=sys.argv[2:];nodes=[n for n in root.iter("node") if n.get(field)==needle]
if len(nodes)!=1: raise SystemExit("Expected exactly one visible "+repr(needle)+": "+str(len(nodes)))
m=re.fullmatch(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]",nodes[0].get("bounds",""))
if m is None: raise SystemExit("No screen coordinates for "+needle)
a,b,c,d=map(int,m.groups()); print((a+c)//2,(b+d)//2)
PY
}
tap() {
  step=$((step+1));dump "before-tap-$step"
  local xy;xy="$(locate "$1" "$2")"
  "$ADB" shell input tap $xy
  sleep .5
}
hold() {
  step=$((step+1));dump "before-hold-$step"
  local xy;xy="$(locate "$1" "$2")"
  local x y;read -r x y <<< "$xy"
  timeout 20 "$ADB" shell input swipe "$x" "$y" "$x" "$y" 950
  sleep .7
  dump "after-hold-$step"
}
assert_ui() {
  python3 - "$DEST/longpress-last.xml" "$1" "$2" <<'PY'
import sys,xml.etree.ElementTree as ET
root=ET.parse(sys.argv[1]).getroot()
needle,field=sys.argv[2:]
if not any(n.get(field)==needle for n in root.iter("node")):
  raise SystemExit("Expected UI element missing: "+repr(needle))
PY
}
assert_selected() {
  python3 - "$DEST/longpress-last.xml" "$1" <<'PY'
import sys,xml.etree.ElementTree as ET
nodes=[n for n in ET.parse(sys.argv[1]).getroot().iter("node") if n.get("content-desc")==sys.argv[2]]
if len(nodes)!=1 or nodes[0].get("selected")!="true":
  raise SystemExit("Active tab changed or did not restore: "+sys.argv[2])
PY
}

# Previous suite leaves project-readme.md active, with unsaved daily-journal.md
# still present. Inactive long-press must not activate the other tab.
hold "Switch to daily-journal.md" content-desc
assert_ui "Unsaved changes · daily-journal.md" text
assert_ui "Open to save" text
assert_ui "Discard and close" text
tap "Cancel" text
dump "cancel-keeps-active"
assert_selected "Switch to project-readme.md"
assert_ui "Switch to daily-journal.md" content-desc

# Explicit discard closes only the long-pressed inactive tab; current editor
# remains on project-readme.md and must still accept taps.
hold "Switch to daily-journal.md" content-desc
tap "Discard and close" text
dump "inactive-removed"
assert_selected "Switch to project-readme.md"
python3 - "$DEST/longpress-last.xml" <<'PY'
import sys,xml.etree.ElementTree as ET
descs=[n.get("content-desc","") for n in ET.parse(sys.argv[1]).getroot().iter("node")]
if "Switch to daily-journal.md" in descs:
  raise SystemExit("Explicitly discarded inactive tab still exists")
PY

# Active dirty tab uses the existing save/discard/cancel protection.
hold "Switch to project-readme.md" content-desc
assert_ui "Unsaved changes" text
tap "Cancel" text
dump "active-cancel"
assert_selected "Switch to project-readme.md"
hold "Switch to project-readme.md" content-desc
tap "Discard" text
dump "active-discard"
assert_ui "Switch to checklist.md" content-desc

# A clean unsaved blank tab can be closed directly without switching or a
# confirmation prompt, even while a different document is active.
tap "New document" content-desc
tap "Blank document" text
tap "Switch to checklist.md" content-desc
hold "Switch to Untitled.md" content-desc
dump "clean-inactive-removed"
assert_selected "Switch to checklist.md"
python3 - "$DEST/longpress-last.xml" <<'PY'
import sys,xml.etree.ElementTree as ET
texts=[n.get("text","") for n in ET.parse(sys.argv[1]).getroot().iter("node")]
if "Unsaved changes" in texts: raise SystemExit("Clean inactive close displayed dirty confirmation")
PY

sleep 2
"$ADB" shell am force-stop com.example.markdowndesk
"$ADB" shell am start -W -n com.example.markdowndesk/.MainActivity
sleep 6
dump "relaunch-after-closes"
assert_ui "Switch to checklist.md" content-desc
assert_selected "Switch to checklist.md"
"$ADB" logcat -d -t 1500 > "$DEST/longpress-logcat.txt" || true
echo 'PASS native long-press closes active/inactive dirty and clean tabs, preserves active selection, cancel, cold relaunch.' >> "$GITHUB_STEP_SUMMARY"
