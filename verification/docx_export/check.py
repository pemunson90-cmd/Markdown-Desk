import sys,zipfile,hashlib,xml.etree.ElementTree as E
from pathlib import Path
out=Path(sys.argv[1]);w='{http://schemas.openxmlformats.org/wordprocessingml/2006/main}';r='{http://schemas.openxmlformats.org/package/2006/relationships}'
checks=0
def check(value,msg):
 global checks
 checks+=1
 assert value,msg
for name in ['rich','fallback','caps-fallback','semantics']:
 with zipfile.ZipFile(out/(name+'.docx')) as z:
  check(len(z.namelist())==len(set(z.namelist()))==7,'fixed unique parts')
  check(z.testzip() is None,'crc')
  docs={}
  for part in z.namelist():
   data=z.read(part);check(b'<!DOCTYPE' not in data and b'<!ENTITY' not in data,'no declarations');docs[part]=E.fromstring(data)
  root=docs['word/document.xml'];text=''.join(root.itertext())
  if name=='rich':
   for token in ['Heading & <text>','bold','italic','strike','code','cell','escaped | pipe','  <xml> & code','café','שלום','中文','😀','image not included']:
    check(token in text,'content '+token)
   for prop in ['b','i','strike','numPr','tbl','tblHeader','tab','br']:
    check(root.find('.//'+w+prop) is not None,'semantic '+prop)
   check(root.find('.//'+w+'pStyle').get(w+'val')=='Heading1','heading')
   rels=docs['word/_rels/document.xml.rels'];external=[x for x in rels if x.get('TargetMode')=='External'];check(len(external)==1,'one safe hyperlink');check(external[0].get('Target')=='https://example.com/a?x=1&y=2','escaped target')
   check(docs['word/numbering.xml'].find('.//'+w+'numFmt').get(w+'val')=='decimal','real ordered numbering')
  elif name=='fallback':
   for token in ['1 | 2 | SURPLUS','**ambiguous * nested**','  - oddindent','[x](https://example.com/a(b))']:
    check(token in text,'literal fallback '+token)
   check(root.find('.//'+w+'tbl') is None,'ambiguous table literal')
  elif name=='caps-fallback':
   check('a|b' in text and 'x|y' in text and '    - deep' in text,'capped fallback all source')
  else:
   check(root.find('.//'+w+'pStyle').get(w+'val')=='Heading6','sixth heading style')
   for t in ['three','four','restart','☑ done','☐ todo','spaces  ']: check(t in text,'semantic content '+t)
   numbering=docs['word/numbering.xml']
   starts=[n.get(w+'val') for n in numbering.findall('.//'+w+'start')]
   check('3' in starts and '9' in starts,'ordered starts and restart')
   rels=docs['word/_rels/document.xml.rels'];external=[x.get('Target') for x in rels if x.get('TargetMode')=='External']
   check(external==['mailto:hello@example.com','http://example.com'],'mailto and http targets')
print('PASS independent ZIP/XML' ,checks,'checks')
for name,expected in [('rich',[('first',0,'decimal',1),('nested',1,'bullet',1),('second',0,'decimal',1)]),('semantics',[('three',0,'decimal',3),('four',0,'decimal',3),('restart',0,'decimal',9),('☑ done',0,'bullet',1),('☐ todo',0,'bullet',1)])]:
 with zipfile.ZipFile(out/(name+'.docx')) as z:
  root=E.fromstring(z.read('word/document.xml'));nums=E.fromstring(z.read('word/numbering.xml'))
  abstracts={a.get(w+'abstractNumId'):a for a in nums.findall(w+'abstractNum')}
  mapping={n.get(w+'numId'):abstracts[n.find(w+'abstractNumId').get(w+'val')] for n in nums.findall(w+'num')}
  seen=[]
  for p in root.findall('.//'+w+'p'):
   num=p.find(w+'pPr/'+w+'numPr')
   if num is None:continue
   text=''.join(p.itertext());depth=num.find(w+'ilvl').get(w+'val');numid=num.find(w+'numId').get(w+'val')
   lvl=mapping[numid].find(w+'lvl[@'+w+'ilvl="'+depth+'"]')
   check(lvl is not None,'level resolves')
   seen.append((text,int(depth),lvl.find(w+'numFmt').get(w+'val'),int(lvl.find(w+'start').get(w+'val'))))
  check(seen==expected,'numbering paragraph references/depth/type/restarts '+name)
print('PASS cross-referenced list semantics',checks,'total independent checks')
