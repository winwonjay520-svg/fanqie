'use strict';
// Independently implemented after studying the interaction patterns listed in docs/research-1.2.md.
let immersive=false,focusScroll=0,cycleKey='',labelGroups=[];
const reducedMotion=matchMedia('(prefers-reduced-motion: reduce)');
function setImmersive(enabled){
 if(enabled&&page!=='focus')navigate('focus');
 immersive=!!enabled;document.body.classList.toggle('immersive',immersive);
 if(immersive){focusScroll=window.scrollY;window.scrollTo(0,0);$('immerseButton').textContent='退出沉浸 ↙';$('immerseButton').setAttribute('aria-label','退出沉浸专注');}
 else{$('immerseButton').textContent='沉浸 ↗';$('immerseButton').setAttribute('aria-label','进入沉浸专注');window.scrollTo(0,focusScroll);}
 $('immerseButton').setAttribute('aria-pressed',String(immersive));
}
function moveNavCapsule(next){
 const index=['focus','workspace','events','stats'].indexOf(next);
 document.querySelector('.bottom-nav').style.setProperty('--nav-index',Math.max(0,index));
 document.querySelectorAll('[data-page]').forEach(b=>{if(b.dataset.page===next)b.setAttribute('aria-current','page');else b.removeAttribute('aria-current');});
}
function completedPomos(){return historyIndex().pomos;}
function roundInfo(){
 const total=completedPomos(),completed=data.timer.mode==='focus'&&data.timer.status==='completed'&&data.timer.completed;
 const done=completed&&total>0?(total%4||4):total%4;
 return {total,done,round:Math.min(4,done+1),long:completed&&total>0&&total%4===0};
}
function renderEnhancements(){
 const t=data.timer,info=roundInfo();
 document.body.classList.toggle('timer-active',active());
 $('extendButton').disabled=!active()||t.mode==='up'||t.duration+300>86400;
 const key=[info.total,t.mode,t.status,t.completed].join('|');
 if(key!==cycleKey){cycleKey=key;
  $('cycleIndicator').innerHTML=t.mode==='focus'?`<span class="cycle-dots" aria-hidden="true">${Array.from({length:4},(_,i)=>`<i class="${i<info.done?'done':i===info.done?'current':''}"></i>`).join('')}</span><span>${info.done===4?'4 / 4 已完成':'第 '+info.round+' / 4 轮'}</span>`:`<span>${t.mode==='up'?'跟随心流，自由记录':t.mode==='break'?'留白，让下一次更从容':'一步一步，接近目标'}</span>`;
  $('cycleIndicator').setAttribute('aria-label',t.mode==='focus'?`本组已完成 ${info.done} 个番茄，共 4 个`:t.mode==='break'?'休息时间不计入专注':'自由计时');
 }
}
function selectedPeriodSessions(){const start=range==='all'?'0000-01-01':dayOffset(-Number(range)+1);return data.sessions.filter(s=>localDay(s.end)>=start&&localDay(s.end)<=localDay());}
function durationLabel(seconds){if(seconds<60)return seconds+' 秒';const h=Math.floor(seconds/3600),m=Math.floor(seconds%3600/60),s=seconds%60;return (h?h+' 小时 ':'')+(m?m+' 分钟 ':'')+(s?s+' 秒':'');}
function groupByLabel(sessions){
 const map=new Map();sessions.forEach(s=>{const name=s.label||'自由专注';const current=map.get(name)||{name,seconds:0,count:0};current.seconds+=s.seconds;current.count++;map.set(name,current);});
 return [...map.values()].sort((a,b)=>b.seconds-a.seconds||a.name.localeCompare(b.name,'zh-CN'));
}
function renderInsights(sessions){
 const total=sessions.reduce((n,s)=>n+s.seconds,0);
 $('preciseTotal').textContent=total>0?`实际记录 ${durationLabel(total)} · 点击日期查看当天记录`:'从一次专注开始，时间会慢慢积累';
 labelGroups=groupByLabel(sessions);
 $('labelBreakdown').innerHTML=labelGroups.length?labelGroups.slice(0,8).map((g,i)=>`<button class="label-row" data-label-index="${i}" aria-label="${escapeHTML(g.name)}，${escapeHTML(durationLabel(g.seconds))}"><div class="label-top"><span>${escapeHTML(g.name)}</span><small>${escapeHTML(durationLabel(g.seconds))} <b>↗</b></small></div><div class="label-track"><i style="width:${total?g.seconds/total*100:0}%"></i></div><div class="label-meta">${g.count} 次专注 · ${total?Math.round(g.seconds/total*100):0}%</div></button>`).join(''):'<p class="insight-empty">给专注事项起个名字，就能看见时间的去向。</p>';
 if(labelGroups.length>8)$('labelBreakdown').innerHTML+='<p class="insight-empty">展示时长最多的 8 个事项；完整记录见下方列表。</p>';
}
function detailRecords(sessions){
 if(!sessions.length)return '<p class="insight-empty">这一天还没有专注记录，给自己留一点时间吧。</p>';
 return [...sessions].sort((a,b)=>b.end-a.end).slice(0,60).map(s=>`<article class="detail-record"><div><b>${escapeHTML(s.label||'自由专注')}</b><small>${escapeHTML(localDay(s.end))} · ${new Date(s.end).toLocaleTimeString('zh-CN',{hour:'2-digit',minute:'2-digit'})} · ${s.mode==='focus'?(s.completed?'完成番茄':'番茄计时'):s.mode==='up'?'正计时':'倒计时'}</small></div><span>${escapeHTML(durationLabel(s.seconds))}</span></article>`).join('')+(sessions.length>60?'<p class="insight-empty">此处显示最近 60 条，可导出本地数据查看全部记录。</p>':'');
}
function showDayDetails(day){
 const sessions=data.sessions.filter(s=>localDay(s.end)===day),total=sessions.reduce((n,s)=>n+s.seconds,0);
 sheet(day+' 的积累',`<div class="detail-summary"><strong>${escapeHTML(durationLabel(total))}</strong><span>${sessions.length} 次专注 · ${sessions.filter(s=>s.mode==='focus'&&s.completed).length} 个完成番茄</span></div>${detailRecords(sessions)}<button class="primary" id="detailDone">收好这份积累</button>`);
 $('detailDone').onclick=()=>closeSheet();
}
function showLabelDetails(index){const group=labelGroups[index];if(!group)return;const sessions=selectedPeriodSessions().filter(s=>(s.label||'自由专注')===group.name);
 sheet(group.name,`<div class="detail-summary"><strong>${escapeHTML(durationLabel(group.seconds))}</strong><span>当前统计范围内 · ${group.count} 次专注</span></div>${detailRecords(sessions)}<button class="primary" id="detailDone">返回统计</button>`);$('detailDone').onclick=()=>closeSheet();
}
const baseShowCompletion=showCompletion;
showCompletion=function(){
 if(immersive&&!window.isFocusSceneOpen?.())setImmersive(false);
 baseShowCompletion();
 if(roundInfo().long){$('completionNext').textContent='四个番茄完成 · 长休息 '+data.settings.long+' 分钟';$('completionNext').onclick=()=>{closeSheet();configure('break',data.settings.long*60);};if($('longRest')){$('longRest').textContent='想继续保持节奏？短休息 '+data.settings.short+' 分钟';$('longRest').onclick=()=>{closeSheet();configure('break',data.settings.short*60);};}}
};
$('immerseButton').onclick=()=>setImmersive(!immersive);
$('extendButton').onclick=()=>{if(command('extend',{seconds:300}))toast('已延长 5 分钟，已用时间保持不变');};
$('labelBreakdown').onclick=e=>{const button=e.target.closest('[data-label-index]');if(button)showLabelDetails(Number(button.dataset.labelIndex));};
$('barChart').onclick=e=>{const column=e.target.closest('[data-chart-day]');if(column)showDayDetails(column.dataset.chartDay);};
$('barChart').onkeydown=e=>{if(e.key==='Enter'||e.key===' '){const column=e.target.closest('[data-chart-day]');if(column){e.preventDefault();showDayDetails(column.dataset.chartDay);}}};
// A held touch moves the highlight without rebuilding the card or running idle animation loops.
let highlightTarget=null,highlightFrame=0,highlightPoint;
document.addEventListener('pointerdown',e=>{if(!data?.appearance?.liquid||data.appearance.material==='eco'||reducedMotion.matches)return;highlightTarget=e.target.closest('.glass,.timer-orb');},{passive:true});
document.addEventListener('pointermove',e=>{if(!highlightTarget)return;highlightPoint={x:e.clientX,y:e.clientY};if(highlightFrame)return;highlightFrame=requestAnimationFrame(()=>{highlightFrame=0;if(!highlightTarget)return;const box=highlightTarget.getBoundingClientRect();highlightTarget.style.setProperty('--touch-x',(Math.max(0,Math.min(1,(highlightPoint.x-box.left)/box.width))*100)+'%');highlightTarget.style.setProperty('--touch-y',(Math.max(0,Math.min(1,(highlightPoint.y-box.top)/box.height))*100)+'%');});},{passive:true});
for(const event of ['pointerup','pointercancel'])document.addEventListener(event,()=>{highlightTarget=null;if(highlightFrame)cancelAnimationFrame(highlightFrame);highlightFrame=0;},{passive:true});
moveNavCapsule(page);lastStats='';if(data)render();
