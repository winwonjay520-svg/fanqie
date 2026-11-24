package com.lumifocus.app;

import android.app.*;
import android.content.*;
import android.media.*;
import android.os.*;

public final class AmbientService extends Service {
    private AmbientEngine engine;
    private AudioManager audio;
    private AudioFocusRequest focus;
    private PowerManager.WakeLock wake;
    private static volatile boolean active;
    public static boolean active(){return active;}
    public static void sync(Context c) {
        if(Store.get(c).audioWanted())c.startForegroundService(new Intent(c,AmbientService.class));
        else c.stopService(new Intent(c,AmbientService.class));
    }
    @Override public int onStartCommand(Intent intent,int flags,int id) {
        NotificationManager manager=getSystemService(NotificationManager.class);
        manager.createNotificationChannel(new NotificationChannel("ambient","专注环境声音",NotificationManager.IMPORTANCE_LOW));
        PendingIntent open=PendingIntent.getActivity(this,20,new Intent(this,MainActivity.class).putExtra("openSounds",true),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        PendingIntent stop=PendingIntent.getService(this,21,new Intent(this,AmbientService.class).setAction("stop"),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        startForeground(3,new Notification.Builder(this,"ambient").setSmallIcon(R.drawable.ic_notification).setContentTitle("番茄 · 环境声音").setContentText("给专注留一片安静的空间").setContentIntent(open).setOnlyAlertOnce(true).setOngoing(true).addAction(new Notification.Action.Builder(null,"停止声音",stop).build()).build());
        if(intent!=null&&"stop".equals(intent.getAction())){stopPreference();stopSelf();return START_NOT_STICKY;}
        if(!Store.get(this).audioWanted()){stopSelf();return START_NOT_STICKY;}
        if(engine==null){
            audio=getSystemService(AudioManager.class);engine=new AmbientEngine();
            focus=new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN).setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()).setOnAudioFocusChangeListener(change->{if(engine==null)return;if(change==AudioManager.AUDIOFOCUS_LOSS){stopPreference();stopSelf();}else engine.muted(change!=AudioManager.AUDIOFOCUS_GAIN);}).build();
            if(audio.requestAudioFocus(focus)!=AudioManager.AUDIOFOCUS_REQUEST_GRANTED){stopPreference();stopSelf();return START_NOT_STICKY;}
            try{
                engine.levels(Store.get(this).audioMix());engine.start();
                wake=getSystemService(PowerManager.class).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,getPackageName()+":ambient");wake.acquire(12*60*60*1000L);active=true;
            }catch(RuntimeException e){stopPreference();stopSelf();return START_NOT_STICKY;}
        }else engine.levels(Store.get(this).audioMix());
        return START_NOT_STICKY;
    }
    private void stopPreference(){
        try {org.json.JSONObject m=new org.json.JSONObject(Store.get(this).snapshot()).getJSONObject("mixer").put("playing",false);Store.get(this).command(new org.json.JSONObject().put("action","mixerSave").put("value",m).toString());}catch(org.json.JSONException ignored){}
    }
    @Override public void onDestroy(){active=false;if(engine!=null){engine.close();engine=null;}if(audio!=null&&focus!=null)audio.abandonAudioFocusRequest(focus);if(wake!=null&&wake.isHeld())wake.release();stopForeground(STOP_FOREGROUND_REMOVE);super.onDestroy();}
    @Override public IBinder onBind(Intent intent){return null;}
}
