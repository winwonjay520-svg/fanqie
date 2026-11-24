'use strict';
let referenceDay=localDay(),referenceMonth=localDay().slice(0,7),referenceYear=localDay().slice(0,4),referenceToday=localDay();
let referenceHistory=null,referenceModels=new Map(),referenceTotals,referenceResizeFrame;
const referenceCharts={};
function syncReferenceData(){
 const today=localDay();
 if(referenceHistory!==data.sessions||referenceToday!==today){
  if(referenceDay===referenceToday)referenceDay=today;
  if(referenceMonth===referenceToday.slice(0,7))referenceMonth=today.slice(0,7);
  if(referenceYear===referenceToday.slice(0,4))referenceYear=today.slice(0,4);
  referenceHistory=data.sessions;referenceToday=today;referenceModels.clear();referenceTotals=null;
 }
}
function referenceModel(unit,key){
 syncReferenceData();const today=referenceToday;
 const id=unit+':'+key;
 if(!referenceModels.has(id)){
  if(referenceModels.size>=6)referenceModels.delete(referenceModels.keys().next().value);
  referenceModels.set(id,LumiChartsData.calendar(data.sessions,key,unit,today,historyIndex()));
 }
 return referenceModels.get(id);
}
function setupReferenceStats(){
 if($('referenceAllTime'))return;
 $('referenceStatsTop').innerHTML=`<div class="stats-appearance-tools"><button id="statsWallpaperButton" class="glass">${icon('image')}选择背景图</button><button id="statsGlassButton" class="glass">玻璃与外观 ↗</button></div><section class="glass reference-total"><div class="section-title"><h2>累计专注</h2><span>全部历史记录</span></div><div class="reference-metrics" id="referenceAllTime"><div><small>专注次数</small><strong id="referenceAllCount">0</strong></div><div><small>实际专注</small><strong id="referenceAllDuration">0 秒</strong></div><div><small>有专注日均</small><strong id="referenceAllAverage">0 秒</strong></div></div></section><section class="glass reference-day"><div class="reference-heading"><h2>当日专注</h2><div class="period-controls"><button id="referencePreviousDay" aria-label="前一天">‹</button><input id="referenceDayInput" type="date" min="1900-01-01" aria-label="查看专注日期"><button id="referenceNextDay" aria-label="后一天">›</button><button id="referenceTodayButton">今天</button></div></div><div class="reference-metrics two"><div><small>专注次数</small><strong id="referenceDayCount">0</strong></div><div><small>实际专注</small><strong id="referenceDayDuration">0 秒</strong></div></div><button class="reference-records" id="referenceDayRecords">查看当天记录 ↗</button></section>`;
 $('statsWallpaperButton').onclick=()=>{flushAppearance();Native.chooseWallpaper();};$('statsGlassButton').onclick=appearance;
 $('referencePreviousDay').onclick=()=>moveReferenceDay(-1);$('referenceNextDay').onclick=()=>moveReferenceDay(1);
 $('referenceTodayButton').onclick=()=>{referenceDay=localDay();renderReferenceStats();};
 $('referenceDayInput').onchange=()=>{const input=$('referenceDayInput');if(!input.value||!input.checkValidity()){input.value=referenceDay;return;}referenceDay=input.value;renderReferenceStats();};
 $('referenceDayRecords').onclick=()=>showChartRecords(referenceDay+' 的专注',referenceModel('day',referenceDay).sessions);
 for(const unit of ['month','year']){
  const title=unit==='month'?'月度专注统计':'年度专注统计',node=document.createElement('section');node.className='glass reference-calendar';node.dataset.calendarUnit=unit;
  node.innerHTML=`<div class="reference-heading"><h2>${title}</h2><div class="period-controls"><button data-period-step="-1" aria-label="${unit==='month'?'上个月':'上一年'}">‹</button><input id="reference-${unit}-input" type="${unit==='month'?'month':'number'}" ${unit==='year'?'step="1" min="1900" inputmode="numeric"':'min="1900-01"'} aria-label="${title}时间"><button data-period-step="1" aria-label="${unit==='month'?'下个月':'下一年'}">›</button></div></div><p class="reference-calendar-total" id="reference-${unit}-total"></p><div class="calendar-chart" id="reference-${unit}-chart"></div><p class="reference-calendar-caption" id="reference-${unit}-caption"></p>`;
  $('referenceCalendars').append(node);node.querySelectorAll('[data-period-step]').forEach(b=>b.onclick=()=>moveReferencePeriod(unit,Number(b.dataset.periodStep)));
  const input=$('reference-'+unit+'-input');input.onchange=()=>{if(!input.value||!input.checkValidity()){input.value=unit==='month'?referenceMonth:referenceYear;return;}if(unit==='month')referenceMonth=input.value;else referenceYear=input.value;renderReferenceStats();};
  node.onclick=e=>{const point=e.target.closest('[data-calendar-key]');if(point)openReferenceBucket(unit,point.dataset.calendarKey);};
  node.onkeydown=e=>{if(e.key==='Enter'||e.key===' '){const point=e.target.closest('[data-calendar-key]');if(point){e.preventDefault();openReferenceBucket(unit,point.dataset.calendarKey);}}};
 }
 if(typeof ResizeObserver!=='undefined')new ResizeObserver(resizeReferenceCharts).observe($('referenceCalendars'));
 else window.addEventListener('resize',resizeReferenceCharts,{passive:true});
}
function moveReferenceDay(delta){
 const next=LumiChartsData.indexKey(LumiChartsData.dayIndex(referenceDay)+delta);if(next<'1900-01-01'||next>localDay())return;referenceDay=next;renderReferenceStats();
}
function moveReferencePeriod(unit,delta){
 if(unit==='year'){const next=String(Number(referenceYear)+delta);if(Number(next)<1900||next>localDay().slice(0,4))return;referenceYear=next;}
 else{const date=new Date(referenceMonth+'-01T12:00:00Z');date.setUTCMonth(date.getUTCMonth()+delta);const next=date.toISOString().slice(0,7);if(next<'1900-01'||next>localDay().slice(0,7))return;referenceMonth=next;}
 renderReferenceStats();
}
function openReferenceBucket(unit,key){const model=referenceModel(unit==='year'?'month':'day',key);showChartRecords(key+' 的专注',model.sessions);}
function renderReferenceStats(){
 if(page!=='stats')return;syncReferenceData();setupReferenceStats();const day=referenceModel('day',referenceDay);
 if(!referenceTotals){const rows=historyIndex().rows.filter(r=>r.day<=referenceToday),total=rows.reduce((n,r)=>n+r.session.seconds,0),days=new Set(rows.map(r=>r.day)).size;referenceTotals={count:rows.length,total,average:days?total/days:0};}
 $('referenceAllCount').textContent=referenceTotals.count;$('referenceAllDuration').textContent=durationLabel(referenceTotals.total);$('referenceAllAverage').textContent=durationLabel(Math.round(referenceTotals.average));
 const date=$('referenceDayInput');date.max=localDay();date.value=referenceDay;
 $('referencePreviousDay').disabled=referenceDay<='1900-01-01';$('referenceNextDay').disabled=referenceDay>=localDay();
 $('referenceDayCount').textContent=day.count;$('referenceDayDuration').textContent=durationLabel(day.total);
 for(const unit of ['month','year']){
  const key=unit==='month'?referenceMonth:referenceYear,model=referenceModel(unit,key),input=$('reference-'+unit+'-input'),node=input.closest('.reference-calendar');
  input.max=localDay().slice(0,unit==='month'?7:4);input.value=key;
  node.querySelector('[data-period-step="-1"]').disabled=key<=(unit==='month'?'1900-01':'1900');node.querySelector('[data-period-step="1"]').disabled=key>=input.max;
  $('reference-'+unit+'-total').textContent=durationLabel(model.total)+' · '+model.count+' 次专注';
  $('reference-'+unit+'-caption').textContent=model.count?`${model.activeDays} 个有专注的日子 · ${unit==='month'?'按每天':'按每月'}汇总，点击圆点查看记录`:`这个${unit==='month'?'月':'年份'}还没有专注记录`;
  renderReferenceChart(unit,model);
 }
}
function renderReferenceChart(unit,model){
 const host=$('reference-'+unit+'-chart'),width=Math.max(220,Math.round(host.clientWidth)),height=matchMedia('(min-width:700px)').matches?220:190;
 const previous=referenceCharts[unit];if(previous?.model===model&&previous.width===width&&previous.height===height)return;
 referenceCharts[unit]={model,width,height};const left=38,right=width-16,top=18,bottom=height-32,scale=LumiChartsData.scale(model.buckets.map(b=>b.seconds));
 const points=model.buckets.map((b,i)=>({b,x:left+i/(model.buckets.length-1)*(right-left),y:bottom-b.seconds/scale.divisor/scale.max*(bottom-top)}));
 const path=points.map((p,i)=>(i?'L':'M')+p.x.toFixed(2)+','+p.y.toFixed(2)).join(' '),area=path+` L${right},${bottom} L${left},${bottom} Z`,gradient='calendarFill-'+unit;
 const grid=[0,.5,1].map(f=>{const y=bottom-f*(bottom-top);return `<line x1="${left}" y1="${y}" x2="${right}" y2="${y}" class="calendar-grid-line"/><text x="${left-7}" y="${y+4}" text-anchor="end" class="calendar-axis">${Number((scale.max*f).toFixed(1))}</text>`;}).join('');
 const stride=unit==='year'?2:7;
 const labels=points.filter((_,i)=>i===0||i===points.length-1||(i%stride===0&&points.length-1-i>Math.max(2,stride/2))).map(p=>`<text x="${p.x}" y="${height-8}" text-anchor="${p===points[0]?'start':p===points[points.length-1]?'end':'middle'}" class="calendar-axis">${Number(p.b.key.slice(unit==='year'?5:8))}${unit==='year'?'月':'日'}</text>`).join('');
 host.innerHTML=`<svg viewBox="0 0 ${width} ${height}" role="group" aria-label="${escapeHTML(model.key)} 专注趋势，单位${scale.unit}"><defs><linearGradient id="${gradient}" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="currentColor" stop-opacity=".30"/><stop offset="1" stop-color="currentColor" stop-opacity=".02"/></linearGradient></defs>${grid}<path d="${area}" fill="url(#${gradient})"/><path d="${path}" class="calendar-stroke"/>${points.map(p=>`<circle cx="${p.x}" cy="${p.y}" r="${unit==='month'?3:4}" class="calendar-point" role="button" tabindex="0" data-calendar-key="${p.b.key}" aria-label="${p.b.key}，${escapeHTML(durationLabel(p.b.seconds))}，查看记录"/>`).join('')}${labels}<text x="${left}" y="10" class="calendar-axis">${scale.unit}</text></svg>`;
}
function resizeReferenceCharts(){
 if(page!=='stats'||document.hidden)return;cancelAnimationFrame(referenceResizeFrame);referenceResizeFrame=requestAnimationFrame(()=>{
  if(page==='stats'&&!document.hidden)for(const unit of ['month','year']){const previous=referenceCharts[unit];if(previous)renderReferenceChart(unit,previous.model);}
 });
}
const renderStatsBeforeReference=renderStats;
renderStats=function(){renderStatsBeforeReference();renderReferenceStats();};
