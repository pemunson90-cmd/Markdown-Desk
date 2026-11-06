#!/usr/bin/env python3
"""Offline Android crash report triage. Does not upload data.
Usage: python3 extract_crash.py path/to/bugreport.zip
Prints only the latest Markdown Desk FATAL EXCEPTION stack.
"""
import argparse, io, re, zipfile
from pathlib import Path

PACKAGE = "com.example.markdowndesk"
STACK = re.compile(r"(AndroidRuntime|\.java:\d+|\.kt:\d+|Caused by:|Exception:|Error:|Process:|FATAL EXCEPTION|Fatal signal|Cmdline:)")

def lines(path):
    path = Path(path)
    if zipfile.is_zipfile(path):
        with zipfile.ZipFile(path) as archive:
            members = [n for n in archive.namelist()
                       if n.lower().endswith((".txt", ".log"))
                       and any(k in n.lower() for k in ("bugreport", "logcat", "crash", "tombstone"))]
            for name in members[:24]:
                with archive.open(name) as stream:
                    yield from (line.rstrip("\r\n") for line in
                                io.TextIOWrapper(stream, encoding="utf-8", errors="replace"))
    else:
        with path.open(errors="replace") as stream:
            yield from (line.rstrip("\r\n") for line in stream)

def scan(path):
    pending, hits = [], []
    for line in lines(path):
        if re.search(r"(FATAL EXCEPTION|Fatal signal)", line):
            if pending:
                hits.append(pending)
            pending = [line]
        elif not pending and ("Process: " + PACKAGE in line or "Cmdline: " + PACKAGE in line):
            pending = [line]
        elif pending and (STACK.search(line) or len(pending) < 4):
            if len(pending) < 55:
                pending.append(line)
        elif pending and len(pending) >= 4:
            hits.append(pending)
            pending = []
        if len(hits) > 40:
            hits = hits[-40:]
    if pending:
        hits.append(pending)
    candidates = [h for h in hits if any(PACKAGE in line for line in h)]
    if not candidates:
        print("No Markdown Desk FATAL EXCEPTION found; absence does not prove no crash.")
        return 1
    print("Latest Markdown Desk crash candidate:")
    for line in candidates[-1][:45]:
        print(line[:500])
    return 0

if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("bugreport_zip_or_log")
    raise SystemExit(scan(parser.parse_args().bugreport_zip_or_log))
