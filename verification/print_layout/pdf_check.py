#!/usr/bin/env python3
"""Real Chromium PDF structural goldens. Android WebView acceptance is separate."""
import asyncio, base64, json, os, re, shutil, subprocess, sys, tempfile, time
from pathlib import Path
from xml.etree import ElementTree as ET
import websockets
from PIL import Image

OUT = Path(sys.argv[1]).resolve()
for tool in ('chromium', 'pdftotext', 'pdfinfo', 'pdftoppm'):
    if not shutil.which(tool):
        raise SystemExit('Required installed tool missing: ' + tool)

class CDP:
    def __init__(self, socket): self.socket, self.sequence = socket, 0
    async def call(self, method, params=None, session=None):
        self.sequence += 1
        ident = self.sequence
        request = dict(id=ident, method=method, params=params or {})
        if session: request['sessionId'] = session
        await self.socket.send(json.dumps(request))
        while True:
            message = json.loads(await asyncio.wait_for(self.socket.recv(), 45))
            if message.get('id') == ident:
                if 'error' in message: raise AssertionError(message['error'])
                return message.get('result', {})

async def main():
    pdfdir = OUT / 'pdf'; pdfdir.mkdir(exist_ok=True)
    observations = {'chromium': subprocess.check_output(['chromium', '--version'], text=True).strip(), 'cases': {}}
    with tempfile.TemporaryDirectory(prefix='md-print-browser-',ignore_cleanup_errors=True) as profile, open(OUT / 'chromium.log', 'w') as log:
        browser = subprocess.Popen(['chromium', '--headless', '--no-sandbox', '--disable-gpu', '--disable-dev-shm-usage',
            '--disable-background-networking', '--disable-component-update', '--disable-extensions', '--disable-sync',
            '--no-first-run', '--remote-debugging-port=0', '--user-data-dir=' + profile, 'about:blank'], stdout=log, stderr=log)
        try:
            endpoint = Path(profile) / 'DevToolsActivePort'
            deadline = time.monotonic() + 15
            while not endpoint.exists():
                assert browser.poll() is None, 'Chromium exited before debugging readiness'
                assert time.monotonic() < deadline, 'Chromium startup timeout'
                await asyncio.sleep(.05)
            port, browser_path = endpoint.read_text().splitlines()[:2]
            async with websockets.connect('ws://127.0.0.1:' + port + browser_path, max_size=16 << 20) as socket:
                cdp = CDP(socket)
                target = (await cdp.call('Target.createTarget', {'url': 'about:blank'}))['targetId']
                session = (await cdp.call('Target.attachToTarget', {'targetId': target, 'flatten': True}))['sessionId']
                case_deadline = time.monotonic() + 45
                async def call(method, params=None): return await asyncio.wait_for(cdp.call(method, params, session),max(.01,case_deadline-time.monotonic()))
                async def js(expression): return (await call('Runtime.evaluate', {'expression': expression, 'returnByValue': True}))['result'].get('value')
                await call('Page.enable'); await call('Network.enable')
                await call('Network.setBlockedURLs', {'urls': ['http://*', 'https://*']})
                await call('Emulation.setDeviceMetricsOverride', {'width':794, 'height':1123, 'deviceScaleFactor':1, 'mobile':False})
                names=['code','headings','table','tall-row']+['theme'+str(i) for i in range(9)]
                cases = [(name, OUT/'html'/(name+'.html'), 'A4') for name in names]
                cases += [(name+'-letter', OUT/'html'/(name+'.html'), 'Letter') for name in ('table', 'code')]
                theme_text, theme_pages = None, None
                for name, source, paper in cases:
                    case_deadline=time.monotonic()+45
                    assert source.stat().st_size <= 128 << 10, 'fixture exceeds resource limit'
                    await call('Emulation.setEmulatedMedia', {'media':'screen'})
                    await call('Page.navigate', {'url':'about:blank'})
                    frame = (await call('Page.getFrameTree'))['frameTree']['frame']['id']
                    await call('Page.setDocumentContent', {'frameId':frame,'html':source.read_text()})
                    ready_deadline = time.monotonic() + 10
                    while not await js("document.readyState==='complete'&&document.documentElement.getAttribute('data-md-preview')==='12345678-1234-1234-1234-123456789abc'"):
                        assert time.monotonic() < ready_deadline, ('fixture readiness timeout', await js('({url:location.href,html:document.documentElement.outerHTML.slice(0,500)})'))
                        await asyncio.sleep(.03)
                    screen = await js("({background:getComputedStyle(document.body).backgroundColor,padding:getComputedStyle(document.body).paddingTop,white:document.querySelector('td')?getComputedStyle(document.querySelector('td')).whiteSpace:null})")
                    assert screen['padding'] != '0px', 'print override leaked into screen'
                    if name.startswith('table'): assert screen['white'] == 'nowrap'
                    assert await js("window.untrustedPrint===undefined"), 'user script executed'
                    await call('Emulation.setEmulatedMedia', {'media':'print'})
                    styles = await js("({background:getComputedStyle(document.body).backgroundColor,color:getComputedStyle(document.body).color,padding:getComputedStyle(document.body).paddingTop,white:document.querySelector('td')?getComputedStyle(document.querySelector('td')).whiteSpace:null})")
                    assert styles['background']=='rgb(255, 255, 255)' and styles['color']=='rgb(17, 17, 17)' and styles['padding']=='0px', styles
                    if name.startswith('table'): assert styles['white']=='normal'
                    params = {'displayHeaderFooter':False,'printBackground':True,'preferCSSPageSize':False,
                              'paperWidth':8.5 if paper=='Letter' else 210/25.4,'paperHeight':11 if paper=='Letter' else 297/25.4,
                              'marginTop':0,'marginBottom':0,'marginLeft':0,'marginRight':0}
                    pdf = pdfdir/(name+'.pdf')
                    encoded = (await call('Page.printToPDF', params))['data']
                    assert len(encoded) <= (8 << 20)*4//3+4, 'PDF exceeds resource limit'
                    pdf.write_bytes(base64.b64decode(encoded))
                    subprocess.run(['pdftotext','-layout',str(pdf),str(pdf.with_suffix('.txt'))],check=True,timeout=15)
                    subprocess.run(['pdftotext','-bbox-layout',str(pdf),str(pdf.with_suffix('.bbox.html'))],check=True,timeout=15)
                    text = pdf.with_suffix('.txt').read_text()
                    pages = text.split('\f')
                    if not pages[-1].strip(): pages.pop()
                    assert 1 <= len(pages) <= 12, (name,len(pages))
                    ns = {'x':'http://www.w3.org/1999/xhtml'}
                    tree = ET.parse(pdf.with_suffix('.bbox.html'))
                    for page in tree.findall('.//x:page',ns):
                        width,height=float(page.attrib['width']),float(page.attrib['height'])
                        for word in page.findall('.//x:word',ns):
                            assert float(word.attrib['xMin'])>=25 and float(word.attrib['xMax'])<=width-25, (name,word.text,word.attrib,width)
                            assert float(word.attrib['yMin'])>=25 and float(word.attrib['yMax'])<=height-25, (name,word.text,word.attrib,height)
                    compact = re.sub(r'\s+','',text)
                    if name=='headings':
                        assert len(pages)>1
                        last=-1
                        for i in range(48):
                            for marker in (f'Paragraph{i:03}',f'Heading{i:03}',f'Following{i:03}'):
                                assert compact.count(marker)==1, (name,marker)
                                offset=compact.index(marker);assert offset>last;last=offset
                            assert any(f'Heading{i:03}' in p and f'Following{i:03}' in p for p in pages), ('orphan heading',i)
                    elif name.startswith('table'):
                        assert len(pages)>1
                        for i in range(70):
                            for marker in (f'Row{i:03}',f'Right{i:03}'):
                                assert compact.count(marker)==1,(name,marker)
                        payload=re.sub(r'Row\d{3}|Right\d{3}|RepeatHeader|RightHeader|Pagedtable','',compact)
                        assert payload=='WideCell'*(9*70), 'table cell characters clipped'
                        assert text.count('RepeatHeader')==len(pages), ('header repetition',name,len(pages),text.count('RepeatHeader'))
                    elif name=='tall-row':
                        assert len(pages)>1
                        for i in range(180): assert compact.count(f'Tall{i:03}')==1,(name,i)
                        assert compact.count('SideEnd')==1
                    elif name.startswith('code'):
                        assert len(pages)>1
                        for i in range(120): assert compact.count(f'CodeLine{i:03}')==1,(name,i)
                        for marker in ('WideStart_', '_WideEnd', 'CodeAfter'): assert compact.count(marker)==1,(name,marker)
                        assert 'WideStart_'+'x'*300+'_WideEnd' in compact, 'long code characters clipped'
                    else:
                        for marker in ('café','Ω','Ж','UnicodeEnd','OrderedFirst','OrderedSecond','NestedLeaf','TaskDone','TaskOpen','ImageAlt','remoteimageblocked','SafeLink','QuotedText','InlineCode','<script>window.untrustedPrint=true</script>'):
                            assert marker in compact,(name,marker)
                        normalized = re.sub(r'\s+',' ',text).strip()
                        if theme_text is None: theme_text,theme_pages=normalized,len(pages)
                        else: assert normalized==theme_text and len(pages)==theme_pages,'palette changes PDF semantics/pagination'
                    observations['cases'][name]={'paper':paper,'pages':len(pages),'bytes':pdf.stat().st_size,'screen':screen,'print':styles}
                    print('PASS real PDF',name,paper,len(pages),'pages; content/order/bounds')
                await cdp.call('Browser.close')
        finally:
            if browser.poll() is None:
                browser.terminate()
                try: browser.wait(timeout=5)
                except subprocess.TimeoutExpired: browser.kill();browser.wait(timeout=5)
    # Raster evidence retained for actual visual inspection; no PDF byte hashes.
    for name,page in [('table',1),('table',2),('tall-row',2),('code',1),('code',3),('code',4),('theme0',1),('theme1',1)]:
        prefix=pdfdir/(name+'-page'+str(page))
        subprocess.run(['pdftoppm','-f',str(page),'-l',str(page),'-singlefile','-scale-to','1100','-png',str(pdfdir/(name+'.pdf')),str(prefix)],check=True,timeout=20)
        if name.startswith('theme'):
            image=Image.open(str(prefix)+'.png').convert('RGB')
            assert image.getpixel((5,5))==(255,255,255),'paper raster not white'
    (OUT/'observations.json').write_text(json.dumps(observations,indent=2)+'\n')
    print('PASS print PDF structural goldens;15 PDFs,8 page rasters; Android gate NOT RUN')

asyncio.run(asyncio.wait_for(main(),180))
