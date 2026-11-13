package space.shakalizator.camera;
import android.content.Context;
import android.graphics.Bitmap;
import android.media.*;
import android.provider.MediaStore;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.Assert.*;
@RunWith(AndroidJUnit4.class)
@androidx.annotation.OptIn(markerClass=androidx.media3.common.util.UnstableApi.class)
public class VideoEngineTest {
    @Test public void exportedMp4ReallyHasIrregularFrameTimesAndFreezes() throws Exception {
        android.app.Instrumentation instrumentation=InstrumentationRegistry.getInstrumentation();Context context=instrumentation.getTargetContext();
        File input=new File(context.getCacheDir(),"motion-long.mp4"),output=new File(context.getCacheDir(),"irregular.mp4");
        try(InputStream in=instrumentation.getContext().getAssets().open("motion-long.mp4");OutputStream out=new FileOutputStream(input)){byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1)out.write(b,0,n);}
        CountDownLatch latch=new CountDownLatch(1);AtomicReference<Exception> error=new AtomicReference<>();AtomicReference<VideoEngine> engine=new AtomicReference<>();
        instrumentation.runOnMainSync(()->{VideoEngine e=new VideoEngine(context);engine.set(e);e.start(input,6,32,output,new VideoEngine.Listener(){public void progress(int n,String s){}public void done(File f){latch.countDown();}public void error(Exception ex){error.set(ex);latch.countDown();}});});
        boolean complete=latch.await(180,TimeUnit.SECONDS);if(!complete)instrumentation.runOnMainSync(()->engine.get().cancel());assertTrue("export timeout",complete);assertNull("export failed: "+error.get(),error.get());
        File external=context.getExternalFilesDir(null);if(external!=null){try(InputStream in=new FileInputStream(output);OutputStream out=new FileOutputStream(new File(external,"video-1.0.1-sample.mp4"))){byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1)out.write(b,0,n);}}
        // Gradle uninstalls the tested app, removing app-private external files. Keep the synthetic
        // diagnostic in Downloads on the disposable emulator so the workflow can pull it afterwards.
        if(android.os.Build.VERSION.SDK_INT>=29){android.content.ContentValues v=new android.content.ContentValues();v.put(MediaStore.MediaColumns.DISPLAY_NAME,"shakal-video-1.0.1-sample.mp4");v.put(MediaStore.MediaColumns.MIME_TYPE,"video/mp4");v.put(MediaStore.MediaColumns.RELATIVE_PATH,android.os.Environment.DIRECTORY_DOWNLOADS);android.net.Uri uri=context.getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI,v);assertNotNull(uri);try(InputStream in=new FileInputStream(output);OutputStream out=context.getContentResolver().openOutputStream(uri)){assertNotNull(out);byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1)out.write(b,0,n);}}
        MediaExtractor extractor=new MediaExtractor();long minimum=Long.MAX_VALUE,maximum=0,previous=-1;int count=0;java.util.Set<Long> intervals=new java.util.HashSet<>();java.util.List<Long> timestamps=new java.util.ArrayList<>();
        try{extractor.setDataSource(output.getPath());for(int i=0;i<extractor.getTrackCount();i++)if(extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME).startsWith("video/")){extractor.selectTrack(i);break;}
            while(extractor.getSampleTime()>=0){timestamps.add(extractor.getSampleTime());extractor.advance();}
            java.util.Collections.sort(timestamps);for(long time:timestamps){if(previous>=0){long d=time-previous;minimum=Math.min(minimum,d);maximum=Math.max(maximum,d);intervals.add(d/1000);}previous=time;count++;}
        }finally{extractor.release();input.delete();output.delete();}
        assertTrue("real frames exist",count>20);assertTrue("no >20fps intervals: "+minimum,minimum>=49_000);assertTrue("half-second hold: "+maximum,maximum>=400_000);assertTrue("hold not accidentally extended: "+maximum,maximum<650_000);assertTrue("irregular PTS",intervals.size()>=3);
        android.util.Log.i("ShakalTiming","frames="+count+" minIntervalUs="+minimum+" maxIntervalUs="+maximum+" distinctIntervals="+intervals.size());
    }
    @Test public void nativeVideoHasDecodableAvcAndAacAtEveryLevel() throws Exception {
        android.app.Instrumentation instrumentation=InstrumentationRegistry.getInstrumentation();Context context=instrumentation.getTargetContext();
        File input=new File(context.getCacheDir(),"motion.mp4");try(InputStream in=instrumentation.getContext().getAssets().open("motion.mp4");OutputStream out=new FileOutputStream(input)){byte[] buffer=new byte[8192];int n;while((n=in.read(buffer))!=-1)out.write(buffer,0,n);}
        for(int level:new int[]{1,2,3,4,5,6,7,8,9,10,12}){
            CountDownLatch latch=new CountDownLatch(1);AtomicReference<Exception> error=new AtomicReference<>();AtomicReference<VideoEngine> engine=new AtomicReference<>();File output=new File(context.getCacheDir(),"video-test-"+level+".mp4");
            instrumentation.runOnMainSync(()->{VideoEngine e=new VideoEngine(context);engine.set(e);e.start(input,level,42,output,new VideoEngine.Listener(){public void progress(int n,String s){}public void done(File f){latch.countDown();}public void error(Exception ex){error.set(ex);latch.countDown();}});});
            boolean complete=latch.await(120,TimeUnit.SECONDS);if(!complete)instrumentation.runOnMainSync(()->engine.get().cancel());assertTrue("timeout level "+level,complete);assertNull("export level "+level+": "+error.get(),error.get());assertTrue(output.length()>100);
            MediaExtractor extractor=new MediaExtractor();try{extractor.setDataSource(output.getPath());boolean avc=false,aac=false;for(int t=0;t<extractor.getTrackCount();t++){MediaFormat format=extractor.getTrackFormat(t);String mime=format.getString(MediaFormat.KEY_MIME);if("video/avc".equals(mime)){avc=true;assertEquals(480,Math.max(format.getInteger(MediaFormat.KEY_WIDTH),format.getInteger(MediaFormat.KEY_HEIGHT)));}if("audio/mp4a-latm".equals(mime))aac=true;}assertTrue("AVC",avc);assertTrue("AAC",aac);}finally{extractor.release();}
            try(MediaMetadataRetriever r=new MediaMetadataRetriever()){r.setDataSource(output.getPath());Bitmap frame=r.getFrameAtTime(0);assertNotNull("decoded frame",frame);assertNotEquals(0xff000000,frame.getPixel(frame.getWidth()/2,frame.getHeight()/2));frame.recycle();}
            output.delete();
        }
        input.delete();
    }
}
