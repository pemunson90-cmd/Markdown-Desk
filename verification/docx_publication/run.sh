#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../.."
out=$(mktemp -d)
trap 'python3 -c "import shutil,sys;shutil.rmtree(sys.argv[1])" "$out"' EXIT
mapfile -t stubs < <(find verification/docx_publication/stubs -name '*.java' -print)
javac --release 11 -d "$out" "${stubs[@]}" verification/workspace_tree_reader/stubs/android/{net/Uri,database/Cursor}.java apps/MarkdownDesk/src/com/example/markdowndesk/{MarkdownDocx,DocxStage,DocxNewCopy,DocumentDocxExport}.java verification/docx_publication/DocxPublicationCheck.java
java -Xmx64m -Ddocx.fixture="$out/provider.docx" -cp "$out" com.example.markdowndesk.DocxPublicationCheck
for mode in worker-reject directory-close provider-close error-write error-close review-stage review-supplier review-phase review-stage-close review-phase-close review-cancel; do
 java -Xmx64m -cp "$out" com.example.markdowndesk.DocxPublicationCheck "$mode"
done
python3 - "$out/provider.docx" <<'PYZIP'
import sys,zipfile,xml.etree.ElementTree as E
with zipfile.ZipFile(sys.argv[1]) as z:
 expected={'[Content_Types].xml','_rels/.rels','word/document.xml','word/styles.xml','word/numbering.xml','word/_rels/document.xml.rels','docProps/core.xml'}
 assert len(z.namelist())==7 and set(z.namelist())==expected
 assert z.testzip() is None
 total=0
 for member in z.namelist():
  data=z.read(member);total+=len(data);assert b'<!DOCTYPE' not in data and b'<!ENTITY' not in data;E.fromstring(data)
 assert total<=8388608
 text=''.join(E.fromstring(z.read('word/document.xml')).itertext())
 assert 'Captured' in text and 'body 😀' in text
print('PASS independent provider ZIP/XML/CRC checks')
PYZIP
