package com.lumifocus.app;

import android.app.*;
import android.content.*;
import android.os.*;
import org.json.*;

public class TimerService extends Service {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable tick = new Runnable() { public void run() {
        Store store = Store.get(TimerService.this);
        if (store.checkFinish()) {completed(TimerService.this);schedule();}
        if (!store.running()) { stopSelf(); return; }
        getSystemService(NotificationManager.class).notify(1, notification());
        handler.postDelayed(this,1000);
    }};
    public static void channels(Context c) {
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel("timer", "正在专注", NotificationManager.IMPORTANCE_LOW));
        NotificationChannel done = new NotificationChannel("done", "计时完成提醒", NotificationManager.IMPORTANCE_HIGH);
        done.enableVibration(true); nm.createNotificationChannel(done);
        nm.createNotificationChannel(new NotificationChannel("silent_done", "静音完成提醒", NotificationManager.IMPORTANCE_LOW));
    }
    private static PendingIntent open(Context c) { return PendingIntent.getActivity(c,0,new Intent(c, MainActivity.class),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE); }
    private static PendingIntent alarm(Context c) { return PendingIntent.getBroadcast(c,2,new Intent(c, TimerReceiver.class).setAction("complete"),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE); }
    public static void cancelAlarm(Context c) { c.getSystemService(AlarmManager.class).cancel(alarm(c)); }
    private void schedule() {
        cancelAlarm(this);
        Store s = Store.get(this);
        if(s.running() && s.countdown()) {
            AlarmManager am = getSystemService(AlarmManager.class);
            long at = SystemClock.elapsedRealtime() + s.remaining();
            if(Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()) am.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, at, alarm(this));
            else am.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP,at,alarm(this));
        }
    }
    public static void completed(Context c) {
        channels(c); cancelAlarm(c);
        boolean sound = true; String mode = "focus";
        try { JSONObject d = new JSONObject(Store.get(c).snapshot()); sound = d.getJSONObject("settings").optBoolean("sound",true); mode = d.optJSONObject("lastCompletion")!=null?d.getJSONObject("lastCompletion").optString("mode"):d.getJSONObject("timer").optString("mode"); } catch(Exception ignored) {}
        c.getSystemService(NotificationManager.class).notify(2,new Notification.Builder(c,sound?"done":"silent_done").setSmallIcon(com.lumifocus.app.R.drawable.ic_notification).setContentTitle(mode.equals("break")?"休息结束，准备好了吗？":"专注完成，做得很好").setContentText(mode.equals("break")?"给自己片刻留白后，继续下一段专注。":"打开番茄，看看这段时间的收获。").setContentIntent(open(c)).setAutoCancel(true).build());
        AmbientService.sync(c);FocusWidget.updateAll(c);
    }
    private Notification notification() {
        Store s = Store.get(this);
        long seconds = (s.countdown() ? s.remaining()+999 : s.elapsed()) / 1000;
        String time = String.format(java.util.Locale.ROOT,"%02d:%02d",seconds/60,seconds%60);
        PendingIntent pause = PendingIntent.getBroadcast(this,3,new Intent(this,TimerReceiver.class).setAction("pause"),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this,"timer").setSmallIcon(R.drawable.ic_notification).setContentTitle("番茄 · " + time).setContentText("留一点时间，给真正重要的事").setContentIntent(open(this)).setOngoing(true).setOnlyAlertOnce(true).addAction(new Notification.Action.Builder(null,"暂停",pause).build()).build();
    }
    @Override public int onStartCommand(Intent intent,int flags,int id) {
        channels(this); startForeground(1,notification()); schedule(); handler.removeCallbacks(tick); handler.post(tick);AmbientService.sync(this);FocusWidget.updateAll(this); return START_STICKY;
    }
    @Override public void onDestroy() { handler.removeCallbacks(tick); super.onDestroy(); }
    @Override public IBinder onBind(Intent i) { return null; }
}
