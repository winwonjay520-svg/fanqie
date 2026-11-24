(function(root){
 'use strict';
 const DAY=86400000;
 function dayIndex(key){return Math.floor(Date.parse(key+'T00:00:00Z')/DAY);}
 function keyAt(index){return new Date(index*DAY).toISOString().slice(0,10);}
 function addDays(key,days){return keyAt(dayIndex(key)+days);}
 function weekStart(today){const day=new Date(today+'T12:00:00Z').getUTCDay();return addDays(today,-((day+6)%7));}
 function clamped(year,month,day){const max=new Date(Date.UTC(year,month,0)).getUTCDate();return `${year}-${String(month).padStart(2,'0')}-${String(Math.min(day,max)).padStart(2,'0')}`;}
 function occurrence(event,today){
  const original=event.date,repeat=event.repeat||'none';if(original>=today||repeat==='none')return original;
  if(repeat==='weekly')return addDays(today,((dayIndex(original)-dayIndex(today))%7+7)%7);
  const [,m,d]=original.split('-').map(Number),[year,month]=today.split('-').map(Number);
  if(repeat==='monthly'){let next=clamped(year,month,d);if(next<today)next=clamped(month===12?year+1:year,month===12?1:month+1,d);return next;}
  if(repeat==='yearly'){let next=clamped(year,m,d);if(next<today)next=clamped(year+1,m,d);return next;}
  return original;
 }
 function localDay(value){const d=new Date(value);return `${d.getFullYear()}-${String(d.getMonth()+1).padStart(2,'0')}-${String(d.getDate()).padStart(2,'0')}`;}
 function week(sessions,today){
  const start=weekStart(today),index=dayIndex(start),offset=dayIndex(today)-index;
  const days=Array.from({length:7},(_,i)=>({date:addDays(start,i),seconds:0,count:0,pomos:0}));let previous=0,previousFull=0;
  for(const s of sessions){if(!s||!Number.isFinite(s.seconds)||s.seconds<=0||s.mode==='break'||!Number.isFinite(s.end))continue;const day=localDay(s.end),position=dayIndex(day)-index;if(position>=0&&position<=offset){days[position].seconds+=s.seconds;days[position].count++;if(s.mode==='focus'&&s.completed)days[position].pomos++;}else if(position>=-7&&position<0){previousFull+=s.seconds;if(position<=offset-7)previous+=s.seconds;}}
  const seconds=days.reduce((n,d)=>n+d.seconds,0);return {start,days,seconds,previous,previousFull,change:previous>0?(seconds-previous)/previous:null};
 }
 function calendar(month){const first=month+'-01',start=weekStart(first);return Array.from({length:42},(_,i)=>({date:addDays(start,i),inMonth:addDays(start,i).slice(0,7)===month}));}
 function taskProgress(task,sessions){let seconds=0,pomos=0;for(const s of sessions)if(s.taskId===task.id){seconds+=s.seconds;if(s.mode==='focus'&&s.completed)pomos++;}return {seconds,pomos,percent:Math.min(100,pomos/task.estimate*100)};}
 function groups(sessions,key){const map=new Map();for(const s of sessions){const name=s[key]||(key==='tag'?'未分类':'自由专注'),row=map.get(name)||{name,seconds:0,count:0};row.seconds+=s.seconds;row.count++;map.set(name,row);}return [...map.values()].sort((a,b)=>b.seconds-a.seconds||a.name.localeCompare(b.name,'zh-CN'));}
 const api={dayIndex,addDays,weekStart,occurrence,week,calendar,taskProgress,groups};
 if(typeof module!=='undefined'&&module.exports)module.exports=api;else root.LumiFeaturesData=api;
})(typeof window!=='undefined'?window:globalThis);
