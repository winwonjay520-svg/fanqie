'use strict';
const $ = id => document.getElementById(id);
const paths = {
 spark:'M12 2l2.3 7.7L22 12l-7.7 2.3L12 22l-2.3-7.7L2 12l7.7-2.3Z',
 settings:'M9 3h6l1 3 3 1 2 5-2 5-3 1-1 3H9l-1-3-3-1-2-5 2-5 3-1Zm3 5a4 4 0 1 0 0 8 4 4 0 0 0 0-8',
 chevron:'m9 5 7 7-7 7',play:'m9 5 10 7-10 7Z',pause:'M8 5v14M16 5v14',stop:'M6 6h12v12H6Z',
 reset:'M4 10a8 8 0 1 1 1 7M4 4v6h6',sliders:'M5 3v7m0 4v7M12 3v12m0 4v2M19 3v2m0 4v12M2 10h6m1 5h6m1-10h6',
 leaf:'M20 3C8 1 1 8 5 16s17 4 15-13ZM5 20 16 9',clock:'M12 3a9 9 0 1 0 0 18 9 9 0 0 0 0-18m0 4v5l4 2',
 tomato:'M12 7c-7-4-12 7-6 12 4 4 8 4 12 0 6-5 1-16-6-12Zm0 0 3-4m-3 4-3-3m3 3V2',
 timer:'M9 2h6M12 6a8 8 0 1 0 0 16 8 8 0 0 0 0-16m0 4v4l3 2M18 5l2 2',
 calendar:'M5 5h14a2 2 0 0 1 2 2v12a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V7a2 2 0 0 1 2-2Zm2-3v6m10-6v6M3 11h18M8 15h2m4 0h2m-8 3h2',
 chart:'M4 3v17h17M8 15v-4m5 4V7m5 8v-6',plus:'M12 5v14M5 12h14',close:'m6 6 12 12M6 18 18 6',check:'m5 12 4 4L19 6'
};
function icon(name){return `<svg viewBox="0 0 24 24" aria-hidden="true"><path d="${paths[name]||paths.spark}"/></svg>`;}
function icons(root=document){root.querySelectorAll('[data-icon]').forEach(el=>el.innerHTML=icon(el.dataset.icon));}
function escapeHTML(value){return String(value??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));}
let data, page='focus',range='7',seenCompletion=0,lastStats='',lastEvents='',statsSource=null,eventsSource=null,recordLimit=10,editingColor='green',toastTimeout,refreshTimer,lastPresetKey='',historySource=null,historyClock='',indexedHistory;
function acceptSnapshot(incoming){
 // Native revisions distinguish actual history edits from timer-only commands.
 if(data){for(const [field,revision] of [['sessions','_historyRevision'],['events','_eventsRevision'],['tasks','_workspaceRevision']]){
  if(incoming[field]&&incoming[revision]!==undefined&&incoming[revision]===data[revision]&&incoming[field].length===data[field].length)incoming[field]=data[field];
 }}
 data={...data,...incoming};
}
function historyIndex(){
 const clock=localDay()+'|'+new Date().getTimezoneOffset();
 if(historySource!==data.sessions||historyClock!==clock){historySource=data.sessions;historyClock=clock;indexedHistory=LumiChartsData.index(data.sessions);}
 return indexedHistory;
}
function toast(message){$('toast').textContent=message;$('toast').classList.add('show');clearTimeout(toastTimeout);toastTimeout=setTimeout(()=>$('toast').classList.remove('show'),2800);}
function command(action,values={}){
 try{
   const result=JSON.parse(Native.command(JSON.stringify({action,...values})));
   if(result.error){toast(result.error);return false;}
   acceptSnapshot(result);render();return true;
 }catch(e){toast('操作未完成，请重试');return false;}
}
function refresh(){
 try{const incoming=JSON.parse(Native.poll(String(data?._revision??-1)));acceptSnapshot(incoming);render();}catch(e){$('timerCaption').textContent='请在番茄安卓应用中打开';scheduleRefresh();}
}
function localDay(value=Date.now()){const d=new Date(value);return `${d.getFullYear()}-${String(d.getMonth()+1).padStart(2,'0')}-${String(d.getDate()).padStart(2,'0')}`;}
function dayOffset(offset){const d=new Date();d.setHours(12,0,0,0);d.setDate(d.getDate()+offset);return localDay(d);}
function dayIndex(date){const parts=date.split('-').map(Number);return Math.floor(Date.UTC(parts[0],parts[1]-1,parts[2])/86400000);}
function formatTime(ms){const sec=Math.max(0,Math.ceil(ms/1000)),h=Math.floor(sec/3600),m=Math.floor(sec%3600/60),s=sec%60;return h?`${String(h).padStart(2,'0')}:${String(m).padStart(2,'0')}:${String(s).padStart(2,'0')}`:`${String(m).padStart(2,'0')}:${String(s).padStart(2,'0')}`;}
function active(){return data&&['running','paused'].includes(data.timer.status);}
function render(){
 if(window.applyAppearance) applyAppearance(data.appearance);
 const t=data.timer,s=data.settings,running=t.status==='running';
 document.body.classList.toggle('running',running);
 const shownMode=t.mode==='break'?'focus':t.mode;
 document.querySelectorAll('[data-mode]').forEach(b=>{b.classList.toggle('selected',b.dataset.mode===shownMode);b.setAttribute('aria-selected',String(b.dataset.mode===shownMode));});
 $('taskLabel').textContent=t.mode==='break'?'休息一下':t.label||'自由专注';
 const elapsed=t.elapsedNow??t.elapsed;
 $('timerText').textContent=formatTime(t.mode==='up'?Math.floor(elapsed/1000)*1000:Math.max(0,t.duration*1000-elapsed));
 $('timerText').classList.toggle('long',$('timerText').textContent.length>5);
 $('timerModeLabel').textContent=({focus:'FOCUS TIME',up:'MAKE TIME COUNT',down:'COUNTDOWN',break:'TAKE A BREATH'})[t.mode];
 $('timerCaption').textContent=({idle:t.mode==='break'?'放松，也是前进的一部分':'一段专注，一点进步',running:t.mode==='break'?'深呼吸，给自己一点留白':'此刻，只做好这一件事',paused:'停一停，随时可以继续',completed:t.mode==='break'?'休息结束，轻装再出发':t.completed?'这段时光，已成为你的积累':'每一份认真，都值得被记录'})[t.status];
 $('progressArc').style.strokeDashoffset=t.mode==='up'?779.12*(1-(elapsed/1000%3600)/3600):779.12*Math.min(1,elapsed/(t.duration*1000));
 $('startLabel').textContent=running?'暂停':t.status==='paused'?'继续计时':t.mode==='break'?'开始休息':t.status==='completed'?'再来一次':'开始专注';
 const playIcon=running?'pause':'play';if($('startButton').firstElementChild.dataset.currentIcon!==playIcon){$('startButton').firstElementChild.innerHTML=icon(playIcon);$('startButton').firstElementChild.dataset.currentIcon=playIcon;}
 $('resetButton').disabled=t.status==='idle';$('finishButton').disabled=!active();
 $('taskButton').disabled=t.mode==='break';
 $('taskButton').setAttribute('aria-label',active()?'切换当前待办':'选择专注待办');
 $('timerFootnote').textContent=running?'专注正在继续，切到后台也会记录':t.mode==='break'?'休息时间不会计入专注统计':'不必着急，按照自己的节奏来';
 const presets=$('presets'),presetKey=JSON.stringify([t.mode,t.duration,active(),s.focus,s.short,s.long]);
 if(presetKey!==lastPresetKey){lastPresetKey=presetKey;
 if(t.mode==='up'){presets.innerHTML='<div class="mode-note">无需设限 · 结束时保存专注时长</div>';}
 else{
 const mins=t.mode==='break'?[s.short,s.long]:[s.focus,45,60];
 const unique=[...new Set(mins)];
 presets.innerHTML=unique.map(m=>`<button data-min="${m}" class="${t.duration===m*60?'selected':''}" ${active()?'disabled':''}>${m} 分钟</button>`).join('')+`<button id="customButton" aria-label="自定义计时" ${active()?'disabled':''}>${icon('sliders')}</button>`;
 }
 }
 const today=historyIndex().days.get(localDay()),todaySec=today?.seconds||0;
 $('todayMinutes').textContent=Math.floor(todaySec/60);$('todayPomodoros').textContent=today?.pomos||0;
 $('goalProgress').style.width=Math.min(100,todaySec/(s.goal*60)*100)+'%';
 $('goalText').textContent=`今日目标 ${s.goal} 分钟 · 已完成 ${Math.min(100,Math.floor(todaySec/(s.goal*60)*100))}%`;
 $('todayNote').textContent=todaySec>=s.goal*60?'今日目标已达成，真棒':'每一次开始，都算数';
 const now=new Date();$('todayLabel').textContent=`${now.getMonth()+1} 月 ${now.getDate()} 日 · ${['星期日','星期一','星期二','星期三','星期四','星期五','星期六'][now.getDay()]} / A LITTLE FOCUS`;
 const key=[range,recordLimit,historyClock,data.planner?.weekGoal].join('|');
 if(page==='stats'&&(statsSource!==data.sessions||key!==lastStats)){lastStats=key;statsSource=data.sessions;renderStats();}
 const eventKey=localDay()+'|'+(data.pinnedEventId||'');
 if(page==='events'&&(eventsSource!==data.events||eventKey!==lastEvents)){lastEvents=eventKey;eventsSource=data.events;renderEvents();}
 if(window.renderEnhancements)renderEnhancements();
 if(window.renderWorkspace)renderWorkspace();
 if(window.renderFocusFeatures)renderFocusFeatures();
 if(t.status==='completed'&&t.completed&&t.completionId!==seenCompletion){seenCompletion=t.completionId;showCompletion();}
 scheduleRefresh();
}
function navigate(next){if(window.setImmersive&&document.body.classList.contains('immersive'))setImmersive(false);page=next;document.body.classList.toggle('days-view',next==='events');if(window.moveNavCapsule)moveNavCapsule(next);document.querySelectorAll('.page').forEach(el=>el.classList.toggle('active',el.id===next+'Page'));document.querySelectorAll('[data-page]').forEach(el=>el.classList.toggle('selected',el.dataset.page===next));if(data)render();window.scrollTo(0,0);}
function sheet(title,body){$('sheetTitle').textContent=title;$('sheetBody').innerHTML=body;$('sheetOverlay').hidden=false;document.body.style.overflow='hidden';icons($('sheetBody'));}
function closeSheet(){$('sheetOverlay').hidden=true;document.body.style.overflow='';}
function handleBack(){if(document.body.classList.contains('immersive')&&$('sheetOverlay').hidden){setImmersive(false);return true;}if(!$('sheetOverlay').hidden){closeSheet();return true;}if(page!=='focus'){navigate('focus');return true;}return false;}
function configure(mode,duration,label,taskId){return command('configure',{mode,duration,label:label??data.timer.label??'自由专注',taskId:taskId??((label===undefined||label===data.timer.label)?data.timer.taskId||'':'')});}
function askEnd(reset=false){
 if(!active()){command('reset');return;}
 sheet(reset?'重新开始？':'保存这段专注？',`<p class="sheet-description">${reset?'重新开始会放弃当前待办尚未保存的时间，之前已保存的时段保留。':'当前待办已专注 '+formatTime(Math.max(0,(data.timer.elapsedNow||0)-(data.timer.accountedSeconds||0)*1000))+ '，结束后保存；之前的待办时段已分别记入统计。'}</p><button class="primary" id="confirmEnd">${reset?'放弃并重置':'结束并保存'}</button><button class="text-button" id="cancelEnd">继续当前计时</button>`);
 $('confirmEnd').onclick=()=>{closeSheet();if(command(reset?'reset':'finish'))toast(reset?'已重置计时':'这段时间已保存');};$('cancelEnd').onclick=closeSheet;
}
function showCustom(){
 const t=data.timer;sheet('给专注设一个刻度',`<p class="sheet-description">短一点，或久一点。找到适合自己的节奏。</p><div class="field-row"><div><label class="form-label" for="customMins">分钟</label><input class="field" id="customMins" type="number" min="0" max="1440" value="${Math.floor(t.duration/60)}" inputmode="numeric"></div><div><label class="form-label" for="customSecs">秒</label><input class="field" id="customSecs" type="number" min="0" max="59" value="${t.duration%60}" inputmode="numeric"></div></div><p class="error-text" id="formError"></p><button class="primary" id="saveCustom">设为计时时间</button>`);
 $('saveCustom').onclick=()=>{const m=Number($('customMins').value),s=Number($('customSecs').value);if(!Number.isInteger(m)||!Number.isInteger(s)||m<0||s<0||s>59||m*60+s<1||m*60+s>86400){$('formError').textContent='请输入 1 秒至 24 小时的有效时间';return;}if(configure(t.mode,m*60+s))closeSheet();};
}
function editTask(){sheet('这一刻，专注什么？','<p class="sheet-description">给这段时间一个名字，稍后在统计中回顾。</p><label class="form-label" for="taskInput">专注事项</label><input class="field" id="taskInput" maxlength="40" placeholder="例如：阅读、学习英语、完成设计"><button class="primary" id="saveTask">准备好了</button>');$('taskInput').value=data.timer.label==='自由专注'?'':data.timer.label;$('saveTask').onclick=()=>{const label=$('taskInput').value.trim()||'自由专注';if(active()?command('switchTask',{taskId:'',label}):configure(data.timer.mode,data.timer.duration,label))closeSheet();};}
function editEvent(id){
 const e=data.events.find(x=>x.id===id)||{title:'',date:dayOffset(1),category:'生活期待',color:'green'};editingColor=e.color||'green';
 sheet(id?'编辑倒计日':'新的期待',`<label class="form-label" for="eventTitle">名称</label><input id="eventTitle" class="field" maxlength="40" placeholder="例如：下一次旅行"><label class="form-label" for="eventDate">日期</label><input id="eventDate" class="field" type="date" min="1900-01-01" max="2200-12-31"><label class="form-label" for="eventCategory">分类</label><select id="eventCategory" class="field"><option>生活期待</option><option>学习计划</option><option>重要纪念</option><option>工作目标</option></select><label class="form-label">卡片颜色</label><div class="color-picker"><button data-color="green" aria-label="薄荷绿"></button><button data-color="peach" aria-label="暖杏色"></button><button data-color="blue" aria-label="雾蓝色"></button></div><p class="error-text" id="formError"></p><button class="primary" id="saveEvent">保存这份期待</button>${id?'<button class="text-button danger" id="deleteEvent">删除倒计日</button>':''}`);
 $('eventTitle').value=e.title;$('eventDate').value=e.date;$('eventCategory').value=e.category;
 function colors(){document.querySelectorAll('[data-color]').forEach(b=>b.classList.toggle('selected',b.dataset.color===editingColor));}colors();
 document.querySelectorAll('[data-color]').forEach(b=>b.onclick=()=>{editingColor=b.dataset.color;colors();});
 $('saveEvent').onclick=()=>{const title=$('eventTitle').value.trim(),date=$('eventDate').value;if(!title||!date||!$('eventDate').checkValidity()){$('formError').textContent='请填写名称，并选择 1900–2200 年之间的有效日期';return;}const value={title,date,category:$('eventCategory').value,color:editingColor};if(id)value.id=id;if(command('eventSave',{value})){closeSheet();toast('期待已保存');}};
 if(id)$('deleteEvent').onclick=()=>{sheet('删除这份期待？',`<p class="sheet-description">「${escapeHTML(e.title)}」将从倒计日中移除。</p><button class="primary" id="confirmDelete">确认删除</button><button class="text-button" id="cancelDelete">保留</button>`);$('confirmDelete').onclick=()=>{if(command('eventDelete',{id})){closeSheet();toast('倒计日已删除');}};$('cancelDelete').onclick=()=>editEvent(id);};
}
function streak(){const days=historyIndex().days;let offset=days.has(dayOffset(0))?0:-1,count=0;while(days.has(dayOffset(offset))){count++;offset--;}return count;}
function renderStats(){
 const today=localDay(),history=historyIndex(),model=LumiChartsData.summary(data.sessions,today,range,history),sessions=model.sessions,seconds=model.total;
 $('totalHours').textContent=Math.floor(seconds/3600);$('totalMins').textContent=Math.floor(seconds%3600/60);$('totalSessions').textContent=sessions.length;$('totalPomos').textContent=sessions.filter(s=>s.mode==='focus'&&s.completed).length;$('streakDays').textContent=streak(data.sessions);
 if(window.renderCharts)renderCharts(model);
 else {
 const count=range==='30'?30:7,days=Array.from({length:count},(_,i)=>dayOffset(i-count+1)),values=days.map(d=>(history.days.get(d)?.seconds||0)/60),max=Math.max(10,...values);
 $('barChart').style.gap=count>7?'3px':'10px';
 $('barChart').innerHTML=days.map((d,i)=>`<div class="bar-column" role="button" tabindex="0" data-chart-day="${d}" aria-label="${d} ${values[i].toFixed(1)} 分钟，查看当天记录" title="${d}：${values[i].toFixed(1)} 分钟"><span class="bar-value">${count<=7&&values[i]>0?Math.round(values[i]*10)/10:''}</span><div class="bar-fill" style="height:${Math.max(3,values[i]/max*104)}px"></div><label>${count<=7?(['日','一','二','三','四','五','六'][new Date(d+'T12:00:00').getDay()]):i===0||i===count-1||i%7===0?d.slice(8):' '}</label></div>`).join('');
 $('barChart').setAttribute('aria-label',days.map((d,i)=>`${d} ${values[i].toFixed(1)}分钟`).join('，'));
 $('chartCaption').textContent=values.some(v=>v>0)?`这 ${count} 天，平均每天专注 ${(values.reduce((a,b)=>a+b,0)/count).toFixed(1)} 分钟`:'从一次专注开始，点亮你的时间轨迹';
 }
 const colors=['#e6ecdf','#cbdcb6','#9aba7d','#5c865d'];
 $('heatmap').innerHTML=Array.from({length:28},(_,i)=>{const d=dayOffset(i-27),m=(history.days.get(d)?.seconds||0)/60,level=m===0?0:m<30?1:m<90?2:3;return `<button class="heat-cell" style="background:${colors[level]}" data-heat-day="${d}" aria-label="${d}，${m.toFixed(1)} 分钟"></button>`;}).join('');
 const sorted=[...sessions].sort((a,b)=>b.end-a.end);$('recordCount').textContent=`${sorted.length} 条`;
 $('sessionList').innerHTML=sorted.length?sorted.slice(0,recordLimit).map(s=>`<div class="glass session-card"><div class="session-icon">${icon(s.mode==='focus'?'tomato':'clock')}</div><div class="session-detail"><b>${escapeHTML(s.label||'自由专注')}</b><small>${escapeHTML(localDay(s.end))} ${new Date(s.end).toLocaleTimeString('zh-CN',{hour:'2-digit',minute:'2-digit'})} · ${s.mode==='focus'?(s.completed?'完成番茄':'番茄计时'):s.mode==='up'?'正计时':'倒计时'}</small></div><span class="session-length">${s.seconds<60?s.seconds+' 秒':(s.seconds/60).toFixed(s.seconds%60?1:0)+' 分钟'}</span></div>`).join(''):`<div class="empty-state glass"><div class="empty-art">${icon('leaf')}</div><h3>进步，从此刻开始</h3><p>完成一次专注<br>这里就会留下你的第一份积累</p></div>`;
 $('moreRecords').hidden=sorted.length<=recordLimit;
 if(window.renderInsights)renderInsights(sessions);
 if(window.renderRichStats)renderRichStats(sessions);
}
function settings(){
 const s=data.settings;
 sheet('调整自己的节奏',`<p class="sheet-description">时间由你定义，专注由此发生。</p><div class="field-row"><div><label class="form-label" for="settingFocus">番茄 · 分钟</label><input class="field" id="settingFocus" type="number" value="${s.focus}" min="1" max="1440"></div><div><label class="form-label" for="settingGoal">日目标 · 分钟</label><input class="field" id="settingGoal" type="number" value="${s.goal}" min="1" max="1440"></div></div><div class="field-row"><div><label class="form-label" for="settingShort">短休息 · 分钟</label><input class="field" id="settingShort" type="number" value="${s.short}" min="1" max="1440"></div><div><label class="form-label" for="settingLong">长休息 · 分钟</label><input class="field" id="settingLong" type="number" value="${s.long}" min="1" max="1440"></div></div><label class="settings-row"><span><b>完成提示音</b><small>通知声音与振动跟随系统通知设置</small></span><input class="switch" id="settingSound" type="checkbox" ${s.sound?'checked':''}></label><label class="settings-row"><span><b>导航触感</b><small>切换栏目时轻振，跟随系统触感设置</small></span><input class="switch" id="settingNavHaptics" type="checkbox" ${s.navigationHaptics!==false?'checked':''}></label><label class="settings-row"><span><b>专注时保持亮屏</b><small>仅在应用前台计时期间生效</small></span><input class="switch" id="settingAwake" type="checkbox" ${s.keepAwake?'checked':''}></label><p class="error-text" id="formError"></p><button class="primary" id="saveSettings">保存设置</button><button class="text-button" id="startRest">${icon('leaf')} 开始一段休息</button><button class="text-button" id="openAppearance">背景与液态玻璃</button><button class="text-button" id="exportData">导出本地数据备份</button><p class="sheet-footer">番茄 2.9.4<br>离线使用，无广告，无需账号<br>统计按专注结束日期归档，休息不计入统计<br>卸载会清除本地数据，请提前导出备份</p>`);
 $('saveSettings').onclick=()=>{const value={focus:Number($('settingFocus').value),goal:Number($('settingGoal').value),short:Number($('settingShort').value),long:Number($('settingLong').value),sound:$('settingSound').checked,navigationHaptics:$('settingNavHaptics').checked,keepAwake:$('settingAwake').checked};if(['focus','goal','short','long'].some(k=>!Number.isInteger(value[k])||value[k]<1||value[k]>1440)){$('formError').textContent='请输入 1–1440 之间的整数分钟';return;}const old=s.focus;if(command('settings',{value})){if(!active()&&data.timer.mode==='focus'&&data.timer.duration===old*60)configure('focus',value.focus*60);closeSheet();toast('节奏已更新');}};
 $('startRest').onclick=()=>{if(active()){toast('请先结束当前计时');return;}closeSheet();configure('break',data.settings.short*60);navigate('focus');};
 $('openAppearance').onclick=()=>appearance();
 $('exportData').onclick=()=>Native.exportData();
}
function showCompletion(){
 const rest=data.timer.mode==='break';
 sheet(rest?'休息结束':'这一段，你做得很好',`<div class="completion-art">✧</div><h3 class="completion-title">${rest?'重新出发吧':'又积累了一点光'}</h3><p class="completion-description">${rest?'给大脑的留白，也会成为前进的力量。':`完成 ${formatTime(data.timer.duration*1000)} 的专注<br>这段认真，已经记在你的统计里。`}</p><button class="primary" id="completionNext">${rest?'回到专注':'休息 '+data.settings.short+' 分钟'}</button>${rest?'':`<button class="text-button" id="longRest">长休息 ${data.settings.long} 分钟</button>`}<button class="text-button" id="completionClose">稍后再说</button>`);
 $('completionNext').onclick=()=>{closeSheet();configure(rest?'focus':'break',(rest?data.settings.focus:data.settings.short)*60);};if(!rest)$('longRest').onclick=()=>{closeSheet();configure('break',data.settings.long*60);};$('completionClose').onclick=closeSheet;
}
icons();
$('modeTabs').onclick=e=>{const b=e.target.closest('[data-mode]');if(!b)return;if(active()){toast('请先结束当前计时，再切换模式');return;}const mode=b.dataset.mode;configure(mode,mode==='focus'?data.settings.focus*60:mode==='up'?0:10*60);};
$('startButton').onclick=()=>{if(data.timer.status==='running')command('pause');else{if(command('start'))Native.notifyPermission();}};
$('resetButton').onclick=()=>askEnd(true);$('finishButton').onclick=()=>askEnd(false);$('taskButton').onclick=editTask;
$('presets').onclick=e=>{if(active())return;const b=e.target.closest('button');if(!b)return;if(b.dataset.min)configure(data.timer.mode,Number(b.dataset.min)*60);else showCustom();};
$('settingsButton').onclick=settings;$('goalButton').onclick=settings;$('viewStats').onclick=()=>navigate('stats');$('addEvent').onclick=()=>editEvent();
$('rangeTabs').onclick=e=>{const b=e.target.closest('[data-range]');if(!b)return;range=b.dataset.range;recordLimit=10;document.querySelectorAll('[data-range]').forEach(x=>x.classList.toggle('selected',x===b));lastStats='';render();};
$('heatmap').onclick=e=>{const b=e.target.closest('[data-heat-day]');if(b&&window.showDayDetails)showDayDetails(b.dataset.heatDay);};
$('moreRecords').onclick=()=>{recordLimit+=20;lastStats='';render();};
$('closeSheet').onclick=closeSheet;$('sheetOverlay').onclick=e=>{if(e.target===$('sheetOverlay'))closeSheet();};
document.addEventListener('keydown',e=>{if(e.key==='Escape')handleBack();});
function scheduleRefresh(){
 clearTimeout(refreshTimer);
 if(document.hidden)return;
 // Running clocks follow native elapsed time; other views only need the next calendar minute.
 const delay=data?.timer.status==='running'?Math.max(50,1000-(data.timer.elapsedNow??data.timer.elapsed)%1000)+15:data?.audioActive?1000:60000-Date.now()%60000+20;
 refreshTimer=setTimeout(()=>refresh(),delay);
}
document.addEventListener('visibilitychange',()=>{if(document.hidden)clearTimeout(refreshTimer);else refresh();});
refresh();if(data?.timer.status==='completed')seenCompletion=data.timer.completionId;
