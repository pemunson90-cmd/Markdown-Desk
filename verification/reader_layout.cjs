// Host Chromium proxy coverage, not Android WebView or physical Fold validation.
const {chromium}=require('playwright');
const fs=require('fs'); const path=require('path');
(async()=>{const browser=await chromium.launch({headless:true,executablePath:process.env.CHROMIUM_PATH||'/usr/bin/chromium',args:['--no-sandbox']});const page=await browser.newPage();let n=0;
const viewports=[{width:344,height:882},{width:882,height:344},{width:690,height:829},{width:829,height:690}];
for(const file of fs.readdirSync(path.join(__dirname,'build','host','rendered'))){if(!file.endsWith('.html'))continue;for(const viewport of viewports){await page.setViewportSize(viewport);await page.setContent(fs.readFileSync(path.join(__dirname,'build','host','rendered',file),'utf8'));const r=await page.evaluate(()=>({width:innerWidth,scroll:document.documentElement.scrollWidth,body:document.body.getBoundingClientRect().width,h1:getComputedStyle(document.querySelector('h1')).fontSize,codeScroll:document.querySelector('pre').scrollWidth,codeClient:document.querySelector('pre').clientWidth}));if(r.scroll>r.width+1||r.body>r.width+1)throw Error(file+' '+JSON.stringify({viewport,r}));n++;}}
console.log('PASS '+n+' Chromium reader layout cases: 9 themes x 3 text sizes x 4 representative viewports, no page-level horizontal overflow');await browser.close();})().catch(e=>{console.error(e);process.exit(1)});
