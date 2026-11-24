'use strict';
// Selection is a draft. Only the explicit start/switch action touches the native clock.
let todoView='list',todoDraft=null,todoKey='',todoTaskLimit=30;
const todoHome=document.createElement('section');todoHome.id='todoHome';
const todoSetup=document.createElement('section');todoSetup.id='todoSetup';todoSetup.hidden=true;
$('focusPage').prepend(todoSetup);$('focusPage').prepend(todoHome);
const todoPlus=document.createElement('button');todoPlus.id='addTodoButton';todoPlus.className='icon-button glass';todoPlus.setAttribute('aria-label','添加待办');todoPlus.innerHTML=icon('plus');document.querySelector('.header-actions').prepend(todoPlus);
document.querySelector('[data-page=focus]>span:last-child').textContent='待办';
function setTodoView(view){
 todoView=view;document.body.dataset.todoView=view;document.body.classList.toggle('todo-selecting',page==='focus'&&view==='setup');todoHome.hidden=view!=='list';todoSetup.hidden=view!=='setup';todoPlus.hidden=page!=='focus'||view!=='list';
 if(view==='list')renderTodoHome();if(view==='setup')renderTodoSetup();window.scrollTo(0,0);
}
function todoCover(task,index){const id=task.cover&&task.cover!=='auto'?task.cover:['ocean','mist','sunset','night'][index%4];return id==='photo'&&!data.appearance.hasImage?'mist':id;}
function freeFocusOngoing(){return active()&&data.timer.mode==='up'&&!data.timer.taskId;}
function freeFocusCard(){return `<button class="glass todo-free-focus" id="todoQuick" aria-label="自由专注，不限时正向计时"><span class="todo-free-icon" aria-hidden="true">∞</span><span class="todo-free-copy"><b>自由专注</b><small id="todoFreeCaption">不设时限，想专注多久就多久</small></span><span class="todo-free-action"><span id="todoFreeAction">开始</span>${icon('chevron')}</span></button>`;}
function openFreeFocus(){
 if(freeFocusOngoing()){closeSheet();if(page!=='focus')navigate('focus');setTodoView('list');setFocusScene(true);return;}
 openTodoSetup(null,true);
}
function renderTodoHome(){
 if(!data||todoView!=='list'||page!=='focus')return;
 const key=[data._workspaceRevision,data._historyRevision,data.timer.taskId,data.timer.status,data.timer.mode,data.appearance.imageVersion,data.settings.focus,todoTaskLimit,localDay()].join('|');
 if(todoKey!==key){
  todoKey=key;const tasks=data.tasks.filter(t=>!t.done).sort((a,b)=>a.created-b.created),totals=taskTotals();
  todoHome.innerHTML=`<div class="todo-heading"><div><p class="eyebrow">给重要的事，一段专注</p><h1>待办</h1></div><button id="todoManage" class="text-button">管理清单 ↗</button></div><button id="todoResume" class="glass todo-resume" hidden><span><b id="todoRunningLabel"></b><small>计时仍在继续，点击返回</small></span><strong id="todoRunningTime"></strong></button>${freeFocusCard()}<div class="todo-card-list">${tasks.length?tasks.slice(0,todoTaskLimit).map((t,i)=>{const cover=todoCover(t,i),progress=totals.get(t.id)||{seconds:0,pomos:0},mode=t.timerMode||'focus',duration=t.duration||data.settings.focus*60,background=cover==='photo'?`url(&quot;https://lumi.local/wallpaper.jpg?v=${encodeURIComponent(data.appearance.imageVersion)}&quot;)`:wallpapers[cover].image;return `<article class="todo-card" data-auto-cover="${!t.cover||t.cover==='auto'}" data-cover="${cover}" style="--todo-cover:${background}"><div><button class="todo-card-name" data-todo-open="${escapeHTML(t.id)}">${escapeHTML(t.title)}</button><span class="todo-card-duration">${mode==='up'?'正计时':Math.floor(duration/60)+(duration%60?' 分 '+duration%60+' 秒':' 分钟')}${t.tag?' · '+escapeHTML(t.tag):''}</span><small>${escapeHTML(durationLabel(progress.seconds))}已积累</small></div><button class="todo-card-start" data-todo-open="${escapeHTML(t.id)}">${active()&&data.timer.taskId===t.id?'继续':'开始'} ${icon('play')}</button><button class="todo-card-edit" data-todo-edit="${escapeHTML(t.id)}" aria-label="编辑 ${escapeHTML(t.title)}">···</button></article>`;}).join(''):`<div class="todo-empty glass"><span>${icon('tomato')}</span><h2>今天想专注什么？</h2><p>点击右上角「＋」添加待办，再选择计时方式和时长。</p><button class="primary" id="todoFirst">添加第一个待办</button></div>`}</div><button class="text-button" id="todoWorkflow">专注与休息流程 ↗</button>${tasks.length>todoTaskLimit?'<button class="text-button" id="todoMore">显示更多待办</button>':''}`;
  $('todoManage').onclick=()=>navigate('workspace');$('todoResume').onclick=()=>setFocusScene(true);if($('todoFirst'))$('todoFirst').onclick=()=>openTodoSetup();$('todoQuick').onclick=openFreeFocus;$('todoWorkflow').onclick=showWorkflow;if($('todoMore'))$('todoMore').onclick=()=>{todoTaskLimit+=30;todoKey='';renderTodoHome();};
 }
 const ongoing=active(),freeOngoing=freeFocusOngoing();$('todoResume').hidden=!ongoing||freeOngoing;
 $('todoFreeAction').textContent=freeOngoing?'返回计时':'开始';$('todoFreeCaption').textContent=freeOngoing?'已专注 '+$('timerText').textContent+' · '+(data.timer.status==='paused'?'已暂停':'计时中'):'不设时限，想专注多久就多久';
 if(ongoing){$('todoRunningLabel').textContent=data.timer.mode==='break'?'休息中':data.timer.label||'自由专注';$('todoRunningTime').textContent=$('timerText').textContent;$('todoResume').querySelector('small').textContent=data.timer.status==='paused'?'已暂停，点击返回计时':'计时仍在继续，点击返回';}
}
function openTodoSetup(id,free=false,force=false){
 if(free&&freeFocusOngoing()&&!force){openFreeFocus();return;}
 if(id&&active()&&data.timer.taskId===id&&!force){setFocusScene(true);return;}
 const task=data.tasks.find(t=>t.id===id);
 todoDraft=task?{...task}:{title:'',tag:'',priority:2,estimate:1,due:'',subtasks:[],timerMode:'focus',duration:data.settings.focus*60,cover:'auto'};
 todoDraft.duration=free?1:task?.duration||data.settings.focus*60;todoDraft.timerMode=free?'up':task?.timerMode||'focus';todoDraft.free=free;
 closeSheet();if(focusSceneOpen)setFocusScene(false);if(page!=='focus')navigate('focus');setTodoView('setup');
}
function renderTodoSetup(){
 if(!todoDraft||todoView!=='setup')return;const d=todoDraft,mode=d.free?'up':d.timerMode||'focus';
 const modeOptions=d.free?'<div class="glass todo-free-mode"><span aria-hidden="true">∞</span><div><b>自由专注 · 正向计时</b><small>无需设定时长，结束时保存实际专注时间</small></div></div>':`<div class="segmented glass todo-mode-tabs" role="group" aria-label="待办计时方式">${[['focus','番茄钟'],['up','正计时'],['down','倒计时']].map(([id,label])=>`<button data-todo-mode="${id}" class="${mode===id?'selected':''}" aria-pressed="${mode===id}">${label}</button>`).join('')}</div>`;
 todoSetup.innerHTML=`<div class="todo-setup-heading"><button class="text-button" id="todoSetupBack">← 待办列表</button><span>${d.free?'自由专注':'选择计时'}</span></div><div class="heading-row"><h1>${d.free?'不设时限，<br>按照自己的节奏。':'把此刻，<br>留给重要的事。'}</h1><div class="little-orbit"><span>✦</span></div></div>${modeOptions}<article class="glass todo-setup-panel" data-free="${d.free}"><div class="todo-setup-top"><label class="todo-name-field"><span class="status-dot"></span><input id="todoSetupName" maxlength="80" placeholder="${d.free?'专注事项（可不填）':'输入待办名称'}" aria-label="${d.free?'专注事项（可选）':'待办名称'}" value="${escapeHTML(d.title)}"></label><button class="text-button" id="todoAdvanced" ${d.free?'hidden':''}>更多设置 ↗</button></div><div class="todo-preview-dial"><svg class="todo-preview-ring" viewBox="0 0 280 280" aria-hidden="true"><circle cx="140" cy="140" r="126"/></svg><div><small>${mode==='up'?'MAKE TIME COUNT':mode==='down'?'COUNTDOWN':'FOCUS TIME'}</small><strong id="todoPreviewTime" class="${mode!=='up'&&formatTime(d.duration*1000).length>5?'long':''}">${mode==='up'?'00:00':formatTime(d.duration*1000)}</strong><p>${d.free?'想专注多久，就专注多久':'选好节奏，再开始这一段'}</p></div></div><div class="todo-duration-presets">${mode==='up'?'<span>正向累计 · 手动结束后保存</span>':[25,45,60].map(m=>`<button data-todo-minutes="${m}" class="${d.duration===m*60?'selected':''}">${m} 分钟</button>`).join('')+'<button id="todoCustom" aria-label="自定义待办时长">'+icon('sliders')+'</button>'}</div><p class="todo-switch-note" id="todoSwitchNote" ${active()?'':'hidden'}></p><button class="primary" id="todoStartSelected">${icon('play')} ${todoStartLabel(d)}</button><button class="text-button" id="todoKeepTimer" ${active()&&!d.free&&data.timer.mode!=='break'?'':'hidden'}>沿用当前计时，切换待办</button><p class="timer-footnote">${icon('leaf')} 不必着急，按照自己的节奏来</p><button class="text-button" id="todoSaveOnly" ${d.free?'hidden':''}>仅保存待办，稍后开始</button></article>`;
 $('todoSetupName').oninput=e=>{todoDraft.title=e.target.value;};$('todoSetupBack').onclick=()=>setTodoView('list');
 todoSetup.querySelectorAll('[data-todo-mode]').forEach(b=>b.onclick=()=>{todoDraft.timerMode=b.dataset.todoMode;renderTodoSetup();});
 todoSetup.querySelectorAll('[data-todo-minutes]').forEach(b=>b.onclick=()=>{todoDraft.duration=Number(b.dataset.todoMinutes)*60;renderTodoSetup();});
 if($('todoCustom'))$('todoCustom').onclick=showTodoDuration;
 $('todoAdvanced').onclick=showTodoAdvanced;$('todoStartSelected').onclick=()=>saveTodoDraft('start');$('todoKeepTimer').onclick=()=>saveTodoDraft('switch');$('todoSaveOnly').onclick=()=>saveTodoDraft('save');updateTodoSetupTimer();
}
function todoStartLabel(d){return d.free?(active()?'保存当前，开始自由专注':'开始自由专注'):(active()?'保存当前，按新设置开始':'开始专注');}
function updateTodoSetupTimer(){
 if(todoView!=='setup'||!$('todoSwitchNote'))return;
 const ongoing=active();$('todoSwitchNote').hidden=!ongoing;$('todoKeepTimer').hidden=!ongoing||todoDraft.free||data.timer.mode==='break';const label=todoStartLabel(todoDraft);if($('todoStartSelected').textContent.trim()!==label)$('todoStartSelected').innerHTML=icon('play')+' '+label;
 if(ongoing)$('todoSwitchNote').textContent='当前「'+(data.timer.label||'自由专注')+'」'+(data.timer.status==='paused'?'已暂停':'计时中')+' '+$('timerText').textContent+'。按新设置开始会先保存原待办时间。';
}
function showTodoDuration(){
 const n=todoDraft.duration;sheet('自定义待办时长',`<div class="field-row"><div><label class="form-label" for="todoMinutes">分钟</label><input class="field" id="todoMinutes" type="number" min="0" max="1440" value="${Math.floor(n/60)}"></div><div><label class="form-label" for="todoSeconds">秒</label><input class="field" id="todoSeconds" type="number" min="0" max="59" value="${n%60}"></div></div><p id="todoDurationError" class="error-text"></p><button class="primary" id="todoSaveDuration">使用这个时长</button>`);
 $('todoSaveDuration').onclick=()=>{const m=Number($('todoMinutes').value),s=Number($('todoSeconds').value);if(!Number.isInteger(m)||!Number.isInteger(s)||m<0||s<0||s>59||m*60+s<1||m*60+s>86400){$('todoDurationError').textContent='请输入 1 秒至 24 小时';return;}todoDraft.duration=m*60+s;closeSheet();renderTodoSetup();};
}
function showTodoAdvanced(){
 const d=todoDraft;sheet('待办设置',`<label class="form-label" for="todoTag">标签</label><input class="field" id="todoTag" maxlength="24" value="${escapeHTML(d.tag||'')}"><label class="form-label" for="todoPriority">优先级</label><select class="field" id="todoPriority"><option value="1">优先处理</option><option value="2">按计划来</option><option value="3">慢慢完成</option></select><label class="form-label" for="todoCover">卡片封面</label><select class="field" id="todoCover"><option value="auto">自动</option>${['mist','ocean','sunset','night'].map(id=>`<option value="${id}">${wallpapers[id].name}</option>`).join('')}${data.appearance.hasImage?'<option value="photo">当前背景照片</option>':''}</select><button class="primary" id="todoSaveAdvanced">完成</button>`);
 $('todoPriority').value=d.priority||2;$('todoCover').value=d.cover||'auto';$('todoSaveAdvanced').onclick=()=>{d.tag=$('todoTag').value.trim();d.priority=Number($('todoPriority').value);d.cover=$('todoCover').value;closeSheet();};
}
function saveTodoDraft(action){
 const d=todoDraft;if(!d)return;if(d.free&&action!=='start')return;d.title=$('todoSetupName').value.trim();if(!d.title&&!d.free){toast('请先输入待办名称');$('todoSetupName').focus();return;}d.title=d.title||'自由专注';const before=new Set(data.tasks.map(t=>t.id)),value={...d};delete value.free;
 let id=d.id||'';
 if(!d.free){if(!command('taskSave',{value}))return;id=d.id||data.tasks.find(t=>!before.has(t.id))?.id;if(!id)return;d.id=id;}
 if(action==='save'){todoKey='';setTodoView('list');toast('待办已保存');return;}
 const ok=action==='switch'?command('switchTask',{taskId:id,label:d.title}):command('startTask',{taskId:id,label:d.title,mode:d.free?'up':d.timerMode,duration:d.free?1:d.duration});
 if(ok){setTodoView('list');setFocusScene(true);if(action==='start')Native.notifyPermission();}
}
todoHome.onclick=e=>{const b=e.target.closest('[data-todo-open],[data-todo-edit]');if(!b)return;openTodoSetup(b.dataset.todoOpen||b.dataset.todoEdit,false,!!b.dataset.todoEdit);};
todoPlus.onclick=()=>openTodoSetup();
const renderBeforeTodos=render;render=function(){renderBeforeTodos();document.body.classList.toggle('todo-selecting',page==='focus'&&todoView==='setup');todoPlus.hidden=page!=='focus'||todoView!=='list';if(page==='focus'){renderTodoHome();updateTodoSetupTimer();}};
const backBeforeTodos=handleBack;handleBack=function(){if(!focusSceneOpen&&!largeClockOpen&&$('sheetOverlay').hidden&&page==='focus'&&todoView==='setup'){setTodoView('list');return true;}return backBeforeTodos();};
setTodoView('list');
if(active())setFocusScene(true);
