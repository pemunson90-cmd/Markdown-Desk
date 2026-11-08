'use strict';
const fs=require('node:fs'),vm=require('node:vm'),assert=require('node:assert/strict');
const script=fs.readFileSync(process.argv[2],'utf8');
function fixture(chunks,beginIndex,beginOffset,endIndex,endOffset,quoteOverride){
 const section={getAttribute(name){return name==='data-message-id'?'message-7':null},querySelector(name){return name==='.archive-content'?content:null}};
 const otherSection={getAttribute(){return 'wrong-message'},querySelector(){return null}};
 const nodes=chunks.map(([value,exclude=false,foreign=false])=>({nodeType:3,nodeValue:value,length:value.length,
   parentElement:{closest(selector){
     if(selector==='[data-message-id]')return foreign?otherSection:section;
     if(selector==='script,style,noscript,template')return exclude?{}:null;
     if(selector==='.archive-part')return {getAttribute(){return '120'}};
     return null;
   }}}));
 const content={contains(node){return nodes.includes(node)}};
 const range={collapsed:false,startContainer:nodes[beginIndex],startOffset:beginOffset,
     endContainer:nodes[endIndex],endOffset:endOffset,
     toString(){return quoteOverride===undefined?
       (beginIndex===endIndex?nodes[beginIndex].nodeValue.slice(beginOffset,endOffset):''):
       quoteOverride}};
 const selection={rangeCount:1,getRangeAt(){return range}};
 const document={createTreeWalker(target){assert.equal(target,content);let i=0;return {nextNode(){return nodes[i++]||null}}}};
 const window={getSelection(){return selection}};
 return {ctx:{document,window,NodeFilter:{SHOW_TEXT:4}},nodes,range};
}
function run(env){return vm.runInNewContext(script,env.ctx,{timeout:3000})}
{
 const env=fixture([['first target then target again']],0,18,0,24,'target');
 const result=JSON.parse(run(env));
 assert.equal(result.quote,'target');
 assert.equal(result.sourcePart,120,'long-message segment preserved');
 assert.equal(result.start,18,'second occurrence, not first occurrence');
 assert.equal(result.end,24);
 assert.equal(result.prefix,'first target then ');
 assert.equal(result.suffix,' again');
}
{
 const env=fixture([['script target',true],['begin target finish']],1,6,1,12,'target');
 assert.equal(JSON.parse(run(env)).start,6,'hidden text must not affect ordinal');
}
{
 const env=fixture([['text target'],[' in another message',false,true]],0,5,1,3,'target in');
 assert.equal(run(env),'','cross-message selection must fail closed');
}
{
 const env=fixture([['alpha target omega']],0,6,0,12,'changed');
 assert.equal(run(env),'','range/text mismatch must fail closed');
}
{
 const env=fixture([['alpha target omega']],0,0,0,0,'');
 assert.equal(run(env),'','empty selection must fail closed');
}
{
 const env=fixture([['before 😀 after']],0,7,0,9,'😀');
 const result=JSON.parse(run(env));
 assert.equal(result.start,7,'UTF-16 code-unit offsets');
 assert.equal(result.end,9);
}
console.log('PASS archive exact emitted selection DOM: second duplicate, hidden text, cross-message, changed, empty and Unicode');
