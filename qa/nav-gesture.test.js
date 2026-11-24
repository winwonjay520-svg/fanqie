'use strict';
const assert = require('node:assert/strict');
const fs = require('node:fs/promises');
const path = require('node:path');
const {chromium} = require('./browser-runtime/node_modules/playwright');

const assets = path.resolve(__dirname, '../app/src/main/assets');
async function exercise(browser, width, height) {
 const context = await browser.newContext({viewport: {width, height}, hasTouch: true, reducedMotion: 'reduce'});
 try {
  const tab = await context.newPage();
  await tab.addInitScript({path: path.join(__dirname, 'browser-native-stub.js')});
  await tab.route('https://lumi.local/**', async route => {
   const name = path.basename(new URL(route.request().url()).pathname);
   const types = {'.js':'text/javascript', '.css':'text/css', '.html':'text/html'};
   let body;
   try { body = await fs.readFile(path.join(assets, name)); }
   catch (error) {
    if (error.code !== 'ENOENT') throw error;
    return route.fulfill({status:404, body:''});
   }
   await route.fulfill({body, contentType: types[path.extname(name)]});
  });
  await tab.goto('https://lumi.local/index.html');
  await tab.evaluate(() => { window.ticks = 0; Native.navigationHaptic = () => { window.ticks++; }; });
  const buttons = tab.locator('.bottom-nav button');
  const centers = await buttons.evaluateAll(items => items.map(el => {
   const b = el.getBoundingClientRect();
   return {x: b.left + b.width / 2, y: b.top + b.height / 2};
  }));
  const cdp = await context.newCDPSession(tab);
  const touch = (type, p) => cdp.send('Input.dispatchTouchEvent', {type, touchPoints: p ? [p] : []});
  const state = () => tab.evaluate(() => [page, window.ticks]);
  const nav = tab.locator('.bottom-nav');
  const chosen = () => nav.locator('[aria-current=page]').getAttribute('data-page');
  const vertical = Math.abs(centers[3].y - centers[0].y) > Math.abs(centers[3].x - centers[0].x);
  assert.equal(vertical, width >= 900 && height >= 560);

  await buttons.nth(1).tap();
  assert.deepEqual(await state(), ['workspace', 1], 'tap changes page with one haptic');
  await buttons.nth(1).tap();
  assert.deepEqual(await state(), ['workspace', 1], 'reselect stays quiet');
  await touch('touchStart', centers[1]);
  await touch('touchMove', centers[2]);
  await tab.waitForFunction(() => document.querySelector('.nav-capsule').style.getPropertyValue('--nav-drag-index') !== '');
  assert.deepEqual(await state(), ['workspace', 2], 'drag previews without rendering the destination');
  assert.equal(await chosen(), 'workspace', 'screen readers keep the committed destination during preview');
  const mid = {x: (centers[2].x * 3 + centers[3].x) / 4, y: (centers[2].y * 3 + centers[3].y) / 4};
  await touch('touchMove', mid);
  const axis = vertical ? 'y' : 'x';
  await tab.waitForFunction(() => {
   const i = parseFloat(document.querySelector('.nav-capsule').style.getPropertyValue('--nav-drag-index'));
   return i > 2.1 && i < 2.4;
  });
  const capsule = await nav.locator('.nav-capsule').boundingBox();
  const center = capsule[axis] + (vertical ? capsule.height : capsule.width) / 2;
  assert(Math.abs(center - mid[axis]) < 2, 'lens follows the finger between item centers');
  assert.deepEqual(await state(), ['workspace', 2], 'movement within one item stays quiet');
  await touch('touchMove', centers[3]);
  await touch('touchEnd');
  assert.deepEqual(await state(), ['stats', 3], 'release commits once without another haptic');
  assert.equal(await chosen(), 'stats');

  await touch('touchStart', centers[3]);
  await touch('touchMove', centers[2]);
  await touch('touchCancel');
  assert.deepEqual(await state(), ['stats', 4], 'native cancellation restores the current page');
  assert.equal(await nav.locator('.nav-preview').count(), 0);
  await touch('touchStart', centers[3]);
  await touch('touchMove', centers[2]);
  const outside = {...centers[2]};
  if (vertical) outside.x += 100; else outside.y -= 100;
  await touch('touchMove', outside);
  await touch('touchEnd');
  assert.deepEqual(await state(), ['stats', 5], 'releasing outside cancels the drag');
  await buttons.nth(3).focus();
  await tab.keyboard.press(vertical ? 'ArrowUp' : 'ArrowLeft');
  assert.deepEqual(await state(), ['events', 6], 'directional keyboard selection follows the layout');
  await tab.keyboard.press('Enter');
  assert.deepEqual(await state(), ['events', 6], 'keyboard reselect stays quiet');
  await tab.evaluate(() => navigate('focus'));
  assert.deepEqual(await state(), ['focus', 6], 'programmatic navigation stays quiet');
  await tab.evaluate(() => { data.settings.navigationHaptics = false; });
  await buttons.nth(2).tap();
  assert.deepEqual(await state(), ['events', 6], 'turning off navigation haptics preserves selection');
  console.log('PASS navigation gestures '+width+' × '+height+(vertical?' side rail':' bottom bar'));
 } finally { await context.close(); }
}
(async () => {
 const browser = await chromium.launch({executablePath: 'C:/Program Files/Google/Chrome/Application/chrome.exe', headless: true});
 try {
  for (const [width,height] of [[360,616], [1696,1176], [950,470]]) await exercise(browser,width,height);
 } finally { await browser.close(); }
})().catch(error => { console.error(error); process.exitCode = 1; });
