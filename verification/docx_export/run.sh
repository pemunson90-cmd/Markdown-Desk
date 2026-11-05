#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../.."
core_only=false
if [[ "${1:-}" == "--core-only" ]]; then core_only=true; shift; fi
if (( $# != 0 )); then echo 'Usage: run.sh [--core-only]' >&2; exit 2; fi
out=verification/build/docx-export
mkdir -p "$out/classes"
javac --release 11 -d "$out/classes" apps/MarkdownDesk/src/com/example/markdowndesk/MarkdownDocx.java verification/docx_export/DocxCheck.java verification/docx_export/ReviewRegression.java
java -Xmx64m -cp "$out/classes" com.example.markdowndesk.DocxCheck "$out"
java -Xmx64m -cp "$out/classes" com.example.markdowndesk.ReviewRegression "$out"
python3 verification/docx_export/check.py "$out"
unzip -t "$out/rich.docx" >/dev/null
if "$core_only"; then
  echo 'PASS core Java/ZIP/XML/CRC checks only; Office/PDF checks NOT RUN (--core-only).'
  exit 0
fi
office=$(mktemp -d "$out/office.XXXXXXXX")
profile=$(mktemp -d "$out/profile.XXXXXXXX")
timeout 45s soffice "-env:UserInstallation=file://$(realpath "$profile")" --headless --convert-to pdf --outdir "$office" "$out/rich.docx"
test -s "$office/rich.pdf"
pdftotext -layout "$office/rich.pdf" "$office/rich.txt"
python3 verification/docx_export/check_pdf.py "$office/rich.txt"

printf "Fresh office evidence: %s\n" "$office"
