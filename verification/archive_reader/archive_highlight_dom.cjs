'use strict';
const fs=require('node:fs'),vm=require('node:vm'),assert=require('node:assert/strict');
const script=fs.readFileSync(process.argv[2],'utf8');
function fixture(text){
 const marked=[];
 const node={nodeType:3,nodeValue:text,length:text.length,parentElement:{}};
 const content={};
 const section={getAttribute(key){return key==='data-message-id'?'message-7':null},querySelector(selector){return selector==='.archive-content'?content:null}};
 const document={
   querySelectorAll(selector){if(selector==='mark.md-archive-highlight')return [];
     if(selector.includes('section[data-message-id]'))return [section];throw Error(selector)},
   createTreeWalker(root){assert.equal(root,content);let i=0;return {nextNode(){return i++===0?node:null}}},
   createRange(){return {setStart(n,offset){this.begin=offset},setEnd(n,offset){this.end=offset},
     extractContents(){return {}},insertNode(label){marked.push({begin:this.begin,end:this.end,label})}}},
   createElement(tag){return {className:'',attrs:{},setAttribute(k,v){this.attrs[k]=v},appendChild(){}}},
   addEventListener(){},
 };
 const window={};const ctx={window,document,NodeFilter:{SHOW_TEXT:4}};
 return {ctx,window,marked};
}
function run(text,rows){const f=fixture(text);vm.runInNewContext(script,f.ctx,{timeout:2000});f.window.mdArchiveAddHighlights(rows);return f.marked}
const base={id:'n1',message:'message-7',quote:'target',prefix:'alpha ',suffix:' omega'};
{
 let r=run('alpha target omega',[base]);
 assert.equal(r.length,1,'valid contextual range decorated');
 assert.equal(r[0].begin,6);
 assert.match(r[0].label.className,/md-archive-tone-violet/,'legacy missing tone shows violet');
}
{
 let r=run('changed target omega',[base]);
 assert.equal(r.length,0,'unique quote with changed preceding text must remain unresolved');
}
{
 let r=run('alpha target changed',[base]);
 assert.equal(r.length,0,'unique quote with changed following text must remain unresolved');
}
{
 let r=run('alpha target omega alpha target omega',[base]);
 assert.equal(r.length,0,'identical valid context repeated remains ambiguous');
}
{
 let r=run('alpha target omega alpha target!',[base]);
 assert.equal(r.length,1,'distinct second occurrence without matching context may be ignored');
}
{
 const r=run('alpha target omega',[{...base,tone:'rose'}]);
 assert.equal(r.length,1,'typed tone still preserves verified quote');
 assert.match(r[0].label.className,/md-archive-tone-rose/,'rose selected');
}
{
 const r=run('alpha target omega',[{...base,tone:'mint'}]);
 assert.match(r[0].label.className,/md-archive-tone-mint/,'mint selected');
}
{
 const r=run('alpha target omega',[{...base,tone:'evil"><script>'}]);
 assert.equal(r.length,1,'unknown tone must not delete verified annotation');
 assert.match(r[0].label.className,/md-archive-tone-violet/,'unsafe tone fails closed');
 assert.ok(!r[0].label.className.includes('script'),'no user CSS/HTML inserted');
}
console.log('PASS archive exact emitted highlight DOM: unique contextual anchor, changed context, repeated ambiguity');
