package com.lumifocus.app;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.os.*;
import android.view.*;
import android.webkit.WebView;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.zip.*;

/** Real WebView/native acceptance scenarios for the 2.0 product features. Debug package only. */
final class FeatureChecks {
    private final Instrumentation test;private final MainActivity activity;private final WebView web;private final Context context;private final Store store;
    private final StringBuilder report=new StringBuilder();private String taskId,reviewId;
    FeatureChecks(Instrumentation t,MainActivity a,WebView w){test=t;activity=a;web=w;context=t.getTargetContext();store=Store.get(context);}
    private void check(boolean pass,String name){if(!pass)throw new AssertionError(name);report.append("PASS ").append(name).append('\n');}
    private JSONObject state() throws Exception{return new JSONObject(store.snapshot());}
    private String js(String code) throws Exception{CountDownLatch latch=new CountDownLatch(1);String[] result=new String[1];test.runOnMainSync(()->web.evaluateJavascript("(()=>{"+code+"})()",v->{result[0]=v;latch.countDown();}));if(!latch.await(12,TimeUnit.SECONDS))throw new AssertionError("WebView callback timed out");return result[0];}
    private boolean yes(String code) throws Exception{return "true".equals(js(code));}
    private JSONObject cmd(String raw) throws Exception{JSONObject result=new JSONObject(store.command(raw));if(result.has("error"))throw new AssertionError(result.getString("error"));return result;}
    private void ui(String code) throws Exception{js(code+";return true");}
    private interface Condition{boolean ready() throws Exception;}
    private void await(String name,long limit,Condition condition) throws Exception{long end=SystemClock.elapsedRealtime()+limit;while(SystemClock.elapsedRealtime()<end){if(condition.ready())return;Thread.sleep(80);}throw new AssertionError(name+" timed out");}
    private void shot(String name) throws Exception{ui("$('toast').classList.remove('show')");context.getSystemService(NotificationManager.class).cancel(2);test.waitForIdleSync();Thread.sleep(500);Bitmap image=test.getUiAutomation().takeScreenshot();try(FileOutputStream out=new FileOutputStream(new File(context.getFilesDir(),name+".png"))){image.compress(Bitmap.CompressFormat.PNG,100,out);}image.recycle();}
    String run() throws Exception {
        byte[] original=BackupStore.export(context,new JSONObject().put("trendView","line"));
        try{taskChecks();plannerChecks();repeatChecks();workflowChecks();soundChecks();widgetChecks();clockAndExportChecks();documentChecks();backupChecks();}
        catch(Throwable e){report.append("FAIL ").append(android.util.Log.getStackTraceString(e));}
        finally{
            try{ui("if(largeClockOpen)setLargeClock(false);closeSheet();command('reset')");BackupStore.restore(context,BackupStore.prepare(new ByteArrayInputStream(original)));ui("backupRestored({trendView:'line'});navigate('focus')");}
            catch(Throwable e){report.append("FAIL restore original debug data ").append(android.util.Log.getStackTraceString(e));}
        }
        return report.toString();
    }
    private void taskChecks() throws Exception{
        check(yes("return typeof showWorkflow==='function'&&typeof renderWorkspace==='function'&&typeof buildStatsImage==='function'"),"All feature modules load inside bundled WebView");
        check(new JSONObject(store.command("{\"action\":\"taskSave\",\"value\":{\"title\":\"invalid\",\"priority\":2,\"estimate\":0}}" )).has("error"),"Task validation rejects zero estimated pomodoros");
        ui("closeSheet();navigate('workspace');editTaskForm();$('taskTitle').value='读完第三章';$('taskTag').value='阅读';$('taskPriority').value='1';$('taskEstimate').value=2;$('taskSubtasks').value='先读例子\\n整理笔记';$('saveTask').click()");
        JSONArray tasks=state().getJSONArray("tasks");JSONObject task=tasks.getJSONObject(tasks.length()-1);taskId=task.getString("id");
        check(task.getString("title").equals("读完第三章")&&task.getString("tag").equals("阅读")&&task.getJSONArray("subtasks").length()==2,"Task form persists title, tag, priority, estimate and subtasks");
        check(yes("return document.documentElement.scrollWidth<=innerWidth&&$('workspaceContent').textContent.includes('读完第三章')"),"Task cards fit a compact phone without overflow");
        ui("$('workspaceContent').querySelector('[data-subtask]').click()");check(state().getJSONArray("tasks").getJSONObject(tasks.length()-1).getJSONArray("subtasks").getJSONObject(0).getBoolean("done"),"Subtask checkbox updates native persisted task");
        ui("editTaskForm("+JSONObject.quote(taskId)+");$('taskTitle').value='读完第三章并复习';$('saveTask').click()");check(WorkspaceFeatures.find(state().getJSONArray("tasks"),taskId).getString("title").equals("读完第三章并复习"),"Editing a task retains its stable identity");
        shot("tasks-2.0");
        ui("selectFocusTask("+JSONObject.quote(taskId)+",false);configure('focus',1,undefined,"+JSONObject.quote(taskId)+");command('start')");
        await("task focus completion",5000,()->state().getJSONObject("timer").optString("status").equals("completed"));
        JSONObject s=state().getJSONArray("sessions").getJSONObject(state().getJSONArray("sessions").length()-1);reviewId=s.getString("id");
        check(s.getString("taskId").equals(taskId)&&s.getString("tag").equals("阅读")&&s.getInt("seconds")==1,"Completed timer records selected task identity and exact duration");
        ui("refresh();closeSheet();navigate('workspace')");check(yes("const progress=taskTotals().get("+JSONObject.quote(taskId)+");return progress.pomos===1&&progress.seconds===1"),"Task progress updates from actual completed focus records");
        ui("showSessionReview("+JSONObject.quote(reviewId)+");document.querySelector('[data-mood=calm]').click();$('reviewNote').value='今天迈出了第一步';$('saveReview').click()");
        s=WorkspaceFeatures.find(state().getJSONArray("sessions"),reviewId);check(s.getString("mood").equals("calm")&&s.getString("note").equals("今天迈出了第一步"),"Mood and reflection save on the correct focus record");
        ui("navigate('stats')");check(yes("return $('richStats').textContent.includes('平静投入')&&$('sessionList').textContent.includes('今天迈出了第一步')"),"Statistics and recent records display saved mood and reflection");
        ui("navigate('workspace');const task=data.tasks.find(t=>t.id==="+JSONObject.quote(taskId)+");command('taskSave',{value:{...task,done:true}});taskFilter='done';workspaceKey='';renderWorkspace()");
        check(yes("return $('workspaceContent').textContent.includes('读完第三章并复习')"),"Completed task filter preserves finished tasks");
        ui("const task=data.tasks.find(t=>t.id==="+JSONObject.quote(taskId)+");command('taskSave',{value:{...task,done:false}});taskFilter='open';workspaceKey='';renderWorkspace()");
    }
    private void plannerChecks() throws Exception{
        ui("workspaceView='week';workspaceKey='';renderWorkspace();editPlan();$('planTitle').value='整理本周阅读笔记';$('planMinutes').value=35;$('savePlan').click()");
        JSONArray plans=state().getJSONObject("planner").getJSONArray("items");JSONObject p=plans.getJSONObject(plans.length()-1);String id=p.getString("id");
        check(p.getString("date").equals(LocalDate.now().toString())&&p.getInt("minutes")==35,"Week-plan form persists date and estimated minutes");
        ui("$('workspaceContent').querySelector('[data-plan-toggle]').click()");check(WorkspaceFeatures.find(state().getJSONObject("planner").getJSONArray("items"),id).getBoolean("done"),"Planned-item completion is persisted independently of focus seconds");
        ui("editPlan("+JSONObject.quote(id)+");$('planMinutes').value=45;$('savePlan').click();editWeekGoal();$('weekGoalInput').value=900;$('saveWeekGoal').click()");
        check(state().getJSONObject("planner").getInt("weekGoal")==900&&WorkspaceFeatures.find(state().getJSONObject("planner").getJSONArray("items"),id).getInt("minutes")==45,"Weekly target and planned duration can be edited");
        check(yes("return $('workspaceContent').querySelectorAll('.day-plan').length===7"),"Week plan renders seven calendar days");shot("week-plan-2.0");
        ui("workspaceView='calendar';workspaceKey='';renderWorkspace()");check(yes("return $('workspaceContent').querySelectorAll('[data-study-day]').length===42&&document.documentElement.scrollWidth<=innerWidth"),"Study month shows a complete accessible six-week calendar");shot("calendar-2.0");
        ui("showStudyDay(localDay())");check(yes("return $('sheetBody').textContent.includes('整理本周阅读笔记')&&$('sheetBody').querySelectorAll('.detail-record').length>=2"),"Study-day details combine plans with actual sessions");
        ui("closeSheet();showWeekSummary()");check(yes("return $('sheetBody').textContent.includes('本周计划完成')&&$('sheetBody').textContent.includes('暂停时间不计入')"),"Weekly report distinguishes plan completion and real active time");ui("closeSheet()");
    }
    private void repeatChecks() throws Exception{
        check(CalendarDates.occurrence("2024-02-29","yearly",LocalDate.of(2025,1,1)).toString().equals("2025-02-28")&&CalendarDates.occurrence("2024-02-29","yearly",LocalDate.of(2028,1,1)).toString().equals("2028-02-29"),"Native annual repeat clamps leap day without drifting its anchor");
        check(CalendarDates.occurrence("2024-01-31","monthly",LocalDate.of(2026,2,1)).toString().equals("2026-02-28")&&CalendarDates.occurrence("2024-01-31","monthly",LocalDate.of(2026,3,1)).toString().equals("2026-03-31"),"Native monthly repeat retains the original day after short months");
        check(CalendarDates.occurrence("2026-11-13","weekly",LocalDate.of(2026,11,20)).toString().equals("2026-11-20"),"Native weekly repeat keeps today at zero days");
        ui("navigate('events');editEvent();$('eventTitle').value='每年的期待';$('eventDate').value='2020-01-01';$('eventRepeat').value='yearly';$('saveEvent').click()");
        JSONArray events=state().getJSONArray("events");check(events.getJSONObject(events.length()-1).getString("repeat").equals("yearly"),"Countdown-date editor persists repeat choice");
        check(yes("const e=data.events.find(e=>e.title==='每年的期待');openDayDetail(e.id);const next=LumiFeaturesData.occurrence(e,localDay());const shown=$('dayDetailDate').dateTime;handleBack();return next>=localDay()&&shown===next"),"Repeated countdown detail displays the next occurrence");
    }
    private void workflowChecks() throws Exception{
        ui("closeSheet();command('reset');command('workflowSave',{value:{focus:1,short:1,long:1,rounds:2,auto:true}});command('flowStart')");int before=state().getJSONArray("sessions").length();
        test.runOnMainSync(()->activity.moveTaskToBack(true));
        await("automatic short break",6000,()->{JSONObject f=state().optJSONObject("flowState");return f!=null&&f.optString("phase").equals("short");});
        check(store.running()&&state().getJSONObject("timer").getString("mode").equals("break"),"Automatic workflow enters short break while activity is backgrounded");
        await("automatic second focus",5000,()->{JSONObject f=state().optJSONObject("flowState");return f!=null&&f.optString("phase").equals("focus")&&f.optInt("round")==2;});
        check(store.running(),"Automatic workflow begins the next numbered focus round");
        await("automatic long break",5000,()->{JSONObject f=state().optJSONObject("flowState");return f!=null&&f.optString("phase").equals("long");});check(state().getJSONArray("sessions").length()==before+2,"Final workflow focus is followed by a long break and records only focus");
        await("workflow end",5000,()->state().optJSONObject("flowState")==null);Thread.sleep(300);
        check(!store.running()&&state().getJSONArray("sessions").length()==before+2,"Automatic workflow stops after its configured rounds without duplicate history");
        context.startActivity(new Intent(context,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_REORDER_TO_FRONT));Thread.sleep(300);
        ui("refresh();closeSheet();command('workflowSave',{value:{focus:1,short:2,long:1,rounds:2,auto:false}});command('flowStart')");
        await("manual next phase",5000,()->{JSONObject f=state().optJSONObject("flowState");return f!=null&&f.optBoolean("waiting");});int saved=state().getJSONArray("sessions").length();Thread.sleep(1400);
        check(!store.running()&&state().getJSONArray("sessions").length()==saved,"Manual workflow waits for explicit start and never invents later rounds");
        ui("refresh();closeSheet();navigate('focus')");check(yes("return !$('flowBanner').hidden&&$('flowBanner').textContent.includes('短休息')"),"Focus page shows the pending workflow stage");shot("workflow-2.0");
        ui("command('flowStop')");check(state().optJSONObject("flowState")==null&&!store.running(),"Stopping a workflow preserves previously completed sessions");
        check(new JSONObject(store.command("{\"action\":\"workflowSave\",\"value\":{\"focus\":0,\"short\":1,\"long\":1,\"rounds\":13}}" )).has("error"),"Workflow rejects invalid durations and round limits");
    }
    private void soundChecks() throws Exception{
        for(int channel=0;channel<3;channel++){AmbientEngine engine=new AmbientEngine();int[] levels={0,0,0};levels[channel]=70;engine.levels(levels);short[] pcm=engine.samples(4096);long energy=0;for(short sample:pcm)energy+=Math.abs((int)sample);check(energy>0,"Soundscape channel "+channel+" produces actual bounded PCM");engine.close();}
        AmbientEngine silent=new AmbientEngine();silent.levels(new int[]{0,0,0});boolean zero=true;for(short sample:silent.samples(1024))zero&=sample==0;check(zero,"Zero mixer volumes produce silence");silent.close();
        ui("showSounds();$('sound-rain').value=30;$('sound-forest').value=20;$('sound-cafe').value=10;$('soundPreview').click()");await("ambient playback",5000,AmbientService::active);
        check(!state().getJSONObject("mixer").getBoolean("followTimer")&&Arrays.equals(store.audioMix(),new int[]{30,20,10}),"Independent preview starts a native media foreground service with mixed volumes");shot("sounds-2.0");
        ui("$('soundMaster').click()");await("ambient stop",5000,()->!AmbientService.active());check(!state().getJSONObject("mixer").getBoolean("playing"),"Stopping sounds releases playback and persists the stopped preference");
        ui("closeSheet();command('mixerSave',{value:{rain:20,forest:0,cafe:0,playing:true,followTimer:true}});configure('down',2);command('start')");await("follow timer audio",4000,AmbientService::active);check(store.running(),"Follow-timer mode starts sounds when focus starts");
        await("follow timer completion",5000,()->!store.running());await("follow timer silence",4000,()->!AmbientService.active());check(!store.audioWanted(),"Follow-timer mode stops actual playback when focus ends");ui("refresh();closeSheet();command('mixerSave',{value:{rain:20,forest:0,cafe:0,playing:false,followTimer:true}});command('reset')");
    }
    private void widgetChecks() throws Exception{
        ui("configure('focus',60)");final View[] widget=new View[1];test.runOnMainSync(()->{try{widget[0]=FocusWidget.views(context).apply(context,new FrameLayout(context));}catch(Exception e){throw new RuntimeException(e);}});
        check(((Chronometer)widget[0].findViewById(R.id.widget_clock)).isCountDown()&&((TextView)widget[0].findViewById(R.id.widget_today)).getText().toString().contains("目标"),"Widget RemoteViews renders native countdown and real daily progress");
        check(((TextView)widget[0].findViewById(R.id.widget_event)).getText().toString().contains("每年的期待"),"Widget selects the next repeated countdown event");
        test.runOnMainSync(()->widget[0].findViewById(R.id.widget_toggle).performClick());await("widget start",4000,store::running);check(store.running(),"Widget PendingIntent starts the shared native timer");
        test.runOnMainSync(()->widget[0].findViewById(R.id.widget_toggle).performClick());await("widget pause",4000,()->!store.running());check(state().getJSONObject("timer").getString("status").equals("paused"),"Widget PendingIntent pauses the same timer without another instance");
        ui("refresh();command('reset');settings();$('widgetSettings').click()");check(yes("return !!$('pinFocusWidget')&&$('sheetBody').textContent.includes('长按手机桌面')"),"Settings exposes widget pinning and launcher instructions");ui("closeSheet()");
    }
    private void clockAndExportChecks() throws Exception{
        ui("navigate('focus');setLargeClock(true)");await("landscape clock",4000,()->activity.getRequestedOrientation()==android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
        await("landscape viewport",5000,()->yes("return innerWidth>innerHeight"));Thread.sleep(350);
        check(yes("return !$('largeClock').hidden&&largeClockOpen&&$('clockTime').textContent==='01:00'"),"Large clock uses the actual timer and requests landscape orientation");
        check(yes("return $('clockDigits').querySelectorAll('.flip-digit').length===4&&getComputedStyle($('largeClock')).backgroundColor==='rgb(0, 0, 0)'"),"Default landscape clock renders four split-flap cards on pure black");
        check(yes("return $('clockDigits').getAttribute('aria-hidden')==='true'&&$('clockTime').getAttribute('role')==='timer'"),"Flip digits preserve a single accessible timer value");
        ui("clearTimeout(largeClockTimeout);largeClockOpen=false;flipClock.stop();flipClock.render('19:59',false)");
        check(yes("flipClock.render('20:00');return document.querySelectorAll('.flip-digit.flipping').length===4"),"Minute carry animates every changed split-flap exactly once");
        Thread.sleep(100);
        check(yes("return Array.from(document.querySelectorAll('.flip-out')).some(e=>e.getAnimations().some(a=>a.playState==='running'&&a.currentTime>0))"),"Changing digits run actual three-dimensional flap animations");
        Thread.sleep(500);
        check(yes("return !document.querySelector('.flipping')&&Array.from(document.querySelectorAll('.flip-bottom:not(.flip-in)>span')).map(e=>e.textContent).join('')==='2000'"),"Animation settles into the new digits and removes moving layers");
        check(yes("const first=$('clockDigits').firstChild;flipClock.render('20:00');return first===$('clockDigits').firstChild&&!document.querySelector('.flipping')"),"Unchanged seconds reuse cards without replaying animations");
        check(yes("flipClock.render('20:01');return document.querySelectorAll('.flipping').length===1"),"A single changed second animates only its own digit");
        check(yes("flipClock.stop();return !document.querySelector('.flipping')&&Array.from(document.querySelectorAll('.flip-bottom:not(.flip-in)>span')).map(e=>e.textContent).join('')==='2001'"),"Stopping the clock immediately settles pending flips");
        check(yes("flipClock.render('01:00:00',false);return document.querySelectorAll('.flip-digit').length===6&&!document.querySelector('.flipping')"),"Hour rollover rebuilds the wider clock without stale animation");
        check(yes("const r=$('clockDigits').getBoundingClientRect();return r.left>=0&&r.right<=innerWidth&&r.bottom<=innerHeight"),"Six-digit flip clock fits the landscape display");
        ui("largeClockOpen=true;updateLargeClock();tickLargeClock()");shot("flip-clock-2.0.2");
        ui("document.querySelector('[data-clock-face=clock]').click();$('clockStyleButton').click()");check(yes("return data.preferences.clockFace==='clock'&&$('largeClock').dataset.style==='ring'&&$('clockTime').textContent.split(':').length===3"),"Large clock can switch to wall time and a persisted ring style");shot("large-clock-2.0");
        check(yes("return handleBack()&&!largeClockOpen&&$('largeClock').hidden"),"Android back exits large clock before leaving the application");Thread.sleep(350);
        ui("navigate('stats');document.querySelector('[data-range=all]').click()");
        check(yes("return $('richStats').textContent.includes('阅读')&&$('richStats').textContent.includes('较上周')===false"),"Tag statistics use saved task tags and honest empty comparison");
        String encoded=(String)new JSONTokener(js("return buildStatsImage().toDataURL('image/png')")).nextValue();byte[] png=MainActivity.decodeStatsImage(encoded);BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;BitmapFactory.decodeByteArray(png,0,png.length,bounds);
        check(bounds.outWidth==1080&&bounds.outHeight==1560,"Statistics export builds a real full-resolution PNG from current records");try(FileOutputStream out=new FileOutputStream(new File(context.getFilesDir(),"stats-export-2.0.png"))){out.write(png);}shot("rich-stats-2.0");
        boolean rejected=false;try{MainActivity.decodeStatsImage("data:text/html;base64,PHNjcmlwdD4=");}catch(IllegalArgumentException e){rejected=true;}check(rejected,"Image export rejects non-PNG payloads");
    }
    private static byte[] zip(String name,byte[] bytes) throws Exception{ByteArrayOutputStream out=new ByteArrayOutputStream();try(ZipOutputStream zip=new ZipOutputStream(out)){zip.putNextEntry(new ZipEntry(name));zip.write(bytes);zip.closeEntry();}return out.toByteArray();}
    private void documentChecks() throws Exception {
        android.net.Uri[] output={null};List<String> intents=new CopyOnWriteArrayList<>();
        Instrumentation.ActivityMonitor monitor=new Instrumentation.ActivityMonitor(){
            @Override public Instrumentation.ActivityResult onStartActivity(Intent intent){
                if(Intent.ACTION_CREATE_DOCUMENT.equals(intent.getAction())||Intent.ACTION_OPEN_DOCUMENT.equals(intent.getAction())){
                    intents.add(intent.getAction()+":"+intent.getType());
                    return new Instrumentation.ActivityResult(Activity.RESULT_OK,new Intent().setData(output[0]).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION));
                }return null;
            }
        };
        List<android.net.Uri> files=new ArrayList<>();test.addMonitor(monitor);
        try {
            android.content.ContentValues values=new android.content.ContentValues();values.put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME,"lumi-qa-"+System.currentTimeMillis()+".zip");values.put(android.provider.MediaStore.MediaColumns.MIME_TYPE,"application/zip");values.put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH,Environment.DIRECTORY_DOWNLOADS+"/LumiFocus-QA");
            output[0]=context.getContentResolver().insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI,values);if(output[0]==null)throw new AssertionError("No document output URI");files.add(output[0]);
            ui("closeSheet();command('reset');showBackupPanel();$('exportFullBackup').click()");
            await("complete ZIP document write",8000,()->{try(InputStream in=context.getContentResolver().openInputStream(output[0])){return in!=null&&BackupStore.read(in,24*1024*1024).length>100;}catch(IOException e){return false;}});
            BackupStore.Prepared backup;try(InputStream in=context.getContentResolver().openInputStream(output[0])){backup=BackupStore.prepare(in);}
            check(backup.data.getJSONArray("tasks").length()>0&&intents.contains(Intent.ACTION_CREATE_DOCUMENT+":application/zip"),"Complete export writes a valid ZIP through the actual document-result callback");
            ui("$('importFullBackup').click()");await("document import preview",8000,()->yes("return !!$('confirmRestoreBackup')"));
            check(intents.contains(Intent.ACTION_OPEN_DOCUMENT+":*/*")&&yes("return $('sheetBody').textContent.includes('专注记录')"),"Selected document URI is parsed asynchronously and opens a real restore preview");
            long revision=state().getLong("_revision");ui("$('confirmRestoreBackup').click()");await("confirmed document restore",8000,()->state().getLong("_revision")>revision&&yes("return $('sheetOverlay').hidden"));
            check(WorkspaceFeatures.find(state().getJSONArray("tasks"),taskId)!=null,"Confirmed file restore uses the previewed backup and refreshes native UI state");
            values.put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME,"lumi-qa-"+System.currentTimeMillis()+".png");values.put(android.provider.MediaStore.MediaColumns.MIME_TYPE,"image/png");output[0]=context.getContentResolver().insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI,values);files.add(output[0]);
            ui("navigate('stats');$('exportStatsButton').click()");
            await("PNG document write",8000,()->{try(InputStream in=context.getContentResolver().openInputStream(output[0])){return in!=null&&BackupStore.read(in,8*1024*1024).length>100;}catch(IOException e){return false;}});
            BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;try(InputStream in=context.getContentResolver().openInputStream(output[0])){BitmapFactory.decodeStream(in,null,bounds);}
            check(bounds.outWidth==1080&&bounds.outHeight==1560&&intents.contains(Intent.ACTION_CREATE_DOCUMENT+":image/png"),"Statistics PNG is written through the document callback at its full dimensions");
            values.put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME,"lumi-qa-"+System.currentTimeMillis()+".json");values.put(android.provider.MediaStore.MediaColumns.MIME_TYPE,"application/json");output[0]=context.getContentResolver().insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI,values);files.add(output[0]);
            ui("showBackupPanel();$('exportLegacyJson').click()");await("JSON document write",8000,()->{try(InputStream in=context.getContentResolver().openInputStream(output[0])){return in!=null&&BackupStore.read(in,16*1024*1024).length>100;}catch(IOException e){return false;}});
            JSONObject exported;try(InputStream in=context.getContentResolver().openInputStream(output[0])){exported=new JSONObject(new String(BackupStore.read(in,16*1024*1024),java.nio.charset.StandardCharsets.UTF_8));}
            check(exported.has("tasks")&&exported.has("sessions")&&intents.contains(Intent.ACTION_CREATE_DOCUMENT+":application/json"),"JSON archive also uses the document callback and includes new product data");ui("closeSheet()");
        } finally {test.removeMonitor(monitor);for(android.net.Uri uri:files)if(uri!=null)context.getContentResolver().delete(uri,null,null);}
    }
    private void backupChecks() throws Exception{
        ui("closeSheet();command('reset')");
        Bitmap bitmap=Bitmap.createBitmap(48,64,Bitmap.Config.ARGB_8888);bitmap.eraseColor(Color.rgb(186,210,225));ByteArrayOutputStream jpg=new ByteArrayOutputStream();bitmap.compress(Bitmap.CompressFormat.JPEG,90,jpg);bitmap.recycle();
        JSONObject appearance=WallpaperStore.state(context).put("preset","photo").put("blur",3).put("position",44);WallpaperStore.restore(context,appearance,jpg.toByteArray());
        byte[] bytes=BackupStore.export(context,new JSONObject().put("trendView","line"));BackupStore.Prepared backup=BackupStore.prepare(new ByteArrayInputStream(bytes));
        check(backup.summary().getBoolean("hasImage")&&backup.data.getJSONArray("tasks").length()>0&&backup.data.getJSONObject("planner").getJSONArray("items").length()>0,"Complete ZIP includes tasks, plans, sessions, preferences and photograph");
        check(Arrays.equals(backup.image,jpg.toByteArray())&&!backup.data.getJSONObject("mixer").getBoolean("playing"),"Backup preserves exact photo bytes and restores audio as stopped");
        JSONObject original=state();long revision=original.getLong("_revision");
        boolean rejected=false;try{BackupStore.prepare(new ByteArrayInputStream(zip("../outside.json","{}".getBytes(java.nio.charset.StandardCharsets.UTF_8))));}catch(Exception e){rejected=true;}
        check(rejected&&state().getLong("_revision")==revision,"Unexpected ZIP paths are rejected before any data mutation");
        rejected=false;try{BackupStore.prepare(new ByteArrayInputStream("{\"timer\":{}}".getBytes(java.nio.charset.StandardCharsets.UTF_8)));}catch(Exception e){rejected=true;}check(rejected&&state().getLong("_revision")==revision,"Invalid backup schema leaves current data intact");
        JSONObject invalid=new JSONObject(original.toString());invalid.getJSONArray("sessions").getJSONObject(0).put("seconds",-1);rejected=false;try{WorkspaceFeatures.validateBackup(invalid);}catch(Exception e){rejected=true;}check(rejected,"Backup validation rejects corrupted negative focus records");
        rejected=false;try{BackupStore.read(new ByteArrayInputStream(new byte[1025]),1024);}catch(IOException e){rejected=true;}check(rejected,"Backup stream enforces its byte limit before allocation can grow unbounded");
        JSONObject running=new JSONObject(original.toString());running.getJSONObject("timer").put("status","running").put("elapsedNow",1234);JSONObject paused=WorkspaceFeatures.validateBackup(running);check(paused.getJSONObject("timer").getString("status").equals("paused")&&paused.getJSONObject("timer").getLong("elapsed")==1234&&!paused.has("flowState"),"Restored running timers become paused and never restart workflow rounds");
        ui("command('start')");rejected=false;try{BackupStore.restore(context,backup);}catch(Exception e){rejected=true;}check(rejected&&store.running(),"Restore is blocked while a timer is active");ui("command('reset')");
        cmd(new JSONObject().put("action","taskDelete").put("id",taskId).toString());
        check(!state().getJSONObject("timer").optString("taskId").equals(taskId)&&WorkspaceFeatures.find(state().getJSONArray("sessions"),reviewId)!=null,"Deleting the selected idle task releases its timer link and preserves history");
        backup.data.put("_historyRevision",Long.MAX_VALUE).put("_revision",Long.MAX_VALUE);
        WallpaperStore.removeImage(context);BackupStore.restore(context,backup);
        JSONObject restored=state();check(WorkspaceFeatures.find(restored.getJSONArray("tasks"),taskId)!=null&&WorkspaceFeatures.find(restored.getJSONArray("sessions"),reviewId).getString("note").equals("今天迈出了第一步"),"Backup round trip restores task identity, focus records, mood and reflection");
        check(restored.getLong("_historyRevision")>0&&restored.getLong("_historyRevision")<Long.MAX_VALUE&&restored.getLong("_revision")<Long.MAX_VALUE,"Restore regenerates local revision counters instead of trusting imported counters");
        try(InputStream in=WallpaperStore.openImage(context)){check(Arrays.equals(BackupStore.read(in,8*1024*1024),jpg.toByteArray())&&WallpaperStore.state(context).getInt("position")==44,"Backup round trip restores the photo file and its appearance settings");}
        ui("backupRestored({trendView:'line'});showBackupPanel()");check(yes("return !!$('exportFullBackup')&&!!$('importFullBackup')&&$('sheetBody').textContent.includes('ZIP')"),"Backup settings exposes complete export, preview import and JSON archive");
        ui("backupReady({sessions:10,tasks:3,plans:2,events:1,hasImage:true})");check(yes("return !!$('confirmRestoreBackup')&&$('sheetBody').textContent.includes('替换当前记录')"),"Restore preview presents counts and requires an explicit replace action");shot("backup-preview-2.0");ui("$('cancelRestoreBackup').click()");check(state().getJSONArray("tasks").length()==restored.getJSONArray("tasks").length(),"Cancelling restore preview preserves current task data");
    }
}
