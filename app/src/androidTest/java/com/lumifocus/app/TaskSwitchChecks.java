package com.lumifocus.app;

import android.app.*;
import android.content.Context;
import android.webkit.WebView;
import org.json.*;
import java.io.*;
import java.util.concurrent.*;

/** Single-clock task switching and selection flow, isolated to the Debug installation. */
final class TaskSwitchChecks {
    private final Instrumentation test;private final Context context;private final WebView web;private final Store store;private final StringBuilder report=new StringBuilder();
    TaskSwitchChecks(Instrumentation t,WebView w){test=t;web=w;context=t.getTargetContext();store=Store.get(context);}
    private String js(String code)throws Exception{CountDownLatch latch=new CountDownLatch(1);String[] result=new String[1];test.runOnMainSync(()->web.evaluateJavascript("(()=>{"+code+"})()",r->{result[0]=r;latch.countDown();}));if(!latch.await(10,TimeUnit.SECONDS))throw new AssertionError("WebView timeout");return result[0];}
    private JSONObject state()throws Exception{return new JSONObject(store.snapshot());}
    private JSONObject cmd(String action,JSONObject values)throws Exception{values.put("action",action);JSONObject result=new JSONObject(store.command(values.toString()));if(result.has("error"))throw new AssertionError(result.getString("error"));return result;}
    private void check(boolean ok,String label){if(!ok)throw new AssertionError(label);report.append("PASS ").append(label).append('\n');}
    private void ui(String code)throws Exception{js(code+";return true");}
    private void elapsedFixture(long ms)throws Exception{
        JSONObject d=state();d.getJSONObject("timer").put("elapsed",ms).put("elapsedNow",ms).put("status","paused");cmd("reset",new JSONObject());store.restore(WorkspaceFeatures.validateBackup(d));ui("refresh();closeSheet()");
    }
    private void timer(String mode,int seconds,String taskId,long elapsed)throws Exception{cmd("reset",new JSONObject());cmd("configure",new JSONObject().put("mode",mode).put("duration",seconds).put("taskId",taskId));elapsedFixture(elapsed);}
    private long totalSince(int index)throws Exception{JSONArray rows=state().getJSONArray("sessions");long n=0;for(int i=index;i<rows.length();i++)n+=rows.getJSONObject(i).getLong("seconds");return n;}
    private void shot(String name)throws Exception{ui("glassMotion.clear();$('toast').classList.remove('show')");Thread.sleep(550);android.graphics.Bitmap b=test.getUiAutomation().takeScreenshot();try(FileOutputStream out=new FileOutputStream(new File(context.getFilesDir(),name+".png"))){b.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);}b.recycle();}
    String run()throws Exception{
        if(!context.getPackageName().endsWith(".debug"))throw new AssertionError("Debug package required");byte[] original=BackupStore.export(context,new JSONObject());
        try{
            cmd("reset",new JSONObject());
            String[] ids={"switch-qa-a","switch-qa-b"};for(int i=0;i<2;i++)cmd("taskSave",new JSONObject().put("value",new JSONObject().put("id",ids[i]).put("title",i==0?"阅读":"复习").put("tag",i==0?"阅读标签":"复习标签").put("priority",2).put("estimate",1)));
            cmd("configure",new JSONObject().put("mode","focus").put("duration",30).put("taskId",ids[0]));cmd("start",new JSONObject());Thread.sleep(1150);js("refresh();return true");
            check("true".equals(js("return !$('taskButton').disabled")),"The current task can be changed while the native timer is running");
            long elapsed=store.elapsed();JSONObject before=state().getJSONObject("timer");int count=state().getJSONArray("sessions").length();
            cmd("switchTask",new JSONObject().put("taskId",ids[1]));JSONObject after=state().getJSONObject("timer");
            check(store.running()&&after.getLong("duration")==30&&after.getLong("base")==before.getLong("base")&&store.elapsed()>=elapsed,"Switching tasks preserves elapsed time and the original countdown deadline");
            check(after.getString("taskId").equals(ids[1])&&state().getJSONArray("sessions").length()==count+1,"A task switch saves the previous task segment and selects the new task");
            cmd("pause",new JSONObject());long paused=store.elapsed();cmd("switchTask",new JSONObject().put("taskId",ids[0]));Thread.sleep(200);
            check(!store.running()&&store.elapsed()==paused&&state().getJSONObject("timer").getString("status").equals("paused"),"Switching a paused task preserves its paused state");
            timer("focus",10,ids[0],1999);count=state().getJSONArray("sessions").length();cmd("switchTask",new JSONObject().put("taskId",ids[1]));elapsedFixture(2450);cmd("finish",new JSONObject());
            check(totalSince(count)==2&&state().getJSONArray("sessions").length()==count+2,"Fractional seconds are conserved across plan segments without rounding drift");
            JSONArray rows=state().getJSONArray("sessions");check(rows.getJSONObject(count).getString("taskId").equals(ids[0])&&rows.getJSONObject(count+1).getString("taskId").equals(ids[1])&&rows.getJSONObject(count+1).getString("tag").equals("复习标签"),"Each saved segment belongs to its actual task and recorded tag");
            timer("up",1,ids[0],300);count=state().getJSONArray("sessions").length();cmd("switchTask",new JSONObject().put("taskId",ids[1]));cmd("switchTask",new JSONObject().put("taskId",ids[0]));cmd("finish",new JSONObject());
            check(state().getJSONArray("sessions").length()==count,"Rapid sub-second switches never create zero-second or duplicate records");
            timer("focus",3,ids[0],1300);count=state().getJSONArray("sessions").length();cmd("switchTask",new JSONObject().put("taskId",ids[1]));cmd("start",new JSONObject());Thread.sleep(1850);store.checkFinish();
            rows=state().getJSONArray("sessions");check(totalSince(count)==3&&rows.length()==count+2&&rows.getJSONObject(count+1).getBoolean("completed"),"Natural completion saves only the remaining task time and awards one completed pomodoro");
            int doneCount=rows.length();store.checkFinish();check(state().getJSONArray("sessions").length()==doneCount,"Repeated completion checks cannot re-save switched segments");
            timer("up",1,ids[0],3030);count=state().getJSONArray("sessions").length();cmd("switchTask",new JSONObject().put("taskId",ids[1]));elapsedFixture(5100);cmd("finish",new JSONObject());check(totalSince(count)==5,"Stopwatch task switches conserve actual focus time");
            timer("down",10,ids[0],1300);count=state().getJSONArray("sessions").length();cmd("switchTask",new JSONObject().put("taskId",ids[1]));
            BackupStore.Prepared backup=BackupStore.prepare(new ByteArrayInputStream(BackupStore.export(context,new JSONObject())));check(backup.data.getJSONObject("timer").getLong("accountedSeconds")==1,"Active split-task backups retain the already recorded time boundary");
            cmd("reset",new JSONObject());BackupStore.restore(context,backup);elapsedFixture(2900);cmd("finish",new JSONObject());check(totalSince(count)==2,"Restoring and finishing a split timer does not count the original task twice");
            timer("focus",10,ids[0],1700);count=state().getJSONArray("sessions").length();JSONObject unchanged=state();JSONObject rejected=new JSONObject(store.command(new JSONObject().put("action","switchTask").put("taskId","missing-task").toString()));check(rejected.has("error")&&state().getJSONArray("sessions").length()==count&&state().getJSONObject("timer").toString().equals(unchanged.getJSONObject("timer").toString()),"Invalid target tasks leave the clock and its history untouched");
            cmd("switchTask",new JSONObject().put("taskId",ids[0]));check(state().getJSONArray("sessions").length()==count,"Selecting the current task does not split it again");
            cmd("startTask",new JSONObject().put("taskId",ids[1]).put("mode","down").put("duration",60));check(store.running()&&state().getJSONObject("timer").getLong("duration")==60&&store.elapsed()<1000&&totalSince(count)==1,"Explicitly starting with new settings saves the previous plan before starting the new clock");
            cmd("reset",new JSONObject());cmd("configure",new JSONObject().put("mode","focus").put("duration",10).put("taskId",ids[0]));cmd("workflowSave",new JSONObject().put("value",new JSONObject().put("focus",2).put("short",3).put("long",3).put("rounds",2).put("auto",true)));cmd("flowStart",new JSONObject());Thread.sleep(1100);cmd("switchTask",new JSONObject().put("taskId",ids[1]));
            check(state().getJSONObject("flowState").getInt("round")==1&&state().getJSONObject("flowState").getString("phase").equals("focus"),"Switching a workflow task preserves the current round and phase");Thread.sleep(1050);store.checkFinish();check(state().getJSONObject("timer").getString("mode").equals("break")&&state().getJSONObject("timer").getString("taskId").equals(ids[1]),"Workflow rest and following rounds carry the newly selected task");
            cmd("reset",new JSONObject());ui("refresh();closeSheet();setFocusScene(false);setTodoView('list')");shot("todo-list");long revision=state().getLong("_revision");
            ui("$('addTodoButton').click();$('todoSetupName').value='新建专注计划';$('todoSetupName').dispatchEvent(new Event('input',{bubbles:true}));document.querySelector('[data-todo-mode=down]').click();$('todoCustom').click();$('todoMinutes').value=0;$('todoSeconds').value=8;$('todoSaveDuration').click()");
            check("true".equals(js("return todoView==='setup'&&$('todoPreviewTime').textContent==='00:08'&&todoDraft.title==='新建专注计划'"))&&state().getLong("_revision")==revision,"The plus button opens a draft circular selection screen without changing the real timer");shot("todo-selection");
            ui("$('todoStartSelected').click()");check("true".equals(js("return focusSceneOpen&&todoView==='list'&&data.timer.status==='running'&&data.timer.mode==='down'&&data.timer.duration===8&&data.timer.label==='新建专注计划'")),"Confirming the selection saves the todo and opens the full-screen timer on the chosen settings");shot("todo-running");ui("$('sceneToggle').click();handleBack()");
            long uiPaused=store.elapsed();check("true".equals(js("return !focusSceneOpen&&todoView==='list'&&data.timer.status==='paused'")),"Back from full-screen timing returns to the todo list and preserves pause state");
            ui("$('addTodoButton').click();handleBack()");check(store.elapsed()==uiPaused&&state().getJSONObject("timer").getString("status").equals("paused"),"Cancelling a new selection leaves an existing paused clock untouched");
            ui("setFocusScene(true);$('sceneTask').click()");check("true".equals(js("return !$('sheetOverlay').hidden&&!!document.querySelector('[data-pick-task]')")),"The active full-screen task name opens the task switcher");
            ui("document.querySelector('[data-pick-task=\""+ids[0]+"\"]').click()");check("true".equals(js("return focusSceneOpen&&data.timer.taskId==='"+ids[0]+"'&&data.timer.status==='paused'")),"Selecting another plan in focus details keeps the same paused timer and route");
            ui("closeSheet();setFocusScene(false)");
            report.append("TASK_SWITCH_DONE\n");
        }catch(Throwable e){report.append("FAIL ").append(android.util.Log.getStackTraceString(e));}
        finally{cmd("reset",new JSONObject());BackupStore.restore(context,BackupStore.prepare(new ByteArrayInputStream(original)));js("refresh();closeSheet();setFocusScene(false);setTodoView('classic');navigate('focus');return true");}
        return report.toString();
    }
}
