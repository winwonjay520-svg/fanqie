'use strict';
const {chromium} = require('./browser-runtime/node_modules/playwright');
const assert = require('node:assert/strict');
const fs = require('node:fs/promises');
const path = require('node:path');

const assets = path.resolve(__dirname, '../app/src/main/assets');
const shots = path.resolve(__dirname, '../dist/qa-days');

async function check(browser, profile, width, height) {
  const page = await browser.newPage({viewport:{width, height}, timezoneId:'Asia/Shanghai'});
  const errors = [];
  page.on('pageerror', error => errors.push(error.message));
  await page.clock.setFixedTime(new Date('2028-06-07T12:00:00+08:00'));
  await page.addInitScript({path:path.join(__dirname, 'browser-native-stub.js')});
  await page.route('https://lumi.local/**', route => {
    const name = path.basename(new URL(route.request().url()).pathname);
    if (name === 'wallpaper.jpg') return route.fulfill({path:path.join(__dirname,'wallpaper-fixture.jpg')});
    return route.fulfill({path:path.join(assets, name)});
  });
  await page.goto('https://lumi.local/index.html');
  await page.locator('.bottom-nav [data-page="events"]').click();
  assert(await page.locator('.days-empty').isVisible());
  await page.evaluate(() => {
    Native.chooseEventImport = () => window.importOpened = true;
    Native.shareEventText = value => window.shared = value;
    Native.saveEventImage = value => window.imageSaved = value.startsWith('data:image/png;base64,');
    for (const [id,title,date,repeat] of [
      ['weekly','阅读计划','2028-05-27','weekly'],
      ['project','项目交付','2028-06-24','none'],
      ['annual','年度旅行','2027-07-17','yearly'],
      ['past','春季课程','2028-06-01','none'],
      ['today','图书归还','2028-06-07','none']
    ]) command('eventSave',{value:{id,title,date,repeat,category:'生活期待',color:'blue'}});
    command('pinEvent',{id:'annual'});
  });
  const click = selector => page.locator(selector).click();
  const visible = selector => page.locator(selector).isVisible();
  const back = () => page.evaluate(() => handleBack());
  assert.equal(await page.locator('.day-feature-count b').textContent(), '40');
  assert.deepEqual(await page.locator('.day-number').allTextContents(), ['0','3','17','40','6']);
  assert.match(await page.locator('.day-name').first().textContent(), /今天/);
  assert.match(await page.locator('.day-name').last().textContent(), /已经/);
  assert(await page.evaluate(() => {
    const a=$('featuredDay').getBoundingClientRect(), b=$('eventList').getBoundingClientRect();
    return document.documentElement.scrollWidth<=innerWidth+1 && (innerWidth>=900?b.left>=a.right:b.top>=a.bottom-1);
  }));
  await page.screenshot({path:path.join(shots, profile+'-list.png'), animations:'disabled'});
  await click('#manageDayPin');
  assert.equal(await page.locator('#pinChoice').inputValue(), 'annual');
  await page.locator('#pinChoice').selectOption('project');
  await click('#saveDayPin');
  assert.equal(await page.locator('.day-feature').getAttribute('data-day-id'), 'project');
  await click('#manageDayPin');
  await page.locator('#pinChoice').selectOption('');
  await click('#saveDayPin');
  assert.equal(await page.locator('.day-feature').getAttribute('data-day-id'), 'today');
  await click('#importEvents');
  assert(await page.evaluate(() => window.importOpened));
  await click('.day-row[data-day-id="weekly"]');
  assert.equal(await page.locator('#dayDetailDate').getAttribute('datetime'), '2028-06-10');
  assert.equal(await page.locator('#dayDetailNumber').textContent(), '3');
  assert.equal(await page.locator('#dayDetail').getAttribute('data-background'), 'wood');
  assert(await page.locator('#dayDetail').evaluate(el => el.scrollWidth <= el.clientWidth+1));
  await page.screenshot({path:path.join(shots, profile+'-detail.png')});
  await click('#dayDetailPin');
  assert.equal(await page.locator('#dayPinLabel').textContent(), '取消置顶');
  assert.equal(await page.locator('#dayDetailPin').getAttribute('aria-pressed'), 'true');
  await click('#dayDetailEdit');
  assert.equal(await page.locator('#eventTitle').inputValue(), '阅读计划');
  await page.locator('#eventTitle').fill('图书馆自习');
  await click('#saveEvent');
  assert.match(await page.locator('#dayDetailName').textContent(), /图书馆自习/);
  await click('#dayDetailShare');
  assert.match(await page.evaluate(() => window.shared), /图书馆自习还有 3 天/);
  await click('#dayDetailSave');
  assert(await page.evaluate(() => window.imageSaved));
  await click('#dayDetailBackground');
  await click('[data-day-background="app"]');
  assert.equal(await page.locator('#dayDetail').getAttribute('data-background'), 'app');
  await page.evaluate(() => {
    Native.appearance(JSON.stringify({preset:'photo',tone:'dark',hasImage:true,imageVersion:'fixture'}));
    refresh();
  });
  assert(await page.locator('#dayDetail').evaluate(el => document.body.classList.contains('theme-dark') && getComputedStyle(el).backgroundImage.includes('wallpaper.jpg')));
  await click('#dayDetailAdd');
  assert(await visible('#eventTitle'));
  await back();
  assert(await visible('#dayDetail') && !(await visible('#sheetOverlay')));
  await back();
  assert(!(await visible('#dayDetail')));
  assert.equal(await page.locator('.day-feature').getAttribute('data-day-id'), 'weekly');
  await click('.day-feature');
  await page.clock.setFixedTime(new Date('2028-06-11T00:03:00+08:00'));
  await page.evaluate(() => refresh());
  assert.equal(await page.locator('#dayDetailDate').getAttribute('datetime'),'2028-06-17');
  await click('#dayDetailEdit');
  await click('#deleteEvent');
  await click('#confirmDelete');
  assert(!(await visible('#dayDetail')) && !(await visible('#sheetOverlay')));
  assert.equal(await page.locator('.day-row').count(), 4);
  assert.deepEqual(errors, []);
  await page.close();
  console.log('PASS '+profile+' countdown list, recurrence, detail, actions, back, layout');
}

(async () => {
  await fs.mkdir(shots, {recursive:true});
  const browser=await chromium.launch({channel:'chrome',headless:true});
  try {
    for (const profile of [['phone',360,780],['tablet',1696,1176],['landscape',800,360]]) {
      await check(browser,...profile);
    }
  } finally {await browser.close();}
})().catch(error => {console.error(error); process.exitCode=1;});
