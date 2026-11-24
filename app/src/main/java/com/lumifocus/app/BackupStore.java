package com.lumifocus.app;

import android.content.Context;
import android.graphics.BitmapFactory;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.*;

/** Portable, bounded ZIP backups. Parsing never writes files or changes current state. */
final class BackupStore {
    private static final int MAX_JSON=16*1024*1024,MAX_IMAGE=8*1024*1024,MAX_TOTAL=24*1024*1024;
    static final class Prepared {
        final JSONObject data,appearance,ui;final byte[] image;
        Prepared(JSONObject d,JSONObject a,JSONObject u,byte[] i){data=d;appearance=a;ui=u;image=i;}
        JSONObject summary() throws JSONException{return new JSONObject().put("sessions",data.getJSONArray("sessions").length()).put("tasks",data.getJSONArray("tasks").length()).put("events",data.getJSONArray("events").length()).put("plans",data.getJSONObject("planner").getJSONArray("items").length()).put("hasImage",image!=null);}
    }
    static byte[] read(InputStream in,int limit) throws IOException {
        if(in==null)throw new IOException("无法读取文件");ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buffer=new byte[16384];int count;
        while((count=in.read(buffer))!=-1){if(out.size()+count>limit)throw new IOException("备份文件过大");out.write(buffer,0,count);}return out.toByteArray();
    }
    private static JSONObject json(byte[] bytes) throws Exception {
        String text=new String(bytes,StandardCharsets.UTF_8);boolean string=false,escape=false;int depth=0;
        for(int i=0;i<text.length();i++){char c=text.charAt(i);if(string){if(escape)escape=false;else if(c=='\\')escape=true;else if(c=='"')string=false;}else if(c=='"')string=true;else if(c=='{'||c=='['){if(++depth>40)throw new IOException("备份结构过深");}else if(c=='}'||c==']')depth--;}
        if(string||depth!=0)throw new IOException("备份结构不完整");return new JSONObject(text);
    }
    static byte[] export(Context c,JSONObject ui) throws Exception {
        JSONObject envelope;byte[] image=null;Store store=Store.get(c);
        synchronized(store){synchronized(WallpaperStore.class){
            JSONObject a=WallpaperStore.state(c);
            if(a.optBoolean("hasImage"))try(InputStream in=WallpaperStore.openImage(c)){image=read(in,MAX_IMAGE);}
            envelope=new JSONObject().put("format","lumi-backup").put("version",1).put("exportedAt",System.currentTimeMillis()).put("data",new JSONObject(store.snapshot())).put("appearance",a).put("ui",ui);
        }}
        byte[] bytes=envelope.toString().getBytes(StandardCharsets.UTF_8);if(bytes.length>MAX_JSON)throw new IOException("历史过大，请先导出 JSON 归档");
        ByteArrayOutputStream output=new ByteArrayOutputStream();try(ZipOutputStream zip=new ZipOutputStream(output)){zip.putNextEntry(new ZipEntry("backup.json"));zip.write(bytes);zip.closeEntry();if(image!=null){zip.putNextEntry(new ZipEntry("wallpaper.jpg"));zip.write(image);zip.closeEntry();}}return output.toByteArray();
    }
    static Prepared prepare(InputStream source) throws Exception {
        byte[] bytes=read(source,MAX_TOTAL);if(bytes.length==0)throw new IOException("备份为空");
        byte[] document=null,image=null;
        if(bytes[0]=='{'||bytes[0]==' '||bytes[0]=='\n')document=bytes;
        else try(ZipInputStream zip=new ZipInputStream(new ByteArrayInputStream(bytes))){
            Set<String> seen=new HashSet<>();ZipEntry e;int total=0;
            while((e=zip.getNextEntry())!=null){String name=e.getName();if(e.isDirectory()||!Arrays.asList("backup.json","wallpaper.jpg").contains(name)||!seen.add(name))throw new IOException("备份包含无效或重复条目");byte[] value=read(zip,name.equals("backup.json")?MAX_JSON:MAX_IMAGE);total+=value.length;if(total>MAX_TOTAL)throw new IOException("备份解压内容过大");if(name.equals("backup.json"))document=value;else image=value;zip.closeEntry();}
        }
        if(document==null||document.length>MAX_JSON)throw new IOException("缺少有效备份数据");JSONObject envelope=json(document),data,appearance,ui;
        if(envelope.optString("format").equals("lumi-backup")){if(envelope.getInt("version")!=1)throw new IOException("暂不支持此备份版本");data=envelope.getJSONObject("data");appearance=envelope.getJSONObject("appearance");ui=envelope.optJSONObject("ui");}
        else{data=envelope;appearance=envelope.optJSONObject("appearance");ui=null;}
        if(appearance==null)appearance=new JSONObject();if(ui==null)ui=new JSONObject();
        JSONObject a=new JSONObject().put("preset",appearance.optString("preset","white")).put("tone",appearance.optString("tone","light")).put("material",appearance.optString("material","clear")).put("liquid",appearance.optBoolean("liquid",true)).put("legibility",appearance.optBoolean("legibility",true)).put("paletteVersion",2);
        for(String key:new String[]{"blur","dim","glass","position"}){int fallback=key.equals("glass")?64:key.equals("position")?50:key.equals("dim")?28:0;JSONObject value=new JSONObject().put(key,appearance.opt(key)==null?fallback:appearance.get(key));a.put(key,WorkspaceFeatures.number(value,key,0,key.equals("blur")?24:key.equals("dim")?75:key.equals("glass")?80:100));}
        if(!Arrays.asList("white","black","mist","ocean","sunset","night","photo").contains(a.getString("preset"))||!Arrays.asList("light","dark").contains(a.getString("tone"))||!Arrays.asList("clear","soft","eco").contains(a.getString("material")))throw new IOException("备份外观无效");
        if(image!=null){BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;BitmapFactory.decodeByteArray(image,0,image.length,bounds);if(bounds.outWidth<1||bounds.outHeight<1||Math.max(bounds.outWidth,bounds.outHeight)>2048||!"image/jpeg".equals(bounds.outMimeType))throw new IOException("备份背景图片无效");android.graphics.Bitmap decoded=BitmapFactory.decodeByteArray(image,0,image.length);if(decoded==null)throw new IOException("备份背景图片已损坏");try{String color=appearance.optString("photoColor");a.put("photoColor",PhotoPalette.valid(color)?color:PhotoPalette.fromBitmap(decoded));}finally{decoded.recycle();}}
        if(image==null&&a.getString("preset").equals("photo"))a.put("preset",a.getString("tone").equals("dark")?"black":"white");
        WallpaperStore.normalizeSolid(a);
        return new Prepared(WorkspaceFeatures.validateBackup(data),a,ui,image);
    }
    static void restore(Context c,Prepared prepared) throws Exception {
        Store store=Store.get(c);synchronized(store){
            JSONObject state=new JSONObject(store.snapshot());String status=state.getJSONObject("timer").getString("status");if(status.equals("running")||status.equals("paused"))throw new IOException("请先结束当前计时，再恢复备份");
            JSONObject previous=WallpaperStore.state(c);byte[] previousImage=null;if(previous.optBoolean("hasImage"))try(InputStream in=WallpaperStore.openImage(c)){previousImage=read(in,MAX_IMAGE);}
            WallpaperStore.restore(c,prepared.appearance,prepared.image);
            try{store.restore(prepared.data);}catch(Exception e){try{WallpaperStore.restore(c,previous,previousImage);}catch(Exception rollback){e.addSuppressed(rollback);}throw e;}
        }
        TimerService.cancelAlarm(c);c.stopService(new android.content.Intent(c,TimerService.class));c.stopService(new android.content.Intent(c,AmbientService.class));FocusWidget.updateAll(c);
    }
}
