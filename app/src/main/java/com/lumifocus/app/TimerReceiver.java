package com.lumifocus.app;
import android.content.*;
public class TimerReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c, Intent intent) {
        if ("pause".equals(intent.getAction())) {
            Store.get(c).command("{\"action\":\"pause\"}");
            TimerService.cancelAlarm(c); c.stopService(new Intent(c,TimerService.class));AmbientService.sync(c);FocusWidget.updateAll(c);
        } else if (Store.get(c).checkFinish()) {
            TimerService.completed(c);if(Store.get(c).running())c.startForegroundService(new Intent(c,TimerService.class));else c.stopService(new Intent(c,TimerService.class));
        }
    }
}
