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
            } catch(Exception e) { callback.failure(e.getMessage()==null?"Preview failed":e.getMessage()); }
        });
    }

    public void extract(Context context, String url, File outputDir, File cookiesFile, Callback callback) {
        executor.execute(() -> {
            try {
                PyObject m=module(context);
                PyObject result=cookiesFile!=null&&cookiesFile.isFile()
                        ?m.callAttr("download_bestaudio",url,outputDir.getAbsolutePath(),cookiesFile.getAbsolutePath())
                        :m.callAttr("download_bestaudio",url,outputDir.getAbsolutePath());
                File source=new File(result.toString()); String base=source.getName(); int dot=base.lastIndexOf('.'); if(dot>0)base=base.substring(0,dot);
                File target=new File(outputDir,base+".mp3");
                String command="-y -i "+quote(source.getAbsolutePath())+" -codec:a libmp3lame -b:a 192k "+quote(target.getAbsolutePath());
                FFmpegKit.executeAsync(command,session->{ if(ReturnCode.isSuccess(session.getReturnCode())){source.delete();callback.success(target);}else callback.failure("FFmpeg conversion failed"); });
            } catch(Exception e) { callback.failure(e.getMessage()==null?"Embedded extractor failed":e.getMessage()); }
        });
    }

    private static String quote(String path){return "'"+path.replace("'","'\\''")+"'";}
    public void shutdown(){executor.shutdownNow();}
}
