// Operator-owned behavioral check of the page's real sync scripts (appended after the
// CASES prelude written by FootnoteSyncDump). Contract: editor->preview sync
// mdScrollToSource(off) must land on the block whose data-source-offset is the greatest
// value <= off (first in DOM order on ties), or on the first block when none qualifies,
// regardless of DOM order. Footnote lists render after the body but carry their
// definitions' offsets, so DOM order is not offset order when definitions are early.
let checks = 0;
function fail(msg) { console.error('FAIL ' + msg); process.exit(1); }
for (const c of CASES) {
  if (!c.offsets.length) fail('doc ' + c.doc + ' has no blocks');
  const blocks = c.offsets.map((o, i) => ({ dataset: { sourceOffset: String(o) }, offsetTop: 1000 + 100 * i, i,
    closest() { return null; } }));
  let scrolled = null;
  const window = { scrollY: 0, scrollTo(x, y) { scrolled = y; } };
  const document = { querySelectorAll(sel) {
      return sel === '[data-source-offset]' ? blocks : []; },
    addEventListener() {} };
  const location = {};
  new Function('window', 'document', 'location', c.script)(window, document, location);
  if (typeof window.mdScrollToSource !== 'function') fail('doc ' + c.doc + ' lacks mdScrollToSource');
  for (let off = 0; off <= c.length; off++) {
    let want = null;
    for (const b of blocks) {
      const n = Number(b.dataset.sourceOffset);
      if (n <= off && (want === null || n > Number(want.dataset.sourceOffset))) want = b;
    }
    if (want === null) want = blocks[0];
    scrolled = null;
    window.mdScrollToSource(off);
    checks++;
    if (scrolled !== Math.max(0, want.offsetTop - 16))
      fail('doc ' + c.doc + ' offset ' + off + ' scrolled to y=' + scrolled + ' expected block ' + want.i +
           ' (source offset ' + want.dataset.sourceOffset + ', DOM offsets ' + c.offsets.join(',') + ')');
  }
  // viewport -> source must still report the block at the top of the viewport
  for (const b of blocks) {
    window.scrollY = b.offsetTop - 24;
    checks++;
    if (window.mdSourceAtViewport() !== Number(b.dataset.sourceOffset))
      fail('doc ' + c.doc + ' viewport at block ' + b.i + ' reported ' + window.mdSourceAtViewport());
  }
}
console.log('FootnoteSync: ' + checks + ' checks PASS');
