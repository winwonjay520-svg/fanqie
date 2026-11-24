package com.lumifocus.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.SystemClock;
import android.provider.Settings;
import org.json.*;

/** Single synchronized source of truth shared by the UI, service and alarm receiver. */
public final class Store {
    private static Store instance;
    private final Context context;
    private final SharedPreferences prefs;
    private JSONObject data;
    public static synchronized Store get(Context c) {
        if (instance == null) instance = new Store(c.getApplicationContext());
        return instance;
    }
    private Store(Context c) {
        context = c; prefs = c.getSharedPreferences("lumi_v1", Context.MODE_PRIVATE);
        try { data = new JSONObject(prefs.getString("data", "{}")); } catch (Exception e) { data = new JSONObject(); }
        try {
            if (!data.has("timer")) data.put("timer", fresh("focus", 1500));
            if (!data.has("sessions")) data.put("sessions", new JSONArray());
            if (!data.has("events")) data.put("events", new JSONArray());
            if (!data.has("settings")) data.put("settings", new JSONObject("{\"focus\":25,\"short\":5,\"long\":15,\"goal\":120,\"sound\":true,\"keepAwake\":false}"));
            if (!data.has("_revision")) data.put("_revision",0);
            if (!data.has("_historyRevision")) data.put("_historyRevision",data.getJSONArray("sessions").length());
            if (!data.has("_eventsRevision")) data.put("_eventsRevision",0);
            WorkspaceFeatures.defaults(data);
        } catch (JSONException e) { throw new IllegalStateException(e); }
    }
    private int boot() { return Settings.Global.getInt(context.getContentResolver(), Settings.Global.BOOT_COUNT, -1); }
    private JSONObject fresh(String mode, long duration) throws JSONException {
        return new JSONObject().put("mode", mode).put("duration", duration).put("elapsed", 0).put("status", "idle").put("label", "自由专注");
    }
    private void save() {
        try {data.put("_revision",data.optLong("_revision")+1);}catch(JSONException e){throw new IllegalStateException(e);}
        if (!prefs.edit().putString("data", data.toString()).commit()) throw new IllegalStateException("本地存储空间不足，请释放空间后重试");
    }
    public synchronized boolean keepAwake() { return data.optJSONObject("settings").optBoolean("keepAwake"); }
    public synchronized boolean navigationHapticsEnabled() {
        return data.optJSONObject("settings").optBoolean("navigationHaptics", true);
    }
    public synchronized boolean audioWanted() {
        JSONObject m=data.optJSONObject("mixer");return m.optBoolean("playing")&&(!m.optBoolean("followTimer",true)||(running()&&!data.optJSONObject("timer").optString("mode").equals("break")))&&(m.optInt("rain")+m.optInt("forest")+m.optInt("cafe")>0);
    }
    public synchronized int[] audioMix() {JSONObject m=data.optJSONObject("mixer");return new int[]{m.optInt("rain"),m.optInt("forest"),m.optInt("cafe")};}
    public synchronized void restore(JSONObject validated) throws JSONException {
        if(running()||data.getJSONObject("timer").optString("status").equals("paused"))throw new IllegalStateException("请先结束当前计时，再恢复备份");
        JSONObject next=new JSONObject(validated.toString());
        // Revisions belong to this installation, not the imported file.
        for(String key:new String[]{"_revision","_historyRevision","_eventsRevision","_workspaceRevision"})next.put(key,data.optLong(key)+1);
        if(!prefs.edit().putString("data",next.toString()).commit())throw new IllegalStateException("恢复失败，本地空间不足");data=next;
    }
    public synchronized String poll(long knownRevision) {
        if(knownRevision!=data.optLong("_revision"))return snapshot();
        try {return new JSONObject().put("_revision",knownRevision).put("timer",new JSONObject(data.getJSONObject("timer").toString()).put("elapsedNow",elapsed())).put("audioActive",AmbientService.active()).put("appearance",WallpaperStore.state(context)).toString();}
        catch(JSONException e){throw new IllegalStateException(e);}
    }
    public synchronized long elapsed() {
        JSONObject t = data.optJSONObject("timer");
        long ms = t.optLong("elapsed");
        if (t.optString("status").equals("running")) {
            long delta = boot() == t.optInt("boot", -2) ? SystemClock.elapsedRealtime() - t.optLong("base") : System.currentTimeMillis() - t.optLong("wall");
            ms += Math.max(0, delta);
        }
        return ms;
    }
    public synchronized boolean running() { return data.optJSONObject("timer").optString("status").equals("running"); }
    public synchronized boolean countdown() { return !data.optJSONObject("timer").optString("mode").equals("up"); }
    public synchronized long remaining() { return Math.max(0, data.optJSONObject("timer").optLong("duration") * 1000 - elapsed()); }
    public synchronized boolean checkFinish() {
        if (running() && countdown() && remaining() == 0) {
            try { finish(true); return true; } catch (JSONException e) { throw new IllegalStateException(e); }
        }
        return false;
    }
    private void finish(boolean complete) throws JSONException {
        JSONObject t = data.getJSONObject("timer");
        long ms = complete ? t.getLong("duration") * 1000 : elapsed();
        String sessionId="";
        long seconds=ms/1000-t.optLong("accountedSeconds");
        if (seconds > 0 && !t.optString("mode").equals("break")) {
            long end = complete && running() ? System.currentTimeMillis() - Math.max(0, elapsed() - ms) : System.currentTimeMillis();
            sessionId=java.util.UUID.randomUUID().toString();
            data.getJSONArray("sessions").put(new JSONObject().put("id",sessionId).put("start", t.optLong("segmentStarted",t.optLong("started", end - ms))).put("end", end).put("seconds", seconds).put("mode", t.getString("mode")).put("label", t.optString("label", "自由专注")).put("taskId",t.optString("taskId")).put("tag",t.optString("tag")).put("completed", complete));
            data.put("_historyRevision",data.optLong("_historyRevision")+1);
        }
        t.put("elapsed", ms).put("status", "completed").put("completed", complete).put("completionId", System.currentTimeMillis());
        data.put("lastCompletion",new JSONObject().put("id",t.getLong("completionId")).put("mode",t.getString("mode")).put("sessionId",sessionId).put("completed",complete));
        if(complete)advanceFlow();else data.remove("flowState");
        save();
    }
    /** Commit the old segment and new assignment together; the clock itself is unchanged. */
    private void changeTask(JSONObject cmd,boolean restart) throws JSONException {
        JSONObject current=data.getJSONObject("timer");
        boolean active=running()||current.optString("status").equals("paused");
        if(!restart&&(!active||current.optString("mode").equals("break")))throw new IllegalArgumentException("请在专注计时中切换待办");
        String id=cmd.optString("taskId"),label,tag;
        JSONObject task=WorkspaceFeatures.find(data.getJSONArray("tasks"),id);
        if(!id.isEmpty()&&(task==null||task.optBoolean("done")))throw new IllegalArgumentException("请选择尚未完成的待办");
        label=task==null?WorkspaceFeatures.text(cmd,"label",80,false):task.getString("title");if(label.isEmpty())label="自由专注";
        tag=task==null?"":task.optString("tag");
        String mode=cmd.optString("mode",current.optString("mode"));long duration=current.optLong("duration");
        if(restart){if(!java.util.Arrays.asList("focus","down","up").contains(mode))throw new IllegalArgumentException("无效计时模式");duration=WorkspaceFeatures.number(cmd,"duration",1,86400);}
        long ms=elapsed(),boundary=ms/1000;
        if(!restart&&countdown()&&ms>=current.optLong("duration")*1000)throw new IllegalArgumentException("本段时间已满，请先结束计时");
        if(!restart&&id.equals(current.optString("taskId"))&&label.equals(current.optString("label"))&&tag.equals(current.optString("tag")))return;
        JSONObject previous=data;data=new JSONObject(previous.toString());
        try{
            JSONObject timer=data.getJSONObject("timer");long seconds=boundary-timer.optLong("accountedSeconds");
            if(active&&seconds>0&&!timer.optString("mode").equals("break")){
                long now=System.currentTimeMillis();data.getJSONArray("sessions").put(new JSONObject().put("id",java.util.UUID.randomUUID().toString()).put("start",timer.optLong("segmentStarted",timer.optLong("started",now-ms))).put("end",now).put("seconds",seconds).put("mode",timer.getString("mode")).put("label",timer.optString("label","自由专注")).put("taskId",timer.optString("taskId")).put("tag",timer.optString("tag")).put("completed",false));
                data.put("_historyRevision",data.optLong("_historyRevision")+1);
            }
            if(restart){timer=fresh(mode,duration);data.put("timer",timer);data.remove("flowState");start(timer);}
            else timer.put("accountedSeconds",boundary).put("segmentStarted",System.currentTimeMillis());
            timer.put("taskId",id).put("label",label).put("tag",tag);save();
        }catch(Exception e){data=previous;throw e;}
    }
    private JSONObject linkedFresh(String mode,long duration,JSONObject previous) throws JSONException {
        boolean linked=WorkspaceFeatures.find(data.getJSONArray("tasks"),previous.optString("taskId"))!=null;
        return fresh(mode,duration).put("label",previous.optString("label","自由专注")).put("taskId",linked?previous.optString("taskId"):"").put("tag",linked?previous.optString("tag"):"");
    }
    private void start(JSONObject timer) throws JSONException {
        if(timer.optString("status").equals("idle"))timer.put("started",System.currentTimeMillis());
        timer.put("status","running").put("base",SystemClock.elapsedRealtime()).put("wall",System.currentTimeMillis()).put("boot",boot());
        JSONObject flow=data.optJSONObject("flowState");if(flow!=null)flow.put("waiting",false);
    }
    private void advanceFlow() throws JSONException {
        JSONObject flow=data.optJSONObject("flowState");if(flow==null)return;
        String phase=flow.getString("phase");int round=flow.getInt("round");
        if(phase.equals("long")){data.remove("flowState");return;}
        String next=phase.equals("focus")?(round>=flow.getInt("rounds")?"long":"short"):"focus";
        if(next.equals("focus"))flow.put("round",round+1);
        flow.put("phase",next).put("waiting",!flow.getBoolean("auto"));
        JSONObject timer=linkedFresh(next.equals("focus")?"focus":"break",flow.getLong(next),data.getJSONObject("timer"));
        data.put("timer",timer);if(flow.getBoolean("auto"))start(timer);
    }
    public synchronized String snapshot() {
        try { JSONObject result = new JSONObject(data.toString()); result.getJSONObject("timer").put("elapsedNow", elapsed()); result.put("appearance",WallpaperStore.state(context)).put("audioActive",AmbientService.active()); return result.toString(); }
        catch (JSONException e) { throw new IllegalStateException(e); }
    }
    public synchronized String command(String raw) {
        try {
            checkFinish();
            JSONObject cmd = new JSONObject(raw), t = data.getJSONObject("timer");
            String action = cmd.getString("action");
            switch (action) {
                case "switchTask":changeTask(cmd,false);return snapshot();
                case "startTask":changeTask(cmd,true);return snapshot();
                case "configure":
                    if (running() || t.optString("status").equals("paused")) throw new IllegalArgumentException("请先结束当前计时");
                    String mode = cmd.getString("mode");
                    if (!java.util.Arrays.asList("focus", "up", "down", "break").contains(mode)) throw new IllegalArgumentException("无效计时模式");
                    long duration = Math.max(1, Math.min(86400, cmd.optLong("duration", 1500)));
                    String taskId=cmd.optString("taskId","");JSONObject task=WorkspaceFeatures.find(data.getJSONArray("tasks"),taskId);
                    if(!taskId.isEmpty()&&(task==null||task.optBoolean("done")))throw new IllegalArgumentException("请选择尚未完成的任务");
                    data.put("timer", fresh(mode, duration).put("label",task==null?cmd.optString("label","自由专注"):task.getString("title")).put("taskId",taskId).put("tag",task==null?"":task.optString("tag")));data.remove("flowState");
                    break;
                case "start":
                    if (running()) break;
                    if (t.optString("status").equals("completed")) { t = linkedFresh(t.getString("mode"),t.getLong("duration"),t);data.put("timer",t); }
                    start(t);
                    break;
                case "pause":
                    if (running()) t.put("elapsed", elapsed()).put("status", "paused");
                    break;
                case "extend":
                    if(!running()&&!t.optString("status").equals("paused"))throw new IllegalArgumentException("请先开始计时");
                    if(!countdown())throw new IllegalArgumentException("正计时无需延长");
                    long extra=cmd.optLong("seconds",300);
                    if(extra<1||extra>3600||t.getLong("duration")+extra>86400)throw new IllegalArgumentException("总计时不能超过 24 小时");
                    t.put("duration",t.getLong("duration")+extra);
                    break;
                case "finish":
                    if (running() || t.optString("status").equals("paused")) finish(false);
                    break;
                case "reset":
                    data.put("timer",linkedFresh(t.getString("mode"),t.getLong("duration"),t));data.remove("flowState");
                    break;
                case "flowStart":
                    if(running()||t.optString("status").equals("paused"))throw new IllegalArgumentException("请先结束当前计时");
                    JSONObject flow=new JSONObject(data.getJSONObject("workflow").toString()).put("round",1).put("phase","focus").put("waiting",false);
                    data.put("flowState",flow);t=linkedFresh("focus",flow.getLong("focus"),t);data.put("timer",t);start(t);break;
                case "flowStop":
                    data.remove("flowState");data.put("timer",linkedFresh("focus",data.getJSONObject("workflow").getLong("focus"),t));break;
                case "settings":
                    JSONObject s = cmd.getJSONObject("value");
                    for (String k : new String[]{"focus", "short", "long", "goal"}) {
                        int value = s.optInt(k, data.getJSONObject("settings").optInt(k));
                        if (value < 1 || value > 1440) throw new IllegalArgumentException("时间范围为 1–1440 分钟");
                        data.getJSONObject("settings").put(k, value);
                    }
                    for (String k : new String[]{"sound", "keepAwake", "navigationHaptics"}) if (s.has(k)) data.getJSONObject("settings").put(k, s.getBoolean(k));
                    break;
                case "eventSave":
                    JSONObject event = cmd.getJSONObject("value");
                    String title = event.optString("title").trim();
                    if (title.isEmpty() || title.length() > 40) throw new IllegalArgumentException("请输入 1–40 字的名称");
                    WorkspaceFeatures.date(event.getString("date"),false);CalendarDates.validateRepeat(event.optString("repeat","none"));
                    JSONArray events = data.getJSONArray("events");
                    String id = event.optString("id", java.util.UUID.randomUUID().toString());
                    event.put("id", id).put("title", title);
                    boolean found = false;
                    for (int i = 0; i < events.length(); i++) if (events.getJSONObject(i).getString("id").equals(id)) { events.put(i, event); found = true; break; }
                    if (!found) events.put(event);
                    data.put("_eventsRevision",data.optLong("_eventsRevision")+1);
                    break;
                case "eventDelete":
                    JSONArray list = data.getJSONArray("events");
                    for (int i = list.length()-1; i >= 0; i--) if (list.getJSONObject(i).getString("id").equals(cmd.getString("id"))) list.remove(i);
                    data.put("_eventsRevision",data.optLong("_eventsRevision")+1);
                    break;
                default: if(!WorkspaceFeatures.command(data,cmd))throw new IllegalArgumentException("未知操作");
            }
            save(); return snapshot();
        } catch (Exception e) {
            try { return new JSONObject().put("error", e.getMessage()).toString(); } catch (JSONException impossible) { return "{}"; }
        }
    }
}
