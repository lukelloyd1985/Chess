// Screenshots each generated HTML page at its exact target size.
//   node src/generate.py is NOT needed here - run `python3 src/generate.py` first to (re)write the HTML.
let chromium;
try { ({ chromium } = require('playwright')); } catch (e) { ({ chromium } = require('playwright-core')); }
const path = require('path');

const jobs = [
  { file: 'icon.html', out: 'icon-512.png', width: 512, height: 512 },
  { file: 'feature-graphic.html', out: 'feature-graphic-1024x500.png', width: 1024, height: 500 },
  { file: 'screenshot-signin.html', out: 'screenshot-1-sign-in.png', width: 1080, height: 1920 },
  { file: 'screenshot-game.html', out: 'screenshot-2-game.png', width: 1080, height: 1920 },
  { file: 'screenshot-analysis.html', out: 'screenshot-3-analysis.png', width: 1080, height: 1920 },
  { file: 'screenshot-appearance.html', out: 'screenshot-4-appearance.png', width: 1080, height: 1920 },
];

const outDir = path.resolve(__dirname, '..');

(async () => {
  const browser = await chromium.launch({
    headless: true,
    // Optional: point at a specific Chromium (e.g. CHROMIUM_PATH=/opt/pw-browsers/chromium-1194/chrome-linux/chrome).
    executablePath: process.env.CHROMIUM_PATH || undefined,
  });
  for (const job of jobs) {
    const page = await browser.newPage({ viewport: { width: job.width, height: job.height }, deviceScaleFactor: 1 });
    await page.goto('file://' + path.resolve(__dirname, job.file));
    await page.waitForTimeout(100);
    await page.screenshot({ path: path.resolve(outDir, job.out) });
    await page.close();
    console.log('Rendered', job.out);
  }
  await browser.close();
})();
