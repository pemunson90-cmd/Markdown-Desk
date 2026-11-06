#!/usr/bin/env python3
"""Check new APK's app-defined cross-dex method/field references resolve.
Avoids NoSuchMethodError from compiling archive against incorrect stub signatures.
"""
import struct,sys
from pathlib import Path
U16=lambda a,p:struct.unpack_from('<H',a,p)[0]
U32=lambda a,p:struct.unpack_from('<I',a,p)[0]
def leb(a,p):
 shift=0;num=0
 while True:
  b=a[p];p+=1;num+=(b&0x7f)<<shift
  if b<128:return num,p
  shift+=7
  if shift>35:raise Exception('ULEB overflow')
class Dex:
 def __init__(self,path):
  self.a=Path(path).read_bytes();a=self.a
  assert a[:4]==b'dex\n'
  n,off=U32(a,56),U32(a,60)
  self.strings=[]
  for i in range(n):
   at=U32(a,off+i*4);_,at=leb(a,at)
   end=a.index(b'\0',at)
   self.strings.append(a[at:end].decode('utf-8','replace'))
  n,off=U32(a,64),U32(a,68)
  self.types=[self.strings[U32(a,off+i*4)] for i in range(n)]
  n,off=U32(a,72),U32(a,76)
  self.protos=[]
  for i in range(n):
   at=off+i*12;_,return_idx,params=struct.unpack_from('<III',a,at)
   args=()
   if params:
    plen=U32(a,params);args=tuple(self.types[U16(a,params+4+j*2)] for j in range(plen))
   self.protos.append(('(' + ''.join(args) + ')' + self.types[return_idx]))
  n,off=U32(a,80),U32(a,84)
  self.fields=[]
  for i in range(n):
   cls,typ,name=struct.unpack_from('<HHI',a,off+i*8)
   self.fields.append((self.types[cls],self.strings[name],self.types[typ]))
  n,off=U32(a,88),U32(a,92)
  self.methods=[]
  for i in range(n):
   cls,proto,name=struct.unpack_from('<HHI',a,off+i*8)
   self.methods.append((self.types[cls],self.strings[name],self.protos[proto]))
  self.owners=set();self.def_methods=set();self.def_fields=set()
  n,off=U32(a,96),U32(a,100)
  for i in range(n):
   clidx,_,_,_,_,_,coff,_=struct.unpack_from('<IIIIIIII',a,off+i*32)
   self.owners.add(self.types[clidx]);
   if coff==0:continue
   counts=[]
   for _ in range(4):c,coff=leb(a,coff);counts.append(c)
   for count in (counts[0],counts[1]):
    last=0
    for j in range(count):
     diff,coff=leb(a,coff);last+=diff
     _,coff=leb(a,coff)
     self.def_fields.add(self.fields[last])
   for count in (counts[2],counts[3]):
    last=0
    for j in range(count):
     diff,coff=leb(a,coff);last+=diff
     _,coff=leb(a,coff);_,coff=leb(a,coff)
     self.def_methods.add(self.methods[last])

def main(old,new):
 a,b=Dex(old),Dex(new)
 defined_methods=a.def_methods|b.def_methods
 defined_fields=a.def_fields|b.def_fields
 external_owners=a.owners-b.owners
 import_refs=[m for m in b.methods if m[0] in external_owners and m[0].startswith('Lcom/example/markdowndesk/')]
 # These receivers inherit methods from framework/JDK superclasses. Their
 # descriptor therefore legitimately names the app-owned subtype in bytecode.
 inherited_preview={'evaluateJavascript','getScrollY','getSettings','loadDataWithBaseURL',
                    'setBackgroundColor','setOnScrollChangeListener','setWebViewClient'}
 def known_inherited(m):
  owner,name,_sig=m
  return (owner=='Lcom/example/markdowndesk/PreviewWebView;' and name in inherited_preview) or (
          owner=='Lcom/example/markdowndesk/ReaderTypography$Family;' and name=='name')
 missing_methods=[m for m in import_refs if m not in defined_methods and not known_inherited(m)]
 import_fields=[f for f in b.fields if f[0] in external_owners and f[0].startswith('Lcom/example/markdowndesk/')]
 missing_fields=[f for f in import_fields if f not in defined_fields]
 print('Original owners',len(a.owners),'new owners',len(b.owners))
 print('App-local external method refs',len(import_refs),'fields',len(import_fields))
 print('Unresolved methods:',len(missing_methods),'fields:',len(missing_fields))
 for thing in (missing_methods+missing_fields)[:45]:print('  ',thing)
 if missing_methods or missing_fields:sys.exit(1)
 print('PASS: all compiled app-local method and field references resolve')
if __name__=='__main__':main(*sys.argv[1:])