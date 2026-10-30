// Render a deck (or preview) at 1920×1080 and save one PNG per slide.
// Usage: node tools/screenshot.js <html> <outDir> [--audit]
// --audit prints overflow / overlap findings per slide (elements marked data-fit or .card).
const { chromium } = require('playwright');
const path = require('path');
const fs = require('fs');

(async () => {
  const [html, outDir] = process.argv.slice(2);
  const audit = process.argv.includes('--audit');
  fs.mkdirSync(outDir, { recursive: true });
  const browser = await chromium.launch();
  const page = await browser.newPage({ viewport: { width: 1920, height: 1080 }, deviceScaleFactor: 1 });
  await page.goto('file://' + path.resolve(html));
  await page.evaluate(() => document.fonts.ready);
  await page.mouse.move(960, 540); // keep the edit hotzone (top-left) from showing
  const count = await page.evaluate(() => document.querySelectorAll('.slide').length);
  let problems = 0;
  for (let i = 0; i < count; i++) {
    await page.evaluate((n) => window.deck ? window.deck.go(n) : null, i);
    await page.waitForTimeout(2600); // let entrance animations finish
    const file = path.join(outDir, `slide-${String(i + 1).padStart(2, '0')}.png`);
    await page.screenshot({ path: file });
    if (audit) {
      const issues = await page.evaluate((n) => {
        const slide = document.querySelectorAll('.slide')[n];
        const out = [];
        const sr = slide.getBoundingClientRect();
        const scale = sr.width / 1920;
        // 1) text boxes whose content is taller/wider than the box
        slide.querySelectorAll('*').forEach((el) => {
          const cs = getComputedStyle(el);
          if (cs.visibility === 'hidden' || cs.display === 'none') return;
          if (['svg', 'path', 'g', 'line', 'text', 'tspan', 'rect', 'circle', 'stop', 'defs', 'linearGradient', 'polyline', 'polygon'].includes(el.tagName.toLowerCase())) return;
          if (el.scrollHeight > el.clientHeight + 2 && ['hidden', 'clip', 'auto', 'scroll'].includes(cs.overflowY) && el.clientHeight > 0)
            out.push(`clipped-y ${el.className || el.tagName} (${el.scrollHeight}>${el.clientHeight})`);
          if (el.scrollWidth > el.clientWidth + 2 && ['hidden', 'clip', 'auto', 'scroll'].includes(cs.overflowX) && el.clientWidth > 0 && el !== slide)
            out.push(`clipped-x ${el.className || el.tagName} (${el.scrollWidth}>${el.clientWidth})`);
          // 2) anything with text leaving the slide
          if (el.childElementCount === 0 && el.textContent.trim()) {
            const r = el.getBoundingClientRect();
            if (r.right > sr.right + 1 || r.bottom > sr.bottom + 1 || r.left < sr.left - 1 || r.top < sr.top - 1)
              out.push(`off-slide "${el.textContent.trim().slice(0, 30)}"`);
          }
        });
        // 3) sibling cards that overlap each other
        const cards = [...slide.querySelectorAll('[data-box]')];
        for (let a = 0; a < cards.length; a++) for (let b = a + 1; b < cards.length; b++) {
          const r1 = cards[a].getBoundingClientRect(), r2 = cards[b].getBoundingClientRect();
          const ox = Math.min(r1.right, r2.right) - Math.max(r1.left, r2.left);
          const oy = Math.min(r1.bottom, r2.bottom) - Math.max(r1.top, r2.top);
          if (ox > 2 && oy > 2) out.push(`overlap [${cards[a].dataset.box}] × [${cards[b].dataset.box}]`);
        }
        return out;
      }, i);
      if (issues.length) { problems += issues.length; console.log(`slide ${i + 1}:`, issues.join(' | ')); }
    }
  }
  console.log(`${count} slide(s) → ${outDir}${audit ? `, ${problems} issue(s)` : ''}`);
  await browser.close();
})();
