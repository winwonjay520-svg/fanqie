'use strict';
const path = require('node:path');
const fs = require('node:fs/promises');
const assert = require('node:assert/strict');
const {chromium} = require('./browser-runtime/node_modules/playwright');

const assets = path.resolve(__dirname, '../app/src/main/assets');
const output = path.join(__dirname, 'screenshots-2.7.0');

async function checkProfile(browser, name, width, height) {
  const page = await browser.newPage({viewport: {width, height}});
  const errors = [];
  page.on('pageerror', error => errors.push(error.message));
  await page.addInitScript({path: path.join(__dirname, 'browser-native-stub.js')});
  await page.route('https://lumi.local/**', async route => {
    const file = path.basename(new URL(route.request().url()).pathname);
    if (file === 'wallpaper.jpg') {
      return route.fulfill({path: path.join(__dirname, 'wallpaper-fixture.jpg'), contentType: 'image/jpeg'});
    }
    return route.fulfill({path: path.join(assets, file)});
  });
  await page.goto('https://lumi.local/index.html');
  await page.waitForSelector('#todoQuick');
  const click = selector => page.locator(selector).click();
  const screenshot = async label => {
    await page.waitForFunction(() => !document.querySelector('#toast.show') && !window.glassMotion.activeCount);
    await page.waitForTimeout(350);
    await page.screenshot({path: path.join(output, name + '-' + label + '.png')});
  };

  await click('#appearanceButton');
  await click('[data-preset="black"]');
  assert.equal(await page.locator('body').evaluate(el => getComputedStyle(el).backgroundColor), 'rgb(0, 0, 0)');
  await click('[data-appearance-section="material"]');
  assert.equal(await page.locator('#range-glass').isVisible(), false);
  await click('[data-appearance-section="background"]');
  await click('[data-preset="white"]');
  await click('#previewDone');
  await page.waitForFunction(() => !document.querySelector('.glass-flow'));
  await screenshot('white-home');
  await page.evaluate(() => {
    for (const [title, cover] of [['读完这一章','auto'],['整理课堂笔记','night']]) {
      command('taskSave', {value:{title,cover,estimate:1,priority:2,timerMode:'focus',duration:1500}});
    }
  });

  // Feed the same completed import callback as Android; the image is served as a real JPEG.
  await page.evaluate(() => {
    Native.chooseWallpaper = () => {
      Native.appearance(JSON.stringify({preset:'photo',hasImage:true,imageVersion:'fixture',glass:56,dim:20}));
      wallpaperPicked(true);
    };
  });
  await click('#appearanceButton');
  await click('#choosePhoto');
  await click('[data-appearance-section="material"]');
  await page.evaluate(async () => {
    const image = new Image();
    image.src = 'https://lumi.local/wallpaper.jpg?v=fixture';
    await image.decode();
  });
  const sample = page.locator('.sample-card');
  const homeCard = page.locator('#todoQuick');
  const optics = element => element.evaluate(el => {
    const style = getComputedStyle(el);
    return {color: style.backgroundColor, blur: style.backdropFilter};
  });
  const clear = await optics(sample);
  assert.deepEqual(clear, await optics(homeCard), 'preview should use the material applied to the app');
  assert.match(clear.color, /^rgba\(/);
  assert.match(clear.blur, /blur\(/);
  assert.ok(parseFloat(clear.blur.slice(5)) <= 4, 'clear glass preserves the wallpaper detail');
  assert.ok(parseFloat(clear.color.split(',')[3]) < .56, 'photo glass stays translucent at the default preset');
  await click('[data-material="soft"]');
  assert.notEqual((await optics(sample)).blur, clear.blur);
  await click('[data-material="eco"]');
  assert.equal((await optics(sample)).blur, 'none');
  await click('[data-material="clear"]');
  const range = page.locator('#range-glass');
  await range.focus(); await range.press('Home');
  const low = (await optics(homeCard)).color;
  await range.press('End');
  assert.notEqual(low, (await optics(homeCard)).color);
  await click('#photoGlassLook');
  assert.equal(await page.locator('#appearanceMaterial').isVisible(), true);
  assert.deepEqual(await optics(sample), clear);
  await screenshot('photo-materials');
  await click('#previewDone');
  for (const dest of ['focus','workspace','events','stats']) {
    await click('.bottom-nav [data-page="'+dest+'"]');
    assert.equal(await page.locator('#'+dest+'Page').isVisible(), true);
    assert.equal(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth+1), true);
    assert.deepEqual(await optics(page.locator('#'+dest+'Page .glass:visible').first()), clear);
  }
  await click('.bottom-nav [data-page="focus"]');
  assert.equal((await optics(page.locator('.todo-card[data-auto-cover=true]'))).color, clear.color);
  assert.notEqual(await page.locator('[data-cover=night]').evaluate(el => getComputedStyle(el).backgroundImage), 'none');
  await screenshot('photo-home');
  await click('#settingsButton');
  await page.locator('#settingNavHaptics').uncheck();
  await click('#saveSettings');
  await click('#settingsButton');
  assert.equal(await page.locator('#settingNavHaptics').isChecked(), false);
  await click('#closeSheet');
  assert.deepEqual(errors, []);
  await page.close();
  console.log('PASS '+name+' editor, live photo optics, four pages and haptic preference');
}

(async () => {
  await fs.mkdir(output, {recursive:true});
  const browser = await chromium.launch({executablePath:'C:/Program Files/Google/Chrome/Application/chrome.exe', headless:true});
  try {
    await checkProfile(browser,'phone',420,909);
    await checkProfile(browser,'tablet',1696,1176);
    await checkProfile(browser,'compact',360,616);
  } finally { await browser.close(); }
})().catch(error => { console.error(error); process.exitCode=1; });
