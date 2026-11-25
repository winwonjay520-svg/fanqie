'use strict';
(() => {
 const active=new Set(),byButton=new Map();
 const motionPreference=matchMedia('(prefers-reduced-motion: reduce)');
 function clear(){[...active].forEach(effect=>effect.remove());}
 function play(button,held=false){
  if(!button||button.disabled||button.getAttribute('aria-disabled')==='true'||!data?.appearance?.liquid||motionPreference.matches)return null;
  const box=button.getBoundingClientRect();
  if(data.appearance.material==="eco"||button.closest(".bottom-nav")||!("scale" in button.style))return null;
  if(box.width<2||box.height<2||!button.isConnected||typeof button.animate!=='function')return null;
  if(byButton.has(button))byButton.get(button).remove();
  while(active.size>=6)active.values().next().value.remove();
  const effects=[];
  let removed=false,compression=null,pullX=0,pullY=0,pullFrame=0,strength=0;
  const effect={button,release,pull,remove(){
   if(removed)return;
   removed=true;cancelAnimationFrame(pullFrame);resetPull();
   effects.forEach(animation=>animation.cancel());
   active.delete(effect);byButton.delete(button);
  }};
  active.add(effect);byButton.set(button,effect);
  function animate(node,frames,options){const animation=node.animate(frames,options);effects.push(animation);animation.finished.catch(()=>{});return animation;}
   // Cap travel in pixels on wide tablet buttons.
   const wide=Math.min(.09,8/box.width),deep=Math.min(.14,8/box.height);
   compression=animate(button,[{scale:'1 1'},{scale:(1+wide)+' '+(1-deep)}],
                        {duration:100,easing:'cubic-bezier(.18,.78,.22,1)',fill:'forwards'});
  function resetPull(){
   button.style.removeProperty('--pull-offset');
   button.style.removeProperty('--pull-shape');
   button.classList.remove('glass-pulled');
  }
  function pull(dx,dy,pressure){
   const distance=Math.hypot(dx,dy);
   if(removed||(distance<3&&!pullFrame&&!button.classList.contains('glass-pulled')))return;
   strength=Math.min(1,distance/85+Math.max(0,pressure-.5)*.35);
   const travel=12*(1-Math.exp(-distance/38));
   pullX=travel*dx/(distance||1);pullY=travel*dy/(distance||1);
   if(!pullFrame)pullFrame=requestAnimationFrame(()=>{
    pullFrame=0;
    compression?.cancel();
    button.classList.add('glass-pulled');
    const offset=pullX+'px '+pullY+'px';
    button.style.setProperty('--pull-offset',offset);
    const axis=(Math.abs(pullX)-Math.abs(pullY))/12;
    const sx=1+axis*Math.min(.14,12/box.width), sy=1-axis*Math.min(.14,12/box.height);
    button.style.setProperty('--pull-shape',sx+' '+sy);
   });
  }
  function release(){
   if(removed||!compression)return;
   cancelAnimationFrame(pullFrame);
   const [sx=1,sy=sx]=getComputedStyle(button).scale.split(' ').map(v=>parseFloat(v)||1);
   const heldPull=button.classList.contains('glass-pulled');
   compression.cancel();compression=null;
   resetPull();
   const wide=heldPull?sx-1:Math.max(sx-1,Math.min(.07,6/box.width));
   const deep=heldPull?sy-1:Math.min(sy-1,-Math.min(.11,7/box.height));
   const rebound=animate(button,liquidSpringFrames(k=>({
    scale:(1+wide*k)+' '+(1+deep*k),translate:pullX*k+'px '+pullY*k+'px'
   })),{duration:440+130*strength,easing:'linear'});
   rebound.finished.then(effect.remove,()=>{});
  }
  if(!held)compression.finished.then(release,()=>{});
  return effect;
 }
 let pressed=null;
 document.addEventListener('pointerdown',event=>{
  if(event.isPrimary===false||event.button>0)return;
  const button=event.target.closest('button,[role="button"]');if(!button)return;
  if(pressed)pressed.effect.remove();
  const effect=play(button,true);
  pressed=effect?{id:event.pointerId,x:event.clientX,y:event.clientY,effect}:null;
 },{capture:true,passive:true});
 document.addEventListener('pointermove',event=>{
  if(pressed?.id===event.pointerId)pressed.effect.pull(event.clientX-pressed.x,event.clientY-pressed.y,event.pressure);
 },{passive:true});
 document.addEventListener('pointercancel',event=>{if(pressed?.id===event.pointerId){pressed.effect.remove();pressed=null;}},{passive:true});
 window.addEventListener('pointerup',event=>{
  if(pressed?.id===event.pointerId){pressed.effect.release();pressed=null;}
 },{capture:true,passive:true});
 document.addEventListener('click',event=>{
  if(event.detail===0){const button=event.target.closest('button,[role="button"]');if(button)play(button);}
  // A click may close a sheet or replace a whole page. Remove its old
  // animations when their controls are no longer visible.
  if(active.size)requestAnimationFrame(()=>{
   for(const effect of active){
    const box=effect.button.getBoundingClientRect();
    const hit=document.elementFromPoint(box.left+box.width/2,box.top+box.height/2);
    if(!hit||!effect.button.contains(hit))effect.remove();
   }
  });
 },{capture:true,passive:true});
 window.addEventListener('scroll',clear,{capture:true,passive:true});
 window.addEventListener('resize',clear,{passive:true});
 window.addEventListener('blur',clear);
 document.addEventListener('visibilitychange',()=>{if(document.hidden){pressed=null;clear();}});
 if(motionPreference.addEventListener)motionPreference.addEventListener('change',clear);
 window.glassMotion={play,clear,get activeCount(){return active.size;}};
})();
