package com.lumifocus.app;

import org.json.*;
import java.time.LocalDate;
import java.util.*;

/** Validated product data; Store owns persistence and synchronization. */
final class WorkspaceFeatures {
    private WorkspaceFeatures() {}
    static void defaults(JSONObject d) throws JSONException {
        if(!d.has("tasks"))d.put("tasks",new JSONArray());
        if(!d.has("planner"))d.put("planner",new JSONObject().put("weekGoal",600).put("items",new JSONArray()));
        if(!d.has("workflow"))d.put("workflow",new JSONObject().put("focus",1500).put("short",300).put("long",900).put("rounds",4).put("auto",false));
        if(!d.has("mixer"))d.put("mixer",new JSONObject().put("rain",35).put("forest",0).put("cafe",0).put("playing",false).put("followTimer",true));
        if(!d.has("preferences"))d.put("preferences",new JSONObject().put("clockStyle","digital").put("clockFace","timer"));
        if(!d.has("_workspaceRevision"))d.put("_workspaceRevision",0);
    }
    static String text(JSONObject o,String key,int max,boolean required) {
        String s=o.optString(key,"").trim();
        if((required&&s.isEmpty())||s.length()>max)throw new IllegalArgumentException("请填写有效的"+switch(key){case "title"->"名称";case "note"->"记录（最多 240 字）";default->"内容";});
        return s;
    }
    static int number(JSONObject o,String key,int min,int max) throws JSONException {
        double v=o.getDouble(key);if(!Double.isFinite(v)||v!=Math.rint(v)||v<min||v>max)throw new IllegalArgumentException("数值超出范围："+key);
        return (int)v;
    }
    static String date(String value,boolean optional) {
        if(optional&&value.isEmpty())return "";
        LocalDate d=LocalDate.parse(value);if(d.getYear()<1900||d.getYear()>2200)throw new IllegalArgumentException("日期范围为 1900–2200 年");return d.toString();
    }
    static String id(JSONObject o){String s=o.optString("id","");if(s.isEmpty())return UUID.randomUUID().toString();if(s.length()>80)throw new IllegalArgumentException("无效标识");return s;}
    static JSONObject task(JSONObject input) throws JSONException {
        JSONArray children=input.optJSONArray("subtasks");if(children==null)children=new JSONArray();if(children.length()>50)throw new IllegalArgumentException("每个任务最多 50 个子任务");
        JSONArray sub=new JSONArray();Set<String> ids=new HashSet<>();
        for(int i=0;i<children.length();i++){JSONObject c=children.getJSONObject(i);String cid=id(c);if(!ids.add(cid))throw new IllegalArgumentException("子任务标识重复");sub.put(new JSONObject().put("id",cid).put("title",text(c,"title",80,true)).put("done",c.optBoolean("done")));}
        JSONObject result=new JSONObject().put("id",id(input)).put("title",text(input,"title",80,true)).put("tag",text(input,"tag",24,false)).put("priority",number(input,"priority",1,3)).put("estimate",number(input,"estimate",1,100)).put("due",date(input.optString("due"),true)).put("done",input.optBoolean("done")).put("subtasks",sub).put("created",input.optLong("created",System.currentTimeMillis()));
        String mode=input.optString("timerMode","focus"),cover=input.optString("cover","auto");
        if(!Arrays.asList("focus","up","down").contains(mode)||!Arrays.asList("auto","mist","ocean","sunset","night","photo").contains(cover))throw new IllegalArgumentException("无效待办设置");
        result.put("timerMode",mode).put("cover",cover);if(input.has("duration"))result.put("duration",number(input,"duration",1,86400));return result;
    }
    static JSONObject plan(JSONObject input) throws JSONException {
        return new JSONObject().put("id",id(input)).put("title",text(input,"title",80,true)).put("date",date(input.getString("date"),false)).put("minutes",number(input,"minutes",1,1440)).put("done",input.optBoolean("done"));
    }
    static JSONObject workflow(JSONObject input) throws JSONException {
        return new JSONObject().put("focus",number(input,"focus",1,86400)).put("short",number(input,"short",1,86400)).put("long",number(input,"long",1,86400)).put("rounds",number(input,"rounds",1,12)).put("auto",input.optBoolean("auto"));
    }
    static JSONObject mixer(JSONObject input) throws JSONException {
        return new JSONObject().put("rain",number(input,"rain",0,100)).put("forest",number(input,"forest",0,100)).put("cafe",number(input,"cafe",0,100)).put("playing",input.optBoolean("playing")).put("followTimer",input.optBoolean("followTimer",true));
    }
    static JSONObject find(JSONArray rows,String id) throws JSONException {for(int i=0;i<rows.length();i++)if(rows.getJSONObject(i).optString("id").equals(id))return rows.getJSONObject(i);return null;}
    private static void upsert(JSONArray rows,JSONObject row,int limit) throws JSONException {
        for(int i=0;i<rows.length();i++)if(rows.getJSONObject(i).optString("id").equals(row.getString("id"))){rows.put(i,row);return;}
        if(rows.length()>=limit)throw new IllegalArgumentException("记录数量已达上限");rows.put(row);
    }
    private static void remove(JSONArray rows,String id) throws JSONException {for(int i=rows.length()-1;i>=0;i--)if(rows.getJSONObject(i).optString("id").equals(id))rows.remove(i);}
    static boolean command(JSONObject d,JSONObject cmd) throws JSONException {
        switch(cmd.getString("action")) {
            case "taskSave":upsert(d.getJSONArray("tasks"),task(cmd.getJSONObject("value")),5000);break;
            case "taskDelete":
                String deleted=cmd.getString("id");remove(d.getJSONArray("tasks"),deleted);JSONObject timer=d.getJSONObject("timer");
                if(timer.optString("taskId").equals(deleted)&&!Arrays.asList("running","paused").contains(timer.optString("status")))timer.put("taskId","").put("tag","");break;
            case "planSave":upsert(d.getJSONObject("planner").getJSONArray("items"),plan(cmd.getJSONObject("value")),10000);break;
            case "planDelete":remove(d.getJSONObject("planner").getJSONArray("items"),cmd.getString("id"));break;
            case "weekGoal":d.getJSONObject("planner").put("weekGoal",number(cmd,"minutes",1,10080));break;
            case "workflowSave":d.put("workflow",workflow(cmd.getJSONObject("value")));break;
            case "mixerSave":d.put("mixer",mixer(cmd.getJSONObject("value")));break;
            case "clockPreferences":
                String style=cmd.optString("style"),face=cmd.optString("face");
                if(!Arrays.asList("digital","ring").contains(style)||!Arrays.asList("timer","clock").contains(face))throw new IllegalArgumentException("无效时钟样式");
                d.put("preferences",new JSONObject().put("clockStyle",style).put("clockFace",face));break;
            case "sessionReview":
                String mood=cmd.optString("mood"),note=text(cmd,"note",240,false);
                if(!Arrays.asList("great","calm","tired","distracted","").contains(mood))throw new IllegalArgumentException("无效心情");
                JSONObject session=find(d.getJSONArray("sessions"),cmd.getString("id"));if(session==null)throw new IllegalArgumentException("这条记录已不存在");
                session.put("mood",mood).put("note",note);d.put("_historyRevision",d.optLong("_historyRevision")+1);break;
            default:return false;
        }
        d.put("_workspaceRevision",d.optLong("_workspaceRevision")+1);return true;
    }
    static JSONObject validateBackup(JSONObject source) throws JSONException {
        JSONObject d=new JSONObject(source.toString());defaults(d);
        JSONObject t=d.getJSONObject("timer"),s=d.getJSONObject("settings");
        if(!Arrays.asList("focus","up","down","break").contains(t.getString("mode")))throw new IllegalArgumentException("备份计时模式无效");
        number(t,"duration",1,86400);
        if(!Arrays.asList("idle","paused","running","completed").contains(t.getString("status")))throw new IllegalArgumentException("备份计时状态无效");
        long elapsed=t.optLong("elapsedNow",t.optLong("elapsed"));if(elapsed<0||elapsed>31536000000L)throw new IllegalArgumentException("备份计时时长无效");
        if(t.has("accountedSeconds"))t.put("accountedSeconds",number(t,"accountedSeconds",0,(int)(elapsed/1000)));
        if(t.has("segmentStarted")){double start=t.getDouble("segmentStarted");if(!Double.isFinite(start)||start<=0||start>7289654400000L||start!=Math.rint(start))throw new IllegalArgumentException("备份待办切换时间无效");}
        t.put("elapsed",elapsed);t.remove("elapsedNow");if(t.optString("status").equals("running"))t.put("status","paused");t.remove("base");t.remove("wall");t.remove("boot");
        for(String k:new String[]{"focus","short","long","goal"})number(s,k,1,1440);
        JSONArray sessions=d.getJSONArray("sessions");if(sessions.length()>200000)throw new IllegalArgumentException("备份记录过多");
        Set<String> ids=new HashSet<>();
        for(int i=0;i<sessions.length();i++){JSONObject row=sessions.getJSONObject(i);String rid=row.getString("id");if(rid.isEmpty()||rid.length()>80||!ids.add(rid))throw new IllegalArgumentException("备份记录标识无效或重复");double end=row.getDouble("end"),seconds=row.getDouble("seconds");if(!Double.isFinite(end)||end<=0||end>7289654400000L||!Double.isFinite(seconds)||seconds<1||seconds>31536000||seconds!=Math.rint(seconds)||!Arrays.asList("focus","up","down").contains(row.getString("mode")))throw new IllegalArgumentException("备份专注记录无效");text(row,"label",80,false);text(row,"note",240,false);if(!Arrays.asList("great","calm","tired","distracted","").contains(row.optString("mood")))throw new IllegalArgumentException("备份心情无效");}
        JSONArray tasks=d.getJSONArray("tasks"),plans=d.getJSONObject("planner").getJSONArray("items"),events=d.getJSONArray("events");
        if(tasks.length()>5000||plans.length()>10000||events.length()>5000)throw new IllegalArgumentException("备份内容过多");
        ids.clear();for(int i=0;i<tasks.length();i++){JSONObject row=task(tasks.getJSONObject(i));if(!ids.add(row.getString("id")))throw new IllegalArgumentException("任务标识重复");tasks.put(i,row);}
        ids.clear();for(int i=0;i<plans.length();i++){JSONObject row=plan(plans.getJSONObject(i));if(!ids.add(row.getString("id")))throw new IllegalArgumentException("计划标识重复");plans.put(i,row);}
        ids.clear();for(int i=0;i<events.length();i++){JSONObject row=events.getJSONObject(i);if(!ids.add(row.getString("id")))throw new IllegalArgumentException("倒计日标识重复");text(row,"title",40,true);date(row.getString("date"),false);CalendarDates.validateRepeat(row.optString("repeat","none"));}
        number(d.getJSONObject("planner"),"weekGoal",1,10080);d.put("workflow",workflow(d.getJSONObject("workflow")));d.put("mixer",mixer(d.getJSONObject("mixer")).put("playing",false));
        JSONObject pref=d.getJSONObject("preferences");if(!Arrays.asList("digital","ring").contains(pref.optString("clockStyle"))||!Arrays.asList("timer","clock").contains(pref.optString("clockFace")))throw new IllegalArgumentException("备份时钟设置无效");
        // A restored workflow requires an explicit restart; never launch background rounds from a file.
        d.remove("flowState");d.remove("lastCompletion");return d;
    }
}
