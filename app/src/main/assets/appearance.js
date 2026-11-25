'use strict';
paths.image='M4 3h16a1 1 0 0 1 1 1v16a1 1 0 0 1-1 1H4a1 1 0 0 1-1-1V4a1 1 0 0 1 1-1ZM3 17l6-6 5 5 3-3 4 4M16 6a2 2 0 1 0 0 4 2 2 0 0 0 0-4';
const wallpapers={
 white:{name:'纯白',image:'linear-gradient(#fff,#fff)',tone:'light'},
 black:{name:'纯黑',image:'linear-gradient(#000,#000)',tone:'dark'},
 mist:{name:'晨雾',image:'radial-gradient(ellipse at 85% 20%,#e2edf988,transparent 62%),radial-gradient(ellipse at 0% 95%,#e6edf466,transparent 60%),linear-gradient(145deg,#ffffff,#f6f9fc)',tone:'light'},
 ocean:{name:'潮汐',image:'radial-gradient(ellipse at 75% 18%,#8be1d0aa,transparent 48%),radial-gradient(ellipse at 0% 68%,#396f9ccc,transparent 55%),linear-gradient(150deg,#3f8798,#223e6c 65%,#528184)',tone:'dark'},
 sunset:{name:'落日',image:'radial-gradient(ellipse at 72% 40%,#f2c37dee,transparent 38%),radial-gradient(ellipse at 15% 80%,#633e70ee,transparent 60%),linear-gradient(140deg,#d58889,#a26785 55%,#473859)',tone:'dark'},
 night:{name:'星夜',image:'radial-gradient(ellipse at 95% 7%,#799ea3aa,transparent 50%),radial-gradient(ellipse at 10% 65%,#62519188,transparent 60%),linear-gradient(150deg,#182333,#233a4c 65%,#101d2b)',tone:'dark'}
};
let appearanceKey='',appearanceOpen=false,wallpaperBusy=false,appearanceWriteTimer,appearanceDraft;
function applyAppearance(value){
 if(appearanceDraft){value={...value,...appearanceDraft};if(data)data.appearance=value;}
 const a=value||{preset:'white',tone:'light',blur:0,dim:28,glass:64,liquid:true,position:50};
 const key=JSON.stringify(a);if(key===appearanceKey)return;appearanceKey=key;
 const root=document.documentElement;
 const photo=a.preset==='photo'&&a.hasImage;
 const image=photo?`url("https://lumi.local/wallpaper.jpg?v=${encodeURIComponent(a.imageVersion)}")`:(wallpapers[a.preset]||wallpapers.white).image;
 const tint=a.photoPalette?.[a.tone==='dark'?'dark':'light'];
 for(const [key,value] of Object.entries({'--photo-tint':photo?tint?.tint:undefined,'--photo-accent':photo?tint?.accent:undefined,'--photo-scene-tint':photo?a.photoPalette?.dark?.tint:undefined,'--photo-scene-accent':photo?a.photoPalette?.dark?.accent:undefined})){
  if(value)document.body.style.setProperty(key,value);else document.body.style.removeProperty(key);
 }
 document.querySelectorAll('.photo-swatch').forEach(el=>el.style.backgroundImage=`url("https://lumi.local/wallpaper.jpg?v=${encodeURIComponent(a.imageVersion)}")`);
 root.style.setProperty('--wallpaper-image',image);
 root.style.setProperty('--wallpaper-blur',a.blur+'px');
 root.style.setProperty('--wallpaper-dim',a.dim/100);
 root.style.setProperty('--glass-alpha',a.glass/100);
 root.style.setProperty('--wallpaper-position',a.position+'%');
 document.body.classList.toggle('theme-dark',a.tone==='dark');
 document.body.classList.toggle('liquid',a.liquid);
 document.body.classList.toggle('photo-background',photo);
 document.body.classList.toggle('palette-theme',['mist','ocean','sunset','night'].includes(a.preset));
 document.body.dataset.wallpaper=a.preset;
 document.body.dataset.material=a.material||'clear';
 document.body.dataset.legibility=String(a.legibility!==false);
 document.querySelectorAll('button[data-material]').forEach(b=>{b.classList.toggle('selected',b.dataset.material===(a.material||'clear'));b.setAttribute('aria-pressed',String(b.dataset.material===(a.material||'clear')));});
 document.querySelectorAll('[data-preset]').forEach(b=>{b.classList.toggle('selected',b.dataset.preset===a.preset);b.setAttribute('aria-pressed',String(b.dataset.preset===a.preset));});
 document.querySelectorAll('button[data-tone]').forEach(b=>{b.classList.toggle('selected',b.dataset.tone===a.tone);b.setAttribute('aria-pressed',String(b.dataset.tone===a.tone));});
 if($('photoPreset'))$('photoPreset').hidden=!a.hasImage;
 if($('removePhoto'))$('removePhoto').hidden=!a.hasImage;
 if($('positionControl'))$('positionControl').hidden=!photo;
 if($('materialHint')){
  const material=a.material||'clear',solid=['white','black'].includes(a.preset);
  $('materialHint').textContent=!a.liquid?'打开后可使用透明玻璃材质。':{
   clear:'清晰透出底色，边缘保留细微高光。',soft:'柔化背后的细节，让文字更突出。',eco:'使用静态材质，减少模糊渲染。'
  }[material];
  $('backgroundTuning').hidden=solid;
  $('glassCoverage').hidden=solid||!a.liquid||material==='eco';
  $('photoTools').hidden=!a.hasImage;
 }

}
function updateAppearance(patch){
 try {
  const next=JSON.parse(Native.appearance(JSON.stringify(patch)));
  if(next.error){toast(next.error);return false;}
  data.appearance=next;applyAppearance(next);return true;
 }catch(e){toast('外观保存失败，请重试');return false;}
}
function saveSlider(key,value){
 // Preview every movement; persist once the slider settles, avoiding disk writes per frame.
 appearanceDraft={...appearanceDraft,[key]:Number(value)};
 data.appearance={...data.appearance,...appearanceDraft};applyAppearance(data.appearance);
 $('value-'+key).textContent=value+(key==='blur'?' px':'%');
 clearTimeout(appearanceWriteTimer);
 appearanceWriteTimer=setTimeout(()=>flushAppearance(),180);
}
function flushAppearance(){
 clearTimeout(appearanceWriteTimer);appearanceWriteTimer=undefined;
 if(!data?.appearance||!appearanceDraft)return;
 const patch=appearanceDraft;appearanceDraft=undefined;updateAppearance(patch);
}
function appearance(){
 flushAppearance();appearanceOpen=true;const a=data.appearance;
 const currentSection=$('appearanceMaterial') && !$('appearanceMaterial').hidden?'material':'background';
 const presetButtons=Object.entries(wallpapers).filter(([id])=>!['white','black'].includes(id)).map(([id,preset])=>
  `<button data-preset="${id}" aria-label="${preset.name}背景"><span style="background:${preset.image}"></span><small>${preset.name}</small></button>`
 ).join('');
 sheet('属于你的专注空间',`
  <div class="wallpaper-preview appearance-sample" role="img" aria-label="当前背景和卡片材质预览">
   <div class="preview-background"></div><div class="preview-shade"></div>
   <div class="glass sample-card" aria-hidden="true"><span class="sample-clock">${icon('timer')}</span><div><span>专注计时</span><strong>25:00</strong></div></div>
  </div>
  <div class="appearance-sections" role="group" aria-label="外观设置分类">
   <button data-appearance-section="background" aria-controls="appearanceBackground">背景</button>
   <button data-appearance-section="material" aria-controls="appearanceMaterial">玻璃材质</button>
  </div>
  <section id="appearanceBackground">
   <div class="solid-mode-options" role="group" aria-label="默认纯色模式">
    <button data-preset="white"><span class="mode-sample mode-white"></span><span><b>浅色模式</b><small>纯白</small></span></button>
    <button data-preset="black"><span class="mode-sample mode-black"></span><span><b>深色模式</b><small>纯黑</small></span></button>
   </div>
   <div class="wallpaper-presets">${presetButtons}<button id="photoPreset" data-preset="photo" ${a.hasImage?'':'hidden'} aria-label="我的图片背景"><span class="photo-swatch"></span><small>我的图片</small></button></div>
   <button class="primary photo-upload" id="choosePhoto">${icon('image')}<span>${wallpaperBusy?'正在处理图片…':'从相册选择背景'}</span></button>
   <details id="backgroundTuning" class="appearance-details"><summary>背景微调</summary>
    ${sliderControl('blur','背景模糊',a.blur,0,24,' px')}${sliderControl('dim','背景遮罩',a.dim,0,75,'%')}
    <div id="positionControl">${sliderControl('position','图片上下位置',a.position,0,100,'%')}</div>
   </details>
  </section>
  <section id="appearanceMaterial" hidden>
   <div class="appearance-title"><h3>液态玻璃</h3><label class="switch-label"><input type="checkbox" class="switch" id="liquidToggle" aria-label="启用液态玻璃" ${a.liquid?'checked':''}></label></div>
   <div class="material-options" role="group" aria-label="玻璃材质"><button data-material="clear">清透</button><button data-material="soft">柔雾</button><button data-material="eco">轻量</button></div>
   <p class="picker-note" id="materialHint"></p>
   <div id="glassCoverage">${sliderControl('glass','玻璃遮盖度',a.glass,0,80,'%')}</div>
   <div class="tone-options" role="group" aria-label="文字风格"><button data-tone="light">深色文字</button><button data-tone="dark">浅色文字</button></div>
   <div id="photoTools" class="photo-look-tools" ${a.hasImage?'':'hidden'}>
    <label class="settings-row"><span><b>照片文字保护</b><small>降低照片细节对文字和图表的干扰</small></span><input class="switch" id="photoLegibility" type="checkbox" ${a.legibility!==false?'checked':''}></label>
    <button class="text-button" id="photoGlassLook">一键照片液态玻璃</button>
   </div>
  </section>
  <button class="primary" id="previewDone">完成</button>
  <div class="appearance-footer"><button class="text-button" id="resetAppearance">恢复默认外观</button><button class="text-button danger" id="removePhoto" ${a.hasImage?'':'hidden'}>移除背景图片</button></div>`);
 function showSection(section){
  $('appearanceBackground').hidden=section!=='background';
  $('appearanceMaterial').hidden=section!=='material';
  document.querySelectorAll('[data-appearance-section]').forEach(button=>button.setAttribute('aria-pressed',String(button.dataset.appearanceSection===section)));
 }
 document.querySelectorAll('[data-appearance-section]').forEach(button=>button.onclick=()=>showSection(button.dataset.appearanceSection));
 showSection(currentSection);
 $('sheetOverlay').classList.add('appearance-overlay');
 $('choosePhoto').disabled=wallpaperBusy;
 $('removePhoto').disabled=wallpaperBusy;
 document.querySelectorAll('button[data-material]').forEach(b=>b.onclick=()=>{flushAppearance();updateAppearance({material:b.dataset.material});});
 $('choosePhoto').onclick=()=>{flushAppearance();Native.chooseWallpaper();};
 $('photoGlassLook').onclick=applyPhotoGlass;
 $('photoLegibility').onchange=()=>{flushAppearance();updateAppearance({legibility:$('photoLegibility').checked});};
 document.querySelectorAll('[data-preset]').forEach(b=>b.onclick=()=>{flushAppearance();const id=b.dataset.preset;updateAppearance({preset:id,tone:id==='photo'?'light':wallpapers[id].tone});});
 document.querySelectorAll('button[data-tone]').forEach(b=>b.onclick=()=>{flushAppearance();const tone=b.dataset.tone,solid=['white','black'].includes(data.appearance.preset);updateAppearance(solid?{preset:tone==='dark'?'black':'white',tone}:{tone});});
 $('liquidToggle').onchange=e=>{flushAppearance();updateAppearance({liquid:e.target.checked});};
 document.querySelectorAll('[data-appearance-range]').forEach(input=>{input.oninput=()=>saveSlider(input.dataset.appearanceRange,input.value);input.onchange=flushAppearance;});
 $('previewDone').onclick=closeSheet;
 $('resetAppearance').onclick=()=>{flushAppearance();if(updateAppearance({preset:'white',tone:'light',blur:0,dim:28,glass:64,position:50,liquid:true,material:'clear'})){appearance();toast('已恢复默认外观，图片仍可重新选择');}};
 $('removePhoto').onclick=()=>{flushAppearance();sheet('移除这张背景？','<p class="sheet-description">将删除保存在番茄里的图片副本，手机相册原图不受影响。恢复当前深浅模式的纯色背景。</p><button class="primary" id="confirmRemovePhoto">移除并恢复纯色背景</button><button class="text-button" id="cancelRemovePhoto">保留这张图片</button>');$('confirmRemovePhoto').onclick=()=>{try{const next=JSON.parse(Native.removeWallpaper());if(next.error){toast(next.error);return;}data.appearance=next;applyAppearance(next);appearance();toast('已移除背景图片');}catch(e){toast('移除失败，请重试');}};$('cancelRemovePhoto').onclick=appearance;};
 appearanceKey='';applyAppearance(a);
}
function sliderControl(key,label,value,min,max,unit){return `<label class="range-label" for="range-${key}"><span>${label}</span><output id="value-${key}">${value}${unit}</output></label><input id="range-${key}" data-appearance-range="${key}" class="appearance-range" type="range" min="${min}" max="${max}" step="1" value="${value}">`;}
function wallpaperLoading(){wallpaperBusy=true;if($('choosePhoto')){$('choosePhoto').disabled=true;$('choosePhoto').innerHTML=icon('image')+'<span>正在处理图片…</span>';}}
function wallpaperPicked(success,error){wallpaperBusy=false;refresh();if(appearanceOpen&&!$('sheetOverlay').hidden)appearance();if(error)toast(error);else if(success)toast('图片背景已保存，可以调整玻璃效果');}
const originalSheet=sheet,originalCloseSheet=closeSheet;
sheet=function(title,body){$('sheetOverlay').classList.remove('appearance-overlay');originalSheet(title,body);};
closeSheet=function(){if(appearanceOpen)flushAppearance();appearanceOpen=false;if($('appearanceMaterial'))$('appearanceMaterial').hidden=true;$('sheetOverlay').classList.remove('appearance-overlay');originalCloseSheet();};
// Keep callback lookups dynamic so all routes also flush an unfinished slider edit.
$('closeSheet').onclick=()=>closeSheet();
$('appearanceButton').innerHTML=icon('image');$('appearanceButton').onclick=appearance;
window.addEventListener('pagehide',()=>{if(appearanceWriteTimer)flushAppearance();});
document.addEventListener('visibilitychange',()=>{if(document.hidden&&appearanceWriteTimer)flushAppearance();});
if(data)applyAppearance(data.appearance);
function applyPhotoGlass(){
 flushAppearance();if(!data.appearance.hasImage){Native.chooseWallpaper();return;}
 if(updateAppearance({preset:'photo',tone:'light',liquid:true,material:'clear',glass:56,dim:20,blur:0,legibility:true})){
  if(appearanceOpen)appearance();toast('已应用照片液态玻璃');
 }
}
