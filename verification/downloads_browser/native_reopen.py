#!/usr/bin/env python3
"""Real Android emulator test of Downloads browsing and durable Recents."""
import os
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET

ADB = os.environ.get("ADB", "adb")
PACKAGE = "com.example.markdowndesk"
TITLE = "download-reopen-proof.md"
SOURCE = "/sdcard/Download/" + TITLE

def run(*args, timeout=150):
    return subprocess.run([ADB, *args], check=True, stdout=subprocess.PIPE,
                          stderr=subprocess.PIPE, text=True, timeout=timeout).stdout

def ui():
    run("shell", "uiautomator", "dump", "/sdcard/md-downloads-ui.xml")
    payload = run("exec-out", "cat", "/sdcard/md-downloads-ui.xml")
    return ET.fromstring(payload)

def nodes(phrase, *, exact=False):
    root = ui()
    return [n for n in root.iter("node")
            if n.attrib.get("clickable") == "true" and (
                (n.attrib.get("text", "") == phrase or n.attrib.get("content-desc", "") == phrase)
                if exact else (phrase in n.attrib.get("text", "")
                               or phrase in n.attrib.get("content-desc", "")))]

def click(phrase, *, exact=False, scroll=False):
    for step in range(12 if scroll else 4):
        matches = nodes(phrase, exact=exact)
        if matches:
            match = re.fullmatch(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", matches[0].attrib.get("bounds", ""))
            if not match:
                raise AssertionError("No bounds for: " + phrase)
            x1, y1, x2, y2 = map(int, match.groups())
            run("shell", "input", "tap", str((x1+x2)//2), str((y1+y2)//2))
            time.sleep(2)
            return
        if scroll:
            run("shell", "input", "swipe", "160", "650", "160", "240", "330")
        time.sleep(1)
    raise AssertionError("Missing clickable UI item: " + phrase)

def assert_present(phrase):
    root = ui()
    text = " ".join((n.attrib.get("text", "") + " " + n.attrib.get("content-desc", ""))
                    for n in root.iter("node"))
    if phrase not in text:
        raise AssertionError("Expected UI text missing: " + phrase + "; observed: " + text[:2000])

def assert_no_error():
    root = ui()
    text = " ".join(n.attrib.get("text", "") for n in root.iter("node"))
    if "Could not open recent file" in text or "Permission Denial" in text:
        raise AssertionError("Recent failed to reopen: " + text[:1200])

def main():
    run("shell", "wm", "size", "360x800")
    run("shell", "mkdir", "-p", "/sdcard/Download")
    run("shell", "sh", "-c", "printf '# Downloads entry point proof\\n\\nSaved file.\\n' > " + SOURCE)
    run("shell", "appops", "set", PACKAGE, "MANAGE_EXTERNAL_STORAGE", "allow")
    run("shell", "am", "force-stop", PACKAGE)
    run("shell", "am", "start", "-W", "-n", PACKAGE + "/.MainActivity")
    time.sleep(3)
    click("Files", exact=True)
    click("Downloads", scroll=True)
    assert_present(TITLE)
    click(TITLE)
    assert_no_error()
    print("PASS opened direct Downloads file with All files access")
    run("shell", "am", "force-stop", PACKAGE)
    run("shell", "am", "start", "-W", "-n", PACKAGE + "/.MainActivity")
    time.sleep(3)
    click("Files", exact=True)
    click(TITLE, scroll=True)
    time.sleep(1)
    assert_no_error()
    print("PASS reopened same document through persisted Recents after force-stop")
    run("shell", "appops", "set", PACKAGE, "MANAGE_EXTERNAL_STORAGE", "deny")
    run("shell", "am", "force-stop", PACKAGE)
    run("shell", "am", "start", "-W", "-n", PACKAGE + "/.MainActivity")
    time.sleep(3)
    click("Files", exact=True)
    click("Downloads", scroll=True)
    assert_present("Allow Downloads access")
    print("PASS revoked All files access requests consent rather than bypassing it")

if __name__ == "__main__":
    try:
        main()
    except Exception as error:
        print("NATIVE DOWNLOADS TEST FAILED:", error, file=sys.stderr)
        try:
            print(run("shell", "dumpsys", "package", PACKAGE, timeout=30)[-2500:], file=sys.stderr)
        except Exception:
            pass
        raise
