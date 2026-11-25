'use strict';
// Browser UI checks only. This fixture never calls ADB or reads device data.
(() => {
 const initial={_revision:1,_historyRevision:0,_workspaceRevision:0,_eventsRevision:0,timer:{mode:'focus',duration:1500,elapsed:0,elapsedNow:0,status:'idle',label:'自由专注',taskId:'',tag:''},sessions:[],tasks:[],events:[],settings:{focus:25,short:5,long:15,goal:120,sound:true,keepAwake:false},planner:{weekGoal:600,items:[]},workflow:{focus:1500,short:300,long:900,rounds:4,auto:false},mixer:{rain:35,forest:0,cafe:0,playing:false,followTimer:true},preferences:{clockStyle:'digital',clockFace:'timer'},audioActive:false,appearance:{preset:'white',tone:'light',blur:0,dim:28,glass:64,position:50,liquid:true,material:'clear',hasImage:false,imageVersion:'',legibility:true}};
 const s=structuredClone(initial);let base=0;window.__nativeCalls=[];
 function elapsed(){return s.timer.elapsed+(s.timer.status==='running'?performance.now()-base:0);}
 function snapshot(){return JSON.stringify({...s,timer:{...s.timer,elapsedNow:elapsed()}});}
 window.Native={poll:snapshot,appearance(raw){Object.assign(s.appearance,JSON.parse(raw));return JSON.stringify(s.appearance);},notifyPermission(){},focusSceneMode(){},clockMode(){},discardBackup(){},command(raw){const c=JSON.parse(raw);window.__nativeCalls.push(c);
  switch(c.action){
   case 'eventSave':{const e={...c.value,id:c.value.id||crypto.randomUUID()};const i=s.events.findIndex(x=>x.id===e.id);if(i<0)s.events.push(e);else s.events[i]=e;s._eventsRevision++;break;}
   case 'pinEvent':s.pinnedEventId=c.id;s._eventsRevision++;break;
   case 'settings':Object.assign(s.settings,c.value);break;
   case 'configure':s.timer={...initial.timer,mode:c.mode,duration:c.duration,label:c.label,taskId:c.taskId};base=0;break;
   case 'eventDelete':s.events=s.events.filter(x=>x.id!==c.id);if(s.pinnedEventId===c.id)s.pinnedEventId='';s._eventsRevision++;break;
   case 'taskSave':{const t={...c.value,id:c.value.id||crypto.randomUUID(),created:Date.now()};const i=s.tasks.findIndex(x=>x.id===t.id);if(i<0)s.tasks.push(t);else s.tasks[i]=t;s._workspaceRevision++;break;}
   case 'startTask':s.timer={mode:c.mode,duration:c.duration,label:c.label||'自由专注',taskId:c.taskId||'',elapsed:0,status:'running'};base=performance.now();break;
   case 'pause':s.timer.elapsed=elapsed();s.timer.status='paused';break;
   case 'start':s.timer.status='running';base=performance.now();break;
   case 'finish':s.timer.elapsed=elapsed();s.timer.status='completed';break;
   case 'reset':s.timer={...initial.timer};break;
   default:return JSON.stringify({error:'Browser fixture has no handler: '+c.action});
  }s._revision++;return snapshot();
 }};
 window.__qaSeedTimer=(timer)=>{s.timer={...initial.timer,...timer};base=performance.now();s._revision++;};
})();
