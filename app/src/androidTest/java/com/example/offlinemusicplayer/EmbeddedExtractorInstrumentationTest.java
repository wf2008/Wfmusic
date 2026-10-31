package com.example.offlinemusicplayer;

import android.content.Context;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.File;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class EmbeddedExtractorInstrumentationTest {
    @Test
    public void downloadsAndConvertsRealSampleVideoToMp3() throws Exception {
        Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
        File output=new File(context.getCacheDir(),"instrumentation-output");
        deleteTree(output); assertTrue(output.mkdirs() || output.isDirectory());
        CountDownLatch done=new CountDownLatch(1);
        AtomicReference<File> result=new AtomicReference<>();
        AtomicReference<String> error=new AtomicReference<>();
        EmbeddedExtractor extractor=new EmbeddedExtractor();
        try {
            extractor.extract(context,
                    "https://github.com/mediaelement/mediaelement-files/blob/master/big_buck_bunny.mp4?raw=true",
                    output, null,
                    new EmbeddedExtractor.Callback(){
                        @Override public void success(File mp3){ result.set(mp3); done.countDown(); }
                        @Override public void failure(String message){ error.set(message); done.countDown(); }
                    });
            assertTrue("conversion timed out",done.await(7,TimeUnit.MINUTES));
            assertNull("conversion failed: "+error.get(),error.get());
            assertNotNull(result.get());
            assertTrue(result.get().isFile());
            assertTrue(result.get().getName().endsWith(".mp3"));
            assertTrue(result.get().length()>10000);
            assertEquals(0, countFilesWithExtension(output,".webm"));
            assertEquals(0, countFilesWithExtension(output,".mp4"));
        } finally { extractor.shutdown(); deleteTree(output); }
    }

    private static int countFilesWithExtension(File dir,String ext){int n=0;File[] fs=dir.listFiles();if(fs!=null)for(File f:fs)if(f.isFile()&&f.getName().endsWith(ext))n++;return n;}
    private static void deleteTree(File f){if(!f.exists())return;File[] fs=f.listFiles();if(fs!=null)for(File c:fs)deleteTree(c);f.delete();}
}
