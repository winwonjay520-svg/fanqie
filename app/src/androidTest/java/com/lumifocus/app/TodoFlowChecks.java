package com.lumifocus.app;

import android.app.Instrumentation;
import android.content.Context;
import android.graphics.Bitmap;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.webkit.WebView;
import org.json.*;
import java.io.*;
import java.util.concurrent.*;

/** Exercises the shipped todo routes, separate from legacy timer controls. */
final class TodoFlowChecks {
    private final Instrumentation test;private final Context context;private final WebView web;private final Store store;
    private final StringBuilder report=new StringBuilder();private String profile;
    TodoFlowChecks(Instrumentation t,WebView w){test=t;web=w;context=t.getTargetContext();store=Store.get(context);}
    private String js(String code)throws Exception{CountDownLatch ready=new CountDownLatch(1);String[] value=new String[1];test.runOnMainSync(()->web.evaluateJavascript("(()=>{"+code+"})()",s->{value[0]=s;ready.countDown();}));if(!ready.await(10,TimeUnit.SECONDS))throw new AssertionError("WebView timeout");return value[0];}
    private void ui(String code)throws Exception{js(code+";return true");}
    private void check(String code,String label)throws Exception{if(!"true".equals(js(code))){report.append("TODO_DIAGNOSTIC ").append(js("return {draft:todoDraft,preview:$('todoPreviewTime')?.textContent,width:innerWidth,scroll:document.documentElement.scrollWidth,overflow:[...document.querySelectorAll('#todoSetup *')].filter(e=>e.getBoundingClientRect().right>innerWidth+1).map(e=>[e.tagName,e.className,e.getBoundingClientRect().right])}")).append('\n');throw new AssertionError(label);}report.append("PASS ").append(label).append('\n');}
    private void shot(String name)throws Exception{ui("glassMotion.clear();$('toast').classList.remove('show')");Thread.sleep(500);Bitmap b=test.getUiAutomation().takeScreenshot();try(FileOutputStream out=new FileOutputStream(new File(context.getFilesDir(),"todo-"+profile+"-"+name+".png"))){b.compress(Bitmap.CompressFormat.PNG,100,out);}b.recycle();}
    private void tap(String id)throws Exception{
        JSONArray p=new JSONArray(js("const r=$("+JSONObject.quote(id)+").getBoundingClientRect();return [r.left+r.width/2,r.top+r.height/2]"));int[] offset=new int[2];float[] scale=new float[1];test.runOnMainSync(()->{web.getLocationOnScreen(offset);scale[0]=web.getScale();});
        float x=offset[0]+(float)p.getDouble(0)*scale[0],y=offset[1]+(float)p.getDouble(1)*scale[0];long now=SystemClock.uptimeMillis();MotionEvent down=MotionEvent.obtain(now,now,MotionEvent.ACTION_DOWN,x,y,0),up=MotionEvent.obtain(now,now+60,MotionEvent.ACTION_UP,x,y,0);try{test.sendPointerSync(down);test.sendPointerSync(up);}finally{down.recycle();up.recycle();}
    }
    String run(String name)throws Exception{
        profile=name;if(!context.getPackageName().endsWith(".debug"))throw new AssertionError("Debug package required");byte[] original=BackupStore.export(context,new JSONObject());
        try{
            ui("setFocusScene(false);closeSheet();command('reset');updateAppearance({preset:'white',tone:'light'});setTodoView('list');navigate('focus');command('settings',{value:{focus:25,short:5,long:15,goal:120}});command('taskSave',{value:{id:'todo-layout-qa',title:'读一章喜欢的书',tag:'阅读',priority:2,estimate:1,timerMode:'focus',duration:1500}})");Thread.sleep(400);
            report.append("TODO_VIEWPORT ").append(profile).append(' ').append(js("return {width:innerWidth,height:innerHeight,dpr:devicePixelRatio}")).append('\n');
            check("return todoView==='list'&&!$('addTodoButton').hidden&&document.documentElement.scrollWidth<=innerWidth+1","Todo list and plus fit the window");shot("list");tap("addTodoButton");Thread.sleep(300);
            check("return todoView==='setup'&&$('todoPreviewTime').textContent==='25:00'&&data.timer.status==='idle'","Tapping plus opens the circular draft without starting a clock");
            check("const r=document.querySelector('.todo-preview-dial').getBoundingClientRect();return r.width>120&&Math.abs(r.width-r.height)<2&&r.left>=0&&r.right<=innerWidth+1&&getComputedStyle(document.body).backgroundColor==='rgb(255, 255, 255)'","White circular selection matches the window without horizontal overflow");shot("white-selection");
            ui("$('todoSetupName').value='读一章喜欢的书';$('todoSetupName').dispatchEvent(new Event('input',{bubbles:true}));document.querySelector('[data-todo-mode=up]').click()");
            check("return todoDraft.title==='读一章喜欢的书'&&$('todoPreviewTime').textContent==='00:00'&&!$('todoCustom')&&data.timer.status==='idle'","Stopwatch selection retains the draft name without mutating native time");
            ui("document.querySelector('[data-todo-mode=down]').click();document.querySelector('[data-todo-minutes=\"45\"]').click();$('todoCustom').click();$('todoMinutes').value=12;$('todoSeconds').value=34;$('todoSaveDuration').click()");
            check("return todoDraft.duration===754&&$('todoPreviewTime').textContent==='12:34'&&document.documentElement.scrollWidth<=innerWidth+1","Custom minutes and seconds return to the same selection page");
            ui("updateAppearance({preset:'black',tone:'dark'})");check("return getComputedStyle(document.body).backgroundColor==='rgb(0, 0, 0)'&&getComputedStyle($('todoPreviewTime')).color!=='rgb(0, 0, 0)'","Black mode applies to the new selection page");shot("black-selection");
            if(new JSONObject(store.snapshot()).getJSONObject("appearance").optBoolean("hasImage")){
                ui("applyPhotoGlass()");check("return document.body.classList.contains('photo-background')&&getComputedStyle(document.querySelector('.todo-setup-panel')).backdropFilter.includes('blur')","Saved photo and liquid glass apply to the new selection page");shot("photo-selection");
            }
            ui("updateAppearance({preset:'white',tone:'light'});$('todoStartSelected').scrollIntoView({block:'center'})");Thread.sleep(400);
            check("const b=$('todoStartSelected'),r=b.getBoundingClientRect();return r.top>=0&&r.bottom<=innerHeight&&b.contains(document.elementFromPoint(r.left+r.width/2,r.top+r.height/2))","Start can be scrolled into view and is not covered by navigation");shot("start-visible");tap("todoStartSelected");Thread.sleep(500);
            check("return focusSceneOpen&&data.timer.status==='running'&&data.timer.mode==='down'&&data.timer.duration===754&&data.timer.label==='读一章喜欢的书'","A real start tap saves the chosen todo and enters the full-screen timer");
            check("const r=$('sceneToggle').getBoundingClientRect();return r.width>30&&r.top>=0&&r.bottom<=innerHeight&&document.documentElement.scrollWidth<=innerWidth+1","Full-screen pause control fits the window");shot("running");
            ui("handleBack();$('addTodoButton').click()");long before=store.elapsed();String base=new JSONObject(store.snapshot()).getJSONObject("timer").getString("base");Thread.sleep(600);ui("handleBack()");
            check("return todoView==='list'&&!focusSceneOpen&&data.timer.status==='running'","Cancelling another draft returns to the list while the original clock runs");
            if(store.elapsed()<before+500||!new JSONObject(store.snapshot()).getJSONObject("timer").getString("base").equals(base))throw new AssertionError("Draft cancelled the running clock");report.append("PASS Draft cancellation preserves the original deadline\n");
            ui("command('reset');navigate('workspace');selectFocusTask('todo-layout-qa')");check("return todoView==='setup'&&page==='focus'&&todoDraft.id==='todo-layout-qa'&&data.timer.status==='idle'","Plan-library selection also enters the circular selection stage");
            report.append("TODO_DONE ").append(profile).append('\n');
        }catch(Throwable e){report.append("FAIL ").append(android.util.Log.getStackTraceString(e));}
        finally{ui("setFocusScene(false);closeSheet();command('reset')");BackupStore.restore(context,BackupStore.prepare(new ByteArrayInputStream(original)));ui("refresh();setTodoView('classic');navigate('focus')");}
        return report.toString();
    }
}
