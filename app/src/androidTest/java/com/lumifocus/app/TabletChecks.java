package com.lumifocus.app;

import android.app.*;
import android.content.*;
import android.content.pm.ActivityInfo;
import android.graphics.Bitmap;
import android.os.*;
import android.view.*;
import android.view.inputmethod.InputMethodManager;
import android.webkit.WebView;
import org.json.*;
import java.io.*;
import java.time.LocalDate;
import java.util.concurrent.*;

/** Window-geometry and interaction checks. Runs in the separate Debug app only. */
final class TabletChecks {
    private final Instrumentation test;private final MainActivity activity;private final WebView web;private final Context context;private final Store store;
    private final StringBuilder report=new StringBuilder();private String profile;
    TabletChecks(Instrumentation t,MainActivity a,WebView w){test=t;activity=a;web=w;context=t.getTargetContext();store=Store.get(context);}
    private String js(String code)throws Exception{CountDownLatch ready=new CountDownLatch(1);String[] result=new String[1];test.runOnMainSync(()->web.evaluateJavascript("(()=>{"+code+"})()",value->{result[0]=value;ready.countDown();}));if(!ready.await(10,TimeUnit.SECONDS))throw new AssertionError("WebView callback timeout");return result[0];}
    private void ui(String code)throws Exception{js(code+";return true");}
    private void check(String code,String label)throws Exception{if(!"true".equals(js(code)))throw new AssertionError(label);report.append("PASS ").append(label).append('\n');}
    private void cmd(JSONObject command)throws Exception{JSONObject result=new JSONObject(store.command(command.toString()));if(result.has("error"))throw new AssertionError(result.getString("error"));}
    private void await(String code)throws Exception{for(int i=0;i<50;i++){if("true".equals(js(code)))return;Thread.sleep(100);}throw new AssertionError("Timed out: "+code);}
    private void shot(String name)throws Exception{ui("$('toast').classList.remove('show');glassMotion.clear()");test.waitForIdleSync();Thread.sleep(500);Bitmap bitmap=test.getUiAutomation().takeScreenshot();try(FileOutputStream out=new FileOutputStream(new File(context.getFilesDir(),"tablet-"+profile+"-"+name+".png"))){bitmap.compress(Bitmap.CompressFormat.PNG,100,out);}bitmap.recycle();}
    private void tap(String id)throws Exception{
        JSONArray point=new JSONArray(js("const r=$("+JSONObject.quote(id)+").getBoundingClientRect();return [r.left+r.width/2,r.top+r.height/2]"));int[] offset=new int[2];float[] scale=new float[1];test.runOnMainSync(()->{web.getLocationOnScreen(offset);scale[0]=web.getScale();});
        float x=offset[0]+(float)point.getDouble(0)*scale[0],y=offset[1]+(float)point.getDouble(1)*scale[0];long now=SystemClock.uptimeMillis();MotionEvent down=MotionEvent.obtain(now,now,MotionEvent.ACTION_DOWN,x,y,0),up=MotionEvent.obtain(now,now+50,MotionEvent.ACTION_UP,x,y,0);
        try{test.sendPointerSync(down);test.sendPointerSync(up);}finally{down.recycle();up.recycle();}
    }
    String run(String name)throws Exception{
        profile=name;if(!context.getPackageName().endsWith(".debug"))throw new AssertionError("Debug package required");
        byte[] original=BackupStore.export(context,new JSONObject().put("trendView","line"));
        try{
            ui("if(largeClockOpen)setLargeClock(false);setImmersive(false);closeSheet();command('reset')");
            WallpaperStore.update(context,"{\"preset\":\"white\",\"tone\":\"light\"}");
            seed();ui("refresh();navigate('focus')");Thread.sleep(600);
            report.append("VIEWPORT ").append(profile).append(' ').append(js("return {width:innerWidth,height:innerHeight,dpr:devicePixelRatio}")).append('\n');
            if(profile.equals("landscape"))check("return innerWidth>=1600&&innerHeight>=1000","Pad 3 Pro landscape geometry is active");
            if(profile.equals("portrait"))check("return innerWidth>=1100&&innerHeight>innerWidth","Pad 3 Pro portrait geometry is active");
            if(profile.equals("split"))check("return innerWidth>=800&&innerWidth<900","Half-width split-window geometry is active");
            if(profile.equals("narrow"))check("return innerWidth>=500&&innerWidth<700","Narrow freeform-window geometry is active");
            if(profile.equals("scaled"))check("return innerWidth>=1300&&innerWidth<1400","Larger display-scale geometry is active");
            if(profile.equals("short"))check("return innerWidth>=900&&innerHeight<560","Short freeform-window geometry is active");
            if(profile.equals("phone"))check("return innerWidth<400&&innerHeight>=600","Compact phone geometry is active");
            check("return document.documentElement.scrollWidth<=innerWidth+1","Focus page has no horizontal overflow");
            check("return getComputedStyle(document.body).backgroundColor==='rgb(255, 255, 255)'","Tablet layout preserves the pure white theme");
            check("const grid=document.querySelector('.focus-layout');return getComputedStyle(grid).display===(innerWidth>=900?'grid':'block')","Focus columns follow the actual window width");
            check("const a=document.querySelector('.focus-primary').getBoundingClientRect(),b=document.querySelector('.focus-secondary').getBoundingClientRect();return innerWidth>=900?b.left>=a.right+20:b.top>=a.bottom","Focus controls and daily overview occupy separate usable regions");
            check("const rail=innerWidth>=900&&innerHeight>=560;return getComputedStyle(document.querySelector('.bottom-nav')).flexDirection===(rail?'column':'row')","Navigation switches between side rail and bottom bar");
            check("return $('startButton').getBoundingClientRect().height>=44&&$('largeClockButton').getBoundingClientRect().height>=44","Primary touch controls retain usable hit areas");
            shot("focus");
            ui("$('startButton').click()");check("return data.timer.status==='running'","Start control still starts the native timer in the tablet layout");ui("$('startButton').click()");check("return data.timer.status==='paused'","Pause control still pauses the same timer");ui("command('reset');setImmersive(true)");
            check("return getComputedStyle(document.querySelector('.focus-secondary')).display==='none'&&getComputedStyle(document.querySelector('.bottom-nav')).display==='none'&&document.documentElement.scrollWidth<=innerWidth+1","Immersive focus hides the new sidebar without overflow");ui("setImmersive(false);navigate('workspace');workspaceView='tasks';taskFilter='all';workspaceKey='';renderWorkspace()");
            check("return document.querySelectorAll('.task-card').length>=2&&document.documentElement.scrollWidth<=innerWidth+1","Task cards remain readable without horizontal scrolling");
            check("const cards=[...document.querySelectorAll('.task-card')].slice(0,2).map(e=>e.getBoundingClientRect());return innerWidth>=700?Math.abs(cards[0].top-cards[1].top)<2:cards[1].top>=cards[0].bottom","Tasks use a grid only when the window has enough width");shot("tasks");
            ui("document.querySelector('[data-workspace-view=week]').click()");check("return document.querySelectorAll('.day-plan').length===7&&document.documentElement.scrollWidth<=innerWidth+1","All seven day plans fit the responsive week grid");
            ui("document.querySelector('[data-workspace-view=calendar]').click()");check("return document.querySelectorAll('[data-study-day]').length===42&&document.documentElement.scrollWidth<=innerWidth+1","Study calendar keeps all 42 days within the window");shot("calendar");
            ui("navigate('events')");check("const hero=$('featuredDay').getBoundingClientRect(),list=$('eventList').getBoundingClientRect();return document.querySelectorAll('.day-row').length>=2&&(innerWidth>=900?list.left>=hero.right:list.top>=hero.bottom-1)","Countdown highlights sit beside the list on wide screens and above it on phones");
            check("return document.documentElement.scrollWidth<=innerWidth+1","Countdown page stays within the window");
            ui("navigate('stats');setTrendView('line')");await("return $('lineChart').querySelector('svg')!==null");Thread.sleep(250);
            check("return getComputedStyle(document.querySelector('.stats-grid')).display===(innerWidth>=700?'grid':'block')","Statistics use the tablet chart grid at the correct breakpoint");
            check("const svg=$('lineChart').querySelector('svg'),r=svg.getBoundingClientRect(),card=svg.closest('.chart-card').getBoundingClientRect();return r.left>=card.left&&r.right<=card.right+1&&r.bottom<=$('chartCaption').getBoundingClientRect().top+1","Responsive line chart stays inside its card without covering the caption");
            check("return document.documentElement.scrollWidth<=innerWidth+1","Statistics page has no horizontal overflow");shot("stats");
            referenceStats();
            ui("$('lineChart').querySelector('.line-point').dispatchEvent(new MouseEvent('click',{bubbles:true}))");check("return !$('sheetOverlay').hidden&&$('sheetTitle').textContent.length>0","Chart points still open the matching detail dialog");ui("closeSheet();settings()");Thread.sleep(350);
            check("const r=document.querySelector('.sheet').getBoundingClientRect();return r.left>=0&&r.right<=innerWidth+1&&r.top>=0&&r.bottom<=innerHeight+1&&(innerWidth<700||Math.abs((r.left+r.right)/2-innerWidth/2)<2)","Dialogs fit the visible window and center on tablets");shot("settings");ui("closeSheet()");
            if(profile.equals("landscape")){keyboard();liveResize();gallery();}
            photoGlass();
            solidThemes();
            sceneChecks();
            ui("navigate('focus');configure('up',0);command('start');command('clockPreferences',{style:'digital',face:'clock'});setLargeClock(true)");
            await("return innerWidth>innerHeight&&document.querySelectorAll('.flip-digit').length===6");Thread.sleep(500);
            check("const r=$('clockDigits').getBoundingClientRect();return r.left>=0&&r.right<=innerWidth+1&&r.top>=0&&r.bottom<=innerHeight+1&&getComputedStyle($('largeClock')).backgroundColor==='rgb(0, 0, 0)'","Black flip clock fits all six digits after the landscape transition");
            check("return data.timer.status==='running'&&$('clockTime').textContent.split(':').length===3","Rotation and clock display leave the running stopwatch intact");shot("flip");
            check("return handleBack()&&!largeClockOpen&&$('largeClock').hidden&&data.timer.status==='running'","Back exits the flip clock without stopping the timer");
            check("return document.body.dataset.wallpaper==='white'&&!document.body.classList.contains('clock-mode')","Returning from the clock restores the white application");
            report.append("TABLET_DONE ").append(profile).append('\n');
        }catch(Throwable error){report.append("FAIL ").append(android.util.Log.getStackTraceString(error));}
        finally{
            try{test.runOnMainSync(()->((InputMethodManager)context.getSystemService(Context.INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(web.getWindowToken(),0));ui("if(largeClockOpen)setLargeClock(false);closeSheet();setImmersive(false);command('reset')");BackupStore.restore(context,BackupStore.prepare(new ByteArrayInputStream(original)));ui("backupRestored({trendView:'line'});navigate('focus')");}catch(Throwable error){report.append("FAIL debug restore ").append(android.util.Log.getStackTraceString(error));}
        }
        return report.toString();
    }
    private void keyboard()throws Exception{
        ui("editTaskForm();$('taskTitle').scrollIntoView({block:'center'})");double before=Double.parseDouble(js("return innerHeight"));tap("taskTitle");
        test.runOnMainSync(()->((InputMethodManager)context.getSystemService(Context.INPUT_METHOD_SERVICE)).showSoftInput(web,InputMethodManager.SHOW_IMPLICIT));
        Thread.sleep(1200);report.append("KEYBOARD ").append(js("return {active:document.activeElement.id,height:innerHeight,visual:visualViewport.height}" )).append('\n');shot("keyboard-attempt");
        await("return innerHeight<"+(before-80));
        check("const r=$('taskTitle').getBoundingClientRect();return document.activeElement===$('taskTitle')&&r.top>=0&&r.bottom<=innerHeight&&document.documentElement.scrollWidth<=innerWidth+1","Soft keyboard resizes the dialog and keeps the focused field visible");shot("keyboard");
        test.runOnMainSync(()->((InputMethodManager)context.getSystemService(Context.INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(web.getWindowToken(),0));ui("$('taskTitle').blur();closeSheet()");await("return innerHeight>="+(before-2));
    }
    private void shell(String command)throws Exception{
        try(android.os.ParcelFileDescriptor descriptor=test.getUiAutomation().executeShellCommand(command);InputStream stream=new android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor)){byte[] buffer=new byte[1024];while(stream.read(buffer)!=-1){}}
    }
    private void liveResize()throws Exception{
        ui("navigate('focus');configure('up',0);command('start');editTaskForm();$('taskTitle').value='窗口调整后仍然保留';window.tabletResizeSentinel=true");
        try{
            shell("wm size 1120x2000");await("return innerWidth>=950&&innerWidth<=1050&&innerHeight<560");
            check("return window.tabletResizeSentinel&&$('taskTitle').value==='窗口调整后仍然保留'&&data.timer.status==='running'&&document.documentElement.scrollWidth<=innerWidth+1","Resizing a live window retains the unsaved form and running timer");shot("live-resize");
        }finally{shell("wm size 2400x3392");}
        await("return innerWidth>=1600&&innerHeight>=1000");
        check("return $('taskTitle').value==='窗口调整后仍然保留'&&data.timer.status==='running'","Expanding the same window preserves the form and timer again");
        ui("closeSheet();command('reset')");
    }
    private void seed()throws Exception{
        for(String title:new String[]{"阅读 · 完成下一章","复习 · 整理学习笔记"})cmd(new JSONObject().put("action","taskSave").put("value",new JSONObject().put("title",title).put("tag","学习").put("priority",2).put("estimate",2)));
        for(int i=0;i<3;i++)cmd(new JSONObject().put("action","planSave").put("value",new JSONObject().put("title",i==0?"阅读与笔记":"回顾今天的收获").put("date",LocalDate.now().plusDays(i).toString()).put("minutes",30)));
        for(int i=0;i<2;i++)cmd(new JSONObject().put("action","eventSave").put("value",new JSONObject().put("title",i==0?"下一次旅行":"学习计划完成日").put("date",LocalDate.now().plusDays(12+i*10).toString()).put("category","生活期待").put("color","green")));
    }
    private void referenceStats()throws Exception{
        check("return !!$('statsWallpaperButton')&&!!$('statsGlassButton')&&!!$('referenceAllTime')","Reference-style statistics expose photo, glass, lifetime and daily controls");
        check("const expected=historyIndex().rows.filter(r=>r.day<=localDay()).length;return Number($('referenceAllCount').textContent)===expected","Lifetime count uses all actual focus records independently of the range tabs");
        check("return $('referenceNextDay').disabled&&$('referenceDayInput').value===localDay()","Daily overview opens on today and blocks future navigation");
        ui("moveReferenceDay(-1)");check("return $('referenceDayInput').value===LumiChartsData.indexKey(LumiChartsData.dayIndex(localDay())-1)","Daily arrows select the preceding local calendar date");ui("$('referenceTodayButton').click();referenceMonth='2024-02';referenceYear='2024';renderReferenceStats()");
        check("return $('reference-month-chart').querySelectorAll('[data-calendar-key]').length===29&&$('reference-year-chart').querySelectorAll('[data-calendar-key]').length===12","Monthly and annual charts retain leap-day and twelve-month buckets");
        ui("moveReferencePeriod('month',-1);moveReferencePeriod('year',-1)");check("return referenceMonth==='2024-01'&&referenceYear==='2023'","Calendar arrows browse real archived months and years");
        ui("referenceMonth=localDay().slice(0,7);referenceYear=localDay().slice(0,4);renderReferenceStats();$('reference-month-chart').scrollIntoView({block:'center'})");
        check("const cards=[...document.querySelectorAll('.reference-calendar')];return cards.every(c=>{const r=c.getBoundingClientRect();return r.left>=0&&r.right<=innerWidth+1})&&document.documentElement.scrollWidth<=innerWidth+1","Calendar statistics stay inside phone, tablet and split windows");shot("calendar-stats");
        ui("$('reference-month-chart').querySelector('[data-calendar-key]').dispatchEvent(new MouseEvent('click',{bubbles:true}))");check("return !$('sheetOverlay').hidden&&$('sheetTitle').textContent.includes(referenceMonth)","Calendar points open the correct date's actual records");ui("closeSheet();window.scrollTo(0,0)");
    }
    private void gallery()throws Exception{
        android.net.Uri[] selected={null};java.util.List<String> requests=new java.util.concurrent.CopyOnWriteArrayList<>();
        android.graphics.Bitmap bitmap=android.graphics.Bitmap.createBitmap(1280,800,Bitmap.Config.ARGB_8888);android.graphics.Canvas canvas=new android.graphics.Canvas(bitmap);android.graphics.Paint paint=new android.graphics.Paint(3);
        paint.setShader(new android.graphics.LinearGradient(0,0,1280,800,new int[]{0xffb6d4eb,0xffddbfa9,0xff6b8ea0},null,android.graphics.Shader.TileMode.CLAMP));canvas.drawRect(0,0,1280,800,paint);paint.setShader(null);paint.setColor(0xfff5ddb5);canvas.drawCircle(940,180,95,paint);
        paint.setColor(0xff587e8b);android.graphics.Path ridge=new android.graphics.Path();ridge.moveTo(0,800);ridge.lineTo(0,610);ridge.lineTo(360,380);ridge.lineTo(700,650);ridge.lineTo(1040,430);ridge.lineTo(1280,650);ridge.lineTo(1280,800);ridge.close();canvas.drawPath(ridge,paint);
        android.content.ContentValues values=new android.content.ContentValues();values.put(android.provider.MediaStore.Images.Media.DISPLAY_NAME,"Lumi-photo-glass-QA.jpg");values.put(android.provider.MediaStore.Images.Media.MIME_TYPE,"image/jpeg");values.put(android.provider.MediaStore.Images.Media.RELATIVE_PATH,"Pictures/LumiFocusQA");
        android.net.Uri uri=context.getContentResolver().insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI,values);if(uri==null)throw new AssertionError("Gallery fixture creation failed");
        try(OutputStream out=context.getContentResolver().openOutputStream(uri)){bitmap.compress(Bitmap.CompressFormat.JPEG,92,out);}bitmap.recycle();selected[0]=uri;
        Instrumentation.ActivityMonitor monitor=new Instrumentation.ActivityMonitor(){@Override public Instrumentation.ActivityResult onStartActivity(Intent intent){if(android.provider.MediaStore.ACTION_PICK_IMAGES.equals(intent.getAction())||Intent.ACTION_OPEN_DOCUMENT.equals(intent.getAction())){requests.add(intent.getAction());return new Instrumentation.ActivityResult(selected[0]==null?Activity.RESULT_CANCELED:Activity.RESULT_OK,selected[0]==null?null:new Intent().setData(selected[0]).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION));}return null;}};
        String before=WallpaperStore.state(context).optString("imageVersion");test.addMonitor(monitor);
        try{
            ui("navigate('stats');$('statsWallpaperButton').click()");await("return data.appearance.preset==='photo'&&data.appearance.imageVersion!=="+JSONObject.quote(before));
            check("return data.appearance.liquid&&data.appearance.material==='clear'&&data.appearance.dim===20&&data.appearance.legibility","Selecting a photo through the native picker automatically enables photo glass");
            if(requests.isEmpty())throw new AssertionError("Native image picker was not invoked");
            check("return document.body.classList.contains('photo-background')&&document.body.dataset.legibility==='true'","Native photo import updates statistics without leaving the page");
            String imported=WallpaperStore.state(context).optString("imageVersion");selected[0]=null;ui("$('statsWallpaperButton').click()");Thread.sleep(300);
            check("return data.appearance.imageVersion==="+JSONObject.quote(imported),"Cancelling the native picker preserves the selected photo and glass settings");
            context.getContentResolver().delete(uri,null,null);uri=null;
            try(InputStream image=WallpaperStore.openImage(context)){if(image.read()<0)throw new AssertionError("Private image missing after source removal");}
            check("return data.appearance.hasImage","Photo background remains available after removing the original picker item");
        }finally{test.removeMonitor(monitor);if(uri!=null)context.getContentResolver().delete(uri,null,null);}
    }
    private void photoGlass()throws Exception{
        ui("closeSheet();applyPhotoGlass();navigate('focus')");
        check("return data.appearance.preset==='photo'&&data.appearance.liquid&&document.body.dataset.legibility==='true'","One-click photo glass activates the saved private wallpaper and text protection");
        check("const css=getComputedStyle(document.querySelector('.timer-panel'));return css.backdropFilter.includes('blur')&&css.backgroundColor!=='rgb(255, 255, 255)'","Photo panels have actual transparent backgrounds and backdrop blur");
        check("return getComputedStyle(document.querySelector('.timer-orb')).backdropFilter==='none'","Photo timer avoids an extra nested backdrop blur");
        check("return document.documentElement.scrollWidth<=innerWidth+1","Photo focus layout fits the current window");shot("photo-focus");
        if(profile.equals("phone"))check("return $('startButton').getBoundingClientRect().bottom<document.querySelector('.bottom-nav').getBoundingClientRect().top","Photo glass keeps the start control above navigation on a compact phone");
        ui("navigate('stats');$('referenceCalendars').scrollIntoView({block:'start'})");check("return document.documentElement.scrollWidth<=innerWidth+1&&document.querySelectorAll('.reference-calendar').length===2","Photo glass also covers both calendar statistics cards without overflow");shot("photo-stats");
        ui("appearance()");Thread.sleep(350);check("return !!$('photoGlassLook')&&$('photoLegibility').checked&&getComputedStyle(document.querySelector('.sheet')).backdropFilter.includes('blur')","Appearance dialog exposes photo look and protection with matching glass optics");shot("photo-settings");
        ui("$('photoLegibility').click()");check("return data.appearance.legibility===false&&document.body.dataset.legibility==='false'","Text protection can be explicitly disabled and persisted");
        BackupStore.Prepared backup=BackupStore.prepare(new ByteArrayInputStream(BackupStore.export(context,new JSONObject())));if(backup.appearance.getBoolean("legibility"))throw new AssertionError("Backup lost the disabled text-protection preference");
        ui("updateAppearance({material:'eco'});appearance()");check("return getComputedStyle(document.querySelector('.timer-panel')).backdropFilter==='none'&&getComputedStyle(document.querySelector('.sheet')).backdropFilter==='none'","Lightweight photo mode removes panel and dialog blur");
        ui("$('photoGlassLook').click();closeSheet();navigate('focus')");check("return data.appearance.legibility&&data.appearance.material==='clear'&&data.appearance.dim===20","One-click glass restores the readable photo recipe");
        ui("updateAppearance({tone:'dark'});navigate('stats')");check("const channels=getComputedStyle(document.querySelector('.reference-calendar')).color.match(/[0-9]+/g);return document.body.classList.contains('theme-dark')&&channels.length===3&&channels.every(value=>Number(value)>=240)","Dark photo glass keeps bright readable statistics text");shot("photo-dark");
        ui("updateAppearance({preset:'white',tone:'light'});navigate('focus')");check("return !document.body.classList.contains('photo-background')&&getComputedStyle(document.body).backgroundColor==='rgb(255, 255, 255)'","Switching back to white preserves the photo while restoring the white UI");
    }
    private void statusBar(boolean visible,String label)throws Exception{
        boolean[] actual={false};for(int i=0;i<35;i++){test.runOnMainSync(()->{WindowInsets insets=activity.getWindow().getDecorView().getRootWindowInsets();actual[0]=insets!=null&&insets.isVisible(WindowInsets.Type.statusBars());});if(actual[0]==visible){report.append("PASS ").append(label).append('\n');return;}Thread.sleep(80);}
        for(String name:new String[]{"focusScene","sceneFullscreen","largeClock"}){java.lang.reflect.Field field=MainActivity.class.getDeclaredField(name);field.setAccessible(true);report.append("WINDOW ").append(name).append('=').append(field.getBoolean(activity)).append('\n');}
        report.append("WINDOW_JS ").append(js("return {scene:focusSceneOpen,full:sceneFullscreen,clock:largeClockOpen,hidden:document.hidden,w:innerWidth,h:innerHeight}")).append('\n');shot("bar-failure");throw new AssertionError(label);
    }
    private void sceneChecks()throws Exception{
        ui("navigate('focus');closeSheet();command('reset');configure('focus',120,'二级专注验证')");
        long revision=new JSONObject(store.snapshot()).getLong("_revision");
        ui("document.querySelector('.timer-orb').click()");await("return focusSceneOpen&&!$('focusScene').hidden");Thread.sleep(350);
        check("return document.body.classList.contains('focus-scene-open')&&document.querySelector('.timer-orb').getAttribute('aria-expanded')==='true'","Tapping the main clock opens a distinct accessible secondary focus page");
        if(new JSONObject(store.snapshot()).getLong("_revision")!=revision)throw new AssertionError("Opening focus details mutated the timer");
        check("return data.timer.status==='idle'&&$('sceneTime').textContent===$('timerText').textContent","Opening focus details reuses the current timer without starting it");
        statusBar(false,"Secondary focus requests real Android fullscreen system-bar hiding");
        check("const nodes=['sceneExit','sceneDigits','sceneTask','sceneToggle','sceneFinish'];return nodes.every(id=>{const r=$(id).getBoundingClientRect();return r.left>=0&&r.right<=innerWidth+1&&r.top>=0&&r.bottom<=innerHeight+1})","Secondary focus clock and primary controls fit the current window");
        check("return data.appearance.hasImage&&$('focusScene').dataset.background==='solid'&&getComputedStyle($('focusScene')).backgroundColor==='rgb(255, 255, 255)'&&!$('focusScene').style.getPropertyValue('--scene-image').includes('lumi.local')&&getComputedStyle($('sceneToggle')).color==='rgb(48, 48, 48)'","White focus details ignore stored photos and use readable dark controls");shot("focus-detail");
        ui("updateAppearance({preset:'black',tone:'dark'})");
        check("return $('focusScene').dataset.tone==='dark'&&getComputedStyle($('focusScene')).backgroundColor==='rgb(0, 0, 0)'&&getComputedStyle($('sceneToggle')).color==='rgb(232, 232, 232)'&&getComputedStyle(document.querySelector('.scene-dial')).backdropFilter==='none'","Secondary focus follows pure black with soft white digits and no background blur");shot("focus-detail-black");
        ui("updateAppearance({preset:'photo',tone:'light'})");
        check("return $('focusScene').dataset.background==='photo'&&$('focusScene').style.getPropertyValue('--scene-image').includes('lumi.local')&&getComputedStyle(document.querySelector('.scene-wallpaper')).display!=='none'","Explicit photo selection enables the saved wallpaper in secondary focus");shot("focus-detail-photo");
        ui("updateAppearance({preset:'white',tone:'light'})");
        ui("$('sceneQuote').click();$('sceneQuiet').click()");check("return sceneQuiet&&!$('sceneReveal').hidden&&document.querySelector('.scene-dock').hidden&&!$('sceneExit').hidden","Focus controls can collapse while an exit and reveal control remain available");ui("$('sceneReveal').click()");
        check("return !sceneQuiet&&!document.querySelector('.scene-dock').hidden","The compact focus view restores its controls in one tap");
        ui("$('sceneFullscreen').click()");statusBar(true,"Secondary focus can restore the real Android system bars");ui("$('sceneFullscreen').click()");statusBar(false,"Fullscreen can be enabled again without restarting the timer");
        ui("$('sceneAppearance').click();updateAppearance({material:'eco'})");check("return !$('sheetOverlay').hidden&&$('focusScene').getAttribute('aria-hidden')==='true'&&getComputedStyle(document.querySelector('.scene-dock')).backdropFilter==='none'","Appearance opens above focus details and its lightweight setting applies to the scene");statusBar(false,"Changing appearance preserves the secondary page's fullscreen state");
        check("document.dispatchEvent(new KeyboardEvent('keydown',{key:'Escape',bubbles:true}));return focusSceneOpen&&$('sheetOverlay').hidden&&!$('focusScene').hasAttribute('inert')","Escape dismisses the appearance sheet before leaving focus details");ui("updateAppearance({material:'clear'})");
        ui("$('sceneToggle').click()");check("return data.timer.status==='running'&&$('sceneToggle').getAttribute('aria-label')==='暂停'","Secondary play starts the same native countdown exactly once");Thread.sleep(1100);
        check("return $('sceneTime').textContent===$('timerText').textContent&&data.timer.elapsedNow>=1000","Secondary display and main clock stay synchronized with actual elapsed time");
        ui("$('sceneSound').click()");check("return !$('sheetOverlay').hidden&&!!$('soundMaster')&&focusSceneOpen","Environmental sound controls open as a child sheet of focus details");ui("handleBack();$('sceneToggle').click()");
        long paused=store.elapsed();Thread.sleep(300);if(Math.abs(store.elapsed()-paused)>20)throw new AssertionError("Paused scene timer continued advancing");check("return data.timer.status==='paused'&&$('sceneStatus').textContent.includes('已暂停')","Secondary pause excludes paused wall-clock time");
        ui("$('sceneReset').click();$('cancelEnd').click()");check("return focusSceneOpen&&data.timer.status==='paused'","Cancelling a secondary reset preserves the paused timer");
        int count=new JSONObject(store.snapshot()).getJSONArray("sessions").length();ui("$('sceneFinish').click();$('confirmEnd').click()");
        check("return focusSceneOpen&&data.sessions.length==="+(count+1)+"&&data.timer.status==='completed'","Secondary finish saves the actual session once and keeps the details page open");
        ui("closeSheet();configure('focus',1);$('sceneToggle').click()");await("return data.timer.status==='completed'&&!$('sheetOverlay').hidden");
        check("return focusSceneOpen&&$('focusScene').getAttribute('aria-hidden')==='true'&&!!$('completionNext')","Natural completion presents its result above the secondary page");
        check("return handleBack()&&focusSceneOpen&&$('sheetOverlay').hidden","Closing completion returns to focus details rather than the main page");
        context.getSystemService(NotificationManager.class).cancel(2);Thread.sleep(400);
        ui("configure('up',0);$('sceneToggle').click()");
        if(profile.equals("landscape")){
            long before=store.elapsed();ui("window.sceneResumeProbe=true");test.runOnMainSync(()->activity.moveTaskToBack(true));Thread.sleep(500);
            shell("am start -n com.lumifocus.app.debug/com.lumifocus.app.MainActivity -f 0x20020000");await("return !document.hidden&&focusSceneOpen&&window.sceneResumeProbe&&data.timer.status==='running'");
            if(store.elapsed()<=before+300)throw new AssertionError("Background time lost in secondary focus");check("return $('sceneTime').textContent===$('timerText').textContent","Background and resume preserve the secondary route and native stopwatch");
        }
        ui("$('sceneClock').click()");await("return largeClockOpen&&innerWidth>innerHeight");
        check("return focusSceneOpen&&$('largeClock').dataset.style==='digital'&&$('clockTime').textContent===$('sceneTime').textContent","Secondary focus can open the black flip clock on the same running timer");
        check("return handleBack()&&!largeClockOpen&&focusSceneOpen&&data.timer.status==='running'","First back from the flip clock returns to secondary focus and keeps counting");
        check("return handleBack()&&!focusSceneOpen&&$('focusScene').hidden&&data.timer.status==='running'&&!document.body.classList.contains('immersive')","Second back returns to the main timer without pausing or duplicating it");statusBar(true,"Leaving secondary focus restores the normal Android system bars");
        ui("$('immerseButton').click()");check("return focusSceneOpen&&sceneFullscreen&&$('sceneFullscreen').getAttribute('aria-pressed')==='true'","The main details button reopens focus with a consistent fullscreen control");ui("$('sceneExit').click();command('reset');configure('focus',60)");
    }
    private void solidThemes()throws Exception{
        ui("navigate('focus');appearance();document.querySelector('[data-preset=black]').click()");
        check("return data.appearance.preset==='black'&&data.appearance.tone==='dark'&&getComputedStyle(document.body).backgroundColor==='rgb(0, 0, 0)'&&data.appearance.hasImage","The dark mode button selects pure black without deleting the uploaded photo");
        check("return getComputedStyle(document.querySelector('.sheet')).backgroundColor==='rgb(17, 17, 17)'&&getComputedStyle(document.querySelector('.sheet')).backdropFilter==='none'&&getComputedStyle(document.querySelector('.wallpaper-layer')).display==='none'","Black dialogs and cards use neutral opaque surfaces without wallpaper blur");shot("black-appearance");
        ui("closeSheet()");
        final int[] colors=new int[2];test.runOnMainSync(()->{colors[0]=activity.getWindow().getStatusBarColor();colors[1]=activity.getWindow().getNavigationBarColor();});
        if(colors[0]!=android.graphics.Color.BLACK||colors[1]!=android.graphics.Color.BLACK)throw new AssertionError("Black mode system bars are not pure black");
        check("return getComputedStyle($('startButton')).backgroundColor==='rgb(27, 27, 27)'&&getComputedStyle($('startButton')).color==='rgb(232, 232, 232)'&&getComputedStyle(document.querySelector('.timer-panel')).backgroundImage==='none'&&getComputedStyle(document.querySelector('.timer-panel')).backgroundColor==='rgb(17, 17, 17)'&&document.documentElement.scrollWidth<=innerWidth+1","Black main controls retain neutral contrast and fit the current window");shot("focus-black");
        for(String target:new String[]{"workspace","events","stats"}){
            ui("navigate('"+target+"')");check("return document.documentElement.scrollWidth<=innerWidth+1&&getComputedStyle(document.body).backgroundColor==='rgb(0, 0, 0)'","Black "+target+" page fits the current window");
        }
        check("return getComputedStyle($('reference-month-chart')).color==='rgb(204, 204, 204)'","Black statistics keep calendar chart strokes neutral and readable");shot("stats-black");
        byte[] saved=BackupStore.export(context,new JSONObject());
        BackupStore.Prepared prepared=BackupStore.prepare(new ByteArrayInputStream(saved));
        if(!prepared.appearance.getString("preset").equals("black")||prepared.image==null)throw new AssertionError("Black backup did not retain selected mode and stored photo");
        WallpaperStore.update(context,"{\"preset\":\"black\",\"tone\":\"light\"}");
        if(!WallpaperStore.state(context).getString("tone").equals("dark"))throw new AssertionError("Solid palette accepted conflicting text tone");
        JSONObject removed=WallpaperStore.removeImage(context);
        if(!removed.getString("preset").equals("black")||removed.getBoolean("hasImage"))throw new AssertionError("Removing a photo did not preserve black mode");
        BackupStore.restore(context,prepared);ui("refresh()");
        check("return data.appearance.preset==='black'&&data.appearance.tone==='dark'&&data.appearance.hasImage","Backup round-trip preserves black mode and the optional photo after removal");
        ui("appearance();document.querySelector('button[data-tone=light]').click()");
        check("return data.appearance.preset==='white'&&getComputedStyle(document.body).backgroundColor==='rgb(255, 255, 255)'&&data.appearance.hasImage","Light text-style control switches solid mode to white and retains the optional photo");
        check("const r=document.querySelector('.solid-mode-options').getBoundingClientRect();return r.left>=0&&r.right<=innerWidth+1&&[...document.querySelectorAll('.solid-mode-options button')].every(b=>b.getBoundingClientRect().height>=44)","Both solid-mode choices fit the appearance panel with usable touch targets");shot("white-appearance");
        ui("closeSheet();navigate('focus')");
    }
}
