(function(root){
 'use strict';
 const DAY=86400000;
 function dayKey(value){const d=new Date(value);if(!Number.isFinite(d.getTime()))return '';return d.getFullYear()+'-'+String(d.getMonth()+1).padStart(2,'0')+'-'+String(d.getDate()).padStart(2,'0');}
 function dayIndex(key){return Math.floor(Date.parse(key+'T00:00:00Z')/DAY);}
 function indexKey(index){return new Date(index*DAY).toISOString().slice(0,10);}
 // Build once per immutable native history snapshot, shared by all five charts and the timer summary.
 function index(sessions){
  const rows=[],days=new Map();let pomos=0;
  for(const s of sessions){
   if(!s||!Number.isFinite(s.end)||!Number.isFinite(s.seconds)||s.seconds<=0||s.mode==='break')continue;
   const date=new Date(s.end);if(!Number.isFinite(date.getTime()))continue;
   const day=date.getFullYear()+'-'+String(date.getMonth()+1).padStart(2,'0')+'-'+String(date.getDate()).padStart(2,'0');
   rows.push({session:s,day,hour:Math.floor(date.getHours()/4)});
   const bucket=days.get(day)||{seconds:0,pomos:0,sessions:[]};
   bucket.seconds+=s.seconds;bucket.sessions.push(s);
   if(s.mode==='focus'&&s.completed){bucket.pomos++;pomos++;}days.set(day,bucket);
  }
  return {source:sessions,rows,days,pomos};
 }
 function safeRows(history,today,range){const start=range==='all'?'0000-01-01':indexKey(dayIndex(today)-(range==='30'?29:6));return history.rows.filter(row=>row.day>=start&&row.day<=today);}
 function trend(rows,today,range){
  const last=dayIndex(today);let first=last-(range==='30'?29:6),step='day';
  if(range==='all') {for(const row of rows)first=Math.min(first,dayIndex(row.day));const span=last-first+1;if(span>210)step='month';else if(span>31)step='week';}
  const buckets=[];
  if(step==='day'||step==='week'){
   const stride=step==='week'?7:1;
   for(let start=first;start<=last;start+=stride){const end=Math.min(last,start+stride-1);buckets.push({key:indexKey(start),start:indexKey(start),end:indexKey(end),seconds:0,count:0});}
  }else{
   const firstDate=new Date(first*DAY),lastDate=new Date(last*DAY);
   const months=(lastDate.getUTCFullYear()-firstDate.getUTCFullYear())*12+lastDate.getUTCMonth()-firstDate.getUTCMonth()+1;
   step=months>30?'year':'month';
   const date=new Date(Date.UTC(firstDate.getUTCFullYear(),step==='year'?0:firstDate.getUTCMonth(),1));
   while(date.getTime()/DAY<=last){
    const start=date.toISOString().slice(0,10);
    if(step==='year')date.setUTCFullYear(date.getUTCFullYear()+1);else date.setUTCMonth(date.getUTCMonth()+1);
    const end=indexKey(Math.min(last,date.getTime()/DAY-1));buckets.push({key:start,start,end,seconds:0,count:0});
   }
  }
  // Many records share a date. Resolve its calendar bucket once, without reparsing timestamps.
  const byDate=new Map();
  for(const row of rows){let bucket=byDate.get(row.day);if(!bucket){bucket=buckets.find(b=>row.day>=b.start&&row.day<=b.end);if(bucket)byDate.set(row.day,bucket);}if(bucket){bucket.seconds+=row.session.seconds;bucket.count++;}}
  return {step,buckets};
 }
 function tasks(sessions,limit=5){
  const groups=new Map();for(const s of sessions){const name=s.label||'自由专注';const row=groups.get(name)||{name,seconds:0,count:0,names:[name]};row.seconds+=s.seconds;row.count++;groups.set(name,row);}
  const all=[...groups.values()].sort((a,b)=>b.seconds-a.seconds||a.name.localeCompare(b.name,'zh-CN'));
  if(all.length<=limit)return all;
  const rest=all.slice(limit);return [...all.slice(0,limit),{name:'其他 '+rest.length+' 个事项',seconds:rest.reduce((n,g)=>n+g.seconds,0),count:rest.reduce((n,g)=>n+g.count,0),names:rest.map(g=>g.name),other:true}];
 }
 function hours(entries){
  const rows=Array.from({length:6},(_,i)=>({key:i,start:i*4,end:(i+1)*4,seconds:0,count:0}));
  for(const row of entries){rows[row.hour].seconds+=row.session.seconds;rows[row.hour].count++;}
  return rows;
 }
 function summary(sessions,today,range,prepared){const history=prepared?.source===sessions?prepared:index(sessions),rows=safeRows(history,today,range),selected=rows.map(row=>row.session);return {sessions:selected,total:selected.reduce((n,s)=>n+s.seconds,0),trend:trend(rows,today,range),tasks:tasks(selected),hours:hours(rows)};}
 function scale(values){const max=Math.max(0,...values),unit=max>0&&max<60?'秒':'分钟',divisor=unit==='秒'?1:60;const top=max/divisor;if(top===0)return {unit,divisor,max:1};const power=Math.pow(10,Math.floor(Math.log10(top))),fraction=top/power;return {unit,divisor,max:(fraction<=1?1:fraction<=2?2:fraction<=5?5:10)*power};}
 function calendar(sessions,key,unit,today,prepared){
  if(!['day','month','year'].includes(unit))throw new RangeError('Invalid calendar unit');
  const pattern=unit==='year'?/^\d{4}$/:unit==='month'?/^\d{4}-\d{2}$/:/^\d{4}-\d{2}-\d{2}$/;
  if(!pattern.test(key)||Number(key.slice(0,4))<1900||Number(key.slice(0,4))>2200)throw new RangeError('Invalid calendar period');
  const year=Number(key.slice(0,4)),month=Number(key.slice(5,7));
  if(unit!=='year'&&(month<1||month>12))throw new RangeError('Invalid month');
  const days=unit==='year'?0:new Date(Date.UTC(year,month,0)).getUTCDate();
  if(unit==='day'&&(Number(key.slice(8))<1||Number(key.slice(8))>days))throw new RangeError('Invalid day');
  const history=prepared?.source===sessions?prepared:index(sessions),rows=history.rows.filter(r=>r.day.startsWith(key)&&r.day<=today),selected=rows.map(r=>r.session);
  const count=unit==='year'?12:unit==='month'?days:1;
  const buckets=Array.from({length:count},(_,i)=>({key:unit==='year'?key+'-'+String(i+1).padStart(2,'0'):unit==='month'?key+'-'+String(i+1).padStart(2,'0'):key,seconds:0,count:0}));
  for(const row of rows){const at=unit==='year'?Number(row.day.slice(5,7))-1:unit==='month'?Number(row.day.slice(8))-1:0;buckets[at].seconds+=row.session.seconds;buckets[at].count++;}
  const activeDays=new Set(rows.map(r=>r.day)).size,total=selected.reduce((n,s)=>n+s.seconds,0);
  return {key,unit,sessions:selected,buckets,total,count:selected.length,activeDays,average:activeDays?total/activeDays:0,pomos:selected.filter(s=>s.mode==='focus'&&s.completed).length};
 }
 const api={dayKey,dayIndex,indexKey,index,summary,scale,calendar};
 if(typeof module!=='undefined'&&module.exports)module.exports=api;else root.LumiChartsData=api;
})(typeof window!=='undefined'?window:globalThis);
