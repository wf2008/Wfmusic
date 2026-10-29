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
    public interface Callback { void success(File mp3); void failure(String message); }
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public void extract(Context context, String url, File outputDir, Callback callback) {
        executor.execute(() -> {
            try {
                if (!Python.isStarted()) Python.start(new AndroidPlatform(context.getApplicationContext()));
                PyObject module = Python.getInstance().getModule("embedded_extractor");
                String sourcePath = module.callAttr("download_bestaudio", url, outputDir.getAbsolutePath()).toString();
                File source = new File(sourcePath);
                String base = source.getName();
                int dot = base.lastIndexOf('.');
                if (dot > 0) base = base.substring(0, dot);
                File target = new File(outputDir, base + ".mp3");
                String command = "-y -i " + quote(source.getAbsolutePath()) + " -codec:a libmp3lame -b:a 192k " + quote(target.getAbsolutePath());
                FFmpegKit.executeAsync(command, session -> {
                    if (ReturnCode.isSuccess(session.getReturnCode())) {
                        source.delete(); callback.success(target);
                    } else callback.failure("FFmpeg conversion failed");
                });
            } catch (Exception e) { callback.failure(e.getMessage() == null ? "Embedded extractor failed" : e.getMessage()); }
        });
    }

    private static String quote(String path) { return "'" + path.replace("'", "'\\''") + "'"; }
    public void shutdown() { executor.shutdownNow(); }
}
