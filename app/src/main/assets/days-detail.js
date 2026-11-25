'use strict';

(() => {
 const backgroundKey = 'dayDetailBackground';
 const app = document.querySelector('.app');
 let selectedId = '', returnTarget = null, lastRenderKey = '';
 let preferredBackground;
 try { preferredBackground = localStorage.getItem(backgroundKey); } catch {}

 Object.assign(paths, {
  dayBack: 'm15 4-8 8 8 8',
  dayEdit: 'm14 5 5 5M4 20l5-1L21 7a2.1 2.1 0 0 0-4-4L5 15Z',
  dayShare: 'M12 15V3m-4 4 4-4 4 4M6 11H4v10h16V11h-2',
  daySave: 'M12 3v12m-4-4 4 4 4-4M4 17v4h16v-4',
  dayPin: 'M5 21V3m0 1c5-4 8 3 15-1l-3 5 3 4c-7 4-10-3-15 1'
 });
 const detail = document.createElement('section');
 detail.id = 'dayDetail';
 detail.hidden = true;
 detail.setAttribute('role', 'dialog');
 detail.setAttribute('aria-modal', 'true');
 detail.setAttribute('aria-labelledby', 'dayDetailLabel');
 detail.innerHTML = `
  <div class="day-detail-toolbar">
   <button id="dayDetailBack" type="button" aria-label="返回倒数日列表">${icon('dayBack')}</button>
   <h2 id="dayDetailLabel">倒数日</h2>
   <button id="dayDetailEdit" type="button" aria-label="编辑倒数日">${icon('dayEdit')}</button>
  </div>
  <div class="day-detail-stage">
   <article class="day-detail-card" aria-labelledby="dayDetailName">
    <h3 id="dayDetailName"></h3>
    <div class="day-detail-value"><strong id="dayDetailNumber"></strong><span class="day-sr-only">天</span></div>
    <div class="day-detail-date">目标日 <time id="dayDetailDate"></time></div>
   </article>
   <div class="day-detail-actions" role="group" aria-label="倒数日操作">
    <button id="dayDetailShare" type="button">${icon('dayShare')}<span>分享</span></button>
    <button id="dayDetailSave" type="button">${icon('daySave')}<span>存为图片</span></button>
    <button id="dayDetailBackground" type="button">${icon('image')}<span>背景</span></button>
    <button id="dayDetailPin" type="button" aria-pressed="false" aria-label="置顶倒数日">${icon('dayPin')}<span id="dayPinLabel">置顶</span></button>
    <button id="dayDetailAdd" type="button" aria-label="添加倒数日">${icon('plus')}<span>添加</span></button>
   </div>
  </div>`;
 $('sheetOverlay').before(detail);

 function background() {
  return preferredBackground || (data?.appearance?.preset === 'photo' ? 'app' : 'wood');
 }
 function setBackground(mode) {
  preferredBackground = mode;
  try { localStorage.setItem(backgroundKey, mode); } catch {}
  detail.dataset.background = mode;
 }
 function selectedEvent() { return data?.events?.find(event => event.id === selectedId); }
 function heading(event, info) {
  return info.days === 0 ? event.title + ' · 今天' : event.title + info.relation;
 }
 function closeDetail() {
  if (!selectedId) return;
  selectedId = '';
  detail.hidden = true;
  document.body.classList.remove('day-detail-open');
  app.inert = false;
  const target = returnTarget?.isConnected ? returnTarget : $('addEvent');
  target?.focus({preventScroll:true});
  returnTarget = null;
 }
 function refreshDetail() {
  if (!selectedId) return;
  const event = selectedEvent();
  if (!event || page !== 'events') { closeDetail(); return; }
  const today = localDay();
  const pinned = data.pinnedEventId === selectedId;
  const mode = background();
  const key = JSON.stringify([selectedId,event.title,event.date,event.repeat,today,pinned,mode]);
  if (key === lastRenderKey) return;
  lastRenderKey = key;
  const info = DayDisplay.info(event, today);
  const number = String(Math.abs(info.days));
  $('dayDetailName').textContent = heading(event, info);
  $('dayDetailNumber').textContent = number;
  $('dayDetailNumber').style.setProperty('--day-digits', number.length);
  $('dayDetailDate').dateTime = info.next;
  $('dayDetailDate').textContent = info.fullDate;
  $('dayDetailPin').setAttribute('aria-pressed', String(pinned));
  $('dayDetailPin').setAttribute('aria-label', pinned ? '取消置顶' : '置顶倒数日');
  $('dayPinLabel').textContent = pinned ? '取消置顶' : '置顶';
  detail.dataset.background = mode;
 }
 window.openDayDetail = function(id) {
  if (!data.events.some(e => e.id === id)) return;
  if (!selectedId) returnTarget = document.activeElement;
  selectedId = id;
  lastRenderKey = '';
  detail.hidden = false;
  detail.scrollTop = 0;
  document.body.classList.add('day-detail-open');
  app.inert = true;
  refreshDetail();
  $('dayDetailBack').focus({preventScroll:true});
 };
 window.refreshDayDetail = refreshDetail;
 const previousBack = handleBack;
 handleBack = function() {
  if (selectedId && $('sheetOverlay').hidden) { closeDetail(); return true; }
  return previousBack();
 };
 const previousNavigate = navigate;
 navigate = function(next) {
  if (selectedId && next !== 'events') closeDetail();
  return previousNavigate(next);
 };
 $('dayDetailBack').onclick = closeDetail;
 $('dayDetailEdit').onclick = () => editEvent(selectedId);
 $('dayDetailAdd').onclick = () => editEvent();
 $('dayDetailPin').onclick = () => {
  const id = data.pinnedEventId === selectedId ? '' : selectedId;
  if (command('pinEvent', {id})) { refreshDetail(); toast(id ? '已置顶到倒数日首页' : '已取消置顶'); }
 };
 $('dayDetailBackground').onclick = () => {
  const mode = background();
  sheet('倒数日背景', `<div class="day-backdrop-options" role="group" aria-label="详情页背景">
   <button data-day-background="wood" aria-pressed="${mode === 'wood'}"><i></i>原木</button>
   <button data-day-background="app" aria-pressed="${mode === 'app'}"><i></i>应用背景</button>
  </div><button type="button" class="text-button" id="dayChangeWallpaper">选择应用背景或照片</button>`);
  $('sheetBody').querySelectorAll('[data-day-background]').forEach(button => {
   button.onclick = () => { setBackground(button.dataset.dayBackground); closeSheet(); };
  });
  $('dayChangeWallpaper').onclick = () => { setBackground('app'); appearance(); };
 };

 $('dayDetailShare').onclick = () => {
  const event = selectedEvent();
  if (!event) return;
  const info = DayDisplay.info(event);
  const title = heading(event,info);
  Native.shareEventText((info.days === 0 ? title : title + ' ' + Math.abs(info.days) + ' 天') + '\n目标日 ' + info.fullDate);
 };
 // Export the calendar card itself. Uploaded wallpaper stays on the device.
 $('dayDetailSave').onclick = () => {
  const event = selectedEvent();
  if (!event) return;
  try {
   const info = DayDisplay.info(event);
   const canvas = document.createElement('canvas');
   canvas.width = 960;
   canvas.height = 840;
   const ctx = canvas.getContext('2d');
   const style = getComputedStyle(detail);
   const color = key => style.getPropertyValue('--day-' + key).trim();
   ctx.fillStyle = color('paper');
   ctx.fillRect(0,0,960,840);
   ctx.fillStyle = color('blue');
   ctx.fillRect(0,0,960,170);
   ctx.fillStyle = color('footer');
   ctx.fillRect(0,692,960,148);
   ctx.textAlign = 'center';
   ctx.textBaseline = 'middle';
   ctx.font = '600 66px sans-serif';
   const title = heading(event, info);
   const lines = [''];
   for (const char of title) {
    const last = lines.length - 1;
    if (ctx.measureText(lines[last]+char).width > 840 && lines[last]) lines.push(char);
    else lines[last] += char;
   }
   // Scale wrapped titles to fit the colored band, keeping the full event name.
   ctx.save();
   ctx.translate(480,85);
   const scale = Math.min(1,140 / (lines.length * 75));
   ctx.scale(scale,scale);
   ctx.fillStyle = '#ffffff';
   lines.forEach((line,i) => ctx.fillText(line,0,(i - (lines.length-1)/2)*75));
   ctx.restore();
   ctx.fillStyle = color('ink');
   ctx.font = '700 320px sans-serif';
   ctx.fillText(String(Math.abs(info.days)),480,450,850);
   ctx.fillStyle = color('fine-ink');
   ctx.font = '500 42px sans-serif';
   ctx.fillText('目标日 ' + info.fullDate,480,768,870);
   Native.saveEventImage(canvas.toDataURL('image/png'));
  } catch { toast('图片导出失败，请重试'); }
 };
})();
