'use strict';
const fs=require('node:fs'),vm=require('node:vm'),assert=require('node:assert/strict');
const script=fs.readFileSync(process.argv[2],'utf8');
function fixture(texts,{message='message-7',first=3,last=7,css=true}={}){
 const nodes=texts.map(nodeValue=>({nodeValue,parentElement:{closest(){return null}}}));
 const piece={getAttribute(k){return k==='data-start-part'?String(first):String(last)}};
 const section={getAttribute(){return message},querySelectorAll(){return [piece]}};
 const highlights=[],selected=[],scrolls=[];
 const document={querySelectorAll(){return [section]},
  createTreeWalker(root){assert.equal(root,piece);let i=0;return {nextNode(){return nodes[i++]||null}}},
  createRange(){return {setStart(node,offset){this.start=[nodes.indexOf(node),offset]},
   setEnd(node,offset){this.end=[nodes.indexOf(node),offset]},getBoundingClientRect(){return {top:300}}}},
  getElementById(){return null},createElement(){return {}},head:{appendChild(){}}};
 const CSS=css?{highlights:{set(name,range){highlights.push({name,range})}}}:null;
 const selection={removeAllRanges(){selected.length=0},addRange(r){selected.push(r)}};
 function Highlight(range){this.range=range}
 const window={CSS,Highlight,getSelection(){return selection},scrollBy(x,y){scrolls.push([x,y])}};
 const result=vm.runInNewContext(script,{window,document,CSS,Highlight,NodeFilter:{SHOW_TEXT:4}},{timeout:1000});
 return {result,highlights,selected,scrolls};
}
{
 const f=fixture(['prefix foo-','bar café 😀 suffix']);
 assert.equal(f.result,true,'actual FTS span with punctuation, accents and astral Unicode resolves');
 assert.deepEqual(f.highlights[0].range.range.start,[0,7]);
 assert.deepEqual(f.highlights[0].range.range.end,[1,11]);
 assert.deepEqual(f.scrolls,[[0,220]],'scrolls to matched range rather than message heading');
}
{
 const f=fixture(['foo-bar café 😀'],{css:false});
 assert.equal(f.result,true);assert.equal(f.selected.length,1,'native selection fallback');
}
assert.equal(fixture(['foo bar cafe 😀']).result,false,'raw normalized query is not mistaken for actual quote');
assert.equal(fixture(['foo-bar café 😀 foo-bar café 😀']).result,false,'ambiguous occurrence refuses');
assert.equal(fixture(['foo-bar café 😀'],{message:'other-message'}).result,false,'wrong message refuses');
assert.equal(fixture(['foo-bar café 😀'],{first:8,last:12}).result,false,'wrong segment refuses');
console.log('PASS actual emitted search-hit DOM: exact normalized-FTS source span, Unicode range, scroll, fallback and wrong/ambiguous refusal');
