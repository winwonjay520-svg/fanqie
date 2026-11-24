'use strict';
(() => {
 const layer=document.createElement('div');layer.className='glass-flow-layer';layer.setAttribute('aria-hidden','true');document.body.appendChild(layer);
 const active=new Set(),byButton=new Map();
 const motionPreference=matchMedia('(prefers-reduced-motion: reduce)');
 function clear(){[...active].forEach(effect=>effect.remove());}
 function play(button,point){
  if(!button||button.disabled||button.getAttribute('aria-disabled')==='true'||!data?.appearance?.liquid||motionPreference.matches)return null;
  const box=button.getBoundingClientRect();
  if(box.width<2||box.height<2||!button.isConnected||typeof button.animate!=='function')return null;
  if(byButton.has(button))byButton.get(button).remove();
  while(active.size>=6)active.values().next().value.remove();
  const style=getComputedStyle(button),shell=document.createElement('div');shell.className='glass-flow';
  Object.assign(shell.style,{left:box.left+'px',top:box.top+'px',width:box.width+'px',height:box.height+'px',borderRadius:style.borderRadius});
  const x=Math.max(0,Math.min(box.width,point?.x??box.width/2)),y=Math.max(0,Math.min(box.height,point?.y??box.height/2));
  const diameter=Math.hypot(box.width,box.height)*2;
  const wave=document.createElement('i');wave.className='glass-flow-wave';
  Object.assign(wave.style,{width:diameter+'px',height:diameter+'px',left:x-diameter/2+'px',top:y-diameter/2+'px'});
  const sheen=document.createElement('i');sheen.className='glass-flow-sheen';
  const spark=document.createElement('i');spark.className='glass-flow-spark';shell.append(wave,sheen,spark);layer.appendChild(shell);
  const effects=[];let removed=false;
  const effect={button,node:shell,remove(){if(removed)return;removed=true;effects.forEach(a=>a.cancel());shell.remove();active.delete(effect);if(byButton.get(button)===effect)byButton.delete(button);}};
  active.add(effect);byButton.set(button,effect);
  const cheap=data.appearance.material==='eco',duration=cheap?420:820;
  function animate(node,frames,options){const animation=node.animate(frames,options);effects.push(animation);animation.finished.catch(()=>{});return animation;}
  const main=animate(wave,[{transform:'scale(.03,.05)',opacity:.15,offset:0},{transform:'scale(.38,.25)',opacity:.95,offset:.3},{transform:'scale(.8,.61)',opacity:.48,offset:.64},{transform:'scale(1.16,.9)',opacity:0,offset:1}],{duration,easing:'cubic-bezier(.16,.7,.25,1)',fill:'forwards'});
  animate(sheen,[{transform:'translateX(-52%) rotate(-7deg)',opacity:0},{transform:'translateX(-13%) rotate(-3deg)',opacity:.8,offset:.36},{transform:'translateX(52%) rotate(5deg)',opacity:0}],{duration,easing:'cubic-bezier(.22,.65,.3,1)',fill:'forwards'});
  animate(spark,[{opacity:0},{opacity:.9,offset:.28},{opacity:0}],{duration,easing:'ease-out',fill:'forwards'});
  if(!cheap&&!button.closest('.bottom-nav'))animate(button,[{transform:'scale(1)'},{transform:'scale(.976,.96)',offset:.24},{transform:'scale(1.013,1.01)',offset:.65},{transform:'scale(1)'}],{duration:430,easing:'cubic-bezier(.22,.7,.3,1)'});
  main.finished.then(()=>effect.remove(),()=>effect.remove());return effect;
 }
 let pressed=null;
 document.addEventListener('pointerdown',event=>{
  if(event.isPrimary===false||event.button>0)return;
  const button=event.target.closest('button,[role="button"]');if(!button)return;
  const box=button.getBoundingClientRect(),effect=play(button,{x:event.clientX-box.left,y:event.clientY-box.top});
  pressed=effect?{id:event.pointerId,x:event.clientX,y:event.clientY,effect}:null;
 },{capture:true,passive:true});
 document.addEventListener('pointermove',event=>{if(pressed?.id===event.pointerId&&Math.hypot(event.clientX-pressed.x,event.clientY-pressed.y)>14){pressed.effect.remove();pressed=null;}},{passive:true});
 document.addEventListener('pointercancel',event=>{if(pressed?.id===event.pointerId){pressed.effect.remove();pressed=null;}},{passive:true});
 document.addEventListener('pointerup',()=>{pressed=null;},{passive:true});
 document.addEventListener('click',event=>{
  if(event.detail===0){const button=event.target.closest('button,[role="button"]');if(button)play(button);}
  // A click may close a sheet or replace a whole page. Remove its old
  // reflections before they are drawn over controls on the new screen.
  if(active.size)requestAnimationFrame(()=>{
   for(const effect of active){
    const box=effect.node.getBoundingClientRect();
    const hit=document.elementFromPoint(box.left+box.width/2,box.top+box.height/2);
    if(!hit||!effect.button.contains(hit))effect.remove();
   }
  });
 },{capture:true,passive:true});
 window.addEventListener('scroll',clear,{capture:true,passive:true});
 window.addEventListener('resize',clear,{passive:true});
 document.addEventListener('visibilitychange',()=>{if(document.hidden){pressed=null;clear();}});
 if(motionPreference.addEventListener)motionPreference.addEventListener('change',clear);
 window.glassMotion={play,clear,get activeCount(){return active.size;}};
})();
