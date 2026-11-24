'use strict';
let focusFeatureKey='',largeClockOpen=false,largeClockTimeout,mixerSaveTimeout;
const moodNames={great:'状态很好',calm:'平静投入',tired:'有些疲惫',distracted:'容易分心'};
const moodEmoji={great:'☀',calm:'◌',tired:'☾',distracted:'≈'};
function renderFocusFeatures(){
 const flow=data.flowState,m=data.mixer,key=JSON.stringify([flow,m,data.audioActive,data.lastCompletion,data.timer.status]);
 if(key!==focusFeatureKey){focusFeatureKey=key;
  $('flowBanner').hidden=!flow;
  if(flow){const phase={focus:'专注',short:'短休息',long:'长休息'}[flow.phase];$('cycleIndicator').textContent=`第 ${flow.round} / ${flow.rounds} 轮 · ${phase}`;$('flowBanner').innerHTML=`<div><small>我的专注流程</small><b>第 ${flow.round} / ${flow.rounds} 轮 · ${phase}</b><span>${data.timer.status==='paused'?'已暂停，随时可以继续':flow.waiting?'准备好后，点击开始继续':flow.auto?'完成后自动进入下一阶段':'完成后由你决定何时继续'}</span></div><button id="stopFlowBanner">结束流程</button>`;$('stopFlowBanner').onclick=confirmStopFlow;}
  $('soundButton').innerHTML=`${data.audioActive?'声景播放中':m?.playing&&m.followTimer?'声音随计时':'环境声音'} <span>♫</span>`;
  const latest=data.lastCompletion?.sessionId;$('lastReviewButton').hidden=!latest;
  $('lastReviewButton').onclick=()=>showSessionReview(latest);
 }
 if(largeClockOpen)updateLargeClock();
}
function showWorkflow(){
 const f=data.workflow;
 sheet('找到自己的专注节奏',`<p class="sheet-description">专注与休息交替，最后一轮后安排长休息。默认由你手动开始下一阶段。</p><div class="flow-preview"><span>专注</span><i>→</i><span>短休息</span><i>↻</i><span>长休息</span></div><div class="field-row"><div><label class="form-label" for="flowFocus">专注 · 分钟</label><input class="field" id="flowFocus" type="number" min="1" max="1440" value="${f.focus/60}"></div><div><label class="form-label" for="flowRounds">轮次</label><input class="field" id="flowRounds" type="number" min="1" max="12" value="${f.rounds}"></div></div><div class="field-row"><div><label class="form-label" for="flowShort">短休息 · 分钟</label><input class="field" id="flowShort" type="number" min="1" max="1440" value="${f.short/60}"></div><div><label class="form-label" for="flowLong">长休息 · 分钟</label><input class="field" id="flowLong" type="number" min="1" max="1440" value="${f.long/60}"></div></div><label class="settings-row"><span><b>自动进入下一阶段</b><small>切到后台也会衔接；暂停后可手动继续</small></span><input class="switch" id="flowAuto" type="checkbox" ${f.auto?'checked':''}></label><p class="error-text" id="formError"></p><button class="primary" id="saveWorkflow">保存流程</button><button class="text-button" id="runWorkflow" ${active()?'disabled':''}>开始这套流程 ↗</button>${data.flowState?'<button class="text-button danger" id="stopWorkflow">结束当前流程</button>':''}`);
 function save(){const mins=['flowFocus','flowShort','flowLong'].map(id=>Number($(id).value)),rounds=Number($('flowRounds').value);if(mins.some(n=>!Number.isInteger(n)||n<1||n>1440)||!Number.isInteger(rounds)||rounds<1||rounds>12){$('formError').textContent='时间为 1–1440 分钟，轮次为 1–12';return false;}return command('workflowSave',{value:{focus:mins[0]*60,short:mins[1]*60,long:mins[2]*60,rounds,auto:$('flowAuto').checked}});}
 $('saveWorkflow').onclick=()=>{if(save()){closeSheet();toast('你的节奏已保存');}};
 $('runWorkflow').onclick=()=>{if(save()&&command('flowStart')){closeSheet();navigate('focus');Native.notifyPermission();}};
 if($('stopWorkflow'))$('stopWorkflow').onclick=confirmStopFlow;
}
function confirmStopFlow(){
 sheet('结束这套专注流程？','<p class="sheet-description">已经完成的记录会保留。当前这一段，可以选择保存，或放弃后结束。</p><button class="primary" id="saveAndStopFlow">保存本段并结束</button><button class="text-button danger" id="discardFlow">放弃本段并结束</button><button class="text-button" id="continueFlow">继续流程</button>');
 $('saveAndStopFlow').onclick=()=>{if(active()){if(!command('finish'))return;}if(command('flowStop')){closeSheet();toast('流程已结束');}};$('discardFlow').onclick=()=>{if(command('flowStop')){closeSheet();toast('流程已结束');}};$('continueFlow').onclick=closeSheet;
}
function showSounds(){
 const m=data.mixer;
 sheet('给专注一点环境声音',`<p class="sheet-description">三种离线生成的环境声，可叠加混合。连接耳机后，也可以只留一点轻轻的背景。</p><div class="soundscapes">${[['rain','雨落窗边','细密雨声与柔和底噪','☂'],['forest','林间微风','轻风与偶尔的合成鸟鸣','♧'],['cafe','午后咖啡馆','低声环境底噪与柔和节奏','♨']].map(([id,title,detail,symbol])=>`<div class="soundscape"><div class="soundscape-heading"><span>${symbol}</span><div><b>${title}</b><small>${detail}</small></div><output id="sound-${id}-value">${m[id]}%</output></div><input id="sound-${id}" type="range" min="0" max="100" value="${m[id]}" aria-label="${title}音量"></div>`).join('')}</div><label class="settings-row"><span><b>跟随专注启停</b><small>开始专注播放，暂停、休息或结束时停止</small></span><input class="switch" id="soundFollow" type="checkbox" ${m.followTimer?'checked':''}></label><p id="soundStatus" class="sound-status">${m.playing?(m.followTimer?'已开启，随专注开始播放':'已开启独立播放'):'声音已停止'}</p><button class="primary" id="soundMaster">${m.playing?'停止声音':'开启声音'}</button><button class="text-button" id="soundPreview">立即试听，不跟随计时</button><p class="sheet-footer">环境声为应用生成的合成声景，可离线使用。</p>`);
 function save(playing=data.mixer.playing){clearTimeout(mixerSaveTimeout);const value={rain:Number($('sound-rain').value),forest:Number($('sound-forest').value),cafe:Number($('sound-cafe').value),playing,followTimer:$('soundFollow').checked};if(playing&&value.rain+value.forest+value.cafe===0){toast('先给一种声音调高音量');return false;}const ok=command('mixerSave',{value});if(ok){$('soundMaster').textContent=playing?'停止声音':'开启声音';$('soundStatus').textContent=playing?(value.followTimer?'已开启，随专注开始播放':'已开启独立播放'):'声音已停止';setTimeout(refresh,350);}return ok;}
 for(const id of ['rain','forest','cafe'])$('sound-'+id).oninput=()=>{$('sound-'+id+'-value').textContent=$('sound-'+id).value+'%';clearTimeout(mixerSaveTimeout);mixerSaveTimeout=setTimeout(()=>{if($('soundMaster'))save();},180);};
 $('soundFollow').onchange=()=>save();$('soundMaster').onclick=()=>{const enabling=!data.mixer.playing;if(save(enabling)&&enabling)Native.notifyPermission();};$('soundPreview').onclick=()=>{$('soundFollow').checked=false;if(save(true))Native.notifyPermission();};
}
function showSessionReview(id){
 const s=data.sessions.find(s=>s.id===id);if(!s){toast('这条记录已不存在');return;}let mood=s.mood||'';
 sheet('给这一段留一点感受',`<div class="review-heading"><small>${escapeHTML(s.label||'自由专注')}</small><b>${escapeHTML(durationLabel(s.seconds))}</b></div><p class="form-label">刚才的状态怎么样？</p><div class="mood-options">${Object.entries(moodNames).map(([key,name])=>`<button data-mood="${key}" class="${mood===key?'selected':''}" aria-pressed="${mood===key}"><span>${moodEmoji[key]}</span>${name}</button>`).join('')}</div><label class="form-label" for="reviewNote">一句话记录（可选）</label><textarea class="field" id="reviewNote" rows="4" maxlength="240" placeholder="哪一步比较顺利？下次想怎样开始？"></textarea><button class="primary" id="saveReview">保存感受</button><button class="text-button" id="clearMood">清除心情选择</button>`);$('reviewNote').value=s.note||'';
 function choose(next){mood=next;document.querySelectorAll('[data-mood]').forEach(b=>{b.classList.toggle('selected',b.dataset.mood===mood);b.setAttribute('aria-pressed',String(b.dataset.mood===mood));});}
 document.querySelectorAll('[data-mood]').forEach(b=>b.onclick=()=>choose(b.dataset.mood));$('clearMood').onclick=()=>choose('');$('saveReview').onclick=()=>{if(command('sessionReview',{id,mood,note:$('reviewNote').value})){closeSheet();toast('感受已收好');}};
}
const showCompletionWithCycles=showCompletion;
showCompletion=function(){showCompletionWithCycles();const id=data.lastCompletion?.sessionId;if(id){const button=document.createElement('button');button.className='text-button';button.id='completionReview';button.textContent='记录本次心情 ↗';button.onclick=()=>showSessionReview(id);$('sheetBody').append(button);}};
function setLargeClock(enabled){
 largeClockOpen=!!enabled;clearTimeout(largeClockTimeout);$('largeClock').hidden=!enabled;document.body.classList.toggle('clock-mode',!!enabled);
 if(enabled){closeSheet();updateLargeClock();tickLargeClock();}else flipClock.stop();Native.clockMode(!!enabled);
}
function tickLargeClock(){clearTimeout(largeClockTimeout);if(!largeClockOpen||document.hidden)return;updateLargeClock();largeClockTimeout=setTimeout(tickLargeClock,1000-Date.now()%1000+20);}
function updateLargeClock(){
 if(!largeClockOpen)return;const pref=data.preferences,now=new Date(),clock=pref.clockFace==='clock',host=$('largeClock');
 const changed=host.dataset.style!==pref.clockStyle||host.dataset.face!==pref.clockFace;
 if(changed)flipClock.stop();host.dataset.style=pref.clockStyle;host.dataset.face=pref.clockFace;
 const value=clock?[now.getHours(),now.getMinutes(),now.getSeconds()].map(n=>String(n).padStart(2,'0')).join(':'):$('timerText').textContent;
 if($('clockTime').textContent!==value)$('clockTime').textContent=value;
 if(pref.clockStyle==='digital')flipClock.render(value,!changed);else flipClock.stop();
 $('clockStyleButton').textContent=pref.clockStyle==='digital'?'圆环样式':'翻页样式';
 $('clockDate').textContent=localDay().replaceAll('-',' / ')+' · '+['星期日','星期一','星期二','星期三','星期四','星期五','星期六'][now.getDay()];$('clockTask').textContent=clock?'此刻，就是生活':data.timer.mode==='break'?'休息一下':data.timer.label||'自由专注';$('clockToggle').textContent=$('startLabel').textContent;$('clockToggle').hidden=clock;
 document.querySelectorAll('[data-clock-face]').forEach(b=>b.classList.toggle('selected',b.dataset.clockFace===pref.clockFace));
}
const handleBackBeforeClock=handleBack;handleBack=function(){if(largeClockOpen){setLargeClock(false);return true;}return handleBackBeforeClock();};
$('flowButton').onclick=showWorkflow;$('soundButton').onclick=showSounds;$('largeClockButton').onclick=()=>setLargeClock(true);$('clockExit').onclick=()=>setLargeClock(false);$('clockToggle').onclick=()=>$('startButton').click();
$('clockStyleButton').onclick=()=>command('clockPreferences',{style:data.preferences.clockStyle==='digital'?'ring':'digital',face:data.preferences.clockFace});document.querySelectorAll('[data-clock-face]').forEach(b=>b.onclick=()=>command('clockPreferences',{style:data.preferences.clockStyle,face:b.dataset.clockFace}));
document.addEventListener('visibilitychange',()=>{if(document.hidden){clearTimeout(largeClockTimeout);flipClock.stop();}else if(largeClockOpen)tickLargeClock();});
