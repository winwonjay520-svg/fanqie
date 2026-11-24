'use strict';
(() => {
 const nav = document.querySelector('.bottom-nav');
 const capsule = nav.querySelector('.nav-capsule');
 const items = Array.from(nav.querySelectorAll('button[data-page]'));
 let gesture = null;
 let frame = 0;
 let suppressClick = false;

 function tick() {
  if (data?.settings?.navigationHaptics !== false) window.Native?.navigationHaptic?.();
 }

 function select(item, withFeedback) {
  if (withFeedback && item.dataset.page !== page) tick();
  navigate(item.dataset.page);
 }

 function clearGesture() {
  const old = gesture;
  gesture = null;
  cancelAnimationFrame(frame);
  frame = 0;
  nav.classList.remove('nav-tracking');
  capsule.style.removeProperty('--nav-drag-index');
  if (old) {
   items[old.target].classList.remove('nav-preview');
   if (nav.hasPointerCapture(old.id)) nav.releasePointerCapture(old.id);
  }
 }

 function cancel() {
  if (!gesture) return;
  suppressClick = true;
  clearGesture();
 }

 nav.addEventListener('pointerdown', event => {
  if (event.isPrimary === false) { cancel(); return; }
  if (event.button !== 0) return;
  cancel();
  suppressClick = false;
  if (!event.target.closest('button[data-page]')) return;
  const vertical = getComputedStyle(nav).flexDirection === 'column';
  const first = items[0].getBoundingClientRect();
  const last = items[items.length - 1].getBoundingClientRect();
  const center = box => vertical ? box.top + box.height / 2 : box.left + box.width / 2;
  gesture = {
   id: event.pointerId, vertical, bounds: nav.getBoundingClientRect(),
   origin: center(first), step: (center(last) - center(first)) / (items.length - 1),
   x: event.clientX, y: event.clientY, dragging: false,
   target: items.findIndex(item => item.classList.contains('selected'))
  };
 });

 function track(event) {
  const g = gesture;
  const point = g.vertical ? event.clientY : event.clientX;
  const position = Math.max(0, Math.min(items.length - 1, (point - g.origin) / g.step));
  const next = Math.round(position);
  g.position = position;
  if (next !== g.target) {
   items[g.target].classList.remove('nav-preview');
   g.target = next;
   tick();
  }
  items[next].classList.add('nav-preview');
  if (!frame) frame = requestAnimationFrame(() => {
   frame = 0;
   capsule.style.setProperty('--nav-drag-index', g.position);
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
  } else clearGesture();
 }, true);
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
