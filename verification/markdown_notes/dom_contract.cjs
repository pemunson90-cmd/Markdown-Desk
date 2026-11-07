#!/usr/bin/env node
"use strict";
// Test the EXACT Java-generated WebView functions without depending on jsdom.
const fs = require("node:fs");
const vm = require("node:vm");
const assert = require("node:assert/strict");
if(process.argv.length!==4)throw Error("Need emitted capture and boot JS paths");
const capture=fs.readFileSync(process.argv[2],"utf8"),boot=fs.readFileSync(process.argv[3],"utf8");
function fixture(chunks) {
  const nodes=chunks.map(([text,excluded=false])=>({
    nodeType:3,nodeValue:text,length:text.length,
    parentElement:{closest(){return excluded?{}:null},scrollIntoView(){}}}));
  const ranges=[],highlighted=[];
  const body={textContent:nodes.map(n=>n.nodeValue).join(""),contains(){return true}};
  const document={body,head:{appendChild(){}},
    createElement(){return {textContent:""}},
    createTreeWalker(){let i=0;return {nextNode(){return nodes[i++]||null}}},
    createRange(){let a=null,z=null;return {
      setStart(n,at){a={n,at};this.startContainer=n;this.startOffset=at},
      setEnd(n,at){z={n,at};this.endContainer=n;this.endOffset=at},
      get positions(){return {a,z}}
    }}};
  const selection={rangeCount:0,getRangeAt(){return this.r}};
  const window={getSelection(){return selection}};
  const ctx={document,window,NodeFilter:{SHOW_TEXT:4},CSS:{highlights:{set(key,value){highlighted.push({key,value})}}},Highlight:function(...items){this.items=items}};
  window.CSS=ctx.CSS;window.Highlight=ctx.Highlight;
  return {nodes,selection,ctx,window,highlighted};
}
const run=(script,ctx)=>vm.runInNewContext(script,ctx,{timeout:1000});
{
  const env=fixture([["alpha target omega"],["target",true]]);
  run(boot,env.ctx);
  env.window.mdNoteDecorate([{id:"one",quote:"target",prefix:"",suffix:"",start:6}]);
  assert.equal(env.window.mdNoteJump("one"),true,"hidden script text must not count as a visible duplicate");
  assert.equal(env.highlighted.find(x=>x.key==='markdown-notes-violet').value.items.length,1,"one visible highlight");
}
{
  const env=fixture([["alpha target omega target"]]);
  run(boot,env.ctx);
  env.window.mdNoteDecorate([{id:"duplicate",quote:"target",prefix:"",suffix:"",start:6}]);
  assert.equal(env.window.mdNoteJump("duplicate"),false,"old offset cannot override duplicate ambiguity");
  assert.equal(env.highlighted.find(x=>x.key==='markdown-notes-violet').value.items.length,0,"ambiguous highlights omitted");
}
{
  const env=fixture([["alpha target omega alpha target omega"]]);
  run(boot,env.ctx);
  env.window.mdNoteDecorate([{id:"duplicate-with-context",quote:"target",prefix:"alpha ",suffix:" omega",start:6}]);
  assert.equal(env.window.mdNoteJump("duplicate-with-context"),false,"identical context must fail closed");
}
{
  const env=fixture([["target",true],["alpha target omega"]]);
  const node=env.nodes[1];
  env.selection.rangeCount=1;
  env.selection.r={collapsed:false,commonAncestorContainer:node,startContainer:node,startOffset:6,
    endContainer:node,endOffset:12,toString(){return "target"}};
  const selected=run(capture,env.ctx);
  assert.equal(selected.quote,"target");
  assert.equal(selected.start,6,"hidden preceding script text must not change source offset");
  assert.equal(selected.end,12);
  assert.equal(selected.prefix,"alpha ");
  assert.equal(selected.suffix," omega");
}
{
  const env=fixture([["alpha different omega"]]);
  run(boot,env.ctx);
  env.window.mdNoteDecorate([{id:"changed",quote:"target",prefix:"alpha ",suffix:" omega",start:6}]);
  assert.equal(env.window.mdNoteJump("changed"),false,"changed source must never silently reanchor");
}
{
  const env=fixture([["rose gap mint"]]);
  run(boot,env.ctx);
  env.window.mdNoteDecorate([
    {id:"rose",quote:"rose",prefix:"",suffix:"",tone:"rose"},
    {id:"mint",quote:"mint",prefix:"",suffix:"",tone:"mint"}]);
  const latest={};
  for(const h of env.highlighted) latest[h.key]=h.value;
  const palette=["violet","rose","mint","amber","blue","slate"];
  for(const tone of palette) assert.ok(latest["markdown-notes-"+tone], "fixed color exists: "+tone);
  assert.equal(latest["markdown-notes-rose"].items.length,1,"rose range");
  assert.equal(latest["markdown-notes-mint"].items.length,1,"mint range");
  assert.equal(latest["markdown-notes-violet"].items.length,0,"other colors do not inherit marks");
  assert.equal(env.window.mdNoteJump("mint"),true,"colored note retains exact jump");
}
{
  const env=fixture([["legacy annotation"]]);
  run(boot,env.ctx);
  env.window.mdNoteDecorate([{id:"old",quote:"annotation",prefix:"legacy ",suffix:""}]);
  const violet=env.highlighted.find(x=>x.key==="markdown-notes-violet");
  assert.equal(violet.value.items.length,1,"legacy record without a tone remains visible");
}
console.log("PASS exact emitted Markdown selection/highlight JS: hidden text, duplicate quotes, context and changed passages");
