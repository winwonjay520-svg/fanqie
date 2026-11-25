package com.lumifocus.app;

import android.app.*;
import android.os.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.view.*;
import android.webkit.*;
import org.json.*;

public class MainActivity extends Activity {
    private WebView web;
    private boolean resumed;
    private boolean screenKeptOn;
    private boolean largeClock;
    private boolean focusScene;
    private boolean sceneFullscreen;
    private static final int PICK_EVENTS = 21;
    private boolean pageReady;
    private boolean importingEvents;
    private byte[] pendingWrite;
    private BackupStore.Prepared pendingBackup;
    private final java.util.concurrent.atomic.AtomicBoolean documentBusy=new java.util.concurrent.atomic.AtomicBoolean();
    private final java.util.concurrent.ExecutorService imageWorker = java.util.concurrent.Executors.newSingleThreadExecutor();
    private android.widget.FrameLayout container;
    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setStatusBarColor(Color.WHITE);
        getWindow().setNavigationBarColor(Color.WHITE);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        web = new WebView(this); web.setBackgroundColor(Color.WHITE);
        WebView.setWebContentsDebuggingEnabled((getApplicationInfo().flags & android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0);
        web.getSettings().setJavaScriptEnabled(true);
        web.getSettings().setDomStorageEnabled(true);
        web.getSettings().setAllowFileAccess(false);
        web.getSettings().setAllowContentAccess(false);
        web.setWebViewClient(new WebViewClient() {
            @Override public void onPageFinished(WebView view,String url){pageReady=true;handleLaunchIntent();}
            @Override public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) { return true; }
            @Override public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                android.net.Uri uri=request.getUrl();
                if("lumi.local".equals(uri.getHost()) && "/wallpaper.jpg".equals(uri.getPath())) {
                    try { return new WebResourceResponse("image/jpeg",null,200,"OK",java.util.Collections.singletonMap("Cache-Control","no-store"),WallpaperStore.openImage(MainActivity.this)); }
                    catch(java.io.IOException e) { return new WebResourceResponse("image/jpeg",null,new java.io.ByteArrayInputStream(new byte[0])); }
                }
                return null;
            }
        });
        web.addJavascriptInterface(new Bridge(), "Native");
        container = new android.widget.FrameLayout(this);
        container.setBackgroundColor(Color.WHITE);
        container.addView(web, new android.widget.FrameLayout.LayoutParams(-1,-1));
        if (Build.VERSION.SDK_INT >= 30) {
            getWindow().setDecorFitsSystemWindows(false);
            container.setOnApplyWindowInsetsListener((view, insets) -> {
                android.graphics.Insets safe = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout() | WindowInsets.Type.ime());
                view.setPadding(safe.left,safe.top,safe.right,safe.bottom);
                return WindowInsets.CONSUMED;
            });
        }
        setContentView(container);
        web.loadUrl("file:///android_asset/index.html");
    }
    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (pageReady) handleLaunchIntent();
    }

    private void handleLaunchIntent() {
        Intent intent = getIntent();
        if (Intent.ACTION_VIEW.equals(intent.getAction())) {
            android.net.Uri uri = intent.getData();
            intent.setAction(null);
            showEventImport(uri);
        } else if (intent.getBooleanExtra("openSounds", false)) {
            intent.removeExtra("openSounds");
            web.evaluateJavascript("window.showSounds&&showSounds()", null);
        }
    }

    private void eventImportMessage(String message) {
        android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_LONG).show();
    }

    private void showEventImport(android.net.Uri uri) {
        if (uri == null || !"content".equals(uri.getScheme())) {
            eventImportMessage("请通过文件选择器打开倒数日 JSON 文件");
            return;
        }
        if (importingEvents) {
            eventImportMessage("请先完成当前倒数日导入");
            return;
        }
        importingEvents = true;
        imageWorker.execute(() -> {
            try (java.io.InputStream in = getContentResolver().openInputStream(uri)) {
                EventImport document = EventImport.read(in);
                String preview = document.preview();
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    new AlertDialog.Builder(this).setTitle("导入倒数日").setMessage(preview)
                            .setNegativeButton("取消", (dialog, which) -> importingEvents = false)
                            .setOnCancelListener(dialog -> importingEvents = false)
                            .setPositiveButton("确认导入", (dialog, which) -> importEvents(document)).show();
                });
            } catch (Exception e) {
                String reason = e instanceof IllegalArgumentException || e instanceof java.io.IOException
                        && !(e instanceof java.io.FileNotFoundException) ? e.getMessage() : null;
                failEventImport(reason == null ? "无法读取倒数日，请选择有效的 JSON 文件" : reason);
            }
        });
    }

    private void importEvents(EventImport document) {
        imageWorker.execute(() -> {
            try {
                EventImport.Merge result = Store.get(this).importEvents(document);
                runOnUiThread(() -> {
                    importingEvents = false;
                    if (isFinishing() || isDestroyed()) return;
                    String message = "已新增 " + result.added + " 项，跳过 " + result.skipped + " 项重复记录";
                    web.evaluateJavascript("closeSheet();refresh();navigate('events');toast(" + JSONObject.quote(message) + ")", null);
                });
            } catch (Exception e) {
                failEventImport(e.getMessage() == null ? "倒数日导入失败，请重试" : e.getMessage());
            }
        });
    }

    private void failEventImport(String message) {
        runOnUiThread(() -> {
            importingEvents = false;
            if (!isFinishing() && !isDestroyed()) eventImportMessage(message);
        });
    }

    public class Bridge {
        @JavascriptInterface public void navigationHaptic() {
            if (!Store.get(MainActivity.this).navigationHapticsEnabled()) return;
            runOnUiThread(() -> {
                if (!resumed || !web.hasWindowFocus()) return;
                int feedback = Build.VERSION.SDK_INT >= 34
                        ? HapticFeedbackConstants.SEGMENT_TICK
                        : HapticFeedbackConstants.CLOCK_TICK;
                web.performHapticFeedback(feedback);
            });
        }

        @JavascriptInterface public String appearance(String raw) {
            try { String result=WallpaperStore.update(MainActivity.this,raw).toString();runOnUiThread(()->updateSystemColors());return result; }
            catch(Exception e) { try{return new JSONObject().put("error",e.getMessage()).toString();}catch(JSONException impossible){return "{}";} }
        }
        @JavascriptInterface public void chooseWallpaper() {
            runOnUiThread(()->{
                Intent pick;
                if(Build.VERSION.SDK_INT>=33) pick=new Intent(android.provider.MediaStore.ACTION_PICK_IMAGES).setType("image/*");
                else pick=new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("image/*");
                try {startActivityForResult(pick,20);}
                catch(ActivityNotFoundException e) {
                    try {startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("image/*"),20);}
                    catch(ActivityNotFoundException unavailable){web.evaluateJavascript("wallpaperPicked(false,'这台设备没有可用的图片选择器')",null);}
                }
            });
        }
        @JavascriptInterface public String removeWallpaper() {
            try {String result=WallpaperStore.removeImage(MainActivity.this).toString();runOnUiThread(()->updateSystemColors());return result;}
            catch(Exception e) {return "{\"error\":\"图片移除失败，请重试\"}";}
        }
        @JavascriptInterface public String snapshot() { runOnUiThread(() -> updateAwake()); return Store.get(MainActivity.this).snapshot(); }
        @JavascriptInterface public String poll(String revision) {
            long known=-1;try{known=Long.parseLong(revision);}catch(NumberFormatException ignored){}
            runOnUiThread(()->updateAwake());return Store.get(MainActivity.this).poll(known);
        }
        @JavascriptInterface public String command(String raw) {
            String result = Store.get(MainActivity.this).command(raw);
            runOnUiThread(() -> { syncService(); updateAwake(); });
            return result;
        }
        @JavascriptInterface public void notifyPermission() {
            runOnUiThread(() -> { if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission("android.permission.POST_NOTIFICATIONS") != PackageManager.PERMISSION_GRANTED) requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 1); });
        }
        @JavascriptInterface public void exportData() {
            runOnUiThread(() -> {
                Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT).setType("application/json").addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_TITLE, "番茄-备份.json");
                startActivityForResult(intent, 10);
            });
        }
        @JavascriptInterface public void exportBackup(String ui) {
            if(!documentBusy.compareAndSet(false,true))return;
            imageWorker.execute(()->{
                try{byte[] bytes=BackupStore.export(getApplicationContext(),new JSONObject(ui));runOnUiThread(()->{if(!isDestroyed()){pendingWrite=bytes;createDocument("application/zip","番茄-完整备份.zip",11);}});}
                catch(Exception e){documentBusy.set(false);callbackError("备份导出失败，请检查剩余空间");}
            });
        }
        @JavascriptInterface public void chooseEventImport() {
            runOnUiThread(() -> {
                Intent pick = new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
                        .setType("*/*").putExtra(Intent.EXTRA_MIME_TYPES,
                                new String[]{"application/json", "text/json", "text/plain", "application/octet-stream"});
                try { startActivityForResult(pick, PICK_EVENTS); }
                catch (ActivityNotFoundException e) { eventImportMessage("设备没有可用的文件选择器"); }
            });
        }
        @JavascriptInterface public void chooseBackup(){runOnUiThread(()->{pendingBackup=null;Intent pick=new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_MIME_TYPES,new String[]{"application/zip","application/json","application/octet-stream"});startActivityForResult(pick,12);});}
        @JavascriptInterface public void discardBackup(){pendingBackup=null;}
        @JavascriptInterface public void restoreBackup(){
            BackupStore.Prepared selected=pendingBackup;pendingBackup=null;if(selected==null){callbackError("请重新选择备份文件");return;}
            imageWorker.execute(()->{try{BackupStore.restore(getApplicationContext(),selected);runOnUiThread(()->{if(!isDestroyed()){updateSystemColors();updateAwake();web.evaluateJavascript("window.backupRestored&&backupRestored("+selected.ui+")",null);}});}catch(Exception e){callbackError(e.getMessage()==null?"恢复失败，原数据已保留":e.getMessage());}});
        }
        @JavascriptInterface public void saveStatsImage(String uri){ savePng(uri, "番茄-统计.png"); }
        @JavascriptInterface public void saveEventImage(String uri){ savePng(uri, "番茄-倒数日.png"); }
        private void savePng(String uri, String name){
            if(!documentBusy.compareAndSet(false,true))return;
            try{
                byte[] bytes=decodeStatsImage(uri);
                runOnUiThread(()->{
                    try{pendingWrite=bytes;createDocument("image/png",name,13);}
                    catch(ActivityNotFoundException e){pendingWrite=null;documentBusy.set(false);callbackError("无法打开文件保存窗口");}
                });
            }catch(Exception e){documentBusy.set(false);callbackError("图片生成失败，请重试");}
        }
        @JavascriptInterface public void shareEventText(String text){
            if(text==null||text.trim().isEmpty())return;
            runOnUiThread(()->{
                Intent send=new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT,text);
                try{startActivity(Intent.createChooser(send,"分享倒数日"));}
                catch(ActivityNotFoundException e){callbackError("未找到可以分享的应用");}
            });
        }
        @JavascriptInterface public void clockMode(boolean enabled){runOnUiThread(()->{largeClock=enabled;setRequestedOrientation(enabled?android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE:android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);updateAwake();updateSystemColors();});}
        @JavascriptInterface public void focusSceneMode(boolean enabled,boolean fullscreen){runOnUiThread(()->{focusScene=enabled;sceneFullscreen=enabled&&fullscreen;updateAwake();updateSystemColors();});}
        @JavascriptInterface public void requestWidget(){runOnUiThread(()->{android.appwidget.AppWidgetManager m=android.appwidget.AppWidgetManager.getInstance(MainActivity.this);if(m.isRequestPinAppWidgetSupported())m.requestPinAppWidget(new ComponentName(MainActivity.this,FocusWidget.class),null,null);else android.widget.Toast.makeText(MainActivity.this,"请长按手机桌面，在小组件中找到「番茄」",android.widget.Toast.LENGTH_LONG).show();});}
    }
    static byte[] decodeStatsImage(String uri){
        String prefix="data:image/png;base64,";if(!uri.startsWith(prefix)||uri.length()>8*1024*1024)throw new IllegalArgumentException("无效图片");
        byte[] bytes=android.util.Base64.decode(uri.substring(prefix.length()),android.util.Base64.DEFAULT);
        android.graphics.BitmapFactory.Options bounds=new android.graphics.BitmapFactory.Options();bounds.inJustDecodeBounds=true;android.graphics.BitmapFactory.decodeByteArray(bytes,0,bytes.length,bounds);
        if(!"image/png".equals(bounds.outMimeType)||bounds.outWidth<1||bounds.outHeight<1||bounds.outWidth>4096||bounds.outHeight>8192||(long)bounds.outWidth*bounds.outHeight>12000000)throw new IllegalArgumentException("无效图片尺寸");return bytes;
    }
    private void createDocument(String mime,String name,int request){startActivityForResult(new Intent(Intent.ACTION_CREATE_DOCUMENT).setType(mime).addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_TITLE,name),request);}
    private void callbackError(String message){runOnUiThread(()->{if(!isDestroyed())web.evaluateJavascript("window.backupFailed&&backupFailed("+JSONObject.quote(message)+")",null);});}
    @Override protected void onActivityResult(int request, int result, Intent intent) {
        super.onActivityResult(request,result,intent);
        if (request == PICK_EVENTS) {
            if (result == RESULT_OK && intent != null) showEventImport(intent.getData());
            return;
        }
        if(request==11||request==13){
            documentBusy.set(false);
            byte[] bytes=pendingWrite;pendingWrite=null;
            if(result==RESULT_OK&&intent!=null&&intent.getData()!=null&&bytes!=null){android.net.Uri uri=intent.getData();imageWorker.execute(()->{try(java.io.OutputStream out=getContentResolver().openOutputStream(uri)){if(out==null)throw new java.io.IOException();out.write(bytes);runOnUiThread(()->{if(!isDestroyed())web.evaluateJavascript("toast('文件已保存')",null);});}catch(Exception e){callbackError("文件保存失败，请重试");}});}return;
        }
        if(request==12){
            if(result!=RESULT_OK||intent==null||intent.getData()==null)return;android.net.Uri uri=intent.getData();
            imageWorker.execute(()->{try(java.io.InputStream in=getContentResolver().openInputStream(uri)){BackupStore.Prepared prepared=BackupStore.prepare(in);JSONObject summary=prepared.summary();runOnUiThread(()->{if(!isDestroyed()){pendingBackup=prepared;web.evaluateJavascript("window.backupReady&&backupReady("+summary+")",null);}});}catch(Exception e){callbackError("无法读取备份："+(e.getMessage()==null?"文件无效":e.getMessage()));}});return;
        }
        if(request==20) {
            if(result!=RESULT_OK||intent==null||intent.getData()==null) {web.evaluateJavascript("window.wallpaperPicked&&wallpaperPicked(false,null)",null);return;}
            android.net.Uri uri=intent.getData();
            web.evaluateJavascript("window.wallpaperLoading&&wallpaperLoading()",null);
            imageWorker.execute(()->{
                String message=null;
                try {WallpaperStore.importImage(getApplicationContext(),uri);}
                catch(OutOfMemoryError e){message="图片过大，请选择尺寸较小的图片";}
                catch(Exception e){message="图片导入失败："+(e.getMessage()==null?"请换一张图片重试":e.getMessage());}
                final String error=message;
                runOnUiThread(()->{if(!isDestroyed()){updateSystemColors();web.evaluateJavascript("window.wallpaperPicked&&wallpaperPicked("+(error==null)+","+(error==null?"null":JSONObject.quote(error))+")",null);}});
            });return;
        }
        if (request == 10 && result == RESULT_OK && intent != null && intent.getData() != null) {
            android.net.Uri uri=intent.getData();imageWorker.execute(()->{try(java.io.OutputStream out=getContentResolver().openOutputStream(uri)){if(out==null)throw new java.io.IOException();out.write(Store.get(this).snapshot().getBytes(java.nio.charset.StandardCharsets.UTF_8));runOnUiThread(()->android.widget.Toast.makeText(this,"备份已导出",android.widget.Toast.LENGTH_SHORT).show());}catch(Exception e){callbackError("导出失败，请重试");}});
        }
    }
    private void syncService() {
        if (!resumed) return;
        if (Store.get(this).running()) startForegroundService(new Intent(this, TimerService.class));
        else { stopService(new Intent(this, TimerService.class)); TimerService.cancelAlarm(this); }
        AmbientService.sync(this);FocusWidget.updateAll(this);
    }
    private void updateAwake() {
        try {
            boolean keep = largeClock || (Store.get(this).keepAwake() && Store.get(this).running());
            if (keep == screenKeptOn) return;
            screenKeptOn = keep;
            if (keep) getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        } catch(Exception ignored) {}
    }
    private void updateSystemColors() {
        JSONObject look=WallpaperStore.state(this);
        boolean photo=look.optString("preset").equals("photo")&&look.optBoolean("hasImage");
        boolean dark=largeClock||(focusScene&&photo)||look.optString("tone").equals("dark");
        int color=largeClock||look.optString("preset").equals("black")?Color.BLACK:dark?Color.rgb(22,32,38):Color.WHITE;
        if(!largeClock)color=switch(look.optString("preset")){
            case "mist" -> Color.parseColor(dark?"#283444":"#edf3fa");
            case "ocean" -> Color.parseColor(dark?"#123448":"#e3f2f7");
            case "sunset" -> Color.parseColor(dark?"#472637":"#faece8");
            case "night" -> Color.parseColor(dark?"#1d1f3f":"#eeeaf9");
            default -> color;
        };
        if(!largeClock&&photo)color=PhotoPalette.surface(look.optString("photoColor"),dark);
        container.setBackgroundColor(color);
        web.setBackgroundColor(color);
        getWindow().setStatusBarColor(color);getWindow().setNavigationBarColor(color);
        getWindow().getDecorView().setSystemUiVisibility(dark?0:View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        updateSceneWindow();
    }
    private void updateSceneWindow(){
        boolean hide=focusScene&&sceneFullscreen;
        if(Build.VERSION.SDK_INT>=30){
            WindowInsetsController controller=getWindow().getInsetsController();
            if(controller!=null){controller.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);if(hide)controller.hide(WindowInsets.Type.systemBars());else controller.show(WindowInsets.Type.systemBars());}
        }else{
            View decor=getWindow().getDecorView();int flags=decor.getSystemUiVisibility();
            int fullscreen=View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY|View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN|View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_LAYOUT_STABLE;
            decor.setSystemUiVisibility(hide?flags|fullscreen:flags&~fullscreen);
        }
    }
    @Override public void onWindowFocusChanged(boolean focused){super.onWindowFocusChanged(focused);if(focused)updateSceneWindow();}
    @Override protected void onResume() { super.onResume(); resumed = true; if(Store.get(this).checkFinish())TimerService.completed(this); syncService(); updateAwake(); updateSystemColors(); if(web != null) web.evaluateJavascript("window.refresh&&refresh()",null); }
    @Override protected void onPause() { resumed = false; super.onPause(); }
    @Override public void onBackPressed() { web.evaluateJavascript("window.handleBack&&handleBack()",value -> { if("false".equals(value)) moveTaskToBack(true); }); }
    @Override protected void onDestroy() { imageWorker.shutdown();if(web != null) { web.removeJavascriptInterface("Native"); web.destroy(); } super.onDestroy(); }
}
