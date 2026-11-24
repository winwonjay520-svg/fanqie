package com.lumifocus.app;

import android.app.*;
import android.appwidget.*;
import android.content.*;
import android.os.SystemClock;
import android.widget.RemoteViews;
import org.json.*;
import java.time.*;
import java.time.temporal.ChronoUnit;

public final class FocusWidget extends AppWidgetProvider {
    public static void updateAll(Context c){AppWidgetManager manager=AppWidgetManager.getInstance(c);int[] ids=manager.getAppWidgetIds(new ComponentName(c,FocusWidget.class));for(int id:ids)update(c,manager,id);}
    static RemoteViews views(Context c) throws JSONException {
        Store store=Store.get(c);JSONObject d=new JSONObject(store.snapshot()),t=d.getJSONObject("timer");boolean running=store.running(),up=t.optString("mode").equals("up");
        RemoteViews v=new RemoteViews(c.getPackageName(),R.layout.focus_widget);
        v.setTextViewText(R.id.widget_title,t.optString("mode").equals("break")?"休息一下":t.optString("label","自由专注"));
        v.setBoolean(R.id.widget_clock,"setCountDown",!up);
        v.setChronometer(R.id.widget_clock,SystemClock.elapsedRealtime()+(up?-store.elapsed():store.remaining()),null,running);
        // A stopped Chronometer still derives the exact paused or idle value from its base.
        long seconds=0;LocalDate today=LocalDate.now();JSONArray sessions=d.getJSONArray("sessions");
        for(int i=0;i<sessions.length();i++){JSONObject s=sessions.getJSONObject(i);if(Instant.ofEpochMilli(s.optLong("end")).atZone(ZoneId.systemDefault()).toLocalDate().equals(today))seconds+=s.optLong("seconds");}
        v.setTextViewText(R.id.widget_today,"今日 "+seconds/60+" 分钟 · 目标 "+d.getJSONObject("settings").optInt("goal")+" 分钟");
        String eventText="把此刻，留给重要的事";long nearest=Long.MAX_VALUE;JSONArray events=d.getJSONArray("events");
        for(int i=0;i<events.length();i++){JSONObject e=events.getJSONObject(i);try{LocalDate next=CalendarDates.occurrence(e.getString("date"),e.optString("repeat","none"),today);long diff=ChronoUnit.DAYS.between(today,next);if(diff>=0&&diff<nearest){nearest=diff;eventText=e.getString("title")+" · "+(diff==0?"就是今天":diff+" 天后");}}catch(RuntimeException ignored){}}
        v.setTextViewText(R.id.widget_event,eventText);v.setTextViewText(R.id.widget_toggle,running?"暂停":t.optString("status").equals("paused")?"继续专注":"开始专注");
        PendingIntent open=PendingIntent.getActivity(c,30,new Intent(c,MainActivity.class),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        PendingIntent toggle=PendingIntent.getBroadcast(c,31,new Intent(c,FocusWidget.class).setAction("com.lumifocus.TOGGLE"),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        v.setOnClickPendingIntent(R.id.widget_open,open);v.setOnClickPendingIntent(R.id.widget_title,open);v.setOnClickPendingIntent(R.id.widget_event,open);v.setOnClickPendingIntent(R.id.widget_toggle,toggle);return v;
    }
    private static void update(Context c,AppWidgetManager manager,int id){try{manager.updateAppWidget(id,views(c));}catch(JSONException ignored){}}
    @Override public void onUpdate(Context c,AppWidgetManager manager,int[] ids){for(int id:ids)update(c,manager,id);}
    @Override public void onReceive(Context c,Intent intent){
        super.onReceive(c,intent);
        if("com.lumifocus.TOGGLE".equals(intent.getAction())){
            Store s=Store.get(c);boolean completed=s.checkFinish();if(completed)TimerService.completed(c);
            s.command(s.running()?"{\"action\":\"pause\"}":"{\"action\":\"start\"}");
            if(s.running())c.startForegroundService(new Intent(c,TimerService.class));else{TimerService.cancelAlarm(c);c.stopService(new Intent(c,TimerService.class));}AmbientService.sync(c);updateAll(c);
        }
    }
}
