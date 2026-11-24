package com.lumifocus.app;

import android.graphics.*;
import org.json.*;
import java.io.File;
import java.util.Locale;

/** Bounded, one-time color sampling. No image work is performed by the timer renderer. */
final class PhotoPalette {
    static final String FALLBACK="#7d9aad";
    private PhotoPalette(){}
    static boolean valid(String value){return value!=null&&value.matches("#[a-fA-F0-9]{6}");}
    static String fromBitmap(Bitmap source){
        Bitmap sample=Bitmap.createScaledBitmap(source,24,24,true);
        try{
            double[] weight=new double[13],red=new double[13],green=new double[13],blue=new double[13];float[] hsv=new float[3];
            for(int y=0;y<24;y++)for(int x=0;x<24;x++){
                int color=sample.getPixel(x,y);if(Color.alpha(color)<128)continue;Color.colorToHSV(color,hsv);
                int bin=hsv[1]<.12f?12:Math.min(11,(int)(hsv[0]/30));
                double w=(.25+.75*hsv[1])*(hsv[2]<.06f||hsv[2]>.97f?.2:1);
                weight[bin]+=w;red[bin]+=Color.red(color)*w;green[bin]+=Color.green(color)*w;blue[bin]+=Color.blue(color)*w;
            }
            int best=0;for(int i=1;i<weight.length;i++)if(weight[i]>weight[best])best=i;
            if(weight[best]==0)return FALLBACK;
            return String.format(Locale.ROOT,"#%02x%02x%02x",(int)Math.round(red[best]/weight[best]),(int)Math.round(green[best]/weight[best]),(int)Math.round(blue[best]/weight[best]));
        }finally{if(sample!=source)sample.recycle();}
    }
    static String fromFile(File file){
        Bitmap bitmap=null;
        try{
            BitmapFactory.Options options=new BitmapFactory.Options();options.inJustDecodeBounds=true;BitmapFactory.decodeFile(file.getPath(),options);
            if(options.outWidth<1||options.outHeight<1)return FALLBACK;
            options.inSampleSize=1;while(Math.max(options.outWidth,options.outHeight)/options.inSampleSize>96)options.inSampleSize*=2;
            options.inJustDecodeBounds=false;bitmap=BitmapFactory.decodeFile(file.getPath(),options);return bitmap==null?FALLBACK:fromBitmap(bitmap);
        }catch(RuntimeException e){return FALLBACK;}finally{if(bitmap!=null)bitmap.recycle();}
    }
    private static int mix(int color,int base,double amount){return Color.rgb((int)Math.round(base*(1-amount)+Color.red(color)*amount),(int)Math.round(base*(1-amount)+Color.green(color)*amount),(int)Math.round(base*(1-amount)+Color.blue(color)*amount));}
    static int surface(String raw,boolean dark){int color=Color.parseColor(valid(raw)?raw:FALLBACK);return mix(color,dark?14:255,dark?.28:.10);}
    static JSONObject values(String raw)throws JSONException{
        int color=Color.parseColor(valid(raw)?raw:FALLBACK);JSONObject result=new JSONObject();
        for(boolean dark:new boolean[]{false,true}){
            int tint=surface(raw,dark),accent=mix(color,dark?255:0,dark?.55:.50);
            result.put(dark?"dark":"light",new JSONObject().put("tint",Color.red(tint)+","+Color.green(tint)+","+Color.blue(tint)).put("accent",String.format(Locale.ROOT,"#%06x",accent&0xffffff)));
        }
        return result;
    }
}
