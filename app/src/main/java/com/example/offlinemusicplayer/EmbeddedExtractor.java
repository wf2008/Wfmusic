package com.example.offlinemusicplayer;

import android.content.Context;
import com.chaquo.python.PyObject;
import com.chaquo.python.Python;
import com.chaquo.python.android.AndroidPlatform;
import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.json.JSONObject;

public final class EmbeddedExtractor {
    public interface PreviewCallback { void success(String json); void failure(String message); }
    public interface Callback { void success(File mp3); void failure(String message); }
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private PyObject module(Context context) {
        if (!Python.isStarted()) Python.start(new AndroidPlatform(context.getApplicationContext()));
        return Python.getInstance().getModule("embedded_extractor");
    }

    public void preview(Context context, String url, File cookiesFile, PreviewCallback callback) {
        executor.execute(() -> {
            try {
                PyObject m=module(context);
                PyObject result=cookiesFile!=null&&cookiesFile.isFile()
                        ?m.callAttr("preview_info",url,cookiesFile.getAbsolutePath())
                        :m.callAttr("preview_info",url);
                callback.success(result.toString());
            } catch(Throwable e) { callback.failure(message(e,"Preview failed")); }
        });
    }

    public void extract(Context context, String url, File outputDir, File cookiesFile, Callback callback) {
        executor.execute(() -> {
            File source=null;
            try {
                if(!outputDir.exists() && !outputDir.mkdirs()) throw new IllegalStateException("Cannot create music folder");
                File tempDir=new File(context.getCacheDir(),"wfmusic-source");
                if(!tempDir.exists() && !tempDir.mkdirs()) throw new IllegalStateException("Cannot create temporary download folder");
                File[] stale=tempDir.listFiles(); if(stale!=null) for(File f:stale) if(f.isFile()) f.delete();
                PyObject m=module(context);
                PyObject result=cookiesFile!=null&&cookiesFile.isFile()
                        ?m.callAttr("download_bestaudio",url,tempDir.getAbsolutePath(),cookiesFile.getAbsolutePath())
                        :m.callAttr("download_bestaudio",url,tempDir.getAbsolutePath());
                JSONObject metadata=new JSONObject(result.toString());
                source=new File(metadata.optString("path"));
                if(!source.isFile()) throw new IllegalStateException("The audio source was not created");
                String title=metadata.optString("title","Untitled");
                String extension=extensionOf(source.getName());
                if(extension.isEmpty()) extension="m4a";
                File target=new File(outputDir,safeFileName(title)+"."+extension);
                if(target.exists()) target.delete();
                if(!source.renameTo(target)) copyFile(source,target);
                source.delete();
                if(target.isFile() && target.length()>0){
                    JSONObject tags=new JSONObject(); tags.put("title",title); tags.put("artist",metadata.optString("artist","Unknown artist")); tags.put("album",metadata.optString("album","YouTube downloads")); tags.put("duration",metadata.optInt("duration",0));
                    try(java.io.FileWriter writer=new java.io.FileWriter(new File(outputDir,target.getName()+".json"))){writer.write(tags.toString());}
                    callback.success(target);
                }
                else callback.failure("The audio file could not be saved");
            } catch(Throwable e) { if(source!=null)source.delete(); callback.failure(message(e,"Embedded extractor failed")); }
        });
    }

    private static String safeFileName(String value){
        String name=value==null?"Untitled":value.trim();
        if(name.isEmpty())name="Untitled";
        name=name.replaceAll("[^a-zA-Z0-9 ._'()-]","_").replaceAll("\\s+"," ").trim();
        return name.length()>100?name.substring(0,100).trim():name;
    }
    private static String extensionOf(String name){int dot=name.lastIndexOf('.');return dot>0&&dot<name.length()-1?name.substring(dot+1).toLowerCase(java.util.Locale.US):"";}
    private static void copyFile(File source,File target)throws java.io.IOException{try(java.io.InputStream in=new java.io.FileInputStream(source);java.io.OutputStream out=new java.io.FileOutputStream(target)){byte[] buffer=new byte[8192];int n;while((n=in.read(buffer))!=-1)out.write(buffer,0,n);}}
    private static String message(Throwable e,String fallback){Throwable x=e;while(x.getCause()!=null&&x.getMessage()==null)x=x.getCause();return x.getMessage()==null?fallback:x.getMessage();}
    public void shutdown(){executor.shutdownNow();}
}
