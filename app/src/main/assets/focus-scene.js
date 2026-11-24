'use strict';
let focusSceneOpen=false,sceneFullscreen=true,sceneQuiet=false,sceneLastFocus,sceneAppearanceKey='',sceneQuoteIndex=0;
let sceneSizeFrame=0,sceneDiameter=0;
const sceneQuotes=['此刻，只做好这一件事。','慢慢来，每一步都算数。','给重要的事，完整的一刻。','处处留心皆学问。'];
paths.sceneBack='m14 5-7 7 7 7M7 12h14';paths.sceneExpand='M8 3H3v5m13-5h5v5M3 16v5h5m13-5v5h-5';paths.sceneCollapse='M3 8h5V3m8 0v5h5M8 21v-5H3m18 0h-5v5';paths.sceneDown='m6 9 6 6 6-6';paths.sceneMusic='M9 18V5l11-2v13M9 5l11-2M9 16c-6-2-8 5-3 5 2 0 3-1 3-3m11-4c-6-2-8 5-3 5 2 0 3-1 3-3';
const sceneLegacyImmersive=setImmersive;
window.isFocusSceneOpen=()=>focusSceneOpen;
function sceneIcon(id,name){const node=$(id);if(node.dataset.sceneIcon!==name){node.innerHTML=icon(name);node.dataset.sceneIcon=name;}}
function sceneText(id,value){const node=$(id);if(node.textContent!==value)node.textContent=value;}
function obscureFocusScene(hidden){if(hidden){$('focusScene').setAttribute('aria-hidden','true');$('focusScene').setAttribute('inert','');}else{$('focusScene').removeAttribute('aria-hidden');$('focusScene').removeAttribute('inert');}}
function setFocusScene(enabled){
 enabled=!!enabled;if(enabled===focusSceneOpen)return;
 if(enabled){
  if(largeClockOpen)setLargeClock(false);if(page!=='focus')navigate('focus');closeSheet();sceneLastFocus=document.activeElement;
  sceneLegacyImmersive(true);focusSceneOpen=true;sceneFullscreen=true;$('focusScene').hidden=false;obscureFocusScene(false);document.body.classList.add('focus-scene-open');
  sceneIcon('sceneFullscreen','sceneCollapse');$('sceneFullscreen').setAttribute('aria-pressed','true');$('sceneFullscreen').setAttribute('aria-label','显示系统栏');
  document.querySelector('.timer-orb').setAttribute('aria-expanded','true');setSceneQuiet(false);renderFocusScene();Native.focusSceneMode(true,true);sizeFocusScene();$('sceneExit').focus({preventScroll:true});
 }else{
  if(largeClockOpen)setLargeClock(false);focusSceneOpen=false;$('focusScene').hidden=true;obscureFocusScene(false);document.body.classList.remove('focus-scene-open');
  document.querySelector('.timer-orb').setAttribute('aria-expanded','false');sceneLegacyImmersive(false);Native.focusSceneMode(false,false);$('immerseButton').textContent='专注详情 ↗';$('immerseButton').setAttribute('aria-label','打开专注详情页');
  if(sceneLastFocus?.isConnected)sceneLastFocus.focus({preventScroll:true});
 }
}
setImmersive=enabled=>setFocusScene(enabled);
function setSceneQuiet(enabled){
 sceneQuiet=!!enabled;$('focusScene').classList.toggle('scene-quiet',sceneQuiet);
 for(const node of [document.querySelector('.scene-toolbar-tools'),$('sceneQuote'),document.querySelector('.scene-dock'),$('sceneQuiet')])node.hidden=sceneQuiet;
 $('sceneReveal').hidden=!sceneQuiet;
 sizeFocusScene();
}
function sizeFocusScene(){
 if(!focusSceneOpen||document.hidden)return;cancelAnimationFrame(sceneSizeFrame);sceneSizeFrame=requestAnimationFrame(()=>{
  if(!focusSceneOpen||document.hidden)return;
  const box=document.querySelector('.scene-clock-area').getBoundingClientRect(),short=matchMedia('(max-height:600px) and (orientation:landscape)').matches;
  const limit=short?340:matchMedia('(min-width:700px)').matches?680:440;
  const diameter=Math.max(80,Math.floor(Math.min(box.width,box.height,limit)));
  if(diameter!==sceneDiameter){sceneDiameter=diameter;const dial=document.querySelector('.scene-dial');dial.style.width=diameter+'px';dial.style.height=diameter+'px';}
 });
}
if(typeof ResizeObserver!=='undefined')new ResizeObserver(sizeFocusScene).observe(document.querySelector('.scene-clock-area'));
else window.addEventListener('resize',sizeFocusScene,{passive:true});
document.addEventListener('visibilitychange',()=>{if(!document.hidden&&focusSceneOpen)sizeFocusScene();});
const sceneSegments={a:'12,2 36,2 41,7 36,12 12,12 7,7',b:'36,10 41,5 46,10 46,34 41,39 36,34',c:'36,48 41,43 46,48 46,72 41,77 36,72',d:'12,74 36,74 41,79 36,84 12,84 7,79',e:'2,48 7,43 12,48 12,72 7,77 2,72',f:'2,10 7,5 12,10 12,34 7,39 2,34',g:'12,38 36,38 41,43 36,48 12,48 7,43'};
const sceneNumbers=['abcdef','bc','abdeg','abcdg','bcfg','acdfg','acdefg','abc','abcdefg','abcdfg'];
let sceneDigitShape='',sceneDigitValue='',sceneDigitSlots=[];
function renderSceneDigits(value){
 if(value===sceneDigitValue)return;const shape=value.replace(/\d/g,'0');
 if(shape!==sceneDigitShape){
  const ns='http://www.w3.org/2000/svg',svg=document.createElementNS(ns,'svg');let x=0;sceneDigitSlots=[];
  for(const char of value){
   const group=document.createElementNS(ns,'g');group.setAttribute('transform',`translate(${x} 0)`);
   if(char===':'){for(const y of [28,58]){const dot=document.createElementNS(ns,'circle');dot.setAttribute('cx','9');dot.setAttribute('cy',String(y));dot.setAttribute('r','3.3');group.append(dot);}x+=22;}
   else{const pieces={};for(const [key,points] of Object.entries(sceneSegments)){const piece=document.createElementNS(ns,'polygon');piece.setAttribute('points',points);group.append(piece);pieces[key]=piece;}sceneDigitSlots.push(pieces);x+=52;}
   svg.append(group);
  }
  svg.setAttribute('viewBox',`0 0 ${x-4} 86`);$('sceneDigits').replaceChildren(svg);sceneDigitShape=shape;
 }
 const digits=value.replace(/:/g,'');sceneDigitSlots.forEach((pieces,i)=>{const on=sceneNumbers[Number(digits[i])];for(const [key,piece] of Object.entries(pieces))piece.classList.toggle('on',on.includes(key));});sceneDigitValue=value;
}
function renderFocusScene(){
 if(!focusSceneOpen||!data)return;const t=data.timer,a=data.appearance,scene=$('focusScene');
 const photo=a.preset==='photo'&&a.hasImage,solid=['white','black'].includes(a.preset);
 const look=[a.preset,a.tone,a.hasImage,a.imageVersion,a.photoColor,a.dim,a.blur,a.liquid,a.material].join('|');
 if(look!==sceneAppearanceKey){
  sceneAppearanceKey=look;scene.style.setProperty('--scene-image',photo?`url("https://lumi.local/wallpaper.jpg?v=${encodeURIComponent(a.imageVersion)}")`:(wallpapers[a.preset]||wallpapers.white).image);
  scene.dataset.background=solid?'solid':photo?'photo':'gradient';scene.dataset.tone=photo?'dark':a.tone;
  scene.style.setProperty('--scene-dim',Math.max(.30,Math.min(.6,a.dim/100)));scene.dataset.material=a.material||'clear';scene.dataset.liquid=String(a.liquid);
 }
 const elapsed=t.elapsedNow||0,value=formatTime(t.mode==='up'?Math.floor(elapsed/1000)*1000:Math.max(0,t.duration*1000-elapsed));
 sceneText('sceneTime',value);renderSceneDigits(value);sceneText('sceneQuoteText',sceneQuotes[sceneQuoteIndex]);
 sceneText('sceneMode',({focus:'番茄专注',up:'正计时',down:'倒计时',break:'休息时间'})[t.mode]);
 const task=t.mode==='break'?'休息一下':t.label||'自由专注';sceneText('sceneTask',task);$('sceneTask').disabled=t.mode==='break';$('sceneTask').setAttribute('aria-label','当前事项：'+task+(t.mode==='break'?'':'，切换专注待办'));
 let status=({idle:'准备好了，就开始吧',running:'进行中',paused:'已暂停，随时可以继续',completed:t.mode==='break'?'休息结束':'这一段专注已完成'})[t.status];
 if(data.flowState)status+=' · 第 '+data.flowState.round+' / '+data.flowState.rounds+' 轮';
 if(t.mode==='up')status+=' · 结束后保存实际时长';sceneText('sceneStatus',status);
 const ratio=t.mode==='up'?1:Math.max(0,Math.min(1,1-elapsed/(Math.max(1,t.duration)*1000)));$('sceneArc').style.strokeDashoffset=String(791.681*(1-ratio));
 const running=t.status==='running';sceneIcon('sceneToggle',running?'pause':'play');$('sceneToggle').setAttribute('aria-label',$('startLabel').textContent);$('sceneToggle').setAttribute('aria-pressed',String(running));
 $('sceneReset').disabled=t.status==='idle';$('sceneFinish').disabled=!active();$('sceneSound').setAttribute('aria-label',data.audioActive?'环境声音正在播放，调整声音':'环境声音');
}
const focusFeaturesBeforeScene=renderFocusFeatures;
renderFocusFeatures=function(){focusFeaturesBeforeScene();const entry=document.querySelector('.timer-orb'),label='当前计时 '+$('timerText').textContent+'，打开专注详情页';if(entry.getAttribute('aria-label')!==label)entry.setAttribute('aria-label',label);if(focusSceneOpen)renderFocusScene();};
const appearanceBeforeScene=applyAppearance;
applyAppearance=function(value){appearanceBeforeScene(value);if(focusSceneOpen)renderFocusScene();};
const sheetBeforeScene=sheet,closeSheetBeforeScene=closeSheet;
sheet=function(title,body){sheetBeforeScene(title,body);if(focusSceneOpen)obscureFocusScene(true);};
closeSheet=function(){const visible=!$('sheetOverlay').hidden;closeSheetBeforeScene();if(focusSceneOpen&&!largeClockOpen){obscureFocusScene(false);if(visible)$('sceneToggle').focus({preventScroll:true});}};
const clockBeforeScene=setLargeClock;
setLargeClock=function(enabled){clockBeforeScene(enabled);if(focusSceneOpen){if(enabled)obscureFocusScene(true);else if($('sheetOverlay').hidden){obscureFocusScene(false);$('sceneClock').focus({preventScroll:true});}}};
const backBeforeScene=handleBack;
handleBack=function(){if(largeClockOpen||!$('sheetOverlay').hidden)return backBeforeScene();if(focusSceneOpen){setFocusScene(false);return true;}return backBeforeScene();};
const completionBeforeScene=showCompletion;
showCompletion=function(){if(largeClockOpen)setLargeClock(false);if(focusSceneOpen){setSceneQuiet(false);renderFocusScene();}completionBeforeScene();};
$('immerseButton').textContent='专注详情 ↗';$('immerseButton').setAttribute('aria-label','打开专注详情页');$('immerseButton').onclick=()=>setFocusScene(true);
const sceneEntry=document.querySelector('.timer-orb');sceneEntry.onclick=()=>setFocusScene(true);sceneEntry.onkeydown=e=>{if(e.key==='Enter'||e.key===' '){e.preventDefault();setFocusScene(true);}};
$('sceneExit').onclick=()=>setFocusScene(false);$('sceneToggle').onclick=()=>$('startButton').click();$('sceneReset').onclick=()=>askEnd(true);$('sceneFinish').onclick=()=>askEnd(false);
$('sceneSound').onclick=showSounds;$('sceneTask').onclick=()=>editTask();$('sceneAppearance').onclick=appearance;
$('sceneClock').onclick=()=>{if(command('clockPreferences',{style:'digital',face:'timer'}))setLargeClock(true);};
$('sceneFullscreen').onclick=()=>{sceneFullscreen=!sceneFullscreen;Native.focusSceneMode(true,sceneFullscreen);sceneIcon('sceneFullscreen',sceneFullscreen?'sceneCollapse':'sceneExpand');$('sceneFullscreen').setAttribute('aria-pressed',String(sceneFullscreen));$('sceneFullscreen').setAttribute('aria-label',sceneFullscreen?'显示系统栏':'隐藏系统栏，铺满当前窗口');};
$('sceneQuiet').onclick=()=>{setSceneQuiet(true);$('sceneReveal').focus({preventScroll:true});};$('sceneReveal').onclick=()=>{setSceneQuiet(false);$('sceneToggle').focus({preventScroll:true});};
$('sceneQuote').onclick=()=>{sceneQuoteIndex=(sceneQuoteIndex+1)%sceneQuotes.length;sceneText('sceneQuoteText',sceneQuotes[sceneQuoteIndex]);};
for(const [id,name] of Object.entries({sceneBackIcon:'sceneBack',sceneAppearance:'image',sceneClock:'timer',sceneFullscreen:'sceneCollapse',sceneSound:'sceneMusic',sceneReset:'reset',sceneFinish:'stop',sceneQuiet:'sceneDown'}))sceneIcon(id,name);
document.addEventListener('keydown',e=>{
 if(!focusSceneOpen)return;
 if(!$('sheetOverlay').hidden||largeClockOpen)return;
 if(e.key==='Tab'){
  const controls=[...$('focusScene').querySelectorAll('button:not(:disabled)')].filter(el=>el.getClientRects().length&&getComputedStyle(el).visibility!=='hidden');
  if(e.shiftKey&&document.activeElement===controls[0]){e.preventDefault();controls[controls.length-1].focus();}
  else if(!e.shiftKey&&document.activeElement===controls[controls.length-1]){e.preventDefault();controls[0].focus();}
 }
});
