'use strict';
// The persisted "digital" style now uses split-flap cards; old backups stay compatible.
const flipClock=(()=>{
 let previous='',slots=[],layout='',motion=window.matchMedia('(prefers-reduced-motion: reduce)');
 function settle(slot){
  clearTimeout(slot.timeout);slot.timeout=undefined;
  slot.el.classList.remove('flipping');
  slot.bottom.textContent=slot.next;
 }
 function stop(){slots.forEach(settle);previous='';}
 function render(value,animate=true){
  const host=$('clockDigits'),shape=value.replace(/\d/g,'0');
  if(shape!==layout){
   stop();host.replaceChildren();slots=[];layout=shape;
   for(const char of value){
    if(char===':'){const colon=document.createElement('span');colon.className='flip-colon';host.append(colon);continue;}
    const el=document.createElement('span');el.className='flip-digit';
    el.innerHTML='<span class="flip-half flip-top"><span></span></span><span class="flip-half flip-bottom"><span></span></span><span class="flip-half flip-top flip-out"><span></span></span><span class="flip-half flip-bottom flip-in"><span></span></span><i class="flip-seam"></i>';
    host.append(el);const parts=el.querySelectorAll('.flip-half>span');
    slots.push({el,top:parts[0],bottom:parts[1],out:parts[2],incoming:parts[3],next:char});
   }
   host.dataset.digits=String(slots.length);
  }
  if(previous===value)return;
  const digits=value.replace(/:/g,''),old=previous.replace(/:/g,''),moving=[];
  slots.forEach((slot,i)=>{
   if(previous&&digits[i]===old[i])return;
   settle(slot);slot.next=digits[i];slot.top.textContent=slot.next;
   if(animate&&previous&&!motion.matches&&!document.hidden){
    slot.bottom.textContent=old[i];slot.out.textContent=old[i];slot.incoming.textContent=slot.next;
    moving.push(slot);
   }else slot.bottom.textContent=slot.next;
  });
  if(moving.length){
   // Batch layout once, including when an unusually fast update interrupts a flap.
   void host.offsetWidth;
   moving.forEach(slot=>{slot.el.classList.add('flipping');slot.timeout=setTimeout(()=>settle(slot),480);});
  }
  previous=value;
 }
 const reduce=()=>{if(motion.matches)stop();};
 if(motion.addEventListener)motion.addEventListener('change',reduce);else motion.addListener(reduce);
 return {render,stop};
})();
