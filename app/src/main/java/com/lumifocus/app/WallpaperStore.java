package com.lumifocus.app;

import android.content.*;
import android.graphics.*;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Build;
import org.json.*;
import java.io.*;

/** Keeps imported images private, bounded in size, and independent of picker grants. */
public final class WallpaperStore {
    private static final String PREFS = "lumi_appearance";
    private static final int MAX_EDGE = 2048;
    private WallpaperStore() {}
    private static android.content.SharedPreferences prefs(Context c) { return c.getSharedPreferences(PREFS,0); }
    public static synchronized JSONObject state(Context c) {
        try {
            JSONObject value = new JSONObject(prefs(c).getString("settings", "{}"));
            String file = prefs(c).getString("image", "");
            value.put("hasImage", !file.isEmpty() && new File(c.getFilesDir(),file).isFile());
            value.put("imageVersion", file);
            if(!value.has("preset")) value.put("preset","white");
            if(!value.has("blur")) value.put("blur",0);
            if(!value.has("dim")) value.put("dim",28);
            if(!value.has("glass")) value.put("glass",64);
            if(!value.has("liquid")) value.put("liquid",true);
            if(!value.has("tone")) value.put("tone","light");
            if(!value.has("position")) value.put("position",50);
            if(!value.has("material")) value.put("material","clear");
            if(!value.has("legibility")) value.put("legibility",true);
            // Apply the requested white palette once on upgrade, retaining the image and focus data.
            if(value.optInt("paletteVersion")<2) {
                value.put("paletteVersion",2).put("preset","white").put("tone","light").put("glass",Math.max(64,value.optInt("glass")));
                if(!file.isEmpty())value.put("dim",Math.max(48,value.optInt("dim")));
                if(!prefs(c).edit().putString("settings",value.toString()).commit())throw new IllegalStateException("外观保存失败");
            }
            if(value.optString("preset").equals("photo") && !value.optBoolean("hasImage")) value.put("preset",value.optString("tone").equals("dark")?"black":"white");
            // Old photo installations sample only when their photo is actually selected.
            if(value.optBoolean("hasImage")&&value.optString("preset").equals("photo")&&!PhotoPalette.valid(value.optString("photoColor"))){
                value.put("photoColor",PhotoPalette.fromFile(new File(c.getFilesDir(),file)));
                if(!prefs(c).edit().putString("settings",value.toString()).commit())throw new IllegalStateException("图片配色保存失败");
            }
            value.put("photoPalette",PhotoPalette.values(value.optString("photoColor",PhotoPalette.FALLBACK)));
            normalizeSolid(value);
            return value;
        } catch(JSONException e) { throw new IllegalStateException(e); }
    }
    public static synchronized JSONObject update(Context c, String raw) throws JSONException {
        JSONObject input = new JSONObject(raw), next = state(c);
        if(input.has("preset")) {
            String preset = input.getString("preset");
            if(!java.util.Arrays.asList("white","black","mist","ocean","sunset","night","photo").contains(preset)) throw new IllegalArgumentException("无效背景");
            if(preset.equals("photo") && !next.getBoolean("hasImage")) throw new IllegalArgumentException("请先选择一张图片");
            next.put("preset",preset);
        }
        for(String key : new String[]{"blur","dim","glass","position"}) if(input.has(key)) {
            double number = input.getDouble(key);
            int maximum = key.equals("blur")?24:key.equals("dim")?75:key.equals("glass")?80:100;
            if(!Double.isFinite(number)||number!=Math.rint(number)||number<0||number>maximum) throw new IllegalArgumentException("无效外观参数");
            next.put(key,(int)number);
        }
        if(input.has("tone")) {
            String tone=input.getString("tone");
            if(!tone.equals("light")&&!tone.equals("dark")) throw new IllegalArgumentException("无效文字风格");
            next.put("tone",tone);
        }
        if(input.has("liquid")) next.put("liquid",input.getBoolean("liquid"));
        if(input.has("legibility")) next.put("legibility",input.getBoolean("legibility"));
        if(input.has("material")) {
            String material=input.getString("material");
            if(!java.util.Arrays.asList("clear","soft","eco").contains(material))throw new IllegalArgumentException("无效玻璃样式");
            next.put("material",material);
        }
        // A solid preset always owns its matching text palette, including restored backups.
        normalizeSolid(next);
        if(!prefs(c).edit().putString("settings",next.toString()).commit()) throw new IllegalStateException("外观保存失败");
        return state(c);
    }
    static void normalizeSolid(JSONObject value) throws JSONException {
        String preset=value.optString("preset");
        if(preset.equals("white")||preset.equals("black"))value.put("tone",preset.equals("black")?"dark":"light");
    }
    public static synchronized InputStream openImage(Context c) throws IOException {
        String file=prefs(c).getString("image", "");
        if(!file.matches("wallpaper-[a-f0-9-]+\\.jpg")) throw new FileNotFoundException();
        return new FileInputStream(new File(c.getFilesDir(),file));
    }
    public static synchronized JSONObject removeImage(Context c) throws JSONException {
        String previous=prefs(c).getString("image", "");
        JSONObject next=state(c);
        next.put("preset",next.optString("tone").equals("dark")?"black":"white");
        next.remove("photoColor");next.remove("photoPalette");
        normalizeSolid(next);
        if(!prefs(c).edit().remove("image").putString("settings",next.toString()).commit()) throw new IllegalStateException("背景移除失败");
        if(previous.matches("wallpaper-[a-f0-9-]+\\.jpg")) new File(c.getFilesDir(),previous).delete();
        return state(c);
    }
    static synchronized void restore(Context c,JSONObject appearance,byte[] image) throws Exception {
        String previous=prefs(c).getString("image",""),name="";File created=null;
        try {
            if(image!=null){name="wallpaper-"+java.util.UUID.randomUUID()+".jpg";created=new File(c.getFilesDir(),name);try(FileOutputStream out=new FileOutputStream(created)){out.write(image);out.getFD().sync();}}
            android.content.SharedPreferences.Editor edit=prefs(c).edit().putString("settings",appearance.toString());
            if(name.isEmpty())edit.remove("image");else edit.putString("image",name);
            if(!edit.commit())throw new IOException("背景恢复失败");created=null;
            if(previous.matches("wallpaper-[a-f0-9-]+\\.jpg"))new File(c.getFilesDir(),previous).delete();
        } finally {if(created!=null)created.delete();}
    }
    public static JSONObject importImage(Context c, Uri uri) throws Exception {
        File source=File.createTempFile("import-", ".image", c.getCacheDir());
        File output=null; Bitmap bitmap=null;
        try {
            try(InputStream in=c.getContentResolver().openInputStream(uri); OutputStream out=new FileOutputStream(source)) {
                if(in==null) throw new IOException("无法读取图片");
                byte[] buffer=new byte[16384]; int count; long length=0;
                while((count=in.read(buffer))!=-1) { length+=count;if(length>30L*1024*1024) throw new IOException("请选择小于 30 MB 的图片");out.write(buffer,0,count); }
            }
            if(Build.VERSION.SDK_INT>=28) {
                bitmap=ImageDecoder.decodeBitmap(ImageDecoder.createSource(source),(decoder,info,src)->{
                    int w=info.getSize().getWidth(),h=info.getSize().getHeight();
                    double scale=Math.min(1.0,(double)MAX_EDGE/Math.max(w,h));
                    decoder.setTargetSize(Math.max(1,(int)(w*scale)),Math.max(1,(int)(h*scale)));
                    decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);
                });
            } else {
                BitmapFactory.Options options=new BitmapFactory.Options();options.inJustDecodeBounds=true;
                BitmapFactory.decodeFile(source.getPath(),options);
                if(options.outWidth<1||options.outHeight<1) throw new IOException("该文件不是可读取的图片");
                options.inSampleSize=1;
                while(Math.max(options.outWidth,options.outHeight)/options.inSampleSize>MAX_EDGE) options.inSampleSize*=2;
                options.inJustDecodeBounds=false;bitmap=BitmapFactory.decodeFile(source.getPath(),options);
                if(bitmap==null) throw new IOException("无法解码图片");
                int orientation=1;
                try {orientation=new ExifInterface(source.getPath()).getAttributeInt(ExifInterface.TAG_ORIENTATION,1);}catch(IOException noExif){/* Readable non-EXIF formats need no rotation. */}
                Matrix matrix=new Matrix();
                switch(orientation) {
                    case 2:matrix.setScale(-1,1);break;
                    case 3:matrix.setRotate(180);break;
                    case 4:matrix.setScale(1,-1);break;
                    case 5:matrix.setRotate(90);matrix.postScale(-1,1);break;
                    case 6:matrix.setRotate(90);break;
                    case 7:matrix.setRotate(-90);matrix.postScale(-1,1);break;
                    case 8:matrix.setRotate(-90);break;
                }
                if(!matrix.isIdentity()) { Bitmap rotated=Bitmap.createBitmap(bitmap,0,0,bitmap.getWidth(),bitmap.getHeight(),matrix,true);if(rotated!=bitmap)bitmap.recycle();bitmap=rotated; }
            }
            if(bitmap==null) throw new IOException("无法解码图片");
            String name="wallpaper-"+java.util.UUID.randomUUID()+".jpg";
            output=new File(c.getFilesDir(),name);
            try(FileOutputStream stream=new FileOutputStream(output)) { if(!bitmap.compress(Bitmap.CompressFormat.JPEG,90,stream))throw new IOException("图片保存失败");stream.getFD().sync(); }
            synchronized(WallpaperStore.class) {
                String previous=prefs(c).getString("image", "");
                JSONObject next=state(c).put("preset","photo").put("tone","light").put("dim",20).put("glass",56).put("blur",0).put("liquid",true).put("material","clear").put("legibility",true).put("photoColor",PhotoPalette.fromBitmap(bitmap));
                if(!prefs(c).edit().putString("image",name).putString("settings",next.toString()).commit()) throw new IOException("图片保存失败");
                output=null;
                if(previous.matches("wallpaper-[a-f0-9-]+\\.jpg"))new File(c.getFilesDir(),previous).delete();
                return state(c);
            }
        } finally { source.delete();if(output!=null)output.delete();if(bitmap!=null)bitmap.recycle(); }
    }
}
