'use strict';
const chartColors=['#7f9fbd','#89b3aa','#aa9ac1','#c6a68f','#b8b182','#a7b7c6'];
let chartModel=null,trendView='bar';
let lineRenderedSize='',chartResizeFrame;
let savedTrendView;
try{const saved=localStorage.getItem('lumi-trend-view');if(saved==='line')trendView=saved;}catch(e){}
function chartPercent(value,total){if(!total)return '0%';const p=value/total*100;return p>0&&p<.1?'<0.1%':p.toFixed(p<10?1:0)+'%';}
function chartLabel(bucket,step){if(step==='year')return bucket.start.slice(0,4);if(step==='month')return bucket.start.slice(2,7).replace('-','/');return Number(bucket.start.slice(5,7))+'/'+Number(bucket.start.slice(8));}
function chartRangeLabel(bucket){return bucket.start===bucket.end?bucket.start:bucket.start+' 至 '+bucket.end;}
function setTrendView(view){
 if(!['bar','line'].includes(view))return;trendView=view;
 $('barChart').hidden=view!=='bar';$('lineChart').hidden=view!=='line';
 document.querySelectorAll('[data-trend-view]').forEach(b=>{const active=b.dataset.trendView===view;b.classList.toggle('selected',active);b.setAttribute('aria-selected',String(active));b.tabIndex=active?0:-1;});
 if(savedTrendView!==view){try{localStorage.setItem('lumi-trend-view',view);savedTrendView=view;}catch(e){}}
}
function renderCharts(prepared){
 chartModel=prepared||LumiChartsData.summary(data.sessions,localDay(),range,historyIndex());
 const {buckets,step}=chartModel.trend,scale=LumiChartsData.scale(buckets.map(b=>b.seconds));
 $('chartUnit').textContent=scale.unit+' / '+({day:'天',week:'周',month:'月',year:'年'}[step]);
 $('trendSpan').textContent=buckets.length?buckets[0].start.replaceAll('-','.')+' — '+buckets[buckets.length-1].end.replaceAll('-','.'):'暂无记录';
 const daily=step==='day',count=buckets.length;
 $('barChart').style.gap=count>7?'3px':'10px';
 $('barChart').innerHTML=buckets.map((b,i)=>{
  const height=b.seconds>0?Math.max(2,b.seconds/scale.divisor/scale.max*104):0;
  const label=count<=7||i===0||i===count-1||i%7===0?chartLabel(b,step):'';
  return `<div class="bar-column" role="button" tabindex="0" data-trend-key="${b.key}" ${daily?`data-chart-day="${b.start}"`:''} aria-label="${chartRangeLabel(b)}，${durationLabel(b.seconds)}，查看记录"><span class="bar-value">${count<=7&&b.seconds>0?Number((b.seconds/scale.divisor).toFixed(1)):''}</span><div class="bar-fill ${b.seconds?'':'zero'}" style="height:${height}px"></div><label>${label||' '}</label></div>`;
 }).join('');
 $('barChart').setAttribute('aria-label','专注趋势柱状图，'+buckets.map(b=>chartRangeLabel(b)+' '+durationLabel(b.seconds)).join('，'));
 renderLine(buckets,step,scale);renderDonut();renderHours();setTrendView(trendView);
 const sum=buckets.reduce((n,b)=>n+b.seconds,0),average=sum/buckets.length;
 $('chartCaption').textContent=sum>0?`${({day:'每天',week:'每周',month:'每月',year:'每年'})[step]}平均约 ${average<1?'<1 秒':durationLabel(Math.round(average))} · 点击图表查看记录`:'还没有专注记录，开始一次计时后点亮趋势';
}
function renderLine(buckets,step,scale){
 const {width,height}=lineDimensions();lineRenderedSize=width+'x'+height;
 const left=34,right=width-14,top=17,bottom=height-36,plotH=bottom-top,count=buckets.length;
 const points=buckets.map((b,i)=>({x:left+(count>1?i/(count-1):.5)*(right-left),y:bottom-b.seconds/scale.divisor/scale.max*plotH,b}));
 const path=points.map((p,i)=>(i?'L':'M')+p.x.toFixed(2)+','+p.y.toFixed(2)).join(' ');
 const area=points.length?`${path} L${points[points.length-1].x},${bottom} L${points[0].x},${bottom} Z`:'';
 const grid=Array.from({length:3},(_,i)=>{const y=top+i*plotH/2,value=scale.max*(1-i/2);return `<line class="line-grid" x1="${left}" x2="${right}" y1="${y}" y2="${y}"/><text class="line-axis" x="${left-7}" y="${y+3}" text-anchor="end">${Number(value.toFixed(2))}</text>`;}).join('');
 const dots=points.map(p=>`<circle class="line-point" role="button" tabindex="0" data-trend-key="${p.b.key}" cx="${p.x}" cy="${p.y}" r="${count>10?2.8:4}" aria-label="${chartRangeLabel(p.b)}，${durationLabel(p.b.seconds)}，查看记录"/>`).join('');
 const labels=points.filter((_,i)=>count<=7||i===0||i===count-1||i===Math.floor(count/2)).map(p=>`<text class="line-axis" x="${p.x}" y="${height-12}" text-anchor="${p===points[0]?'start':p===points[count-1]?'end':'middle'}">${chartLabel(p.b,step)}</text>`).join('');
 $('lineChart').innerHTML=`<svg viewBox="0 0 ${width} ${height}" role="group" aria-label="专注趋势折线图，单位${scale.unit}"><defs><linearGradient id="trendFill" x1="0" y1="0" x2="0" y2="1"><stop offset="0%" stop-color="#9fbcd5" stop-opacity=".38"/><stop offset="100%" stop-color="#dfeaf5" stop-opacity=".03"/></linearGradient></defs>${grid}<path d="${area}" fill="url(#trendFill)"/><path d="${path}" class="line-stroke"/>${dots}${labels}</svg>`;
}
function lineDimensions(){
 const card=$('lineChart').parentElement,style=getComputedStyle(card);
 return {width:Math.max(200,Math.round(card.clientWidth-parseFloat(style.paddingLeft)-parseFloat(style.paddingRight))),height:matchMedia('(min-width:700px)').matches?230:176};
}
function resizeVisibleLine(){
 if(page!=='stats'||!chartModel||document.hidden)return;
 cancelAnimationFrame(chartResizeFrame);chartResizeFrame=requestAnimationFrame(()=>{
  if(page!=='stats'||!chartModel||document.hidden)return;
  const {width,height}=lineDimensions();if(lineRenderedSize===width+'x'+height)return;
  const {buckets,step}=chartModel.trend;renderLine(buckets,step,LumiChartsData.scale(buckets.map(b=>b.seconds)));
 });
}
if(typeof ResizeObserver!=='undefined')new ResizeObserver(resizeVisibleLine).observe($('lineChart').parentElement);
else window.addEventListener('resize',resizeVisibleLine,{passive:true});
function renderDonut(){
 const total=chartModel.total,rows=chartModel.tasks,C=2*Math.PI*72;let offset=0;
 const arcs=rows.map((g,i)=>{const length=total?g.seconds/total*C:0,arc=`<circle class="donut-segment" role="button" tabindex="0" data-chart-task="${i}" cx="100" cy="100" r="72" pathLength="${C}" stroke="${chartColors[i]}" stroke-dasharray="${length} ${C-length}" stroke-dashoffset="${-offset}" aria-label="${escapeHTML(g.name)}，${escapeHTML(chartPercent(g.seconds,total))}，${escapeHTML(durationLabel(g.seconds))}"/>`;offset+=length;return arc;}).join('');
 const amount=total<60?total:Number((total/60).toFixed(1)),unit=total<60?'秒':'分钟';
 $('taskDonut').innerHTML=`<svg viewBox="0 0 200 200" role="group" aria-label="事项专注时长占比"><circle class="donut-track" cx="100" cy="100" r="72"/><g transform="rotate(-90 100 100)">${arcs}</g></svg><div class="donut-center"><small>${total?'专注合计':'等待第一份积累'}</small><strong>${total?amount:'—'}</strong><span>${total?unit:'暂无记录'}</span></div>`;
 $('donutLegend').innerHTML=rows.length?rows.map((g,i)=>`<button class="donut-legend-row" data-chart-task="${i}"><i style="background:${chartColors[i]}"></i><span>${escapeHTML(g.name)}</span><b>${escapeHTML(chartPercent(g.seconds,total))}</b><small>${escapeHTML(durationLabel(g.seconds))}</small></button>`).join(''):'<p class="chart-empty">为专注事项命名，记录会自动汇总到这里。</p>';
 $('taskChartCaption').textContent=rows.some(r=>r.other)?'展示前 5 个事项，其余合并 · 点击查看记录':'按实际专注时长统计 · 点击事项查看记录';
}
function hourLabel(row){return String(row.start).padStart(2,'0')+':00–'+String(row.end).padStart(2,'0')+':00';}
function renderHours(){
 const rows=chartModel.hours,scale=LumiChartsData.scale(rows.map(r=>r.seconds));
 $('hourUnit').textContent=scale.unit;
 $('hourChart').innerHTML=rows.map((r,i)=>`<button class="hour-column" data-chart-hour="${i}" aria-label="${hourLabel(r)}，${durationLabel(r.seconds)}，${r.count}次专注"><span class="hour-value">${r.seconds?Number((r.seconds/scale.divisor).toFixed(1)):''}</span><span class="hour-plot"><i style="height:${r.seconds?Math.max(3,r.seconds/scale.divisor/scale.max*96):0}px"></i></span><span class="hour-label">${String(r.start).padStart(2,'0')}–${String(r.end).padStart(2,'0')}</span></button>`).join('');
 const best=rows.reduce((a,b)=>b.seconds>a.seconds?b:a,rows[0]);
 $('hourChartCaption').textContent=chartModel.total?`积累最多的结束时段：${hourLabel(best)}`:'先记录一次专注，发现自己的节奏';
}
function showChartRecords(title,sessions){const total=sessions.reduce((n,s)=>n+s.seconds,0);sheet(title,`<div class="detail-summary"><strong>${escapeHTML(durationLabel(total))}</strong><span>${sessions.length} 次专注 · 当前统计范围内</span></div>${detailRecords(sessions)}<button class="primary" id="chartDetailDone">返回图表</button>`);$('chartDetailDone').onclick=()=>closeSheet();}
function openTrendBucket(key){const b=chartModel?.trend.buckets.find(b=>b.key===key);if(!b)return;if(b.start===b.end){showDayDetails(b.start);return;}showChartRecords(chartRangeLabel(b),chartModel.sessions.filter(s=>{const d=localDay(s.end);return d>=b.start&&d<=b.end;}));}
function openChartTask(index){const group=chartModel?.tasks[index];if(group)showChartRecords(group.name,chartModel.sessions.filter(s=>group.names.includes(s.label||'自由专注')));}
function openChartHour(index){const row=chartModel?.hours[index];if(row)showChartRecords(hourLabel(row)+' 的专注',chartModel.sessions.filter(s=>{const hour=new Date(s.end).getHours();return hour>=row.start&&hour<row.end;}));}
function chartTarget(event,selector){return event.target.closest(selector);}
for(const id of ['barChart','lineChart']){
 $(id).onclick=e=>{const hit=chartTarget(e,'[data-trend-key]');if(hit)openTrendBucket(hit.dataset.trendKey);};
 $(id).onkeydown=e=>{if(e.key==='Enter'||e.key===' '){const hit=chartTarget(e,'[data-trend-key]');if(hit){e.preventDefault();openTrendBucket(hit.dataset.trendKey);}}};
}
for(const id of ['taskDonut','donutLegend']){
 $(id).onclick=e=>{const hit=chartTarget(e,'[data-chart-task]');if(hit)openChartTask(Number(hit.dataset.chartTask));};
 $(id).onkeydown=e=>{if((e.key==='Enter'||e.key===' ')&&e.target.tagName.toLowerCase()==='circle'){e.preventDefault();openChartTask(Number(e.target.dataset.chartTask));}};
}
$('hourChart').onclick=e=>{const hit=chartTarget(e,'[data-chart-hour]');if(hit)openChartHour(Number(hit.dataset.chartHour));};
$('trendViewTabs').onclick=e=>{const hit=chartTarget(e,'[data-trend-view]');if(hit)setTrendView(hit.dataset.trendView);};
$('trendViewTabs').onkeydown=e=>{if(e.key==='ArrowLeft'||e.key==='ArrowRight'){e.preventDefault();const next=trendView==='bar'?'line':'bar';setTrendView(next);document.querySelector(`[data-trend-view="${next}"]`).focus();}};
lastStats='';if(data)render();
