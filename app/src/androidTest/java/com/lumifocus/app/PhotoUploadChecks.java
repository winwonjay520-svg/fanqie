package com.lumifocus.app;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.net.Uri;
import android.provider.MediaStore;
import android.webkit.WebView;
import org.json.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

/** Replace two owned gallery fixtures through the actual native picker result path. */
final class PhotoUploadChecks {
    private final Instrumentation test;private final WebView web;private final Context context;private final StringBuilder report=new StringBuilder();private int failures;
    PhotoUploadChecks(Instrumentation t,WebView w){test=t;web=w;context=t.getTargetContext();}
    private String js(String code)throws Exception{CountDownLatch latch=new CountDownLatch(1);String[] result=new String[1];test.runOnMainSync(()->web.evaluateJavascript("(()=>{"+code+"})()",r->{result[0]=r;latch.countDown();}));if(!latch.await(10,TimeUnit.SECONDS))throw new AssertionError("WebView timeout");return result[0];}
    private void check(String code,String label)throws Exception{boolean ok="true".equals(js(code));report.append(ok?"PASS ":"FAIL ").append(label).append('\n');if(!ok)failures++;}
    private void await(String code)throws Exception{for(int i=0;i<70;i++){if("true".equals(js(code)))return;Thread.sleep(100);}throw new AssertionError("Timed out: "+code);}
    private void systemColors(boolean dark,String label)throws Exception{
        int expected=PhotoPalette.surface(WallpaperStore.state(context).optString("photoColor"),dark);int[] actual=new int[2];
        test.runOnMainSync(()->{android.view.Window w=((Activity)web.getContext()).getWindow();actual[0]=w.getStatusBarColor();actual[1]=w.getNavigationBarColor();});
        if(actual[0]!=expected||actual[1]!=expected)throw new AssertionError("Photo system bars differ from "+label);report.append("PASS Photo system bars follow ").append(label).append('\n');
    }
    private void samplingChecks()throws Exception{
        for(int color:new int[]{Color.BLACK,Color.WHITE,Color.RED,Color.BLUE}){
            Bitmap b=Bitmap.createBitmap(3,3,Bitmap.Config.ARGB_8888);b.eraseColor(color);String actual=PhotoPalette.fromBitmap(b);
            if(!actual.equals(String.format(java.util.Locale.ROOT,"#%06x",color&0xffffff))||b.isRecycled())throw new AssertionError("Neutral/solid photo sampling failed");b.recycle();
        }
        Bitmap transparent=Bitmap.createBitmap(2,2,Bitmap.Config.ARGB_8888);if(!PhotoPalette.fromBitmap(transparent).equals(PhotoPalette.FALLBACK))throw new AssertionError("Transparent image fallback failed");transparent.recycle();
        if(PhotoPalette.valid("url(bad)")||PhotoPalette.valid("#abc")||!PhotoPalette.valid("#a1B2c3"))throw new AssertionError("Photo color validation failed");
        report.append("PASS Solid, neutral, transparent and invalid-color sampling cases are bounded and deterministic\n");
    }
    private void shot(String name)throws Exception{js("glassMotion.clear();$('toast').classList.remove('show');return true");test.waitForIdleSync();Thread.sleep(600);Bitmap b=test.getUiAutomation().takeScreenshot();try(FileOutputStream out=new FileOutputStream(new File(context.getFilesDir(),name+".png"))){b.compress(Bitmap.CompressFormat.PNG,100,out);}b.recycle();}
    private Uri fixture(String name,int first,int second)throws Exception{
        Bitmap bitmap=Bitmap.createBitmap(1280,800,Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(bitmap);Paint paint=new Paint(3);
        paint.setShader(new LinearGradient(0,0,1280,800,first,second,Shader.TileMode.CLAMP));canvas.drawRect(0,0,1280,800,paint);paint.setShader(null);paint.setColor(second);canvas.drawCircle(980,140,105,paint);
        ContentValues value=new ContentValues();value.put(MediaStore.Images.Media.DISPLAY_NAME,"Lumi-"+name+"-QA.jpg");value.put(MediaStore.Images.Media.MIME_TYPE,"image/jpeg");value.put(MediaStore.Images.Media.RELATIVE_PATH,"Pictures/LumiFocusQA");Uri uri=context.getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,value);if(uri==null)throw new AssertionError("Fixture creation failed");
        try(OutputStream out=context.getContentResolver().openOutputStream(uri)){bitmap.compress(Bitmap.CompressFormat.JPEG,92,out);}finally{bitmap.recycle();}return uri;
    }
    String run(String profile)throws Exception{
        if(!context.getPackageName().endsWith(".debug"))throw new AssertionError("Debug installation required");
        JSONObject original=WallpaperStore.state(context);byte[] image=null;if(original.optBoolean("hasImage"))try(InputStream in=WallpaperStore.openImage(context)){image=BackupStore.read(in,8*1024*1024);}
        Uri[] selected={null};List<Uri> created=new ArrayList<>();int[] requests={0};
        Instrumentation.ActivityMonitor monitor=new Instrumentation.ActivityMonitor(){@Override public Instrumentation.ActivityResult onStartActivity(Intent intent){if(MediaStore.ACTION_PICK_IMAGES.equals(intent.getAction())||Intent.ACTION_OPEN_DOCUMENT.equals(intent.getAction())){requests[0]++;return new Instrumentation.ActivityResult(selected[0]==null?Activity.RESULT_CANCELED:Activity.RESULT_OK,selected[0]==null?null:new Intent().setData(selected[0]).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION));}return null;}};
        test.addMonitor(monitor);
        try{
            samplingChecks();js("if(largeClockOpen)setLargeClock(false);setFocusScene(false);closeSheet();window.uploadColors={};window.uploadDarkColors={};return true");
            for(String name:new String[]{"cool","warm"}){
                Uri uri=fixture(name,name.equals("cool")?0xff245b98:0xffc45432,name.equals("cool")?0xff8fcbdc:0xfff2bb78);created.add(uri);selected[0]=uri;
                String previous=WallpaperStore.state(context).optString("imageVersion");
                js("navigate('stats');$('statsWallpaperButton').click();return true");await("return data.appearance.preset==='photo'&&data.appearance.imageVersion!=="+JSONObject.quote(previous));
                check("return document.body.classList.contains('photo-background')&&!document.body.classList.contains('palette-theme')&&data.appearance.liquid&&data.appearance.material==='clear'","Uploading the "+name+" gallery image enables photo liquid glass");
                check("return getComputedStyle(document.documentElement).getPropertyValue('--wallpaper-image').includes(data.appearance.imageVersion)","The "+name+" background uses a new private-image cache key");
                js("window.uploadProbe=new Image();uploadProbe.src='https://lumi.local/wallpaper.jpg?v='+data.appearance.imageVersion;return true");await("return uploadProbe.complete&&uploadProbe.naturalWidth>0");
                check("const c=getComputedStyle(document.querySelector('.reference-total'));const key=c.backgroundColor;const different=!Object.values(uploadColors).includes(key);uploadColors['"+name+"']=key;return different","The "+name+" upload changes the glass tint to match the image");
                check("return /^#[a-f0-9]{6}$/i.test(data.appearance.photoColor)&&getComputedStyle(document.body).getPropertyValue('--photo-tint').trim()===data.appearance.photoPalette.light.tint","The "+name+" photo color is persisted and supplies the light glass tint");systemColors(false,name+" light");
                report.append("UPLOAD ").append(name).append(' ').append(js("return {version:data.appearance.imageVersion,tint:getComputedStyle(document.body).getPropertyValue('--photo-tint'),glass:getComputedStyle(document.querySelector('.reference-total')).backgroundColor}")).append('\n');
                for(String page:new String[]{"focus","workspace","events","stats"}){
                    js("navigate('"+page+"');return true");check("return document.body.classList.contains('photo-background')&&document.documentElement.scrollWidth<=innerWidth+1","The "+name+" photo glass fits "+page);shot("upload-"+profile+"-"+name+"-"+page);
                }
                js("navigate('focus');setFocusScene(true);return true");check("return $('focusScene').dataset.background==='photo'&&$('focusScene').style.getPropertyValue('--scene-image').includes(data.appearance.imageVersion)&&getComputedStyle(document.querySelector('.scene-dock')).backgroundColor.includes(data.appearance.photoPalette.dark.tint.split(',').join(', '))","Secondary focus follows the new "+name+" image and its dark glass tint");systemColors(true,name+" secondary focus");shot("upload-"+profile+"-"+name+"-detail");
                js("setFocusScene(false);appearance();document.querySelector('button[data-tone=dark]').click();closeSheet();navigate('stats');return true");
                check("const c=getComputedStyle(document.querySelector('.reference-total'));const key=c.backgroundColor;const different=!Object.values(uploadDarkColors).includes(key);uploadDarkColors['"+name+"']=key;return different&&c.color==='rgb(245, 248, 255)'&&getComputedStyle(document.body).getPropertyValue('--photo-tint').trim()===data.appearance.photoPalette.dark.tint","The "+name+" dark photo tint adapts while keeping bright readable text");systemColors(true,name+" dark");shot("upload-"+profile+"-"+name+"-dark");
                byte[] backup=BackupStore.export(context,new JSONObject());BackupStore.Prepared prepared=BackupStore.prepare(new ByteArrayInputStream(backup));
                String color=WallpaperStore.state(context).getString("photoColor");if(!prepared.appearance.getString("photoColor").equals(color)||prepared.image==null)throw new AssertionError("Photo backup lost the sampled palette");
                WallpaperStore.restore(context,prepared.appearance,prepared.image);js("refresh();return true");
                check("return data.appearance.photoColor==="+JSONObject.quote(color)+"&&getComputedStyle(document.body).getPropertyValue('--photo-tint').trim()===data.appearance.photoPalette.dark.tint","The "+name+" ZIP photo restore preserves the exact glass palette");
                JSONObject legacy=new JSONObject(prepared.appearance.toString());legacy.remove("photoColor");legacy.remove("photoPalette");WallpaperStore.restore(context,legacy,prepared.image);
                if(!PhotoPalette.valid(WallpaperStore.state(context).optString("photoColor")))throw new AssertionError("Older photo settings were not migrated");js("refresh();return true");
                check("return !!data.appearance.photoPalette.dark.tint&&data.appearance.preset==='photo'","Existing "+name+" photos gain a bounded sampled palette without re-uploading");
            }
            if(requests[0]!=2)throw new AssertionError("The actual native picker was not invoked twice");
            String version=WallpaperStore.state(context).optString("imageVersion");selected[0]=null;js("appearance();$('choosePhoto').click();return true");Thread.sleep(400);
            check("return data.appearance.imageVersion==="+JSONObject.quote(version),"Cancelling replacement preserves the current photo");
            js("closeSheet();updateAppearance({preset:'white',tone:'light'});return true");check("return !document.body.classList.contains('photo-background')&&data.appearance.hasImage&&getComputedStyle(document.body).backgroundColor==='rgb(255, 255, 255)'&&document.body.style.getPropertyValue('--photo-tint')===''","Returning to white clears photo tint and keeps the imported photo available");
            report.append(failures==0?"PHOTO_UPLOAD_DONE ":"PHOTO_UPLOAD_FAILED ").append(profile).append(" failures=").append(failures).append('\n');
        }finally{test.removeMonitor(monitor);WallpaperStore.restore(context,original,image);js("refresh();closeSheet();navigate('focus');return true");for(Uri uri:created)context.getContentResolver().delete(uri,null,null);}
        return report.toString();
    }
}
