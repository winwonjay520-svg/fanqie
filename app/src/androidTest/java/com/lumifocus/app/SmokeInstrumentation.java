package com.lumifocus.app;

import android.app.*;
import android.os.*;
import android.content.*;
import android.webkit.WebView;
import android.view.ViewGroup;
import org.json.*;
import java.util.concurrent.*;

/** Device-level regression checks; no external test libraries required. */
public class SmokeInstrumentation extends Instrumentation {
    private final StringBuilder report = new StringBuilder();
    private MainActivity activity;
    private WebView web;
    private Store store;
    private boolean restoreOnly;
    private boolean perfOnly;
    private boolean featuresOnly;
    private boolean profileOnly;
    private String tabletProfile;
    private String navigationProfile;
    private String appearanceProfile;
    private String photoUploadProfile;
    private boolean taskSwitchOnly;
    private String todoProfile;
    @Override public void onCreate(Bundle args) { super.onCreate(args);restoreOnly=args!=null&&"true".equals(args.getString("restore"));perfOnly=args!=null&&"true".equals(args.getString("perf"));featuresOnly=args!=null&&"true".equals(args.getString("features"));profileOnly=args!=null&&"true".equals(args.getString("profile"));taskSwitchOnly=args!=null&&"true".equals(args.getString("taskSwitch"));tabletProfile=args==null?null:args.getString("tablet");navigationProfile=args==null?null:args.getString("navigation");appearanceProfile=args==null?null:args.getString("appearance");photoUploadProfile=args==null?null:args.getString("photoUpload");todoProfile=args==null?null:args.getString("todo");start(); }
    private void check(boolean pass,String label) { if(!pass) throw new AssertionError(label); report.append("PASS ").append(label).append('\n'); }
    private JSONObject snap() throws Exception { return new JSONObject(store.snapshot()); }
    private void cmd(String json) throws Exception { JSONObject result = new JSONObject(store.command(json)); if(result.has("error")) throw new AssertionError(result.getString("error")); }
    private String js(String code) throws Exception {
        CountDownLatch latch = new CountDownLatch(1); String[] value = new String[1];
        runOnMainSync(() -> web.evaluateJavascript("(()=>{"+code+"})()", result -> {value[0] = result; latch.countDown();}));
        if(!latch.await(10,TimeUnit.SECONDS)) throw new AssertionError("WebView callback timeout"); return value[0];
    }
    private void shot(String name) throws Exception {
        js("$('toast').classList.remove('show');return true");
        getTargetContext().getSystemService(NotificationManager.class).cancel(2);
        waitForIdleSync(); Thread.sleep(1100);
        android.graphics.Bitmap image = getUiAutomation().takeScreenshot();
        try(java.io.FileOutputStream out = new java.io.FileOutputStream(new java.io.File(getTargetContext().getFilesDir(),name+".png"))) { image.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out); }
    }
    @Override public void onStart() {
        Bundle results = new Bundle();
        try {
            store=Store.get(getTargetContext());
            activity=(MainActivity) startActivitySync(new Intent(getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            runOnMainSync(() -> web=(WebView)((ViewGroup)((ViewGroup)activity.findViewById(android.R.id.content)).getChildAt(0)).getChildAt(0));
            for(int i=0;i<30;i++){Thread.sleep(200);if("true".equals(js("return typeof data!=='undefined'&&!!data&&typeof setTodoView==='function'")))break;}
            js("if(window.setTodoView)setTodoView('classic');return true");
            if(perfOnly){performanceChecks();results.putString("stream",report.toString());finish(Activity.RESULT_OK,results);return;}
            if(taskSwitchOnly){report.append(new TaskSwitchChecks(this,web).run());results.putString("stream",report.toString());finish(Activity.RESULT_OK,results);return;}
            if(todoProfile!=null){report.append(new TodoFlowChecks(this,web).run(todoProfile));results.putString("stream",report.toString());finish(Activity.RESULT_OK,results);return;}
            if(navigationProfile!=null){report.append(new NavigationChecks(this,activity,web).run(navigationProfile));results.putString("stream",report.toString());finish(Activity.RESULT_OK,results);return;}
            if(appearanceProfile!=null){report.append(new AppearanceChecks(this,web).run(appearanceProfile));results.putString("stream",report.toString());finish(Activity.RESULT_OK,results);return;}
            if(photoUploadProfile!=null){report.append(new PhotoUploadChecks(this,web).run(photoUploadProfile));results.putString("stream",report.toString());finish(Activity.RESULT_OK,results);return;}
            if(tabletProfile!=null){report.append(new TabletChecks(this,activity,web).run(tabletProfile));results.putString("stream",report.toString());finish(Activity.RESULT_OK,results);return;}
            if(featuresOnly){report.append(new FeatureChecks(this,activity,web).run());results.putString("stream",report.toString());finish(Activity.RESULT_OK,results);return;}
            if(profileOnly){Thread.sleep(1200);report.append("PROFILE ").append(profile()).append('\n');results.putString("stream",report.toString());finish(Activity.RESULT_OK,results);return;}
            if(restoreOnly) {
                JSONObject a=snap().getJSONObject("appearance");
                check(a.getBoolean("hasImage")&&a.getString("preset").equals("photo"),"Imported wallpaper file survives process restart");
                try(java.io.InputStream image=WallpaperStore.openImage(getTargetContext())){check(image.read()!=-1&&a.getInt("blur")==3&&a.getInt("glass")==28&&a.getInt("position")==44&&a.getString("material").equals("soft"),"Appearance and material adjustments survive process restart");}
                check("true".equals(js("return document.body.classList.contains('photo-background')&&document.body.classList.contains('liquid')&&getComputedStyle(document.body).getPropertyValue('--photo-tint').trim()===data.appearance.photoPalette[data.appearance.tone].tint")),"Photo background and liquid glass render after restart");
                check(snap().getJSONArray("sessions").length()==2&&snap().getJSONObject("settings").getInt("focus")==35,"Focus history and settings survive process restart");
                check("true".equals(js("navigate('stats');return trendView==='line'&&$('barChart').hidden&&!$('lineChart').hidden")),"Chosen chart view survives process restart");
                results.putString("stream",report.toString());finish(Activity.RESULT_OK,results);return;
            }
            check("true".equals(js("return !!data && document.getElementById('timerText').textContent==='25:00'")),"WebView loads bundled UI and native data");
            check("true".equals(js("return statsSource===null&&$('barChart').children.length===0")),"Focus startup does not build hidden statistics");
            check("true".equals(js("return document.documentElement.scrollWidth<=innerWidth")),"No horizontal overflow on phone");
            check("true".equals(js("return $('startButton').getBoundingClientRect().bottom<document.querySelector('.bottom-nav').getBoundingClientRect().top")),"Start control remains above bottom navigation on small phone");
            check("true".equals(js("return $('extendButton').getBoundingClientRect().bottom<document.querySelector('.bottom-nav').getBoundingClientRect().top")),"Round progress and extension remain visible above phone navigation");
            shot("focus");
            whiteMotionChecks();
            check("true".equals(js("$('customButton').click();$('customMins').value=0;$('customSecs').value=0;$('saveCustom').click();return !!$('formError').textContent")),"Reject zero custom duration");
            check("true".equals(js("$('customSecs').value=3;$('saveCustom').click();return data.timer.duration===3")),"Custom seconds configure native timer");
            js("$('startButton').click();return true"); Thread.sleep(1100);
            check(store.running(),"UI starts native foreground timer");
            js("$('startButton').click();return true");
            long paused=store.elapsed();Thread.sleep(1100);
            check(!store.running()&&Math.abs(store.elapsed()-paused)<20,"Pause excludes paused wall time");
            js("$('startButton').click();return true");
            // Put the actual activity in the background, keeping the timer service alive.
            runOnMainSync(() -> activity.moveTaskToBack(true));Thread.sleep(2600);
            check(snap().getJSONObject("timer").getString("status").equals("completed"),"Countdown completes in background");
            check(snap().getJSONArray("sessions").length()==1&&snap().getJSONArray("sessions").getJSONObject(0).getInt("seconds")==3,"Completion recorded once with exact active duration");
            Thread.sleep(1000);check(snap().getJSONArray("sessions").length()==1,"Alarm and service do not double-count");
            getTargetContext().startActivity(new Intent(getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_REORDER_TO_FRONT));
            Thread.sleep(500);
            cmd("{\"action\":\"configure\",\"mode\":\"up\",\"duration\":1,\"label\":\"正计时测试\"}");cmd("{\"action\":\"start\"}");Thread.sleep(1300);cmd("{\"action\":\"finish\"}");
            check(snap().getJSONArray("sessions").length()==2,"Stopwatch saves real elapsed duration");
            cmd("{\"action\":\"configure\",\"mode\":\"break\",\"duration\":1}");cmd("{\"action\":\"start\"}");Thread.sleep(1200);store.checkFinish();
            check(snap().getJSONArray("sessions").length()==2,"Rest never increases focus statistics");
            js("refresh();closeSheet();navigate('events');$('addEvent').click();$('eventTitle').value='下一次出发';$('eventDate').value=dayOffset(30);$('saveEvent').click();return true");
            check(snap().getJSONArray("events").length()==1,"Event form persists countdown date");
            check("true".equals(js("return $('eventList').textContent.includes('30')&&$('eventList').textContent.includes('下一次出发')")),"Countdown date displays correct day count");shot("events");
            js("editEvent(data.events[0].id);$('eventTitle').value='旅行计划';$('saveEvent').click();return true");
            check(snap().getJSONArray("events").getJSONObject(0).getString("title").equals("旅行计划"),"Edit updates existing event");
            js("navigate('stats');return true");shot("stats");
            check("true".equals(js("return $('totalSessions').textContent==='2'&&$('totalPomos').textContent==='1'")),"Statistics use real records and completed pomodoros");
            js("document.querySelector('[data-range=\"30\"]').click();return true");check("true".equals(js("return $('barChart').children.length===30")),"Monthly statistics renders 30 daily bars");
            js("editEvent(data.events[0].id);$('deleteEvent').click();$('confirmDelete').click();return true");check(snap().getJSONArray("events").length()==0,"Event delete confirmation works");
            check("true".equals(js("settings();$('settingFocus').value=0;$('saveSettings').click();return !!$('formError').textContent")),"Settings reject invalid minutes");
            js("$('settingFocus').value=35;$('saveSettings').click();return true");check(snap().getJSONObject("settings").getInt("focus")==35,"Custom settings persist");
            String persisted=getTargetContext().getSharedPreferences("lumi_v1",0).getString("data", "");check(new JSONObject(persisted).getJSONArray("sessions").length()==2,"Sessions committed to on-device storage");
            enhancementChecks();
            chartChecks();
            performanceRegressionChecks();
            appearanceChecks();
            results.putString("stream",report.toString());finish(Activity.RESULT_OK,results);
        } catch(Throwable e) { results.putString("stream",report+"FAIL "+android.util.Log.getStackTraceString(e));finish(Activity.RESULT_CANCELED,results); }
    }
    private void performanceRegressionChecks() throws Exception {
        check("true".equals(js("""
            closeSheet();navigate('focus');
            const sessions=data.sessions,index=historyIndex(),revision=data._historyRevision;
            command('reset');
            return data.sessions===sessions&&historyIndex()===index&&data._historyRevision===revision;
            """)),"Timer-only command retains the shared history index");
        check("true".equals(js("""
            navigate('stats');const chart=$('barChart').firstElementChild;
            command('reset');
            return chart===$('barChart').firstElementChild;
            """)),"Timer-only revision leaves current chart DOM intact");
        check("true".equals(js("""
            const original=data,index=historyIndex();
            try {data={...original,sessions:[]};const empty=historyIndex();
              return empty!==index&&empty.pomos===0&&empty.days.size===0;
            } finally {data=original;render();}
            """)),"Replacing history invalidates the aggregate cache");
        js("navigate('focus');window.__perfRefresh=refresh;window.__perfPolls=0;refresh=function(){window.__perfPolls++;return window.__perfRefresh();};return true");
        Thread.sleep(3200);
        check("true".equals(js("const pass=window.__perfPolls<=1;refresh=window.__perfRefresh;delete window.__perfRefresh;delete window.__perfPolls;return pass")),"Idle page avoids repeated native polls");
        check("true".equals(js("""
            const originalDay=localDay,index=historyIndex();
            try {localDay=function(value){return value===undefined?'2099-01-01':originalDay(value);};render();
              return historyIndex()!==index&&$('todayPomodoros').textContent==='0';
            } finally {localDay=originalDay;render();}
            """)),"Calendar rollover refreshes the cached today summary");
        js("window.__perfTimer={...data.timer};configure('down',30);command('start');window.__perfRefresh=refresh;window.__perfPolls=0;refresh=function(){window.__perfPolls++;return window.__perfRefresh();};return true");
        Thread.sleep(2250);
        check("true".equals(js("return data.timer.status==='running'&&data.timer.elapsedNow>=1000&&$('timerText').textContent!=='00:30'&&window.__perfPolls>=1&&window.__perfPolls<=3")),"Running timer updates each second without rapid polling");
        js("refresh=window.__perfRefresh;delete window.__perfRefresh;delete window.__perfPolls;command('reset');configure(window.__perfTimer.mode,window.__perfTimer.duration,window.__perfTimer.label);delete window.__perfTimer;return true");
    }
    private void performanceChecks() throws Exception {
        // Synthetic history lives only in WebView memory; no Native commands or saved history.
        String timing=js("""
            const original=data, originalRange=range, originalRenderStats=renderStats;
            closeSheet();navigate('focus');
            const now=Date.now(), sessions=Array.from({length:5000},(_,i)=>({id:'perf-'+i,
              start:now-(i%365)*86400000-1500000,end:now-(i%365)*86400000,
              seconds:1500,mode:'focus',completed:true,label:'事项 '+(i%8)}));
            try {
              data={...original,sessions};range='all';lastStats='';
              let hidden=0;renderStats=function(){hidden++;return originalRenderStats();};
              const start=performance.now();render();const focusMs=performance.now()-start;
              renderStats=originalRenderStats;
              const samples=[];for(let i=0;i<5;i++){const t=performance.now();renderStats();samples.push(performance.now()-t);}
              samples.sort((a,b)=>a-b);
              return {records:sessions.length,hiddenStatsRenders:hidden,focusMs,statsMedianMs:samples[2],statsSamplesMs:samples};
            } finally {renderStats=originalRenderStats;data=original;range=originalRange;lastStats='';render();}
            """);
        report.append("PERF_HISTORY ").append(timing).append('\n');
        js("window.__perfRefresh=refresh;window.__perfPolls=0;refresh=function(){window.__perfPolls++;return window.__perfRefresh();};return true");
        Thread.sleep(3200);
        String polling=js("const count=window.__perfPolls;refresh=window.__perfRefresh;delete window.__perfRefresh;delete window.__perfPolls;return {idlePollsIn3200ms:count,idle:data.timer.status!=='running'}");
        report.append("PERF_IDLE ").append(polling).append('\n');
    }
    private String profile() throws Exception {
        return js("""
            const original=data,originalRange=range,fnNames=['renderCharts','renderInsights','renderRichStats'];
            const originals=Object.fromEntries(fnNames.map(n=>[n,window[n]])),dateFn=Date.prototype.toLocaleTimeString;
            const now=Date.now(),sessions=Array.from({length:5000},(_,i)=>({id:'profile-'+i,start:now-(i%365)*86400000-1500000,end:now-(i%365)*86400000,seconds:1500,mode:'focus',completed:true,label:'事项 '+(i%8)}));
            let current;const rows=[];
            try {
              data={...data,sessions};range='all';
              for(const name of fnNames)window[name]=function(...args){const t=performance.now();try{return originals[name](...args);}finally{current[name]=(current[name]||0)+performance.now()-t;}};
              Date.prototype.toLocaleTimeString=function(...args){const t=performance.now();try{return dateFn.apply(this,args);}finally{current.localTimeMs=(current.localTimeMs||0)+performance.now()-t;}};
              for(let i=0;i<5;i++){current={};const t=performance.now();renderStats();current.totalMs=performance.now()-t;rows.push(current);}
              return rows;
            } finally {for(const n of fnNames)window[n]=originals[n];Date.prototype.toLocaleTimeString=dateFn;data=original;range=originalRange;lastStats='';render();}
            """);
    }
    private void chartChecks() throws Exception {
        js("closeSheet();navigate('stats');return true");
        check("true".equals(js("return chartModel.total===data.sessions.reduce((n,s)=>n+s.seconds,0)&&chartModel.sessions.length===2")),"All chart types share the actual selected focus records");
        js("document.querySelector('[data-trend-view=line]').click();return true");
        check("true".equals(js("return $('barChart').hidden&&!$('lineChart').hidden&&$('lineChart').querySelectorAll('.line-point').length===30")),"Line chart switch shows one real point per daily bucket");
        check("true".equals(js("return getComputedStyle($('lineChart').querySelector('text')).stroke==='none'&&getComputedStyle($('lineChart').querySelector('path')).stroke==='none'")),"Chart labels and area fill do not inherit icon outlines");
        js("$('lineChart').querySelector('.line-point').dispatchEvent(new MouseEvent('click',{bubbles:true,detail:1}));return true");
        check("true".equals(js("return $('sheetTitle').textContent.includes('的积累')")),"Line chart point opens its underlying daily records");
        js("closeSheet();document.querySelector('[data-trend-view=bar]').click();return true");
        check("true".equals(js("return !$('barChart').hidden&&$('lineChart').hidden&&$('barChart').children.length===30")),"Bar and line switches keep the same selected time range");
        check("true".equals(js("return $('taskDonut').querySelectorAll('.donut-segment').length===2&&chartModel.tasks.reduce((n,g)=>n+g.seconds,0)===chartModel.total")),"Donut slices preserve the exact total of real tasks");
        js("$('donutLegend').querySelector('[data-chart-task]').click();return true");
        check("true".equals(js("return $('sheetBody').querySelectorAll('.detail-record').length===1")),"Donut legend opens records for its actual task");
        js("closeSheet();return true");
        check("true".equals(js("return $('hourChart').children.length===6&&chartModel.hours.reduce((n,g)=>n+g.seconds,0)===chartModel.total")),"Hourly distribution has six local-time groups and no invented duration");
        js("openChartHour(chartModel.hours.findIndex(g=>g.count>0));return true");
        check("true".equals(js("return $('sheetBody').querySelectorAll('.detail-record').length===2")),"Active time bucket opens the corresponding real sessions");
        js("closeSheet();document.querySelector('[data-range=\"7\"]').click();return true");
        check("true".equals(js("return chartModel.trend.buckets.length===7&&$('barChart').children.length===7&&$('lineChart').querySelectorAll('.line-point').length===7")),"Date range changes synchronise both trend charts");
        check("true".equals(js("const original=data;data={...data,sessions:[]};renderCharts();const empty=!$('taskDonut').querySelector('.donut-segment')&&$('taskDonut').textContent.includes('暂无记录')&&chartModel.total===0;data=original;renderCharts();return empty")),"Empty chart state contains no fake donut sectors or focus time");
        check("true".equals(js("return document.documentElement.scrollWidth<=innerWidth")),"Multiple chart cards fit a compact phone without horizontal overflow");
        js("document.querySelector('[data-trend-view=line]').click();document.querySelector('.chart-card').scrollIntoView({block:'center'});return true");shot("charts-line");
        js("document.querySelector('.donut-card').scrollIntoView({block:'center'});return true");shot("charts-donut");
        js("document.querySelector('.hour-card').scrollIntoView({block:'center'});return true");shot("charts-hours");
        js("navigate('focus');return true");
    }
    private void whiteMotionChecks() throws Exception {
        check("true".equals(js("return data.appearance.tone==='light'&&data.appearance.glass>=64&&!document.body.classList.contains('theme-dark')")),"Fresh installation uses white glass surfaces and dark readable text");
        check("true".equals(js("return getComputedStyle($('startButton')).backgroundColor==='rgb(255, 255, 255)'&&getComputedStyle($('startButton')).color!=='rgb(255, 255, 255)'")),"Primary timer button uses the white glass palette");
        String motion=js("glassMotion.clear();const revision=data._revision;$('startButton').click();return {status:data.timer.status,delta:data._revision-revision,flows:glassMotion.activeCount,rect:$('startButton').getBoundingClientRect().toJSON(),view:todoView}");report.append("MOTION ").append(motion).append('\n');JSONObject motionState=new JSONObject(motion);
        check(motionState.getString("status").equals("running")&&motionState.getInt("delta")==1&&motionState.getInt("flows")==1,"Click starts a glass flow and executes timer action exactly once");
        Thread.sleep(150);
        check("true".equals(js("const wave=document.querySelector('.glass-flow-wave');return !!wave&&wave.getAnimations().some(a=>a.currentTime>0&&a.playState==='running')")),"Glass ripple is a running expanding animation rather than a static highlight");
        android.graphics.Bitmap frame=getUiAutomation().takeScreenshot();
        try(java.io.FileOutputStream out=new java.io.FileOutputStream(new java.io.File(getTargetContext().getFilesDir(),"white-flow-frame.png"))){frame.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);}
        Thread.sleep(1000);
        check("true".equals(js("return glassMotion.activeCount===0&&!document.querySelector('.glass-flow')")),"Flow overlays clean themselves up after animation");
        js("command('pause');command('reset');glassMotion.clear();return true");
        check("true".equals(js("glassMotion.play($('resetButton'));return glassMotion.activeCount===0")),"Disabled buttons do not animate or accept flow effects");
        check("true".equals(js("for(let i=0;i<20;i++)glassMotion.play($('startButton'));return glassMotion.activeCount===1")),"Repeated taps replace the previous flow instead of accumulating overlays");
        check("true".equals(js("return getComputedStyle(document.querySelector('.glass-flow-layer')).pointerEvents==='none'")),"Glass flow overlay never intercepts button or scroll input");
        js("glassMotion.clear();updateAppearance({liquid:false});return true");
        check("true".equals(js("glassMotion.play($('startButton'));return glassMotion.activeCount===0")),"Disabling liquid glass also disables click flow");
        js("updateAppearance({liquid:true});return true");
        check("true".equals(js("const b=$('startButton'),r=b.getBoundingClientRect();b.dispatchEvent(new PointerEvent('pointerdown',{bubbles:true,isPrimary:true,pointerId:17,button:0,clientX:r.left+20,clientY:r.top+20}));const started=glassMotion.activeCount===1;b.dispatchEvent(new PointerEvent('pointercancel',{bubbles:true,pointerId:17}));return started&&glassMotion.activeCount===0")),"Cancelled touch removes the flow immediately");
        js("document.querySelector('[data-mode=focus]').click();return true");
        check("true".equals(js("return glassMotion.activeCount===1")),"Keyboard and accessible clicks receive the same glass flow feedback");
        js("glassMotion.clear();return true");
    }
    private void enhancementChecks() throws Exception {
        js("closeSheet();configure('down',600,'延时验证');command('start');return true");Thread.sleep(300);
        long before=store.elapsed();
        js("$('extendButton').click();return true");
        check(snap().getJSONObject("timer").getInt("duration")==900,"Quick extension adds five minutes to an active countdown");
        check(store.elapsed()>=before&&store.elapsed()<before+1000,"Extension preserves elapsed time rather than restarting");
        js("command('pause');$('extendButton').click();return true");
        check(snap().getJSONObject("timer").getInt("duration")==1200&&snap().getJSONObject("timer").getString("status").equals("paused"),"Extension also preserves paused state");
        js("$('immerseButton').click();return true");
        check("true".equals(js("return document.body.classList.contains('immersive')&&getComputedStyle(document.querySelector('.bottom-nav')).display==='none'")),"Immersive focus hides navigation without duplicating timer state");shot("immersive-focus");
        check("true".equals(js("return handleBack()&&!document.body.classList.contains('immersive')")),"Android back exits immersive focus before leaving app");
        js("command('reset');configure('break',1);command('start');command('extend',{seconds:2});return true");
        Thread.sleep(1400);check(store.running(),"Extension cancels the old alarm deadline");
        Thread.sleep(2100);check(snap().getJSONObject("timer").getString("status").equals("completed"),"Extended timer completes at its updated deadline");
        js("closeSheet();command('reset');configure('up',1);command('start');return true");
        check(new JSONObject(store.command("{\"action\":\"extend\",\"seconds\":300}")).has("error"),"Stopwatch rejects unnecessary extension");
        js("command('reset');configure('down',86400);command('start');return true");
        check(new JSONObject(store.command("{\"action\":\"extend\",\"seconds\":300}")).has("error"),"Extension rejects durations above 24 hours");
        js("command('reset');configure('focus',1500);window.presetProbe=$('presets').firstElementChild;refresh();refresh();return true");
        check("true".equals(js("return window.presetProbe===$('presets').firstElementChild")),"Timer polling preserves unchanged preset DOM nodes");
        long rev=snap().getLong("_revision");JSONObject delta=new JSONObject(store.poll(rev));
        check(!delta.has("sessions")&&!delta.has("events")&&delta.has("timer"),"Unchanged poll omits full historical arrays");
        cmd("{\"action\":\"reset\"}");check(new JSONObject(store.poll(rev)).has("sessions"),"Revision change sends complete data to UI");
        js("refresh();navigate('stats');return true");
        check("true".equals(js("return $('labelBreakdown').querySelectorAll('[data-label-index]').length===2&&$('preciseTotal').textContent.includes('秒')")),"Statistics show separate task totals including sub-minute durations");
        js("$('labelBreakdown').scrollIntoView({block:'center'});return true");shot("stats-insights");
        js("showDayDetails(localDay());return true");check("true".equals(js("return $('sheetBody').querySelectorAll('.detail-record').length===2")),"Daily detail view contains the real sessions");shot("daily-details");
        js("closeSheet();document.querySelector('[data-chart-day]').click();return true");check("true".equals(js("return $('sheetTitle').textContent.includes('的积累')")),"Chart bars open accessible daily detail views");
        js("closeSheet();showDayDetails(dayOffset(-50));return true");check("true".equals(js("return $('sheetBody').textContent.includes('没有专注记录')")),"Empty dates show an honest empty state");
        js("closeSheet();showLabelDetails(0);return true");check("true".equals(js("return $('sheetBody').querySelectorAll('.detail-record').length===1")),"Task detail filters records within selected statistics range");
        js("closeSheet();navigate('events');return true");check("true".equals(js("return document.querySelector('.bottom-nav').style.getPropertyValue('--nav-index')==='2'&&document.querySelector('[data-page=events]').getAttribute('aria-current')==='page'")),"Sliding glass navigation tracks selected page accessibly");
        check("true".equals(js("const original=data;data={...data,sessions:Array.from({length:4},()=>({end:Date.now(),seconds:1500,mode:'focus',completed:true})),timer:{...data.timer,mode:'focus',status:'completed',completed:true}};const result=roundInfo().long;data=original;return result")),"Four completed pomodoros trigger a long-break recommendation");
        js("const original=data;data={...data,sessions:Array.from({length:4},()=>({end:Date.now(),seconds:1500,mode:'focus',completed:true})),timer:{...data.timer,mode:'focus',status:'completed',completed:true}};showCompletion();data=original;$('completionNext').click();return true");
        check(snap().getJSONObject("timer").getString("mode").equals("break")&&snap().getJSONObject("timer").getInt("duration")==snap().getJSONObject("settings").getInt("long")*60,"Long-break recommendation button configures the chosen rest duration");
        js("navigate('focus');return true");
    }
    private void appearanceChecks() throws Exception {
        js("closeSheet();configure('focus',1500,'自由专注');navigate('focus');$('appearanceButton').click();return true");
        check("true".equals(js("return $('sheetTitle').textContent==='属于你的专注空间'&&!!$('choosePhoto')")),"Appearance panel opens from header");
        js("document.querySelector('[data-preset=\"ocean\"]').click();return true");
        check("true".equals(js("return data.appearance.preset==='ocean'&&document.body.classList.contains('theme-dark')")),"Ocean preset applies dark glass theme");
        js("saveSlider('blur',9);return true");Thread.sleep(100);
        js("saveSlider('glass',38);refresh();return true");
        check("true".equals(js("return data.appearance.blur===9&&data.appearance.glass===38")),"Timer refresh preserves slider draft while dragging");
        Thread.sleep(350);
        check(snap().getJSONObject("appearance").getInt("blur")==9&&snap().getJSONObject("appearance").getInt("glass")==38,"Slider changes are persisted after debounce");
        js("$('liquidToggle').click();return true");check("true".equals(js("return !document.body.classList.contains('liquid')")),"Liquid glass switch can be disabled");
        js("$('liquidToggle').click();return true");
        check("true".equals(js("return document.body.classList.contains('liquid')")),"Liquid glass can be re-enabled");
        js("document.querySelector('button[data-material=eco]').click();return true");
        check("true".equals(js("return data.appearance.material==='eco'&&getComputedStyle(document.querySelector('.timer-panel')).backdropFilter==='none'")),"Lightweight material actually disables backdrop filtering");
        js("document.querySelector('button[data-material=soft]').click();return true");
        check(snap().getJSONObject("appearance").getString("material").equals("soft"),"Material choice persists independently of wallpaper");
        js("document.querySelector('button[data-tone=\"light\"]').click();return true");check("true".equals(js("return !document.body.classList.contains('theme-dark')")),"Text contrast theme is independently adjustable");
        js("document.querySelector('[data-preset=\"night\"]').click();closeSheet();return true");shot("night-glass");
        // Create an owned MediaStore item and import through the exact Activity-result path.
        android.content.Context c=getTargetContext();
        android.graphics.Bitmap fixture=android.graphics.Bitmap.createBitmap(2800,1800,android.graphics.Bitmap.Config.ARGB_8888);
        android.graphics.Canvas canvas=new android.graphics.Canvas(fixture);android.graphics.Paint p=new android.graphics.Paint(3);
        p.setShader(new android.graphics.LinearGradient(0,0,2800,1800,new int[]{0xff234f7a,0xff7ea7b5,0xffe5bb95,0xff315c70},null,android.graphics.Shader.TileMode.CLAMP));canvas.drawRect(0,0,2800,1800,p);p.setShader(null);
        p.setColor(0xfff7d7a2);canvas.drawCircle(1850,540,210,p);
        p.setColor(0xff466c78);android.graphics.Path mountain=new android.graphics.Path();mountain.moveTo(0,1800);mountain.lineTo(0,1270);mountain.lineTo(550,720);mountain.lineTo(1000,1190);mountain.lineTo(1550,870);mountain.lineTo(2200,1350);mountain.lineTo(2800,1040);mountain.lineTo(2800,1800);mountain.close();canvas.drawPath(mountain,p);
        p.setColor(0xff244c60);canvas.drawOval(-200,1430,3200,2250,p);
        java.io.File source=new java.io.File(c.getCacheDir(),"qa-rotated.jpg");
        try(java.io.OutputStream out=new java.io.FileOutputStream(source)){fixture.compress(android.graphics.Bitmap.CompressFormat.JPEG,94,out);}fixture.recycle();
        android.media.ExifInterface exif=new android.media.ExifInterface(source.getPath());exif.setAttribute(android.media.ExifInterface.TAG_ORIENTATION,"6");exif.saveAttributes();
        android.content.ContentValues values=new android.content.ContentValues();values.put(android.provider.MediaStore.Images.Media.DISPLAY_NAME,"Lumi-QA-landscape.jpg");values.put(android.provider.MediaStore.Images.Media.MIME_TYPE,"image/jpeg");
        if(android.os.Build.VERSION.SDK_INT>=29) values.put(android.provider.MediaStore.Images.Media.RELATIVE_PATH,"Pictures/LumiFocusQA");
        android.net.Uri uri=c.getContentResolver().insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI,values);
        if(uri==null) throw new AssertionError("MediaStore fixture creation failed");
        try(java.io.OutputStream out=c.getContentResolver().openOutputStream(uri);java.io.InputStream in=new java.io.FileInputStream(source)){byte[] buf=new byte[16384];int count;while((count=in.read(buf))!=-1)out.write(buf,0,count);}
        js("appearance();return true");
        runOnMainSync(()->activity.onActivityResult(20,Activity.RESULT_OK,new Intent().setData(uri)));
        for(int i=0;i<50;i++){Thread.sleep(100);if(snap().getJSONObject("appearance").optString("preset").equals("photo"))break;}
        check(snap().getJSONObject("appearance").getBoolean("hasImage"),"System picker result imports a private image copy");
        android.graphics.BitmapFactory.Options info=new android.graphics.BitmapFactory.Options();info.inJustDecodeBounds=true;
        try(java.io.InputStream in=WallpaperStore.openImage(c)){android.graphics.BitmapFactory.decodeStream(in,null,info);}
        check(info.outHeight<=2048&&info.outWidth<=2048,"Large imported image is bounded to 2048 pixels");
        check(info.outHeight>info.outWidth,"EXIF camera orientation is preserved correctly");
        c.getContentResolver().delete(uri,null,null);
        try(java.io.InputStream in=WallpaperStore.openImage(c)){check(in.read()!=-1,"Background survives removal of original picker image");}
        js("refresh();closeSheet();window.imageProbeLoaded=false;window.imageProbe=new Image();imageProbe.onload=()=>imageProbeLoaded=true;imageProbe.src='https://lumi.local/wallpaper.jpg?v='+data.appearance.imageVersion;return true");
        for(int i=0;i<30;i++){Thread.sleep(100);if("true".equals(js("return imageProbeLoaded")))break;}
        check("true".equals(js("return imageProbeLoaded&&document.body.classList.contains('photo-background')")),"Private image renders through restricted WebView resource route");
        js("updateAppearance({blur:0,glass:24,dim:24});return true");shot("photo-glass");
        String before=snap().getJSONObject("appearance").getString("imageVersion");
        runOnMainSync(()->activity.onActivityResult(20,Activity.RESULT_CANCELED,null));Thread.sleep(200);
        check(snap().getJSONObject("appearance").getString("imageVersion").equals(before),"Cancelling picker keeps existing background");
        java.io.File invalid=new java.io.File(c.getCacheDir(),"qa-invalid.txt");try(java.io.FileOutputStream out=new java.io.FileOutputStream(invalid)){out.write("not an image".getBytes());}
        boolean rejected=false;try{WallpaperStore.importImage(c,android.net.Uri.fromFile(invalid));}catch(Exception expected){rejected=true;}invalid.delete();
        check(rejected&&snap().getJSONObject("appearance").getString("imageVersion").equals(before),"Invalid image is rejected without replacing the previous background");
        js("appearance();$('removePhoto').click();$('cancelRemovePhoto').click();return true");check(snap().getJSONObject("appearance").getBoolean("hasImage"),"Cancelling removal preserves selected image");
        js("$('removePhoto').click();$('confirmRemovePhoto').click();return true");
        check(!snap().getJSONObject("appearance").getBoolean("hasImage")&&snap().getJSONObject("appearance").getString("preset").equals("white"),"Confirmed removal restores default and deletes image reference");
        check(c.getFilesDir().listFiles((dir,name)->name.startsWith("wallpaper-")).length==0,"Removing wallpaper cleans private image file");
        check(snap().getJSONArray("sessions").length()==2&&snap().getJSONObject("settings").getInt("focus")==35,"Appearance changes preserve timer history and custom settings");
        js("$('resetAppearance').click();closeSheet();return true");
        check(snap().getJSONObject("appearance").getBoolean("liquid")&&snap().getJSONObject("appearance").getInt("blur")==0,"Reset restores appearance defaults without clearing focus data");
        WallpaperStore.importImage(c,android.net.Uri.fromFile(source));source.delete();
        JSONObject oldAppearance=WallpaperStore.state(c),legacy=new JSONObject(oldAppearance.toString());
        legacy.put("paletteVersion",0).put("tone","dark").put("glass",24);
        c.getSharedPreferences("lumi_appearance",0).edit().putString("settings",legacy.toString()).commit();
        JSONObject migrated=WallpaperStore.state(c);
        check(migrated.getString("preset").equals("white")&&migrated.getInt("paletteVersion")==2&&migrated.getString("tone").equals("light")&&migrated.getInt("glass")>=64&&migrated.getString("imageVersion").equals(oldAppearance.getString("imageVersion")),"Upgrading a dark photo theme applies white glass without deleting its image");
        check(snap().getJSONArray("sessions").length()==2&&snap().getJSONObject("settings").getInt("focus")==35,"White palette migration preserves timer history and settings");
        WallpaperStore.update(c,"{\"preset\":\"photo\",\"blur\":3,\"glass\":28,\"position\":44,\"material\":\"soft\"}");
        js("refresh();navigate('focus');return true");shot("photo-glass");
    }
}
