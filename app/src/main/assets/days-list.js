'use strict';

window.DayDisplay = {
  info(event, today = localDay()) {
    const next = LumiFeaturesData.occurrence(event, today);
    const days = dayIndex(next) - dayIndex(today);
    const weekday = new Date(next + 'T12:00:00').getDay();
    return {
      title: event.title,
      next,
      days,
      relation: days === 0 ? '今天' : days > 0 ? '还有' : '已经',
      fullDate: next + ' 星期' + '日一二三四五六'[weekday]
    };
  }
};

function renderEvents() {
  const today = localDay();
  const items = data.events.map(event => ({id: event.id, color: event.color, ...DayDisplay.info(event, today)}));
  items.sort((a, b) => (a.days < 0) - (b.days < 0) || Math.abs(a.days) - Math.abs(b.days));
  const featured = items.find(item => item.id === data.pinnedEventId) || items[0];
  const hero = $('featuredDay');
  const list = $('eventList');
  hero.hidden = !featured;
  $('manageDayPin').hidden = !featured;
  if (featured) {
    const isPinned = featured.id === data.pinnedEventId;
    const dateSize = String(Math.abs(featured.days)).length;
    hero.innerHTML = `<button type="button" class="glass day-feature" data-day-id="${escapeHTML(featured.id)}">
      <span class="day-feature-copy">
        <small class="day-feature-label">${isPinned ? '已置顶' : featured.days >= 0 ? '即将到来' : '最近过去'}</small>
        <strong>${escapeHTML(featured.title)}<span class="day-relation">${featured.relation}</span></strong>
        <small class="day-feature-date">目标日 · ${escapeHTML(featured.fullDate)}</small>
      </span>
      <span class="day-feature-count" data-digits="${dateSize}"><b>${Math.abs(featured.days)}</b><span lang="en" aria-label="天">Days</span></span>
    </button>`;
    list.innerHTML = items.map(item => `<button type="button" class="day-row" data-day-id="${escapeHTML(item.id)}" data-day-color="${escapeHTML(item.color)}">
      <span class="day-name">${escapeHTML(item.title)}<span class="day-relation">${item.relation}</span></span>
      <b class="day-number">${Math.abs(item.days)}</b><span class="day-unit">天</span>
    </button>`).join('');
  } else {
    hero.replaceChildren();
    list.innerHTML = `<div class="glass days-empty">${icon('calendar')}<h2>还没有倒数日</h2>
      <p>点右上角的加号添加日期，也可以从文件导入。</p></div>`;
  }
  window.refreshDayDetail?.();
}

$('eventsPage').addEventListener('click', event => {
  const button = event.target.closest('[data-day-id]');
  if (button) window.openDayDetail(button.dataset.dayId);
});
$('importEvents').onclick = () => Native.chooseEventImport();
$('manageDayPin').onclick = () => {
  const options = data.events.map(event =>
    `<option value="${escapeHTML(event.id)}">${escapeHTML(event.title)} · ${escapeHTML(DayDisplay.info(event).next)}</option>`
  ).join('');
  sheet('设置置顶', `<label class="form-label" for="pinChoice">首页大卡显示</label>
    <select class="field" id="pinChoice"><option value="">自动显示最近的倒数日</option>${options}</select>
    <button type="button" class="primary" id="saveDayPin">保存</button>`);
  $('pinChoice').value = data.pinnedEventId || '';
  $('saveDayPin').onclick = () => {
    const id = $('pinChoice').value;
    if (!command('pinEvent', {id})) return;
    closeSheet();
    $('manageDayPin').focus({preventScroll:true});
    toast(id ? '已更新置顶倒数日' : '已切换为自动显示');
  };
};
