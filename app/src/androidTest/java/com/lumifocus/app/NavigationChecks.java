package com.lumifocus.app;

import android.app.*;
import android.content.Context;
import android.graphics.Bitmap;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.webkit.WebView;
import org.json.*;
import java.io.*;
import java.util.concurrent.*;

/** Actual hit-target and indicator geometry, rather than just page overflow. */
final class NavigationChecks {
    private final Instrumentation test;private final MainActivity activity;private final WebView web;private final Context context;
    private final StringBuilder report=new StringBuilder();private int failures;
    NavigationChecks(Instrumentation t,MainActivity a,WebView w){test=t;activity=a;web=w;context=t.getTargetContext();}
    private String js(String code)throws Exception{CountDownLatch latch=new CountDownLatch(1);String[] value=new String[1];test.runOnMainSync(()->web.evaluateJavascript("(()=>{"+code+"})()",r->{value[0]=r;latch.countDown();}));if(!latch.await(10,TimeUnit.SECONDS))throw new AssertionError("WebView callback timeout");return value[0];}
    private void check(String code,String label)throws Exception{boolean ok="true".equals(js(code));report.append(ok?"PASS ":"FAIL ").append(label).append('\n');if(!ok)failures++;}
    private void tapPage(String page)throws Exception{
        JSONArray point=new JSONArray(js("const r=document.querySelector('.bottom-nav [data-page="+page+"]').getBoundingClientRect();return [r.left+r.width/2,r.top+r.height/2]"));
        int[] offset=new int[2];float[] scale=new float[1];test.runOnMainSync(()->{web.getLocationOnScreen(offset);scale[0]=web.getScale();});
        float x=offset[0]+(float)point.getDouble(0)*scale[0],y=offset[1]+(float)point.getDouble(1)*scale[0];long now=SystemClock.uptimeMillis();
        MotionEvent down=MotionEvent.obtain(now,now,MotionEvent.ACTION_DOWN,x,y,0),up=MotionEvent.obtain(now,now+50,MotionEvent.ACTION_UP,x,y,0);
        try{test.sendPointerSync(down);test.sendPointerSync(up);}finally{down.recycle();up.recycle();}Thread.sleep(420);
    }
    private void shot(String name)throws Exception{js("glassMotion.clear();$('toast').classList.remove('show');return true");test.waitForIdleSync();Bitmap b=test.getUiAutomation().takeScreenshot();try(FileOutputStream out=new FileOutputStream(new File(context.getFilesDir(),name+".png"))){b.compress(Bitmap.CompressFormat.PNG,100,out);}b.recycle();}
    String run(String profile)throws Exception{
        if(!context.getPackageName().endsWith(".debug"))throw new AssertionError("Debug package required");
        JSONObject original=WallpaperStore.state(context);long revision=new JSONObject(Store.get(context).snapshot()).getLong("_revision");
        try{
            js("if(largeClockOpen)setLargeClock(false);setFocusScene(false);closeSheet();navigate('focus');return true");
            report.append("NAV_VIEWPORT ").append(profile).append(' ').append(js("return {w:innerWidth,h:innerHeight,dpr:devicePixelRatio}")).append('\n');
            for(String theme:new String[]{"white","black","photo"}){
                if(theme.equals("photo")&&!original.optBoolean("hasImage"))continue;
                js("updateAppearance({preset:'"+theme+"',tone:'"+(theme.equals("black")?"dark":"light")+"',liquid:true});return true");
                for(String page:new String[]{"focus","workspace","events","stats"}){
                    tapPage(page);
                    check("return page==='"+page+"'&&document.querySelector('.bottom-nav [aria-current=page]').dataset.page==='"+page+"'","Actual tap opens "+theme+" / "+page);
                    report.append("NAV_GEOMETRY ").append(theme).append('/').append(page).append(' ').append(js("const r=e=>{const b=e.getBoundingClientRect();return [b.x,b.y,b.width,b.height]};return {nav:r(document.querySelector('.bottom-nav')),buttons:[...document.querySelectorAll('.bottom-nav button')].map(r),capsule:r(document.querySelector('.nav-capsule')),position:getComputedStyle(document.querySelector('.nav-capsule')).position}")).append('\n');
                    check("const n=document.querySelector('.bottom-nav').getBoundingClientRect(),bs=[...document.querySelectorAll('.bottom-nav button')].map(b=>b.getBoundingClientRect());return n.left>=0&&n.right<=innerWidth+1&&n.top>=0&&n.bottom<=innerHeight+1&&bs.every(r=>r.width>=44&&r.height>=44&&r.left>=n.left&&r.right<=n.right+1&&r.top>=n.top&&r.bottom<=n.bottom+1)","All four "+theme+" targets fit the navigation without overflow");
                    check("const a=document.querySelector('.bottom-nav .selected').getBoundingClientRect(),b=document.querySelector('.nav-capsule').getBoundingClientRect();return ['left','top','width','height'].every(k=>Math.abs(a[k]-b[k])<=1.5)","Selected indicator aligns with "+theme+" / "+page);
                    check("return [...document.querySelectorAll('.bottom-nav button')].every(b=>{const r=b.getBoundingClientRect();return document.elementFromPoint(r.left+r.width/2,r.top+r.height/2)?.closest('button')===b})","All four "+theme+" button centers receive their own touch input");
                    check("return document.documentElement.scrollWidth<=innerWidth+1","The "+theme+" / "+page+" page stays within the current window");
                    if(theme.equals("white"))shot("navigation-"+profile+"-"+page);
                }
            }
            if(new JSONObject(Store.get(context).snapshot()).getLong("_revision")!=revision)throw new AssertionError("Navigation changed the native timer or history");
            report.append(failures==0?"NAV_DONE ":"NAV_FAILED ").append(profile).append(" failures=").append(failures).append('\n');
        }finally{WallpaperStore.update(context,original.toString());js("refresh();closeSheet();navigate('focus');return true");}
        return report.toString();
    }
}
