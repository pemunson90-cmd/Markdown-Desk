'use strict';
const fs=require('node:fs'),vm=require('node:vm'),assert=require('node:assert/strict');
const capture=fs.readFileSync(process.argv[2],'utf8'),restore=fs.readFileSync(process.argv[3],'utf8');
function part(ordinal,start,top,bottom){return {
  getBoundingClientRect(){return {top,bottom}},getAttribute(){return String(start)},
  closest(){return {getAttribute(){return String(ordinal)}}}
};}
const visible=part(72,120,-340,600),prior=part(71,0,-1600,-400),later=part(72,140,600,1800);
let context={document:{querySelectorAll(){return [prior,visible,later]}}};
assert.deepEqual(JSON.parse(vm.runInNewContext(capture,context)),{ordinal:72,part:120,y:340});
context.document.querySelectorAll=()=>[];
assert.equal(vm.runInNewContext(capture,context),'','empty viewport preserves old anchor');
let into=0,offset;
context={document:{querySelector(selector){
 assert.equal(selector,'[data-ordinal="72"] .archive-part[data-start-part="120"]');
 return {scrollIntoView(){into++}};
}},window:{scrollBy(x,y){assert.equal(x,0);offset=y}}};
assert.equal(vm.runInNewContext(restore,context),true);
assert.equal(into,1);assert.equal(offset,340);
context.document.querySelector=()=>null;
assert.equal(vm.runInNewContext(restore,context),false,'missing target never invents a location');
console.log('PASS logical archive position: pruned messages, long-message parts, empty/missing nodes and exact relative restore');
