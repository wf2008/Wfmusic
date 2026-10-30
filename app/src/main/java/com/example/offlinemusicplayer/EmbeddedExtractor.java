package com.example.offlinemusicplayer;

import android.content.Context;
import com.chaquo.python.PyObject;
import com.chaquo.python.Python;
import com.chaquo.python.android.AndroidPlatform;
import com.arthenica.ffmpegkit.FFmpegKit;
import com.arthenica.ffmpegkit.ReturnCode;
import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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
            } catch(Exception e) { callback.failure(message(e,"Preview failed")); }
        });
    }

    public void extract(Context context, String url, File outputDir, File cookiesFile, Callback callback) {
        executor.execute(() -> {
            File source=null;
            try {
                File tempDir=new File(context.getCacheDir(),"wfmusic-source");
                if(!tempDir.exists() && !tempDir.mkdirs()) throw new IllegalStateException("Cannot create temporary download folder");
                File[] stale=tempDir.listFiles(); if(stale!=null) for(File f:stale) if(f.isFile()) f.delete();
                PyObject m=module(context);
                PyObject result=cookiesFile!=null&&cookiesFile.isFile()
                        ?m.callAttr("download_bestaudio",url,tempDir.getAbsolutePath(),cookiesFile.getAbsolutePath())
                        :m.callAttr("download_bestaudio",url,tempDir.getAbsolutePath());
                source=new File(result.toString());
                if(!source.isFile()) throw new IllegalStateException("The audio source was not created");
                String base=source.getName(); int dot=base.lastIndexOf('.'); if(dot>0)base=base.substring(0,dot);
                File target=new File(outputDir,base+".mp3");
                String command="-y -i "+quote(source.getAbsolutePath())+" -vn -codec:a libmp3lame -b:a 192k "+quote(target.getAbsolutePath());
                File sourceForCallback=source;
                FFmpegKit.executeAsync(command,session->{
                    if(ReturnCode.isSuccess(session.getReturnCode()) && target.isFile() && target.length()>0){
                        sourceForCallback.delete(); callback.success(target);
                    } else {
                        sourceForCallback.delete(); target.delete(); callback.failure("FFmpeg could not convert the downloaded audio");
                    }
                });
            } catch(Exception e) { if(source!=null)source.delete(); callback.failure(message(e,"Embedded extractor failed")); }
        });
    }

    private static String quote(String path){return "'"+path.replace("'","'\\''")+"'";}
    private static String message(Exception e,String fallback){return e.getMessage()==null?fallback:e.getMessage();}
    public void shutdown(){executor.shutdownNow();}
}
