package com.lumifocus.app;

import android.app.*;
import android.content.Context;
import android.graphics.Bitmap;
import android.webkit.WebView;
import org.json.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

/** Choose the real appearance controls and inspect every page's rendered palette. */
final class AppearanceChecks {
    private final Instrumentation test;private final WebView web;private final Context context;
    private final StringBuilder report=new StringBuilder();private int failures;
    AppearanceChecks(Instrumentation t,WebView w){test=t;web=w;context=t.getTargetContext();}
    private String js(String code)throws Exception{CountDownLatch latch=new CountDownLatch(1);String[] result=new String[1];test.runOnMainSync(()->web.evaluateJavascript("(()=>{"+code+"})()",r->{result[0]=r;latch.countDown();}));if(!latch.await(10,TimeUnit.SECONDS))throw new AssertionError("WebView timeout");return result[0];}
    private void check(String code,String label)throws Exception{boolean ok="true".equals(js(code));report.append(ok?"PASS ":"FAIL ").append(label).append('\n');if(!ok)failures++;}
    private void shot(String name)throws Exception{js("glassMotion.clear();$('toast').classList.remove('show');return true");test.waitForIdleSync();Thread.sleep(550);Bitmap b=test.getUiAutomation().takeScreenshot();try(FileOutputStream out=new FileOutputStream(new File(context.getFilesDir(),name+".png"))){b.compress(Bitmap.CompressFormat.PNG,100,out);}b.recycle();}
    private void systemColors(String label)throws Exception{
        JSONArray rgb=new JSONArray(js("return getComputedStyle(document.body).backgroundColor.match(/\\d+/g).slice(0,3).map(Number)"));int expected=android.graphics.Color.rgb(rgb.getInt(0),rgb.getInt(1),rgb.getInt(2));int[] colors=new int[2];
        test.runOnMainSync(()->{android.view.Window w=((Activity)web.getContext()).getWindow();colors[0]=w.getStatusBarColor();colors[1]=w.getNavigationBarColor();});
        if(colors[0]!=expected||colors[1]!=expected)throw new AssertionError("System bars differ from "+label);report.append("PASS Android system bars follow ").append(label).append('\n');
    }
    String run(String profile)throws Exception{
        if(!context.getPackageName().endsWith(".debug"))throw new AssertionError("Debug package required");
        JSONObject original=WallpaperStore.state(context);long revision=new JSONObject(Store.get(context).snapshot()).getLong("_revision");
        try{
            js("if(largeClockOpen)setLargeClock(false);setFocusScene(false);closeSheet();navigate('focus');window.paletteSamples={};window.pagePaletteSamples={};updateAppearance({liquid:true,material:'clear'});return true");
            for(String preset:new String[]{"mist","ocean","sunset","night"}){
                js("appearance();document.querySelector('button[data-preset="+preset+"]').click();return true");
                check("return data.appearance.preset==='"+preset+"'&&document.body.dataset.wallpaper==='"+preset+"'&&document.querySelector('button[data-preset="+preset+"]').getAttribute('aria-pressed')==='true'","Selecting "+preset+" persists and selects the real preset");
                report.append("PALETTE ").append(preset).append(' ').append(js("return {tint:getComputedStyle(document.body).getPropertyValue('--glass-tint'),sheet:getComputedStyle(document.querySelector('.sheet')).background,primary:getComputedStyle($('choosePhoto')).background,body:getComputedStyle(document.body).backgroundColor}")).append('\n');
                check("const a=getComputedStyle(document.querySelector('.sheet')),b=getComputedStyle($('choosePhoto'));const sample=[a.background,b.background].join('|');const unique=!Object.values(window.paletteSamples).includes(sample);window.paletteSamples['"+preset+"']=sample;return unique","The "+preset+" preset changes interface colors as well as the wallpaper");
                systemColors(preset);
                js("closeSheet();return true");
                for(String page:new String[]{"focus","workspace","events","stats"}){
                    js("navigate('"+page+"');return true");
                    check("return document.body.dataset.wallpaper==='"+preset+"'&&document.documentElement.scrollWidth<=innerWidth+1","The "+page+" page follows "+preset+" without overflow");
                    check("const card=$('"+page+"Page').querySelector('.glass'),c=getComputedStyle(card),sample=c.background,seen=window.pagePaletteSamples['"+page+"']||[];const ok=!seen.includes(sample)&&c.color===getComputedStyle(document.body).color;seen.push(sample);window.pagePaletteSamples['"+page+"']=seen;return ok","Rendered "+page+" cards and text adopt the distinct "+preset+" palette");
                    shot("appearance-"+profile+"-"+preset+"-"+page);
                }
                js("navigate('focus');setFocusScene(true);return true");
                check("return $('focusScene').dataset.background==='gradient'&&getComputedStyle($('focusScene')).color===getComputedStyle(document.body).color&&getComputedStyle(document.querySelector('.scene-dock')).backgroundColor.includes(getComputedStyle(document.body).getPropertyValue('--glass-tint').split(',').map(x=>x.trim()).join(', '))","Secondary focus uses the "+preset+" palette and wallpaper");
                shot("appearance-"+profile+"-"+preset+"-detail");js("setFocusScene(false);appearance();document.querySelector('button[data-tone="+(preset.equals("mist")?"dark":"light")+"]').click();return true");
                check("return data.appearance.preset==='"+preset+"'&&document.body.classList.contains('palette-theme')","Independent contrast adjustment retains the "+preset+" color family");systemColors(preset+" alternate contrast");
                js("document.querySelector('button[data-material=eco]').click();return true");check("return getComputedStyle(document.querySelector('.timer-panel')).backdropFilter==='none'","The "+preset+" lightweight material disables backdrop blur");
                js("document.querySelector('button[data-material=soft]').click();return true");check("return getComputedStyle(document.querySelector('.timer-panel')).backdropFilter.includes('24px')","The "+preset+" soft material retains its own blur recipe");
                js("document.querySelector('button[data-material=clear]').click();closeSheet();return true");
            }
            for(String solid:new String[]{"white","black"}){
                js("appearance();document.querySelector('button[data-preset="+solid+"]').click();closeSheet();return true");
                check("return !document.body.classList.contains('palette-theme')&&getComputedStyle(document.body).backgroundColor==='"+(solid.equals("white")?"rgb(255, 255, 255)":"rgb(0, 0, 0)")+"'","Switching from a colored preset restores pure "+solid+" without leftover tint");systemColors(solid);
            }
            if(new JSONObject(Store.get(context).snapshot()).getLong("_revision")!=revision)throw new AssertionError("Appearance changed the native timer/history");
            report.append(failures==0?"APPEARANCE_DONE ":"APPEARANCE_FAILED ").append(profile).append(" failures=").append(failures).append('\n');
        }finally{WallpaperStore.update(context,original.toString());js("refresh();closeSheet();navigate('focus');return true");}
        return report.toString();
    }
}
