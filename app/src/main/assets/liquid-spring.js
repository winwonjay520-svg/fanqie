'use strict';
// Sample the damped return once per release. Web Animations interpolates the
// keyframes on the display timeline, including on high-refresh-rate screens.
function liquidSpringFrames(sample) {
 const frames = [];
 for (let i = 0; i <= 36; i++) {
  const t = i / 36;
  const remaining = i === 36 ? 0 : Math.exp(-5.6*t) * Math.cos(4.8*Math.PI*t);
  frames.push({...sample(remaining), offset:t});
 }
 return frames;
}
