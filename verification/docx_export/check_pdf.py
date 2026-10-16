from pathlib import Path
import sys
s=Path(sys.argv[1]).read_text()
for t in ['Heading & <text>','bold','italic','first','second','cell','escaped | pipe','<xml> & code','café','image not included']:
 assert t in s,repr(t)
assert 1<=s.count('\f')<=4
print('PASS LibreOffice PDF content and bounded pagination (11 tokens)')
