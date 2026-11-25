'use strict';
(() => {
 const nav = document.querySelector('.bottom-nav');
 const capsule = nav.querySelector('.nav-capsule');
 const items = Array.from(nav.querySelectorAll('button[data-page]'));
 const glyphs = items.map(item => item.querySelector('[data-icon]'));
 // Keep each silhouette steady while the part that identifies it moves.
 const details = {
  timer: ['M9 2h6M12 6a8 8 0 1 0 0 16 8 8 0 0 0 0-16M18 5l2 2', 'M12 10v4l3 2'],
  check: ['', 'M5 12l4 4L19 6'],
  calendar: ['M5 5h14a2 2 0 0 1 2 2v12a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V7a2 2 0 0 1 2-2ZM7 2v6M17 2v6M3 11h18', 'M8 15h2m4 0h2m-8 3h2'],
  chart: ['M4 3v17h17', 'M8 16v-5', 'M13 16V7', 'M18 16V9']
 };
 glyphs.forEach(glyph => {
  const [outline,...strokes] = details[glyph.dataset.icon];
  const svg = glyph.querySelector('svg');
  svg.innerHTML = (outline ? '<path d="'+outline+'"/>' : '') +
   strokes.map(d=>'<path class="nav-stroke" pathLength="1" d="'+d+'"/>').join('');
 });
 const reducedMotion = matchMedia('(prefers-reduced-motion: reduce)');
 let gesture = null;
 let frame = 0;
 let suppressClick = false;
 let rebound = null;
 let glyphBounce = null;
 let strokesInMotion = [];

 function stopStrokes() {
  strokesInMotion.forEach(animation=>animation.cancel());
  strokesInMotion = [];
 }
 function drawIcon(item) {
  stopStrokes();
  if (!elasticEnabled()) return;
  const glyph = glyphs[items.indexOf(item)];
  const name = glyph.dataset.icon;
  glyph.querySelectorAll('.nav-stroke').forEach((stroke,index)=>{
   if (!stroke.animate) return;
   let frames;
   if (name === 'timer') {
    frames = [{transform:'rotate(-64deg)'},{transform:'rotate(9deg)',offset:.72},{transform:'rotate(0deg)'}];
   } else if (name === 'chart') {
    frames = [{transform:'scaleY(.24)'},{transform:'scaleY(1.1)',offset:.7},{transform:'scaleY(1)'}];
   } else {
    frames = [{strokeDashoffset:'.86',opacity:.6},{strokeDashoffset:'0',opacity:1}];
   }
   strokesInMotion.push(stroke.animate(frames,{duration:320,delay:index*35,easing:'cubic-bezier(.22,.68,.29,1)',fill:'backwards'}));
  });
 }

 function liftIcons(position, vertical) {
  glyphs.forEach((glyph,index) => {
   const offset = position-index;
   const proximity = Math.max(0,1-Math.abs(offset));
   const lift = proximity*proximity*(3-2*proximity);
   const follow = 5*lift*offset;
   const x = vertical ? 3*lift : follow;
   const y = vertical ? follow : -3*lift;
   glyph.style.transform = 'translate('+x+'px,'+y+'px) scale('+(1+.2*lift)+')';
  });
 }
 function bounceIcon(item,vertical) {
  glyphBounce?.cancel();
  if (!elasticEnabled()) return;
  const glyph = glyphs[items.indexOf(item)];
  if (!glyph?.animate) return;
  glyphBounce = glyph.animate(liquidSpringFrames(k=>({
   transform:'translate('+(vertical?3*k:0)+'px,'+(vertical?0:-3*k)+'px) scale('+(1+.2*k)+')'
  })),{duration:400});
 }

 function elasticEnabled() {
  return document.body.classList.contains('liquid') && document.body.dataset.material !== 'eco' && !reducedMotion.matches;
 }
 function stopRebound() {
  rebound?.cancel();
  rebound = null;
  nav.classList.remove('nav-releasing');
 }
 function settle(shape, duration) {
  stopRebound();
  if (!elasticEnabled() || !capsule.animate) return;
  nav.classList.add('nav-releasing');
  const animation = capsule.animate(liquidSpringFrames(shape), {duration,easing:'linear'});
  rebound = animation;
  animation.onfinish = () => { if (rebound === animation) stopRebound(); };
 }

 function tick() {
  if (data?.settings?.navigationHaptics !== false) window.Native?.navigationHaptic?.();
 }

 function select(item, withFeedback) {
  if (withFeedback && item.dataset.page !== page) drawIcon(item);
  if (withFeedback && item.dataset.page !== page) tick();
  navigate(item.dataset.page);
  if (withFeedback && 'scale' in capsule.style) {
   settle(k => ({scale:(1+.085*k)+' '+(1-.13*k)}),460);
   bounceIcon(item,getComputedStyle(nav).flexDirection === 'column');
  }
 }

 function clearGesture() {
  const old = gesture;
  gesture = null;
  cancelAnimationFrame(frame);
  frame = 0;
  nav.classList.remove('nav-tracking');
  capsule.style.removeProperty('--nav-drag-index');
  capsule.style.removeProperty('--lens-stretch');
  capsule.style.removeProperty('--lens-compress');
  glyphs.forEach(glyph=>glyph.style.removeProperty('transform'));
  if (old) {
   items[old.target].classList.remove('nav-preview');
   if (nav.hasPointerCapture(old.id)) nav.releasePointerCapture(old.id);
  }
 }

 function cancel() {
  if (!gesture) return;
  stopStrokes();
  suppressClick = true;
  clearGesture();
 }

 nav.addEventListener('pointerdown', event => {
  if (event.isPrimary === false) { cancel(); return; }
  if (event.button !== 0) return;
  cancel();
  stopRebound();
  glyphBounce?.cancel();
  stopStrokes();
  suppressClick = false;
  const pressedItem = event.target.closest('button[data-page]');
  if (!pressedItem) return;
  const vertical = getComputedStyle(nav).flexDirection === 'column';
  const first = items[0].getBoundingClientRect();
  const last = items[items.length - 1].getBoundingClientRect();
  const center = box => vertical ? box.top + box.height / 2 : box.left + box.width / 2;
  gesture = {
   id: event.pointerId, pressedItem, vertical, bounds: nav.getBoundingClientRect(),
   origin: center(first), step: (center(last) - center(first)) / (items.length - 1),
   x: event.clientX, y: event.clientY, dragging: false,
   lastPoint: vertical ? event.clientY : event.clientX, lastTime: event.timeStamp,
   elastic: elasticEnabled(),
   target: items.findIndex(item => item.classList.contains('selected'))
  };
 });

 function track(event) {
  const g = gesture;
  const point = g.vertical ? event.clientY : event.clientX;
  const position = Math.max(0, Math.min(items.length - 1, (point - g.origin) / g.step));
  const next = Math.round(position);
  if (g.elastic && g.lastPoint !== point) {
   const distance = point - g.lastPoint;
   const speed = Math.abs(distance) / Math.max(8, event.timeStamp - g.lastTime);
   const travel = Math.abs(point-(g.vertical?g.y:g.x)) / Math.max(1,g.step);
   g.stretch = .035 + .14*(1-Math.exp(-travel)) + .075*Math.min(1,speed/2);
   g.lastPoint = point;
   g.lastTime = event.timeStamp;
  }
  g.position = position;
  if (next !== g.target) {
   items[g.target].classList.remove('nav-preview');
   g.target = next;
   if (g.elastic) drawIcon(items[next]);
   tick();
  }
  items[next].classList.add('nav-preview');
  if (!frame) frame = requestAnimationFrame(() => {
   frame = 0;
   capsule.style.setProperty('--nav-drag-index', g.position);
   if (g.stretch) {
    capsule.style.setProperty('--lens-stretch', 1 + g.stretch);
    capsule.style.setProperty('--lens-compress', 1 - g.stretch * .55);
    liftIcons(g.position,g.vertical);
   }
  });
 }

 nav.addEventListener('pointermove', event => {
  const g = gesture;
  if (!g || event.pointerId !== g.id) return;
  if (!g.dragging) {
   const dx = Math.abs(event.clientX - g.x), dy = Math.abs(event.clientY - g.y);
   if (Math.max(dx, dy) < 7) return;
   if (g.vertical ? dx > dy : dy > dx) { cancel(); return; }
   g.dragging = true;
   nav.setPointerCapture(g.id);
   nav.classList.add('nav-tracking');
  }
  track(event);
 });

 function inside(event, bounds) {
  return event.clientX >= bounds.left && event.clientX <= bounds.right &&
         event.clientY >= bounds.top && event.clientY <= bounds.bottom;
 }

 window.addEventListener('pointerup', event => {
  const g = gesture;
  if (!g || event.pointerId !== g.id) return;
  if (!inside(event, g.bounds)) { cancel(); return; }
  if (g.dragging) {
   suppressClick = true;
   track(event);
   const item = items[g.target];
   clearGesture();
   if (item.dataset.page !== page) select(item, false);
   if (g.elastic) {
    bounceIcon(item,g.vertical);
    const stretch=g.stretch||.045;
    settle(k=>{
     const pos=(g.target+(g.position-g.target)*k)*100+'%';
     const along=1+stretch*k, across=1-.62*stretch*k;
     return {transform:g.vertical ? 'translate3d(0,'+pos+',0) scale('+across+','+along+')'
       : 'translate3d('+pos+',0,0) scale('+along+','+across+')'};
    },465+stretch*450);
   }
  } else {
   clearGesture();
   // WebView may suppress its compatibility click immediately after a drag.
   if (event.pointerType === 'touch' && inside(event,g.pressedItem.getBoundingClientRect())) {
    suppressClick = true;
    select(g.pressedItem,true);
   }
  }
 }, true);
 reducedMotion.addEventListener?.('change',event=>{
  if(event.matches){stopStrokes();stopRebound();glyphBounce?.cancel();cancel();}
 });
 window.addEventListener('pointercancel', event => {
  if (gesture?.id === event.pointerId) cancel();
 }, true);
 nav.addEventListener('lostpointercapture', event => {
  // Touch browsers transfer their implicit capture from a button to the bar.
  if (event.target === nav && gesture?.id === event.pointerId) cancel();
 });
 window.addEventListener('blur', cancel);
 window.addEventListener('resize', cancel);
 document.addEventListener('visibilitychange', () => { if (document.hidden) cancel(); });

 nav.addEventListener('click', event => {
  if (suppressClick && event.detail !== 0) { event.preventDefault(); return; }
  const item = event.target.closest('button[data-page]');
  if (item) select(item, true);
 });
 nav.addEventListener('keydown', event => {
  if (event.key === 'Escape' && gesture) { cancel(); return; }
  const index = items.indexOf(event.target);
  if (index < 0 || event.altKey || event.ctrlKey || event.metaKey) return;
  const vertical = getComputedStyle(nav).flexDirection === 'column';
  let next;
  if (event.key === (vertical ? 'ArrowDown' : 'ArrowRight')) next = (index + 1) % items.length;
  else if (event.key === (vertical ? 'ArrowUp' : 'ArrowLeft')) next = (index + items.length - 1) % items.length;
  else if (event.key === 'Home') next = 0;
  else if (event.key === 'End') next = items.length - 1;
  else return;
  event.preventDefault();
  items[next].focus({preventScroll: true});
  select(items[next], true);
 });
})();
