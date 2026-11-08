'use strict';
const fs=require('node:fs'),vm=require('node:vm'),assert=require('node:assert/strict');
const restore=fs.readFileSync(process.argv[2],'utf8');
function fixture(chunks,id='message-7'){
 const nodes=chunks.map(nodeValue=>({nodeValue,parentElement:{closest(){return null}}}));
 let selected=null,removed=0;
 const content={};const section={getAttribute(){return id},querySelector(){return content}};
 const document={querySelectorAll(){return [section]},createTreeWalker(){let i=0;return {nextNode(){return nodes[i++]||null}}},
 createRange(){return {setStart(node,offset){this.start=[nodes.indexOf(node),offset]},setEnd(node,offset){this.end=[nodes.indexOf(node),offset]}}}};
 const window={getSelection(){return {removeAllRanges(){removed++},addRange(range){selected=range}}}};
 return {ctx:{document,window,NodeFilter:{SHOW_TEXT:4}},get selected(){return selected},get removed(){return removed}};
}
let env=fixture(['before tar','get after']);
assert.equal(vm.runInNewContext(restore,env.ctx),true);
assert.deepEqual(env.selected.start,[0,7]);assert.deepEqual(env.selected.end,[1,3]);assert.equal(env.removed,1);
for(const [chunks,id] of [
 [['before target after before target after'],'message-7'],
 [['changed target after'],'message-7'],
 [['before target changed'],'message-7'],
 [['before target after'],'wrong-source']]){
 env=fixture(chunks,id);assert.equal(vm.runInNewContext(restore,env.ctx),false);assert.equal(env.removed,0);
}
console.log('PASS selection recreation: exact multi-node range; ambiguous, changed context and wrong message refused');
